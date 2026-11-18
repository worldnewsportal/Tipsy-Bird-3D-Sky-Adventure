# دليل تكامل Unity Ads

اللعبة جاهزة بالكامل من جهة الويب (JS). كل ما عليك هو إضافة الجزء الأصيل (الأندرويد) الذي يوفر إضافة كاباسيتور باسم `UnityAds`.

## العقد المتفق عليه (Bridge API)

الكود في `src/index.html` يبحث عن:

```js
window.Capacitor.Plugins.UnityAds.showRewarded({ placement: 'Rewarded_Android' })
// يرجع Promise → resolves { rewarded: true }  أو rejects
```

- `rewarded: true` تعني أن اللاعب أكمل المشاهدة وحصل على المكافأة.
- إذا لم تكن الإضافة موجودة (نسخة قبل إضافتك)، تُخفى جميع أزرار الإعلانات تلقائيًا ولا يظهر أي خطأ — اللعبة تعمل 100% بلا إعلانات.

## نقاط ظهور الإعلانات (كلها اختيارية ولا تظهر إلا بضغطة اللاعب)

| المكان | الزر | المكافأة |
|---|---|---|
| القائمة الرئيسية | 🎁 (يظهر كل 3 دقائق) | +15 عملة |
| شاشة نهاية الجولة | استمرار 🎁 | إحياء مجاني مرة واحدة بالجولة |

لا نوافذ منبثقة، لا بانرات، لا إعلانات بينية — إعلانات مكافآت فقط، وهذا الأنسب لتقييم جوجل بلا إزعاج.

## خطوات الربط (أندرويد)

1. أنشئ مشروعًا في [Unity Dashboard](https://dashboard.unity3d.com/) → **Monetization** واحصل على **Game ID (Android)** وأنشئ Placement من نوع Rewarded باسم `Rewarded_Android`.
2. في `android/app/build.gradle` أضف:
   ```gradle
   dependencies {
       implementation 'com.unity3d.ads:unity-ads:4.12.3'
   }
   ```
3. أنشئ `android/app/src/main/java/com/tipsybird/game/UnityAdsPlugin.java`:

   ```java
   package com.tipsybird.game;

   import com.getcapacitor.JSObject;
   import com.getcapacitor.Plugin;
   import com.getcapacitor.PluginCall;
   import com.getcapacitor.PluginMethod;
   import com.getcapacitor.annotation.CapacitorPlugin;
   import com.unity3d.ads.IUnityAdsFinishListener;
   import com.unity3d.ads.IUnityAdsLoadListener;
   import com.unity3d.ads.IUnityAdsShowListener;
   import com.unity3d.ads.UnityAds;
   import com.unity3d.ads.splash.SplashView; // غير مطلوب، احذفه إن لم تستخدمه

   @CapacitorPlugin(name = "UnityAds")
   public class UnityAdsPlugin extends Plugin {
     private static final String GAME_ID = "ضع_معرف_اللعبة_هنا";
     private String pendingCall;

     @PluginMethod
     public void showRewarded(PluginCall call) {
       String placement = call.getString("placement", "Rewarded_Android");
       pendingCall = call.getCallbackId();
       UnityAds.load(placement, new IUnityAdsLoadListener() {
         @Override public void onUnityAdsAdLoaded(String p) {
           UnityAds.show(getActivity(), p, new IUnityAdsShowListener() {
             @Override public void onUnityAdsShowComplete(String p, UnityAds.UnityAdsShowCompletionState state) {
               boolean rewarded = state == UnityAds.UnityAdsShowCompletionState.COMPLETED;
               PluginCall c = bridge.getSavedCall(pendingCall);
               if (c != null) { JSObject r = new JSObject(); r.put("rewarded", rewarded); c.resolve(r); }
             }
             @Override public void onUnityAdsFailedToShow(String p, UnityAds.UnityAdsShowError e) { resolveFail(); }
             @Override public void onUnityAdsClick(String p) {}
             @Override public void onUnityAdsShowStart(String p) {}
             @Override public void onUnityAdsShowFailure(String p, UnityAds.UnityAdsShowError e) { resolveFail(); }
           });
         }
         @Override public void onUnityAdsFailedToLoad(String p, UnityAds.UnityAdsLoadError e, String msg) { resolveFail(); }
         private void resolveFail() {
           PluginCall c = bridge.getSavedCall(pendingCall);
           if (c != null) c.reject("ad_failed");
         }
       });
     }
   }
   ```

4. سجّل الإضافة في `MainActivity.java`:
   ```java
   registerPlugin(UnityAdsPlugin.class);
   ```
5. شغّل `npx cap sync android` (تلقائي في الـ workflow).

## ملاحظات امتثال مهمة

- **GDPR/خصوصية**: Unity Ads يعرض بنر الموافقة الخاص به في المناطق الأوروبية تلقائيًا عند تهيئة `UnityAds.initialize()`. ننصح باستدعاء `initialize` مع `testMode=false` فقط في نسخة الإنتاج.
- **استهدف الأطفال**: اللعبة مناسبة للعائلات؛ عند ملء استبيان محتوى Play اختر التصنيف المناسب، وأعلن عن جمع بيانات الإعلانات في نموذج "أمان البيانات".
- لا تضع Game ID في أي مستودع عام حساس — هو ليس سرًا (معلن في الـ SDK أصلًا)، لكن الأفضل وضعه في `strings.xml`.
