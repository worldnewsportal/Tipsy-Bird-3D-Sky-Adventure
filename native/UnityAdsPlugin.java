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

    private void ui(Runnable r) {
        if (bridge == null) return;
        Activity a = bridge.getActivity();
        if (a == null || a.isFinishing()) return;
        a.runOnUiThread(r);
    }

    private IUnityAdsShowListener showListener(final PluginCall call, final boolean rewarded) {
        return new IUnityAdsShowListener() {
            @Override
            public void onUnityAdsShowComplete(String placement, UnityAds.UnityAdsShowCompletionState state) {
                try {
                    JSObject r = new JSObject();
                    r.put(rewarded ? "rewarded" : "shown",
                            state == UnityAds.UnityAdsShowCompletionState.COMPLETED);
                    call.resolve(r);
                } catch (Exception e) { }
            }

            @Override
            public void onUnityAdsShowFailure(String placement, UnityAds.UnityAdsShowError error, String message) {
                try {
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
                            } catch (Exception e) { }
                        }

                        @Override
                        public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String message) {
                            try {
                                JSObject r = new JSObject();
                                r.put("failed", true);
                                call.resolve(r);
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

    @PluginMethod
    public void showBanner(PluginCall call) {
        final String pl = call.getString("placement", "Banner_Android");
        if (bridge == null || bridge.getActivity() == null) { call.resolve(); return; }
        bannerTries = 0;
        call.setKeepAlive(true);
        ui(new Runnable() {
            public void run() {
                try {
                    if (banner != null) { try { banner.destroy(); } catch (Exception e) { } banner = null; }
                    Activity a = bridge.getActivity();
                    if (a == null || a.isFinishing()) { call.resolve(); return; }
                    BannerView b = new BannerView(a, pl, new UnityBannerSize(320, 50));
                    b.setListener(new BannerView.IListener() {
                        @Override
                        public void onBannerLoaded(BannerView bannerAdView) { }

                        @Override
                        public void onBannerShown(BannerView bannerAdView) { }

                        @Override
                        public void onBannerClick(BannerView bannerAdView) { }

                        @Override
                        public void onBannerFailedToLoad(BannerView bannerAdView, BannerErrorInfo errorInfo) {
                            try {
                                if (bannerTries++ < 3) {
                                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                        public void run() {
                                            try {
                                                if (banner != null) banner.load(new UnityAdsLoadOptions());
                                            } catch (Exception ignored) { }
                                        }
                                    }, 5000);
                                }
                            } catch (Exception e) { }
                        }

                        @Override
                        public void onBannerLeftApplication(BannerView bannerAdView) { }
                    });
                    ViewGroup root = a.findViewById(android.R.id.content);
                    root.addView(b, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
                    banner = b;
                    b.load(new UnityAdsLoadOptions());
                } catch (Exception e) { }
                call.resolve();
            }
        });
    }

    @PluginMethod
    public void hideBanner(PluginCall call) {
        ui(new Runnable() {
            public void run() {
                try {
                    if (banner != null) { banner.destroy(); banner = null; }
                } catch (Exception e) { }
            }
        });
        call.resolve();
    }
}
