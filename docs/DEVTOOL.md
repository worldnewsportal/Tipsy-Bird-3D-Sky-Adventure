# 🛠️ Tipsy DevTools — تطبيق المطورين المنفصل

تطبيق Android **مستقل تمامًا عن اللعبة** (لا Capacitor، لا WebView، لا gradle) — حزمة خاصة بنا:
`com.tipsybird.devtool`. يُبنى تلقائيًا في GitHub Actions كل مرة يتغير مجلد `devtool/`.

## ماذا يعرض التطبيق؟

| القسم | المحتوى |
|---|---|
| 🚀 بناءات اللعبة (CI) | آخر 6 بناءات للعبة: رقم البناء، الفرع، الحالة (✅/❌/⏳)، ورابط APK مباشر لكل بناء ناجح |
| 🧪 إصدارات dev-N | آخر إصدار لهذا التطبيق نفسه + رابط تحميل مباشر |
| 📦 بطاقة المشروع | package اللعبة، Game ID، أسماء placements، مكان ملف التوقيع |
| 🖼️ حزمة النشر | حالة أصول Uptodown (10 صور جاهزة في `icons/store`) |
| 📋 نسخ الوصف | زر نسخ الوصف العربي + زر نسخ الإنجليزي (جاهزان للّصق في Uptodown) |
| 🔗 روابط سريعة | UPTODOWN.md · صفحة الخصوصية · Releases · PR #1 |
| 🎓 سجل المراحل | ملخص كل التحديثات من 1 حتى 29 |
| ✅ قائمة التحقق | ما اكتمل وما تبقى يدويًا (بريد التواصل، تفعيل GitHub Pages) |

## كيف تُحدّثه؟

1. عدّل ما تريد داخل `devtool/src/com/tipsybird/devtool/MainActivity.java`
   (كل الواجهة مبنية برمجيًا — لا XML layouts، والنصوص ثوابت في أعلى الملف).
2. ارفع التغيير إلى الفرع `vibe/fully-offline-c73d38` — يبدأ workflow باسم
   **"Build DevTools APK"** تلقائيًا.
3. عند نجاح البناء يظهر إصدار جديد باسم `dev-N` على صفحة Releases، وفيه:
   `TipsyBirdDevTools.apk`.
4. ثبّت الـ APK من الرابط المباشر:
   `https://github.com/worldnewsportal/Tipsy-Bird-3D-Sky-Adventure/releases/download/dev-N/TipsyBirdDevTools.apk`
   (يمكن تنزيل dev tool الجديد مباشرة من قسم "إصدارات dev-N" داخل التطبيق نفسه عند اتصال الإنترنت).

## ملخص تقني للبناء

- `devtool/AndroidManifest.xml` — package `com.tipsybird.devtool`، إذن `INTERNET` فقط، `fullSensor`.
- البناء في CI: `aapt2 compile/link` + `javac` + `d8` + `zipalign` + `apksigner`
  **بملف التوقيع الثابت** `tools/release.p12` — نفس توقيع اللعبة تمامًا.
- يُنشر على tag `dev-N` (N = run_number يتصاعد تلقائيًا؛ لا يتعارض مع إصدارات اللعبة `build-N`).
- `versionCode` يتصاعد تلقائيًا مع كل بناء لذا التحديث فوق النسخة القديمة يعمل دون مسح.

## ملاحظات

- التطبيق **داخلي لنا فقط** — غير موجه للمستخدمين، ولا يُنشر على Uptodown/Play.
- لا يعالج أي بيانات من اللعبة؛ يقرأ فقط واجهة GitHub API العامة (بناءات + releases).
