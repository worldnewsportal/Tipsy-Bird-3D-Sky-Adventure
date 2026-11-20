package com.tipsybird.game;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Mirrors the game save into SharedPreferences ("tipsy_backup").
 * SharedPreferences live in sharedprefs/ which Android Auto Backup
 * (android:allowBackup=true) sends to the Google device backup, so the
 * whole save is restored automatically after uninstall + reinstall.
 */
@CapacitorPlugin(name = "SaveBackup")
public class SavePlugin extends Plugin {

    private static final String PREFS = "tipsy_backup";
    private static final String KEY = "data";
    private static final int MAX = 262144;

    @PluginMethod
    public void backup(PluginCall call) {
        String data = call.getString("data", "");
        if (data == null || data.isEmpty() || data.length() > MAX) { call.resolve(); return; }
        try {
            SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            sp.edit().putString(KEY, data).putLong("t", System.currentTimeMillis()).apply();
        } catch (Exception ignored) { }
        call.resolve();
    }

    @PluginMethod
    public void restore(PluginCall call) {
        String data = "";
        long t = 0;
        try {
            SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            data = sp.getString(KEY, "");
            t = sp.getLong("t", 0);
        } catch (Exception ignored) { }
        if (data != null && !data.isEmpty()) {
            JSObject r = new JSObject();
            r.put("data", data);
            r.put("time", t);
            call.resolve(r);
        } else {
            JSObject r = new JSObject();
            r.put("empty", true);
            call.resolve(r);
        }
    }
}
