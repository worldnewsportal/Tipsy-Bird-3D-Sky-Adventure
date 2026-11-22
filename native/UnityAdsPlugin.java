package com.tipsybird.game;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.view.Gravity;
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
import com.unity3d.services.banners.BannerErrorInfo;
import com.unity3d.services.banners.BannerView;
import com.unity3d.services.banners.UnityBannerSize;

@CapacitorPlugin(name = "UnityAds")
public class UnityAdsPlugin extends Plugin {
    private String gameId = "800443610";
    private boolean testMode = false;
    private BannerView banner;
    private int bannerTries = 0;
    private boolean bannerWanted = false;
    private boolean bannerReady = false;
    private String bannerPlacement = "Banner_Android";
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    // KeepAlive: periodic watchdog so the banner NEVER dies (owner requirement).
    // Every 60s: if the banner is not alive+ready it is recreated/reloaded.
    private static final long KEEPALIVE_MS = 60000L;
    private final Runnable keepAlive = new Runnable() {
        @Override public void run() {
            try { bannerKeepAliveTick(); } catch (Exception ignored) { }
            try { bannerHandler.postDelayed(keepAlive, KEEPALIVE_MS); } catch (Exception ignored) { }
        }
    };
    private Application.ActivityLifecycleCallbacks lifecycle;
    private boolean resumed = true;

    private void ui(Runnable r) {
        if (bridge == null) return;
        Activity a = bridge.getActivity();
        if (a == null || a.isFinishing()) return;
        a.runOnUiThread(r);
    }

    private void stat(String k, String v) {
        try {
            if (bridge == null) return;
            JSObject d = new JSObject();
            d.put("k", k);
            d.put("v", v);
            bridge.triggerWindowJSEvent("adstat", d.toString());
        } catch (Exception ignored) { }
    }

    private IUnityAdsShowListener showListener(final PluginCall call, final boolean rewarded) {
        return new IUnityAdsShowListener() {
            @Override
            public void onUnityAdsShowComplete(String placement, UnityAds.UnityAdsShowCompletionState state) {
                try {
                    stat(rewarded ? "Rewarded" : "Interstitial",
                            state == UnityAds.UnityAdsShowCompletionState.COMPLETED ? "completed" : "skipped/closed");
                    JSObject r = new JSObject();
                    r.put(rewarded ? "rewarded" : "shown",
                            state == UnityAds.UnityAdsShowCompletionState.COMPLETED);
                    call.resolve(r);
                } catch (Exception e) { }
            }

            @Override
            public void onUnityAdsShowFailure(String placement, UnityAds.UnityAdsShowError error, String message) {
                try {
                    stat(rewarded ? "Rewarded" : "Interstitial", "show failed: " + message);
                    JSObject r = new JSObject();
                    r.put("failed", true);
                    call.resolve(r);
                } catch (Exception e) { }
            }

            @Override
            public void onUnityAdsShowStart(String placement) { }

            @Override
            public void onUnityAdsShowClick(String placement) { }
        };
    }

    private IUnityAdsLoadListener loadListener(final String placement, final boolean rewarded, final PluginCall call) {
        return new IUnityAdsLoadListener() {
            @Override
            public void onUnityAdsAdLoaded(String p) {
                ui(new Runnable() {
                    public void run() {
                        try {
                            Activity a = bridge != null ? bridge.getActivity() : null;
                            if (a == null || a.isFinishing()) {
                                JSObject r = new JSObject();
                                r.put("failed", true);
                                call.resolve(r);
                                return;
                            }
                            UnityAds.show(a, placement, new UnityAdsShowOptions(), showListener(call, rewarded));
                        } catch (Exception e) {
                            try {
                                JSObject r = new JSObject();
                                r.put("failed", true);
                                call.resolve(r);
                            } catch (Exception ignored) { }
                        }
                    }
                });
            }

            @Override
            public void onUnityAdsFailedToLoad(String p, UnityAds.UnityAdsLoadError error, String message) {
                try {
                    stat(rewarded ? "Rewarded" : "Interstitial", "load failed: " + message);
                    JSObject r = new JSObject();
                    r.put("failed", true);
                    call.resolve(r);
                } catch (Exception e) { }
            }
        };
    }

