# Tipsy Bird 3D — Android (APK via GitHub Actions)

1. Create a new GitHub repository and upload ALL files of this folder (keep `.github/workflows/android.yml`).
2. Push to `main` (or run the workflow manually from the Actions tab).
3. When it finishes, open the run and download the artifact **TipsyBird3D-apk**.

## Fixed signature (for updates / Play Store)
Without secrets the workflow signs with a temporary key (installable, but a new key each build).
For a permanent key add repository secrets: `KEYSTORE_BASE64` (base64 of your .jks), `KEYSTORE_PASSWORD`, `KEY_ALIAS`.
Create a keystore once:  keytool -genkeypair -keystore release.jks -alias tipsy -keyalg RSA -keysize 2048 -validity 10000
Then:  base64 -w0 release.jks   (paste into the secret)
