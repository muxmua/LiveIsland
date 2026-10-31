package com.harmonyos.liveIsland;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;

public class IslandSettingsActivity extends Activity {
    private static final int ACCENT = 0xFF4F7CFF;
    private static final int ACCENT_2 = 0xFF8B5CF6;
    private static final int INK = 0xFF171A21;
    private static final int INK_SUB = 0xFF6E7686;
    private static final int INK_FAINT = 0xFF9AA2B1;
    private static final int CARD_FILL = 0xE8FFFFFF;
    private static final int HAIRLINE = 0x12000000;

    private float mDp;
    private final int[] mBgModes = {
            IslandPrefs.BG_FROSTED, IslandPrefs.BG_BLACK
    };
    private String[] mBgTitles;
    private String[] mBgDescs;
    private final View[] mBgRows = new View[2];
    private final TextView[] mBgChecks = new TextView[2];
    private int mCurBg = IslandPrefs.BG_FROSTED;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDp = getResources().getDisplayMetrics().density;
        mBgTitles = new String[]{getString(R.string.bg_frosted), getString(R.string.bg_black)};
        mBgDescs = new String[]{
                getString(R.string.bg_frosted_desc), getString(R.string.bg_black_desc)};

        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFFF3F5FA);

        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap blurred = blurredWallpaper();
        if (blurred != null) {
            bg.setImageBitmap(blurred);
            bg.setAlpha(0.9f);
        }
        root.addView(bg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View veil = new View(this);
        veil.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xB8FFFFFF, 0xDEF7F8FC, 0xEEF3F5FA}));
        root.addView(veil, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View blob1 = new View(this);
        GradientDrawable b1 = new GradientDrawable();
        b1.setShape(GradientDrawable.OVAL);
        b1.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        b1.setGradientRadius(dp(360));
        b1.setColors(new int[]{0x244F7CFF, 0x004F7CFF});
        blob1.setBackground(b1);
        FrameLayout.LayoutParams b1lp = new FrameLayout.LayoutParams(dp(640), dp(640));
        b1lp.gravity = Gravity.TOP | Gravity.LEFT;
        b1lp.leftMargin = -dp(240);
        b1lp.topMargin = -dp(300);
        root.addView(blob1, b1lp);

        View blob2 = new View(this);
        GradientDrawable b2 = new GradientDrawable();
        b2.setShape(GradientDrawable.OVAL);
        b2.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        b2.setGradientRadius(dp(320));
        b2.setColors(new int[]{0x1E8B5CF6, 0x008B5CF6});
        blob2.setBackground(b2);
        FrameLayout.LayoutParams b2lp = new FrameLayout.LayoutParams(dp(560), dp(560));
        b2lp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        b2lp.rightMargin = -dp(200);
        b2lp.bottomMargin = -dp(260);
        root.addView(blob2, b2lp);

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int contentW = Math.min(screenW, dp(1000));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), statusBarHeight() + dp(10), dp(18), dp(36));

        boolean wide = screenW / mDp >= 840f;
        if (wide) {
            LinearLayout cols = new LinearLayout(this);
            cols.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.addView(buildHeader());
            left.addView(sectionLabel(getString(R.string.section_appearance), dp(18)));
            left.addView(buildBackgroundCard());
            left.addView(sectionLabel(getString(R.string.section_tips), dp(18)));
            left.addView(buildTipsCard());

            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.addView(sectionLabel(getString(R.string.section_features), 0));
            right.addView(buildFeatureCard());
            right.addView(sectionLabel(getString(R.string.section_maintenance), dp(18)));
            right.addView(buildMaintenanceCard());

            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            rlp.leftMargin = dp(18);
            cols.addView(left, llp);
            cols.addView(right, rlp);
            content.addView(cols);
            content.addView(buildFooter());
        } else {
            content.addView(buildHeader());
            content.addView(sectionLabel(getString(R.string.section_appearance), dp(18)));
            content.addView(buildBackgroundCard());
            content.addView(sectionLabel(getString(R.string.section_tips), dp(18)));
            content.addView(buildTipsCard());
            content.addView(sectionLabel(getString(R.string.section_features), dp(18)));
            content.addView(buildFeatureCard());
            content.addView(sectionLabel(getString(R.string.section_maintenance), dp(18)));
            content.addView(buildMaintenanceCard());
            content.addView(buildFooter());
        }

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipToPadding(false);
        sv.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                contentW, ViewGroup.LayoutParams.MATCH_PARENT);
        slp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        root.addView(sv, slp);

        setContentView(root);
    }

    private View buildHeader() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(4), dp(6), dp(4), dp(16));

        FrameLayout icon = new FrameLayout(this);
        GradientDrawable ig = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ACCENT, ACCENT_2});
        ig.setCornerRadius(dp(14));
        icon.setBackground(ig);

        LinearLayout mini = new LinearLayout(this);
        mini.setOrientation(LinearLayout.HORIZONTAL);
        mini.setGravity(Gravity.CENTER_VERTICAL);

        View miniPill = new View(this);
        GradientDrawable mp = new GradientDrawable();
        mp.setColor(0xFF101218);
        mp.setCornerRadius(dp(7));
        miniPill.setBackground(mp);
        mini.addView(miniPill, new LinearLayout.LayoutParams(dp(27), dp(14)));

        View miniDot = new View(this);
        GradientDrawable md = new GradientDrawable();
        md.setShape(GradientDrawable.OVAL);
        md.setColor(0xFFFFFFFF);
        miniDot.setBackground(md);
        LinearLayout.LayoutParams mdlp = new LinearLayout.LayoutParams(dp(7), dp(7));
        mdlp.leftMargin = dp(3);
        mini.addView(miniDot, mdlp);

        FrameLayout.LayoutParams minilp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        minilp.gravity = Gravity.CENTER;
        icon.addView(mini, minilp);
        box.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(14);
        box.addView(text, tlp);

        TextView title = new TextView(this);
        title.setText(R.string.settings_title);
        title.setTextColor(INK);
        title.setTextSize(23f);
        title.setTypeface(medium());
        text.addView(title);

        TextView sub = new TextView(this);
        sub.setText(getString(R.string.settings_subtitle,
                IslandPrefs.VERSION_NAME, IslandPrefs.AUTHOR));
        sub.setTextColor(INK_SUB);
        sub.setTextSize(12.5f);
        sub.setPadding(0, dp(3), 0, 0);
        text.addView(sub);

        TextView chip = new TextView(this);
        chip.setText(R.string.settings_badge);
        chip.setTextColor(ACCENT);
        chip.setTextSize(11f);
        chip.setTypeface(medium());
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        chip.setBackground(roundRect(0x144F7CFF, dp(11)));
        box.addView(chip);

        return box;
    }

    private View buildBackgroundCard() {
        LinearLayout card = card();
        card.setPadding(dp(8), dp(8), dp(8), dp(8));

        mCurBg = IslandPrefs.getIntDirect(this, IslandPrefs.K_BG, IslandPrefs.BG_FROSTED);
        if (mCurBg != IslandPrefs.BG_FROSTED && mCurBg != IslandPrefs.BG_BLACK) {
            mCurBg = IslandPrefs.BG_FROSTED;
        }

        for (int i = 0; i < mBgModes.length; i++) {
            final int mode = mBgModes[i];

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            row.setClickable(true);

            View preview = new View(this);
            preview.setBackground(previewBg(mode));
            row.addView(preview, new LinearLayout.LayoutParams(dp(56), dp(30)));

            LinearLayout text = new LinearLayout(this);
            text.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tlp.leftMargin = dp(13);
            row.addView(text, tlp);

            TextView t = new TextView(this);
            t.setText(mBgTitles[i]);
            t.setTextColor(INK);
            t.setTextSize(15f);
            t.setTypeface(medium());
            text.addView(t);

            TextView d = new TextView(this);
            d.setText(mBgDescs[i]);
            d.setTextColor(INK_SUB);
            d.setTextSize(12f);
            d.setPadding(0, dp(2), 0, 0);
            text.addView(d);

            TextView check = new TextView(this);
            check.setGravity(Gravity.CENTER);
            check.setTextSize(12f);
            check.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(check, new LinearLayout.LayoutParams(dp(22), dp(22)));

            mBgRows[i] = row;
            mBgChecks[i] = check;
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectBg(mode);
                }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i < mBgModes.length - 1) lp.bottomMargin = dp(6);
            card.addView(row, lp);
        }
        selectBg(mCurBg);

        return card;
    }

    private void selectBg(int mode) {
        mCurBg = mode;
        for (int i = 0; i < mBgModes.length; i++) {
            boolean on = mBgModes[i] == mode;
            if (mBgRows[i] != null) {
                mBgRows[i].setBackground(rowBg(on));
            }
            if (mBgChecks[i] != null) {
                styleCheck(mBgChecks[i], on);
            }
        }
        IslandPrefs.putInt(this, IslandPrefs.K_BG, mode);
    }

    private Drawable rowBg(boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(on ? 0x144F7CFF : 0x00FFFFFF);
        g.setCornerRadius(dp(16));
        if (on) g.setStroke(Math.max(1, (int) dp(1.2f)), ACCENT);
        float r = dp(16);
        ShapeDrawable mask = new ShapeDrawable(new RoundRectShape(
                new float[]{r, r, r, r, r, r, r, r}, null, null));
        return new RippleDrawable(
                new ColorStateList(new int[][]{{}}, new int[]{0x144F7CFF}), g, mask);
    }

    private void styleCheck(TextView v, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(on ? ACCENT : 0x00FFFFFF);
        g.setStroke(Math.max(1, (int) dp(1.4f)), on ? ACCENT : 0xFFD3D8E4);
        v.setBackground(g);
        v.setText(on ? "✓" : "");
        v.setTextColor(0xFFFFFFFF);
    }

    private GradientDrawable previewBg(int mode) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(15));
        switch (mode) {
            case IslandPrefs.BG_BLACK:
                g.setColor(0xFF000000);
                break;
            default:
                g.setGradientType(GradientDrawable.LINEAR_GRADIENT);
                g.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
                g.setColors(new int[]{0xE8FDFDFF, 0xD9E7EBF2});
                break;
        }
        return g;
    }

    private View buildTipsCard() {
        LinearLayout card = card();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        addTip(card, getString(R.string.tip_auto), false);
        addTip(card, getString(R.string.tip_rotate), false);
        addTip(card, getString(R.string.tip_instant), true);
        return card;
    }

    private void addTip(LinearLayout card, String text, boolean last) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        dot.setBackground(roundRect(ACCENT, dp(3)));
        row.addView(dot, new LinearLayout.LayoutParams(dp(6), dp(6)));

        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(INK_SUB);
        t.setTextSize(12.5f);
        t.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(10);
        row.addView(t, tlp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (!last) lp.bottomMargin = dp(10);
        card.addView(row, lp);
    }

    private View buildFeatureCard() {
        LinearLayout card = card();
        card.setPadding(dp(6), dp(4), dp(6), dp(4));
        addSwitch(card, IslandPrefs.K_FEAT_MEDIA, "音", getString(R.string.feat_media), getString(R.string.feat_media_desc), false);
        addSwitch(card, IslandPrefs.K_FEAT_NAV, "导", getString(R.string.feat_nav), getString(R.string.feat_nav_desc), false);
        addSwitch(card, IslandPrefs.K_FEAT_CAPSULE, "实", getString(R.string.feat_capsule), getString(R.string.feat_capsule_desc), false);
        addSwitch(card, IslandPrefs.K_FEAT_STOPWATCH, "秒", getString(R.string.feat_stopwatch), getString(R.string.feat_stopwatch_desc), false);
        addSwitch(card, IslandPrefs.K_FEAT_USB, "U", getString(R.string.feat_usb), getString(R.string.feat_usb_desc), false);
        addSwitch(card, IslandPrefs.K_FEAT_CHARGE, "充", getString(R.string.feat_charge), getString(R.string.feat_charge_desc), false);
        addSwitch(card, IslandPrefs.K_FEAT_FLASH, "灯", getString(R.string.feat_flash), getString(R.string.feat_flash_desc), false);
        addSwitch(card, IslandPrefs.K_FEAT_RECORD, "录", getString(R.string.feat_record), getString(R.string.feat_record_desc), true);
        return card;
    }

    private void addSwitch(LinearLayout card, final String key, String glyph,
                           String title, String desc, boolean last) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(10), dp(12));

        TextView icon = new TextView(this);
        icon.setText(glyph);
        icon.setTextColor(ACCENT);
        icon.setTextSize(12.5f);
        icon.setTypeface(medium());
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundRect(0x124F7CFF, dp(10)));
        row.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(12);
        row.addView(text, tlp);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(INK);
        t.setTextSize(15f);
        text.addView(t);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(INK_SUB);
        d.setTextSize(12f);
        d.setPadding(0, dp(2), 0, 0);
        text.addView(d);

        final Toggle sw = new Toggle(this, mDp);
        boolean on = IslandPrefs.getBoolDirect(this, key, true);
        sw.setChecked(on, false);
        sw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !sw.isChecked();
                sw.setChecked(next, true);
                IslandPrefs.putBool(IslandSettingsActivity.this, key, next);
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(46), dp(28));
        slp.leftMargin = dp(4);
        row.addView(sw, slp);

        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!last) {
            View div = new View(this);
            div.setBackgroundColor(HAIRLINE);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) dp(0.6f)));
            dlp.leftMargin = dp(54);
            dlp.rightMargin = dp(12);
            card.addView(div, dlp);
        }
    }

    private View buildMaintenanceCard() {
        LinearLayout card = card();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = new TextView(this);
        title.setText(R.string.restart_title);
        title.setTextColor(INK);
        title.setTextSize(15f);
        title.setTypeface(medium());
        card.addView(title);

        TextView desc = new TextView(this);
        desc.setText(R.string.restart_desc);
        desc.setTextColor(INK_SUB);
        desc.setTextSize(12f);
        desc.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(5);
        card.addView(desc, dlp);

        TextView btn = new TextView(this);
        btn.setText(R.string.restart_button);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(15f);
        btn.setTypeface(medium());
        btn.setGravity(Gravity.CENTER);
        btn.setClickable(true);
        btn.setBackground(primaryButtonBg());
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restartSystemUi();
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        blp.topMargin = dp(14);
        card.addView(btn, blp);

        return card;
    }

    private Drawable primaryButtonBg() {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ACCENT, ACCENT_2});
        g.setCornerRadius(dp(16));
        float r = dp(16);
        ShapeDrawable mask = new ShapeDrawable(new RoundRectShape(
                new float[]{r, r, r, r, r, r, r, r}, null, null));
        return new RippleDrawable(
                new ColorStateList(new int[][]{{}}, new int[]{0x33FFFFFF}), g, mask);
    }

    private void restartSystemUi() {
        Toast.makeText(this, R.string.restart_toast, Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    Process p = Runtime.getRuntime().exec("su");
                    DataOutputStream os = new DataOutputStream(p.getOutputStream());
                    os.writeBytes("kill -9 $(pidof com.android.systemui)\n");
                    os.writeBytes("exit\n");
                    os.flush();
                    p.waitFor();
                    ok = true;
                } catch (Throwable t) {
                    android.util.Log.i("LiveIsland", "restart scope failed: " + t);
                }
                final boolean done = ok;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(IslandSettingsActivity.this,
                                done ? getString(R.string.restart_ok) : getString(R.string.restart_need_root),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private View buildFooter() {
        TextView t = new TextView(this);
        t.setText(getString(R.string.settings_footer,
                IslandPrefs.VERSION_NAME, IslandPrefs.AUTHOR));
        t.setTextColor(INK_FAINT);
        t.setTextSize(11.5f);
        t.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        lp.leftMargin = dp(4);
        lp.rightMargin = dp(4);
        t.setLayoutParams(lp);
        return t;
    }

    private static class Toggle extends View {
        private final Paint mTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mThumb = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float mDp;
        private boolean mChecked;
        private float mAnim;
        private android.animation.ValueAnimator mVa;

        Toggle(Context c, float dp) {
            super(c);
            mDp = dp;
            setClickable(true);
        }

        void setChecked(boolean checked, boolean animate) {
            if (mChecked == checked && (animate || mAnim == (checked ? 1f : 0f))) return;
            mChecked = checked;
            float target = checked ? 1f : 0f;
            if (mVa != null) {
                mVa.cancel();
                mVa = null;
            }
            if (!animate) {
                mAnim = target;
                invalidate();
                return;
            }
            mVa = android.animation.ValueAnimator.ofFloat(mAnim, target);
            mVa.setDuration(180);
            mVa.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(android.animation.ValueAnimator a) {
                    mAnim = (float) a.getAnimatedValue();
                    invalidate();
                }
            });
            mVa.start();
        }

        boolean isChecked() {
            return mChecked;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            float r = h / 2f;
            mTrack.setColor(blend(0xFFDDE2EC, ACCENT, mAnim));
            c.drawRoundRect(0, 0, w, h, r, r, mTrack);

            float margin = mDp * 3f;
            float tr = r - margin;
            float cx = margin + tr + (w - 2f * (margin + tr)) * mAnim;
            float cy = h / 2f;

            mShadow.setColor(0x1A000000);
            c.drawCircle(cx, cy + mDp * 1.2f, tr + mDp * 0.5f, mShadow);
            mThumb.setColor(0xFFFFFFFF);
            c.drawCircle(cx, cy, tr, mThumb);
        }

        private static int blend(int from, int to, float t) {
            t = Math.max(0f, Math.min(1f, t));
            int a = (int) (((from >> 24) & 0xFF)
                    + (((to >> 24) & 0xFF) - ((from >> 24) & 0xFF)) * t);
            int r = (int) (((from >> 16) & 0xFF)
                    + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
            int g = (int) (((from >> 8) & 0xFF)
                    + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
            int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    private TextView sectionLabel(String text, int topMargin) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(INK_FAINT);
        t.setTextSize(12f);
        t.setLetterSpacing(0.08f);
        t.setTypeface(medium());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        lp.bottomMargin = dp(8);
        lp.leftMargin = dp(4);
        t.setLayoutParams(lp);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD_FILL);
        g.setCornerRadius(dp(22));
        g.setStroke(Math.max(1, (int) dp(0.8f)), 0x80FFFFFF);
        c.setBackground(g);
        return c;
    }

    private GradientDrawable roundRect(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    private Bitmap blurredWallpaper() {
        try {
            WallpaperManager wm = WallpaperManager.getInstance(this);
            Drawable d = wm.getDrawable();
            if (d == null) return null;
            int w = 64;
            int h = Math.max(64, Math.round(64f * viewH() / Math.max(1f, viewW())));
            Bitmap small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(small);
            d.setBounds(0, 0, w, h);
            d.draw(c);
            int[] px = new int[w * h];
            small.getPixels(px, 0, w, 0, 0, w, h);
            IslandView.boxBlurRgb(px, w, h, 4);
            IslandView.boxBlurRgb(px, w, h, 4);
            IslandView.boostSaturation(px, 1.18f, 10);
            small.setPixels(px, 0, w, 0, 0, w, h);
            return Bitmap.createScaledBitmap(small,
                    Math.max(1, viewW() / 2), Math.max(1, viewH() / 2), true);
        } catch (Throwable t) {
            android.util.Log.i("LiveIsland", "wallpaper blur failed: " + t);
            return null;
        }
    }

    private int viewW() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int viewH() {
        return getResources().getDisplayMetrics().heightPixels;
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : (int) (24 * mDp);
    }

    private int dp(float v) {
        return (int) (v * mDp + 0.5f);
    }
}
