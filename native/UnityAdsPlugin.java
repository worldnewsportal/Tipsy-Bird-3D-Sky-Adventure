package com.tipsybird.game;

import android.app.Application;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
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
    private volatile boolean bannerWanted = false;
    private volatile boolean bannerReady = false;
    private volatile boolean bannerLoading = false;
    private volatile String bannerPlacement = "Banner_Android";
    private long bannerLoadStartedAt = 0L;
    private boolean lifecycleRegistered = false;
    private static final long BANNER_RETRY_MS = 15000L;
    private static final long BANNER_CHECK_INTERVAL_MS = 45000L;
    private static final long BANNER_LOAD_TIMEOUT_MS = 45000L;
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private final Runnable bannerRetryTask = new Runnable() {
        @Override public void run() { attachBanner(); }
    };

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

    /* The banner is a single native overlay above the WebView, not part of any
       game page. It stays requested through page changes and app backgrounding. */
    private void scheduleBannerRetry(long delayMs) {
        try {
            bannerHandler.removeCallbacks(bannerRetryTask);
            bannerHandler.postDelayed(bannerRetryTask, Math.max(0L, delayMs));
        } catch (Exception ignored) { }
    }

    private void registerBannerLifecycle() {
        if (lifecycleRegistered) return;
        try {
            Application app = (Application) getContext().getApplicationContext();
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity activity, Bundle state) { }
                @Override public void onActivityStarted(Activity activity) { }
                @Override public void onActivityResumed(Activity activity) {
                    Activity current = bridge != null ? bridge.getActivity() : null;
                    if (bannerWanted && activity == current) scheduleBannerRetry(800L);
                }
                @Override public void onActivityPaused(Activity activity) { }
                @Override public void onActivityStopped(Activity activity) { }
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
                @Override public void onActivityDestroyed(Activity activity) { }
            });
            lifecycleRegistered = true;
        } catch (Exception e) {
            stat("Banner", "lifecycle monitor unavailable");
        }
    }

    private void destroyBannerView() {
        try {
            if (banner != null) {
                if (banner.getParent() instanceof ViewGroup) {
                    ((ViewGroup) banner.getParent()).removeView(banner);
                }
                banner.destroy();
            }
        } catch (Exception ignored) { }
        banner = null;
        bannerReady = false;
        bannerLoading = false;
        bannerLoadStartedAt = 0L;
    }

    private void showBannerView(Activity activity, ViewGroup root) {
        try {
            if (banner == null || banner.getParent() != root) return;
            banner.setVisibility(View.VISIBLE);
            banner.setAlpha(1f);
            banner.setElevation(8f * activity.getResources().getDisplayMetrics().density);
            banner.bringToFront();
            root.bringChildToFront(banner);
            root.requestLayout();
            root.invalidate();
        } catch (Exception ignored) { }
    }

    /** Runs only on the UI thread. Reattach if Android recreated the activity,
        keep the view above every game page, and retry no-fill/timeout responses. */
    private void attachBanner() {
        try {
            if (!bannerWanted) return;
            final Activity activity = bridge != null ? bridge.getActivity() : null;
            if (activity == null || activity.isFinishing()) {
                scheduleBannerRetry(5000L);
                return;
            }
            if (!UnityAds.isInitialized()) {
                stat("Banner", "waiting: SDK not initialized; request queued");
                scheduleBannerRetry(5000L);
                return;
            }
            final ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) {
                stat("Banner", "waiting: Android content view unavailable");
                scheduleBannerRetry(3000L);
                return;
            }
            if (banner != null && banner.getParent() != root) {
                destroyBannerView();
                stat("Banner", "reattaching after activity view change");
            }
            if (banner == null) {
                bannerTries = 0;
                BannerView view = new BannerView(activity, bannerPlacement, new UnityBannerSize(320, 50));
                view.setVisibility(View.VISIBLE);
                view.setListener(new BannerView.IListener() {
                    @Override public void onBannerLoaded(final BannerView bannerAdView) {
                        ui(new Runnable() {
                            @Override public void run() {
                                if (banner != bannerAdView) return;
                                bannerLoading = false;
                                bannerReady = true;
                                bannerTries = 0;
                                Activity current = bridge != null ? bridge.getActivity() : null;
                                if (current != null) {
                                    ViewGroup currentRoot = current.findViewById(android.R.id.content);
                                    if (currentRoot != null) showBannerView(current, currentRoot);
                                }
                                stat("Banner", "ready");
                                scheduleBannerRetry(BANNER_CHECK_INTERVAL_MS);
                            }
                        });
                    }

                    @Override public void onBannerShown(final BannerView bannerAdView) {
                        ui(new Runnable() {
                            @Override public void run() {
                                if (banner != bannerAdView) return;
                                bannerLoading = false;
                                bannerReady = true;
                                Activity current = bridge != null ? bridge.getActivity() : null;
                                if (current != null) {
                                    ViewGroup currentRoot = current.findViewById(android.R.id.content);
                                    if (currentRoot != null) showBannerView(current, currentRoot);
                                }
                                stat("Banner", "visible");
                                scheduleBannerRetry(BANNER_CHECK_INTERVAL_MS);
                            }
                        });
                    }

                    @Override public void onBannerClick(BannerView bannerAdView) { }

                    @Override public void onBannerFailedToLoad(final BannerView bannerAdView,
                                                                final BannerErrorInfo errorInfo) {
                        ui(new Runnable() {
                            @Override public void run() {
                                if (banner != bannerAdView) return;
                                bannerLoading = false;
                                bannerReady = false;
                                String message = errorInfo != null && errorInfo.errorMessage != null
                                        ? errorInfo.errorMessage : "unknown";
                                stat("Banner", "load failed (retry " + (++bannerTries) + "): " + message);
                                scheduleBannerRetry(BANNER_RETRY_MS);
                            }
                        });
                    }

                    @Override public void onBannerLeftApplication(BannerView bannerAdView) { }
                });
                banner = view;
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
                stat("Banner", "created " + bannerPlacement);
            }

            showBannerView(activity, root);
            long now = SystemClock.elapsedRealtime();
            if (bannerLoading && now - bannerLoadStartedAt >= BANNER_LOAD_TIMEOUT_MS) {
                bannerLoading = false;
                bannerReady = false;
                stat("Banner", "load timed out; retrying");
            }
            if (!bannerReady && !bannerLoading) {
                bannerLoading = true;
                bannerLoadStartedAt = now;
                try {
                    banner.load(new UnityAdsLoadOptions());
                    stat("Banner", "load requested");
                } catch (Exception e) {
                    bannerLoading = false;
                    stat("Banner", "load call failed; retrying");
                    scheduleBannerRetry(BANNER_RETRY_MS);
                    return;
                }
            }
            scheduleBannerRetry(BANNER_CHECK_INTERVAL_MS);
        } catch (Exception e) {
            stat("Banner", "attach failed; retrying");
            scheduleBannerRetry(BANNER_RETRY_MS);
        }
    }

    @PluginMethod
    public void showBanner(final PluginCall call) {
        final String placement = call.getString("placement", "Banner_Android");
        bannerWanted = true;
        bannerPlacement = placement;
        call.resolve();
        ui(new Runnable() {
            @Override public void run() {
                registerBannerLifecycle();
                attachBanner();
            }
        });
    }

    @PluginMethod
    public void hideBanner(PluginCall call) {
        // Kept as a compatibility no-op: this app's banner is persistent.
        bannerWanted = true;
        call.resolve();
        ui(new Runnable() {
            @Override public void run() {
                registerBannerLifecycle();
                attachBanner();
            }
        });
    }
}
