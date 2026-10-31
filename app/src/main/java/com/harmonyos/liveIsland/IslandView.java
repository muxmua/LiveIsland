package com.harmonyos.liveIsland;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

public class IslandView extends View {
    static final boolean DEBUG_GLASS = false;
    private long mGlassLogAt;
    static final boolean DEBUG_ANIM = false;

    interface Host {
        void resizeWindow(int w, int h);

        void playPause();

        void next();

        void prev();

        void seekTo(long ms);

        void openCurrent();

        void swipeSession(int dir);

        void switchActivity();

        void capsuleAction(int index);

        void setBlur(boolean on);

        void onExpandStart();

        void onExpandBegin();

        void onCollapseEnd();

        void prewarmExpand();

        void onPillGestureEnd();

        void tapPill();
    }

    static final int MODE_MEDIA = 0;
    static final int MODE_NAV = 1;
    static final int MODE_CAPSULE = 2;
    static final int MODE_TRANSIENT = 3;

    private static final int T_NONE = 0;
    private static final int T_PILL = 1;
    private static final int T_COLLAPSE = 2;
    private static final int T_PREV = 3;
    private static final int T_PLAY = 4;
    private static final int T_NEXT = 5;
    private static final int T_SEEK = 6;
    private static final int T_OPEN = 7;
    private static final int T_SWITCH = 8;
    private static final int T_CAP_ACTION = 9;

    private final Host mHost;
    private final float mD;

    private int mStatusBarH = 64;
    private boolean mBlurSupported = true;

    private final Paint mBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArtPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint mGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint mIcon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mPillTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mPillSub = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mCardTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mCardSub = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mTime = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mNote = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mNavTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mNavRoadPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mNavInfo = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mNavBtn = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mCapTimer = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mCapAction = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mDeliverTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final Rect mSrc = new Rect();
    private final Rect mDst = new Rect();
    private final Path mPath = new Path();
    private final Paint mFakePaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    private float mExpand;
    private boolean mExpanded;
    private ValueAnimator mAnim;
    private float mSwapAlpha = 1f;
    private ValueAnimator mSwapAnim;
    private float mSwapShift;
    private float mSwapDir = 1f;
    private float mSwapT = 1f;
    private float mDrawW = -1f;
    private ValueAnimator mWidthAnim;
    private Bitmap mFake;
    private boolean mSnapshotting;

    private float mBurnX;
    private float mBurnY;
    private int mBurnDir;
    private long mShownAt;
    private float mFade = 1f;
    private Runnable mBurnTick;
    private Runnable mAutoCollapse;
    private long mTouchGuardUntil;

    private boolean mLightBg = true;
    private int mTint = 0x0A0A0C;
    private int mScrimColor = 0xFF000000;
    private float mScrimAlpha = 0.68f;

    private int mBgMode = IslandPrefs.BG_FROSTED;

    void setBgMode(int mode) {
        if (mBgMode == mode) return;
        mBgMode = mode;
        applyFgColors();
        invalidate();
    }

    int bgMode() {
        return mBgMode;
    }

    private Bitmap mBackdrop;
    private Bitmap mBackdropBlur;
    private Bitmap mBackdropPrev;
    private float mCross = 1f;
    private Bitmap mArtBackdrop;
    private float mBackdropAlpha;
    private ValueAnimator mBackdropAnim;

    private Bitmap mNoise;
    private BitmapShader mNoiseShader;
    private final Paint mNoisePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private volatile boolean mSysBlur;
    private int mSysBlurRadius = 64;

    boolean isSystemBlur() {
        return mSysBlur;
    }

    void setSystemBlur(boolean on) {
        if (mSysBlur == on) return;
        mSysBlur = on;
        invalidate();
    }

    private String mTitle = "";
    private String mArtist = "";
    private Bitmap mArt;
    private Bitmap mAppIcon;
    private String mAppPkg = "";
    private long mDuration;
    private long mBasePos;
    private long mBaseTime;
    private float mSpeed = 1f;
    private boolean mPlaying;
    private long mSeekPreview = -1;

    private int mMode = MODE_MEDIA;
    private String mNavDist = "";
    private String mNavRoad = "";
    private String mNavAction = "";
    private String mNavExtra = "";
    private Bitmap mNavIcon;

    private String mCapTitle = "";
    private String mCapContent = "";
    private Bitmap mCapIcon;
    private int mCapType = 1;
    private int mCapBg = 0xFF2A2A30;
    private int mCapFg = 0xFFFFFFFF;
    private long mCapBaseMs;
    private long mCapSetAt;
    private boolean mCapCountdown;
    private boolean mCapPaused;
    private int mCapProgress;
    private int mCapMax;
    private boolean mCapIndeterminate;
    private String[] mCapActions = new String[0];
    private int mCapHitIndex = -1;

    private boolean mCapDelivery;
    private int mCapPct;
    private Bitmap mCapIndicator;
    private Bitmap[] mCapNodes;
    private int mCapBarColor = 0xFFFFD100;
    private int mCapBarBg = 0x40FFFFFF;
    private int mCapBarType = 1;
    private Bitmap mCapPic;
    private String mCapExtend = "";

    private int mSecType = 0;
    private Bitmap mSecIcon;

    private int mTransientKind;
    private int mTransientValue;
    private String mTransientText = "";
    private Bitmap mTransientIcon;
    private final TextPaint mRingText = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private boolean mPillSnapValid;
    private int mPillSnapMode;
    private String mPillSnapTitle = "";
    private String mPillSnapRight = "";
    private Bitmap mPillSnapArt;
    private Bitmap mPillSnapIcon;
    private int mPillSnapBg = 0xFF2A2A30;
    private boolean mPillSnapDelivery;

    private float mDownX;
    private float mDownY;
    private boolean mMoved;
    private boolean mSeeking;
    private int mDownTarget = T_NONE;
    private int mPressedTarget = T_NONE;
    private int mSwipeAction;

