package io.github.taro902.isolationpolicy.ui.pixel;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class PixelChrome extends Drawable {

    private final Paint fillPaint = new Paint();
    private final Paint borderPaint = new Paint();
    private final Paint hiPaint = new Paint();
    private final Paint shPaint = new Paint();
    private final Paint shadowPaint = new Paint();
    private final Paint railPaint = new Paint();
    private final Paint railEdgePaint = new Paint();

    private final int unit;
    private final boolean inset;
    private final boolean dropShadow;
    private final boolean showRail;

    public PixelChrome(
            int fillColor,
            Shader fillShader,
            int borderColor,
            int highlightColor,
            int shadeColor,
            int shadowColor,
            Shader railShader,
            int railAccent,
            int unit,
            boolean inset,
            boolean dropShadow,
            boolean showRail
    ) {
        this.unit = Math.max(2, unit);
        this.inset = inset;
        this.dropShadow = dropShadow;
        this.showRail = showRail;

        fillPaint.setAntiAlias(false);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(fillColor);
        if (fillShader != null) {
            fillPaint.setShader(fillShader);
        }

        borderPaint.setAntiAlias(false);
        borderPaint.setStyle(Paint.Style.FILL);
        borderPaint.setColor(borderColor);

        hiPaint.setAntiAlias(false);
        hiPaint.setStyle(Paint.Style.FILL);
        hiPaint.setColor(highlightColor);

        shPaint.setAntiAlias(false);
        shPaint.setStyle(Paint.Style.FILL);
        shPaint.setColor(shadeColor);

        shadowPaint.setAntiAlias(false);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(shadowColor);

        railPaint.setAntiAlias(false);
        railPaint.setStyle(Paint.Style.FILL);
        if (railShader != null) {
            railPaint.setShader(railShader);
        } else {
            railPaint.setColor(railAccent);
        }

        railEdgePaint.setAntiAlias(false);
        railEdgePaint.setStyle(Paint.Style.FILL);
        railEdgePaint.setColor(railAccent);
    }

    public static PixelChrome panel(PixelPalette p, boolean dithered) {
        return new PixelChrome(
                p.bgPanel,
                dithered ? p.ditherShader : null,
                p.outline,
                p.highlight,
                p.shade,
                p.shadow,
                null,
                p.ok,
                p.px,
                false,
                true,
                false
        );
    }

    public static PixelChrome card(PixelPalette p, boolean guarded) {
        return new PixelChrome(
                guarded ? p.bgCardGuarded : p.bgCard,
                null,
                guarded ? p.ok : p.outline,
                p.highlight,
                p.shade,
                p.shadow,
                guarded ? p.railShader : null,
                p.ok,
                p.px,
                false,
                true,
                guarded
        );
    }

    public static PixelChrome inset(PixelPalette p) {
        return new PixelChrome(
                p.bgInset,
                null,
                p.outline,
                p.highlight,
                p.shade,
                p.shadow,
                null,
                p.ok,
                p.px,
                true,
                false,
                false
        );
    }

    public static PixelChrome modal(PixelPalette p) {
        return new PixelChrome(
                p.bgPanel,
                null,
                p.ok,
                p.highlight,
                p.shade,
                p.shadow,
                null,
                p.ok,
                p.px,
                false,
                true,
                false
        );
    }

    public static PixelChrome ditherBar(PixelPalette p) {
        return new PixelChrome(
                p.bgPanel,
                p.ditherShader,
                p.outline,
                p.highlight,
                p.shade,
                p.shadow,
                null,
                p.ok,
                p.px,
                false,
                false,
                false
        );
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect r = getBounds();
        int u = unit;
        int shOff = dropShadow ? u : 0;
        int l = r.left;
        int t = r.top;
        int ri = r.right - shOff;
        int b = r.bottom - shOff;

        if (ri - l <= u * 6 || b - t <= u * 6) {
            canvas.drawRect(r, fillPaint);
            return;
        }

        if (dropShadow) {
            canvas.drawRect(l + u * 3, b, ri - u * 2, b + u, shadowPaint);
            canvas.drawRect(ri, t + u * 3, ri + u, b - u * 2, shadowPaint);
            canvas.drawRect(ri - u, b - u * 2, ri, b - u, shadowPaint);
            canvas.drawRect(ri - u * 2, b - u, ri - u, b, shadowPaint);
        }

        canvas.drawRect(l + u, t + u, ri - u, b - u, fillPaint);

        if (showRail) {
            int railW = u * 3;
            canvas.drawRect(l + u, t + u, l + u + railW, b - u, railPaint);
            canvas.drawRect(l + u + railW, t + u, l + u + railW + u, b - u, railEdgePaint);
        }

        Paint topLeft = inset ? shPaint : hiPaint;
        Paint botRight = inset ? hiPaint : shPaint;
        int hiStart = showRail ? (l + u * 5) : (l + u * 2);
        canvas.drawRect(hiStart, t + u, ri - u * 2, t + u * 2, topLeft);
        if (!showRail) {
            canvas.drawRect(l + u, t + u * 2, l + u * 2, b - u * 2, topLeft);
        }
        canvas.drawRect(hiStart, b - u * 2, ri - u * 2, b - u, botRight);
        canvas.drawRect(ri - u * 2, t + u * 2, ri - u, b - u * 2, botRight);

        canvas.drawRect(l + u * 2, t, ri - u * 2, t + u, borderPaint);
        canvas.drawRect(l + u * 2, b - u, ri - u * 2, b, borderPaint);
        canvas.drawRect(l, t + u * 2, l + u, b - u * 2, borderPaint);
        canvas.drawRect(ri - u, t + u * 2, ri, b - u * 2, borderPaint);

        canvas.drawRect(l + u, t + u, l + u * 2, t + u * 2, borderPaint);
        canvas.drawRect(ri - u * 2, t + u, ri - u, t + u * 2, borderPaint);
        canvas.drawRect(l + u, b - u * 2, l + u * 2, b - u, borderPaint);
        canvas.drawRect(ri - u * 2, b - u * 2, ri - u, b - u, borderPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        fillPaint.setAlpha(alpha);
        borderPaint.setAlpha(alpha);
        hiPaint.setAlpha(alpha);
        shPaint.setAlpha(alpha);
        shadowPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        borderPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
