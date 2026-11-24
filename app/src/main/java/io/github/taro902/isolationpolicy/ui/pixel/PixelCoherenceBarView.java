package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public final class PixelCoherenceBarView extends View {

    private static final String[] PROBES = {"DEV", "ADB", "W-ADB", "USB", "A11Y"};

    private final PixelMetrics m;
    private final Paint paint = PixelDraw.newPixelPaint();
    private PixelPalette palette;
    private boolean connected = false;
    private int hostDev = -1;
    private int hostAdb = -1;
    private int hostAdbWifi = -1;
    private int hostA11y = -1;
    private int selfAppId = -1;
    private int graphCount = 0;

    public PixelCoherenceBarView(Context ctx, PixelMetrics metrics, PixelPalette pal) {
        super(ctx);
        this.m = metrics;
        this.palette = pal;
    }

    public void setPalette(PixelPalette pal) {
        this.palette = pal;
        invalidate();
    }

    public void bind(boolean isConnected, int dev, int adb, int adbWifi, int a11y, int selfId, int graph) {
        this.connected = isConnected;
        this.hostDev = dev;
        this.hostAdb = adb;
        this.hostAdbWifi = adbWifi;
        this.hostA11y = a11y;
        this.selfAppId = selfId;
        this.graphCount = graph;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int u = m.u;
        int lh = PixelFont.lineHeight(m.microScale);
        int cellH = lh * 3 + u * 10;
        int footH = lh + u * 5;
        setMeasuredDimension(resolveSize(m.dp(340), widthMeasureSpec), resolveSize(cellH + footH + u * 2, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int u = m.u;
        if (w < u * 30 || h < u * 24) {
            return;
        }

        int sc = m.microScale;
        int lh = PixelFont.lineHeight(sc);
        int gap = u * 2;
        int footH = lh + u * 5;
        int gridH = h - footH - gap;
        int colW = (w - gap * 4) / 5;

        int[] hostStates = {
            hostDev,
            hostAdb,
            hostAdbWifi,
            hostAdb,
            hostA11y
        };

        int y1 = u * 3;
        int y2 = y1 + lh + u * 2;
        int y3 = y2 + lh + u * 2;

        for (int i = 0; i < 5; i++) {
            int left = i * (colW + gap);
            int right = left + colW;
            PixelDraw.drawNotchedBox(canvas, left, 0, right, gridH, u, palette.well, palette.border, paint);

            int cx = left + colW / 2;
            String lbl = PixelFont.fitText(PROBES[i], colW - u * 4, sc);
            PixelFont.drawText(canvas, lbl, cx - PixelFont.measureWidth(lbl, sc) / 2, y1, sc, palette.ink, paint);

            int hv = hostStates[i];
            String hTxt = !connected || hv < 0 ? "H:NA" : (hv > 0 ? "H:ON" : "H:0");
            int hCol = (!connected || hv < 0) ? palette.inkMuted : (hv > 0 ? palette.warn : palette.accent);
            hTxt = PixelFont.fitText(hTxt, colW - u * 4, sc);
            PixelFont.drawText(canvas, hTxt, cx - PixelFont.measureWidth(hTxt, sc) / 2, y2, sc, hCol, paint);

            String gTxt = connected ? "G:0" : "WAIT";
            int gCol = connected ? palette.accent : palette.danger;
            gTxt = PixelFont.fitText(gTxt, colW - u * 4, sc);
            PixelFont.drawText(canvas, gTxt, cx - PixelFont.measureWidth(gTxt, sc) / 2, y3, sc, gCol, paint);
        }

        int fy = gridH + gap + u * 2;
        String leftFoot = "SELF APPID " + (selfAppId >= 10000 ? selfAppId : 0);
        String rightFoot = "ID GRAPH " + graphCount;
        PixelFont.drawText(canvas, leftFoot, u, fy, sc, palette.inkMuted, paint);
        int rw = PixelFont.measureWidth(rightFoot, sc);
        PixelFont.drawText(canvas, rightFoot, w - u - rw, fy, sc, palette.accent, paint);
    }
}
