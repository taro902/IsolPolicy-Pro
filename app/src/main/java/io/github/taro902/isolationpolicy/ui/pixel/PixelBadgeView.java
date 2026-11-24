package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public final class PixelBadgeView extends View {

    public static final int TONE_MUTED = 0;
    public static final int TONE_ACCENT = 1;
    public static final int TONE_WARN = 2;
    public static final int TONE_DANGER = 3;
    public static final int TONE_AUDIT = 4;

    private final PixelMetrics m;
    private final Paint paint = PixelDraw.newPixelPaint();
    private PixelPalette palette;
    private String text = "";
    private int tone = TONE_MUTED;
    private boolean showLed = false;
    private boolean micro = false;

    public PixelBadgeView(Context ctx, PixelMetrics metrics, PixelPalette pal) {
        super(ctx);
        this.m = metrics;
        this.palette = pal;
    }

    public void setPalette(PixelPalette pal) {
        this.palette = pal;
        invalidate();
    }

    public void bind(String label, int newTone, boolean led) {
        this.text = label != null ? label : "";
        this.tone = newTone;
        this.showLed = led;
        requestLayout();
        invalidate();
    }

    public void setMicro(boolean isMicro) {
        this.micro = isMicro;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int u = m.u;
        int sc = micro ? m.microScale : m.smallFontScale;
        int tw = micro ? PixelFont.measureMicroWidth(text, sc) : PixelFont.measureWidth(text, sc);
        int th = micro ? PixelFont.microHeight(sc) : PixelFont.lineHeight(sc);
        int ledSpace = showLed ? u * 4 : 0;
        int w = tw + ledSpace + u * 6;
        int h = th + u * 4;
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int u = m.u;
        if (w < u * 4 || h < u * 4) {
            return;
        }
        int bg;
        int fg;
        int bdr;
        switch (tone) {
            case TONE_ACCENT:
                bg = palette.accentDim;
                fg = palette.accent;
                bdr = palette.accent;
                break;
            case TONE_WARN:
                bg = palette.surfaceAlt;
                fg = palette.warn;
                bdr = palette.warn;
                break;
            case TONE_DANGER:
                bg = palette.surfaceAlt;
                fg = palette.danger;
                bdr = palette.danger;
                break;
            case TONE_AUDIT:
                bg = palette.surfaceAlt;
                fg = palette.audit;
                bdr = palette.audit;
                break;
            default:
                bg = palette.well;
                fg = palette.inkMuted;
                bdr = palette.border;
                break;
        }
        PixelDraw.drawNotchedBox(canvas, 0, 0, w, h, u, bg, bdr, paint);
        int x = u * 3;
        if (showLed) {
            int ledSz = u * 3;
            PixelDraw.drawLed(canvas, x - u, (h - ledSz) / 2, ledSz, u, fg, bdr, paint);
            x += ledSz + u;
        }
        int sc = micro ? m.microScale : m.smallFontScale;
        int th = micro ? PixelFont.microHeight(sc) : PixelFont.lineHeight(sc);
        int y = (h - th) / 2;
        if (micro) {
            PixelFont.drawMicro(canvas, text, x, y, sc, fg, paint);
        } else {
            PixelFont.drawText(canvas, text, x, y, sc, fg, paint);
        }
    }
}
