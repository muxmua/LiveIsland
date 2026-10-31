package com.harmonyos.liveIsland;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;

public class IslandPrefsProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        MatrixCursor c = new MatrixCursor(new String[]{"key", "value"});
        Context ctx = getContext();
        if (ctx == null) return c;
        try {
            SharedPreferences sp = ctx.getSharedPreferences("liveisland",
                    Context.MODE_PRIVATE);
            Bundle def = IslandPrefs.defaults();
            for (String k : def.keySet()) {
                Object d = def.get(k);
                String defV = (d instanceof Boolean)
                        ? (((Boolean) d) ? "1" : "0") : String.valueOf(d);
                String v = sp.getString(k, defV);
                c.addRow(new Object[]{k, v == null ? defV : v});
            }
            for (String k : sp.getAll().keySet()) {
                if (def.containsKey(k)) continue;
                Object o = sp.getAll().get(k);
                if (o != null) c.addRow(new Object[]{k, String.valueOf(o)});
            }
        } catch (Throwable t) {
            android.util.Log.i("LiveIsland", "prefs query failed: " + t);
        }
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/vnd." + IslandPrefs.AUTHORITY + ".prefs";
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if ("reload".equals(method)) return null;
        return super.call(method, arg, extras);
    }
}
