package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.LinearLayout;

public final class PixelPanelView extends LinearLayout {

    private final PixelMetrics m;
    private final Paint paint = PixelDraw.newPixelPaint();
    private PixelPalette palette;
    private String headerTitle = "";
    private String headerRight = "";
    private boolean highlighted = false;

    public PixelPanelView(Context ctx, PixelMetrics metrics, PixelPalette pal) {
        super(ctx);
        this.m = metrics;
        this.palette = pal;
        setOrientation(VERTICAL);
        setWillNotDraw(false);
        updatePadding();
    }

    public void setPalette(PixelPalette pal) {
        this.palette = pal;
        invalidate();
    }

    public void setHighlighted(boolean hi) {
        if (this.highlighted != hi) {
            this.highlighted = hi;
            invalidate();
        }
    }

    public void setHeader(String title, String rightText) {
        this.headerTitle = title != null ? title : "";
        this.headerRight = rightText != null ? rightText : "";
        updatePadding();
        invalidate();
    }

    private int headerHeight() {
        if (headerTitle.isEmpty() && headerRight.isEmpty()) {
            return 0;
        }
        return PixelFont.lineHeight(m.smallFontScale) + m.u * 5;
    }

    private void updatePadding() {
        int u = m.u;
        int topExtra = headerHeight();
        setPadding(u * 4, u * 3 + topExtra, u * 5, u * 4);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int u = m.u;
        if (w < u * 6 || h < u * 6) {
            return;
        }
        PixelDraw.drawCard(canvas, 0, 0, w, h, u, palette, highlighted, paint);

        int hh = headerHeight();
        if (hh > 0) {
            int barLeft = u;
            int barTop = u;
            int barRight = w - u * 2;
            int barBottom = barTop + hh;
            PixelDraw.drawBayerFill(canvas, barLeft, barTop, barRight, barBottom, palette.surfaceAlt, palette.ditherDot, 4, u, paint);
            PixelDraw.drawDivider(canvas, barLeft, barBottom - u, barRight, u, palette.border, paint);

            int sc = m.smallFontScale;
            int th = PixelFont.lineHeight(sc);
            int ty = barTop + (hh - u - th) / 2;
            if (!headerTitle.isEmpty()) {
                PixelFont.drawText(canvas, headerTitle, barLeft + u * 3, ty, sc, palette.accent, paint);
            }
            if (!headerRight.isEmpty()) {
                int rw = PixelFont.measureMicroWidth(headerRight, m.microScale);
                int ry = barTop + (hh - u - PixelFont.microHeight(m.microScale)) / 2;
                PixelFont.drawMicro(canvas, headerRight, barRight - u * 3 - rw, ry, m.microScale, palette.inkMuted, paint);
            }
        }
    }
}
