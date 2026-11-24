package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public final class PixelTagBarView extends View {

    private static final int MAX_TAGS = 8;

    private final PixelMetrics m;
    private final Paint paint = PixelDraw.newPixelPaint();
    private final String[] labels = new String[MAX_TAGS];
    private final int[] tones = new int[MAX_TAGS];
    private PixelPalette palette;
    private int count = 0;

    public PixelTagBarView(Context ctx, PixelMetrics metrics, PixelPalette pal) {
        super(ctx);
        this.m = metrics;
        this.palette = pal;
    }

    public void setPalette(PixelPalette pal) {
        this.palette = pal;
        invalidate();
    }

    public void clearTags() {
        count = 0;
        invalidate();
    }

    public void addTag(String label, int tone) {
        if (label == null || label.isEmpty() || count >= MAX_TAGS) {
            return;
        }
        labels[count] = label;
        tones[count] = tone;
        count++;
        invalidate();
    }

    public int getTagCount() {
        return count;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int u = m.u;
        int h = PixelFont.microHeight(m.microScale) + u * 4;
        setMeasuredDimension(resolveSize(m.dp(240), widthMeasureSpec), resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (count == 0) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        int u = m.u;
        int sc = m.microScale;
        int th = PixelFont.microHeight(sc);
        int x = 0;

        for (int i = 0; i < count; i++) {
            String lbl = labels[i];
            int tw = PixelFont.measureMicroWidth(lbl, sc);
            int chipW = tw + u * 4;
            if (x + chipW > w) {
                break;
            }
            int tone = tones[i];
            int bg;
            int fg;
            int bdr;
            switch (tone) {
                case PixelBadgeView.TONE_ACCENT:
                    bg = palette.accentDim;
                    fg = palette.accent;
                    bdr = palette.accent;
                    break;
                case PixelBadgeView.TONE_WARN:
                    bg = palette.surfaceAlt;
                    fg = palette.warn;
                    bdr = palette.warn;
                    break;
                case PixelBadgeView.TONE_DANGER:
                    bg = palette.surfaceAlt;
                    fg = palette.danger;
                    bdr = palette.danger;
                    break;
                case PixelBadgeView.TONE_AUDIT:
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
            PixelDraw.drawNotchedBox(canvas, x, 0, x + chipW, h, u, bg, bdr, paint);
            PixelFont.drawMicro(canvas, lbl, x + u * 2, (h - th) / 2, sc, fg, paint);
            x += chipW + u * 2;
        }
    }
}
