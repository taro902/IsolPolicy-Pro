package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public final class PixelThreatBar extends View {

    private static final int SEGMENTS = 5;

    private final PixelPalette pal;
    private final Paint framePaint = new Paint();
    private final Paint offPaint = new Paint();
    private final Paint onPaint = new Paint();
    private final Paint badgeBgPaint = new Paint();
    private final Paint badgeInkPaint = new Paint();

    private int litSegments;
    private int liveHits;

    public PixelThreatBar(Context context, PixelPalette pal) {
        super(context);
        this.pal = pal;

        framePaint.setAntiAlias(false);
        framePaint.setColor(pal.outline);

        offPaint.setAntiAlias(false);
        offPaint.setColor(pal.bgInset);

        onPaint.setAntiAlias(false);
        onPaint.setColor(pal.info);

        badgeBgPaint.setAntiAlias(false);
        badgeBgPaint.setColor(pal.okBg);

        badgeInkPaint.setAntiAlias(false);
        badgeInkPaint.setColor(pal.ok);
    }

    public void setMetrics(int score, boolean isSandbox, int hits) {
        int next;
        if (isSandbox) {
            next = score > 0 ? 1 : 0;
        } else if (score <= 0) {
            next = 0;
        } else if (score <= 20) {
            next = 1;
        } else if (score <= 40) {
            next = 2;
        } else if (score <= 60) {
            next = 3;
        } else if (score <= 80) {
            next = 4;
        } else {
            next = 5;
        }

        boolean layoutChanged = (hits > 0) != (this.liveHits > 0) || (hits != this.liveHits && hits > 0);
        this.litSegments = next;
        this.liveHits = Math.max(0, hits);

        if (isSandbox) {
            onPaint.setColor(pal.inkMuted);
        } else if (next >= 4) {
            onPaint.setColor(pal.danger);
        } else if (next == 3) {
            onPaint.setColor(pal.warn);
        } else {
            onPaint.setColor(pal.info);
        }

        if (layoutChanged) {
            requestLayout();
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int u = pal.px;
        int fs = pal.smallFontScale;
        int barW = u * 21;
        int badgeW = 0;
        if (liveHits > 0) {
            String txt = liveHits + " HIT";
            badgeW = PixelFont.measureWidth(txt, fs) + u * 5;
        }
        int desiredW = barW + (badgeW > 0 ? (badgeW + u * 2) : 0);
        int desiredH = Math.max(u * 5, PixelFont.lineHeight(fs) + u * 2);
        setMeasuredDimension(
                resolveSize(desiredW, widthMeasureSpec),
                resolveSize(desiredH, heightMeasureSpec)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int u = pal.px;
        int b = Math.max(2, u / 2);
        int x = 0;

        if (liveHits > 0) {
            int fs = pal.smallFontScale;
            String txt = liveHits + " HIT";
            int tw = PixelFont.measureWidth(txt, fs);
            int bw = tw + u * 4;
            canvas.drawRect(x + b, 0, x + bw - b, b, badgeInkPaint);
            canvas.drawRect(x + b, h - b, x + bw - b, h, badgeInkPaint);
            canvas.drawRect(x, b, x + b, h - b, badgeInkPaint);
            canvas.drawRect(x + bw - b, b, x + bw, h - b, badgeInkPaint);
            canvas.drawRect(x + b, b, x + bw - b, h - b, badgeBgPaint);
            PixelFont.drawCentered(canvas, txt, x + bw * 0.5f, h * 0.5f, fs, badgeInkPaint);
            x += bw + u * 2;
        }

        int barW = w - x;
        int barH = u * 4;
        int top = (h - barH) / 2;
        int gap = Math.max(2, u);
        int segW = (barW - gap * (SEGMENTS - 1)) / SEGMENTS;
        if (segW < 3) {
            return;
        }
        for (int i = 0; i < SEGMENTS; i++) {
            int sx = x + i * (segW + gap);
            canvas.drawRect(sx, top, sx + segW, top + barH, framePaint);
            Paint fill = i < litSegments ? onPaint : offPaint;
            canvas.drawRect(sx + 2, top + 2, sx + segW - 2, top + barH - 2, fill);
        }
    }
}
