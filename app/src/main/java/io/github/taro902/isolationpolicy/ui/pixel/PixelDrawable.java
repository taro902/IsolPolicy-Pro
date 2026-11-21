package io.github.taro902.isolationpolicy.ui.pixel;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class PixelDrawable extends Drawable {

    private final Paint fillPaint = new Paint();
    private final Paint borderPaint = new Paint();
    private final Paint lightPaint = new Paint();
    private final Paint darkPaint = new Paint();

    private final int unit;
    private final boolean inset;
    private final boolean interactive;
    private boolean pressed;

    public PixelDrawable(
            int fillColor,
            int borderColor,
            int bevelLight,
            int bevelDark,
            int unit,
            boolean inset,
            boolean interactive
    ) {
        this.unit = Math.max(2, unit);
        this.inset = inset;
        this.interactive = interactive;

        fillPaint.setAntiAlias(false);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(fillColor);

        borderPaint.setAntiAlias(false);
        borderPaint.setStyle(Paint.Style.FILL);
        borderPaint.setColor(borderColor);

        lightPaint.setAntiAlias(false);
        lightPaint.setStyle(Paint.Style.FILL);
        lightPaint.setColor(bevelLight);

        darkPaint.setAntiAlias(false);
        darkPaint.setStyle(Paint.Style.FILL);
        darkPaint.setColor(bevelDark);
    }

    public static PixelDrawable panel(PixelTheme t) {
        return new PixelDrawable(t.bgPanel, t.borderOuter, t.bevelLight, t.bevelDark, t.px, false, false);
    }

    public static PixelDrawable card(PixelTheme t, boolean active) {
        int bg = active ? t.bgCardActive : t.bgCard;
        int brd = active ? t.accentOk : t.borderOuter;
        return new PixelDrawable(bg, brd, t.bevelLight, t.bevelDark, t.px, false, false);
    }

    public static PixelDrawable inset(PixelTheme t) {
        return new PixelDrawable(t.bgInput, t.borderOuter, t.bevelLight, t.bevelDark, t.px, true, false);
    }

    public static PixelDrawable button(PixelTheme t, int fillColor, int borderColor) {
        return new PixelDrawable(fillColor, borderColor, t.bevelLight, t.bevelDark, t.px, false, true);
    }

    public static PixelDrawable tab(PixelTheme t, boolean active) {
        if (active) {
            return new PixelDrawable(t.bgCardActive, t.accentOk, t.bevelLight, t.bevelDark, t.px, false, true);
        }
        return new PixelDrawable(t.bgPanel, t.borderOuter, t.bevelLight, t.bevelDark, t.px, true, true);
    }

    @Override
    public boolean isStateful() {
        return interactive;
    }

    @Override
    protected boolean onStateChange(@NonNull int[] stateSet) {
        if (!interactive) {
            return false;
        }
        boolean nextPressed = false;
        for (int st : stateSet) {
            if (st == android.R.attr.state_pressed) {
                nextPressed = true;
                break;
            }
        }
        if (nextPressed != pressed) {
            pressed = nextPressed;
            invalidateSelf();
            return true;
        }
        return false;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect r = getBounds();
        int w = r.width();
        int h = r.height();
        int u = unit;
        if (w <= u * 4 || h <= u * 4) {
            canvas.drawRect(r, fillPaint);
            return;
        }

        int l = r.left;
        int t = r.top;
        int ri = r.right;
        int b = r.bottom;

        canvas.drawRect(l + u, t, ri - u, t + u, borderPaint);
        canvas.drawRect(l + u, b - u, ri - u, b, borderPaint);
        canvas.drawRect(l, t + u, l + u, b - u, borderPaint);
        canvas.drawRect(ri - u, t + u, ri, b - u, borderPaint);

        canvas.drawRect(l + u, t + u, ri - u, b - u, fillPaint);

        boolean sunken = inset || pressed;
        Paint topLeft = sunken ? darkPaint : lightPaint;
        Paint botRight = sunken ? lightPaint : darkPaint;

        canvas.drawRect(l + u, t + u, ri - u, t + u * 2, topLeft);
        canvas.drawRect(l + u, t + u, l + u * 2, b - u, topLeft);

        canvas.drawRect(l + u * 2, b - u * 2, ri - u, b - u, botRight);
        canvas.drawRect(ri - u * 2, t + u * 2, ri - u, b - u, botRight);
    }

    @Override
    public void setAlpha(int alpha) {
        fillPaint.setAlpha(alpha);
        borderPaint.setAlpha(alpha);
        lightPaint.setAlpha(alpha);
        darkPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        borderPaint.setColorFilter(colorFilter);
        lightPaint.setColorFilter(colorFilter);
        darkPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}