    IslandView(Context ctx, Host host) {
        super(ctx);
        mHost = host;
        mD = ctx.getResources().getDisplayMetrics().density;

        mPillTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mPillTitle.setTextSize(dp(14));
        mPillTitle.setColor(0xFFFFFFFF);

        mPillSub.setTextSize(dp(11));
        mPillSub.setColor(0xB3FFFFFF);

        mRingText.setTextSize(dp(7.5f));
        mRingText.setColor(0xFFFFFFFF);
        mRingText.setTextAlign(Paint.Align.CENTER);
        mRingText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        mCardTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mCardTitle.setTextSize(dp(18));
        mCardTitle.setColor(0xFFFFFFFF);

        mCardSub.setTextSize(dp(12));
        mCardSub.setColor(0xCCFFFFFF);

        mTime.setTextSize(dp(10));
        mTime.setColor(0x8AFFFFFF);

        mNote.setTextSize(dp(12));
        mNote.setColor(0xFFB9B9C0);

        mNavTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mNavTitle.setTextSize(dp(24));
        mNavTitle.setColor(0xFFFFFFFF);

        mNavRoadPaint.setTextSize(dp(15));
        mNavRoadPaint.setColor(0xCFFFFFFF);

        mNavInfo.setTextSize(dp(13));
        mNavInfo.setColor(0xB0FFFFFF);

        mNavBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mNavBtn.setTextSize(dp(14));
        mNavBtn.setColor(0xFFFFFFFF);

        mCapTimer.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mCapTimer.setTextSize(dp(30));
        mCapTimer.setColor(0xFFFFFFFF);

        mCapAction.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mCapAction.setTextSize(dp(13));
        mCapAction.setColor(0xFFFFFFFF);

        mDeliverTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mDeliverTitle.setTextSize(dp(16));
        mDeliverTitle.setColor(0xFFFFFFFF);

        applyFgColors();
        ensureNoise();
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    private boolean lightFg() {
        if (mBgMode == IslandPrefs.BG_BLACK) return false;
        if (mMode == MODE_MEDIA && mArtBackdrop != null && !mArtBackdrop.isRecycled()) {
            return false;
        }
        if (mBackdropBlur == null || mBackdropBlur.isRecycled()) return true;
        return mLightBg;
    }

    private int fg() {
        return lightFg() ? 0xFF171A21 : 0xFFFFFFFF;
    }

    private int fgSub() {
        return lightFg() ? 0xFF171A21 : 0xFFFFFFFF;
    }

    private int fgTrack() {
        return lightFg() ? 0x2E171A21 : 0x38FFFFFF;
    }

    private int fgButton() {
        return lightFg() ? 0x14171A21 : 0x1FFFFFFF;
    }

    private void applyFgColors() {
        int fg = fg();
        mPillTitle.setColor(fg);
        mPillSub.setColor(fgSub());
        mRingText.setColor(fg);
        mCardTitle.setColor(fg);
        mCardSub.setColor(fgSub());
        mTime.setColor(fg);
        mNote.setColor(fg);
        mNavTitle.setColor(fg);
        mNavRoadPaint.setColor(fgSub());
        mNavInfo.setColor(fgSub());
        mNavBtn.setColor(fg);
        mCapTimer.setColor(fg);
        mCapAction.setColor(fg);
        mDeliverTitle.setColor(fg);
    }

    private void ensureNoise() {
        if (mNoise != null && !mNoise.isRecycled()) return;
        try {
            int s = 128;
            int[] px = new int[s * s];
            java.util.Random rnd = new java.util.Random(7);
            for (int i = 0; i < px.length; i++) {
                int v = 140 + rnd.nextInt(116);
                int al = 24 + rnd.nextInt(33);
                px[i] = (al << 24) | (v << 16) | (v << 8) | v;
            }
            Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            b.setPixels(px, 0, s, 0, 0, s, s);
            mNoise = b;
            mNoiseShader = new BitmapShader(b,
                    Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        } catch (Throwable t) {
            mNoise = null;
            mNoiseShader = null;
        }
    }

    private float dp(float v) {
        return v * mD;
    }

    private static final class SpringInterpolator implements TimeInterpolator {
        private final float mDamping;
        private final float mOmega;
        private final float mDuration;

        SpringInterpolator(float damping, float response) {
            mDamping = damping;
            mOmega = (float) (2 * Math.PI / response);
            mDuration = Math.max(0.12f, response * 1.15f);
        }

        long durationMs() {
            return (long) (mDuration * 1000f);
        }

        @Override
        public float getInterpolation(float t) {
            if (t <= 0f) return 0f;
            if (t >= 1f) return 1f;
            double x = t * mDuration;
            double z = mDamping;
            double w = mOmega;
            if (z < 1d) {
                double wd = w * Math.sqrt(1d - z * z);
                return (float) (1d - Math.exp(-z * w * x)
                        * (Math.cos(wd * x) + (z * w / wd) * Math.sin(wd * x)));
            }
            if (z == 1d) {
                return (float) (1d - Math.exp(-w * x) * (1d + w * x));
            }
            double s = Math.sqrt(z * z - 1d);
            double r1 = -w * (z - s);
            double r2 = -w * (z + s);
            return (float) (1d - (r2 * Math.exp(r1 * x) - r1 * Math.exp(r2 * x))
                    / (r2 - r1));
        }
    }

    private float areaPad() {
        return dp(8);
    }

    private boolean isTablet() {
        try {
            return getResources().getConfiguration().smallestScreenWidthDp >= 600;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private float areaGap() {
        return isTablet() ? dp(6) : dp(30);
    }

    void setStatusBarHeight(int h) {
        mStatusBarH = h;
        if (!mExpanded) {
            mHost.resizeWindow(windowWidth(), windowHeight());
            invalidate();
        }
    }

    void setBlurSupported(boolean supported) {
        mBlurSupported = supported;
        invalidate();
    }

    float pillHeight() {
        float target = isTablet() ? dp(28) : dp(34);
        return Math.min(target, Math.max(dp(22), mStatusBarH - dp(2)));
    }

    private float secCircleR() {
        return dp(12);
    }

    private float secGap() {
        return dp(4);
    }

    private float sideMargin() {
        return secGap() + secCircleR() * 2 + dp(4);
    }

    private float pillIcon() {
        return isTablet() ? dp(18) : dp(19);
    }

    private float pillWidth() {
        float pad = areaPad();
        float gap = areaGap();
        float icon = Math.min(dp(11), pillHeight() * 0.36f) * 2f;
        float min = isTablet() ? dp(74) : dp(108);
        float max = isTablet() ? dp(180) : dp(250);
        float w;
        if (mMode == MODE_TRANSIENT) {
            float tw = mPillTitle.measureText(pillTransientText());
            if (hasBatteryRing()) {
                w = pad * 2 + icon + dp(5) + tw + dp(7) + ringR() * 2;
            } else {
                w = pad * 2 + icon + gap + tw + dp(6);
            }
        } else if (mMode == MODE_NAV) {
            float left = mPillTitle.measureText(mNavDist.length() == 0 ? mNavAction : mNavDist);
            String right = mNavRoad.length() == 0 ? mNavAction : mNavRoad;
            float rw = mPillSub.measureText(right);
            w = pad * 2 + icon + dp(10) + Math.min(left, dp(96)) + gap
                    + Math.min(rw, dp(90));
        } else if (mMode == MODE_CAPSULE) {
            float tw = mPillTitle.measureText(capPillTitle());
            float rw = mCapType == 1 ? 0 : mPillSub.measureText(capPillRight());
            w = pad * 2 + icon + gap + tw + (rw > 0 ? dp(6) + rw : 0) + dp(14);
        } else {
            float tw = mPillTitle.measureText(mTitle == null ? "" : mTitle);
            float rw = mPlaying ? dp(20) + dp(4) : 0;
            w = pad * 2 + icon + gap + tw + rw;
        }
        return Math.max(min, Math.min(max, w));
    }

    private float cardWidth() {
        return dp(364);
    }

    private float cardHeight() {
        return dp(168);
    }

    int collapsedWindowWidth() {
        float max = isTablet() ? dp(180) : dp(250);
        return Math.round(max + 2 * sideMargin());
    }

    int windowWidth() {
        return Math.round(pillWidth() + 2 * sideMargin());
    }

    int windowHeight() {
        return Math.round(pillHeight());
    }

    int mainWidthPx() {
        return Math.round(cardWidth() + 2 * sideMargin());
    }

    int mainHeightPx() {
        return Math.round(cardTop() + cardHeight());
    }

    int cardWidthPx() {
        return Math.round(cardWidth());
    }

    int cardTopPx() {
        return Math.round(cardTop());
    }

    int cardHeightPx() {
        return mainHeightPx();
    }

    private void animatePillWidth() {
        float target = pillWidth();
        if (mDrawW < 0f) {
            mDrawW = target;
            return;
        }
        if (Math.abs(target - mDrawW) < 0.5f) return;
        if (mWidthAnim != null) mWidthAnim.cancel();
        SpringInterpolator si = new SpringInterpolator(0.82f, 0.40f);
        ValueAnimator va = ValueAnimator.ofFloat(mDrawW, target);
        va.setDuration(si.durationMs());
        va.setInterpolator(si);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mDrawW = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
        va.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (canceled) return;
                if (!mExpanded) mHost.resizeWindow(windowWidth(), windowHeight());
            }
        });
        mWidthAnim = va;
        va.start();
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private float baseline(Paint p, float centerY) {
        return centerY - (p.ascent() + p.descent()) / 2f;
    }

    void setMedia(String title, String artist, Bitmap art, long duration,
                  boolean playing, long position, float speed, long positionUpdateTime) {
        boolean changed = !eq(mTitle, title) || !eq(mArtist, artist);
        mTitle = title == null ? "" : title;
        mArtist = artist == null ? "" : artist;
        mDuration = Math.max(0, duration);
        mPlaying = playing;
        mSpeed = speed > 0 ? speed : 1f;
        mBasePos = Math.max(0, position);
        long elapsed = SystemClock.elapsedRealtime() - positionUpdateTime;
        if (elapsed < 0 || elapsed > 60000) elapsed = 0;
        mBasePos += (long) (elapsed * mSpeed);
        mBaseTime = SystemClock.elapsedRealtime();
        if (mDuration > 0 && mBasePos > mDuration) mBasePos = mDuration;

        if (art != null) {
            try {
                if (art.getWidth() > 256 || art.getHeight() > 256) {
                    mArt = Bitmap.createScaledBitmap(art, 256, 256, true);
                } else {
                    mArt = art;
                }
            } catch (Throwable t) {
                mArt = art;
            }
        } else {
            mArt = null;
        }
        rebuildArtBackdrop();

        if (changed) {
            noteContentChanged();
            animatePillWidth();
        }
        invalidate();
    }

    void setAppIcon(String pkg, Bitmap icon) {
        boolean changed = !eq(mAppPkg, pkg);
        mAppPkg = pkg == null ? "" : pkg;
        if (icon != null) {
            try {
                if (icon.getWidth() > 64 || icon.getHeight() > 64) {
                    mAppIcon = Bitmap.createScaledBitmap(icon, 64, 64, true);
                } else {
                    mAppIcon = icon;
                }
            } catch (Throwable t) {
                mAppIcon = icon;
            }
        } else if (changed) {
            mAppIcon = null;
        }
        if (changed) invalidate();
    }

    void setNav(String distance, String road, String action, String extra, Bitmap icon) {
        boolean changed = !eq(mNavDist, distance) || !eq(mNavRoad, road);
        boolean major = !eq(mNavRoad, road) || !eq(mNavAction, action);
        mNavDist = distance == null ? "" : distance;
        mNavRoad = road == null ? "" : road;
        mNavAction = action == null ? "" : action;
        mNavExtra = extra == null ? "" : extra;
        mNavIcon = icon;
        if (major) noteContentChanged();
        if (changed) animatePillWidth();
        invalidate();
    }

    void setMode(int mode) {
        if (mMode == mode) return;
        mMode = mode;
        applyFgColors();
        noteContentChanged();
        animatePillWidth();
        invalidate();
    }

    void setCapsule(String title, String content, Bitmap icon, int type,
                    int bgColor, long timeMs, boolean countdown, boolean paused,
                    int progress, int max, boolean indeterminate, String[] actions) {
        String t = title == null ? "" : title;
        String c = content == null ? "" : content;
        boolean changed = !eq(mCapTitle, t) || !eq(mCapContent, c) || mCapType != type;
        mCapTitle = t;
        mCapContent = c;
        mCapType = type;
        mCapBg = bgColor == 0 ? 0xFF2A2A30 : bgColor;
        mCapFg = luminance(mCapBg) > 0.62f ? 0xFF101014 : 0xFFFFFFFF;
        if (icon != null) {
            try {
                if (icon.getWidth() > 128 || icon.getHeight() > 128) {
                    mCapIcon = Bitmap.createScaledBitmap(icon, 128, 128, true);
                } else {
                    mCapIcon = icon;
                }
            } catch (Throwable th) {
                mCapIcon = icon;
            }
        } else {
            mCapIcon = null;
        }
        mCapCountdown = countdown;
        mCapPaused = paused;
        mCapBaseMs = Math.max(0, timeMs);
        mCapSetAt = SystemClock.elapsedRealtime();
        mCapProgress = Math.max(0, progress);
        mCapMax = Math.max(0, max);
        mCapIndeterminate = indeterminate;
        mCapActions = actions == null ? new String[0] : actions;

        if (changed) noteContentChanged();
        animatePillWidth();
        invalidate();
    }

    void setDelivery(boolean delivery, int pct, Bitmap indicator, Bitmap[] nodes,
                     int barColor, int barBg, int barType, Bitmap pic, String extendText) {
        boolean changed = mCapDelivery != delivery || mCapPct != pct
                || !eq(mCapExtend, extendText == null ? "" : extendText);
        mCapDelivery = delivery;
        mCapPct = Math.max(0, Math.min(100, pct));
        mCapIndicator = indicator;
        mCapNodes = nodes;
        if (barColor != 0) mCapBarColor = barColor;
        if (barBg != 0) mCapBarBg = barBg;
        mCapBarType = barType;
        mCapPic = pic;
        mCapExtend = extendText == null ? "" : extendText;
        if (changed) noteContentChanged();
        animatePillWidth();
        invalidate();
    }

    private int capsuleCardColor() {
        int r = (mCapBg >> 16) & 0xFF;
        int g = (mCapBg >> 8) & 0xFF;
        int b = mCapBg & 0xFF;
        int nr = 16 + (int) (r * 0.24f);
        int ng = 16 + (int) (g * 0.24f);
        int nb = 18 + (int) (b * 0.24f);
        return 0xFF000000 | (Math.min(255, nr) << 16)
                | (Math.min(255, ng) << 8) | Math.min(255, nb);
    }

    void setTransient(int kind, int value, String text, Bitmap icon) {
        boolean changed = mTransientKind != kind || mTransientValue != value
                || !eq(mTransientText, text);
        mTransientKind = kind;
        mTransientValue = value;
        mTransientText = text == null ? "" : text;
        mTransientIcon = icon;
        if (mMode != MODE_TRANSIENT) {
            mMode = MODE_TRANSIENT;
            changed = true;
        }
        if (mExpanded) {
            mExpanded = false;
            if (mAnim != null) mAnim.cancel();
            mExpand = 0f;
            mHost.onCollapseEnd();
        }
        if (changed) noteContentChanged();
        animatePillWidth();
        invalidate();
    }

    private static float luminance(int color) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    private String capPillTitle() {
        if (mCapTitle.length() > 0) return mCapTitle;
        if (mCapContent.length() > 0) return mCapContent;
        return "通知";
    }

    private String capPillRight() {
        if (mCapDelivery) return "";
        if (mCapType == 2) return fmtCap(capTimeNow());
        if (mCapType == 3) {
            if (mCapIndeterminate || mCapMax <= 0) return "";
            return Math.round(mCapProgress * 100f / mCapMax) + "%";
        }
        return "";
    }

    private long capTimeNow() {
        if (mCapType != 2) return 0;
        long elapsed = SystemClock.elapsedRealtime() - mCapSetAt;
        if (elapsed < 0 || elapsed > 86400000L) elapsed = 0;
        long v = mCapPaused ? mCapBaseMs
                : (mCapCountdown ? mCapBaseMs - elapsed : mCapBaseMs + elapsed);
        return Math.max(0, v);
    }

    private static String fmtCap(long ms) {
        long total = ms / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        String ss = s < 10 ? "0" + s : String.valueOf(s);
        if (h > 0) {
            String mm = m < 10 ? "0" + m : String.valueOf(m);
            return h + ":" + mm + ":" + ss;
        }
        return m + ":" + ss;
    }

    int mode() {
        return mMode;
    }

    void setSecondary(int type, Bitmap icon) {
        boolean changed = mSecType != type;
        mSecType = type;
        mSecIcon = icon;
        if (changed) noteContentChanged();
        invalidate();
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private void rebuildArtBackdrop() {
        try {
            if (mArt == null || mArt.isRecycled()) {
                mArtBackdrop = null;
                return;
            }
            int w = Math.max(1, cardWidthPx());
            int h = Math.max(1, cardHeightPx());
            Bitmap small = Bitmap.createScaledBitmap(mArt, 72, 72, true);
            mArtBackdrop = Bitmap.createScaledBitmap(small, w, h, true);
            if (small != mArtBackdrop) small.recycle();
        } catch (Throwable t) {
            mArtBackdrop = null;
        }
    }

    void setBackdrop(Bitmap b) {
        setBackdropBlur(glassOf(b));
    }

    void setBackdropBlur(Bitmap blur) {
        Bitmap old = mBackdropBlur;
        boolean had = old != null && !old.isRecycled();
        mBackdropBlur = blur;
        if (blur != null) {
            sampleBackdrop(blur);
        }
        if (mBackdropAnim != null) mBackdropAnim.cancel();
        dropPrev();
        if (blur == null) {
            if (old != null && !old.isRecycled()) {
                try {
                    old.recycle();
                } catch (Throwable ignored) {
                }
            }
            mBackdropAlpha = 0f;
            mCross = 1f;
            invalidate();
            return;
        }
        if (!had) {
            mCross = 1f;
            mBackdropAlpha = 0f;
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(140);
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mBackdropAlpha = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            mBackdropAnim = va;
            va.start();
            return;
        }

        mBackdropPrev = old;
        mBackdropAlpha = 1f;
        mCross = 0f;
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(55);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mCross = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mCross = 1f;
                dropPrev();
                invalidate();
            }
        });
        mBackdropAnim = va;
        va.start();
    }

    private void dropPrev() {
        if (mBackdropPrev != null) {
            try {
                if (!mBackdropPrev.isRecycled()) mBackdropPrev.recycle();
            } catch (Throwable ignored) {
            }
            mBackdropPrev = null;
        }
    }

    private void drawCover(Canvas c, Bitmap b, float l, float t, float w, float h, float alpha) {
        if (b == null || b.isRecycled() || alpha <= 0.004f) return;
        float bw = b.getWidth();
        float bh = b.getHeight();
        if (bw <= 0 || bh <= 0) return;
        float sc = Math.max(w / bw, h / bh);
        float dw = bw * sc;
        float dh = bh * sc;
        mRect.set(l + (w - dw) / 2f, t + (h - dh) / 2f,
                l + (w + dw) / 2f, t + (h + dh) / 2f);
        mArtPaint.setAlpha((int) (255 * Math.min(1f, alpha)));
        c.drawBitmap(b, null, mRect, mArtPaint);
    }

    private static Bitmap glassBlur(Bitmap src) {
        int w = Math.max(1, src.getWidth());
        int h = Math.max(1, src.getHeight());

        int sw = Math.max(5, w / 16);
        int sh = Math.max(4, h / 16);
        Bitmap small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(small);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        sc.drawBitmap(src, null, new RectF(0, 0, sw, sh), p);
        int[] px = new int[sw * sh];
        small.getPixels(px, 0, sw, 0, 0, sw, sh);

        boxBlurRgb(px, sw, sh, 1);
        boxBlurRgb(px, sw, sh, 1);
        boxBlurRgb(px, sw, sh, 1);

        boostSaturation(px, 1.23f, 0);
        small.setPixels(px, 0, sw, 0, 0, sw, sh);
        Bitmap out = Bitmap.createScaledBitmap(small, w, h, true);
        if (small != out) small.recycle();
        return out;
    }

