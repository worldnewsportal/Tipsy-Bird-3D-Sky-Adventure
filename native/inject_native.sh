#!/usr/bin/env bash
# Injects the Unity Ads native plugin into the CI-generated Android project.
# Idempotent: safe to run multiple times.
set -euo pipefail
cd "$(dirname "$0")/.."

APP_ID=$(node -p "require('./capacitor.config.json').appId")
APP=android/app/src/main
JAVA_DIR=$APP/java/$APP_ID

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

# 2) Register plugin in MainActivity (rewrite canonically; cap add generates an empty class)
M=$(find $APP/java -name MainActivity.java)
if [ -z "$M" ]; then
  echo "ERROR: MainActivity.java not found"; exit 1
fi
cat > "$M" <<JAVA
package $APP_ID;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerPlugin(UnityAdsPlugin.class);
    }
}
JAVA

echo "--- verifying native ad integration ---"
grep -q 'com.unity3d.ads:unity-ads:4.22.0' "$G" || { echo "FAIL: gradle dependency missing"; exit 1; }
grep -q 'registerPlugin(UnityAdsPlugin.class)' "$M" || { echo "FAIL: MainActivity registration missing"; exit 1; }
test -f "$JAVA_DIR/UnityAdsPlugin.java" || { echo "FAIL: plugin java file missing"; exit 1; }
echo "OK: UnityAdsPlugin native integration is in place (package: $APP_ID)"
