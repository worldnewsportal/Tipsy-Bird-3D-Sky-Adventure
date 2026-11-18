# دليل ربط Unity Ads (محدَّث 2026/2027)

اللعبة جاهزة بالكامل من جهة الويب (JS). كل ما عليك هو إضافة الجزء الأصلي (الأندرويد) الذي يوفر إضافة كاباسيتور باسم `UnityAds`.

## الوضع الحالي لـ Unity Ads (مهم)

- **حزمة Unity القديمة داخل المحرر (Advertisement Legacy)**: منذ 1 أبريل 2026 أداؤها منخفض — لا نستخدمها.
- **Unity Ads Android SDK المباشر**: مدعوم ونشط — أحدث إصدار **4.21.0** (تحسينات تهيئة أسرع وحمولات أضمن). **هذا ما نستخدمه**.
- **Unity LevelPlay**: منصة وساطة لجمع شبكات إعلانية متعددة — اختيارية لاحقاً إن أردت AdMob/Meta في نفس اللعبة. ليست مطلوبة الآن.

## الخطوة 1: من لوحة Unity (مرة واحدة)

1. [dash.unity3d.com](https://dash.unity3d.com) → **Monetization → Projects → Add new project**
2. اكتب اسم المشروع و الحزمة نفسها الموجودة في `android/app/build.gradle` (applicationId)
3. **Monetization Setup → Android** → **Game ID (Android)** — لعبتك: `800443610` (أُضيف في الكود مسبقاً)
4. فعّل **Test mode** في البداية حتى تتحقق، وأوقفه قبل رفع الإصدار النهائي للنشر
5. الخانات الجاهزة تكفي: `Rewarded_Android`, `Interstitial_Android`, `Banner_Android`

## الخطوة 2: الإضافة الأصلية (Android/Java)

أضف السطر في `android/app/build.gradle`:

```
dependencies {
    implementation 'com.unity3d.ads:unity-ads:4.21.0'
}
```

أنشئ `android/app/src/main/java/<حزمتك>/UnityAdsPlugin.java`:

```java
import android.app.Activity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.ads.UnityAdsShowOptions;
import com.unity3d.services.banners.BannerView;
import com.unity3d.services.banners.UnityBannerSize;

@CapacitorPlugin(name = "UnityAds")
public class UnityAdsPlugin extends Plugin {
    private String gameId = "800443610";
    private boolean testMode = true; // اجعلها false قبل نشر الإصدار على متجر Play
    private BannerView banner;
    private IUnityAdsShowListener showListener(final PluginCall call, final boolean rewarded) {
        return new IUnityAdsShowListener() {
            @Override public void onUnityAdsShowComplete(String p, UnityAds.UnityAdsShowCompletionState st) {
                JSObject r = new JSObject();
                r.put(rewarded ? "rewarded" : "shown",
                      st == UnityAds.UnityAdsShowCompletionState.COMPLETED);
                call.resolve(r);
            }
            @Override public void onUnityAdsFailedToShow(String p, UnityAds.UnityAdsShowError e, String m) { call.resolve(); }
            @Override public void onUnityAdsStart(String p) { }
            @Override public void onUnityAdsClick(String p) { }
        };
    }
    private IUnityAdsLoadListener loadListener(final String placement, final boolean rewarded, final PluginCall call) {
        return new IUnityAdsLoadListener() {
            @Override public void onUnityAdsAdLoaded(String p) {
                Activity a = bridge.getActivity();
                UnityAds.show(a, placement, new UnityAdsShowOptions(), showListener(call, rewarded));
            }
            @Override public void onUnityAdsFailedToLoad(String p, UnityAds.UnityAdsLoadError e, String m) { call.resolve(); }
        };
    }
    @PluginMethod
    public void initialize(PluginCall call) {
        String id = call.getString("gameId");
        if (id != null && !id.isEmpty()) gameId = id;
        UnityAds.initialize(getContext(), gameId, new IUnityAdsInitializationListener() {
            @Override public void onInitializationComplete() { call.resolve(); }
            @Override public void onInitializationFailed(UnityAds.UnityAdsInitializationError e, String m) { call.reject(m); }
        }, call.getBoolean("testMode", testMode));
    }
    @PluginMethod
    public void showRewarded(PluginCall call) {
        if (!UnityAds.isInitialized()) { call.resolve(); return; }
        String pl = call.getString("placement", "Rewarded_Android");
        UnityAds.load(pl, loadListener(pl, true, call));
    }
    @PluginMethod
    public void showInterstitial(PluginCall call) {
        if (!UnityAds.isInitialized()) { call.resolve(); return; }
        String pl = call.getString("placement", "Interstitial_Android");
        UnityAds.load(pl, loadListener(pl, false, call));
    }
    @PluginMethod
    public void showBanner(PluginCall call) {
        String pl = call.getString("placement", "Banner_Android");
        Activity a = bridge.getActivity();
        if (banner != null) { banner.destroy(); banner = null; }
        banner = new BannerView(a, pl, new UnityBannerSize(320, 50));
        banner.setListener(new BannerView.IListener() { });
        ViewGroup root = a.findViewById(android.R.id.content);
        root.addView(banner, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL));
        banner.loadBanner();
        call.resolve();
    }
    @PluginMethod
    public void hideBanner(PluginCall call) {
        if (banner != null) { banner.destroy(); banner = null; }
        call.resolve();
    }
}
```

وسجّلها في `MainActivity.java`:

```java
registerPlugin(UnityAdsPlugin.class);
```

## الخطوة 3: كيف تتأكد أن الإعلانات تعمل؟

بعد ربط الإضافة، ادخل اللعبة:

- **الإعدادات ← حالة الإعلانات** ستعرض **مفعّلة ✓** مع زر **اختبار** — اضغطه: يظهر إعلان مكافأة حقيقي ثم رسالة "الإعلان يعمل ✓". هذا اختبار الربط الفوري.
- **الإعلان البيني** (interstitial): أنهِ 4 جولات كاملة دون إحياء — في نهاية الجولة الرابعة يظهر تلقائياً.
- **البانر**: شريط صغير أسفل شاشة القائمة فقط، ويختفي تماماً بمجرد بدء اللعب.

وقبل ربط الإضافة: تظهر "غير مرتبطة" ولا يوجد أي زر أو شريط إعلانات.

## سياسة عدم الإزعاج (مبنية في كود اللعبة)

| النوع | متى يظهر | الحماية |
|---|---|---|
| مكافأة Rewarded | فقط بضغطة اللاعب (هدية 🎁 / إحياء) | اختياري دائماً + مؤقّت أمان 20 ث |
| بيني Interstitial | بعد نهاية الجولة | كل 4 جولات فقط + فاصل دقيقتين على الأقل + لا يظهر بعد إحياء |
| بانر Banner | شاشة القائمة فقط | يختفي قبل بدء اللعب |

- مفتاح **المكافآت الإعلانية** في الإعدادات يوقف الثلاثة فوراً.
- الـ Game ID (`800443610`) مدموج في كود اللعبة، ويمكن تجاوزه عبر `window.UnityAdsGameId`، ووضع الاختبار عبر `window.UnityAdsTestMode` (افتراضياً: إنتاج).
- لا يوجد أي مفتاح API سرّي — Game ID ليس سراً.

## ملاحظات أمنية ونشر

- تأكد أن Protobuf 3.25.6+ يأتي كتركيبة تبعية SDK (توصية أمنية من Unity).
- **Test mode ON** أثناء التجربة، **OFF** قبل رفع الإصدار إلى Google Play.
- إن رغبت لاحقاً بالوساطة (AdMob + Meta + غيرها) فالحل Unity LevelPlay — الحاجة وقتها تغيير الإضافة الأصلية فقط؛ واجهة اللعبة (JS) لن تتغير.
