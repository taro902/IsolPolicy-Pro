package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.Arrays;
import java.util.List;

import io.github.taro902.isolationpolicy.hook.TelemetryHub;
import io.github.taro902.isolationpolicy.radar.TelemetryClient;

public final class PixelScopeView extends View {

    private static final int BUCKETS = 32;

    private final PixelMetrics m;
    private final Paint paint = PixelDraw.newPixelPaint();
    private final int[] enfBuckets = new int[BUCKETS];
    private final int[] audBuckets = new int[BUCKETS];
    private final int[] peakBuckets = new int[BUCKETS];
    private PixelPalette palette;
    private int maxBucket = 1;
    private long totalEnf = 0L;
    private long totalAud = 0L;
    private int ringCount = 0;

    public PixelScopeView(Context ctx, PixelMetrics metrics, PixelPalette pal) {
        super(ctx);
        this.m = metrics;
        this.palette = pal;
    }

    public void setPalette(PixelPalette pal) {
        this.palette = pal;
        invalidate();
    }

    public void bindEvents(List<TelemetryClient.EventRow> events, long enforcedTotal, long auditTotal) {
        Arrays.fill(enfBuckets, 0);
        Arrays.fill(audBuckets, 0);
        this.totalEnf = enforcedTotal;
        this.totalAud = auditTotal;
        this.ringCount = events != null ? events.size() : 0;

        if (events != null && !events.isEmpty()) {
            int n = events.size();
            long minTs = events.get(0).timestampMs;
            long maxTs = events.get(n - 1).timestampMs;
            long span = Math.max(1L, maxTs - minTs);
            for (int i = 0; i < n; i++) {
                TelemetryClient.EventRow ev = events.get(i);
                int b;
                if (maxTs == minTs) {
                    b = Math.min(BUCKETS - 1, (i * BUCKETS) / n);
                } else {
                    b = (int) (((ev.timestampMs - minTs) * (BUCKETS - 1)) / span);
                    if (b < 0) {
                        b = 0;
                    } else if (b >= BUCKETS) {
                        b = BUCKETS - 1;
                    }
                }
                if (ev.isAudit) {
                    audBuckets[b]++;
                } else {
                    enfBuckets[b]++;
                }
            }
        }

        int peak = 1;
        for (int i = 0; i < BUCKETS; i++) {
            int sum = enfBuckets[i] + audBuckets[i];
            if (sum > peak) {
                peak = sum;
            }
            if (sum >= peakBuckets[i]) {
                peakBuckets[i] = sum;
            } else if (peakBuckets[i] > 0) {
                peakBuckets[i]--;
            }
        }
        this.maxBucket = peak;
        invalidate();
    }

    public void resetPeaks() {
        Arrays.fill(peakBuckets, 0);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = m.dp(118);
        setMeasuredDimension(resolveSize(m.dp(340), widthMeasureSpec), resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int u = m.u;
        if (w < u * 24 || h < u * 16) {
            return;
        }

        PixelDraw.drawRecessedWell(canvas, 0, 0, w, h, u, palette, paint);

        int hudTop = u * 3;
        int sc = m.microScale;
        int mh = PixelFont.microHeight(sc);
        PixelFont.drawMicro(canvas, "ENF " + totalEnf, u * 3, hudTop, sc, palette.accent, paint);
        int audX = u * 3 + PixelFont.measureMicroWidth("ENF " + totalEnf, sc) + u * 4;
        PixelFont.drawMicro(canvas, "AUD " + totalAud, audX, hudTop, sc, palette.audit, paint);

        String rightHud = "RING " + ringCount + "/" + TelemetryHub.RING_SIZE;
        int rw = PixelFont.measureMicroWidth(rightHud, sc);
        PixelFont.drawMicro(canvas, rightHud, w - u * 3 - rw, hudTop, sc, palette.inkMuted, paint);

        int plotLeft = u * 3;
        int plotRight = w - u * 3;
        int plotTop = hudTop + mh + u * 3;
        int plotBottom = h - u * 3;
        int plotW = plotRight - plotLeft;
        int plotH = plotBottom - plotTop;
        if (plotW <= 0 || plotH <= u * 4) {
            return;
        }

        paint.setColor(palette.ditherDot);
        for (int row = 1; row <= 3; row++) {
            int gy = plotTop + (plotH * row) / 4;
            for (int gx = plotLeft; gx < plotRight; gx += u * 3) {
                canvas.drawRect(gx, gy, Math.min(plotRight, gx + u), gy + Math.max(1, u / 2), paint);
            }
        }

        int step = Math.max(u * 2, plotW / BUCKETS);
        int barW = Math.max(u, step - u);
        int startX = plotLeft + Math.max(0, (plotW - step * BUCKETS) / 2);

        for (int i = 0; i < BUCKETS; i++) {
            int bx = startX + i * step;
            if (bx + barW > plotRight) {
                break;
            }
            int enf = enfBuckets[i];
            int aud = audBuckets[i];
            int sum = enf + aud;
            int enfH = (enf * plotH) / maxBucket;
            int audH = (aud * plotH) / maxBucket;
            if (enf > 0 && enfH < u) {
                enfH = u;
            }
            if (aud > 0 && audH < u) {
                audH = u;
            }
            if (enfH + audH > plotH) {
                audH = Math.max(0, plotH - enfH);
            }

            if (enfH > 0) {
                paint.setColor(palette.accent);
                canvas.drawRect(bx, plotBottom - enfH, bx + barW, plotBottom, paint);
            }
            if (audH > 0) {
                paint.setColor(palette.audit);
                canvas.drawRect(bx, plotBottom - enfH - audH, bx + barW, plotBottom - enfH, paint);
            }

            int pk = peakBuckets[i];
            if (pk > 0 && pk >= sum) {
                int pkH = Math.min(plotH, Math.max(u, (pk * plotH) / maxBucket));
                int py = Math.max(plotTop, plotBottom - pkH - u);
                paint.setColor(palette.warn);
                canvas.drawRect(bx, py, bx + barW, py + u, paint);
            }
        }
    }
}
