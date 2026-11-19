#!/usr/bin/env bash
# Injects the Unity Ads + Bluetooth native plugins into the CI-generated Android project.
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
cp native/BluetoothPlugin.java "$JAVA_DIR/BluetoothPlugin.java"

# 1) Gradle dependency (Unity Ads SDK 4.22.0)
G=android/app/build.gradle
if ! grep -q 'com.unity3d.ads:unity-ads' "$G"; then
  sed -i "0,/dependencies {/s//dependencies {\n    implementation 'com.unity3d.ads:unity-ads:4.22.0'/" "$G"
fi

# 2) Bluetooth permissions + optional hardware feature (python writer avoids sed \n pitfalls)
if ! grep -q 'android.permission.BLUETOOTH_CONNECT' "$M"; then
  python3 - "$M" <<'PYEX'
import sys
p = sys.argv[1]
s = open(p).read()
add = (''
    '    <uses-feature android:name="android.hardware.bluetooth" android:required="false" />\n'
    '    <uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />\n'
    '    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />\n'
    '    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />\n'
    '    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />\n'
    '    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />\n'
    '    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />\n'
    '    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" android:maxSdkVersion="30" />\n')
s = s.replace('</manifest>', add + '</manifest>', 1)
open(p, 'w').write(s)
PYEX
fi

# 3) Register both plugins in MainActivity (rewrite canonically; cap add generates an empty class)
MF=$(find $APP/java -name MainActivity.java)
if [ -z "$MF" ]; then
  echo "ERROR: MainActivity.java not found"; exit 1
fi
cat > "$MF" <<JAVA
package $APP_ID;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(UnityAdsPlugin.class);
        registerPlugin(BluetoothPlugin.class);
        super.onCreate(savedInstanceState);
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

echo "--- verifying native bluetooth integration ---"
grep -q 'registerPlugin(BluetoothPlugin.class)' "$MF" || { echo "FAIL: BT registration missing"; exit 1; }
BP=$(grep -n 'registerPlugin(BluetoothPlugin.class)' "$MF" | head -1 | cut -d: -f1)
if [ -z "$BP" ] || [ "$BP" -gt "$SC" ]; then echo "FAIL: BT registerPlugin must be called BEFORE super.onCreate (Capacitor requirement)"; exit 1; fi
test -f "$JAVA_DIR/BluetoothPlugin.java" || { echo "FAIL: bluetooth plugin java file missing"; exit 1; }
grep -q 'android.permission.BLUETOOTH_CONNECT' "$M" || { echo "FAIL: bluetooth permissions missing"; exit 1; }
grep -q 'android.permission.BLUETOOTH_SCAN' "$M" || { echo "FAIL: bluetooth scan permission missing"; exit 1; }
grep -q 'uses-feature android:name="android.hardware.bluetooth"' "$M" || { echo "FAIL: bluetooth feature declaration missing"; exit 1; }
echo "OK: BluetoothPlugin native integration is in place (package: $APP_ID)"
