package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public final class PixelTextView extends View {

    public static final int ROLE_INK = 0;
    public static final int ROLE_MUTED = 1;
    public static final int ROLE_ACCENT = 2;
    public static final int ROLE_WARN = 3;
    public static final int ROLE_DANGER = 4;
    public static final int ROLE_AUDIT = 5;

    private final PixelMetrics m;
    private final Paint paint = PixelDraw.newPixelPaint();
    private PixelPalette palette;
    private String text = "";
    private String fitted = "";
    private int scale;
    private boolean microMode = false;
    private int colorRole = ROLE_INK;
    private int customColor = 0;
    private boolean alignCenter = false;
    private int lastFitWidth = -1;

    public PixelTextView(Context ctx, PixelMetrics metrics, PixelPalette pal) {
        super(ctx);
        this.m = metrics;
        this.palette = pal;
        this.scale = metrics.fontScale;
    }

    public void setPalette(PixelPalette pal) {
        this.palette = pal;
        invalidate();
    }

    public void setText(String t) {
        String safe = t != null ? t : "";
        if (!safe.equals(this.text)) {
            this.text = safe;
            this.lastFitWidth = -1;
            requestLayout();
            invalidate();
        }
    }

    public String getText() {
        return text;
    }

    public void setScale(int s) {
        this.scale = Math.max(1, s);
        this.lastFitWidth = -1;
        requestLayout();
        invalidate();
    }

    public void setMicroMode(boolean micro) {
        this.microMode = micro;
        this.lastFitWidth = -1;
        requestLayout();
        invalidate();
    }

    public void setColorRole(int role) {
        this.colorRole = role;
        this.customColor = 0;
        invalidate();
    }

    public void setCustomColor(int color) {
        this.customColor = color;
        invalidate();
    }

    public void setAlignCenter(boolean center) {
        this.alignCenter = center;
        invalidate();
    }

    private int resolveColor() {
        if (customColor != 0) {
            return customColor;
        }
        switch (colorRole) {
            case ROLE_MUTED:
                return palette.inkMuted;
            case ROLE_ACCENT:
                return palette.accent;
            case ROLE_WARN:
                return palette.warn;
            case ROLE_DANGER:
                return palette.danger;
            case ROLE_AUDIT:
                return palette.audit;
            default:
                return palette.ink;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int contentW = microMode ? PixelFont.measureMicroWidth(text, scale) : PixelFont.measureWidth(text, scale);
        int contentH = microMode ? PixelFont.microHeight(scale) : PixelFont.lineHeight(scale);
        int desiredW = contentW + getPaddingLeft() + getPaddingRight();
        int desiredH = contentH + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(desiredW, widthMeasureSpec), resolveSize(desiredH, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (text.isEmpty()) {
            return;
        }
        int availW = getWidth() - getPaddingLeft() - getPaddingRight();
        if (availW <= 0) {
            return;
        }
        if (availW != lastFitWidth) {
            fitted = microMode ? PixelFont.fitMicro(text, availW, scale) : PixelFont.fitText(text, availW, scale);
            lastFitWidth = availW;
        }
        int th = microMode ? PixelFont.microHeight(scale) : PixelFont.lineHeight(scale);
        int y = getPaddingTop() + Math.max(0, (getHeight() - getPaddingTop() - getPaddingBottom() - th) / 2);
        int x = getPaddingLeft();
        if (alignCenter) {
            int tw = microMode ? PixelFont.measureMicroWidth(fitted, scale) : PixelFont.measureWidth(fitted, scale);
            x = getPaddingLeft() + Math.max(0, (availW - tw) / 2);
        }
        int color = resolveColor();
        if (microMode) {
            PixelFont.drawMicro(canvas, fitted, x, y, scale, color, paint);
        } else {
            PixelFont.drawText(canvas, fitted, x, y, scale, color, paint);
        }
    }
}
