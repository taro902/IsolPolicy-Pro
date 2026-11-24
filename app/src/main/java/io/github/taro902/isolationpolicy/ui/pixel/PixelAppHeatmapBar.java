package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class PixelAppHeatmapBar extends View {

    private final PixelMetrics m;
    private final Paint paint = PixelDraw.newPixelPaint();
    private final long[] hits = new long[PolicyFlags.PILLAR_COUNT];
    private PixelPalette palette;
    private boolean auditTone = false;
    private long maxHit = 1L;
    private long totalHits = 0L;

    public PixelAppHeatmapBar(Context ctx, PixelMetrics metrics, PixelPalette pal) {
        super(ctx);
        this.m = metrics;
        this.palette = pal;
    }

    public void setPalette(PixelPalette pal) {
        this.palette = pal;
        invalidate();
    }

    public void bind(long[] perPillarHits, boolean isAuditMode) {
        this.auditTone = isAuditMode;
        long max = 1L;
        long sum = 0L;
        for (int i = 0; i < PolicyFlags.PILLAR_COUNT; i++) {
            long v = (perPillarHits != null && i < perPillarHits.length) ? perPillarHits[i] : 0L;
            hits[i] = v;
            sum += v;
            if (v > max) {
                max = v;
            }
        }
        this.maxHit = max;
        this.totalHits = sum;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(m.dp(320), widthMeasureSpec), resolveSize(m.dp(92), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int u = m.u;
        if (w < u * 30 || h < u * 16) {
            return;
        }

        PixelDraw.drawRecessedWell(canvas, 0, 0, w, h, u, palette, paint);

        int sc = m.microScale;
        int mh = PixelFont.microHeight(sc);
        int topPad = u * 3;
        int botPad = u * 3;
        int leftPad = u * 3;
        int rightPad = u * 3;
        int innerW = w - leftPad - rightPad;
        int step = innerW / PolicyFlags.PILLAR_COUNT;
        int barW = Math.max(u * 2, step - u * 2);

        int barTop = topPad + mh + u * 2;
        int barBottom = h - botPad - mh - u * 2;
        int barMaxH = Math.max(u * 3, barBottom - barTop);
        int activeColor = auditTone ? palette.audit : palette.accent;

        for (int p = 0; p < PolicyFlags.PILLAR_COUNT; p++) {
            int cx = leftPad + p * step + step / 2;
            long v = hits[p];
            int bh = v > 0 ? Math.max(u * 2, (int) ((v * barMaxH) / maxHit)) : u;

            paint.setColor(v > 0 ? activeColor : palette.border);
            canvas.drawRect(cx - barW / 2, barBottom - bh, cx + barW / 2, barBottom, paint);

            String cntTxt = v > 999 ? "999" : String.valueOf(v);
            int cw = PixelFont.measureMicroWidth(cntTxt, sc);
            PixelFont.drawMicro(canvas, cntTxt, cx - cw / 2, topPad, sc, v > 0 ? activeColor : palette.inkMuted, paint);

            String code = PolicyFlags.pillarCode(p);
            if (code.length() > 3) {
                code = code.substring(0, 3);
            }
            int lw = PixelFont.measureMicroWidth(code, sc);
            PixelFont.drawMicro(canvas, code, cx - lw / 2, barBottom + u * 2, sc, v > 0 ? palette.ink : palette.inkMuted, paint);
        }

        if (totalHits == 0L) {
            String idle = "NO HITS YET";
            int iw = PixelFont.measureMicroWidth(idle, sc);
            PixelFont.drawMicro(canvas, idle, (w - iw) / 2, barTop + (barMaxH - mh) / 2, sc, palette.inkMuted, paint);
        }
    }
}
