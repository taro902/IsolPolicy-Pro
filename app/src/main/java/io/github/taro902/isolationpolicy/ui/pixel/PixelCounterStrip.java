package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class PixelCounterStrip extends View {

    public interface OnPillarFilterListener {
        void onPillarFilterChanged(int pillarOrAll);
    }

    private final PixelMetrics m;
    private final Paint paint = PixelDraw.newPixelPaint();
    private final long[] enforced = new long[PolicyFlags.PILLAR_COUNT];
    private final long[] audited = new long[PolicyFlags.PILLAR_COUNT];
    private PixelPalette palette;
    private OnPillarFilterListener listener;
    private int selectedPillar = -1;

    public PixelCounterStrip(Context ctx, PixelMetrics metrics, PixelPalette pal) {
        super(ctx);
        this.m = metrics;
        this.palette = pal;
        setClickable(true);
    }

    public void setPalette(PixelPalette pal) {
        this.palette = pal;
        invalidate();
    }

    public void setOnPillarFilterListener(OnPillarFilterListener l) {
        this.listener = l;
    }

    public void setSelectedPillar(int pillar) {
        this.selectedPillar = pillar;
        invalidate();
    }

    public int getSelectedPillar() {
        return selectedPillar;
    }

    public void bindTotals(long[] enf, long[] aud) {
        for (int i = 0; i < PolicyFlags.PILLAR_COUNT; i++) {
            enforced[i] = (enf != null && i < enf.length) ? enf[i] : 0L;
            audited[i] = (aud != null && i < aud.length) ? aud[i] : 0L;
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int u = m.u;
        int rowH = Math.max(m.dp(46), PixelFont.lineHeight(m.microScale) * 2 + u * 8);
        setMeasuredDimension(resolveSize(m.dp(340), widthMeasureSpec), resolveSize(rowH * 2 + u * 2, heightMeasureSpec));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            int w = getWidth();
            int h = getHeight();
            if (w > 0 && h > 0) {
                int col = Math.max(0, Math.min(4, (int) (event.getX() * 5f / w)));
                int row = event.getY() >= (h / 2f) ? 1 : 0;
                int pillar = row * 5 + col;
                int next = (selectedPillar == pillar) ? -1 : pillar;
                selectedPillar = next;
                if (PixelKeycapButton.isHapticsEnabled()) {
                    try {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    } catch (Throwable ignored) {
                    }
                }
                invalidate();
                if (listener != null) {
                    listener.onPillarFilterChanged(next);
                }
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int u = m.u;
        if (w < u * 30 || h < u * 20) {
            return;
        }

        int gap = u * 2;
        int colW = (w - gap * 4) / 5;
        int rowH = (h - gap) / 2;
        int iconScale = Math.max(2, u - 1);
        int iconSz = 9 * iconScale;
        int sc = m.microScale;
        int lh = PixelFont.lineHeight(sc);
        int ledSz = u * 3;

        for (int p = 0; p < PolicyFlags.PILLAR_COUNT; p++) {
            int row = p / 5;
            int col = p % 5;
            int left = col * (colW + gap);
            int top = row * (rowH + gap);
            int right = left + colW;
            int bottom = top + rowH;

            long e = enforced[p];
            long a = audited[p];
            long total = e + a;
            boolean sel = (p == selectedPillar);

            int bg = sel ? palette.accentDim : palette.surface;
            int bdr = sel ? palette.accent : (total > 0 ? palette.borderHi : palette.border);
            PixelDraw.drawNotchedBox(canvas, left, top, right, bottom, u, bg, bdr, paint);

            int ledColor = e > 0 ? palette.accent : (a > 0 ? palette.audit : palette.border);
            int topLineY = top + u * 3;
            PixelSpriteAtlas.drawSprite(
                canvas,
                PixelSpriteAtlas.pillarIcon(p),
                left + u * 2,
                topLineY,
                iconScale,
                sel ? palette.ink : palette.inkMuted,
                ledColor,
                paint
            );

            String code = PixelFont.fitText(PolicyFlags.pillarCode(p), colW - u * 5 - iconSz, sc);
            PixelFont.drawText(canvas, code, left + u * 3 + iconSz, topLineY + Math.max(0, (iconSz - lh) / 2), sc, sel ? palette.ink : palette.inkMuted, paint);

            String numStr = formatCompactCount(total);
            int numColor = e > 0 ? palette.accent : (a > 0 ? palette.audit : palette.inkMuted);
            int numY = bottom - u * 3 - lh;
            PixelFont.drawText(canvas, numStr, left + u * 2, numY, sc, numColor, paint);

            PixelDraw.drawLed(canvas, right - u * 2 - ledSz, numY + Math.max(0, (lh - ledSz) / 2), ledSz, u, ledColor, palette.border, paint);
        }
    }

    private static String formatCompactCount(long v) {
        if (v >= 10000L) {
            return (v / 1000L) + "K";
        }
        return String.valueOf(v);
    }
}
