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
        return PixelFont.lineHeight(m.smallFontScale) + m.u * 7;
    }

    private void updatePadding() {
        int u = m.u;
        int topExtra = headerHeight();
        setPadding(u * 4, u * 4 + topExtra, u * 6, u * 6);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int u = m.u;
        if (w < u * 8 || h < u * 8) {
            return;
        }
        PixelDraw.drawCard(canvas, 0, 0, w, h, u, palette, highlighted, paint);

        int hh = headerHeight();
        if (hh > 0) {
            int barLeft = u;
            int barTop = u;
            int barRight = w - u * 3;
            int barBottom = barTop + hh;
            PixelDraw.drawBayerFill(canvas, barLeft, barTop, barRight, barBottom, palette.surfaceAlt, palette.ditherDot, 4, u, paint);
            PixelDraw.drawDivider(canvas, barLeft, barBottom - u, barRight, u, palette.border, paint);

            int rvY = barTop + (hh - u - u * 2) / 2;
            PixelDraw.drawRivet(canvas, barLeft + u * 2, rvY, u, palette.borderHi, palette.shadow, paint);

            int rw = !headerRight.isEmpty() ? PixelFont.measureWidth(headerRight, m.microScale) : 0;
            int rightReserve = rw > 0 ? (rw + u * 5) : (u * 3);
            int titleLeft = barLeft + u * 6;
            int maxTitleW = Math.max(0, barRight - rightReserve - titleLeft);

            if (!headerTitle.isEmpty()) {
                int sc = PixelFont.measureWidth(headerTitle, m.smallFontScale) <= maxTitleW ? m.smallFontScale : m.microScale;
                int ty = barTop + (hh - u - PixelFont.lineHeight(sc)) / 2;
                String fitT = PixelFont.fitText(headerTitle, maxTitleW, sc);
                PixelFont.drawTextWithShadow(canvas, fitT, titleLeft, ty, sc, palette.accent, palette.shadow, paint);
            }
            if (!headerRight.isEmpty()) {
                int ry = barTop + (hh - u - PixelFont.lineHeight(m.microScale)) / 2;
                PixelFont.drawText(canvas, headerRight, barRight - u * 3 - rw, ry, m.microScale, palette.inkMuted, paint);
            }
        }
    }
}
