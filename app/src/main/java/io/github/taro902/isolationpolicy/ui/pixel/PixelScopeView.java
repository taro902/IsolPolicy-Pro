package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public final class PixelScopeView extends View {

    private static final int COLS = 16;
    private static final int SEGS = 8;

    private final PixelTheme theme;
    private final int[] levels = new int[COLS];
    private final Paint bgPaint = new Paint();
    private final Paint borderPaint = new Paint();
    private final Paint gridPaint = new Paint();
    private final Paint segPaint = new Paint();

    public PixelScopeView(Context context, PixelTheme theme) {
        super(context);
        this.theme = theme;

        bgPaint.setAntiAlias(false);
        bgPaint.setColor(theme.bgInput);

        borderPaint.setAntiAlias(false);
        borderPaint.setColor(theme.borderOuter);

        gridPaint.setAntiAlias(false);
        gridPaint.setColor(theme.divider);

        segPaint.setAntiAlias(false);
        segPaint.setStyle(Paint.Style.FILL);
    }

    public void pushDelta(long deltaEvents) {
        System.arraycopy(levels, 1, levels, 0, COLS - 1);
        levels[COLS - 1] = scaleDelta(deltaEvents);
        invalidate();
    }

    public void clear() {
        for (int i = 0; i < COLS; i++) {
            levels[i] = 0;
        }
        invalidate();
    }

    private static int scaleDelta(long d) {
        if (d <= 0L) return 0;
        if (d == 1L) return 2;
        if (d <= 3L) return 3;
        if (d <= 6L) return 4;
        if (d <= 12L) return 5;
        if (d <= 24L) return 6;
        if (d <= 48L) return 7;
        return SEGS;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredW = theme.dp(118);
        int desiredH = theme.dp(42);
        setMeasuredDimension(
                resolveSize(desiredW, widthMeasureSpec),
                resolveSize(desiredH, heightMeasureSpec)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int u = theme.px;
        if (w <= u * 6 || h <= u * 6) {
            return;
        }

        canvas.drawRect(u, 0, w - u, u, borderPaint);
        canvas.drawRect(u, h - u, w - u, h, borderPaint);
        canvas.drawRect(0, u, u, h - u, borderPaint);
        canvas.drawRect(w - u, u, w, h - u, borderPaint);
        canvas.drawRect(u, u, w - u, h - u, bgPaint);

        int pad = u * 2;
        int innerW = w - pad * 2;
        int innerH = h - pad * 2;
        if (innerW <= COLS || innerH <= SEGS) {
            return;
        }

        for (int g = 1; g < 4; g++) {
            int gy = pad + (innerH * g) / 4;
            canvas.drawRect(pad, gy, w - pad, gy + 1, gridPaint);
        }

        int colStep = innerW / COLS;
        int barW = Math.max(2, colStep - Math.max(1, u / 2));
        int segStep = innerH / SEGS;
        int segH = Math.max(2, segStep - 1);
        int startX = pad + (innerW - colStep * COLS) / 2;

        for (int c = 0; c < COLS; c++) {
            int lv = levels[c];
            int x = startX + c * colStep;
            for (int s = 0; s < SEGS; s++) {
                int y = (h - pad) - (s + 1) * segStep;
                if (s < lv) {
                    segPaint.setColor(colorForSegment(s));
                } else {
                    segPaint.setColor(theme.divider);
                }
                canvas.drawRect(x, y, x + barW, y + segH, segPaint);
            }
        }
    }

    private int colorForSegment(int segIdx) {
        if (segIdx >= 6) return theme.accentDanger;
        if (segIdx >= 4) return theme.accentWarn;
        if (segIdx >= 2) return theme.accentOk;
        return theme.accentInfo;
    }
}