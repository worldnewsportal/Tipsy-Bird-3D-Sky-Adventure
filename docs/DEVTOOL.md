# 🛠️ Tipsy Tools Pro — تطبيق المطور الرسمي

تطبيق أندرويد **منفصل تمامًا عن اللعبة** (لا WebView ولا Capacitor) — Java native نقي يُبنى في CI بأمرين (`aapt2 + javac + d8` بلا gradle).
الفكرة: قوة Termux وسطر الأوامر… لكن بواجهة أزرار وصفحات مرتّبة يفهمها المبتدئ من أول دقيقة.

## التحميل
- أحدث إصدار: `https://github.com/worldnewsportal/Tipsy-Bird-3D-Sky-Adventure/releases/download/dev-N/TipsyBirdDevTools.apk` (N = رقم آخر بناء ناجح لـ devtool.yml — يعرضه فاحص التحديث داخل التطبيق)
- كل الإصدارات: صفحة [Releases](https://github.com/worldnewsportal/Tipsy-Bird-3D-Sky-Adventure/releases) — الوسوم `dev-N` (لا تُزاح شارة Latest الخاصة ببناءات اللعبة `build-N` أبدًا).

## الصفحات الـ 12

| # | الصفحة | ماذا تفعل |
|---|--------|-----------|
| 0 | الرئيسية | لوحة قيادة: حالة اللعبة المثبتة + آخر إصدار بالمستودع + شبكة بلاطات تنقلك لكل صفحة |
| 1 | بناءات اللعبة | آخر 6 بناءات CI (✅/⏳/❌) مع الفرع والتاريخ + زر تحميل أحدث APK |
| 2 | إصدارات المطور | سجل إصدارات هذه الأداة dev-N مع الأحجام + تحميل مباشر |
| 3 | فاحص التحديث | يقارن نسختك المثبتة (لعبة + أداة) مع آخر إصدار وينبّهك، ثم ينزّل عبر DownloadManager بضغطة |
| 4 | قناة العمل | آخر تعليقات PR #1 حية + رد مباشر من التطبيق + إدارة رمز PAT (يُحفظ على جهازك فقط) |
| 5 | حزمة النشر | نسخ الوصفين AR/EN لمتجر Uptodown بضغطة + روابط: دليل Uptodown، الخصوصية، الإصدارات، PR #1 |
| 6 | بطاقة المشروع | الحزمة، Unity Game ID، المواضع الإعلانية، الفرع، التوقيع، إعدادات أندرويد |
| 7 | سجل المراحل | changelog كامل لكل مراحل المشروع |
| 8 | قائمة التحقق | checklist النشر — البنود المؤتمتة في CI مقابل ما تبقى يدويًا |
| 9 | وحدة الأوامر | «terminal» مرتب بأوامر جاهزة: help / status / builds / latest / releases / dispatch game / dispatch tool — كل أمر يخفي نداء API حقيقي |
| 10 | الجهاز | معلومات جهازك: الطراز، أندرويد، الرام، التخزين، البطارية، الشاشة |
| 11 | دليل المبتدئ | 6 خطوات من التثبيت حتى فهم علاقة الأداة بـ Termux وPAT |

## ما الذي حُفظ من النسخة السابقة؟
كل ميزات التطبيق القديم موجودة في صفحاتها: بناءات CI (ص1)، إصدارات dev-N (ص2)، حزمة النشر بالنسخ AR/EN (ص5)، بطاقة المشروع (ص6)، سجل المراحل (ص7)، قائمة التحقق (ص8).

## رمز PAT — لماذا وكيف
- **بدونه**: كل الصفحات القراءة تعمل (بناءات، إصدارات، تعليقات) — القراءة عامة.
- **به**: الرد على PR #1 من التطبيق + تشغيل بناء `dispatch game/tool`.
- أنشئه: GitHub → Settings → Developer settings → Personal access tokens (classic) → Generate → فعّل `repo` → الصقه في صفحة «قناة العمل».
- يُخزَّن في `SharedPreferences` المحلية فقط، ولا يُرسل إلا إلى api.github.com.

## المعمارية التقنية
- **بناء**: `.github/workflows/devtool.yml` → aapt2 compile/link → javac (JDK17) → d8 (`--min-api 23`) → zipalign → apksigner (مفتاح من GitHub Secrets؛ مفتاح مؤقت إن لم تُضبط الـ secrets).
- **واجهة**: LinearLayout برمجي بالكامل — Shell واحد (رأس متدرج + زر رجوع + ScrollView) و`show(page)` يبني الصفحة كل ضغطة (فتح = تحديث بيانات حي).
- **شبكة**: `httpGet`/`httpPost` مع `User-Agent: tipsy-devtool` (بدونه يرفض GitHub API) — خيوط خلفية daemon + `runOnUiThread`.
- **dispatch**: `POST /repos/{owner}/{repo}/actions/workflows/{file}/dispatches` مع `{"ref":"vibe/fully-offline-c73d38"}` — الرمز 204 نجاح.
- **كشف التطبيقات**: مانيفست يحتوي `<queries><package android:name="com.tipsybird.game"/></queries>` (مطلوب لأندرويد 11+).
- **versionCode** = `run_number` تلقائيًا → تثبيت التحديثات ينجح دائمًا بدون إلغاء تثبيت.

## حوكمة
- يُبنى فقط عند تغيّر `devtool/**` أو الـ workflow نفسه — إصدارات اللعبة `build-N` لا تتأثر.
- لا يُلمس `src/index.html` ولا `native/*.java` ولا gh-pages.
