# Tipsy Bird Mono — نسخة 2D كاملة بـ MonoGame (C# native)

> استُبدل مشروع Raylib القديم `ultra/` بطلب من المالك بهذا المشروع الجديد: **نفس اللعبة الأصلية (فرع `vibe/fully-offline-c73d38`) بالضبط لكن 2D قوية و native** بلغة C# عبر **MonoGame 3.8.2 + net8.0-android**.

## بنية المشروع

```
mono/
├── TipsyBird.csproj        # net8.0-android + MonoGame.Framework.Android 3.8.2.1105
├── MainActivity.cs         # AndroidGameActivity (fullscreen landscape, SensorLandscape)
├── Game.cs                 # محرّك اللعبة كاملًا (~65KB ملف واحد)
├── Data.cs                 # مولّد آليًا من الأصلية — لا تُعدَّل يدويًا
└── Resources/
    ├── font_atlas.png      # أطلس Amiri 1024×211 — 125 حرفًا مُشكَّلًا (EmbeddedResource)
    └── mipmap-xxhdpi/ic_launcher.png
```

## المحتويات (مطابقة للأصلية)

| العنصر | القيمة |
|---|---|
| المابات | 8 (مرج/صحراء/قطب/بركان/محيط/فضاء/أدغال/عاصفة) بألوان + ضباب + 3 أطوار سماء أصلة |
| السكنات | 35 بأسعارها `p` وبوابات `m/mg` الأصلية |
| بوابات المابات | 30/50/75/100/130 نقطة أو 3/5/8/12/18 مفتاحًا |
| الإحياء | 25 عملة (درع 2.5 ثانية) |
| المهام | 5 خانات متجددة (score/coins/runs/combo) بمكافآت تصاعدية |
| الصندوق اليومي | 8 عملات + streak×2 (حتى 40) + مفتاح |
| الصوت | 8 مؤثرات + 3 قوالب موسيقية chiptune مولّدة PCM 22050 في الذاكرة |
| الخط | Amiri-Bold عربي مُشكَّل (لا حاجة لـ harfbuzz — النص جاهز LTR) |
| الأداء | 60 FPS ثابتة، قوائم Canvas فورية، جسيمات مقيّدة 260 |

## البناء (GitHub Actions فقط — لا يحتاج PC)

الـ workflow: `.github/workflows/monogame.yml`
1. `setup-dotnet` 8.0.x + `dotnet workload install android`
2. `dotnet publish mono/TipsyBird.csproj` → APK arm64
3. توقيع `tools/release.p12` (alias `tipsy`) عبر `apksigner`
4. artifact `TipsyBirdMono-apk` + إصدار **`mono-N`** (لا يمس `Latest`)

رابط التحميل بعد أي بناء:
`https://github.com/worldnewsportal/Tipsy-Bird-3D-Sky-Adventure/releases/download/mono-N/TipsyBirdMono.apk`

## ملاحظات تقنية

- كل الرسم عبر `SpriteBatch` (مستطيل/دائرة/دوران من texture أبيض 1×1 + دائرة 128px).
- واجهة افتراضية بكاشفة ثابتة **H=720** و `W = viewport * 720/vh` — تدعم أي نسبة شاشة.
- الحفظ: باينري بمagic `TBM3` + checksum بسيط (`tipsy_mono`).
- الإدخال: `TouchPanel` + Fallback موس.
