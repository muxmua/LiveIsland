package com.harmonyos.liveIsland;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class NavReporter {
    static final String ACTION = "com.harmonyos.liveIsland.NAV_STATE";

    private static final Map<String, String> sTexts = new LinkedHashMap<>();
    private static final Map<String, String> sIcons = new LinkedHashMap<>();

    private static String sPkg = "";
    private static Handler sH;
    private static volatile boolean sNav;
    private static boolean sInstalled;
    private static volatile Context sCtx;
    private static volatile Bitmap sIconBmp;
    private static volatile int sIconRes;
    private static volatile String sIconName = "";
    private static volatile String sIconResName = "";
    private static volatile String sIconAction = "";
    private static volatile boolean sDirty;
    private static volatile java.lang.ref.WeakReference<View> sTurnView;

    static void install(ClassLoader cl, String pkg) {
        if (sInstalled) return;
        sInstalled = true;
        sPkg = pkg;
        sH = new Handler(Looper.getMainLooper());

        hookText();
        hookImage();
        hookForeground();
        hookContext();

        sH.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    flush();
                } catch (Throwable t) {
                    log("flush: " + t);
                }
                sH.postDelayed(this, 1000);
            }
        }, 1200);
        log("nav reporter installed: " + pkg);
    }

    private static void hookContext() {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof Context) {
                        sCtx = (Context) param.thisObject;
                    }
                }
            });
        } catch (Throwable t) {
            log("hook context: " + t);
        }
    }

    private static void hookText() {
        try {
            XposedHelpers.findAndHookMethod(TextView.class, "setText", CharSequence.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            onText((View) param.thisObject, (CharSequence) param.args[0]);
                        }
                    });
        } catch (Throwable t) {
            log("hook setText: " + t);
        }
        try {
            XposedHelpers.findAndHookMethod(TextView.class, "setText", CharSequence.class,
                    TextView.BufferType.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            onText((View) param.thisObject, (CharSequence) param.args[0]);
                        }
                    });
        } catch (Throwable t) {
            log("hook setText2: " + t);
        }
    }

    private static void hookImage() {
        try {
            XposedHelpers.findAndHookMethod(ImageView.class, "setImageResource", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            onIconRes((View) param.thisObject, (Integer) param.args[0]);
                        }
                    });
        } catch (Throwable t) {
            log("hook setImageResource: " + t);
        }
        try {
            XposedHelpers.findAndHookMethod(ImageView.class, "setImageDrawable", Drawable.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            onIconDrawable((View) param.thisObject, (Drawable) param.args[0]);
                        }
                    });
        } catch (Throwable t) {
            log("hook setImageDrawable: " + t);
        }
        try {
            XposedHelpers.findAndHookMethod(ImageView.class, "setImageBitmap",
                    android.graphics.Bitmap.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            onIconBitmap((View) param.thisObject,
                                    (android.graphics.Bitmap) param.args[0]);
                        }
                    });
        } catch (Throwable t) {
            log("hook setImageBitmap: " + t);
        }
    }

    private static boolean isTurnView(String name) {
        if (name == null) return false;
        String n = name.toLowerCase();
        return n.contains("turn") || n.contains("maneuver") || n.contains("arrow")
                || (n.contains("guide") && n.contains("icon"))
                || (n.contains("navi") && n.contains("icon"));
    }

    private static void onIconDrawable(View v, Drawable d) {
        try {
            if (v == null || d == null) return;
            String name = nameOf(v);
            if (!isTurnView(name)) return;
            sTurnView = new java.lang.ref.WeakReference<>(v);
            Bitmap b = rasterize(d, 72);
            if (b != null) {
                sIconBmp = b;
                sIconName = name;
                String act = guessAction(b);
                if (act.length() > 0) sIconAction = act;
                sDirty = true;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void onIconBitmap(View v, Bitmap src) {
        try {
            if (v == null || src == null) return;
            String name = nameOf(v);
            if (!isTurnView(name)) return;
            sTurnView = new java.lang.ref.WeakReference<>(v);
            Bitmap b = Bitmap.createScaledBitmap(src, 72, 72, true);
            sIconBmp = b;
            sIconName = name;
            String act = guessAction(b);
            if (act.length() > 0) sIconAction = act;
            sDirty = true;
        } catch (Throwable ignored) {
        }
    }

    private static String guessAction(Bitmap src) {
        try {
            final int n = 48;
            Bitmap b = Bitmap.createScaledBitmap(src, n, n, true);
            long topSum = 0, botSum = 0;
            int topN = 0, botN = 0;
            int minX = n, maxX = -1, minY = n, maxY = -1;
            int[] row = new int[n];
            for (int y = 0; y < n; y++) {
                b.getPixels(row, 0, n, 0, y, n, 1);
                for (int x = 0; x < n; x++) {
                    if ((row[x] >>> 24) > 60) {
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                        if (y < n / 3) {
                            topSum += x;
                            topN++;
                        } else if (y > n * 2 / 3) {
                            botSum += x;
                            botN++;
                        }
                    }
                }
            }
            if (maxX < 0 || topN == 0 || botN == 0) return "";
            int span = Math.max(1, maxX - minX);
            int height = Math.max(1, maxY - minY);
            if (height < span / 3) return "";
            float dx = (topSum / (float) topN - botSum / (float) botN) / span;
            if (dx < -0.16f) return "左转";
            if (dx > 0.16f) return "右转";
            return "直行";
        } catch (Throwable t) {
            return "";
        }
    }

    private static Bitmap rasterize(Drawable d, int size) {
        try {
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, size, size);
            d.draw(c);
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void hookForeground() {
        try {
            XposedHelpers.findAndHookMethod(Service.class, "startForeground", int.class,
                    Notification.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            sNav = true;
                        }
                    });
        } catch (Throwable t) {
            log("hook startForeground: " + t);
        }
        try {
            XposedHelpers.findAndHookMethod(Service.class, "stopForeground", boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            sNav = false;
                        }
                    });
        } catch (Throwable t) {
            log("hook stopForeground: " + t);
        }
        try {
            XposedHelpers.findAndHookMethod(NotificationManager.class, "cancel", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            sNav = false;
                        }
                    });
        } catch (Throwable t) {
            log("hook cancel: " + t);
        }
    }

    private static void onText(View v, CharSequence cs) {
        try {
            if (v == null || v.getId() == View.NO_ID) return;
            String t = cs == null ? "" : cs.toString().trim();
            if (t.isEmpty() || t.length() > 64) return;
            String name = nameOf(v);
            if (name == null) return;
            synchronized (sTexts) {
                if (!t.equals(sTexts.get(name))) {
                    sTexts.put(name, t);
                    sDirty = true;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void onIconRes(View v, int resId) {
        try {
            if (v == null || v.getId() == View.NO_ID) return;
            String name = nameOf(v);
            if (name == null) return;
            synchronized (sIcons) {
                sIcons.put(name, String.valueOf(resId));
            }
            if (resId != 0 && isTurnView(name)) {
                if (resId != sIconRes) sDirty = true;
                sIconRes = resId;

                String entry = resName(v, resId);
                if (entry != null && entry.length() > 0) sIconResName = entry;
                if (sIconBmp == null) sIconName = name;
            }
        } catch (Throwable ignored) {
        }
    }

    private static String resName(View v, int resId) {
        try {
            return v.getResources().getResourceEntryName(resId);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String nameOf(View v) {
        try {
            return v.getResources().getResourceEntryName(v.getId());
        } catch (Throwable t) {
            return null;
        }
    }

    private static void flush() {
        Context ctx = sCtx;
        if (ctx == null) return;

        boolean alive = false;
        java.lang.ref.WeakReference<View> ref = sTurnView;
        View tv0 = ref == null ? null : ref.get();
        if (tv0 != null) {
            alive = tv0.isAttachedToWindow() && tv0.getVisibility() == View.VISIBLE;
            if (!alive) return;
        } else if (!sDirty) {
            return;
        }
        sDirty = false;
        ArrayList<String> tk = new ArrayList<>();
        ArrayList<String> tv = new ArrayList<>();
        synchronized (sTexts) {
            for (Map.Entry<String, String> e : sTexts.entrySet()) {
                tk.add(e.getKey());
                tv.add(e.getValue());
            }
        }
        ArrayList<String> ik = new ArrayList<>();
        ArrayList<String> iv = new ArrayList<>();
        synchronized (sIcons) {
            for (Map.Entry<String, String> e : sIcons.entrySet()) {
                ik.add(e.getKey());
                iv.add(e.getValue());
            }
        }
        if (tk.isEmpty() && ik.isEmpty()) return;
        Intent i = new Intent(ACTION);
        i.setPackage("com.android.systemui");
        i.putExtra("pkg", sPkg);
        i.putExtra("nav", sNav);
        i.putStringArrayListExtra("tk", tk);
        i.putStringArrayListExtra("tv", tv);
        i.putStringArrayListExtra("ik", ik);
        i.putStringArrayListExtra("iv", iv);
        Bitmap icon = sIconBmp;
        if (icon != null) i.putExtra("icon", icon);
        if (sIconRes != 0) i.putExtra("iconRes", sIconRes);
        if (sIconName != null) i.putExtra("iconName", sIconName);
        if (sIconResName != null) i.putExtra("iconResName", sIconResName);
        if (sIconAction != null) i.putExtra("action", sIconAction);
        try {
            ctx.sendBroadcast(i);
        } catch (Throwable t) {
            log("send: " + t);
        }
    }

    private static long sLastLog;

    private static void log(String msg) {
        long now = System.currentTimeMillis();
        if (now - sLastLog < 3000) return;
        sLastLog = now;
        IslandHook.log("NavReporter/" + sPkg + ": " + msg);
    }
}
