package com.frank.tvhome;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One focusable app tile: 16:9 artwork with the name underneath. */
final class Tile extends LinearLayout {
    private final ArtView art;
    private final GradientDrawable ring;
    private final GradientDrawable goldRing;
    private boolean moving;
    private boolean focusedNow;

    Tile(Context c, float dp, AppEntry e, float labelSp, Runnable onFocused) {
        super(c);
        setOrientation(VERTICAL);
        setFocusable(true);
        setClickable(true);
        int pad = Math.round(10f * dp);
        setPadding(pad, pad, pad, pad);

        art = new ArtView(c, dp);
        addView(art, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView label = new TextView(c);
        label.setText(e.label);
        label.setTextSize(labelSp);
        label.setTextColor(0xFFD0D0D6);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(8f * dp);
        addView(label, lp);

        art.setImageDrawable(e.art);
        if (e.isBanner) {
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            int p = Math.round(14f * dp);
            art.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            art.setPadding(p, p, p, p);
        }

        ring = new GradientDrawable();
        ring.setColor(0x00000000);
        ring.setCornerRadius(10f * dp);
        ring.setStroke(Math.max(2, Math.round(2f * dp)), 0xFFFFFFFF);

        goldRing = new GradientDrawable();
        goldRing.setColor(0x00000000);
        goldRing.setCornerRadius(10f * dp);
        goldRing.setStroke(Math.max(3, Math.round(3f * dp)), 0xFFFFC94D);

        setOnFocusChangeListener((v, focused) -> {
            focusedNow = focused;
            refreshRing();
            float s = focused ? 1.06f : 1f;
            animate().scaleX(s).scaleY(s).setDuration(120).start();
            if (focused && onFocused != null) onFocused.run();
        });
    }

    /** While true the tile shows a gold ring: it is being moved along the Favorites row. */
    void setMoving(boolean on) {
        moving = on;
        refreshRing();
    }

    private void refreshRing() {
        art.setForeground(moving ? goldRing : focusedNow ? ring : null);
    }

    /** Artwork box that is always 16:9 with rounded corners. */
    private static final class ArtView extends ImageView {
        ArtView(Context c, float dp) {
            super(c);
            setBackgroundColor(0xFF1C1C1E);
            final float radius = 10f * dp;
            setOutlineProvider(new Round(radius));
            setClipToOutline(true);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int w = MeasureSpec.getSize(widthSpec);
            setMeasuredDimension(w, w * 9 / 16);
        }
    }

    /** Rounded-rectangle outline (a named class: the SDK d8 cannot read anonymous ones from JDK 27). */
    private static final class Round extends ViewOutlineProvider {
        private final float radius;

        Round(float radius) {
            this.radius = radius;
        }

        @Override
        public void getOutline(View v, Outline outline) {
            outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
        }
    }
}
