package com.harmonyos.liveIsland;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class IslandController implements IslandView.Host {
    private static final Map<String, String> NAV_APPS = new HashMap<>();
    static {
        NAV_APPS.put("com.baidu.BaiduMap", "百度地图");
        NAV_APPS.put("com.autonavi.minimap", "高德地图");
    }

    private static final long POLL_MS = 700;
    private static final long TICK_MS = 80;
    private static final long FG_CHECK_MS = 100;
    private static final long BACKDROP_TTL_MS = 30000;
    private static final long NAV_TIMEOUT_MS = 5000;

    private final Context mCtx;
    private final Handler mH = new Handler(Looper.getMainLooper());
    private final WindowManager mWm;
    private final MediaSessionManager mMsm;
    private final IslandView mView;
    private final float mDensity;

    private WindowManager.LayoutParams mLp;
    private boolean mAdded;
    private boolean mBlur;

    private View mTouchView;
    private WindowManager.LayoutParams mTouchLp;
    private boolean mTouchAdded;
    private boolean mTouchExpanded;
    private boolean mPrewarm;
    private int mWinType = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
    private boolean mExpandedState;

    private final List<MediaController> mSessions = new ArrayList<>();
    private final List<MediaController> mKnown = new ArrayList<>();
    private final Set<String> mSeenPlaying = new HashSet<>();
    private final Set<String> mWasPlaying = new HashSet<>();
    private final HashMap<String, Long> mPlayStart = new HashMap<>();
    private int mIndex = -1;
    private MediaController mCtrl;
    private long mManualAt;
    private long mNavStartAt;
    private long mMediaStartAt;
    private boolean mLastPlaying;
    private String mIconPkg = "";
    private Bitmap mIconBmp;

    private int mStatusBarH;
    private int mY;

    private boolean mShadeExpanded;
    private boolean mLastShadeState;
    private String mForegroundPkg;
    private long mLastFgCheck;
    private boolean mBlurSupported = true;
    private static volatile Object sPanel;
    private static volatile ClassLoader sCl;
    private static volatile Object sWakeControl;
    private static boolean sWakeTried;
    private static long sWakeAt;
    private static volatile Object sFlashlight;
    private static boolean sFlashlightTried;
    private static long sFlashlightAt;
    private Bitmap mBackdrop;
    private long mBackdropAt;
    private int mCaptureGen;
    private boolean mCapturing;
    private int mLastCount = -1;
    private View mCapsule;
    private int mCapsuleVis = View.VISIBLE;
    private boolean mCapsuleHidden;
    private final List<View> mCapsuleViews = new ArrayList<>();

    private boolean mNavActive;
    private String mNavPkg = "";
    private String mNavDist = "";
    private String mNavRoad = "";
    private String mNavAction = "";
    private String mNavExtra = "";
    private int mNavIconRes;
    private long mNavAt;
    private Bitmap mNavIcon;
    private int mPrimary = IslandView.MODE_MEDIA;
    private boolean mHadNav;
    private long mLastNavLog;
    private long mLastPickLog;

    private final Map<String, Cap> mCaps = new HashMap<>();
    private Cap mCap;
    private boolean mCapsuleActive;

    private static final long CHARGE_HUD_MS = 5000;
    private static final long VOLUME_HUD_MS = 3000;
    private static final long RINGER_HUD_MS = 3000;
    private static final long LOW_BATTERY_HUD_MS = 4000;
    private static final long BATTERY_CHECK_MS = 5000;
    private AudioManager mAudio;
    private boolean mHudInit;
    private int mLastMusicVol = -1;
    private int mLastMusicMax = -1;
    private int mLastRingVol = -1;
    private int mLastRingMax = -1;
    private int mLastRinger = -1;
    private int mLastBatteryPct = -1;
    private boolean mLow20Shown;
    private boolean mLow10Shown;
    private long mLastBatteryCheck;
    private boolean mTransientActive;
    private int mTransientKind;
    private int mTransientValue;
    private String mTransientText = "";
    private Bitmap mTransientIcon;
    private long mTransientUntil;

    static final class Act {
        int type;
        String key = "";
        Cap cap;
        MediaController ctrl;
    }

    private final List<Act> mActs = new ArrayList<>();
    private final Set<String> mPrevActKeys = new HashSet<>();
    private String mSelKey = "";
    private String mManualKey = "";

    private String mPillKey = "";
    private String mDotKey = "";
    private Act mPillAct;
    private Act mDotAct;

    private Act mDotShown;

    private final List<String> mHidden = new ArrayList<>();
    private int mSelIndex;
    private long mCapStartAt;
    private String mCapIconKey = "";
    private Bitmap mCapIconBmp;
    private String mSecArtKey = "";
    private Bitmap mSecArtBmp;
    private long mLastReconcile;

    private static volatile IslandController sInst;
    private static volatile Object sListener;

    static final class Cap {
        String key = "";
        String pkg = "";
        String title = "";
        String content = "";
        String event = "";
        int type = 1;
        int bg = 0xFF2A2A30;
        long timeMs;
        boolean countdown;
        boolean paused;
        int progress;
        int progressMax;
        boolean indeterminate;
        Bitmap icon;
        Notification.Action[] actions = new Notification.Action[0];
        boolean synthetic;
        long updatedAt;

        boolean delivery;
        int pct;
        Bitmap indicator;
        Bitmap[] nodes;
        int barColor;
        int barBg;
        int barType;
        Bitmap pic;
        String extendText = "";
    }

    private static final Pattern P_DIST = Pattern.compile(
            "^\\s*(\\d+(?:\\.\\d+)?)\\s*(米|公里|m|km|M|KM)\\s*$");
    private static final Pattern P_ETA = Pattern.compile(
            ".*(分钟|小时|剩余|预计).*");
    private static final String[] ACTIONS = {
            "左转", "右转", "直行", "掉头", "调头", "靠左", "靠右",
            "左前方", "右前方", "进入环岛", "驶出", "上桥", "下桥", "进入"
    };
    private static final String[] ROAD_HINTS = {
            "路", "街", "道", "桥", "巷", "大街", "高速", "环路", "线", "胡同"
    };

    private static final String STOPWATCH_KEY = "stopwatch";
    private static final String STOPWATCH_PKG = "com.huawei.deskclock";

    private static final String USB_KEY = "usb";
    private static final String USB_STATE_ACTION = "android.hardware.usb.action.USB_STATE";
    private static final int USB_MODE_CHARGE = 0;
    private static final int USB_MODE_MTP = 1;
    private static final int USB_MODE_PTP = 2;
    private boolean mUsbConnected;
    private int mUsbMode = USB_MODE_CHARGE;

    private final BroadcastReceiver mUsbRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                handleUsbState(intent);
            } catch (Throwable t) {
                IslandHook.log("usb rx: " + t);
            }
        }
    };

    private static final String DEMO_ACTION = "com.harmonyos.liveIsland.DEMO";
    private static final String DEMO_KEY = "demo:delivery";

    private final BroadcastReceiver mDemoRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if ("off".equals(intent.getStringExtra("act"))) {
                    if (mCaps.remove(DEMO_KEY) != null) recomputeCapsule();
                    return;
                }
                Cap cap = new Cap();
                cap.key = DEMO_KEY;
                cap.pkg = "com.sankuai.meituan";
                cap.synthetic = true;
                cap.delivery = true;
                cap.type = 3;
                cap.title = pick(intent.getStringExtra("title"), "预计 13:36 送达");
                cap.content = pick(intent.getStringExtra("content"), "商家已接单，骑手已到店");
                cap.pct = Math.max(0, Math.min(100, intent.getIntExtra("pct", 62)));
                cap.progress = cap.pct;
                cap.progressMax = 100;
                cap.barColor = 0xFFFFD100;
                cap.barBg = 0x40FFFFFF;
                cap.barType = 1;
                cap.icon = loadAppIcon("com.sankuai.meituan");
                cap.pic = demoThumb();
                cap.extendText = "查看订单 ›";
                cap.actions = new Notification.Action[]{synthAction("查看详情")};
                cap.updatedAt = SystemClock.elapsedRealtime();
                mCaps.put(DEMO_KEY, cap);
                IslandHook.log("demo delivery in pct=" + cap.pct);
                mManualKey = DEMO_KEY;
                mManualAt = SystemClock.elapsedRealtime();
                recomputeCapsule();
                if (intent.getBooleanExtra("expand", false)) {
                    mH.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mView.debugExpand();
                        }
                    }, 150);
                }
            } catch (Throwable t) {
                IslandHook.log("demo rx: " + t);
            }
        }
    };

    private static String pick(String v, String def) {
        return v == null || v.length() == 0 ? def : v;
    }

    private Bitmap demoThumb() {
        try {
            int s = 128;
            Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new android.graphics.LinearGradient(0, 0, s, s,
                    new int[]{0xFFFFC46B, 0xFFE8622A, 0xFF8A2B12},
                    null, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, s, s, p);
            p.setShader(null);
            p.setColor(0x33FFFFFF);
            c.drawCircle(s * 0.32f, s * 0.30f, s * 0.20f, p);
            c.drawCircle(s * 0.66f, s * 0.62f, s * 0.26f, p);
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    private final BroadcastReceiver mPowerRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())
                        && feat(IslandPrefs.K_FEAT_CHARGE)) {
                    int pct = batteryPercent();
                    IslandHook.log("power connected " + pct + "%");
                    showTransient(0, pct, "充电中", null, CHARGE_HUD_MS);
                }
            } catch (Throwable t) {
                IslandHook.log("power rx: " + t);
            }
        }
    };

    private final BroadcastReceiver mStopwatchRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                handleStopwatch(intent);
            } catch (Throwable t) {
                IslandHook.log("stopwatch rx: " + t);
            }
        }
    };

    private final BroadcastReceiver mNavRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                handleNav(intent);
            } catch (Throwable t) {
                IslandHook.log("nav rx: " + t);
            }
        }
    };

    private final MediaController.Callback mCb = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            updateMedia();
            mH.post(mCheck);
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            updateMedia();
        }

        @Override
        public void onSessionDestroyed() {
            mH.post(new Runnable() {
                @Override
                public void run() {
                    mCtrl = null;
                    refresh();
                }
            });
        }
    };

    private final Runnable mPoll = new Runnable() {
        @Override
        public void run() {
            try {
                refresh();
            } catch (Throwable t) {
                IslandHook.log("poll failed: " + t);
            }
            mH.postDelayed(this, POLL_MS);
        }
    };

    private final Runnable mCheck = new Runnable() {
        @Override
        public void run() {
            try {
                mShadeExpanded = readShadeExpanded();
                mForegroundPkg = foregroundPackage();
                mLastFgCheck = SystemClock.elapsedRealtime();
                applyState();
                applyVisibility();
            } catch (Throwable t) {
                IslandHook.log("check failed: " + t);
            }
        }
    };

    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            try {
                mShadeExpanded = readShadeExpanded();
                long now = SystemClock.elapsedRealtime();
                if (mShadeExpanded != mLastShadeState) {
                    mLastShadeState = mShadeExpanded;
                    IslandHook.log("shade=" + mShadeExpanded);
                }
                if (now - mLastFgCheck >= FG_CHECK_MS) {
                    mLastFgCheck = now;
                    String fg = foregroundPackage();
                    boolean changed = fg == null ? mForegroundPkg != null
                            : !fg.equals(mForegroundPkg);
                    mForegroundPkg = fg;
                    if (changed) {
                        applyState();
                        mH.removeCallbacks(mBgSnap);
                        mH.post(mBgSnap);
                    }
                }
                if (mTransientActive && now >= mTransientUntil) {
                    applyState();
                    applyVisibility();
                }
                checkHud();
                checkBattery(now);
                if (mNavActive && now - mNavAt > NAV_TIMEOUT_MS) {
                    mNavActive = false;
                    applyState();
                }
                if (now - mLastReconcile > 8000) {
                    mLastReconcile = now;
                    reconcileCapsules(false);
                }
                refreshBackdropCollapsed();
                applyVisibility();
                if (mAdded) {
                    if (mCapsule == null || !mCapsule.isAttachedToWindow()) {
                        mCapsule = findCapsule(findStatusBarView());
                    }
                    setCapsuleHidden(true);
                }

                if (mAdded) {
                    setMainTouchable(false);
                    if (mExpandedState) {
                        resizeMainWindow(mView.mainWidthPx(), mView.mainHeightPx());
                    } else {
                        syncCollapsedMainWindow();
                    }
                    if (!mTouchAdded) {
                        showTouchWindow(mExpandedState);
                    } else if (mTouchExpanded != mExpandedState) {
                        removeTouchWindow();
                        showTouchWindow(mExpandedState);
                    }
                }
            } catch (Throwable t) {
                IslandHook.log("tick failed: " + t);
            }
            mH.postDelayed(this, TICK_MS);
        }
    };

    IslandController(Context ctx) {
        mCtx = ctx;
        mDensity = ctx.getResources().getDisplayMetrics().density;
        mWm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        mMsm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
        mStatusBarH = resDimen("status_bar_height", 64);
        mView = new IslandView(ctx, this);
        mView.setStatusBarHeight(mStatusBarH);
        mY = Math.max(0, (mStatusBarH - Math.round(mView.pillHeight())) / 2);

        try {
            mBlurSupported = mWm != null && mWm.isCrossWindowBlurEnabled();
        } catch (Throwable t) {
            mBlurSupported = false;
        }
        mView.setBlurSupported(mBlurSupported);

        try {
            mCtx.registerReceiver(mNavRx, new IntentFilter(NavReporter.ACTION));
        } catch (Throwable t) {
            IslandHook.log("register nav rx failed: " + t);
        }
        try {
            mCtx.registerReceiver(mStopwatchRx,
                    new IntentFilter(StopwatchReporter.ACTION));
        } catch (Throwable t) {
            IslandHook.log("register stopwatch rx failed: " + t);
        }
        try {
            mCtx.registerReceiver(mPowerRx,
                    new IntentFilter(Intent.ACTION_POWER_CONNECTED));
        } catch (Throwable t) {
            IslandHook.log("register power rx failed: " + t);
        }
        try {
            mCtx.registerReceiver(mUsbRx, new IntentFilter(USB_STATE_ACTION));
        } catch (Throwable t) {
            IslandHook.log("register usb rx failed: " + t);
        }
        try {
            mCtx.registerReceiver(mDemoRx, new IntentFilter(DEMO_ACTION));
        } catch (Throwable t) {
            IslandHook.log("register demo rx failed: " + t);
        }
        try {
            mAudio = (AudioManager) mCtx.getSystemService(Context.AUDIO_SERVICE);
            if (mAudio != null) {
                mLastMusicMax = mAudio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                mLastMusicVol = mAudio.getStreamVolume(AudioManager.STREAM_MUSIC);
                mLastRingMax = mAudio.getStreamMaxVolume(AudioManager.STREAM_RING);
                mLastRingVol = mAudio.getStreamVolume(AudioManager.STREAM_RING);
                mLastRinger = mAudio.getRingerMode();
                mHudInit = true;
            }
        } catch (Throwable t) {
            IslandHook.log("audio init failed: " + t);
        }
        try {
            mLastBatteryPct = batteryPercent();
            mLow20Shown = mLastBatteryPct <= 20;
            mLow10Shown = mLastBatteryPct <= 10;
            mLastBatteryCheck = SystemClock.elapsedRealtime();
        } catch (Throwable ignored) {
        }

        ctx.registerComponentCallbacks(new ComponentCallbacks() {
            @Override
            public void onConfigurationChanged(Configuration newConfig) {
                mH.post(new Runnable() {
                    @Override
                    public void run() {
                        mStatusBarH = resDimen("status_bar_height", 64);
                        mView.setStatusBarHeight(mStatusBarH);
                        mY = Math.max(0, (mStatusBarH - Math.round(mView.pillHeight())) / 2);
                        clearBackdrop();
                        if (mAdded) {
                            try {
                                mWm.removeView(mView);
                            } catch (Throwable ignored) {
                            }
                            mAdded = false;
                            showWindow();
                        }
                    }
                });
            }

            @Override
            public void onLowMemory() {
            }
        });
    }

    void start() {
        sInst = this;
        IslandHook.log("controller start, media=any MediaSession"
                + ", nav=" + NAV_APPS.keySet() + ", blur=" + mBlurSupported);
        IslandPrefs.load(mCtx);
        IslandPrefs.watch(mCtx, new Runnable() {
            @Override
            public void run() {
                applyPrefs();
            }
        });
        applyPrefs();
        mH.post(mPoll);
        mH.post(mTick);
        seedNotifications();
        initUsb();
    }

    private void applyPrefs() {
        int bg = IslandPrefs.bgMode();
        boolean wasBlack = false;
        try {
            wasBlack = mView.bgMode() == IslandPrefs.BG_BLACK;
        } catch (Throwable ignored) {
        }
        try {
            mView.setBgMode(bg);
        } catch (Throwable ignored) {
        }

        if (bg != IslandPrefs.BG_BLACK && (wasBlack || mBackdrop == null)) {
            mBackdropAt = 0;
            mH.post(new Runnable() {
                @Override
                public void run() {
                    refreshBackdropCollapsed(true);
                }
            });
        }
        applyState();
        applyVisibility();
    }

    private static boolean feat(String key) {
        return IslandPrefs.getBool(key, true);
    }

    private boolean capAllowed(Cap c) {
        if (c == null) return false;
        String k = c.key == null ? "" : c.key;
        if (USB_KEY.equals(k)) return feat(IslandPrefs.K_FEAT_USB);
        if (STOPWATCH_KEY.equals(k)) return feat(IslandPrefs.K_FEAT_STOPWATCH);
        if (isFlashCap(c)) return feat(IslandPrefs.K_FEAT_FLASH);
        if (isRecordCap(c)) return feat(IslandPrefs.K_FEAT_RECORD);
        return feat(IslandPrefs.K_FEAT_CAPSULE);
    }

    private static boolean isFlashCap(Cap c) {
        String k = c.key == null ? "" : c.key.toLowerCase();
        String p = c.pkg == null ? "" : c.pkg.toLowerCase();
        return k.contains("flash") || k.contains("torch") || p.contains("flash");
    }

    private static boolean isRecordCap(Cap c) {
        String k = c.key == null ? "" : c.key.toLowerCase();
        String p = c.pkg == null ? "" : c.pkg.toLowerCase();
        return k.contains("record") || k.contains("screenrec")
                || p.contains("screenrec") || p.contains("recorder");
    }

    private int resDimen(String name, int def) {
        try {
            int id = mCtx.getResources().getIdentifier(name, "dimen", "android");
            if (id != 0) return mCtx.getResources().getDimensionPixelSize(id);
        } catch (Throwable ignored) {
        }
        return def;
    }

    static void hookPowerWarnings(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClass(
                    "com.android.systemui.power.PowerNotificationWarnings", cl);
            XposedHelpers.findAndHookMethod(cls,
                    "l", boolean.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(null);
                            IslandHook.log("suppressed system low battery warning");
                        }
                    });
        } catch (Throwable t) {
            IslandHook.log("power warning hook failed: " + t);
        }
    }

    static void hookPanel(ClassLoader cl) {
        sCl = cl;
        try {
            Class<?> pvc = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.phone.PanelViewController", cl);
            XposedBridge.hookAllConstructors(pvc, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    sPanel = param.thisObject;
                }
            });
            IslandHook.log("panel hook installed (early)");
        } catch (Throwable t) {
            IslandHook.log("panel hook failed: " + t);
        }
    }

    static void hookNotifications(ClassLoader cl) {
        try {
            Class<?> nl = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.NotificationListener", cl);
            XposedBridge.hookAllConstructors(nl, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    sListener = param.thisObject;
                }
            });
            XposedBridge.hookAllMethods(nl, "onNotificationPosted", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length < 1
                            || !(param.args[0] instanceof StatusBarNotification)) return;
                    IslandController c = sInst;
                    if (c != null) c.onPosted((StatusBarNotification) param.args[0]);
                }
            });
            XposedBridge.hookAllMethods(nl, "onNotificationRemoved", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length < 1
                            || !(param.args[0] instanceof StatusBarNotification)) return;
                    IslandController c = sInst;
                    if (c != null) c.onRemoved((StatusBarNotification) param.args[0]);
                }
            });
            XposedBridge.hookAllMethods(nl, "onListenerConnected", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    IslandController c = sInst;
                    if (c != null) c.seedNotifications();
                }
            });
            IslandHook.log("notification hook installed");
        } catch (Throwable t) {
            IslandHook.log("notification hook failed: " + t);
        }
    }

    private void onPosted(StatusBarNotification sbn) {
        final StatusBarNotification s = sbn;
        mH.post(new Runnable() {
            @Override
            public void run() {
                ingest(s);
            }
        });
    }

    private void onRemoved(StatusBarNotification sbn) {
        final String key = sbn.getKey();
        mH.post(new Runnable() {
            @Override
            public void run() {
                if (mCaps.remove(key) != null) {
                    IslandHook.log("cap removed " + key);
                    recomputeCapsule();
                }
            }
        });
    }

    void seedNotifications() {
        mH.postDelayed(new Runnable() {
            @Override
            public void run() {
                reconcileCapsules(true);
            }
        }, 600);
    }

    private void reconcileCapsules(boolean log) {
        Object l = sListener;
        if (l == null) return;
        try {
            Object r = XposedHelpers.callMethod(l, "getActiveNotifications");
            if (!(r instanceof StatusBarNotification[])) return;
            StatusBarNotification[] arr = (StatusBarNotification[]) r;
            Set<String> seen = new HashSet<>();
            for (StatusBarNotification sbn : arr) {
                String key = sbn.getKey();
                if (mCaps.containsKey(key)) {
                    if ("flashlight_notification_tag".equals(sbn.getTag())
                            && !isFlashlightOn()) continue;
                    if (!capEnded(sbn.getNotification())) seen.add(key);
                    continue;
                }
                Cap cap = parseCap(sbn);
                if (cap == null) continue;
                seen.add(cap.key);
                mCaps.put(cap.key, cap);
            }
            boolean changed = false;
            java.util.Iterator<String> it = mCaps.keySet().iterator();
            while (it.hasNext()) {
                String k = it.next();
                if (STOPWATCH_KEY.equals(k) || USB_KEY.equals(k) || DEMO_KEY.equals(k)) continue;
                if (!seen.contains(k)) {
                    it.remove();
                    changed = true;
                }
            }
            if (log) {
                IslandHook.log("seed notifications: " + arr.length
                        + ", capsules=" + mCaps.size());
            }
            if (changed) recomputeCapsule();
        } catch (Throwable t) {
            logOnce("reconcile: " + t);
        }
    }

    private void ingest(StatusBarNotification sbn) {
        try {
            Cap cap = parseCap(sbn);
            if (cap == null) {
                if (mCaps.remove(sbn.getKey()) != null) {
                    IslandHook.log("cap drop " + sbn.getKey());
                    recomputeCapsule();
                }
                return;
            }
            mCaps.put(cap.key, cap);
            IslandHook.log("cap in " + cap.key + " type=" + cap.type
                    + " title=" + cap.title + " n=" + mCaps.size());
            recomputeCapsule();
        } catch (Throwable t) {
            logOnce("ingest: " + t);
        }
    }

    private Cap parseCap(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (n == null) return null;
        if ("flashlight_notification_tag".equals(sbn.getTag()) && !isFlashlightOn()) {
            return null;
        }
        if ("com.huawei.mediacontroller".equals(sbn.getPackageName())) return null;
        if ("com.huawei.mediacontroller".equals(sbn.getPackageName())) return null;
        Bundle e = n.extras;
        Bundle cb = e == null ? null : e.getBundle("notification.live.capsule");
        Bundle feat = e == null ? null : e.getBundle("notification.live.feature");
        String event = e == null ? null : e.getString("notification.live.event");
        boolean liveStyle = e != null && e.getBoolean("hw_enable_common_live_style", false);
        boolean ongoing = (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        boolean progressLike = feat != null || (isDeliveryPkg(sbn.getPackageName())
                && (ongoing || (e != null && e.getInt("android.progressMax", 0) > 0)));
        if (cb == null && event == null && !(liveStyle && ongoing) && !progressLike) return null;
        if (cb != null && cb.getInt("notification.live.capsuleStatus", 1) == -1) return null;

        Cap cap = new Cap();
        cap.key = sbn.getKey();
        cap.pkg = sbn.getPackageName();
        cap.event = event == null ? "" : event;
        cap.type = cb == null
                ? (e != null && e.getBoolean("android.showChronometer", false) ? 2 : 1)
                : cb.getInt("notification.live.capsuleType", 1);
        if (cap.type < 1 || cap.type > 3) cap.type = 1;

        CharSequence title = cb == null ? null
                : cb.getCharSequence("notification.live.capsuleTitle");
        if (title == null && e != null) title = e.getCharSequence("android.title");
        CharSequence content = cb == null ? null
                : cb.getCharSequence("notification.live.capsuleContent");
        if (content == null && e != null) content = e.getCharSequence("android.text");
        cap.title = title == null ? "" : title.toString().trim();
        cap.content = content == null ? "" : content.toString().trim();
        if (cap.title.length() == 0 && cap.content.length() == 0
                && cap.event.length() == 0) return null;
        if (cap.title.length() > 40) cap.title = cap.title.substring(0, 40);
        if (cap.content.length() > 80) cap.content = cap.content.substring(0, 80);
        if (cb == null) {
            String all = cap.title + " " + cap.content;
            if (all.contains("已关闭") || all.contains("已停止")
                    || all.contains("已结束") || all.contains("已断开")) return null;
        }

        int bg = cb == null ? 0 : cb.getInt("notification.live.capsuleBgColor", 0);
        cap.bg = bg == 0 ? 0xFF2A2A30 : bg;
        cap.timeMs = cb == null ? 0 : cb.getLong("notification.live.capsuleTime", 0);
        cap.countdown = cb != null && cb.getBoolean("notification.live.capsuleCountdown", false);
        cap.paused = cb != null && cb.getBoolean("notification.live.capsulePause", false);
        if (cap.type == 2 && cap.timeMs <= 0) {
            long since = System.currentTimeMillis() - sbn.getPostTime();
            cap.timeMs = Math.max(0, since);
        }
        if (e != null) {
            cap.progress = e.getInt("android.progress", 0);
            cap.progressMax = e.getInt("android.progressMax", 0);
            cap.indeterminate = e.getBoolean("android.progressIndeterminate", false);
        }
        Icon ic = cb == null ? null : cb.getParcelable("notification.live.capsuleIcon");
        if ("flashlight_notification_tag".equals(sbn.getTag())) {
            cap.icon = loadSystemIcon("ic_flashlight_tile_on");
        } else {
            cap.icon = loadCapIcon(cap.pkg, sbn.getId(), ic, n.getSmallIcon());
        }
        cap.actions = n.actions == null ? new Notification.Action[0] : n.actions;
        parseDeliveryFeature(cap, sbn, e, feat);
        cap.updatedAt = SystemClock.elapsedRealtime();
        return cap;
    }

    private static boolean isDeliveryPkg(String pkg) {
        if (pkg == null) return false;
        return pkg.equals("com.sankuai.meituan")
                || pkg.startsWith("com.sankuai.meituan.")
                || pkg.equals("me.ele")
                || pkg.equals("com.jingdong.app.mall")
                || pkg.equals("com.taobao.taobao")
                || pkg.equals("com.achievo.vipshop.shopping");
    }

    private void parseDeliveryFeature(Cap cap, StatusBarNotification sbn,
                                      Bundle e, Bundle feat) {
        boolean delivery = feat != null && (feat.containsKey("notification.live.feature.progress")
                || feat.containsKey("notification.live.feature.indicatorIcon")
                || feat.containsKey("notification.live.feature.nodeIcon"));
        if (!delivery) {
            if (!isDeliveryPkg(sbn.getPackageName())) return;
            int max = e == null ? 0 : e.getInt("android.progressMax", 0);
            if (max <= 0) {
                cap.delivery = true;
                cap.type = 3;
                cap.pct = 0;
                cap.progress = 0;
                cap.progressMax = 100;
                cap.pic = largePicture(e);
                return;
            }
            cap.delivery = true;
            cap.type = 3;
            cap.pct = Math.round(100f * e.getInt("android.progress", 0) / max);
            cap.progress = cap.pct;
            cap.progressMax = 100;
            cap.pic = largePicture(e);
            return;
        }

        cap.delivery = true;
        cap.type = 3;
        cap.pct = Math.max(0, Math.min(100,
                feat.getInt("notification.live.feature.progress", 0)));
        cap.progress = cap.pct;
        cap.progressMax = 100;
        cap.barType = feat.getInt("notification.live.feature.progressType", 1);
        cap.barColor = feat.getInt("notification.live.feature.progressColor", 0);
        cap.barBg = feat.getInt("notification.live.feature.progressBgColor", 0);
        Icon ind = feat.getParcelable("notification.live.feature.indicatorIcon");
        cap.indicator = iconBitmap(ind, 56);
        try {
            java.util.ArrayList<Icon> nodes =
                    feat.getParcelableArrayList("notification.live.feature.nodeIcon");
            if (nodes != null && !nodes.isEmpty()) {
                Bitmap[] arr = new Bitmap[Math.min(2, nodes.size())];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = iconBitmap(nodes.get(i), 48);
                }
                cap.nodes = arr;
            }
        } catch (Throwable ignored) {
        }
        Icon ext = feat.getParcelable("notification.live.feature.extendImage");
        cap.pic = iconBitmap(ext, 128);
        if (cap.pic == null) cap.pic = largePicture(e);
        CharSequence et = feat.getCharSequence("notification.live.feature.extendText");
        if (et != null) cap.extendText = et.toString().trim();
    }

    private Bitmap largePicture(Bundle e) {
        if (e == null) return null;
        try {
            Bitmap b = e.getParcelable(Notification.EXTRA_PICTURE);
            if (b != null) return b;
        } catch (Throwable ignored) {
        }
        try {
            Object big = e.get(Notification.EXTRA_LARGE_ICON_BIG);
            if (big instanceof Bitmap) return (Bitmap) big;
            Icon ic = e.getParcelable(Notification.EXTRA_LARGE_ICON);
            return iconBitmap(ic, 128);
        } catch (Throwable t) {
            return null;
        }
    }

    private Bitmap iconBitmap(Icon icon, int sizePx) {
        if (icon == null) return null;
        try {
            Drawable d = icon.loadDrawable(mCtx);
            if (d == null) return null;
            int s = Math.max(1, sizePx);
            Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, s, s);
            d.draw(c);
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean capEnded(Notification n) {
        if (n == null) return true;
        Bundle e = n.extras;
        Bundle cb = e == null ? null : e.getBundle("notification.live.capsule");
        if (cb != null) return cb.getInt("notification.live.capsuleStatus", 1) == -1;
        CharSequence t = e == null ? null : e.getCharSequence("android.text");
        if (t == null) return false;
        String s = t.toString();
        return s.contains("已关闭") || s.contains("已停止")
                || s.contains("已结束") || s.contains("已断开");
    }

    private boolean isFlashlightOn() {
        if (!sFlashlightTried || (sFlashlight == null
                && SystemClock.elapsedRealtime() - sFlashlightAt > 5000)) {
            sFlashlightTried = true;
            sFlashlightAt = SystemClock.elapsedRealtime();
            try {
                ClassLoader cl = sCl != null ? sCl : mCtx.getClassLoader();
                Class<?> dep = Class.forName("com.android.systemui.plugins.Dependency", false, cl);
                Class<?> fc = Class.forName(
                        "com.android.systemui.statusbar.policy.FlashlightController", false, cl);
                sFlashlight = XposedHelpers.callStaticMethod(dep, "get", fc);
                IslandHook.log("flashlight ctrl: " + (sFlashlight != null));
            } catch (Throwable t) {
                logOnce("flashlight ctrl: " + t);
            }
        }
        if (sFlashlight == null) return true;
        try {
            Object r = XposedHelpers.callMethod(sFlashlight, "isFlashlightOn");
            return !(r instanceof Boolean) || (Boolean) r;
        } catch (Throwable t) {
            return true;
        }
    }

    private static Integer capsuleEnableStatus(Notification n) {        try {
            Object v = XposedHelpers.callMethod(n, "getCapsuleEnableStatus");
            if (v instanceof Integer) return (Integer) v;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Bitmap loadSystemIcon(String name) {
        String key = "sys:" + name;
        if (key.equals(mCapIconKey)) return mCapIconBmp;
        mCapIconKey = key;
        mCapIconBmp = null;
        try {
            Resources r = mCtx.getResources();
            int id = r.getIdentifier(name, "drawable", "com.android.systemui");
            if (id == 0) return null;
            Drawable d = r.getDrawable(id, null);
            if (d == null) return null;
            int s = Math.max(1, Math.round(mDensity * 48));
            Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, s, s);
            d.draw(c);
            mCapIconBmp = b;
        } catch (Throwable t) {
            logOnce("sys icon: " + t);
        }
        return mCapIconBmp;
    }

    private Bitmap loadCapIcon(String pkg, int id, Icon icon, Icon small) {
        String key = pkg + "#" + id + "#" + (icon != null ? icon.toString()
                : (small != null ? small.toString() : ""));
        if (key.equals(mCapIconKey)) return mCapIconBmp;
        mCapIconKey = key;
        mCapIconBmp = null;
        try {
            Icon use = icon != null ? icon : small;
            if (use == null) return null;
            Drawable d = use.loadDrawable(mCtx);
            if (d == null) return null;
            int s = Math.max(1, Math.round(mDensity * 48));
            Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, s, s);
            d.draw(c);
            mCapIconBmp = b;
        } catch (Throwable t) {
            logOnce("cap icon: " + t);
        }
        return mCapIconBmp;
    }

    private void handleStopwatch(Intent i) {
        if (!feat(IslandPrefs.K_FEAT_STOPWATCH)) return;
        boolean visible = i.getBooleanExtra("visible", false);
        if (!visible) {
            if (mCaps.remove(STOPWATCH_KEY) != null) {
                IslandHook.log("stopwatch removed");
                recomputeCapsule();
            }
            return;
        }
        long elapsed = Math.max(0, i.getLongExtra("elapsed", 0));
        boolean paused = i.getBooleanExtra("paused", false);
        Cap c = mCaps.get(STOPWATCH_KEY);
        boolean fresh = c == null;
        if (c == null) {
            c = new Cap();
            c.key = STOPWATCH_KEY;
            c.pkg = STOPWATCH_PKG;
            c.title = "秒表";
            c.type = 2;
            c.bg = 0xFF2A6FE8;
            c.synthetic = true;
            c.icon = loadSystemIcon("ic_clock_black_24dp");
        }
        c.timeMs = elapsed;
        c.countdown = false;
        c.paused = paused;
        c.actions = new Notification.Action[]{
                synthAction(paused ? "继续" : "暂停"),
                synthAction("重置")
        };
        c.updatedAt = SystemClock.elapsedRealtime();
        mCaps.put(STOPWATCH_KEY, c);
        if (fresh) IslandHook.log("stopwatch in " + elapsed + " paused=" + paused);
        recomputeCapsule();
    }

    private void initUsb() {
        try {
            String st = sysProp("sys.usb.state");
            if (st.length() == 0 || "none".equals(st)) return;
            mUsbConnected = true;
            mUsbMode = st.contains("mtp") ? USB_MODE_MTP
                    : st.contains("ptp") ? USB_MODE_PTP : USB_MODE_CHARGE;
            IslandHook.log("usb init state=" + st + " mode=" + mUsbMode
                    + " bits mtp=" + usbFunctionValue(USB_MODE_MTP)
                    + " ptp=" + usbFunctionValue(USB_MODE_PTP));
            updateUsbCap(true);
        } catch (Throwable t) {
            IslandHook.log("usb init: " + t);
        }
    }

    private String sysProp(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = XposedHelpers.callStaticMethod(sp, "get", key);
            return v == null ? "" : v.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private void handleUsbState(Intent i) {
        if (!feat(IslandPrefs.K_FEAT_USB)) return;
        boolean connected = i.getBooleanExtra("connected", false);
        int mode = i.getBooleanExtra("mtp", false) ? USB_MODE_MTP
                : i.getBooleanExtra("ptp", false) ? USB_MODE_PTP : USB_MODE_CHARGE;
        boolean stateChanged = connected != mUsbConnected || mode != mUsbMode;
        mUsbConnected = connected;
        mUsbMode = mode;
        IslandHook.log("usb connected=" + connected + " mode=" + mode);
        updateUsbCap(stateChanged);
    }

    private String usbModeName() {
        if (mUsbMode == USB_MODE_MTP) return "文件传输";
        if (mUsbMode == USB_MODE_PTP) return "传输照片";
        return "仅充电";
    }

    private void updateUsbCap(boolean stateChanged) {
        if (!mUsbConnected) {
            if (mCaps.remove(USB_KEY) != null) {
                IslandHook.log("usb cap removed");
                recomputeCapsule();
            }
            return;
        }
        Cap c = mCaps.get(USB_KEY);
        boolean fresh = c == null;
        if (c == null) {
            c = new Cap();
            c.key = USB_KEY;
            c.pkg = "";
            c.type = 1;
            c.bg = 0xFF2C6BE0;
            c.synthetic = true;
            c.icon = usbIcon();
        }
        c.title = "USB 已连接";
        c.content = usbModeName();
        c.actions = new Notification.Action[]{
                synthAction("文件传输"),
                synthAction("传输照片"),
                synthAction("仅充电")
        };
        c.updatedAt = SystemClock.elapsedRealtime();
        mCaps.put(USB_KEY, c);
        if (fresh) {
            IslandHook.log("usb cap in mode=" + c.content);
            recomputeCapsule();
        } else if (stateChanged) {
            recomputeCapsule();
            applyState();
        }
    }

    private int usbFunctionValue(int mode) {
        if (mode == USB_MODE_MTP) return 2;
        if (mode == USB_MODE_PTP) return 4;
        return 0;
    }

    private void setUsbMode(int mode) {
        int bits = usbFunctionValue(mode);
        try {
            UsbManager um = (UsbManager) mCtx.getSystemService(Context.USB_SERVICE);
            if (um != null) {
                XposedHelpers.callMethod(um, "setCurrentFunctions", bits);
                IslandHook.log("usb set mode=" + mode + " bits=" + bits);
            }
        } catch (Throwable t) {
            IslandHook.log("usb set failed: " + t);
        }
        mUsbMode = mode;
        updateUsbCap(true);
    }

    private Bitmap usbIcon() {
        try {
            int s = Math.max(1, Math.round(mDensity * 48));
            Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFFFFFFFF);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * 0.082f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            float cx = s * 0.5f;
            float top = s * 0.17f;
            float bot = s * 0.83f;
            c.drawLine(cx, bot, cx, top, p);

            Path head = new Path();
            head.moveTo(cx, s * 0.10f);
            head.lineTo(cx - s * 0.10f, s * 0.26f);
            head.lineTo(cx + s * 0.10f, s * 0.26f);
            head.close();
            p.setStyle(Paint.Style.FILL);
            c.drawPath(head, p);
            p.setStyle(Paint.Style.STROKE);

            c.drawLine(cx, s * 0.55f, cx - s * 0.26f, s * 0.42f, p);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(cx - s * 0.28f, s * 0.40f, s * 0.075f, p);
            p.setStyle(Paint.Style.STROKE);

            c.drawLine(cx, s * 0.46f, cx + s * 0.26f, s * 0.32f, p);
            p.setStyle(Paint.Style.FILL);
            float q = s * 0.075f;
            c.drawRect(cx + s * 0.26f - q, s * 0.32f - q,
                    cx + s * 0.26f + q, s * 0.32f + q, p);
            return b;
        } catch (Throwable t) {
            IslandHook.log("usb icon: " + t);
            return null;
        }
    }

    private Notification.Action synthAction(String title) {        Intent i = new Intent("com.harmonyos.liveIsland.NOOP")
                .setPackage("com.android.systemui");
        PendingIntent pi = PendingIntent.getBroadcast(mCtx, title.hashCode(), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Icon icon = Icon.createWithResource("android", android.R.drawable.ic_media_pause);
        return new Notification.Action.Builder(icon, title, pi).build();
    }

    private void showTransient(int kind, int value, String text, String iconName,
                               long durationMs) {
        mTransientActive = true;
        mTransientKind = kind;
        mTransientValue = Math.max(0, Math.min(100, value));
        mTransientText = text == null ? "" : text;
        mTransientIcon = iconName == null ? null : loadSystemIcon(iconName);
        mTransientUntil = SystemClock.elapsedRealtime() + durationMs;
        applyState();
        applyVisibility();
        syncCollapsedMainWindow();
    }

    private int batteryPercent() {
        try {
            Intent i = mCtx.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i != null) {
                int lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (lvl >= 0 && scale > 0) return Math.min(100, lvl * 100 / scale);
            }
        } catch (Throwable ignored) {
        }
        try {
            BatteryManager bm = (BatteryManager) mCtx.getSystemService(
                    Context.BATTERY_SERVICE);
            if (bm != null) {
                int p = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (p >= 0) return Math.min(100, p);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private void checkHud() {
        if (!mHudInit || mAudio == null) return;
        try {
            int ringer = mAudio.getRingerMode();
            int mv = mAudio.getStreamVolume(AudioManager.STREAM_MUSIC);
            int mm = mAudio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int rv = mAudio.getStreamVolume(AudioManager.STREAM_RING);
            int rm = mAudio.getStreamMaxVolume(AudioManager.STREAM_RING);

            boolean ringerChanged = ringer != mLastRinger;
            boolean musicChanged = mv != mLastMusicVol || mm != mLastMusicMax;
            boolean ringChanged = rv != mLastRingVol || rm != mLastRingMax;

            mLastRinger = ringer;
            mLastMusicVol = mv;
            mLastMusicMax = mm;
            mLastRingVol = rv;
            mLastRingMax = rm;

            if (ringerChanged) {
                String text;
                String icon;
                if (ringer == AudioManager.RINGER_MODE_SILENT) {
                    text = "静音";
                    icon = "ic_soundmode_tile_silent";
                } else if (ringer == AudioManager.RINGER_MODE_VIBRATE) {
                    text = "震动";
                    icon = "ic_soundmode_tile_vibration";
                } else {
                    text = "响铃";
                    icon = "ic_soundmode_tile_bell";
                }
                showTransient(2, 0, text, icon, RINGER_HUD_MS);
                return;
            }
            if (musicChanged) {
                int pct = mm > 0 ? Math.round(mv * 100f / mm) : 0;
                showTransient(1, pct, pct + "%",
                        pct == 0 ? "ic_volume_media_mute" : "ic_volume_media",
                        VOLUME_HUD_MS);
                return;
            }
            if (ringChanged) {
                int pct = rm > 0 ? Math.round(rv * 100f / rm) : 0;
                showTransient(1, pct, pct + "%",
                        pct == 0 ? "ic_volume_ringer_mute" : "ic_volume_ringer",
                        VOLUME_HUD_MS);
            }
        } catch (Throwable t) {
            logOnce("hud: " + t);
        }
    }

    private void checkBattery(long now) {
        if (!feat(IslandPrefs.K_FEAT_CHARGE)) return;
        if (now - mLastBatteryCheck < BATTERY_CHECK_MS) return;
        mLastBatteryCheck = now;
        try {
            int pct = batteryPercent();
            if (pct != mLastBatteryPct && pct <= 21) {
                IslandHook.log("batt pct=" + pct + " last=" + mLastBatteryPct
                        + " low20=" + mLow20Shown + " low10=" + mLow10Shown);
            }
            if (pct == mLastBatteryPct) return;
            mLastBatteryPct = pct;
            boolean charging = isCharging();
            if (pct > 20) {
                mLow20Shown = false;
                mLow10Shown = false;
                return;
            }
            if (charging) return;
            if (pct <= 10 && !mLow10Shown) {
                mLow10Shown = true;
                mLow20Shown = true;
                showTransient(3, pct, "电量低", null, LOW_BATTERY_HUD_MS);
                IslandHook.log("low battery 10: " + pct + "%");
            } else if (pct <= 20 && !mLow20Shown) {
                mLow20Shown = true;
                showTransient(3, pct, "电量低", null, LOW_BATTERY_HUD_MS);
                IslandHook.log("low battery 20: " + pct + "%");
            }
        } catch (Throwable t) {
            logOnce("battery: " + t);
        }
    }

    private boolean isCharging() {
        try {
            Intent i = mCtx.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i != null) {
                int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                return plugged != 0
                        && (status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL);
            }
        } catch (Throwable ignored) {
        }
        try {
            BatteryManager bm = (BatteryManager) mCtx.getSystemService(
                    Context.BATTERY_SERVICE);
            return bm != null && bm.isCharging();
        } catch (Throwable t) {
            return false;
        }
    }

    private void recomputeCapsule() {
        boolean wasActive = mCapsuleActive;
        mCapsuleActive = !mCaps.isEmpty();
        if (mCapsuleActive && !wasActive) mCapStartAt = SystemClock.elapsedRealtime();
        applyState();
        applyVisibility();
    }

    private boolean readShadeExpanded() {
        if (!sWakeTried || (sWakeControl == null
                && SystemClock.elapsedRealtime() - sWakeAt > 5000)) {
            sWakeTried = true;
            sWakeAt = SystemClock.elapsedRealtime();
            try {
                ClassLoader cl = sCl != null ? sCl : mCtx.getClassLoader();
                Class<?> dep = Class.forName("com.huawei.systemui.emui.HwDependency", false, cl);
                Class<?> ctrl = Class.forName(
                        "com.android.systemui.statusbar.phone.HwPanelWakeUpControl", false, cl);
                sWakeControl = XposedHelpers.callStaticMethod(dep, "get", ctrl);
                IslandHook.log("wake control: " + (sWakeControl != null));
            } catch (Throwable t) {
                IslandHook.log("wake control failed: " + t);
            }
        }
        if (sWakeControl != null) {
            try {
                Object r = XposedHelpers.callMethod(sWakeControl, "isPanelExpand");
                if (r instanceof Boolean) return (Boolean) r;
            } catch (Throwable ignored) {
            }
        }
        Object p = sPanel;
        if (p == null) return false;
        boolean fraction = false;
        try {
            fraction = XposedHelpers.getFloatField(p, "mExpandedFraction") > 0.01f;
        } catch (Throwable ignored) {
        }
        try {
            Object r = XposedHelpers.callMethod(p, "isFullyCollapsed");
            if (r instanceof Boolean && !(Boolean) r) return true;
        } catch (Throwable ignored) {
        }
        return fraction;
    }

    private String foregroundPackage() {
        try {
            Class<?> iface = Class.forName("android.app.IActivityTaskManager");
            Object svc = Class.forName("android.app.ActivityTaskManager")
                    .getMethod("getService").invoke(null);
            Object tasks = iface.getMethod("getTasks",
                    int.class, boolean.class, boolean.class, int.class)
                    .invoke(svc, 1, false, false, 0);
            if (tasks instanceof List && !((List<?>) tasks).isEmpty()) {
                Object info = ((List<?>) tasks).get(0);
                Object cn = info.getClass().getField("topActivity").get(info);
                if (cn instanceof ComponentName) return ((ComponentName) cn).getPackageName();
            }
        } catch (Throwable t) {
            logOnce("getTasks: " + t);
        }
        try {
            ActivityManager am = (ActivityManager) mCtx.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks != null && !tasks.isEmpty()) {
                ComponentName cn = tasks.get(0).topActivity;
                if (cn != null) return cn.getPackageName();
            }
        } catch (Throwable t) {
            logOnce("getRunningTasks: " + t);
        }
        return null;
    }

    private boolean isAppForeground(String pkg) {
        return pkg != null && pkg.equals(mForegroundPkg);
    }

    private void refresh() {
        if (mMsm == null) return;
        List<MediaController> all = allSessions();
        mergeSessions(all);
        List<MediaController> valid = new ArrayList<>();
        long now = SystemClock.elapsedRealtime();
        for (MediaController c : new ArrayList<>(mKnown)) {
            if (c == null) continue;
            try {
                String pkg = c.getPackageName();
                if (pkg == null || pkg.equals("com.android.systemui")
                        || pkg.equals("com.harmonyos.liveIsland")) continue;
                PlaybackState ps = c.getPlaybackState();
                MediaMetadata md = c.getMetadata();
                if (ps == null || md == null) continue;
                int st = ps.getState();
                if (st == PlaybackState.STATE_NONE || st == PlaybackState.STATE_STOPPED
                        || st == PlaybackState.STATE_ERROR) continue;
                String key = tokenKey(c);
                if (st == PlaybackState.STATE_PLAYING) {
                    mSeenPlaying.add(key);
                    if (mWasPlaying.add(key)) {
                        mPlayStart.put(key, now);
                    }
                    valid.add(c);
                } else {
                    mWasPlaying.remove(key);
                    if (sameSession(c, mCtrl)) valid.add(c);
                }
            } catch (Throwable ignored) {
            }
        }
        mSessions.clear();
        mSessions.addAll(valid);
        if (valid.size() != mLastCount) {
            mLastCount = valid.size();
            IslandHook.log("sessions=" + valid.size());
            for (MediaController c : valid) {
                try {
                    IslandHook.log("  - " + c.getPackageName());
                } catch (Throwable ignored) {
                }
            }
        }
        if (valid.isEmpty()) {
            switchTo(null);
            return;
        }
        int idx = -1;
        for (int i = 0; i < valid.size(); i++) {
            if (sameSession(valid.get(i), mCtrl)) {
                idx = i;
                break;
            }
        }
        int playingIdx = -1;
        long newest = -1;
        for (int i = 0; i < valid.size(); i++) {
            try {
                PlaybackState ps = valid.get(i).getPlaybackState();
                if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) {
                    Long t = mPlayStart.get(tokenKey(valid.get(i)));
                    long ts = t == null ? 0L : t;
                    if (ts >= newest) {
                        newest = ts;
                        playingIdx = i;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        boolean manualHold = mManualKey.length() > 0;
        if (playingIdx >= 0 && idx != playingIdx && (idx < 0 || !manualHold)) {
            idx = playingIdx;
            mIndex = idx;
            switchTo(valid.get(idx));
            return;
        }
        if (idx < 0) {
            idx = playingIdx >= 0 ? playingIdx : 0;
            mIndex = idx;
            switchTo(valid.get(idx));
            return;
        }
        mIndex = idx;
        mCtrl = valid.get(idx);
        updateMedia();
    }

    @SuppressWarnings("unchecked")
    private List<MediaController> allSessions() {
        if (!sGetSessionsChecked) {
            sGetSessionsChecked = true;
            try {
                sGetSessions = MediaSessionManager.class.getMethod(
                        "getSessions", ComponentName.class);
            } catch (Throwable t) {
                sGetSessions = null;
            }
        }
        if (sGetSessions != null) {
            try {
                Object r = sGetSessions.invoke(mMsm, (Object) null);
                if (r instanceof List) {
                    logOnce("session source: getSessions");
                    return (List<MediaController>) r;
                }
            } catch (Throwable t) {
                logOnce("getSessions: " + t);
            }
        }
        try {
            logOnce("session source: getActiveSessions");
            return mMsm.getActiveSessions(null);
        } catch (Throwable t) {
            logOnce("getActiveSessions: " + t);
            return null;
        }
    }

    private static String tokenKey(MediaController c) {
        try {
            return c.getSessionToken().toString();
        } catch (Throwable t) {
            return String.valueOf(System.identityHashCode(c));
        }
    }

    private void mergeSessions(List<MediaController> all) {
        if (all != null) {
            for (MediaController c : all) {
                if (c == null) continue;
                String k = tokenKey(c);
                boolean found = false;
                for (MediaController kc : mKnown) {
                    if (k.equals(tokenKey(kc))) {
                        found = true;
                        break;
                    }
                }
                if (!found) mKnown.add(c);
            }
        }
        java.util.Iterator<MediaController> it = mKnown.iterator();
        while (it.hasNext()) {
            MediaController c = it.next();
            boolean dead = false;
            try {
                PlaybackState ps = c.getPlaybackState();
                if (ps == null || ps.getState() == PlaybackState.STATE_NONE
                        || ps.getState() == PlaybackState.STATE_ERROR) {
                    dead = true;
                }
            } catch (Throwable t) {
                dead = true;
            }
            if (dead) {
                String k = tokenKey(c);
                mSeenPlaying.remove(k);
                mWasPlaying.remove(k);
                mPlayStart.remove(k);
                it.remove();
            }
        }
    }

    private static boolean sameSession(MediaController a, MediaController b) {
        if (a == null || b == null) return false;
        try {
            return a.getSessionToken().equals(b.getSessionToken());
        } catch (Throwable t) {
            return a == b;
        }
    }

    private void switchTo(MediaController c) {
        if (mCtrl != null) {
            try {
                mCtrl.unregisterCallback(mCb);
            } catch (Throwable ignored) {
            }
        }
        mCtrl = c;
        if (c == null) {
            applyState();
            applyVisibility();
            return;
        }
        try {
            c.registerCallback(mCb, mH);
        } catch (Throwable t) {
            IslandHook.log("registerCallback failed: " + t);
        }
        updateMedia();
    }

    private void updateMedia() {
        updateMediaData();
        applyState();
        applyVisibility();
    }

    private void updateMediaData() {
        MediaController c = mCtrl;
        if (c == null) return;
        try {
            PlaybackState ps = c.getPlaybackState();
            MediaMetadata md = c.getMetadata();
            if (ps == null || md == null) return;
            int st = ps.getState();
            if (st == PlaybackState.STATE_NONE || st == PlaybackState.STATE_STOPPED
                    || st == PlaybackState.STATE_ERROR) return;
            CharSequence title = md.getText(MediaMetadata.METADATA_KEY_TITLE);
            CharSequence artist = md.getText(MediaMetadata.METADATA_KEY_ARTIST);
            if (artist == null || artist.length() == 0) {
                artist = md.getText(MediaMetadata.METADATA_KEY_ALBUM);
            }
            if (title == null || title.length() == 0) {
                CharSequence disp = md.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                title = disp != null ? disp : "";
            }
            long duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
            Bitmap art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);

            boolean playing = st == PlaybackState.STATE_PLAYING;
            if (playing && !mLastPlaying) mMediaStartAt = SystemClock.elapsedRealtime();
            mLastPlaying = playing;

            mView.setMedia(title == null ? "" : title.toString(),
                    artist == null ? "" : artist.toString(),
                    art,
                    duration,
                    playing,
                    ps.getPosition(),
                    ps.getPlaybackSpeed(),
                    ps.getLastPositionUpdateTime());
        } catch (Throwable t) {
            IslandHook.log("updateMedia failed: " + t);
        }
    }

    private boolean hasMedia() {
        if (mCtrl == null) return false;
        try {
            PlaybackState ps = mCtrl.getPlaybackState();
            return ps != null && ps.getState() != PlaybackState.STATE_NONE
                    && ps.getState() != PlaybackState.STATE_STOPPED
                    && ps.getState() != PlaybackState.STATE_ERROR;
        } catch (Throwable t) {
            return false;
        }
    }

    private String mediaPkg() {
        try {
            return mCtrl == null ? null : mCtrl.getPackageName();
        } catch (Throwable t) {
            return null;
        }
    }

    private Bitmap loadAppIcon(String pkg) {
        if (pkg == null || pkg.length() == 0) return null;
        if (pkg.equals(mIconPkg)) return mIconBmp;
        mIconPkg = pkg;
        mIconBmp = null;
        try {
            Drawable d = mCtx.getPackageManager().getApplicationIcon(pkg);
            int s = Math.max(1, Math.round(mDensity * 32));
            Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, s, s);
            d.draw(c);
            mIconBmp = b;
        } catch (Throwable t) {
            mIconBmp = null;
        }
        return mIconBmp;
    }

    private void handleNav(Intent it) {
        if (!feat(IslandPrefs.K_FEAT_NAV)) return;
        String pkg = it.getStringExtra("pkg");
        if (pkg == null || !NAV_APPS.containsKey(pkg)) return;
        boolean nav = it.getBooleanExtra("nav", false);
        ArrayList<String> tk = it.getStringArrayListExtra("tk");
        ArrayList<String> tv = it.getStringArrayListExtra("tv");
        ArrayList<String> ik = it.getStringArrayListExtra("ik");
        ArrayList<String> iv = it.getStringArrayListExtra("iv");

        StringBuilder raw = new StringBuilder();
        if (tk != null && tv != null) {
            for (int i = 0; i < tk.size() && i < tv.size(); i++) {
                raw.append(tk.get(i)).append('=').append(tv.get(i)).append("; ");
            }
        }
        if (ik != null && iv != null) {
            for (int i = 0; i < ik.size() && i < iv.size(); i++) {
                raw.append('@').append(ik.get(i)).append('=').append(iv.get(i)).append("; ");
            }
        }
        long now = SystemClock.elapsedRealtime();
        if (now - mLastNavLog > 8000) {
            mLastNavLog = now;
            String s = raw.toString();
            if (s.length() > 1200) s = s.substring(0, 1200) + "...";
            IslandHook.log("NAVRAW[" + pkg + "] nav=" + nav + " " + s);
        }

        String dist = pick(tk, tv, "nsdk_rg_v2_guide_panel_dist");
        String unit = pick(tk, tv, "nsdk_rg_v2_guide_panel_dist_unit");
        String road = pick(tk, tv, "nsdk_rg_v2_guide_panel_road_name");
        String extra = pick(tk, tv, "bnav_rg_toolbox_remain_time_and_dist");
        String arrive = pick(tk, tv, "bnav_rg_toolbox_arrive_time");
        String action = "";

        if (tk != null && tv != null) {
            for (int i = 0; i < tk.size() && i < tv.size(); i++) {
                String id = tk.get(i) == null ? "" : tk.get(i).toLowerCase();
                String v = tv.get(i) == null ? "" : tv.get(i).trim();
                if (v.length() == 0) continue;
                if (id.contains("speed") || id.contains("scale") || id.contains("service_area")
                        || id.contains("tunnel") || id.contains("logo") || id.contains("label")) {
                    continue;
                }
                if (dist.length() == 0 && P_DIST.matcher(v).matches()) {
                    dist = v;
                } else if (unit.length() == 0 && id.endsWith("_dist_unit")) {
                    unit = v;
                } else if (action.length() == 0 && v.length() <= 12 && containsAny(v, ACTIONS)) {
                    action = v;
                } else if (road.length() == 0 && v.length() <= 24 && endsWithAny(v, ROAD_HINTS)) {
                    road = v;
                } else if (extra.length() == 0 && P_ETA.matcher(v).matches()) {
                    extra = v;
                }
            }
        }

        String iconResName = "";
        try {
            iconResName = it.getStringExtra("iconResName");
        } catch (Throwable ignored) {
        }
        String fromIcon = actionOfIcon(iconResName);
        if (fromIcon.length() == 0) {
            try {
                String a = it.getStringExtra("action");
                if (a != null) fromIcon = a;
            } catch (Throwable ignored) {
            }
        }
        if (fromIcon.length() > 0) action = fromIcon;
        if ("当前道路".equals(road) || "无名道路".equals(road)) road = "";

        String distText = dist;
        if (unit.length() > 0 && dist.length() > 0 && !dist.contains(unit)) distText = dist + unit;
        if (arrive.length() > 0) {
            extra = extra.length() == 0 ? arrive : extra + " · " + arrive;
        }
        if (now - mLastPickLog > 8000) {
            mLastPickLog = now;
            IslandHook.log("NAVPICK[" + pkg + "] dist=" + distText + " road=" + road
                    + " action=" + action + " extra=" + extra + " icon=" + iconResName
                    + " bmp=" + it.hasExtra("icon"));
        }
        if (distText.length() == 0 && road.length() == 0 && action.length() == 0) {
            if (mNavActive) {
                mNavActive = false;
                applyState();
                applyVisibility();
            }
            return;
        }

        int iconRes = 0;
        if (ik != null && iv != null) {
            for (int i = 0; i < ik.size() && i < iv.size(); i++) {
                String name = ik.get(i).toLowerCase();
                String val = iv.get(i);
                if (val == null || val.equals("0")) continue;
                if (name.contains("turn") || name.contains("maneuver") || name.contains("arrow")) {
                    try {
                        iconRes = Integer.parseInt(val);
                        break;
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        mNavPkg = pkg;
        mNavDist = distText;
        mNavRoad = road;
        mNavAction = action;
        mNavExtra = extra;
        mNavAt = now;
        if (!mNavActive) mNavStartAt = now;
        mNavActive = true;
        Bitmap iconBmp = null;
        try {
            iconBmp = it.getParcelableExtra("icon");
        } catch (Throwable ignored) {
        }
        if (fromIcon.length() > 0) {
            if (actionOfIcon(iconResName).length() > 0 || !isManeuverIcon(iconResName)) {
                mNavIcon = null;
                mNavIconRes = 0;
            } else if (iconBmp != null) {
                mNavIcon = iconBmp;
                mNavIconRes = 0;
            }
        } else if (iconBmp != null && isManeuverIcon(iconResName)) {
            mNavIcon = iconBmp;
            mNavIconRes = 0;
        } else if (iconRes != 0 && iconRes != mNavIconRes) {
            mNavIconRes = iconRes;
            mNavIcon = loadNavIcon(pkg, iconRes);
        } else if (!isManeuverIcon(iconResName)) {
            mNavIcon = null;
            mNavIconRes = 0;
        }
        if (!mHadNav) {
            mHadNav = true;
            mManualKey = "";
        }
        applyState();
        applyVisibility();
    }

    private static String pick(ArrayList<String> tk, ArrayList<String> tv, String id) {
        if (tk == null || tv == null) return "";
        for (int i = 0; i < tk.size() && i < tv.size(); i++) {
            if (id.equals(tk.get(i))) {
                String v = tv.get(i);
                return v == null ? "" : v.trim();
            }
        }
        return "";
    }

    private static boolean isManeuverIcon(String name) {
        if (name == null || name.length() == 0) return true;
        String n = name.toLowerCase();
        return !(n.contains("route_tab") || n.contains("tab_") || n.contains("search")
                || n.contains("home") || n.contains("setting") || n.contains("toolbox")
                || n.contains("fav") || n.contains("voice") || n.contains("menu")
                || n.contains("back") || n.contains("close"));
    }

    private static String actionOfIcon(String name) {
        if (name == null || name.length() == 0) return "";
        String n = name.toLowerCase();
        if (n.contains("uturn") || n.contains("u_turn") || n.contains("turn_around")) return "掉头";
        if (n.contains("roundabout") || n.contains("round_about")) return "环岛";
        if (n.contains("left_front") || n.contains("leftfront") || n.contains("front_left")) return "左前方";
        if (n.contains("right_front") || n.contains("rightfront") || n.contains("front_right")) return "右前方";
        if (n.contains("keep_left")) return "靠左";
        if (n.contains("keep_right")) return "靠右";
        if (n.contains("left")) return "左转";
        if (n.contains("right")) return "右转";
        if (n.contains("straight") || n.contains("ahead") || n.contains("front")) return "直行";
        if (n.contains("arrive") || n.contains("dest") || n.contains("end")) return "到达终点";
        return "";
    }

    private Bitmap loadNavIcon(String pkg, int res) {
        try {
            Resources r = mCtx.getPackageManager().getResourcesForApplication(pkg);
            Drawable d = r.getDrawable(res, null);
            int s = Math.round(mDensity * 48);
            Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, s, s);
            d.draw(c);
            return b;
        } catch (Throwable t) {
            logOnce("nav icon: " + t);
            return null;
        }
    }

    private static boolean containsAny(String v, String[] keys) {
        for (String k : keys) if (v.contains(k)) return true;
        return false;
    }

    private static boolean endsWithAny(String v, String[] keys) {
        for (String k : keys) if (v.endsWith(k)) return true;
        return false;
    }

    private void rebuildActs() {
        mActs.clear();
        if (mNavActive && feat(IslandPrefs.K_FEAT_NAV)) {
            Act a = new Act();
            a.type = IslandView.MODE_NAV;
            a.key = "nav";
            mActs.add(a);
        }
        List<Cap> caps = new ArrayList<>(mCaps.values());
        java.util.Collections.sort(caps, new java.util.Comparator<Cap>() {
            @Override
            public int compare(Cap x, Cap y) {
                return Long.compare(y.updatedAt, x.updatedAt);
            }
        });
        for (Cap c : caps) {
            if (!capAllowed(c)) continue;
            Act a = new Act();
            a.type = IslandView.MODE_CAPSULE;
            a.key = c.key;
            a.cap = c;
            mActs.add(a);
        }
        Set<String> seen = new HashSet<>();
        boolean mediaOn = feat(IslandPrefs.K_FEAT_MEDIA);
        if (mediaOn && mCtrl != null && hasMedia()) {
            Act a = new Act();
            a.type = IslandView.MODE_MEDIA;
            a.key = tokenKey(mCtrl);
            a.ctrl = mCtrl;
            mActs.add(a);
            seen.add(a.key);
        }
        for (MediaController c : mSessions) {
            if (c == null || !mediaOn) continue;
            String k = tokenKey(c);
            if (seen.contains(k)) continue;
            Act a = new Act();
            a.type = IslandView.MODE_MEDIA;
            a.key = k;
            a.ctrl = c;
            mActs.add(a);
            seen.add(k);
        }
    }

    private void applyState() {
        long now = SystemClock.elapsedRealtime();
        if (mTransientActive) {
            if (now < mTransientUntil) {
                int old = mPrimary;
                mPrimary = IslandView.MODE_TRANSIENT;
                if (mPrimary != old) mView.snapshotForSwap();
                mView.setMode(IslandView.MODE_TRANSIENT);
                mView.setTransient(mTransientKind, mTransientValue,
                        mTransientText, mTransientIcon);
                mView.setSecondary(0, null);
                if (mPrimary != old) mView.pulse(1);
                return;
            }
            mTransientActive = false;
        }
        rebuildActs();
        Set<String> keys = new HashSet<>();
        for (Act a : mActs) keys.add(a.key);
        boolean newAct = false;
        for (String k : keys) {
            if (!mPrevActKeys.contains(k)) {
                newAct = true;
                break;
            }
        }
        mPrevActKeys.clear();
        mPrevActKeys.addAll(keys);
        if (newAct) mManualKey = "";

        if (mActs.isEmpty()) {
            mView.setSecondary(0, null);
            mCap = null;
            mSelKey = "";
            return;
        }
        Act sel = null;
        if (mManualKey.length() > 0) {
            for (Act a : mActs) {
                if (a.key.equals(mManualKey)) {
                    sel = a;
                    break;
                }
            }
        }
        if (sel == null || isAppForeground(actPkg(sel))) {
            sel = null;
            for (Act a : mActs) {
                if (!isAppForeground(actPkg(a))) {
                    sel = a;
                    break;
                }
            }
        }
        if (sel == null) {
            mView.setSecondary(0, null);
            mCap = null;
            mSelKey = "";
            return;
        }
        mSelKey = sel.key;
        mSelIndex = mActs.indexOf(sel);
        int oldPrimary = mPrimary;
        mPrimary = sel.type;

        if (mPrimary != oldPrimary) mView.snapshotForSwap();
        mView.setMode(mPrimary);

        if (sel.type == IslandView.MODE_NAV) {
            mView.setNav(mNavDist, mNavRoad, mNavAction, mNavExtra, mNavIcon);
        } else if (sel.type == IslandView.MODE_CAPSULE) {
            mCap = sel.cap;
            setCapsuleData(sel.cap);
        } else {
            if (!sameSession(mCtrl, sel.ctrl)) bindMedia(sel.ctrl);
            updateMediaData();
        }

        syncSlots();
        Act pillAct = findAct(mPillKey);
        Act dotAct = findAct(mDotKey);
        mPillAct = pillAct;
        mDotAct = dotAct;
        if (pillAct != null) {
            mView.setPillSnap(true, pillAct.type, pillTitle(pillAct), pillRight(pillAct),
                    pillAct.type == IslandView.MODE_MEDIA ? artOf(pillAct.ctrl) : null,
                    pillAct.type == IslandView.MODE_CAPSULE && pillAct.cap != null
                            ? pillAct.cap.icon : null,
                    pillAct.type == IslandView.MODE_CAPSULE && pillAct.cap != null
                            ? pillAct.cap.bg : 0xFF2A2A30,
                    pillAct.type == IslandView.MODE_CAPSULE && pillAct.cap != null
                            && pillAct.cap.delivery);
        } else {
            mView.setPillSnap(false, 0, "", "", null, null, 0, false);
        }

        Act dotShown = mExpandedState ? dotAct : (pillAct != null ? pillAct : dotAct);
        mDotShown = dotShown;
        if (dotShown != null) {
            int secType = dotShown.type == IslandView.MODE_MEDIA ? 1
                    : (dotShown.type == IslandView.MODE_NAV ? 2 : 3);
            Bitmap secIcon = dotShown.type == IslandView.MODE_CAPSULE && dotShown.cap != null
                    ? dotShown.cap.icon
                    : (dotShown.type == IslandView.MODE_MEDIA ? artOf(dotShown.ctrl) : null);
            mView.setSecondary(secType, secIcon);
        } else {
            mView.setSecondary(0, null);
        }
        if (mPrimary != oldPrimary) mView.pulse(1);
    }

    private Act findAct(String key) {
        if (key == null || key.length() == 0) return null;
        for (Act a : mActs) if (a.key.equals(key)) return a;
        return null;
    }

    private void syncSlots() {
        for (int i = mHidden.size() - 1; i >= 0; i--) {
            String k = mHidden.get(i);
            if (k.equals(mSelKey) || k.equals(mPillKey) || k.equals(mDotKey)
                    || findAct(k) == null) {
                mHidden.remove(i);
            }
        }
        if (mSelKey.length() == 0) {
            mPillKey = "";
            mDotKey = "";
            return;
        }
        if (mPillKey.equals(mSelKey) || findAct(mPillKey) == null) mPillKey = "";
        if (mDotKey.equals(mSelKey) || mDotKey.equals(mPillKey)
                || findAct(mDotKey) == null) mDotKey = "";
        while (mPillKey.length() == 0 && !mHidden.isEmpty()) {
            String k = mHidden.remove(0);
            if (!k.equals(mSelKey)) mPillKey = k;
        }
        while (mDotKey.length() == 0 && !mHidden.isEmpty()) {
            String k = mHidden.remove(0);
            if (!k.equals(mSelKey) && !k.equals(mPillKey)) mDotKey = k;
        }
        for (Act a : mActs) {
            if (a.key.equals(mSelKey)) continue;
            if (mPillKey.length() == 0) {
                mPillKey = a.key;
            } else if (mDotKey.length() == 0 && !a.key.equals(mPillKey)) {
                mDotKey = a.key;
            }
        }
        for (Act a : mActs) {
            String k = a.key;
            if (k.equals(mSelKey) || k.equals(mPillKey) || k.equals(mDotKey)) continue;
            if (!mHidden.contains(k)) mHidden.add(k);
        }
    }

    private String promoteSlot(String tapped) {
        if (tapped == null || tapped.length() == 0) return "";
        String oldCard = mSelKey;
        String refill;
        if (!mHidden.isEmpty()) {
            refill = mHidden.remove(0);
            if (oldCard.length() > 0 && !oldCard.equals(refill)) mHidden.add(oldCard);
        } else {
            refill = oldCard;
        }
        mManualKey = tapped;
        mManualAt = SystemClock.elapsedRealtime();
        return refill;
    }

    private String pillTitle(Act a) {
        if (a == null) return "";
        if (a.type == IslandView.MODE_CAPSULE) {
            return a.cap == null ? "" : a.cap.title;
        }
        if (a.type == IslandView.MODE_NAV) {
            return mNavAction.length() > 0 ? mNavAction : "导航中";
        }
        try {
            String t = a.ctrl == null ? null
                    : a.ctrl.getMetadata() == null ? null
                    : String.valueOf(a.ctrl.getMetadata()
                    .getString(android.media.MediaMetadata.METADATA_KEY_TITLE));
            return t == null || "null".equals(t) ? "音乐" : t;
        } catch (Throwable t) {
            return "音乐";
        }
    }

    private String pillRight(Act a) {
        if (a == null) return "";
        if (a.type == IslandView.MODE_CAPSULE && a.cap != null) {
            return a.cap.delivery ? "" : a.cap.content;
        }
        if (a.type == IslandView.MODE_NAV) return mNavDist;
        return "";
    }

    @Override
    public void tapPill() {
        if (mPillKey.length() == 0) return;
        String tapped = mPillKey;
        mPillKey = promoteSlot(tapped);
        mView.snapshotForSwap();
        applyState();
        mView.pulse(1);
        applyVisibility();
        IslandHook.log("tap pill -> card " + tapped + " pill<-" + mPillKey);
    }

    private Act nextSelectable(int step) {
        if (mActs.size() < 2) return null;
        int n = mActs.size();
        for (int i = 1; i < n; i++) {
            Act a = mActs.get((mSelIndex + i * step + n * 8) % n);
            if (!a.key.equals(mSelKey) && !isAppForeground(actPkg(a))) return a;
        }
        return null;
    }

    private String actPkg(Act a) {
        if (a == null) return null;
        if (a.type == IslandView.MODE_NAV) return mNavPkg;
        if (a.type == IslandView.MODE_CAPSULE) return a.cap == null ? null : a.cap.pkg;
        try {
            return a.ctrl == null ? null : a.ctrl.getPackageName();
        } catch (Throwable t) {
            return null;
        }
    }

    private void setCapsuleData(Cap cap) {
        if (cap == null) return;
        String[] labels = new String[cap.actions.length];
        for (int i = 0; i < cap.actions.length; i++) {
            CharSequence t = cap.actions[i] == null ? null : cap.actions[i].title;
            labels[i] = t == null ? "" : t.toString();
        }
        long capTime = cap.timeMs;
        if (cap.type == 2 && !cap.paused) {
            long dt = Math.max(0, SystemClock.elapsedRealtime() - cap.updatedAt);
            capTime = cap.countdown ? Math.max(0, capTime - dt) : capTime + dt;
        }
        mView.setCapsule(cap.title, cap.content, cap.icon, cap.type,
                cap.bg, capTime, cap.countdown, cap.paused,
                cap.progress, cap.progressMax, cap.indeterminate, labels);
        mView.setDelivery(cap.delivery, cap.pct, cap.indicator, cap.nodes,
                cap.barColor, cap.barBg, cap.barType, cap.pic, cap.extendText);
    }

    private Bitmap artOf(MediaController c) {
        if (c == null) return null;
        String key = tokenKey(c);
        if (key.equals(mSecArtKey)) return mSecArtBmp;
        mSecArtKey = key;
        mSecArtBmp = null;
        try {
            MediaMetadata md = c.getMetadata();
            if (md == null) return null;
            Bitmap b = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            mSecArtBmp = b;
        } catch (Throwable ignored) {
        }
        return mSecArtBmp;
    }

    private void bindMedia(MediaController c) {
        if (mCtrl != null) {
            try {
                mCtrl.unregisterCallback(mCb);
            } catch (Throwable ignored) {
            }
        }
        mCtrl = c;
        if (c != null) {
            try {
                c.registerCallback(mCb, mH);
            } catch (Throwable t) {
                IslandHook.log("registerCallback failed: " + t);
            }
        }
    }

    private String primaryPkg() {
        if (mSelKey.length() == 0) return null;
        for (Act a : mActs) {
            if (a.key.equals(mSelKey)) return actPkg(a);
        }
        return null;
    }

    private void applyVisibility() {
        PowerManager pm = (PowerManager) mCtx.getSystemService(Context.POWER_SERVICE);
        boolean screenOn = pm == null || pm.isInteractive();
        if (mTransientActive) {
            if (screenOn) {
                if (!mAdded) showWindow();
            } else {
                hide();
            }
            return;
        }
        String pkg = primaryPkg();
        if (pkg == null) {
            hide();
            return;
        }
        boolean show = screenOn && !mShadeExpanded && !isAppForeground(pkg);
        if (show) {
            if (!mAdded) showWindow();
        } else {
            hide();
        }
    }

    private void showWindow() {
        if (mAdded || mWm == null) return;
        if (mView.isAttachedToWindow()) {
            mAdded = true;
            mView.onShown();
            return;
        }
        View statusBar = findStatusBarView();
        mCapsule = findCapsule(statusBar);
        setCapsuleHidden(true);
        IBinder token = statusBar != null ? statusBar.getWindowToken() : null;

        int w = mView.mainWidthPx();
        int h = mView.windowHeight();
        int[] types = {
                2014,
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        };
        for (int type : types) {
            int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            if (mBlurSupported) flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, type,
                    flags, PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = mY;
            lp.setTitle("LiveIsland");
            if (type == 2014) lp.token = token;
            if (mBlurSupported) {
                try {
                    lp.setBlurBehindRadius(0);
                } catch (Throwable ignored) {
                }
            }
            try {
                lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            } catch (Throwable ignored) {
            }
            try {
                mWm.addView(mView, lp);
                mLp = lp;
                mAdded = true;
                mBlur = false;
                if (mBlurSupported) {
                    setBlur(true);
                    mView.setSystemBlur(true);
                }
                mWinType = type;
                mExpandedState = false;
                mView.onShown();
                IslandHook.log("island shown type=" + type + " " + w + "x" + h
                        + " y=" + mY + " sbh=" + mStatusBarH);
                showTouchWindow(false);
                return;
            } catch (Throwable t) {
                IslandHook.log("addView type=" + type + " failed: " + t);
            }
        }
    }

    private void hide() {
        if (!mAdded || mWm == null) return;
        IslandHook.log("island hide");
        removeTouchWindow();
        setCapsuleHidden(false);
        try {
            mWm.removeView(mView);
        } catch (Throwable t) {
            IslandHook.log("removeView failed: " + t);
        }
        mAdded = false;
        mBlur = false;
        mExpandedState = false;
        mView.reset();
        mView.onHidden();
        clearBackdrop();
    }

    @Override
    public void resizeWindow(int w, int h) {
        if (!mTouchAdded || mTouchLp == null) return;
        if (mTouchExpanded) return;
        if (w <= 0 || h <= 0) return;
        if (mTouchLp.width == w && mTouchLp.height == h) return;
        mTouchLp.width = w;
        mTouchLp.height = h;
        try {
            mWm.updateViewLayout(mTouchView, mTouchLp);
        } catch (Throwable t) {
            logOnce("touch resize: " + t);
        }
    }

    private void showTouchWindow(boolean expanded) {
        if (mTouchAdded || !mAdded || mWm == null) return;
        if (mTouchView == null) {
            mTouchView = new View(mCtx);
            mTouchView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent ev) {
                    if (mExpandedState) return mView.onCardTouch(ev);
                    return mView.onPillTouch(ev);
                }
            });
        }
        int w = Math.max(1, expanded ? mView.mainWidthPx() : mView.windowWidth());
        int h = Math.max(1, expanded ? mView.mainHeightPx() : mView.windowHeight());
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, mWinType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = mY;
        lp.setTitle(expanded ? "LiveIslandCardTouch" : "LiveIslandTouch");
        try {
            mWm.addView(mTouchView, lp);
            mTouchLp = lp;
            mTouchAdded = true;
            mTouchExpanded = expanded;
        } catch (Throwable t) {
            IslandHook.log("touch window failed: " + t);
        }
    }

    private void removeTouchWindow() {
        if (!mTouchAdded || mWm == null) return;
        try {
            mWm.removeView(mTouchView);
        } catch (Throwable t) {
            logOnce("remove touch: " + t);
        }
        mTouchAdded = false;
        mTouchLp = null;
    }

    private void setMainTouchable(boolean touchable) {
        if (mLp == null || !mAdded) return;
        int flags = mLp.flags;
        if (touchable) {
            flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        if (flags == mLp.flags) return;
        mLp.flags = flags;
        try {
            mWm.updateViewLayout(mView, mLp);
        } catch (Throwable t) {
            logOnce("touchable: " + t);
        }
    }

    @Override
    public void onExpandBegin() {
        mExpandedState = true;
        mPrewarm = false;
        removeTouchWindow();
        setMainTouchable(false);
        resizeMainWindow(mView.mainWidthPx(), mView.mainHeightPx());
        showTouchWindow(true);
    }

    @Override
    public void prewarmExpand() {
        if (!mAdded || mExpandedState) return;
        mPrewarm = true;
        resizeMainWindow(mView.mainWidthPx(), mView.mainHeightPx());
    }

    @Override
    public void onPillGestureEnd() {
        mPrewarm = false;
        syncCollapsedMainWindow();
    }

    @Override
    public void onCollapseEnd() {
        mExpandedState = false;
        mPrewarm = false;
        removeTouchWindow();
        setMainTouchable(false);
        resizeMainWindow(mView.mainWidthPx(), mView.windowHeight());
        showTouchWindow(false);
    }

    private void syncCollapsedMainWindow() {
        if (!mAdded || mExpandedState || mPrewarm) return;
        resizeMainWindow(mView.mainWidthPx(), mView.windowHeight());
    }

    private void resizeMainWindow(int w, int h) {
        if (mLp == null || !mAdded || w <= 0 || h <= 0) return;
        if (mLp.width == w && mLp.height == h) return;
        mLp.width = w;
        mLp.height = h;
        try {
            mWm.updateViewLayout(mView, mLp);
        } catch (Throwable t) {
            logOnce("main resize: " + t);
        }
    }

    @Override
    public void setBlur(boolean on) {
        if (!mBlurSupported || mLp == null || mBlur == on) return;
        mBlur = on;
        try {
            mLp.setBlurBehindRadius(on ? Math.round(mDensity * 42) : 0);
        } catch (Throwable ignored) {
        }
        if (mAdded) {
            try {
                mWm.updateViewLayout(mView, mLp);
            } catch (Throwable t) {
                logOnce("blur update: " + t);
            }
        }
    }

    @Override
    public void onExpandStart() {
    }

    private void captureBackdrop() {
        if (mCapturing) return;
        if (mBackdrop != null && SystemClock.elapsedRealtime() - mBackdropAt < BACKDROP_TTL_MS) {
            mView.setBackdropBlur(mBackdrop);
            return;
        }
        final int w = mView.cardWidthPx();
        final int h = mView.cardHeightPx();
        if (w <= 0 || h <= 0) return;
        final int screenW = mCtx.getResources().getDisplayMetrics().widthPixels;
        final int x = Math.max(0, (screenW - w) / 2);
        final int y = mY + mView.cardTopPx();
        final int pillW = mView.windowWidth();
        final int pillH = mView.windowHeight();
        final int gen = ++mCaptureGen;
        mCapturing = true;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = null;
                Bitmap blur = null;
                try {
                    bmp = captureRect(x, y, w, h);
                    if (bmp != null) {
                        try {
                            bmp = bmp.copy(Bitmap.Config.ARGB_8888, true);
                        } catch (Throwable ignored) {
                        }
                    }
                    if (bmp != null) {
                        float k = bmp.getWidth() / (float) w;
                        patchPill(bmp, (int) (Math.max(0, (w - pillW) / 2) * k), 0,
                                (int) (pillW * k), (int) ((pillH + 8) * k));
                    }
                    blur = IslandView.glassOf(bmp);
                } finally {
                    mCapturing = false;
                    if (bmp != null && bmp != blur) {
                        try {
                            bmp.recycle();
                        } catch (Throwable ignored) {
                        }
                    }
                }
                final Bitmap result = blur;
                if (result == null) return;
                mH.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != mCaptureGen) return;
                        mBackdrop = result;
                        mBackdropAt = SystemClock.elapsedRealtime();
                        mView.setBackdropBlur(result);
                    }
                });
            }
        }, "LiveIslandCapture");
        t.setDaemon(true);
        t.start();
    }

    private static final long BACKDROP_REFRESH_MS = 300;
    private static final long BACKDROP_STALE_MS = 6000;
    private boolean mPillGlassBusy;
    private boolean mBgPending;
    private String mBackdropFg = "";

    private final Runnable mBgSnap = new Runnable() {
        @Override
        public void run() {
            refreshBackdropCollapsed(true);
        }
    };

    private void refreshBackdropCollapsed() {
        refreshBackdropCollapsed(false);
    }

    private void refreshBackdropCollapsed(boolean force) {
        if (mView != null && mView.isSystemBlur()) return;
        if (!mAdded || mExpandedState || mPrewarm) return;
        if (mPillGlassBusy) {
            mBgPending = true;
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (force) {
            if (now - mBackdropAt < 60) return;
        } else {
            if (now - mBackdropAt < BACKDROP_REFRESH_MS) return;
            String fg = mForegroundPkg == null ? "" : mForegroundPkg;
            boolean fgChanged = !fg.equals(mBackdropFg);
            boolean stale = now - mBackdropAt > BACKDROP_STALE_MS;
            if (!fgChanged && !stale && mBackdrop != null) return;
        }
        if (mView.getVisibility() != View.VISIBLE) return;
        final int w = mView.cardWidthPx();
        final int h = mView.cardHeightPx();
        if (w <= 0 || h <= 0) return;
        final int screenW = mCtx.getResources().getDisplayMetrics().widthPixels;
        final int x = Math.max(0, (screenW - w) / 2);
        final int y = mY + mView.cardTopPx();
        final long stamp = now;
        mPillGlassBusy = true;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = null;
                Bitmap blur = null;
                try {
                    bmp = captureRect(x, y, w, h);
                    blur = IslandView.glassOf(bmp);
                } finally {
                    mPillGlassBusy = false;
                    if (mBgPending) {
                        mBgPending = false;
                        mH.post(mBgSnap);
                    }
                    if (bmp != null && bmp != blur) {
                        try {
                            bmp.recycle();
                        } catch (Throwable ignored) {
                        }
                    }
                }
                if (blur == null) return;
                final Bitmap shot = blur;
                mH.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mExpandedState || mPrewarm) return;
                        mBackdrop = shot;
                        mBackdropAt = stamp;
                        mBackdropFg = mForegroundPkg == null ? "" : mForegroundPkg;
                        mView.setBackdropBlur(shot);
                    }
                });
            }
        }, "LiveIslandBackdrop");
        t.setDaemon(true);
        t.start();
    }

    private void patchPill(Bitmap bmp, int px, int py, int pw, int ph) {
        try {
            if (px < 0 || pw <= 0) return;
            int right = Math.min(bmp.getWidth(), px + pw);
            int bottom = Math.min(bmp.getHeight(), py + ph);
            if (right <= px || bottom <= py) return;
            int sampleY = Math.min(bmp.getHeight() - 1, bottom + 8);
            int color = bmp.getPixel(bmp.getWidth() / 2, sampleY);
            Canvas c = new Canvas(bmp);
            Paint p = new Paint();
            p.setColor(color);
            c.drawRect(px, py, right, bottom, p);
        } catch (Throwable t) {
            logOnce("patchPill: " + t);
        }
    }

    private Bitmap captureRect(int x, int y, int w, int h) {
        int rot = 0;
        int dw = mCtx.getResources().getDisplayMetrics().widthPixels;
        int dh = mCtx.getResources().getDisplayMetrics().heightPixels;
        try {
            Display d = mWm.getDefaultDisplay();
            rot = d.getRotation();
            android.util.DisplayMetrics real = new android.util.DisplayMetrics();
            d.getRealMetrics(real);
            dw = real.widthPixels;
            dh = real.heightPixels;
        } catch (Throwable ignored) {
        }
        android.graphics.Rect crop = new android.graphics.Rect(x, y, x + w, y + h);
        int rw = Math.max(1, w / 2);
        int rh = Math.max(1, h / 2);

        Bitmap b = callScreenshot("screenshot_ext_hw",
                new Class[]{android.graphics.Rect.class, int.class, int.class, int.class,
                        boolean.class},
                new Object[]{crop, rw, rh, rot, false});
        if (b != null && b.getWidth() > 0) return b;

        b = callScreenshot("screenshot_ext_hw",
                new Class[]{android.graphics.Rect.class, int.class, int.class, int.class},
                new Object[]{crop, rw, rh, rot});
        if (b != null && b.getWidth() > 0) return b;

        b = callScreenshot("screenshot_ext_hw",
                new Class[]{android.graphics.Rect.class, int.class, int.class, int.class,
                        boolean.class},
                new Object[]{crop, w, h, rot, false});
        if (b != null && b.getWidth() > 0) return b;

        b = callScreenshot("screenshot_ext_hw",
                new Class[]{android.graphics.Rect.class, int.class, int.class, int.class},
                new Object[]{crop, w, h, rot});
        if (b != null) return b;

        Bitmap full = callScreenshot("screenshot_ext_hw",
                new Class[]{android.graphics.Rect.class, int.class, int.class, int.class,
                        boolean.class},
                new Object[]{new android.graphics.Rect(0, 0, dw, dh), dw, dh, rot, false});
        if (full == null) {
            full = callScreenshot("screenshot",
                    new Class[]{android.graphics.Rect.class, int.class, int.class, int.class},
                    new Object[]{new android.graphics.Rect(0, 0, dw, dh), dw, dh, rot});
        }
        if (full == null) {
            full = callScreenshot("screenshot",
                    new Class[]{android.graphics.Rect.class, int.class, int.class},
                    new Object[]{new android.graphics.Rect(0, 0, dw, dh), dw, dh});
        }
        if (full != null) {
            try {
                return Bitmap.createBitmap(full, Math.min(x, full.getWidth() - 1),
                        Math.min(y, full.getHeight() - 1),
                        Math.min(w, full.getWidth() - x),
                        Math.min(h, full.getHeight() - y));
            } catch (Throwable t) {
                return full;
            }
        }
        return null;
    }

    private Bitmap callScreenshot(String name, Class[] sig, Object[] args) {
        try {
            Class<?> sc = Class.forName("android.view.SurfaceControl");
            java.lang.reflect.Method m = sc.getMethod(name, sig);
            Object r = m.invoke(null, args);
            if (r instanceof Bitmap) {
                logOnce("capture ok: " + name + "/" + sig.length);
                return (Bitmap) r;
            }
        } catch (Throwable t) {
            logOnce("capture " + name + "/" + sig.length + ": " + t);
        }
        return null;
    }

    private void clearBackdrop() {
        mCaptureGen++;
        mBackdrop = null;
        mBackdropAt = 0;
        mView.setBackdrop(null);
    }

    @SuppressWarnings("unchecked")
    private View findStatusBarView() {
        try {
            Class<?> cls = Class.forName("android.view.WindowManagerGlobal");
            Object global = cls.getMethod("getInstance").invoke(null);
            java.lang.reflect.Field fv = cls.getDeclaredField("mViews");
            fv.setAccessible(true);
            java.lang.reflect.Field fp = cls.getDeclaredField("mParams");
            fp.setAccessible(true);
            java.util.ArrayList<View> views = (java.util.ArrayList<View>) fv.get(global);
            java.util.ArrayList<WindowManager.LayoutParams> params =
                    (java.util.ArrayList<WindowManager.LayoutParams>) fp.get(global);
            if (views == null || params == null) return null;
            synchronized (views) {
                for (int i = 0; i < views.size() && i < params.size(); i++) {
                    WindowManager.LayoutParams lp = params.get(i);
                    if (lp != null && lp.type == WindowManager.LayoutParams.TYPE_STATUS_BAR) {
                        return views.get(i);
                    }
                }
            }
        } catch (Throwable t) {
            logOnce("findStatusBarView: " + t);
        }
        return null;
    }

    private View findCapsule(View statusBar) {
        if (statusBar == null) return null;
        try {
            int id = mCtx.getResources().getIdentifier(
                    "capsule_root", "id", "com.android.systemui");
            if (id == 0) return null;
            return statusBar.findViewById(id);
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void setCapsuleHidden(boolean hidden) {
        try {
            Class<?> cls = Class.forName("android.view.WindowManagerGlobal");
            Object global = cls.getMethod("getInstance").invoke(null);
            java.lang.reflect.Field fv = cls.getDeclaredField("mViews");
            fv.setAccessible(true);
            java.util.ArrayList<View> views = (java.util.ArrayList<View>) fv.get(global);
            if (views == null) return;
            int id = mCtx.getResources().getIdentifier(
                    "capsule_root", "id", "com.android.systemui");
            if (id == 0) return;
            synchronized (views) {
                int before = mCapsuleViews.size();
                for (View root : views) {
                    if (root == null) continue;
                    View c = root.findViewById(id);
                    if (c == null) continue;
                    if (hidden) {
                        if (!mCapsuleViews.contains(c)) mCapsuleViews.add(c);
                        if (c.getVisibility() != View.GONE) c.setVisibility(View.GONE);
                    } else if (mCapsuleViews.contains(c)) {
                        if (c.getVisibility() == View.GONE) c.setVisibility(View.VISIBLE);
                    }
                }
                if (hidden && mCapsuleViews.size() != before) {
                    IslandHook.log("hidden capsule views=" + mCapsuleViews.size());
                }
            }
            if (!hidden) mCapsuleViews.clear();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void playPause() {
        MediaController c = mCtrl;
        if (c == null) return;
        try {
            MediaController.TransportControls tc = c.getTransportControls();
            if (c.getPlaybackState() != null
                    && c.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) {
                tc.pause();
            } else {
                tc.play();
            }
        } catch (Throwable t) {
            IslandHook.log("playPause failed: " + t);
        }
    }

    @Override
    public void next() {
        MediaController c = mCtrl;
        if (c == null) return;
        try {
            c.getTransportControls().skipToNext();
        } catch (Throwable t) {
            IslandHook.log("next failed: " + t);
        }
    }

    @Override
    public void prev() {
        MediaController c = mCtrl;
        if (c == null) return;
        try {
            c.getTransportControls().skipToPrevious();
        } catch (Throwable t) {
            IslandHook.log("prev failed: " + t);
        }
    }

    @Override
    public void seekTo(long ms) {
        MediaController c = mCtrl;
        if (c == null) return;
        try {
            c.getTransportControls().seekTo(ms);
        } catch (Throwable t) {
            IslandHook.log("seekTo failed: " + t);
        }
    }

    @Override
    public void openCurrent() {
        String pkg = primaryPkg();
        if (pkg == null) return;
        hide();
        try {
            Intent i = mCtx.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        | Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
                mCtx.startActivity(i);
                IslandHook.log("open " + pkg + " via startActivity");
                return;
            }
        } catch (Throwable t) {
            IslandHook.log("open startActivity failed: " + t);
        }
        try {
            ActivityManager am = (ActivityManager) mCtx.getSystemService(
                    Context.ACTIVITY_SERVICE);
            List<ActivityManager.RecentTaskInfo> recents =
                    am.getRecentTasks(20, ActivityManager.RECENT_WITH_EXCLUDED);
            for (ActivityManager.RecentTaskInfo info : recents) {
                if (info.baseIntent != null && info.baseIntent.getComponent() != null
                        && pkg.equals(info.baseIntent.getComponent().getPackageName())) {
                    am.moveTaskToFront(info.persistentId, 0);
                    IslandHook.log("open " + pkg + " via moveTaskToFront");
                    return;
                }
            }
        } catch (Throwable t) {
            IslandHook.log("open moveTaskToFront failed: " + t);
        }
        IslandHook.log("open failed for " + pkg);
    }

    @Override
    public void switchActivity() {
        Act next = mDotShown != null ? mDotShown : nextSelectable(1);
        if (next == null) return;
        String refill = promoteSlot(next.key);
        if (mDotKey.equals(next.key)) {
            mDotKey = refill;
        } else if (mPillKey.equals(next.key)) {
            mPillKey = refill;
        }
        mView.snapshotForSwap();
        applyState();
        mView.pulse(1);
        applyVisibility();
        IslandHook.log("switch activity -> " + next.type + " " + next.key
                + " dot=" + mDotKey + " pill=" + mPillKey + " hidden=" + mHidden.size());
    }

    @Override
    public void capsuleAction(int index) {
        Cap c = mCap;
        if (c == null || index < 0 || index >= c.actions.length) return;
        if (DEMO_KEY.equals(c.key)) {
            openCurrent();
            return;
        }
        if (c.synthetic) {
            if (USB_KEY.equals(c.key)) {
                setUsbMode(index == 0 ? USB_MODE_MTP
                        : index == 1 ? USB_MODE_PTP : USB_MODE_CHARGE);
                return;
            }
            String cmd = index == 1 ? "reset" : "toggle";
            try {
                Intent i = new Intent(StopwatchReporter.CMD_ACTION);
                i.setPackage(STOPWATCH_PKG);
                i.putExtra("cmd", cmd);
                mCtx.sendBroadcast(i);
                IslandHook.log("stopwatch cmd: " + cmd);
            } catch (Throwable t) {
                IslandHook.log("stopwatch cmd send: " + t);
            }
            return;
        }
        Notification.Action a = c.actions[index];
        if (a == null || a.actionIntent == null) return;
        try {
            a.actionIntent.send();
            IslandHook.log("capsule action: " + a.title);
        } catch (Throwable t) {
            IslandHook.log("capsule action failed: " + t);
        }
    }

    @Override
    public void swipeSession(int dir) {
        IslandHook.log("swipe dir=" + dir + " acts=" + mActs.size()
                + " sessions=" + mSessions.size() + " caps=" + mCaps.size());
        if (mActs.size() >= 2) switchActivity();
    }

    private final Set<String> mLogged = new HashSet<>();

    private void logOnce(String msg) {
        if (mLogged.add(msg)) IslandHook.log(msg);
    }

    private static java.lang.reflect.Method sGetSessions;
    private static boolean sGetSessionsChecked;
}
