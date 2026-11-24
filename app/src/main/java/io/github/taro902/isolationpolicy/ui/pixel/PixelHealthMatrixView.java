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
        int lh = PixelFont.lineHeight(m.microScale);
        int cellH = lh * 3 + u * 10;
        int footerH = lh + u * 7;
        int totalH = cellH * 2 + u * 5 + footerH;
        setMeasuredDimension(resolveSize(m.dp(340), widthMeasureSpec), resolveSize(totalH, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int u = m.u;
        if (w < u * 30 || h < u * 30) {
            return;
        }

        int sc = m.microScale;
        int lh = PixelFont.lineHeight(sc);
        int gap = u * 2;
        int footerH = lh + u * 7;
        int gridH = h - footerH - u * 3;
        int colW = (w - gap * 4) / 5;
        int rowH = (gridH - gap) / 2;
        int ledSz = u * 3;

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

            int bdr = st == TelemetryHub.HEALTH_TRIPPED ? palette.danger : (st == TelemetryHub.HEALTH_OK ? palette.borderHi : palette.border);
            PixelDraw.drawNotchedBox(canvas, left, top, right, bottom, u, palette.well, bdr, paint);

            int y1 = top + u * 3;
            int y2 = y1 + lh + u * 2;
            int y3 = y2 + lh + u * 2;

            PixelDraw.drawLed(canvas, left + u * 2, y1 + Math.max(0, (lh - ledSz) / 2), ledSz, u, ledColor, palette.border, paint);
            String code = PixelFont.fitText(PolicyFlags.pillarCode(p), colW - u * 6 - ledSz, sc);
            PixelFont.drawText(canvas, code, left + u * 4 + ledSz, y1, sc, palette.ink, paint);

            String midLine = PixelFont.fitText(stLabel + " T" + tiers[p], colW - u * 4, sc);
            PixelFont.drawText(canvas, midLine, left + u * 2, y2, sc, ledColor, paint);

            String fltLine = PixelFont.fitText("F" + faults[p], colW - u * 4, sc);
            PixelFont.drawText(canvas, fltLine, left + u * 2, y3, sc, faults[p] > 0 ? palette.danger : palette.inkMuted, paint);
        }

        int footTop = gridH + u * 3;
        PixelDraw.drawRecessedWell(canvas, 0, footTop, w, h, u, palette, paint);
        boolean ipcReady = connected && ((healthMask & TelemetryHub.HEALTH_BIT_IPC_READY) != 0);
        boolean setReady = connected && ((healthMask & TelemetryHub.HEALTH_BIT_SETTINGS_READY) != 0);

        int fy = footTop + (footerH - lh) / 2;
        int ledY = footTop + (footerH - ledSz) / 2;

        PixelDraw.drawLed(canvas, u * 3, ledY, ledSz, u, ipcReady ? palette.accent : palette.danger, palette.border, paint);
        PixelFont.drawText(canvas, ipcReady ? "IPC OK" : "IPC WAIT", u * 5 + ledSz, fy, sc, ipcReady ? palette.accent : palette.danger, paint);

        int setX = w * 38 / 100;
        PixelDraw.drawLed(canvas, setX, ledY, ledSz, u, setReady ? palette.accent : palette.warn, palette.border, paint);
        PixelFont.drawText(canvas, setReady ? "SET OK" : "SET WAIT", setX + u * 2 + ledSz, fy, sc, setReady ? palette.accent : palette.warn, paint);

        String rightMeta = "API " + apiVersion + "  GR " + graphCount;
        int rw = PixelFont.measureWidth(rightMeta, sc);
        PixelFont.drawText(canvas, rightMeta, w - u * 3 - rw, fy, sc, palette.inkMuted, paint);
    }
}
