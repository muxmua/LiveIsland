package com.harmonyos.liveIsland;

import android.app.Activity;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class IslandHook implements IXposedHookLoadPackage {
    static final String TAG = "LiveIsland";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final String SETTINGS = "com.android.settings";
    private static final String[] MAP_APPS = {
            "com.baidu.BaiduMap",
            "com.autonavi.minimap"
    };
    private static volatile boolean sStarted;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpp) {
        if (SYSTEMUI.equals(lpp.packageName)) {
            if (lpp.processName != null && !SYSTEMUI.equals(lpp.processName)) return;
            IslandController.hookPanel(lpp.classLoader);
            IslandController.hookPowerWarnings(lpp.classLoader);
            IslandController.hookNotifications(lpp.classLoader);
            hookSystemUi(lpp);
            return;
        }
        for (String pkg : MAP_APPS) {
            if (pkg.equals(lpp.packageName)) {
                NavReporter.install(lpp.classLoader, lpp.packageName);
                return;
            }
        }
        if ("com.huawei.deskclock".equals(lpp.packageName)) {
            StopwatchReporter.install(lpp.classLoader);
            return;
        }
        if (SETTINGS.equals(lpp.packageName)) {
            hookUsbChooser(lpp.classLoader);
        }
    }

    private void hookUsbChooser(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClass(
                    "com.android.settings.deviceinfo.UsbConnModeChooserActivity", cl);
            XposedBridge.hookAllMethods(cls, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (param.thisObject instanceof Activity) {
                            ((Activity) param.thisObject).finish();
                            log("suppressed usb chooser");
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            log("usb chooser hook installed");
        } catch (Throwable t) {
            log("usb chooser hook failed: " + t);
        }
    }

    private void hookSystemUi(XC_LoadPackage.LoadPackageParam lpp) {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    final Application app = (Application) param.thisObject;
                    if (app == null || sStarted) return;
                    sStarted = true;
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                new IslandController(app).start();
                            } catch (Throwable t) {
                                log("start failed: " + t);
                            }
                        }
                    }, 2500);
                }
            });
            log("hooked systemui Application.onCreate");
        } catch (Throwable t) {
            log("hook failed: " + t);
        }
    }

    static void log(String msg) {
        Log.i(TAG, msg);
        try {
            XposedBridge.log("[" + TAG + "] " + msg);
        } catch (Throwable ignored) {
        }
    }
}
