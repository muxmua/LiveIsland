package com.harmonyos.liveIsland;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class StopwatchReporter {
    static final String ACTION = "com.harmonyos.liveIsland.STOPWATCH_STATE";
    static final String CMD_ACTION = "com.harmonyos.liveIsland.STOPWATCH_CMD";
    private static final String SERVICE = "com.android.deskclock.stopwatch.StopwatchService";

    private static boolean sInstalled;
    private static Handler sH;
    private static volatile Context sCtx;

    private static volatile long sElapsed;
    private static volatile long sLastTextAt;
    private static volatile long sLastSent;
    private static volatile boolean sPaused = true;
    private static volatile boolean sShown;
    private static volatile TextView sTimeView;
    private static volatile boolean sRxRegistered;

    static void install(ClassLoader cl) {
        if (sInstalled) return;
        sInstalled = true;
        sH = new Handler(Looper.getMainLooper());
        try {
            Class<?> svc = XposedHelpers.findClass(SERVICE, cl);
            IslandHook.log("stopwatch service found: " + svc);
            hookService(svc);
            hookText();
            sH.postDelayed(new Runnable() {
                @Override
                public void run() {
                    tick();
                    sH.postDelayed(this, 500);
                }
            }, 500);
        } catch (Throwable t) {
            IslandHook.log("stopwatch install failed: " + t);
        }
    }

    private static void tick() {
        try {
            if (!sShown) return;
            long now = SystemClock.elapsedRealtime();
            if (!sPaused) {
                boolean fg = isForeground();
                if (fg && now - sLastTextAt > 1400) {
                    sPaused = true;
                    if (sElapsed <= 0) {
                        sShown = false;
                        send(0, true, false);
                    } else {
                        send(sElapsed, true, true);
                    }
                    return;
                }
                long elapsed = sElapsed + Math.max(0, now - sLastTextAt);
                if (now - sLastSent >= 900) send(elapsed, false, true);
            } else {
                if (sElapsed <= 0) {
                    sShown = false;
                    send(0, true, false);
                } else if (now - sLastSent >= 2000) {
                    send(sElapsed, true, true);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isForeground() {
        try {
            ActivityManager.RunningAppProcessInfo info =
                    new ActivityManager.RunningAppProcessInfo();
            ActivityManager.getMyMemoryState(info);
            return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
        } catch (Throwable t) {
            return true;
        }
    }

    private static void onTime(String text, Context ctx) {
        long ms = parse(text);
        if (ms < 0) return;
        sCtx = ctx;
        sElapsed = ms;
        sLastTextAt = SystemClock.elapsedRealtime();
        boolean wasPaused = sPaused;
        sPaused = false;
        sShown = true;
        registerCmd(ctx);
        long now = sLastTextAt;
        if (wasPaused || now - sLastSent >= 450) {
            sLastSent = now;
            send(ms, false, true);
        }
    }

    private static void registerCmd(Context ctx) {
        if (sRxRegistered) return;
        sRxRegistered = true;
        try {
            ctx.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final String cmd = intent.getStringExtra("cmd");
                    sH.post(new Runnable() {
                        @Override
                        public void run() {
                            handleCmd(cmd);
                        }
                    });
                }
            }, new IntentFilter(CMD_ACTION));
        } catch (Throwable t) {
            IslandHook.log("stopwatch cmd rx: " + t);
        }
    }

    private static void handleCmd(String cmd) {
        try {
            TextView tv = sTimeView;
            View root = tv == null ? null : tv.getRootView();
            if (root == null) {
                IslandHook.log("stopwatch cmd no root: " + cmd);
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if ("reset".equals(cmd)) {
                sElapsed = 0;
                sPaused = true;
                sShown = false;
                send(0, true, false);
                click(root, "stopwatch_reset_tv_btn");
            } else {
                if (sPaused) {
                    sPaused = false;
                    sLastTextAt = now;
                    sShown = true;
                    send(sElapsed, false, true);
                } else {
                    sElapsed = sElapsed + Math.max(0, now - sLastTextAt);
                    sPaused = true;
                    send(sElapsed, true, true);
                }
                if (!click(root, "stopwatch_stop_tv_btn")) {
                    click(root, "stopwatch_start_tv_btn");
                }
            }
        } catch (Throwable t) {
            IslandHook.log("stopwatch cmd failed: " + t);
        }
    }

    private static boolean click(View root, String name) {
        try {
            int id = root.getResources().getIdentifier(name, "id",
                    root.getContext().getPackageName());
            if (id == 0) return false;
            View v = root.findViewById(id);
            if (v == null || v.getVisibility() != View.VISIBLE) return false;
            boolean ok = v.performClick();
            if (!ok) {
                try {
                    v.callOnClick();
                    ok = true;
                } catch (Throwable ignored) {
                }
            }
            if (!ok) {
                View p = v.getParent() instanceof View ? (View) v.getParent() : null;
                if (p != null) ok = p.performClick();
            }
            IslandHook.log("stopwatch click " + name + " ok=" + ok
                    + " clickable=" + v.isClickable());
            return ok;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void send(long elapsed, boolean paused, boolean visible) {
        Context ctx = sCtx;
        if (ctx == null) return;
        sLastSent = SystemClock.elapsedRealtime();
        try {
            Intent i = new Intent(ACTION);
            i.setPackage("com.android.systemui");
            i.putExtra("visible", visible);
            i.putExtra("elapsed", Math.max(0, elapsed));
            i.putExtra("paused", paused);
            ctx.sendBroadcast(i);
        } catch (Throwable t) {
            IslandHook.log("stopwatch send: " + t);
        }
    }

    private static long parse(String s) {
        try {
            String[] p = s.split(":");
            if (p.length == 3) {
                long h = Long.parseLong(p[0]);
                long m = Long.parseLong(p[1]);
                double sec = Double.parseDouble(p[2]);
                return h * 3600000L + m * 60000L + Math.round(sec * 1000);
            }
            if (p.length == 2) {
                long m = Long.parseLong(p[0]);
                double sec = Double.parseDouble(p[1]);
                return m * 60000L + Math.round(sec * 1000);
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static void hookText() {
        try {
            XposedHelpers.findAndHookMethod(TextView.class, "setText", CharSequence.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                CharSequence cs = (CharSequence) param.args[0];
                                if (cs == null) return;
                                String s = cs.toString().trim();
                                if (!s.matches("\\d+:\\d{2}\\.\\d{2}")) return;
                                TextView v = (TextView) param.thisObject;
                                String id = "";
                                try {
                                    id = v.getResources().getResourceName(v.getId());
                                } catch (Throwable ignored) {
                                }
                                if (!id.endsWith("current_stopwatch_time")) return;
                                sTimeView = v;
                                onTime(s, v.getContext().getApplicationContext());
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable t) {
            IslandHook.log("stopwatch text hook failed: " + t);
        }
    }

    private static void hookService(Class<?> cls) {
        try {
            XposedBridge.hookAllMethods(cls, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    IslandHook.log("stopwatch service created");
                }
            });
            XposedBridge.hookAllMethods(cls, "onDestroy", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    IslandHook.log("stopwatch service destroyed");
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
