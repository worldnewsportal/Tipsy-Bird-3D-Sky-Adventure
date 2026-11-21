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
cp native/SavePlugin.java "$JAVA_DIR/SavePlugin.java"
cp native/PlayGamesPlugin.java "$JAVA_DIR/PlayGamesPlugin.java"

# 2c) Auto Backup: Android/Google device backup restores sharedprefs (save mirror) after uninstall+reinstall
if grep -q 'android:allowBackup="false"' "$M"; then
  sed -i 's/android:allowBackup="false"/android:allowBackup="true"/' "$M"
fi
if ! grep -q 'android:allowBackup' "$M"; then
  sed -i '0,/<application /s//<application\n    android:allowBackup="true"/' "$M"
fi

# 1) Gradle dependency (Unity Ads SDK 4.22.0)
G=android/app/build.gradle
if ! grep -q 'com.unity3d.ads:unity-ads' "$G"; then
  sed -i "0,/dependencies {/s//dependencies {\n    implementation 'com.unity3d.ads:unity-ads:4.22.0'/" "$G"
fi

# 1b) Google Play Games Services v2 (cloud save / Saved Games API)
GID=$(cat tools/games-id.txt 2>/dev/null | tr -dc '0-9' || true)
[ -n "$GID" ] || GID=0
if ! grep -q 'play-services-games\|play-services-games-v2' "$G"; then
  python3 - "$G" <<'PYG'
import sys; p=sys.argv[1]; s=open(p).read()
old="    implementation 'com.unity3d.ads:unity-ads:4.22.0'\n"
assert s.count(old)==1
# play-services-games-v2 21.0.0 bundles EVERYTHING we need (PlayGames facade, GamesSignInClient,
# AuthenticationResult, SnapshotsClient + snapshot.* classes) — the v1 artifact would duplicate classes
s=s.replace(old, old+"    implementation 'com.google.android.gms:play-services-games-v2:21.0.0'\n")
open(p,'w').write(s)
PYG
fi

# 1c) Play Games App ID resource + manifest meta-data.
#     IMPORTANT: meta-data is ONLY injected when a real games ID exists in
#     tools/games-id.txt — with the default '0' placeholder we inject nothing,
#     because the GPGS SDK can refuse to start on malformed APP_ID values at
#     app start (hard crash risk). Plugin side also no-ops without real ID.
if [ "$GID" != "0" ]; then
  if ! grep -q 'games_app_id' "$APP/res/values/strings.xml" 2>/dev/null; then
    sed -i "s#</resources>#    <string name=\"games_app_id\">$GID</string>\n</resources>#" "$APP/res/values/strings.xml"
  fi
  if ! grep -q 'com.google.android.gms.games.APP_ID' "$M"; then
    python3 - "$M" <<'PYM'
import sys; p=sys.argv[1]; s=open(p).read()
old="    </application>"
assert s.count(old)==1
s=s.replace(old, '        <meta-data android:name="com.google.android.gms.games.APP_ID" android:value="@string/games_app_id" />\n    </application>')
open(p,'w').write(s)
PYM
  fi
else
  sed -i '/com.google.android.gms.games.APP_ID/d' "$M" || true
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
        registerPlugin(SavePlugin.class);
        registerPlugin(PlayGamesPlugin.class);
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
echo "--- verifying UI-thread safety (app-exit-on-online root cause) ---"
grep -q 'runOnUiThread' "$JAVA_DIR/UnityAdsPlugin.java" || { echo "FAIL: every Unity Ads call and View touch must run on the UI thread (Capacitor dispatches @PluginMethod on a background HandlerThread)"; exit 1; }
BANNER=$(grep -c 'private void ui(Runnable' "$JAVA_DIR/UnityAdsPlugin.java"); CALLS=$(grep -c 'ui(new Runnable' "$JAVA_DIR/UnityAdsPlugin.java")
if [ "$BANNER" -ne 1 ] || [ "$CALLS" -lt 6 ]; then echo "FAIL: UI-thread guard expected on initialize/load/show/banner/hide paths (helper=$BANNER marshalled=$CALLS)"; exit 1; fi
echo "OK: all Unity Ads calls marshalled to the UI thread"

