package com.tipsybird.game;

import android.app.Activity;
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
import com.unity3d.ads.UnityAdsLoadOptions;
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

    /* ---------------- Banner: never-give-up engine ----------------
       Guarantees (owner requirement: banner shows on game entry, never dies):
       - a request made before SDK init is QUEUED, never dropped
       - load failures retry FOREVER every 10s (no try-N/3 cutoff)
       - banner auto-shows right after SDK initialization completes  */

    private void scheduleBannerRetry(long delayMs) {
        try {
            bannerHandler.postDelayed(new Runnable() {
                public void run() { attachBanner(); }
            }, delayMs);
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
                BannerView b = new BannerView(a, bannerPlacement, new UnityBannerSize(320, 50));
                b.setListener(new BannerView.IListener() {
                    @Override
                    public void onBannerLoaded(BannerView bannerAdView) {
                        bannerReady = true;
                        stat("Banner", "ready");
                    }

                    @Override
                    public void onBannerShown(BannerView bannerAdView) { stat("Banner", "visible"); }

                    @Override
                    public void onBannerClick(BannerView bannerAdView) { }

                    @Override
                    public void onBannerFailedToLoad(BannerView bannerAdView, BannerErrorInfo errorInfo) {
                        // NEVER give up: schedule the next load attempt in 10s, forever.
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
                ViewGroup root = a.findViewById(android.R.id.content);
                root.addView(b, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
                stat("Banner", "created " + bannerPlacement);
            }
            if (!bannerReady) {
                try { banner.load(new UnityAdsLoadOptions()); }
                catch (Exception e) { scheduleBannerRetry(10000); }
            }
        } catch (Exception e) {
            scheduleBannerRetry(10000);
        }
    }

    @PluginMethod
    public void showBanner(PluginCall call) {
        final String pl = call.getString("placement", "Banner_Android");
        bannerWanted = true;
        bannerPlacement = pl;
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
            public void run() {
                try {
                    if (banner != null) { banner.destroy(); banner = null; }
                    bannerReady = false;
                } catch (Exception e) { }
            }
        });
        call.resolve();
    }
}
