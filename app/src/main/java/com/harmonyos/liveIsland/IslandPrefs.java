package com.harmonyos.liveIsland;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public final class IslandPrefs {
    public static final String AUTHORITY = "com.harmonyos.liveIsland.prefs";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY + "/prefs");
    public static final String ACTION_CHANGED = "com.harmonyos.liveIsland.PREFS_CHANGED";
    private static final String SP = "liveisland";

    public static final int BG_FROSTED = 0;
    public static final int BG_BLACK = 2;

    public static final String K_BG = "bg_mode";
    public static final String K_BG_FOLLOW = "bg_follow";
    public static final String K_FEAT_MEDIA = "feat_media";
    public static final String K_FEAT_NAV = "feat_nav";
    public static final String K_FEAT_CAPSULE = "feat_capsule";
    public static final String K_FEAT_STOPWATCH = "feat_stopwatch";
    public static final String K_FEAT_USB = "feat_usb";
    public static final String K_FEAT_CHARGE = "feat_charge";
    public static final String K_FEAT_FLASH = "feat_flash";
    public static final String K_FEAT_RECORD = "feat_record";

    public static final String VERSION_NAME = "v1.2.0";
    public static final String AUTHOR = "MuxMu";

    private static final Map<String, String> CACHE = new HashMap<String, String>();
    private static volatile boolean sLoaded;

    private IslandPrefs() {
    }

    static boolean isModuleProcess(Context ctx) {
        return ctx != null && "com.harmonyos.liveIsland".equals(ctx.getPackageName());
    }

    static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    public static void put(Context ctx, String key, String value) {
        if (ctx == null) return;
        if (!isModuleProcess(ctx)) return;
        try {
            sp(ctx).edit().putString(key, value).apply();
        } catch (Throwable t) {
            Log.i("LiveIsland", "prefs put failed: " + t);
        }
        try {
            Intent i = new Intent(ACTION_CHANGED);
            i.setPackage("com.android.systemui");
            ctx.sendBroadcast(i);
        } catch (Throwable ignored) {
        }
    }

    public static void putBool(Context ctx, String key, boolean v) {
        put(ctx, key, v ? "1" : "0");
    }

    public static void putInt(Context ctx, String key, int v) {
        put(ctx, key, String.valueOf(v));
    }

    public static boolean getBoolDirect(Context ctx, String key, boolean def) {
        try {
            String v = sp(ctx).getString(key, null);
            return v == null ? def : "1".equals(v);
        } catch (Throwable t) {
            return def;
        }
    }

    public static int getIntDirect(Context ctx, String key, int def) {
        try {
            String v = sp(ctx).getString(key, null);
            return v == null ? def : Integer.parseInt(v);
        } catch (Throwable t) {
            return def;
        }
    }

    public static synchronized void load(Context ctx) {
        if (ctx == null) return;
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(URI, null, null, null, null);
            if (c == null) return;
            int ik = c.getColumnIndex("key");
            int iv = c.getColumnIndex("value");
            if (ik < 0 || iv < 0) return;
            CACHE.clear();
            while (c.moveToNext()) {
                CACHE.put(c.getString(ik), c.getString(iv));
            }
            sLoaded = true;
        } catch (Throwable t) {
            Log.i("LiveIsland", "prefs load failed: " + t);
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static void watch(Context ctx, final Runnable onChange) {
        if (ctx == null || onChange == null) return;
        try {
            ctx.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    load(c);
                    onChange.run();
                }
            }, new IntentFilter(ACTION_CHANGED));
        } catch (Throwable t) {
            Log.i("LiveIsland", "prefs watch failed: " + t);
        }
    }

    public static String get(String key, String def) {
        String v = CACHE.get(key);
        return v == null ? def : v;
    }

    public static boolean getBool(String key, boolean def) {
        String v = CACHE.get(key);
        return v == null ? def : "1".equals(v);
    }

    public static int getInt(String key, int def) {
        try {
            String v = CACHE.get(key);
            return v == null ? def : Integer.parseInt(v);
        } catch (Throwable t) {
            return def;
        }
    }

    public static int bgMode() {
        int m = getInt(K_BG, BG_BLACK);
        if (m != BG_FROSTED && m != BG_BLACK) return BG_FROSTED;
        return m;
    }

    public static Bundle defaults() {
        Bundle b = new Bundle();
        b.putInt(K_BG, BG_BLACK);
        b.putBoolean(K_BG_FOLLOW, true);
        b.putBoolean(K_FEAT_MEDIA, true);
        b.putBoolean(K_FEAT_NAV, true);
        b.putBoolean(K_FEAT_CAPSULE, true);
        b.putBoolean(K_FEAT_STOPWATCH, true);
        b.putBoolean(K_FEAT_USB, true);
        b.putBoolean(K_FEAT_CHARGE, true);
        b.putBoolean(K_FEAT_FLASH, true);
        b.putBoolean(K_FEAT_RECORD, true);
        return b;
    }
}