    private static void liftBrightness(int[] px, float k) {
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int r = clamp255((int) (((p >> 16) & 0xFF) * k));
            int g = clamp255((int) (((p >> 8) & 0xFF) * k));
            int b = clamp255((int) ((p & 0xFF) * k));
            px[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }

    static void boxBlurRgb(int[] px, int w, int h, int r) {
        int n = 2 * r + 1;
        int[] tmp = new int[px.length];
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int sr = 0, sg = 0, sb = 0;
            for (int k = -r; k <= r; k++) {
                int p = px[row + clampI(k, 0, w - 1)];
                sr += (p >> 16) & 0xFF;
                sg += (p >> 8) & 0xFF;
                sb += p & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                tmp[row + x] = 0xFF000000 | ((sr / n) << 16) | ((sg / n) << 8) | (sb / n);
                int add = px[row + clampI(x + r + 1, 0, w - 1)];
                int sub = px[row + clampI(x - r, 0, w - 1)];
                sr += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
                sg += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
                sb += (add & 0xFF) - (sub & 0xFF);
            }
        }
        for (int x = 0; x < w; x++) {
            int sr = 0, sg = 0, sb = 0;
            for (int k = -r; k <= r; k++) {
                int p = tmp[clampI(k, 0, h - 1) * w + x];
                sr += (p >> 16) & 0xFF;
                sg += (p >> 8) & 0xFF;
                sb += p & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                px[y * w + x] = 0xFF000000 | ((sr / n) << 16) | ((sg / n) << 8) | (sb / n);
                int add = tmp[clampI(y + r + 1, 0, h - 1) * w + x];
                int sub = tmp[clampI(y - r, 0, h - 1) * w + x];
                sr += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
                sg += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
                sb += (add & 0xFF) - (sub & 0xFF);
            }
        }
    }

    static void boostSaturation(int[] px, float sat, int bright) {
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            int luma = (r * 77 + g * 151 + b * 28) >> 8;
            r = clamp255(Math.round(luma + (r - luma) * sat) + bright);
            g = clamp255(Math.round(luma + (g - luma) * sat) + bright);
            b = clamp255(Math.round(luma + (b - luma) * sat) + bright);
            px[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }

    private static Bitmap softBlur(Bitmap src, int factor) {
        int w = Math.max(1, src.getWidth() / factor);
        int h = Math.max(1, src.getHeight() / factor);
        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
        Bitmap out = Bitmap.createScaledBitmap(small, src.getWidth(), src.getHeight(), true);
        if (small != out) small.recycle();
        return out;
    }

    static int clampI(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static Bitmap glassOf(Bitmap src) {
        if (src == null) return null;
        Bitmap sw = src;
        try {
            if (src.getConfig() == Bitmap.Config.HARDWARE) {
                Bitmap c = src.copy(Bitmap.Config.ARGB_8888, false);
                if (c != null) sw = c;
            }
            return glassBlur(sw);
        } catch (Throwable t) {
            IslandHook.log("glassOf failed: " + t);
            try {
                return softBlur(sw, 12);
            } catch (Throwable t2) {
                return null;
            }
        } finally {
            if (sw != src) {
                try {
                    sw.recycle();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void drawGlassAt(Canvas c, float cx, float cy, float w, float h,
                             float alpha, float scrimA) {
        Bitmap g = mBackdropBlur;
        if (g == null || g.isRecycled() || alpha <= 0.01f) return;
        Bitmap prev = mBackdropPrev;
        if (prev != null && !prev.isRecycled() && mCross < 0.999f) {
            drawSlice(c, prev, cx, cy, w, h, alpha * mBackdropAlpha);
            drawSlice(c, g, cx, cy, w, h, alpha * mBackdropAlpha * mCross);
        } else {
            drawSlice(c, g, cx, cy, w, h, alpha * mBackdropAlpha);
        }

        float sa = scrimA <= 0f ? mScrimAlpha : scrimA;
        if (sa > 0.01f) {
            mBg.setStyle(Paint.Style.FILL);
            mBg.setColor(mScrimColor);
            mBg.setAlpha((int) (255 * Math.min(1f, sa * alpha) * mBackdropAlpha));
            c.drawRect(mDst, mBg);
        }
    }

    private void drawSlice(Canvas c, Bitmap g, float cx, float cy, float w, float h,
                           float alpha) {
        if (g == null || g.isRecycled() || alpha <= 0.004f) return;
        float bw = g.getWidth();
        float bh = g.getHeight();
        if (bw <= 0 || bh <= 0) return;

        float cw = Math.max(1f, cardWidth());
        float chH = Math.max(1f, cardHeight());
        float sc = Math.max(cw / bw, chH / bh);
        float dh = bh * sc;
        float srcY = Math.max(0f, (dh - chH) / 2f / sc);
        float offX = cx - getWidth() / 2f;
        float sw = w / sc;
        float sh = Math.max(2f, Math.min(bh - srcY, h / sc));
        float sx = bw / 2f + offX / sc - sw / 2f;
        if (sx < 0f) sx = 0f;
        if (sx + sw > bw) sx = Math.max(0f, bw - sw);
        mSrc.set(Math.round(sx), Math.round(srcY),
                Math.round(Math.min(bw, sx + sw)), Math.round(Math.min(bh, srcY + sh)));
        mDst.set(Math.round(cx - w / 2f), Math.round(cy - h / 2f),
                Math.round(cx + w / 2f), Math.round(cy + h / 2f));
        mArtPaint.setAlpha((int) (255 * Math.min(1f, alpha)));
        c.drawBitmap(g, mSrc, mDst, mArtPaint);
    }

    private void sampleBackdrop(Bitmap b) {
        try {
            int sw = 24;
            int sh = 24;
            Bitmap small = Bitmap.createScaledBitmap(b, sw, sh, false);
            int[] px = new int[sw * sh];
            small.getPixels(px, 0, sw, 0, 0, sw, sh);
            if (small != b) small.recycle();

            int[] luma = new int[px.length];
            long r = 0, g = 0, bl = 0;
            for (int i = 0; i < px.length; i++) {
                int p = px[i];
                int rr = (p >> 16) & 0xFF;
                int gg = (p >> 8) & 0xFF;
                int bb = p & 0xFF;
                r += rr;
                g += gg;
                bl += bb;
                luma[i] = (rr * 299 + gg * 587 + bb * 114) / 1000;
            }
            java.util.Arrays.sort(luma);

            mLightBg = luma[luma.length / 2] > 110;

            float scale = mLightBg ? 0.07f : 0.11f;
            int n = px.length;
            int tr = 10 + clamp255((int) (r / n * scale));
            int tg = 10 + clamp255((int) (g / n * scale));
            int tb = 12 + clamp255((int) (bl / n * scale));
            mTint = 0xFF000000 | (tr << 16) | (tg << 8) | tb;
            mScrimColor = 0xFF000000
                    | (clamp255((int) (tr * 1.7f)) << 16)
                    | (clamp255((int) (tg * 1.7f)) << 8)
                    | clamp255((int) (tb * 1.7f));

            long sum = 0;
            for (int v : luma) sum += v;
            float avg = sum / (float) luma.length;
            mScrimAlpha = 0.12f + 0.24f * (avg / 255f);

            applyFgColors();
            invalidate();
        } catch (Throwable t) {
            mLightBg = true;
            mTint = 0x0A0A0C;
            mScrimColor = 0xFF000000;
            mScrimAlpha = 0.68f;
            applyFgColors();
            invalidate();
        }
    }

    static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private long positionNow() {
        if (mSeekPreview >= 0) return mSeekPreview;
        long pos = mBasePos;
        if (mPlaying) {
            pos += (long) ((SystemClock.elapsedRealtime() - mBaseTime) * mSpeed);
        }
        if (mDuration > 0 && pos > mDuration) pos = mDuration;
        return Math.max(0, pos);
    }

    void reset() {
        boolean wasOpen = mExpanded || running();
        cancelAutoCollapse();
        if (mAnim != null) {
            mAnim.cancel();
            mAnim = null;
        }
        mExpand = 0f;
        mExpanded = false;
        mSeekPreview = -1;
        mSeeking = false;
        mBackdrop = null;
        mBackdropAlpha = 0f;
        if (mBackdropBlur != null) {
            try {
                mBackdropBlur.recycle();
            } catch (Throwable ignored) {
            }
            mBackdropBlur = null;
        }
        dropPrev();
        mCross = 1f;
        if (mBackdropAnim != null) {
            mBackdropAnim.cancel();
            mBackdropAnim = null;
        }
        mHost.setBlur(false);
        if (wasOpen) mHost.onCollapseEnd();
    }

    void pulse(int dir) {
        if (mSwapAnim != null) mSwapAnim.cancel();
        mSwapDir = dir >= 0 ? 1f : -1f;
        mSwapT = 0f;
        mSwapAlpha = 0f;
        mSwapShift = mSwapDir * dp(22);
        noteContentChanged();
        animatePillWidth();
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(300);
        va.setInterpolator(new PathInterpolator(0.2f, 0.9f, 0.2f, 1f));
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float t = (Float) animation.getAnimatedValue();
                mSwapT = t;
                float tin = clamp01((t - 0.17f) / 0.83f);
                mSwapAlpha = 0.15f + 0.85f * tin;
                mSwapShift = mSwapDir * dp(22) * (1f - t) * (1f - t);
                invalidate();
            }
        });
        va.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (canceled) return;
                mSwapT = 1f;
                mSwapAlpha = 1f;
                mSwapShift = 0f;
                recycleFake();
                invalidate();
            }
        });
        mSwapAnim = va;
        va.start();
    }