    @PluginMethod
    public void initialize(PluginCall call) {
        String id = call.getString("gameId");
        if (id != null && !id.isEmpty()) gameId = id;
        Boolean tm = call.getBoolean("testMode");
        if (tm != null) testMode = tm;
        call.setKeepAlive(true);
        startKeepAlive();
        ui(new Runnable() {
            public void run() {
                try {
                    UnityAds.initialize(getContext(), gameId, testMode, new IUnityAdsInitializationListener() {
                        @Override
                        public void onInitializationComplete() {
                            try {
                                JSObject r = new JSObject();
                                r.put("initialized", true);
                                call.resolve(r);
                                stat("init", "ok");
                            // Banner is top priority: auto-show as soon as the SDK
                            // is ready, without waiting for any JS command.
                            if (bannerWanted) scheduleBannerRetry(800);
                            } catch (Exception e) { }
                        }

                        @Override
                        public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String message) {
                            try {
                                JSObject r = new JSObject();
                                r.put("failed", true);
                                call.resolve(r);
                                stat("init", "failed: " + message);
                            } catch (Exception e) { }
                        }
                    });
                } catch (Exception e) {
                    try {
                        JSObject r = new JSObject();
                        r.put("failed", true);
                        call.resolve(r);
                    } catch (Exception ignored) { }
                }
            }
        });
    }

    @PluginMethod
    public void showRewarded(PluginCall call) {
        if (!UnityAds.isInitialized()) { call.resolve(); return; }
        call.setKeepAlive(true);
        final String pl = call.getString("placement", "Rewarded_Android");
        ui(new Runnable() {
            public void run() {
                try { UnityAds.load(pl, loadListener(pl, true, call)); } catch (Exception e) { call.resolve(); }
            }
        });
    }

    @PluginMethod
    public void showInterstitial(PluginCall call) {
        if (!UnityAds.isInitialized()) { call.resolve(); return; }
        call.setKeepAlive(true);
        final String pl = call.getString("placement", "Interstitial_Android");
        ui(new Runnable() {
            public void run() {
                try { UnityAds.load(pl, loadListener(pl, false, call)); } catch (Exception e) { call.resolve(); }
            }
        });
    }

    @PluginMethod
    public void preload(PluginCall call) {
        if (!UnityAds.isInitialized()) { call.resolve(); return; }
        String type = call.getString("type", "rewarded");
        final String pl = "interstitial".equals(type)
                ? call.getString("placement", "Interstitial_Android")
                : call.getString("placement", "Rewarded_Android");
        call.setKeepAlive(true);
        ui(new Runnable() {
            public void run() {
                try {
                    UnityAds.load(pl, new IUnityAdsLoadListener() {
                        @Override
                        public void onUnityAdsAdLoaded(String p) {
                            try {
                                JSObject r = new JSObject();
                                r.put("ready", true);
                                call.resolve(r);
                            } catch (Exception e) { }
                        }

                        @Override
                        public void onUnityAdsFailedToLoad(String p, UnityAds.UnityAdsLoadError error, String message) {
                            try { call.resolve(); } catch (Exception e) { }
                        }
                    });
                } catch (Exception e) {
                    try { call.resolve(); } catch (Exception ignored) { }
                }
            }
        });
    }

    /* ---------------- Banner: never-give-up engine (Unity Ads SDK 4.22.0) ----------------
       Guarantees (owner requirement: banner ALWAYS visible, NEVER dies):
       - a request made before SDK init is QUEUED, never dropped
       - load failures retry FOREVER (10s first, capped at 60s backoff) — no-fill included
       - banner auto-shows right after SDK initialization completes
       - KeepAlive watchdog every 60s: if the banner is missing/not ready → recreate + reload
       - on resume from background: banner is recreated (SDK kills WebView banners on pause)
       - banner is detached from the WebView container and re-attached to the activity
         content view, so no CSS/layout state inside the game can remove it
       Implementation notes (Unity Ads 4.22.0 official API):
       - BannerView.load() can be called again after onBannerFailedToLoad (no dedicated reload API)
       - no-fill arrives through onBannerFailedToLoad with BannerErrorInfo
       - BannerView must be attached to the Activity content view (android.R.id.content)     */

    private void scheduleBannerRetry(long delayMs) {
        try {
            bannerHandler.postDelayed(new Runnable() {
                public void run() { attachBanner(); }
            }, Math.min(delayMs, 60000L));
        } catch (Exception ignored) { }
    }

    /** Must run on the UI thread. Creates the banner view if missing and (re)loads.
        Safe to call any number of times: skips work while a ready banner is live. */
    private void attachBanner() {
        try {
            if (!bannerWanted) return;
            final Activity a = bridge != null ? bridge.getActivity() : null;
            if (a == null || a.isFinishing()) { scheduleBannerRetry(5000); return; }
            if (!UnityAds.isInitialized()) {
                stat("Banner", "waiting: SDK not initialized (queued, will auto-show)");
                scheduleBannerRetry(5000);
                return;
            }
            if (banner == null) {
                bannerTries = 0;
                bannerReady = false;
                createBanner(a);
            }
            if (!bannerReady) {
                try { banner.load(); }
                catch (Exception e) { scheduleBannerRetry(10000); }
            }
        } catch (Exception e) {
            scheduleBannerRetry(10000);
        }
    }

    private void createBanner(final Activity a) {
        BannerView b = new BannerView(a, bannerPlacement, new UnityBannerSize(320, 50));
        b.setListener(new BannerView.IListener() {
            @Override
            public void onBannerLoaded(BannerView bannerAdView) {
                bannerReady = true;
                bannerTries = 0;
                stat("Banner", "ready");
            }

            @Override
            public void onBannerShown(BannerView bannerAdView) {
                bannerReady = true;
                stat("Banner", "visible");
            }

            @Override
            public void onBannerClick(BannerView bannerAdView) { }

            @Override
            public void onBannerFailedToLoad(BannerView bannerAdView, BannerErrorInfo errorInfo) {
                // NEVER give up: schedule the next load attempt, forever (10s floor).
                bannerReady = false;
                try {
                    stat("Banner", "load failed (retry " + (++bannerTries) + " in 10s): "
                            + (errorInfo != null && errorInfo.errorMessage != null
                                ? errorInfo.errorMessage : "unknown"));
                } catch (Exception ignored2) { }
                scheduleBannerRetry(10000);
            }

            @Override
            public void onBannerLeftApplication(BannerView bannerAdView) { }
        });
        banner = b;
        try {
            ViewGroup root = a.findViewById(android.R.id.content);
            root.addView(b, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
            stat("Banner", "created " + bannerPlacement);
        } catch (Exception e) {
            stat("Banner", "attach failed (will retry)");
            scheduleBannerRetry(10000);
        }
    }

    /** KeepAlive tick: runs every 60s. Recreates the banner if the SDK or the view
        killed it (banner == null, not attached, or not ready for too long).
        A banner that is ready+visible is left alone so we never restart a live one. */
    private void bannerKeepAliveTick() {
        if (!bannerWanted) return;
        final Activity a = bridge != null ? bridge.getActivity() : null;
        if (a == null || a.isFinishing()) return;
        if (!resumed) return;
        if (!UnityAds.isInitialized()) return;
        boolean attached = false;
        try {
            attached = banner != null
                    && banner.getParent() instanceof ViewGroup
                    && ((ViewGroup) banner.getParent()).indexOfChild(banner) >= 0;
        } catch (Exception ignored) { }
        if (banner == null || !attached) {
            stat("Banner", "keepalive: recreate (detached or missing)");
            destroyBannerQuietly();
            createBanner(a);
            try { banner.load(); } catch (Exception ignored) { }
            return;
        }
        // Banner exists but never became ready within the last cycle → force a reload.
        if (!bannerReady) {
            stat("Banner", "keepalive: reload (not ready)");
            try { banner.load(); } catch (Exception ignored) { }
        }
    }

    private void destroyBannerQuietly() {
        try {
            if (banner != null) {
                if (banner.getParent() instanceof ViewGroup)
                    ((ViewGroup) banner.getParent()).removeView(banner);
                banner.destroy();
            }
        } catch (Exception ignored) { }
        banner = null;
        bannerReady = false;
    }

    /** Starts the KeepAlive watchdog + activity lifecycle observer (idempotent). */
    private void startKeepAlive() {
        if (lifecycle != null) return;
        try {
            final Application app = (Application) getContext().getApplicationContext();
            lifecycle = new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }
                @Override public void onActivityStarted(Activity activity) { }
                @Override public void onActivityResumed(Activity activity) {
                    if (activity == (bridge != null ? bridge.getActivity() : null)) {
                        resumed = true;
                        // Recreate the banner when coming back to the foreground:
                        // the SDK may have torn down its WebView banner while paused.
                        if (bannerWanted) {
                            stat("Banner", "resume: recreate");
                            destroyBannerQuietly();
                            scheduleBannerRetry(1200);
                        }
                    }
                }
                @Override public void onActivityPaused(Activity activity) {
                    if (activity == (bridge != null ? bridge.getActivity() : null)) resumed = false;
                }
                @Override public void onActivityStopped(Activity activity) { }
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
                @Override public void onActivityDestroyed(Activity activity) { }
            };
            app.registerActivityLifecycleCallbacks(lifecycle);
        } catch (Exception ignored) { }
        try {
            bannerHandler.removeCallbacks(keepAlive);
            bannerHandler.postDelayed(keepAlive, KEEPALIVE_MS);
        } catch (Exception ignored) { }
    }

    @PluginMethod
    public void showBanner(PluginCall call) {
        final String pl = call.getString("placement", "Banner_Android");
        bannerWanted = true;
        bannerPlacement = pl;
        startKeepAlive();
        call.resolve();
        if (!UnityAds.isInitialized()) {
            stat("Banner", "queued: will show as soon as SDK initializes");
            scheduleBannerRetry(3000);
            return;
        }
        ui(new Runnable() { public void run() { attachBanner(); } });
    }

    @PluginMethod
    public void hideBanner(PluginCall call) {
        bannerWanted = false;
        ui(new Runnable() {
            public void run() { destroyBannerQuietly(); }
        });
        call.resolve();
    }
}
