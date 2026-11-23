package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public final class PixelTagBarView extends View {

    private static final int MAX_TAGS = 5;

    private final PixelPalette pal;
    private final Paint bgPaint = new Paint();
    private final Paint borderPaint = new Paint();
    private final Paint inkPaint = new Paint();

    private final String[] texts = new String[MAX_TAGS];
    private final int[] bgs = new int[MAX_TAGS];
    private final int[] borders = new int[MAX_TAGS];
    private final int[] inks = new int[MAX_TAGS];
    private int count;

    public PixelTagBarView(Context context, PixelPalette pal) {
        super(context);
        this.pal = pal;
        bgPaint.setAntiAlias(false);
        borderPaint.setAntiAlias(false);
        inkPaint.setAntiAlias(false);
    }

    public void clearTags() {
        count = 0;
    }

    public void addTag(String label, int bg, int border, int ink) {
        if (count >= MAX_TAGS || label == null || label.isEmpty()) {
            return;
        }
        texts[count] = label;
        bgs[count] = bg;
        borders[count] = border;
        inks[count] = ink;
        count++;
    }

    public void commitTags() {
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int fs = pal.smallFontScale;
        int u = pal.px;
        int h = PixelFont.lineHeight(fs) + u * 4;
        setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                resolveSize(h, heightMeasureSpec)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (count == 0) {
            return;
        }
        int fs = pal.smallFontScale;
        int u = pal.px;
        int h = getHeight();
        int maxW = getWidth();
        int b = Math.max(2, u / 2);
        int x = 0;
        int gap = u * 2;
        int padH = u * 2;
        int th = PixelFont.lineHeight(fs);
        float ty = (h - th) * 0.5f;

        for (int i = 0; i < count; i++) {
            int tw = PixelFont.measureWidth(texts[i], fs);
            int pillW = tw + padH * 2 + b * 2;
            if (x > 0 && x + pillW > maxW) {
                break;
            }
            bgPaint.setColor(bgs[i]);
            borderPaint.setColor(borders[i]);
            inkPaint.setColor(inks[i]);

            canvas.drawRect(x + b, 0, x + pillW - b, b, borderPaint);
            canvas.drawRect(x + b, h - b, x + pillW - b, h, borderPaint);
            canvas.drawRect(x, b, x + b, h - b, borderPaint);
            canvas.drawRect(x + pillW - b, b, x + pillW, h - b, borderPaint);
            canvas.drawRect(x + b, b, x + pillW - b, h - b, bgPaint);

            PixelFont.draw(canvas, texts[i], x + b + padH, ty, fs, inkPaint);
            x += pillW + gap;
        }
    }
}
