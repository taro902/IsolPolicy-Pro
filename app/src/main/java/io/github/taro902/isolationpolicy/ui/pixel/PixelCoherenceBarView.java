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
        int h = u * 26;
        setMeasuredDimension(resolveSize(m.dp(340), widthMeasureSpec), resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int u = m.u;
        if (w < u * 25 || h < u * 20) {
            return;
        }

        int sc = m.microScale;
        int mh = PixelFont.microHeight(sc);
        int gap = u * 2;
        int footH = mh + u * 4;
        int gridH = h - footH - gap;
        int colW = (w - gap * 4) / 5;

        int[] hostStates = {
            hostDev,
            hostAdb,
            hostAdbWifi,
            hostAdb,
            hostA11y
        };

        for (int i = 0; i < 5; i++) {
            int left = i * (colW + gap);
            int right = left + colW;
            PixelDraw.drawNotchedBox(canvas, left, 0, right, gridH, u, palette.well, palette.border, paint);

            int cx = left + colW / 2;
            String lbl = PROBES[i];
            int lw = PixelFont.measureMicroWidth(lbl, sc);
            PixelFont.drawMicro(canvas, lbl, cx - lw / 2, u * 2, sc, palette.ink, paint);

            int hv = hostStates[i];
            String hTxt = !connected || hv < 0 ? "NA" : (hv > 0 ? "H:ON" : "H:0");
            int hCol = (!connected || hv < 0) ? palette.inkMuted : (hv > 0 ? palette.warn : palette.accent);
            int hw = PixelFont.measureMicroWidth(hTxt, sc);
            PixelFont.drawMicro(canvas, hTxt, cx - hw / 2, u * 4 + mh, sc, hCol, paint);

            String gTxt = connected ? "G:0" : "WAIT";
            int gCol = connected ? palette.accent : palette.danger;
            int gw = PixelFont.measureMicroWidth(gTxt, sc);
            PixelFont.drawMicro(canvas, gTxt, cx - gw / 2, gridH - u * 2 - mh, sc, gCol, paint);
        }

        int fy = gridH + gap + u;
        String leftFoot = "SELF APPID " + (selfAppId >= 10000 ? selfAppId : 0);
        String rightFoot = "ID GRAPH " + graphCount;
        PixelFont.drawMicro(canvas, leftFoot, u, fy, sc, palette.inkMuted, paint);
        int rw = PixelFont.measureMicroWidth(rightFoot, sc);
        PixelFont.drawMicro(canvas, rightFoot, w - u - rw, fy, sc, palette.accent, paint);
    }
}
