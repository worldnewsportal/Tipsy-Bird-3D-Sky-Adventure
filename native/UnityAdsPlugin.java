package com.tipsybird.game;

import android.app.Activity;
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

    private IUnityAdsShowListener showListener(final PluginCall call, final boolean rewarded) {
        return new IUnityAdsShowListener() {
            @Override
            public void onUnityAdsShowComplete(String placement, UnityAds.UnityAdsShowCompletionState state) {
                JSObject r = new JSObject();
                r.put(rewarded ? "rewarded" : "shown",
                        state == UnityAds.UnityAdsShowCompletionState.COMPLETED);
                call.resolve(r);
            }

            @Override
            public void onUnityAdsShowFailure(String placement, UnityAds.UnityAdsShowError error, String message) {
                JSObject r = new JSObject();
                r.put("failed", true);
                call.resolve(r);
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
                Activity a = bridge.getActivity();
                UnityAds.show(a, placement, new UnityAdsShowOptions(), showListener(call, rewarded));
            }

            @Override
            public void onUnityAdsFailedToLoad(String p, UnityAds.UnityAdsLoadError error, String message) {
                JSObject r = new JSObject();
                r.put("failed", true);
                call.resolve(r);
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
        UnityAds.initialize(getContext(), gameId, testMode, new IUnityAdsInitializationListener() {
            @Override
            public void onInitializationComplete() {
                JSObject r = new JSObject();
                r.put("initialized", true);
                call.resolve(r);
            }

            @Override
            public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String message) {
                call.resolve();
            }
        });
    }

    @PluginMethod
    public void showRewarded(PluginCall call) {
        if (!UnityAds.isInitialized()) { call.resolve(); return; }
        call.setKeepAlive(true);
        String pl = call.getString("placement", "Rewarded_Android");
        UnityAds.load(pl, loadListener(pl, true, call));
    }

    @PluginMethod
    public void showInterstitial(PluginCall call) {
        if (!UnityAds.isInitialized()) { call.resolve(); return; }
        call.setKeepAlive(true);
        String pl = call.getString("placement", "Interstitial_Android");
        UnityAds.load(pl, loadListener(pl, false, call));
    }

    @PluginMethod
    public void preload(PluginCall call) {
        if (!UnityAds.isInitialized()) { call.resolve(); return; }
        String type = call.getString("type", "rewarded");
        final String pl = "interstitial".equals(type)
                ? call.getString("placement", "Interstitial_Android")
                : call.getString("placement", "Rewarded_Android");
        call.setKeepAlive(true);
        UnityAds.load(pl, new IUnityAdsLoadListener() {
            @Override
            public void onUnityAdsAdLoaded(String p) {
                JSObject r = new JSObject();
                r.put("ready", true);
                call.resolve(r);
            }

            @Override
            public void onUnityAdsFailedToLoad(String p, UnityAds.UnityAdsLoadError error, String message) {
                call.resolve();
            }
        });
    }

    @PluginMethod
    public void showBanner(PluginCall call) {
        String pl = call.getString("placement", "Banner_Android");
        Activity a = bridge.getActivity();
        if (banner != null) { banner.destroy(); banner = null; }
        BannerView b = new BannerView(a, pl, new UnityBannerSize(320, 50));
        b.setListener(new BannerView.IListener() {
            @Override
            public void onBannerLoaded(BannerView bannerAdView) { }

            @Override
            public void onBannerShown(BannerView bannerAdView) { }

            @Override
            public void onBannerClick(BannerView bannerAdView) { }

            @Override
            public void onBannerFailedToLoad(BannerView bannerAdView, BannerErrorInfo errorInfo) { }

            @Override
            public void onBannerLeftApplication(BannerView bannerAdView) { }
        });
        ViewGroup root = a.findViewById(android.R.id.content);
        root.addView(b, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        banner = b;
        b.load(new UnityAdsLoadOptions());
        call.resolve();
    }

    @PluginMethod
    public void hideBanner(PluginCall call) {
        if (banner != null) { banner.destroy(); banner = null; }
        call.resolve();
    }
}