    void snapshotForSwap() {
        if (getWidth() <= 1 || getHeight() <= 1) return;
        recycleFake();
        Bitmap b = null;
        float bx = mBurnX;
        float by = mBurnY;
        mSnapshotting = true;
        try {
            int hh = Math.max(1, Math.round(pillHeight()));
            b = Bitmap.createBitmap(getWidth(), hh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            mBurnX = 0f;
            mBurnY = 0f;
            draw(c);
        } catch (Throwable t) {
            if (b != null) b.recycle();
            b = null;
        } finally {
            mSnapshotting = false;
            mBurnX = bx;
            mBurnY = by;
        }
        mFake = b;
    }

    private void recycleFake() {
        if (mFake != null) {
            try {
                mFake.recycle();
            } catch (Throwable ignored) {
            }
            mFake = null;
        }
    }

    void onShown() {
        startBurnIn();
    }

    void onHidden() {
        stopBurnIn();
        cancelAutoCollapse();
        recycleFake();
        mFade = 1f;
        setAlpha(1f);
    }

    private void startBurnIn() {
        stopBurnIn();
        mBurnX = 0f;
        mBurnY = 0f;
        mBurnDir = 0;
        mFade = 1f;
        setAlpha(1f);
        mShownAt = SystemClock.uptimeMillis();
        mBurnTick = new Runnable() {
            @Override
            public void run() {
                long now = SystemClock.uptimeMillis();
                mBurnDir = (mBurnDir + 1) % 4;
                float step = dp(2f);
                switch (mBurnDir) {
                    case 0:
                        mBurnX = -step;
                        mBurnY = 0f;
                        break;
                    case 1:
                        mBurnX = -step;
                        mBurnY = step;
                        break;
                    case 2:
                        mBurnX = 0f;
                        mBurnY = step;
                        break;
                    default:
                        mBurnX = 0f;
                        mBurnY = 0f;
                        break;
                }
                if (mFade >= 1f && now - mShownAt > 10 * 60 * 1000L) {
                    mFade = 0.5f;
                    animate().alpha(0.5f).setDuration(600).start();
                }
                invalidate();
                postDelayed(this, 60000);
            }
        };
        postDelayed(mBurnTick, 30000);
    }

    private void stopBurnIn() {
        if (mBurnTick != null) {
            removeCallbacks(mBurnTick);
            mBurnTick = null;
        }
        mBurnX = 0f;
        mBurnY = 0f;
        mBurnDir = 0;
    }

    private void noteContentChanged() {
        mShownAt = SystemClock.uptimeMillis();
        mBurnX = 0f;
        mBurnY = 0f;
        mBurnDir = 0;
        if (mFade < 1f) {
            mFade = 1f;
            animate().alpha(1f).setDuration(200).start();
        }
    }

    private void scheduleAutoCollapse() {
        cancelAutoCollapse();
        long delay = mMode == MODE_NAV ? 6000 : (mMode == MODE_CAPSULE ? 6000 : 5000);
        mAutoCollapse = new Runnable() {
            @Override
            public void run() {
                mAutoCollapse = null;
                if (mExpanded && !mSeeking) collapse();
            }
        };
        postDelayed(mAutoCollapse, delay);
    }

    private void cancelAutoCollapse() {
        if (mAutoCollapse != null) {
            removeCallbacks(mAutoCollapse);
            mAutoCollapse = null;
        }
    }

    float cardTop() {
        return Math.round(pillHeight()) + dp(6);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        if (w <= 1) return;

        int root = c.save();
        c.translate(mBurnX, mBurnY);

        float e = clamp01(mExpand);
        if (mDrawW < 0f) mDrawW = pillWidth();

        float ph = pillHeight();
        float pw = mDrawW;
        float pLeft = (w - pw) / 2f;
        float pRadius = ph / 2f;

        float pillA = (mPillSnapValid ? 1f : clamp01(1f - e * 1.6f)) * mSwapAlpha;
        if (pillA <= 0.01f) {
        } else {
        mRect.set(pLeft, 0, pLeft + pw, ph);
        drawMaterialRect(c, mRect, pRadius, pillA, false);
        }

        int saveP = c.save();
        mPath.reset();
        mRect.set(pLeft, 0, pLeft + pw, ph);
        mPath.addRoundRect(mRect, pRadius, pRadius, Path.Direction.CW);
        c.clipPath(mPath);

        if (!mSnapshotting && mFake != null && !mFake.isRecycled() && mSwapT < 1f) {
            int fa = (int) (255 * clamp01(1f - mSwapT * 1.5f) * pillA);
            if (fa > 0) {
                mFakePaint.setAlpha(fa);
                int fs = c.save();
                c.translate(-mSwapDir * dp(20) * mSwapT, 0f);
                mRect.set(0, 0, mFake.getWidth(), mFake.getHeight());
                c.drawBitmap(mFake, null, mRect, mFakePaint);
                c.restoreToCount(fs);
            }
        }

        if (e > 0.02f && mPillSnapValid) {
            drawPillSnap(c, pLeft, pw, mSwapAlpha);
        } else if (pillA > 0.01f) {
            drawPill(c, pLeft, pw, mSwapAlpha * clamp01(pillA));
        }
        c.restoreToCount(saveP);

        if (mSecType != 0) {
            drawSecCircle(c, pLeft + pw + secGap() + secCircleR(), ph / 2f, mSwapAlpha);
        }

        if (e > 0.001f) {
            try {
                float cwCard = cardWidth();
                float chCard = cardHeight();
                float ox = (w - cwCard) / 2f;
                float top = cardTop();
                float a = clamp01(e * 1.7f) * mSwapAlpha;

                int save = c.save();

                c.translate(0f, top - (1f - e) * dp(12));
                if (e < 1f) {
                    float s = 0.92f + 0.08f * e;
                    c.translate(w / 2f, 0f);
                    c.scale(s, s);
                    c.translate(-w / 2f, 0f);
                }
                mPath.reset();
                mRect.set(ox, 0, ox + cwCard, chCard);
                mPath.addRoundRect(mRect, dp(28), dp(28), Path.Direction.CW);
                c.clipPath(mPath);
                drawCard(c, w, cwCard, chCard, a);
                c.restoreToCount(save);
            } catch (Throwable t) {
                IslandHook.log("card draw failed: " + t);
            }
        }

        if ((mAnim != null && mAnim.isRunning())
                || (mSwapAnim != null && mSwapAnim.isRunning())
                || (mWidthAnim != null && mWidthAnim.isRunning())
                || (mBackdropAnim != null && mBackdropAnim.isRunning())) {
            postInvalidateOnAnimation();
        } else if (mPlaying || mMode == MODE_NAV
                || (mMode == MODE_CAPSULE && mCapType == 2 && !mCapPaused)) {
            postInvalidateDelayed(mExpanded ? 16 : 33);
        }

        c.restoreToCount(root);
    }

    void setPillSnap(boolean valid, int mode, String title, String right,
                     Bitmap art, Bitmap icon, int bg, boolean delivery) {
        mPillSnapValid = valid;
        mPillSnapMode = mode;
        mPillSnapTitle = title == null ? "" : title;
        mPillSnapRight = right == null ? "" : right;
        mPillSnapArt = art;
        mPillSnapIcon = icon;
        mPillSnapBg = bg;
        mPillSnapDelivery = delivery;
        invalidate();
    }

    private void drawPillSnap(Canvas c, float left, float cw, float a) {
        float ph = pillHeight();
        float cy = ph / 2f;
        float pad = areaPad();
        float gap = areaGap();
        float artR = Math.min(dp(11), ph * 0.36f);
        float artCx = left + pad + artR;

        if (mPillSnapArt != null && !mPillSnapArt.isRecycled()) {
            int save = c.save();
            mPath.reset();
            mPath.addRoundRect(artCx - artR, cy - artR, artCx + artR, cy + artR,
                    artR * 0.55f, artR * 0.55f, Path.Direction.CW);
            c.clipPath(mPath);
            mArtPaint.setAlpha((int) (255 * a));
            float bw = mPillSnapArt.getWidth();
            float bh = mPillSnapArt.getHeight();
            float sc = Math.max(2 * artR / bw, 2 * artR / bh);
            mRect.set(artCx - bw * sc / 2f, cy - bh * sc / 2f,
                    artCx + bw * sc / 2f, cy + bh * sc / 2f);
            c.drawBitmap(mPillSnapArt, null, mRect, mArtPaint);
            c.restoreToCount(save);
        } else if (mPillSnapIcon != null && !mPillSnapIcon.isRecycled()) {
            if (mPillSnapDelivery) {
                drawAppTile(c, artCx - artR, cy - artR, artR * 2, a, mPillSnapIcon);
            } else {
                mIcon.setStyle(Paint.Style.FILL);
                mIcon.setColor(mPillSnapBg);
                mIcon.setAlpha((int) (255 * a));
                c.drawCircle(artCx, cy, artR, mIcon);
                int save = c.save();
                mPath.reset();
                mPath.addCircle(artCx, cy, artR * 0.96f, Path.Direction.CW);
                c.clipPath(mPath);
                mGlyphPaint.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF,
                        PorterDuff.Mode.SRC_IN));
                mGlyphPaint.setAlpha((int) (255 * a));
                float s = artR * 1.24f;
                mRect.set(artCx - s / 2f, cy - s / 2f, artCx + s / 2f, cy + s / 2f);
                c.drawBitmap(mPillSnapIcon, null, mRect, mGlyphPaint);
                c.restoreToCount(save);
            }
        } else {
            mIcon.setStyle(Paint.Style.FILL);
            mIcon.setColor(fg());
            mIcon.setAlpha((int) (230 * a));
            c.drawCircle(artCx, cy, artR * 0.45f, mIcon);
        }