echo "--- verifying save backup (survives uninstall) ---"
grep -q 'registerPlugin(SavePlugin.class)' "$MF" || { echo "FAIL: SaveBackup plugin not registered in MainActivity"; exit 1; }
RSP=$(grep -n 'registerPlugin(SavePlugin.class)' "$MF" | head -1 | cut -d: -f1)
if [ -z "$RSP" ] || [ "$RSP" -gt "$SC" ]; then echo "FAIL: registerPlugin(SavePlugin.class) must run before super.onCreate"; exit 1; fi
test -f "$JAVA_DIR/SavePlugin.java" || { echo "FAIL: SavePlugin.java missing"; exit 1; }

echo "--- verifying Google Play cloud save integration ---"
grep -q "com.google.android.gms:play-services-games-v2:21.0.0" "$G" || { echo "FAIL: play-services-games-v2 dependency missing"; exit 1; }
if [ "$GID" != "0" ]; then
  grep -q "com.google.android.gms.games.APP_ID" "$M" || { echo "FAIL: Play Games APP_ID meta-data missing"; exit 1; }
  grep -q "games_app_id" "$APP/res/values/strings.xml" || { echo "FAIL: games_app_id resource missing"; exit 1; }
else
  grep -q "com.google.android.gms.games.APP_ID" "$M" && { echo "FAIL: placeholder APP_ID meta-data must NOT be injected"; exit 1; }
  echo "note: cloud save dormant (no tools/games-id.txt) — game runs 100% offline-safe"
fi
grep -q "registerPlugin(PlayGamesPlugin.class)" "$MF" || { echo "FAIL: PlayGamesPlugin not registered in MainActivity"; exit 1; }
test -f "$JAVA_DIR/PlayGamesPlugin.java" || { echo "FAIL: PlayGamesPlugin.java missing"; exit 1; }
echo "OK: PlayGamesPlugin cloud-save integration is in place (games id: $GID$([ "$GID" = "0" ] && echo ' — PLACEHOLDER, add tools/games-id.txt'))"
grep -q 'getSharedPreferences(PREFS' "$JAVA_DIR/SavePlugin.java" || { echo "FAIL: SavePlugin must store into SharedPreferences (Auto Backup target)"; exit 1; }
grep -q 'android:allowBackup="true"' "$M" || { echo "FAIL: android:allowBackup=true required so Android/Google restores the save after uninstall"; exit 1; }
echo "OK: native save backup (SharedPreferences + Auto Backup) in place"
echo "--- verifying ad diagnostics (banner/interstitial visibility root cause) ---"
grep -q 'triggerWindowJSEvent("adstat"' "$JAVA_DIR/UnityAdsPlugin.java" || { echo "FAIL: adstat diagnostics event missing in native plugin"; exit 1; }
DC=$(grep -c 'stat(' "$JAVA_DIR/UnityAdsPlugin.java")
if [ "$DC" -lt 9 ]; then echo "FAIL: expected diagnostics on init/load/show/banner paths (stat calls=$DC)"; exit 1; fi
echo "OK: ad diagnostics wired ($DC stat points)"

echo "--- verifying network permission ---"
grep -q 'android.permission.ACCESS_NETWORK_STATE' "$M" || { echo "FAIL: ACCESS_NETWORK_STATE missing"; exit 1; }
CH=$(grep -n 'setDefaultUncaughtExceptionHandler' "$MF" | head -1 | cut -d: -f1)
if [ -z "$CH" ] || [ "$CH" -gt "$SC" ]; then echo "FAIL: crash handler must be installed before super.onCreate"; exit 1; fi
grep -q 'crash.log' "$MF" || { echo "FAIL: crash log path missing"; exit 1; }
echo "OK: crash reporter installed"
