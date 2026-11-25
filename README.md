# Tipsy Bird 3D — Android (APK via GitHub Actions)

اللعبة تعمل 100% دون إنترنت: محرك Three.js والخطوط مضمّنة في `src/vendor/`.

- دليل ربط Unity Ads (إعلانات مكافآت اختيارية): `docs/UNITY_ADS.md`
- العناصر الأسطورية وتمائم الجولة (عشر + عشر بإعلان دقيقة): `docs/LEGENDARY-CHARMS.md`
- قائمة جاهزية Google Play: `docs/PLAY_STORE.md`

1. Create a new GitHub repository and upload ALL files of this folder (keep `.github/workflows/android.yml`).
2. Push to `main` (or run the workflow manually from the Actions tab).
3. When it finishes, open the run and download the artifact **TipsyBird3D-apk**.

## Fixed signature (for updates / Play Store)
The release workflow refuses to publish unless signing secrets are present. Never commit a keystore or use a fallback password: a leaked signing key can allow forged updates.
For a permanent key add repository secrets: `KEYSTORE_BASE64` (base64 of your .jks), `KEYSTORE_PASSWORD`, `KEY_ALIAS`.
Create a keystore once:  keytool -genkeypair -keystore release.jks -alias tipsy -keyalg RSA -keysize 2048 -validity 10000
Then:  base64 -w0 release.jks   (paste into the secret)

For internal Unity Ads diagnosis, run the workflow manually and set `ads_test_mode=true`. This is deliberately unavailable on push builds; do not upload a test-ads APK to Google Play. Production ads still require an active `Banner_Android` placement and available inventory.
