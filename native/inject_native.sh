#!/usr/bin/env bash
# Injects the Unity Ads native plugin into the CI-generated Android project.
# Idempotent: safe to run multiple times.
set -euo pipefail
cd "$(dirname "$0")/.."

APP_ID=$(node -p "require('./capacitor.config.json').appId")
APP=android/app/src/main
JAVA_DIR=$APP/java/$(printf '%s' "$APP_ID" | tr '.' '/')
M=$APP/AndroidManifest.xml

if [ ! -d android ]; then
  echo "ERROR: android/ project missing (cap add must run first)"; exit 1
fi

mkdir -p "$JAVA_DIR"
cp native/UnityAdsPlugin.java "$JAVA_DIR/UnityAdsPlugin.java"

# 1) Gradle dependency (Unity Ads SDK 4.22.0)
G=android/app/build.gradle
if ! grep -q 'com.unity3d.ads:unity-ads' "$G"; then
  sed -i "0,/dependencies {/s//dependencies {\n    implementation 'com.unity3d.ads:unity-ads:4.22.0'/" "$G"
fi

# 2b) Network state permission (Unity Ads SDK checks connectivity on init/load)
if ! grep -q 'android.permission.ACCESS_NETWORK_STATE' "$M"; then
  python3 - "$M" <<'PYNET'
import sys
p = sys.argv[1]
s = open(p).read()
s = s.replace('</manifest>', '    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />\n</manifest>', 1)
open(p, 'w').write(s)
PYNET
fi

# 3) Register both plugins in MainActivity (rewrite canonically; cap add generates an empty class)
MF=$(find $APP/java -name MainActivity.java)
if [ -z "$MF" ]; then
  echo "ERROR: MainActivity.java not found"; exit 1
fi
cat > "$MF" <<JAVA
package $APP_ID;

import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import com.getcapacitor.BridgeActivity;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Date;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    PrintWriter w = new PrintWriter(new FileWriter(new File(getFilesDir(), "crash.log"), true));
                    w.println("--- crash " + new Date() + " ---");
                    w.println(Log.getStackTraceString(e));
                    w.close();
                } catch (Exception ignored) { }
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
        registerPlugin(UnityAdsPlugin.class);
        super.onCreate(savedInstanceState);
        try {
            File f = new File(getFilesDir(), "crash.log");
            if (f.exists() && f.length() > 0) {
                long skip = Math.max(0, f.length() - 6000);
                FileInputStream in = new FileInputStream(f);
                in.skip(skip);
                byte[] b = new byte[(int) (f.length() - skip)];
                int n = in.read(b);
                in.close();
                if (n > 0) {
                    String s = Base64.encodeToString(b, 0, n, Base64.NO_WRAP);
                    if (bridge != null && bridge.getWebView() != null) {
                        bridge.getWebView().evaluateJavascript("window.__lastCrashB64='" + s + "';try{dispatchEvent(new CustomEvent('crashinfo'))}catch(_e){}", null);
                    }
                }
            }
        } catch (Exception ignored) { }
    }
}
JAVA

echo "--- verifying native ad integration ---"
grep -q 'com.unity3d.ads:unity-ads:4.22.0' "$G" || { echo "FAIL: gradle dependency missing"; exit 1; }
grep -q 'registerPlugin(UnityAdsPlugin.class)' "$MF" || { echo "FAIL: MainActivity registration missing"; exit 1; }
RP=$(grep -n 'registerPlugin(UnityAdsPlugin.class)' "$MF" | head -1 | cut -d: -f1); SC=$(grep -n 'super.onCreate' "$MF" | head -1 | cut -d: -f1)
if [ -z "$RP" ] || [ -z "$SC" ] || [ "$RP" -gt "$SC" ]; then echo "FAIL: registerPlugin must be called BEFORE super.onCreate (Capacitor requirement)"; exit 1; fi
test -f "$JAVA_DIR/UnityAdsPlugin.java" || { echo "FAIL: plugin java file missing"; exit 1; }
echo "OK: UnityAdsPlugin native integration is in place (package: $APP_ID)"

echo "--- verifying network permission ---"
grep -q 'android.permission.ACCESS_NETWORK_STATE' "$M" || { echo "FAIL: ACCESS_NETWORK_STATE missing"; exit 1; }
CH=$(grep -n 'setDefaultUncaughtExceptionHandler' "$MF" | head -1 | cut -d: -f1)
if [ -z "$CH" ] || [ "$CH" -gt "$SC" ]; then echo "FAIL: crash handler must be installed before super.onCreate"; exit 1; fi
grep -q 'crash.log' "$MF" || { echo "FAIL: crash log path missing"; exit 1; }
echo "OK: crash reporter installed"
