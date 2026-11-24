package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import io.github.taro902.isolationpolicy.core.PolicyFlags;
import io.github.taro902.isolationpolicy.hook.TelemetryHub;

public final class PixelHealthMatrixView extends View {

    private final PixelMetrics m;
    private final Paint paint = PixelDraw.newPixelPaint();
    private final int[] tiers = new int[PolicyFlags.PILLAR_COUNT];
    private final int[] faults = new int[PolicyFlags.PILLAR_COUNT];
    private PixelPalette palette;
    private int healthMask = 0;
    private int apiVersion = 0;
    private int graphCount = 0;
    private boolean connected = false;

    public PixelHealthMatrixView(Context ctx, PixelMetrics metrics, PixelPalette pal) {
        super(ctx);
        this.m = metrics;
        this.palette = pal;
    }

    public void setPalette(PixelPalette pal) {
        this.palette = pal;
        invalidate();
    }

    public void bind(boolean isConnected, int mask, int[] pillarTiers, int[] pillarFaults, int api, int graph) {
        this.connected = isConnected;
        this.healthMask = mask;
        this.apiVersion = api;
        this.graphCount = graph;
        for (int i = 0; i < PolicyFlags.PILLAR_COUNT; i++) {
            tiers[i] = (pillarTiers != null && i < pillarTiers.length) ? pillarTiers[i] : 0;
            faults[i] = (pillarFaults != null && i < pillarFaults.length) ? pillarFaults[i] : 0;
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int u = m.u;
        int rowH = u * 15;
        int footerH = u * 10;
        setMeasuredDimension(resolveSize(m.dp(340), widthMeasureSpec), resolveSize(rowH * 2 + footerH + u * 6, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int u = m.u;
        if (w < u * 25 || h < u * 24) {
            return;
        }

        int gap = u * 2;
        int footerH = u * 9;
        int gridH = h - footerH - gap;
        int colW = (w - gap * 4) / 5;
        int rowH = (gridH - gap) / 2;
        int sc = m.microScale;
        int mh = PixelFont.microHeight(sc);

        for (int p = 0; p < PolicyFlags.PILLAR_COUNT; p++) {
            int row = p / 5;
            int col = p % 5;
            int left = col * (colW + gap);
            int top = row * (rowH + gap);
            int right = left + colW;
            int bottom = top + rowH;

            int st = connected ? TelemetryHub.decodePillarHealth(healthMask, p) : TelemetryHub.HEALTH_OFF;
            int ledColor;
            String stLabel;
            switch (st) {
                case TelemetryHub.HEALTH_OK:
                    ledColor = palette.accent;
                    stLabel = "OK";
                    break;
                case TelemetryHub.HEALTH_FALLBACK:
                    ledColor = palette.warn;
                    stLabel = "FB";
                    break;
                case TelemetryHub.HEALTH_TRIPPED:
                    ledColor = palette.danger;
                    stLabel = "TRIP";
                    break;
                default:
                    ledColor = palette.inkMuted;
                    stLabel = "OFF";
                    break;
            }

            PixelDraw.drawNotchedBox(canvas, left, top, right, bottom, u, palette.well, st == TelemetryHub.HEALTH_TRIPPED ? palette.danger : palette.border, paint);

            PixelDraw.drawLed(canvas, left + u * 2, top + u * 2, u * 3, Math.max(1, u / 2), ledColor, palette.border, paint);
            PixelFont.drawMicro(canvas, PolicyFlags.pillarCode(p), left + u * 6, top + u * 2, sc, palette.ink, paint);

            String midLine = stLabel + " T" + tiers[p];
            PixelFont.drawMicro(canvas, PixelFont.fitMicro(midLine, colW - u * 4, sc), left + u * 2, top + u * 3 + mh + u, sc, ledColor, paint);

            String fltLine = "F" + faults[p];
            int fltColor = faults[p] > 0 ? palette.danger : palette.inkMuted;
            PixelFont.drawMicro(canvas, fltLine, left + u * 2, bottom - u * 2 - mh, sc, fltColor, paint);
        }

        int footTop = gridH + gap;
        PixelDraw.drawRecessedWell(canvas, 0, footTop, w, h, u, palette, paint);
        boolean ipcReady = connected && ((healthMask & TelemetryHub.HEALTH_BIT_IPC_READY) != 0);
        boolean setReady = connected && ((healthMask & TelemetryHub.HEALTH_BIT_SETTINGS_READY) != 0);

        int fy = footTop + (footerH - mh) / 2;
        PixelDraw.drawLed(canvas, u * 3, footTop + (footerH - u * 3) / 2, u * 3, Math.max(1, u / 2), ipcReady ? palette.accent : palette.danger, palette.border, paint);
        PixelFont.drawMicro(canvas, ipcReady ? "IPC OK" : "IPC WAIT", u * 7, fy, sc, ipcReady ? palette.accent : palette.danger, paint);

        int setX = w / 3;
        PixelDraw.drawLed(canvas, setX, footTop + (footerH - u * 3) / 2, u * 3, Math.max(1, u / 2), setReady ? palette.accent : palette.warn, palette.border, paint);
        PixelFont.drawMicro(canvas, setReady ? "SET OK" : "SET WAIT", setX + u * 4, fy, sc, setReady ? palette.accent : palette.warn, paint);

        String rightMeta = "API " + apiVersion + "  GR " + graphCount;
        int rw = PixelFont.measureMicroWidth(rightMeta, sc);
        PixelFont.drawMicro(canvas, rightMeta, w - u * 3 - rw, fy, sc, palette.inkMuted, paint);
    }
}
