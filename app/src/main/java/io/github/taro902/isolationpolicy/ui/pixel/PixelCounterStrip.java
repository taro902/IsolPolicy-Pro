package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

public final class PixelCounterStrip extends View {

    private static final String[] NAMES = {"AZ", "ISO", "SET", "USB", "A11Y", "PKG", "DISP"};

    private final PixelPalette pal;
    private final long[] counts = new long[7];
    private final int[] accents = new int[7];
    private final int[] accentBgs = new int[7];

    private final Paint borderPaint = new Paint();
    private final Paint bgPaint = new Paint();
    private final Paint ledPaint = new Paint();
    private final Paint inkPaint = new Paint();

    private int pressedIdx = -1;
    private OnPillarTapListener listener;

    public PixelCounterStrip(Context context, PixelPalette pal) {
        super(context);
        this.pal = pal;
        setClickable(true);

        accents[0] = pal.ok;
        accentBgs[0] = pal.okBg;
        accents[1] = pal.info;
        accentBgs[1] = pal.infoBg;
        accents[2] = pal.ok;
        accentBgs[2] = pal.okBg;
        accents[3] = pal.warn;
        accentBgs[3] = pal.warnBg;
        accents[4] = pal.focus;
        accentBgs[4] = pal.focusBg;
        accents[5] = pal.warn;
        accentBgs[5] = pal.warnBg;
        accents[6] = pal.info;
        accentBgs[6] = pal.infoBg;

        borderPaint.setAntiAlias(false);
        bgPaint.setAntiAlias(false);
        ledPaint.setAntiAlias(false);
        inkPaint.setAntiAlias(false);
    }

    public void setOnPillarTapListener(OnPillarTapListener listener) {
        this.listener = listener;
    }

    public void setCounts(long[] rawCounts) {
        if (rawCounts == null || rawCounts.length < 8) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < 7; i++) {
            if (counts[i] != rawCounts[i + 1]) {
                counts[i] = rawCounts[i + 1];
                changed = true;
            }
        }
        if (changed) {
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int u = pal.px;
        int fs = pal.smallFontScale;
        int desiredH = PixelFont.lineHeight(fs) * 2 + u * 8;
        setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                resolveSize(desiredH, heightMeasureSpec)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int u = pal.px;
        int b = Math.max(2, u / 2);
        int gap = u;
        int tileW = (w - gap * 6) / 7;
        if (tileW <= b * 4) {
            return;
        }
        int startX = (w - (tileW * 7 + gap * 6)) / 2;
        int fs = pal.smallFontScale;
        int lh = PixelFont.lineHeight(fs);

        for (int i = 0; i < 7; i++) {
            int x = startX + i * (tileW + gap);
            boolean hot = counts[i] > 0L;
            boolean down = (i == pressedIdx);
            int yOff = down ? b : 0;

            borderPaint.setColor(hot ? accents[i] : pal.outline);
            bgPaint.setColor(hot ? accentBgs[i] : pal.bgInset);
            ledPaint.setColor(hot ? accents[i] : pal.divider);

            canvas.drawRect(x + b, yOff, x + tileW - b, yOff + b, borderPaint);
            canvas.drawRect(x + b, h - b, x + tileW - b, h, borderPaint);
            canvas.drawRect(x, yOff + b, x + b, h - b, borderPaint);
            canvas.drawRect(x + tileW - b, yOff + b, x + tileW, h - b, borderPaint);
            canvas.drawRect(x + b, yOff + b, x + tileW - b, h - b, bgPaint);

            canvas.drawRect(x + b * 2, yOff + b * 2, x + tileW - b * 2, yOff + b * 3, ledPaint);

            float cx = x + tileW * 0.5f;
            inkPaint.setColor(hot ? accents[i] : pal.inkMuted);
            PixelFont.drawCentered(canvas, NAMES[i], cx, yOff + b * 4 + lh * 0.5f, fs, inkPaint);

            inkPaint.setColor(hot ? pal.inkPrimary : pal.inkMuted);
            String numStr = formatCompact(counts[i]);
            PixelFont.drawCentered(canvas, numStr, cx, h - b * 3 - lh * 0.5f, fs, inkPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int idx = hitTile(event.getX());
        if (action == MotionEvent.ACTION_DOWN) {
            pressedIdx = idx;
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            int picked = pressedIdx;
            pressedIdx = -1;
            invalidate();
            if (picked >= 0 && picked == idx) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                performClick();
                if (listener != null) {
                    listener.onPillarTap(picked + 1);
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            pressedIdx = -1;
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private int hitTile(float x) {
        int w = getWidth();
        if (w <= 0 || x < 0 || x > w) {
            return -1;
        }
        int idx = (int) ((x * 7f) / w);
        return Math.max(0, Math.min(6, idx));
    }

    private static String formatCompact(long n) {
        if (n >= 10000L) {
            return (n / 1000L) + "K";
        }
        return String.valueOf(n);
    }

    public interface OnPillarTapListener {
        void onPillarTap(int pillarId);
    }
}
