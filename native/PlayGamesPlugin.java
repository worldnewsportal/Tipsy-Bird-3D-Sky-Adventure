package com.tipsybird.game;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import com.google.android.gms.games.AuthenticationResult;
import com.google.android.gms.games.GamesSignInClient;
import com.google.android.gms.games.PlayGames;
import com.google.android.gms.games.SnapshotsClient;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotContents;
import com.google.android.gms.games.snapshot.SnapshotMetadata;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.concurrent.TimeUnit;

/**
 * Google Play Games cloud save (Saved Games / Snapshots API, Play Games Services v2).
 *
 * Design rules (learned the hard way in this project):
 *  - NEVER crash, NEVER hang the JS side: every path resolves the call.
 *  - If the Play Games App ID is not configured (placeholder) the plugin reports
 *    configured=false and does nothing — the game continues 100% offline.
 *  - Any failure (no account, sideloaded build not linked in Play Console,
 *    network error) resolves with ok=false and short error text — silent for players,
 *    visible in the in-game developer tools.
 *  - Conflict policy: RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED on open(); the JS layer
 *    compares cloud ts vs St.d.cloud for last-writer-wins safety.
 */
@CapacitorPlugin(name = "PlayGames")
public class PlayGamesPlugin extends Plugin {

    private static final String SAVE_FILE = "TipsyBirdSave3D";
    private static final int MAX = 524288; // 512 KB — save is ~2–6 KB
    private Boolean configured;

    private boolean configured() {
        if (configured != null) return configured.booleanValue();
        configured = Boolean.FALSE;
        try {
            Context ctx = getContext();
            Bundle md = ctx.getPackageManager()
                    .getApplicationInfo(ctx.getPackageName(), PackageManager.GET_META_DATA)
                    .metaData;
            String v = md == null ? null : md.getString("com.google.android.gms.games.APP_ID");
            configured = Boolean.valueOf(v != null && v.matches("\\d{9,14}"));
        } catch (Exception ignored) { }
        return configured.booleanValue();
    }

    private void err(JSObject r, Exception e) {
        String m = e == null ? "unknown" : String.valueOf(e.getMessage());
        if (m == null || m.isEmpty()) m = e == null ? "unknown" : e.getClass().getSimpleName();
        if (m.length() > 140) m = m.substring(0, 140);
        r.put("error", m);
    }

    @PluginMethod
    public void init(final PluginCall call) {
        JSObject r = new JSObject();
        r.put("configured", false);
        r.put("signedIn", false);
        if (getActivity() == null) { call.resolve(r); return; }
        if (!configured()) { call.resolve(r); return; }
        try {
            GamesSignInClient c = PlayGames.getGamesSignInClient(getActivity());
            Task<AuthenticationResult> t = c.isAuthenticated();
            AuthenticationResult res = Tasks.await(t, 15, TimeUnit.SECONDS);
            boolean ok = res != null && res.isAuthenticated();
            r.put("configured", true);
            r.put("signedIn", ok);
        } catch (Exception e) {
            r.put("configured", true);
            r.put("signedIn", false);
            err(r, e);
        }
        call.resolve(r);
    }

    /** Explicit sign-in attempt (v2 keeps it automatic most of the time; this is the fallback with UI). */
    @PluginMethod
    public void signIn(final PluginCall call) {
        JSObject r = new JSObject();
        r.put("configured", false);
        r.put("signedIn", false);
        if (getActivity() == null) { call.resolve(r); return; }
        if (!configured()) { call.resolve(r); return; }
        try {
            GamesSignInClient c = PlayGames.getGamesSignInClient(getActivity());
            AuthenticationResult res = Tasks.await(c.signIn(), 30, TimeUnit.SECONDS);
            boolean ok = res != null && res.isAuthenticated();
            r.put("configured", true);
            r.put("signedIn", ok);
        } catch (Exception e) {
            r.put("configured", true);
            r.put("signedIn", false);
            err(r, e);
        }
        call.resolve(r);
    }

    @PluginMethod
    public void save(final PluginCall call) {
        JSObject r = new JSObject();
        r.put("configured", false);
        r.put("ok", false);
        if (getActivity() == null) { call.resolve(r); return; }
        if (!configured()) { call.resolve(r); return; }
        String data = call.getString("data", "");
        if (data == null || data.isEmpty() || data.length() > MAX) { r.put("configured", true); call.resolve(r); return; }
        try {
            SnapshotsClient sc = PlayGames.getSnapshotsClient(getActivity());
            Snapshot snap = Tasks.await(sc.open(SAVE_FILE, true,
                    SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED), 20, TimeUnit.SECONDS);
            SnapshotContents contents = snap.getSnapshotContents();
            contents.writeBytes(data.getBytes("UTF-8"));
            SnapshotMetadataChange ch = new SnapshotMetadataChange.Builder()
                    .setDescription("Tipsy Bird 3D progress")
                    .build();
            SnapshotMetadata meta = Tasks.await(sc.commitAndClose(snap, ch), 20, TimeUnit.SECONDS);
            r.put("configured", true);
            r.put("ok", true);
            long ts = 0;
            try { ts = meta.getLastModifiedTimestamp(); } catch (Exception ignored2) { }
            r.put("ts", ts);
        } catch (Exception e) {
            r.put("configured", true);
            r.put("ok", false);
            err(r, e);
        }
        call.resolve(r);
    }

    @PluginMethod
    public void load(final PluginCall call) {
        JSObject r = new JSObject();
        r.put("configured", false);
        r.put("empty", true);
        if (getActivity() == null) { call.resolve(r); return; }
        if (!configured()) { call.resolve(r); return; }
        try {
            SnapshotsClient sc = PlayGames.getSnapshotsClient(getActivity());
            Snapshot snap = Tasks.await(sc.open(SAVE_FILE, false,
                    SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED), 20, TimeUnit.SECONDS);
            SnapshotContents contents = snap.getSnapshotContents();
            byte[] b = contents.readFully();
            String data = b == null ? "" : new String(b, "UTF-8");
            long ts = 0;
            try { ts = snap.getMetadata() != null ? snap.getMetadata().getLastModifiedTimestamp() : 0; } catch (Exception ignored2) { }
            r.put("configured", true);
            r.put("ts", ts);
            if (data == null || data.isEmpty() || data.length() > MAX) {
                r.put("empty", true);
            } else {
                r.put("empty", false);
                r.put("data", data);
            }
        } catch (Exception e) {
            // file not found, signed out, network error — all "empty" for the game
            r.put("configured", true);
            r.put("empty", true);
            err(r, e);
        }
        call.resolve(r);
    }
}
