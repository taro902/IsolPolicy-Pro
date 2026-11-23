package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.List;

import io.github.taro902.isolationpolicy.radar.TelemetryClient;

public final class PixelScopeView extends View {

    private static final int COLS = 16;
    private static final int SEGS = 8;

    private final PixelPalette pal;
    private final int[] levels = new int[COLS];
    private final int[] peaks = new int[COLS];
    private final Paint bgPaint = new Paint();
    private final Paint borderPaint = new Paint();
    private final Paint gridPaint = new Paint();
    private final Paint segPaint = new Paint();
    private final Paint peakPaint = new Paint();
    private boolean seeded;

    public PixelScopeView(Context context, PixelPalette pal) {
        super(context);
        this.pal = pal;

        bgPaint.setAntiAlias(false);
        bgPaint.setColor(pal.bgInset);

        borderPaint.setAntiAlias(false);
        borderPaint.setColor(pal.outline);

        gridPaint.setAntiAlias(false);
        gridPaint.setColor(pal.divider);

        segPaint.setAntiAlias(false);
        segPaint.setStyle(Paint.Style.FILL);

        peakPaint.setAntiAlias(false);
        peakPaint.setStyle(Paint.Style.FILL);
        peakPaint.setColor(pal.inkPrimary);
    }

    public void syncFromSnapshot(List<TelemetryClient.EventRecord> events, long deltaEvents) {
        if ((!seeded || deltaEvents >= 6L) && events != null && !events.isEmpty()) {
            seedFromEvents(events);
            seeded = true;
            invalidate();
            return;
        }
        pushDelta(deltaEvents);
    }

    private void seedFromEvents(List<TelemetryClient.EventRecord> events) {
        int n = events.size();
        int[] buckets = new int[COLS];
        long newest = events.get(0).timestampMs;
        long oldest = events.get(n - 1).timestampMs;
        long span = Math.max(1000L, newest - oldest);

        for (int i = 0; i < n; i++) {
            long ts = events.get(i).timestampMs;
            int idx = (int) (((ts - oldest) * (COLS - 1)) / span);
            if (idx < 0) idx = 0;
            if (idx >= COLS) idx = COLS - 1;
            buckets[idx]++;
        }

        for (int c = 0; c < COLS; c++) {
            int lv = scaleDelta(buckets[c]);
            levels[c] = lv;
            peaks[c] = Math.max(lv, peaks[c]);
        }
    }

    public void pushDelta(long deltaEvents) {
        System.arraycopy(levels, 1, levels, 0, COLS - 1);
        int lv = scaleDelta(deltaEvents);
        levels[COLS - 1] = lv;

        for (int i = 0; i < COLS; i++) {
            if (levels[i] >= peaks[i]) {
                peaks[i] = levels[i];
            } else if (peaks[i] > 0) {
                peaks[i]--;
            }
        }
        invalidate();
    }

    public void clear() {
        seeded = false;
        for (int i = 0; i < COLS; i++) {
            levels[i] = 0;
            peaks[i] = 0;
        }
        invalidate();
    }

    private static int scaleDelta(long d) {
        if (d <= 0L) return 0;
        if (d == 1L) return 2;
        if (d <= 2L) return 3;
        if (d <= 4L) return 4;
        if (d <= 8L) return 5;
        if (d <= 14L) return 6;
        if (d <= 24L) return 7;
        return SEGS;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredW = pal.dp(120);
        int desiredH = pal.dp(44);
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
        if (w <= u * 6 || h <= u * 6) {
            return;
        }

        canvas.drawRect(u, u, w - u, h - u, bgPaint);
        canvas.drawRect(u * 2, 0, w - u * 2, u, borderPaint);
        canvas.drawRect(u * 2, h - u, w - u * 2, h, borderPaint);
        canvas.drawRect(0, u * 2, u, h - u * 2, borderPaint);
        canvas.drawRect(w - u, u * 2, w, h - u * 2, borderPaint);
        canvas.drawRect(u, u, u * 2, u * 2, borderPaint);
        canvas.drawRect(w - u * 2, u, w - u, u * 2, borderPaint);
        canvas.drawRect(u, h - u * 2, u * 2, h - u, borderPaint);
        canvas.drawRect(w - u * 2, h - u * 2, w - u, h - u, borderPaint);

        int pad = u * 2;
        int innerW = w - pad * 2;
        int innerH = h - pad * 2;
        if (innerW <= COLS || innerH <= SEGS) {
            return;
        }

        int colStep = innerW / COLS;
        int barW = Math.max(2, colStep - Math.max(2, u / 2));
        int segStep = innerH / SEGS;
        int segH = Math.max(2, segStep - 1);
        int startX = pad + (innerW - colStep * COLS) / 2;

        for (int c = 0; c < COLS; c++) {
            int lv = levels[c];
            int pk = peaks[c];
            int x = startX + c * colStep;
            for (int s = 0; s < SEGS; s++) {
                int y = (h - pad) - (s + 1) * segStep;
                if (s < lv) {
                    segPaint.setColor(colorForSegment(s));
                    canvas.drawRect(x, y, x + barW, y + segH, segPaint);
                } else if (pk > 0 && s == pk - 1) {
                    canvas.drawRect(x, y, x + barW, y + Math.max(2, segH / 2), peakPaint);
                } else {
                    canvas.drawRect(x, y, x + barW, y + segH, gridPaint);
                }
            }
        }
    }

    private int colorForSegment(int segIdx) {
        if (segIdx >= 6) return pal.danger;
        if (segIdx >= 4) return pal.warn;
        if (segIdx >= 2) return pal.ok;
        return pal.info;
    }
}
