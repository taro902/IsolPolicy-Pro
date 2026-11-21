package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public final class PixelThreatBar extends View {

    private static final int SEGMENTS = 5;

    private final PixelTheme theme;
    private final Paint framePaint = new Paint();
    private final Paint offPaint = new Paint();
    private final Paint onPaint = new Paint();
    private int litSegments;

    public PixelThreatBar(Context context, PixelTheme theme) {
        super(context);
        this.theme = theme;

        framePaint.setAntiAlias(false);
        framePaint.setColor(theme.borderOuter);

        offPaint.setAntiAlias(false);
        offPaint.setColor(theme.bgInput);

        onPaint.setAntiAlias(false);
        onPaint.setColor(theme.accentInfo);
    }

    public void setThreatScore(int score) {
        int next;
        if (score <= 0) {
            next = 0;
        } else if (score <= 20) {
            next = 1;
        } else if (score <= 40) {
            next = 2;
        } else if (score <= 60) {
            next = 3;
        } else if (score <= 80) {
            next = 4;
        } else {
            next = 5;
        }
        if (next != litSegments) {
            litSegments = next;
            if (next >= 4) {
                onPaint.setColor(theme.accentDanger);
            } else if (next == 3) {
                onPaint.setColor(theme.accentWarn);
            } else {
                onPaint.setColor(theme.accentInfo);
            }
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int u = theme.px;
        int desiredW = u * 23;
        int desiredH = u * 5;
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
        if (w < SEGMENTS * 3 || h < 3) {
            return;
        }
        int gap = Math.max(2, u);
        int segW = (w - gap * (SEGMENTS - 1)) / SEGMENTS;
        for (int i = 0; i < SEGMENTS; i++) {
            int x = i * (segW + gap);
            canvas.drawRect(x, 0, x + segW, h, framePaint);
            Paint fill = i < litSegments ? onPaint : offPaint;
            if (segW > 2 && h > 2) {
                canvas.drawRect(x + 1, 1, x + segW - 1, h - 1, fill);
            } else {
                canvas.drawRect(x, 0, x + segW, h, fill);
            }
        }
    }
}