        float tx = artCx + artR + gap;
        float rightEdge = left + cw - pad;
        mPillTitle.setAlpha((int) (255 * a));
        mPillSub.setAlpha((int) (255 * 0.72f * a));
        float rw = mPillSnapRight.length() == 0 ? 0 : mPillSub.measureText(mPillSnapRight);
        float maxLeft = rightEdge - tx - (rw > 0 ? rw + dp(6) : 0);
        CharSequence t = TextUtils.ellipsize(mPillSnapTitle, mPillTitle,
                Math.max(dp(16), maxLeft), TextUtils.TruncateAt.END);
        c.drawText(t, 0, t.length(), tx, baseline(mPillTitle, cy), mPillTitle);
        if (rw > 0) {
            c.drawText(mPillSnapRight, rightEdge - rw, baseline(mPillSub, cy), mPillSub);
        }
    }

    private void drawPill(Canvas c, float left, float cw, float a) {
        int saveAll = c.save();
        c.translate(mSwapShift, 0f);
        if (mMode == MODE_TRANSIENT) {
            drawTransientPill(c, left, cw, a);
            c.restoreToCount(saveAll);
            return;
        }
        float ph = pillHeight();
        float cy = ph / 2f;
        float pad = areaPad();
        float gap = areaGap();
        float artR = Math.min(dp(11), ph * 0.36f);
        float artCx = left + pad + artR;
        if (mMode == MODE_NAV) {
            drawNavGlyph(c, artCx, cy, artR * 0.92f, a, mNavAction);
        } else if (mMode == MODE_CAPSULE) {
            if (mCapDelivery) {
                drawAppTile(c, artCx - artR, cy - artR, artR * 2, a);
            } else {
                drawCapsuleIcon(c, artCx, cy, artR, a);
            }
        } else {
            drawArt(c, artCx, cy, artR, a, true);
        }

        float rightPad = pad;
        float tx = artCx + artR + gap;
        float rightEdge = left + cw - rightPad;
        if (mMode == MODE_NAV) {
            String leftText = mNavDist.length() > 0 ? mNavDist : mNavAction;
            String rightText = mNavRoad.length() > 0 ? mNavRoad
                    : (mNavDist.length() > 0 ? mNavAction : "");
            mPillTitle.setAlpha((int) (255 * a));
            mPillSub.setAlpha((int) (255 * 0.82f * a));
            float rw = rightText.length() == 0 ? 0 : mPillSub.measureText(rightText);
            float maxLeft = rightEdge - tx - rw - gap;
            if (maxLeft < dp(24)) {
                rw = 0;
                maxLeft = rightEdge - tx;
            }
            CharSequence lt = TextUtils.ellipsize(leftText == null ? "" : leftText,
                    mPillTitle, Math.max(dp(16), maxLeft), TextUtils.TruncateAt.END);
            c.drawText(lt, 0, lt.length(), tx, baseline(mPillTitle, cy), mPillTitle);
            if (rw > 0) {
                CharSequence rt = TextUtils.ellipsize(rightText, mPillSub,
                        Math.max(dp(16), rightEdge - tx), TextUtils.TruncateAt.END);
                float w = mPillSub.measureText(rt, 0, rt.length());
                c.drawText(rt, 0, rt.length(), rightEdge - w,
                        baseline(mPillSub, cy), mPillSub);
            }
        } else {
            float avail = rightEdge - tx;
            mPillTitle.setAlpha((int) (255 * a));
            if (mMode == MODE_CAPSULE) {
                String right = capPillRight();
                mPillSub.setAlpha((int) (255 * 0.72f * a));
                float rw = right.length() == 0 ? 0 : mPillSub.measureText(right);
                float maxLeft = rightEdge - tx - rw - (rw > 0 ? dp(6) : 0);
                CharSequence lt = TextUtils.ellipsize(capPillTitle(), mPillTitle,
                        Math.max(dp(16), maxLeft), TextUtils.TruncateAt.END);
                c.drawText(lt, 0, lt.length(), tx, baseline(mPillTitle, cy), mPillTitle);
                if (rw > 0) {
                    c.drawText(right, rightEdge - rw, baseline(mPillSub, cy), mPillSub);
                }
                c.restoreToCount(saveAll);
                return;
            }
            String line = mTitle;
            CharSequence t = TextUtils.ellipsize(line == null ? "" : line, mPillTitle,
                    Math.max(dp(16), avail), TextUtils.TruncateAt.END);
            c.drawText(t, 0, t.length(), tx, baseline(mPillTitle, cy), mPillTitle);

            if (mPlaying) {
                drawEq(c, left + cw - rightPad - dp(5), cy, a);
            }
        }
        c.restoreToCount(saveAll);
    }

    private int transientColor() {
        int v = mTransientValue;
        if (v <= 10) return 0xFFFF3B30;
        if (v <= 20) return 0xFFFF9500;
        return 0xFF34C759;
    }

    private boolean hasBatteryRing() {
        return mTransientKind == 0 || mTransientKind == 3;
    }

    private float ringR() {
        return Math.min(dp(10.5f), pillHeight() * 0.36f);
    }

    private String pillTransientText() {
        if (mTransientKind == 0) return mTransientText.length() > 0 ? mTransientText : "充电中";
        if (mTransientKind == 3) return mTransientText.length() > 0 ? mTransientText : "电量低";
        return mTransientText == null ? "" : mTransientText;
    }

    private void drawBolt(Canvas c, float cx, float cy, float s, float a) {
        float h = s * 0.62f;
        float w = s * 0.34f;
        mPath.reset();
        mPath.moveTo(cx + w * 0.45f, cy - h);
        mPath.lineTo(cx - w * 1.05f, cy + h * 0.10f);
        mPath.lineTo(cx - w * 0.10f, cy + h * 0.10f);
        mPath.lineTo(cx - w * 0.45f, cy + h);
        mPath.lineTo(cx + w * 1.05f, cy - h * 0.16f);
        mPath.lineTo(cx + w * 0.10f, cy - h * 0.16f);
        mPath.close();
        mIcon.setStyle(Paint.Style.FILL);
        mIcon.setColor(0xFF3DDC5B);
        mIcon.setAlpha((int) (255 * a));
        c.drawPath(mPath, mIcon);
    }

    private void drawBatteryGlyph(Canvas c, float cx, float cy, float s, float a) {
        float h = s * 1.02f;
        float w = s * 0.58f;
        float left = cx - w / 2f;
        float top = cy - h / 2f;
        float sw = dp(1.6f);
        mIcon.setStyle(Paint.Style.STROKE);
        mIcon.setStrokeWidth(sw);
        mIcon.setColor(transientColor());
        mIcon.setAlpha((int) (255 * a));
        mRect.set(left, top, left + w, top + h);
        c.drawRoundRect(mRect, dp(3), dp(3), mIcon);

        mIcon.setStyle(Paint.Style.FILL);
        mRect.set(left + w + sw, cy - h * 0.16f, left + w + sw + dp(2), cy + h * 0.16f);
        c.drawRoundRect(mRect, dp(1), dp(1), mIcon);

        float pct = Math.max(0, Math.min(100, mTransientValue)) / 100f;
        float ih = (h - sw * 3) * pct;
        if (ih > 0.5f) {
            mRect.set(left + sw * 1.6f, top + h - sw * 1.6f - ih,
                    left + w - sw * 1.6f, top + h - sw * 1.6f);
            c.drawRoundRect(mRect, dp(1.6f), dp(1.6f), mIcon);
        }
    }

    private void drawTransientPill(Canvas c, float left, float cw, float a) {
        float ph = pillHeight();
        float cy = ph / 2f;
        float pad = areaPad();
        float iconS = Math.min(dp(15), ph * 0.56f);
        float iconCx = left + pad + iconS / 2f;

        if (mTransientKind == 0) {
            drawBolt(c, iconCx, cy, iconS, a);
        } else if (mTransientKind == 3) {
            drawBatteryGlyph(c, iconCx, cy, iconS, a);
        } else if (mTransientIcon != null && !mTransientIcon.isRecycled()) {
            mGlyphPaint.setColorFilter(new PorterDuffColorFilter(fg(),
                    PorterDuff.Mode.SRC_IN));
            mGlyphPaint.setAlpha((int) (255 * a));
            mRect.set(iconCx - iconS / 2f, cy - iconS / 2f,
                    iconCx + iconS / 2f, cy + iconS / 2f);
            c.drawBitmap(mTransientIcon, null, mRect, mGlyphPaint);
        } else {
            mIcon.setStyle(Paint.Style.FILL);
            mIcon.setColor(fg());
            mIcon.setAlpha((int) (235 * a));
            c.drawCircle(iconCx, cy, iconS * 0.28f, mIcon);
        }

        if (hasBatteryRing()) {
            float r = ringR();
            float rcx = left + cw - pad - r;
            float tx = iconCx + iconS / 2f + dp(5);
            mPillTitle.setAlpha((int) (255 * a));
            c.drawText(pillTransientText(), tx, baseline(mPillTitle, cy), mPillTitle);

            mIcon.setStyle(Paint.Style.STROKE);
            mIcon.setStrokeWidth(dp(2));
            mIcon.setColor(fgTrack());
            mIcon.setAlpha((int) (255 * a));
            c.drawCircle(rcx, cy, r, mIcon);

            mIcon.setColor(transientColor());
            mIcon.setAlpha((int) (255 * a));
            mRect.set(rcx - r, cy - r, rcx + r, cy + r);
            float pct = Math.max(0, Math.min(100, mTransientValue));
            c.drawArc(mRect, -90f, 360f * pct / 100f, false, mIcon);

            mRingText.setAlpha((int) (255 * a));
            String s = mTransientValue + "%";
            c.drawText(s, rcx, baseline(mRingText, cy), mRingText);
        } else {
            float tx = iconCx + iconS / 2f + dp(6);
            mPillTitle.setAlpha((int) (255 * a));
            String s = mTransientText == null ? "" : mTransientText;
            c.drawText(s, tx, baseline(mPillTitle, cy), mPillTitle);
        }
    }

    private void drawSecCircle(Canvas c, float cx, float cy, float a) {
        float r = secCircleR();
        mRect.set(cx - r, cy - r, cx + r, cy + r);
        drawMaterialRect(c, mRect, r, a, false);

        Bitmap icon = mSecType == 1
                ? (mSecIcon != null ? mSecIcon : mArt)
                : (mSecType == 3 ? mSecIcon : mNavIcon);
        if (icon != null && !icon.isRecycled()) {
            int save = c.save();
            mPath.reset();
            mPath.addCircle(cx, cy, r * 0.96f, Path.Direction.CW);
            c.clipPath(mPath);
            if (mSecType == 1) {
                mArtPaint.setAlpha((int) (255 * a));
                float bw = icon.getWidth();
                float bh = icon.getHeight();
                float sc = Math.max(2 * r / bw, 2 * r / bh);
                float dw = bw * sc;
                float dh = bh * sc;
                mRect.set(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f);
                c.drawBitmap(icon, null, mRect, mArtPaint);
            } else {
                mGlyphPaint.setColorFilter(new PorterDuffColorFilter(
                        fg(), PorterDuff.Mode.SRC_IN));
                mGlyphPaint.setAlpha((int) (255 * a));
                float s = r * 1.24f;
                mRect.set(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f);
                c.drawBitmap(icon, null, mRect, mGlyphPaint);
            }
            c.restoreToCount(save);
        } else if (mSecType == 1) {
            mIcon.setColor(fg());
            mIcon.setAlpha((int) (230 * a));
            mIcon.setStyle(Paint.Style.FILL);
            float s = dp(5.2f);
            mPath.reset();
            mPath.moveTo(cx - s * 0.55f, cy - s);
            mPath.lineTo(cx - s * 0.55f, cy + s);
            mPath.lineTo(cx + s, cy);
            mPath.close();
            c.drawPath(mPath, mIcon);
        } else if (mSecType == 3) {
            mIcon.setStyle(Paint.Style.FILL);
            mIcon.setColor(fg());
            mIcon.setAlpha((int) (240 * a));
            c.drawCircle(cx, cy, r * 0.42f, mIcon);
        } else {
            drawManeuver(c, cx, cy, r * 0.92f, a, mNavAction);
        }
    }

    private void drawMaterialRect(Canvas c, RectF r, float radius, float a, boolean card) {
        if (a <= 0.004f || r.width() <= 1f || r.height() <= 1f) return;
        int save = c.save();
        mPath.reset();
        mPath.addRoundRect(r, radius, radius, Path.Direction.CW);
        c.clipPath(mPath);
        switch (mBgMode) {
            case IslandPrefs.BG_BLACK:

                mBg.setStyle(Paint.Style.FILL);
                mBg.setShader(null);
                mBg.setColor(0xFF000000);
                mBg.setAlpha((int) (255 * a));
                c.drawRect(r, mBg);
                break;
            default:
                drawFrostMaterial(c, r, radius, a, true, card);
                break;
        }
        c.restoreToCount(save);
    }

    private boolean drawBackdrop(Canvas c, RectF r, boolean card, float a) {
        if (mSysBlur) return true;
        Bitmap g = mBackdropBlur;
        if (g == null || g.isRecycled()) return false;
        if (card) {
            if (mBackdropPrev != null && !mBackdropPrev.isRecycled() && mCross < 0.999f) {
                drawCover(c, mBackdropPrev, r.left, r.top, r.width(), r.height(),
                        a * mBackdropAlpha);
                drawCover(c, g, r.left, r.top, r.width(), r.height(),
                        a * mBackdropAlpha * mCross);
            } else {
                drawCover(c, g, r.left, r.top, r.width(), r.height(), a * mBackdropAlpha);
            }
        } else {
            if (mBackdropPrev != null && !mBackdropPrev.isRecycled() && mCross < 0.999f) {
                drawSlice(c, mBackdropPrev, r.centerX(), r.centerY(),
                        r.width(), r.height(), a * mBackdropAlpha);
                drawSlice(c, g, r.centerX(), r.centerY(),
                        r.width(), r.height(), a * mBackdropAlpha * mCross);
            } else {
                drawSlice(c, g, r.centerX(), r.centerY(),
                        r.width(), r.height(), a * mBackdropAlpha);
            }
        }
        return true;
    }

    private void drawFrostMaterial(Canvas c, RectF r, float radius, float a,
                                   boolean white, boolean card) {
        boolean has = drawBackdrop(c, r, card, a);

        if (!has) {
            mBg.setStyle(Paint.Style.FILL);
            mBg.setShader(new LinearGradient(r.left, r.top, r.left, r.bottom,
                    0xFFFCFCFE, 0xFFE8ECF3, Shader.TileMode.CLAMP));
            mBg.setAlpha((int) (255 * a));
            c.drawRect(r, mBg);
            mBg.setShader(null);
        }

        mBg.setStyle(Paint.Style.FILL);
        mBg.setShader(null);
        mBg.setColor(0xFF000000);
        mBg.setAlpha((int) (0x4D * a));
        c.drawRect(r, mBg);
    }

    private void drawCard(Canvas c, float w, float cw, float ch, float a) {
        float cwCard = cardWidth();
        float chCard = cardHeight();
        float ox = (w - cwCard) / 2f;
        float oy = (ch - chCard) / 2f;
        int alpha = (int) (255 * a);

        boolean useArt = mMode == MODE_MEDIA && mArtBackdrop != null
                && !mArtBackdrop.isRecycled();
        mRect.set(ox, oy, ox + cwCard, oy + chCard);
        if (useArt) {
            int s0 = c.save();
            mPath.reset();
            mPath.addRoundRect(mRect, dp(16), dp(16), Path.Direction.CW);
            c.clipPath(mPath);
            drawCover(c, mArtBackdrop, ox, oy, cwCard, chCard, a);

            mBg.setStyle(Paint.Style.FILL);
            mBg.setShader(null);
            mBg.setColor(0xFF000000);
            mBg.setAlpha((int) (255 * 0.45f * a));
            c.drawRect(ox, oy, ox + cwCard, oy + chCard, mBg);
            c.restoreToCount(s0);
        } else {
            drawMaterialRect(c, mRect, dp(16), a, true);
        }

        if (DEBUG_GLASS && SystemClock.uptimeMillis() - mGlassLogAt > 400) {
            mGlassLogAt = SystemClock.uptimeMillis();
            IslandHook.log("glass a=" + Math.round(a * 100) / 100f
                    + " alpha=" + Math.round(mBackdropAlpha * 100) / 100f
                    + " swap=" + Math.round(mSwapAlpha * 100) / 100f
                    + " e=" + Math.round(mExpand * 100) / 100f
                    + " blur=" + (mBackdropBlur != null)
                    + " scrim=" + Math.round(mScrimAlpha * 100) / 100f);
        }
        int saveAll = c.save();
        c.translate(ox + mSwapShift, oy);

        if (mMode == MODE_NAV) {
            drawNavCard(c, cwCard, chCard, alpha, a);
        } else if (mMode == MODE_CAPSULE) {
            if (mCapDelivery) {
                drawDeliveryCard(c, cwCard, chCard, alpha, a);
            } else {
                drawCapsuleCard(c, cwCard, chCard, alpha, a);
            }
        } else {
            drawMediaCard(c, cwCard, chCard, alpha, a);
        }

        c.restoreToCount(saveAll);
    }

    private void drawMediaCard(Canvas c, float cw, float ch, int alpha, float a) {
        float artS = dp(52.5f);
        float artX = dp(15);
        float artY = dp(15);
        drawArt(c, artX + artS / 2f, artY + artS / 2f, artS / 2f, a, false);
        drawAppBadge(c, artX + artS - dp(8), artY + artS - dp(8), dp(8), a);

        float tx = artX + artS + dp(12);
        float avail = Math.max(dp(60), cw - tx - dp(65));
        mCardTitle.setAlpha(alpha);
        CharSequence t = TextUtils.ellipsize(mTitle, mCardTitle, avail, TextUtils.TruncateAt.END);
        c.drawText(t, 0, t.length(), tx, baseline(mCardTitle, dp(31.5f)), mCardTitle);

        mCardSub.setAlpha((int) (255 * 0.80f * a));
        CharSequence sub = TextUtils.ellipsize(mArtist, mCardSub, avail, TextUtils.TruncateAt.END);
        c.drawText(sub, 0, sub.length(), tx, baseline(mCardSub, dp(53)), mCardSub);

        float seekY = dp(133);
        float x0 = dp(69);
        float x1 = cw - dp(69);
        long pos = positionNow();
        float frac = mDuration > 0 ? clamp01(pos / (float) mDuration) : 0f;

        mIcon.setStyle(Paint.Style.FILL);
        mIcon.setColor(fg());
        mIcon.setAlpha((int) (255 * 0.22f * a));
        mRect.set(x0, seekY - dp(2), x1, seekY + dp(2));
        c.drawRoundRect(mRect, dp(2), dp(2), mIcon);

        float px = x0 + (x1 - x0) * frac;
        if (px > x0) {
            mIcon.setAlpha((int) (255 * 0.92f * a));
            mRect.set(x0, seekY - dp(2), px, seekY + dp(2));
            c.drawRoundRect(mRect, dp(2), dp(2), mIcon);
        }
        if (mDuration > 0) {
            mIcon.setAlpha(alpha);
            c.drawCircle(px, seekY, dp(mSeeking ? 7.5f : 5.5f), mIcon);
        }

        mTime.setAlpha((int) (255 * 0.60f * a));
        String left = fmt(pos);
        String right = mDuration > 0 ? fmt(mDuration) : "--:--";
        float lw = mTime.measureText(left);
        float rw = mTime.measureText(right);
        c.drawText(left, dp(41) - lw / 2f, baseline(mTime, seekY), mTime);
        c.drawText(right, cw - dp(41) - rw / 2f, baseline(mTime, seekY), mTime);

        float btnY = dp(103.5f);
        float cx = cw / 2f;
        float gap = dp(78);
        drawButtonBg(c, cx - gap, btnY, a, mPressedTarget == T_PREV);
        drawButtonBg(c, cx, btnY, a, mPressedTarget == T_PLAY);
        drawButtonBg(c, cx + gap, btnY, a, mPressedTarget == T_NEXT);
        drawPrev(c, cx - gap, btnY, a, true);
        drawPlay(c, cx, btnY, a, true);
        drawNext(c, cx + gap, btnY, a, true);
    }

    private void drawButtonBg(Canvas c, float x, float y, float a, boolean pressed) {
        if (!pressed) return;
        mIcon.setStyle(Paint.Style.FILL);
        mIcon.setColor(fg());
        mIcon.setAlpha((int) (0x1F * a));
        c.drawCircle(x, y, dp(20), mIcon);
    }

    private void drawAppBadge(Canvas c, float cx, float cy, float r, float a) {
        if (mAppIcon == null || mAppIcon.isRecycled()) return;
        mIcon.setStyle(Paint.Style.FILL);
        mIcon.setColor(0xFF0A0A0C);
        mIcon.setAlpha((int) (255 * a));
        c.drawCircle(cx, cy, r + dp(1.6f), mIcon);
        int save = c.save();
        mPath.reset();
        mPath.addCircle(cx, cy, r, Path.Direction.CW);
        c.clipPath(mPath);
        mArtPaint.setAlpha((int) (255 * a));
        mRect.set(cx - r, cy - r, cx + r, cy + r);
        c.drawBitmap(mAppIcon, null, mRect, mArtPaint);
        c.restoreToCount(save);
    }

    private int deliveryBarBg() {
        int c = mCapBarBg;
        if ((c >>> 24) == 0) return fgTrack();
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        if (lightFg() && r > 200 && g > 200 && b > 200) return 0x1F171A21;
        return c;
    }

    private void drawDeliveryCard(Canvas c, float cw, float ch, int alpha, float a) {
        float padL = dp(15);
        float iconS = dp(32);
        float iconX = padL;
        float iconY = dp(15);
        drawAppTile(c, iconX, iconY, iconS, a);

        String title = capPillTitle();
        float tx = iconX + iconS + dp(11);
        float rightEdge = cw - padL;
        if (mCapPic != null && !mCapPic.isRecycled()) {
            float picS = dp(50);
            float px = cw - padL - picS;
            int saveP = c.save();
            mRect.set(px, iconY, px + picS, iconY + picS);
            mPath.reset();
            mPath.addRoundRect(mRect, dp(13), dp(13), Path.Direction.CW);
            c.clipPath(mPath);
            drawCenterCrop(c, mCapPic, mRect, a);
            c.restoreToCount(saveP);
            rightEdge = px - dp(10);
        }

        float avail = Math.max(dp(50), rightEdge - tx);
        mDeliverTitle.setAlpha(alpha);
        CharSequence t = TextUtils.ellipsize(title, mDeliverTitle, avail,
                TextUtils.TruncateAt.END);
        c.drawText(t, 0, t.length(), tx, baseline(mDeliverTitle, dp(31)), mDeliverTitle);

        if (mCapContent.length() > 0) {
            mCardSub.setAlpha((int) (255 * 0.78f * a));
            CharSequence sub = TextUtils.ellipsize(mCapContent, mCardSub, avail,
                    TextUtils.TruncateAt.END);
            c.drawText(sub, 0, sub.length(), tx, baseline(mCardSub, dp(52)), mCardSub);
        }

        float barY = dp(116);
        float x0 = dp(26);
        float x1 = cw - dp(26);
        float frac = clamp01(mCapPct / 100f);
        float barH = mCapBarType == 0 ? dp(5) : (mCapBarType == 2 ? dp(6) : dp(3.5f));

        mIcon.setStyle(Paint.Style.FILL);
        mIcon.setColor(deliveryBarBg());
        mIcon.setAlpha((int) (255 * a));
        mRect.set(x0, barY - barH / 2f, x1, barY + barH / 2f);
        c.drawRoundRect(mRect, barH / 2f, barH / 2f, mIcon);

        float px = x0 + (x1 - x0) * frac;
        if (px > x0) {
            mIcon.setColor(mCapBarColor);
            mIcon.setAlpha((int) (255 * 0.95f * a));
            mRect.set(x0, barY - barH / 2f, Math.max(px, x0 + barH), barY + barH / 2f);
            c.drawRoundRect(mRect, barH / 2f, barH / 2f, mIcon);
        }

        float nodeR = dp(4);
        for (int i = 0; i < 2; i++) {
            float cx = i == 0 ? x0 : x1;
            Bitmap nb = (mCapNodes != null && mCapNodes.length > i) ? mCapNodes[i] : null;
            if (nb != null && !nb.isRecycled()) {
                float s = dp(15);
                mGlyphPaint.setAlpha((int) (255 * a));
                mRect.set(cx - s / 2f, barY - s / 2f, cx + s / 2f, barY + s / 2f);
                c.drawBitmap(nb, null, mRect, mGlyphPaint);
            } else {
                mIcon.setColor(fg());
                mIcon.setAlpha((int) (255 * 0.55f * a));
                c.drawCircle(cx, barY, nodeR, mIcon);
            }
        }

        float mr = dp(10);
        mIcon.setStyle(Paint.Style.FILL);
        mIcon.setColor(fg());
        mIcon.setAlpha((int) (255 * a));
        c.drawCircle(px, barY, mr, mIcon);
        mIcon.setColor(mCapBarColor);
        mIcon.setAlpha((int) (255 * 0.95f * a));
        c.drawCircle(px, barY, mr - dp(2.4f), mIcon);
        if (mCapIndicator != null && !mCapIndicator.isRecycled()) {
            float s = mr * 1.5f;
            mGlyphPaint.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF,
                    PorterDuff.Mode.SRC_IN));
            mGlyphPaint.setAlpha((int) (255 * a));
            mRect.set(px - s / 2f, barY - s / 2f, px + s / 2f, barY + s / 2f);
            c.drawBitmap(mCapIndicator, null, mRect, mGlyphPaint);
            mGlyphPaint.setColorFilter(null);
        } else {
            drawScooter(c, px, barY, mr * 0.62f, a);
        }

        if (mCapExtend.length() > 0 && mCapActions.length == 0) {
            mTime.setTextSize(dp(10));
            mTime.setAlpha((int) (255 * 0.62f * a));
            mTime.setTextAlign(Paint.Align.RIGHT);
            CharSequence et = TextUtils.ellipsize(mCapExtend, mTime,
                    cw - dp(52), TextUtils.TruncateAt.END);
            c.drawText(et, 0, et.length(), cw - dp(26), baseline(mTime, dp(143)), mTime);
            mTime.setTextAlign(Paint.Align.LEFT);
        }

        drawCapActions(c, cw, alpha, a, dp(147));
    }

    private void drawAppTile(Canvas c, float x, float y, float s, float a) {
        drawAppTile(c, x, y, s, a, mCapIcon);
    }

    private void drawAppTile(Canvas c, float x, float y, float s, float a, Bitmap bmp) {
        int save = c.save();
        mRect.set(x, y, x + s, y + s);
        mPath.reset();
        mPath.addRoundRect(mRect, dp(9), dp(9), Path.Direction.CW);
        c.clipPath(mPath);
        mBg.setColor(lightFg() ? 0x0F171A21 : 0x22FFFFFF);
        mBg.setAlpha((int) (255 * a));
        c.drawRect(x, y, x + s, y + s, mBg);
        if (bmp != null && !bmp.isRecycled()) {
            mGlyphPaint.setColorFilter(null);
            mGlyphPaint.setAlpha((int) (255 * a));
            c.drawBitmap(bmp, null, mRect, mGlyphPaint);
        } else {
            mIcon.setColor(mCapBarColor);
            mIcon.setAlpha((int) (255 * a));
            c.drawCircle(x + s / 2f, y + s / 2f, s * 0.28f, mIcon);
        }
        c.restoreToCount(save);
    }

    private void drawScooter(Canvas c, float cx, float cy, float s, float a) {
        mIcon.setStyle(Paint.Style.FILL);
        mIcon.setColor(0xFF1B1B1F);
        mIcon.setAlpha((int) (255 * a));

        mRect.set(cx - s * 0.75f, cy - s * 0.30f, cx + s * 0.35f, cy + s * 0.18f);
        c.drawRoundRect(mRect, s * 0.16f, s * 0.16f, mIcon);
        mRect.set(cx + s * 0.05f, cy - s * 0.62f, cx + s * 0.42f, cy - s * 0.12f);
        c.drawRoundRect(mRect, s * 0.12f, s * 0.12f, mIcon);
        c.drawCircle(cx - s * 0.52f, cy + s * 0.45f, s * 0.30f, mIcon);
        c.drawCircle(cx + s * 0.42f, cy + s * 0.45f, s * 0.30f, mIcon);
    }

    private void drawCenterCrop(Canvas c, Bitmap b, RectF dst, float a) {
        float bw = b.getWidth();
        float bh = b.getHeight();
        if (bw <= 0 || bh <= 0) return;
        float sc = Math.max(dst.width() / bw, dst.height() / bh);
        float dw = bw * sc;
        float dh = bh * sc;
        float left = dst.left + (dst.width() - dw) / 2f;
        float top = dst.top + (dst.height() - dh) / 2f;
        mArtPaint.setAlpha((int) (255 * a));
        mRect.set(left, top, left + dw, top + dh);
        c.drawBitmap(b, null, mRect, mArtPaint);
    }

    private void drawCapActions(Canvas c, float cw, int alpha, float a, float by) {
        int n = Math.min(3, mCapActions.length);
        if (n <= 0) return;
        float margin = dp(15);
        float gap = dp(8);
        float total = cw - margin * 2;
        float bw = (total - gap * (n - 1)) / n;
        float bh = dp(28);
        for (int i = 0; i < n; i++) {
            String label = mCapActions[i] == null ? "" : mCapActions[i];
            boolean danger = label.contains("结束") || label.contains("停止")
                    || label.contains("关闭") || label.contains("挂断");
            float bx = margin + i * (bw + gap);
            mIcon.setStyle(Paint.Style.FILL);
            mIcon.setColor(danger ? 0xFF3A1518 : fg());
            mIcon.setAlpha((int) ((danger ? 0.55f : 0.14f) * 255 * a));
            mRect.set(bx, by - bh / 2f, bx + bw, by + bh / 2f);
            c.drawRoundRect(mRect, bh / 2f, bh / 2f, mIcon);
            if (mPressedTarget == T_CAP_ACTION && mCapHitIndex == i) {
                mIcon.setColor(fg());
                mIcon.setAlpha((int) (0.10f * 255 * a));
                c.drawRoundRect(mRect, bh / 2f, bh / 2f, mIcon);
            }
            mCapAction.setColor(danger ? 0xFFFF7A7A : fg());
            mCapAction.setAlpha((int) (255 * (danger ? 0.95f : 0.92f) * a));
            CharSequence lab = TextUtils.ellipsize(label, mCapAction,
                    bw - dp(14), TextUtils.TruncateAt.END);
            float lw = mCapAction.measureText(lab, 0, lab.length());
            c.drawText(lab, 0, lab.length(), bx + (bw - lw) / 2f,
                    baseline(mCapAction, by), mCapAction);
        }
    }

    private void drawCapsuleCard(Canvas c, float cw, float ch, int alpha, float a) {
        float iconS = dp(52.5f);
        float iconX = dp(15);
        float iconY = dp(15);
        drawCapsuleIcon(c, iconX + iconS / 2f, iconY + iconS / 2f, iconS / 2f, a);

        float tx = iconX + iconS + dp(12);
        float avail = Math.max(dp(60), cw - tx - dp(15));
        mCardTitle.setAlpha(alpha);
        CharSequence t = TextUtils.ellipsize(capPillTitle(), mCardTitle, avail,
                TextUtils.TruncateAt.END);
        c.drawText(t, 0, t.length(), tx, baseline(mCardTitle, dp(31.5f)), mCardTitle);

        if (mCapContent.length() > 0 && !mCapContent.equals(capPillTitle())) {
            mCardSub.setAlpha((int) (255 * 0.80f * a));
            CharSequence sub = TextUtils.ellipsize(mCapContent, mCardSub, avail,
                    TextUtils.TruncateAt.END);
            c.drawText(sub, 0, sub.length(), tx, baseline(mCardSub, dp(53)), mCardSub);
        }

        if (mCapType == 2) {
            String time = fmtCap(capTimeNow());
            mCapTimer.setAlpha(alpha);
            float tw = mCapTimer.measureText(time);
            c.drawText(time, (cw - tw) / 2f, baseline(mCapTimer, dp(96)), mCapTimer);
        } else if (mCapType == 3) {
            float y = dp(104);
            float x0 = dp(24);
            float x1 = cw - dp(24);
            mIcon.setStyle(Paint.Style.FILL);
            mIcon.setColor(fg());
            mIcon.setAlpha((int) (255 * 0.18f * a));
            mRect.set(x0, y - dp(2.5f), x1, y + dp(2.5f));
            c.drawRoundRect(mRect, dp(2.5f), dp(2.5f), mIcon);
            float frac = mCapMax > 0 ? clamp01(mCapProgress / (float) mCapMax) : 0f;
            float px;
            if (mCapIndeterminate) {
                float w = (x1 - x0) * 0.32f;
                float phase = (SystemClock.uptimeMillis() % 1600) / 1600f;
                float s = x0 + (x1 - x0 - w) * phase;
                px = s + w;
                mRect.set(s, y - dp(2.5f), px, y + dp(2.5f));
            } else {
                px = x0 + (x1 - x0) * frac;
                mRect.set(x0, y - dp(2.5f), px, y + dp(2.5f));
            }
            mIcon.setAlpha((int) (255 * 0.92f * a));
            c.drawRoundRect(mRect, dp(2.5f), dp(2.5f), mIcon);
            if (!mCapIndeterminate && mCapMax > 0) {
                String pct = Math.round(frac * 100) + "%";
                mTime.setAlpha((int) (255 * 0.60f * a));
                float pw = mTime.measureText(pct);
                c.drawText(pct, cw - dp(24) - pw, baseline(mTime, dp(88)), mTime);
            }
        }

        drawCapActions(c, cw, alpha, a, dp(147));
        if (mCapActions.length == 0 && mCapContent.length() == 0 && mCapType == 1) {
            mNote.setAlpha((int) (255 * 0.45f * a));
            String hint = "点击打开";
            float hw = mNote.measureText(hint);
            c.drawText(hint, (cw - hw) / 2f, baseline(mNote, dp(130)), mNote);
        }
    }

    private void drawCapsuleIcon(Canvas c, float cx, float cy, float r, float a) {
        mIcon.setStyle(Paint.Style.FILL);
        mIcon.setColor(mCapBg);
        mIcon.setAlpha((int) (255 * a));
        c.drawCircle(cx, cy, r, mIcon);
        if (mCapIcon != null && !mCapIcon.isRecycled()) {
            int save = c.save();
            mPath.reset();
            mPath.addCircle(cx, cy, r * 0.98f, Path.Direction.CW);
            c.clipPath(mPath);
            mGlyphPaint.setColorFilter(new PorterDuffColorFilter(mCapFg,
                    PorterDuff.Mode.SRC_IN));
            mGlyphPaint.setAlpha((int) (255 * a));
            float s = r * 0.98f;
            mRect.set(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f);
            c.drawBitmap(mCapIcon, null, mRect, mGlyphPaint);
            c.restoreToCount(save);
        } else {
            mIcon.setColor(mCapFg);
            mIcon.setAlpha((int) (235 * a));
            c.drawCircle(cx, cy, r * 0.30f, mIcon);
        }
    }

    private void drawNavCard(Canvas c, float cw, float ch, int alpha, float a) {
        float cx = cw / 2f;

        String big = mNavDist.length() > 0 ? mNavDist : mNavAction;
        if (big.length() == 0) big = "导航中";
        float glyphR = dp(28);
        float gap = dp(14);
        float maxText = cw - glyphR * 2 - gap - dp(72);
        mNavTitle.setAlpha(alpha);
        mNavRoadPaint.setAlpha((int) (255 * 0.82f * a));

        CharSequence title = TextUtils.ellipsize(big, mNavTitle,
                Math.max(dp(40), maxText), TextUtils.TruncateAt.END);
        String subText = mNavRoad;
        if (mNavAction.length() > 0 && mNavDist.length() > 0) {
            subText = mNavRoad.length() > 0 ? mNavAction + " · " + mNavRoad : mNavAction;
        }
        CharSequence road = TextUtils.ellipsize(subText, mNavRoadPaint,
                Math.max(dp(40), maxText), TextUtils.TruncateAt.END);
        float tw = Math.max(mNavTitle.measureText(title, 0, title.length()),
                mNavRoadPaint.measureText(road, 0, road.length()));
        float blockW = glyphR * 2 + gap + tw;
        float sx = cx - blockW / 2f;

        float rowCy = dp(58);
        drawNavGlyph(c, sx + glyphR, rowCy, glyphR, a, mNavAction);

        float tx = sx + glyphR * 2 + gap;
        c.drawText(title, 0, title.length(), tx, baseline(mNavTitle, rowCy - dp(11)), mNavTitle);
        if (road.length() > 0) {
            c.drawText(road, 0, road.length(), tx, baseline(mNavRoadPaint, rowCy + dp(15)), mNavRoadPaint);
        }

        if (mNavExtra.length() > 0) {
            mNavInfo.setAlpha((int) (255 * 0.66f * a));
            CharSequence info = TextUtils.ellipsize(mNavExtra, mNavInfo,
                    cw - dp(40), TextUtils.TruncateAt.END);
            float ew = mNavInfo.measureText(info, 0, info.length());
            c.drawText(info, 0, info.length(), cx - ew / 2f, baseline(mNavInfo, dp(112)), mNavInfo);
        }

        float by = dp(150);
        float bw = dp(150);
        float bh = dp(32);
        float bx = cx - bw / 2f;
        mIcon.setStyle(Paint.Style.FILL);
        mIcon.setColor(fg());
        mIcon.setAlpha((int) (0x1F * a));
        mRect.set(bx, by - bh / 2f, bx + bw, by + bh / 2f);
        c.drawRoundRect(mRect, bh / 2f, bh / 2f, mIcon);

        mNavBtn.setAlpha((int) (255 * 0.95f * a));
        String label = "打开地图";
        float lw = mNavBtn.measureText(label);
        c.drawText(label, cx - lw / 2f, baseline(mNavBtn, by), mNavBtn);
    }

    private void drawNavGlyph(Canvas c, float cx, float cy, float r, float a, String action) {
        mIcon.setStyle(Paint.Style.FILL);
        mIcon.setColor(lightFg() ? 0x0F171A21 : 0x1FFFFFFF);
        mIcon.setAlpha((int) (0x1F * a));
        c.drawCircle(cx, cy, r, mIcon);
        mGlyphPaint.setColorFilter(new PorterDuffColorFilter(fg(), PorterDuff.Mode.SRC_IN));
        if (mNavIcon != null && !mNavIcon.isRecycled()) {
            mGlyphPaint.setAlpha((int) (255 * a));
            float s = r * 1.34f;
            mRect.set(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f);
            c.drawBitmap(mNavIcon, null, mRect, mGlyphPaint);
            return;
        }
        drawManeuver(c, cx, cy, r * 1.15f, a, action);
    }

    private void drawManeuver(Canvas c, float cx, float cy, float s, float a, String action) {
        String act = action == null ? "" : action;
        mIcon.setStyle(Paint.Style.STROKE);
        mIcon.setStrokeWidth(dp(2.6f));
        mIcon.setStrokeCap(Paint.Cap.ROUND);
        mIcon.setStrokeJoin(Paint.Join.ROUND);
        mIcon.setColor(fg());
        mIcon.setAlpha((int) (245 * a));
        mPath.reset();

        boolean left = act.contains("左");
        boolean right = act.contains("右");
        boolean uturn = act.contains("掉头") || act.contains("调头");
        boolean straight = act.contains("直行") || act.contains("直走");
        boolean round = act.contains("环岛");

        if (round) {
            mRect.set(cx - s * 0.55f, cy - s * 0.55f, cx + s * 0.55f, cy + s * 0.55f);
            c.drawArc(mRect, 40, 280, false, mIcon);
            mPath.moveTo(cx + s * 0.30f, cy - s * 0.52f);
            mPath.lineTo(cx + s * 0.62f, cy - s * 0.30f);
            mPath.lineTo(cx + s * 0.24f, cy - s * 0.16f);
            c.drawPath(mPath, mIcon);
            return;
        }
        if (uturn) {
            mPath.moveTo(cx - s * 0.45f, cy + s * 0.6f);
            mPath.lineTo(cx - s * 0.45f, cy - s * 0.15f);
            mPath.quadTo(cx - s * 0.45f, cy - s * 0.62f, cx + s * 0.1f, cy - s * 0.62f);
            mPath.quadTo(cx + s * 0.55f, cy - s * 0.62f, cx + s * 0.55f, cy - s * 0.1f);
            c.drawPath(mPath, mIcon);
            mPath.reset();
            mPath.moveTo(cx + s * 0.24f, cy - s * 0.28f);
            mPath.lineTo(cx + s * 0.55f, cy - s * 0.02f);
            mPath.lineTo(cx + s * 0.86f, cy - s * 0.28f);
            c.drawPath(mPath, mIcon);
            return;
        }
        if (left || right) {
            float dir = left ? -1f : 1f;
            mPath.moveTo(cx - dir * s * 0.1f, cy + s * 0.75f);
            mPath.lineTo(cx - dir * s * 0.1f, cy + s * 0.05f);
            mPath.quadTo(cx - dir * s * 0.1f, cy - s * 0.35f, cx + dir * s * 0.4f, cy - s * 0.35f);
            c.drawPath(mPath, mIcon);
            mPath.reset();
            mPath.moveTo(cx + dir * s * 0.12f, cy - s * 0.62f);
            mPath.lineTo(cx + dir * s * 0.45f, cy - s * 0.35f);
            mPath.lineTo(cx + dir * s * 0.12f, cy - s * 0.08f);
            c.drawPath(mPath, mIcon);
            return;
        }

        mPath.moveTo(cx, cy + s * 0.75f);
        mPath.lineTo(cx, cy - s * 0.35f);
        c.drawPath(mPath, mIcon);
        mPath.reset();
        mPath.moveTo(cx - s * 0.30f, cy - s * 0.10f);
        mPath.lineTo(cx, cy - s * 0.48f);
        mPath.lineTo(cx + s * 0.30f, cy - s * 0.10f);
        c.drawPath(mPath, mIcon);
    }

    private void drawArt(Canvas c, float cx, float cy, float r, float a, boolean circle) {
        mArtPaint.setAlpha((int) (255 * a));
        mRect.set(cx - r, cy - r, cx + r, cy + r);
        if (mArt != null && !mArt.isRecycled()) {
            int save = c.save();
            mPath.reset();
            if (circle) {
                mPath.addCircle(cx, cy, r, Path.Direction.CW);
            } else {
                mPath.addRoundRect(mRect, r * 0.36f, r * 0.36f, Path.Direction.CW);
            }
            c.clipPath(mPath);
            float bw = mArt.getWidth();
            float bh = mArt.getHeight();
            float scale = Math.max(2 * r / bw, 2 * r / bh);
            float dw = bw * scale;
            float dh = bh * scale;
            mRect.set(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f);
            c.drawBitmap(mArt, null, mRect, mArtPaint);
            c.restoreToCount(save);
        } else {
            mArtPaint.setColor(0xFF2A2A30);
            mArtPaint.setAlpha((int) (255 * a));
            if (circle) {
                c.drawCircle(cx, cy, r, mArtPaint);
            } else {
                mRect.set(cx - r, cy - r, cx + r, cy + r);
                c.drawRoundRect(mRect, r * 0.36f, r * 0.36f, mArtPaint);
            }
            mNote.setAlpha((int) (255 * a));
            mNote.setTextSize(r * 1.15f);
            float nw = mNote.measureText("♪");
            c.drawText("♪", cx - nw / 2f,
                    cy - (mNote.ascent() + mNote.descent()) / 2f, mNote);
        }
    }

    private void drawEq(Canvas c, float cx, float cy, float a) {
        long t = SystemClock.uptimeMillis();
        mIcon.setStyle(Paint.Style.FILL);
        mIcon.setColor(fg());
        mIcon.setAlpha((int) (220 * a));
        float bw = dp(2.4f);
        float gap = dp(2.8f);
        float total = bw * 3 + gap * 2;
        float baseX = cx - total / 2f;
        for (int i = 0; i < 3; i++) {
            double phase = t / 165.0 + i * 1.4;
            float hgt = dp(3.5f) + dp(7.5f) * (float) (0.5 + 0.5 * Math.sin(phase));
            float x = baseX + i * (bw + gap);
            mRect.set(x, cy - hgt / 2f, x + bw, cy + hgt / 2f);
            c.drawRoundRect(mRect, bw / 2f, bw / 2f, mIcon);
        }
    }

    private void drawPlay(Canvas c, float x, float y, float a, boolean flat) {
        mIcon.setStyle(Paint.Style.FILL);
        mIcon.setColor(fg());
        mIcon.setAlpha((int) (255 * a));
        if (!flat) {
            c.drawCircle(x, y, dp(21), mIcon);
            mIcon.setColor(lightFg() ? 0xFFFFFFFF : 0xFF0A0A0C);
        }
        if (mPlaying) {
            float bh = flat ? dp(18) : dp(15);
            float bw = flat ? dp(5) : dp(4.2f);
            float gap = flat ? dp(5.4f) : dp(4.6f);
            mRect.set(x - gap / 2f - bw, y - bh / 2f, x - gap / 2f, y + bh / 2f);
            c.drawRoundRect(mRect, dp(1.4f), dp(1.4f), mIcon);
            mRect.set(x + gap / 2f, y - bh / 2f, x + gap / 2f + bw, y + bh / 2f);
            c.drawRoundRect(mRect, dp(1.4f), dp(1.4f), mIcon);
        } else {
            float s = flat ? dp(9) : dp(7.4f);
            mPath.reset();
            mPath.moveTo(x - s * 0.5f, y - s);
            mPath.lineTo(x - s * 0.5f, y + s);
            mPath.lineTo(x + s * 0.95f, y);
            mPath.close();
            c.drawPath(mPath, mIcon);
        }
    }

    private void drawPrev(Canvas c, float x, float y, float a, boolean flat) {
        mIcon.setStyle(Paint.Style.FILL);
        mIcon.setColor(fg());
        mIcon.setAlpha((int) (235 * a));
        float s = flat ? dp(9) : dp(7);
        float barW = flat ? dp(3.2f) : dp(3);
        float barGap = flat ? dp(7.2f) : dp(9);
        mRect.set(x - barGap, y - s, x - barGap + barW, y + s);
        c.drawRoundRect(mRect, dp(1.2f), dp(1.2f), mIcon);
        mPath.reset();
        mPath.moveTo(x + dp(6), y - s);
        mPath.lineTo(x + dp(6), y + s);
        mPath.lineTo(x - dp(4), y);
        mPath.close();
        c.drawPath(mPath, mIcon);
    }

    private void drawNext(Canvas c, float x, float y, float a, boolean flat) {
        mIcon.setStyle(Paint.Style.FILL);
        mIcon.setColor(fg());
        mIcon.setAlpha((int) (235 * a));
        float s = flat ? dp(9) : dp(7);
        float barW = flat ? dp(3.2f) : dp(3);
        float barGap = flat ? dp(7.2f) : dp(9);
        mRect.set(x + barGap - barW, y - s, x + barGap, y + s);
        c.drawRoundRect(mRect, dp(1.2f), dp(1.2f), mIcon);
        mPath.reset();
        mPath.moveTo(x - dp(6), y - s);
        mPath.lineTo(x - dp(6), y + s);
        mPath.lineTo(x + dp(4), y);
        mPath.close();
        c.drawPath(mPath, mIcon);
    }

    private static String fmt(long ms) {
        if (ms < 0) ms = 0;
        long total = ms / 1000;
        long m = total / 60;
        long s = total % 60;
        return m + ":" + (s < 10 ? "0" + s : String.valueOf(s));
    }

    private void animateTo(final float target) {
        if (mAnim != null) mAnim.cancel();

        if (mWidthAnim != null) {
            mWidthAnim.cancel();
            mWidthAnim = null;
        }
        mDrawW = pillWidth();
        final boolean expanding = target > mExpand;
        SpringInterpolator si = expanding
                ? new SpringInterpolator(0.95f, 0.35f)
                : new SpringInterpolator(1.0f, 0.20f);
        ValueAnimator va = ValueAnimator.ofFloat(mExpand, target);
        va.setDuration(si.durationMs());
        va.setInterpolator(si);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mExpand = (Float) animation.getAnimatedValue();

                mHost.setBlur(true);
                invalidate();
            }
        });
        va.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (canceled) return;
                if (!expanding) {
                    mExpand = 0f;
                    mHost.resizeWindow(windowWidth(), windowHeight());
                    mHost.onCollapseEnd();
                } else {
                    mExpand = 1f;
                }
                if (DEBUG_ANIM) {
                    IslandHook.log("animEnd target=" + target + " dw=" + Math.round(mDrawW)
                            + " win=" + getWidth() + "x" + getHeight());
                }
                invalidate();
            }
        });
        mAnim = va;
        va.start();
    }

    void debugExpand() {
        expand();
    }

    private void expand() {
        if (mExpanded) return;
        if (mMode == MODE_TRANSIENT) return;
        IslandHook.log("expand (pill tap)");        mExpanded = true;
        mTouchGuardUntil = SystemClock.uptimeMillis() + 350;
        mHost.onExpandBegin();
        mHost.onExpandStart();
        noteContentChanged();
        scheduleAutoCollapse();
        animateTo(1f);
    }

    private void collapse() {
        if (!mExpanded) return;
        IslandHook.log("collapse");
        mExpanded = false;
        mSeekPreview = -1;
        mSeeking = false;
        cancelAutoCollapse();
        animateTo(0f);
    }

    private boolean running() {
        return mAnim != null && mAnim.isRunning();
    }

    private float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float secCx() {
        return cardWidth() + secGap() + secCircleR();
    }

    private int hitTest(float x, float y) {
        if (mMode == MODE_TRANSIENT) return T_NONE;
        float cw = cardWidth();
        if (mMode == MODE_NAV) {
            return T_OPEN;
        }
        if (mMode == MODE_CAPSULE) {
            int n = Math.min(3, mCapActions.length);
            if (n > 0 && y > dp(130) && y < dp(164)) {
                float margin = dp(15);
                float gap = dp(8);
                float total = cw - margin * 2;
                float bw = (total - gap * (n - 1)) / n;
                for (int i = 0; i < n; i++) {
                    float bx = margin + i * (bw + gap);
                    if (x >= bx && x <= bx + bw) {
                        mCapHitIndex = i;
                        return T_CAP_ACTION;
                    }
                }
            }
            return T_OPEN;
        }
        float cx = cw / 2f;
        float btnY = dp(103.5f);
        if (dist(x, y, cx, btnY) < dp(24)) return T_PLAY;
        if (dist(x, y, cx - dp(78), btnY) < dp(24)) return T_PREV;
        if (dist(x, y, cx + dp(78), btnY) < dp(24)) return T_NEXT;
        if (y > dp(114) && y < dp(152)) return T_SEEK;
        return T_OPEN;
    }

    private void updateSeekPreview(float x) {
        if (mDuration <= 0) return;
        float cw = cardWidth();
        float x0 = dp(69);
        float x1 = cw - dp(69);
        float f = clamp01((x - x0) / (x1 - x0));
        mSeekPreview = (long) (f * mDuration);
        invalidate();
    }

    boolean onCardTouch(MotionEvent ev) {
        return onTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (running()) return true;
        if (SystemClock.uptimeMillis() < mTouchGuardUntil) return true;

        float x = ev.getX() - (getWidth() - cardWidth()) / 2f;
        float y = ev.getY() - cardTop();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelAutoCollapse();
                mDownX = x;
                mDownY = y;
                mMoved = false;
                mSeeking = false;
                mSwipeAction = 0;
                mCapHitIndex = -1;
                if (!mExpanded) {
                    mDownTarget = T_PILL;
                    mPressedTarget = T_NONE;
                    mHost.onExpandStart();
                    return true;
                }

                if (y < 0) {
                    mDownTarget = T_NONE;
                    float pw = mDrawW > 0 ? mDrawW : pillWidth();
                    float px = ev.getX();
                    float pl = (getWidth() - pw) / 2f;
                    float dotCx = pl + pw + secGap() + secCircleR();
                    if (mSecType != 0
                            && dist(px, ev.getY(), dotCx, pillHeight() / 2f)
                            < secCircleR() + dp(8)) {
                        mHost.switchActivity();
                    } else if (mPillSnapValid && px >= pl && px <= pl + pw) {
                        mHost.tapPill();
                    } else {
                        collapse();
                    }
                    return true;
                }
                mDownTarget = hitTest(x, y);
                mPressedTarget = mDownTarget;
                if (mDownTarget == T_SEEK) {
                    mSeeking = true;
                    updateSeekPreview(x);
                }
                invalidate();
                return mDownTarget != T_NONE;
            case MotionEvent.ACTION_MOVE:
                float dx = x - mDownX;
                float dy = y - mDownY;
                if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) {
                    mMoved = true;
                    if (mPressedTarget != T_NONE) {
                        mPressedTarget = T_NONE;
                        invalidate();
                    }
                }
                if (mSeeking) {
                    updateSeekPreview(x);
                } else if (mExpanded) {
                    if (Math.abs(dy) > dp(28) && Math.abs(dy) > Math.abs(dx) * 1.15f) {
                        mSwipeAction = dy < 0 ? 1 : 2;
                    } else if (Math.abs(dx) > dp(34) && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                        mSwipeAction = dx > 0 ? 3 : 4;
                    } else {
                        mSwipeAction = 0;
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (mSeeking) {
                    if (mSeekPreview >= 0) {
                        mBasePos = mSeekPreview;
                        mBaseTime = SystemClock.elapsedRealtime();
                        mHost.seekTo(mSeekPreview);
                    }
                    mSeekPreview = -1;
                    mSeeking = false;
                    invalidate();
                    if (mExpanded) scheduleAutoCollapse();
                    return true;
                }
                if (!mExpanded) {
                    if (!mMoved) {
                        expand();
                    } else {
                        float mdx = x - mDownX;
                        float mdy = y - mDownY;
                        if (Math.abs(mdx) > dp(36) && Math.abs(mdx) > Math.abs(mdy) * 1.3f) {
                            mHost.swipeSession(mdx > 0 ? 1 : -1);
                        }
                    }
                    mDownTarget = T_NONE;
                    mPressedTarget = T_NONE;
                    return true;
                }
                if (mSwipeAction == 1) {
                    collapse();
                } else if (mSwipeAction == 3 || mSwipeAction == 4) {
                    mHost.swipeSession(mSwipeAction == 3 ? 1 : -1);
                } else if (!mMoved && mDownTarget != T_NONE) {
                    switch (mDownTarget) {
                        case T_SWITCH:
                            mHost.switchActivity();
                            break;
                        case T_CAP_ACTION:
                            if (mCapHitIndex >= 0) mHost.capsuleAction(mCapHitIndex);
                            break;
                        case T_PREV:
                            mHost.prev();
                            break;
                        case T_PLAY:
                            mHost.playPause();
                            break;
                        case T_NEXT:
                            mHost.next();
                            break;
                        case T_OPEN:
                            mHost.openCurrent();
                            break;
                        default:
                            break;
                    }
                }
                mDownTarget = T_NONE;
                mPressedTarget = T_NONE;
                mSwipeAction = 0;
                mCapHitIndex = -1;
                invalidate();
                if (mExpanded) scheduleAutoCollapse();
                return true;
            case MotionEvent.ACTION_CANCEL:
                mSeeking = false;
                mSeekPreview = -1;
                mDownTarget = T_NONE;
                mPressedTarget = T_NONE;
                mSwipeAction = 0;
                invalidate();
                if (mExpanded) scheduleAutoCollapse();
                return true;
            case MotionEvent.ACTION_OUTSIDE:
                if (mExpanded) collapse();
                return true;
            default:
                return super.onTouchEvent(ev);
        }
    }

    boolean onPillTouch(MotionEvent ev) {
        float x = ev.getX() - sideMargin();
        float y = ev.getY();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = x;
                mDownY = y;
                mMoved = false;
                mSeeking = false;
                mDownTarget = T_PILL;
                mHost.prewarmExpand();
                mHost.onExpandStart();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(x - mDownX) > dp(6) || Math.abs(y - mDownY) > dp(6)) {
                    mMoved = true;
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!mMoved) {
                    if (mSecType != 0 && dist(x, y,
                            pillWidth() + secGap() + secCircleR(), pillHeight() / 2f)
                            < secCircleR() + dp(8)) {
                        mHost.switchActivity();
                    } else {
                        expand();
                    }
                } else {
                    float dx = x - mDownX;
                    float dy = y - mDownY;
                    if (Math.abs(dx) > dp(36) && Math.abs(dx) > Math.abs(dy) * 1.3f) {
                        mHost.swipeSession(dx > 0 ? 1 : -1);
                    }
                }
                mDownTarget = T_NONE;
                if (!mExpanded) mHost.onPillGestureEnd();
                return true;
            case MotionEvent.ACTION_CANCEL:
                mDownTarget = T_NONE;
                if (!mExpanded) mHost.onPillGestureEnd();
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopBurnIn();
        cancelAutoCollapse();
        recycleFake();
        if (mAnim != null) {
            mAnim.cancel();
            mAnim = null;
        }
        if (mSwapAnim != null) {
            mSwapAnim.cancel();
            mSwapAnim = null;
        }
        if (mWidthAnim != null) {
            mWidthAnim.cancel();
            mWidthAnim = null;
        }
        if (mBackdropAnim != null) {
            mBackdropAnim.cancel();
            mBackdropAnim = null;
        }
        super.onDetachedFromWindow();
    }
}
