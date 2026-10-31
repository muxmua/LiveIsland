package de.robv.android.xposed;

import java.lang.reflect.Member;

/** 编译期桩：运行时由 LSPosed 注入的真实类替换，签名需与 XposedAPI 一致。 */
public abstract class XC_MethodHook {
    public XC_MethodHook() {}
    public XC_MethodHook(int priority) {}

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;

        public Object getResult() { return null; }
        public void setResult(Object result) {}
        public Throwable getThrowable() { return null; }
        public void setThrowable(Throwable throwable) {}
        public boolean hasThrowable() { return false; }
    }

    public static class Unhook {
        public Member getHookedMethod() { return null; }
    }
}
