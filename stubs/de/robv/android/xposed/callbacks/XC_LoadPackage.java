package de.robv.android.xposed.callbacks;

import de.robv.android.xposed.XC_MethodHook;

public abstract class XC_LoadPackage extends XC_MethodHook {
    public static class LoadPackageParam {
        public String packageName;
        public String processName;
        public ClassLoader classLoader;
        public boolean isFirstApplication;
        public android.content.pm.ApplicationInfo appInfo;
    }
}
