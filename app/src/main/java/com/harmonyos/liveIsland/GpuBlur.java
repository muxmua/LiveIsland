package com.harmonyos.liveIsland;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.view.Surface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

final class GpuBlur {

    private static boolean sInit;
    private static boolean sOk;

    private static Method sShot;
    private static Constructor<?> sNodeCtor;
    private static Class<?> sNodeCls;
    private static Class<?> sHrCls;
    private static Method sSetPosition;
    private static Method sSetLayerType;
    private static Method sSetBehindStyle;
    private static Method sSetBlurMode;
    private static Method sSetBehindEnabled;
    private static Method sSetScaleInput;
    private static Method sSetProgress;
    private static Method sBeginRecording;
    private static Method sEndRecording;
    private static Method sDiscard;
    private static Method sHrSetSurface;
    private static Method sHrSetContentRoot;
    private static Method sHrCreateRequest;
    private static Method sHrDestroy;

    private GpuBlur() {
    }

    static synchronized boolean available() {
        if (sInit) return sOk;
        sInit = true;
        try {
            Class<?> sc = Class.forName("android.view.SurfaceControl");
            for (Method m : sc.getDeclaredMethods()) {
                Class<?>[] ps = m.getParameterTypes();
                if ("screenshot".equals(m.getName()) && ps.length == 4
                        && ps[0] == Rect.class && ps[1] == int.class
                        && ps[2] == int.class && ps[3] == int.class) {
                    m.setAccessible(true);
                    sShot = m;
                }
            }

            sNodeCls = Class.forName("android.graphics.RenderNode");
            sNodeCtor = sNodeCls.getConstructor(String.class);
            sSetPosition = sNodeCls.getMethod("setPosition",
                    int.class, int.class, int.class, int.class);
            sSetLayerType = sNodeCls.getMethod("setLayerType", int.class);
            sSetBehindStyle = sNodeCls.getMethod("setBehindLayerBlurStyle", int.class);
            sSetBlurMode = sNodeCls.getMethod("setBlurMode", int.class);
            sSetBehindEnabled = sNodeCls.getMethod("setBehindBlurEnabled", boolean.class);
            sSetScaleInput = sNodeCls.getMethod("setScaleBlurInputEnabled", boolean.class);
            sSetProgress = sNodeCls.getMethod("setBlurProgress", float.class);
            sBeginRecording = sNodeCls.getMethod("beginRecording", int.class, int.class);
            sEndRecording = sNodeCls.getMethod("endRecording");
            try {
                sDiscard = sNodeCls.getMethod("discardDisplayList");
            } catch (Throwable ignored) {
            }

            sHrCls = Class.forName("android.graphics.HardwareRenderer");
            sHrSetSurface = sHrCls.getMethod("setSurface", Surface.class);
            sHrSetContentRoot = sHrCls.getMethod("setContentRoot", sNodeCls);
            sHrCreateRequest = sHrCls.getMethod("createRenderRequest");
            try {
                sHrDestroy = sHrCls.getMethod("destroy");
            } catch (Throwable ignored) {
            }

            sOk = sShot != null;
        } catch (Throwable t) {
            IslandHook.log("gpu blur unavailable: " + t);
            sOk = false;
        }
        return sOk;
    }

    static Bitmap shot(Rect crop, int outW, int outH, int rotation) {
        if (!available()) return null;
        try {
            return (Bitmap) sShot.invoke(null, crop, outW, outH, rotation);
        } catch (Throwable t) {
            IslandHook.log("gpu shot failed: " + t);
            return null;
        }
    }

    static Bitmap blur(Bitmap src, float progress) {
        if (!available() || src == null || src.isRecycled()) return null;
        final int w = src.getWidth();
        final int h = src.getHeight();
        if (w <= 0 || h <= 0) return null;
        Object node = null;
        Object hr = null;
        ImageReader reader = null;
        try {
            node = sNodeCtor.newInstance("islandBlur");
            sSetPosition.invoke(node, 0, 0, w, h);
            sSetLayerType.invoke(node, 2);
            sSetBehindStyle.invoke(node, 0);
            sSetBlurMode.invoke(node, 0);
            sSetBehindEnabled.invoke(node, true);
            sSetScaleInput.invoke(node, true);
            sSetProgress.invoke(node, progress);

            Canvas canvas = (Canvas) sBeginRecording.invoke(node, w, h);
            canvas.drawBitmap(src, 0f, 0f, null);
            sEndRecording.invoke(node);

            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2,
                    HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
            hr = sHrCls.getConstructor().newInstance();
            sHrSetSurface.invoke(hr, reader.getSurface());
            sHrSetContentRoot.invoke(hr, node);

            Object request = sHrCreateRequest.invoke(hr);
            try {
                request.getClass().getMethod("setWaitForPresent", boolean.class)
                        .invoke(request, true);
            } catch (Throwable ignored) {
            }
            request.getClass().getMethod("syncAndDraw").invoke(request);

            Image image = reader.acquireLatestImage();
            if (image == null) return null;
            Bitmap out = null;
            HardwareBuffer buf = null;
            try {
                buf = image.getHardwareBuffer();
                if (buf != null) {
                    Bitmap hw = Bitmap.wrapHardwareBuffer(buf, null);
                    if (hw != null) {
                        out = hw.copy(Bitmap.Config.ARGB_8888, false);
                        hw.recycle();
                    }
                }
            } finally {
                if (buf != null) buf.close();
                image.close();
            }
            return out;
        } catch (Throwable t) {
            IslandHook.log("gpu blur failed: " + t);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
            if (hr != null && sHrDestroy != null) {
                try {
                    sHrDestroy.invoke(hr);
                } catch (Throwable ignored) {
                }
            }
            if (node != null && sDiscard != null) {
                try {
                    sDiscard.invoke(node);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    static Bitmap shotAndBlur(Rect crop, int outW, int outH, int rotation, float progress) {
        Bitmap shot = shot(crop, outW, outH, rotation);
        if (shot == null) return null;
        Bitmap out = blur(shot, progress);
        try {
            shot.recycle();
        } catch (Throwable ignored) {
        }
        return out;
    }
}
