package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public final class PixelTextView extends View {

    private final Paint textPaint = new Paint();
    private final Paint ledPaint = new Paint();
    private final Paint specPaint = new Paint();
    private String text = "";
    private int scale;
    private boolean showLed;

    public PixelTextView(Context context, String initialText, int color, int pixelScale) {
        super(context);
        this.text = initialText != null ? initialText : "";
        this.scale = Math.max(2, pixelScale);

        textPaint.setAntiAlias(false);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(color);

        ledPaint.setAntiAlias(false);
        ledPaint.setStyle(Paint.Style.FILL);

        specPaint.setAntiAlias(false);
        specPaint.setStyle(Paint.Style.FILL);
    }

    public void setText(String next) {
        String clean = next != null ? next : "";
        if (!clean.equals(this.text)) {
            boolean sizeChanged = clean.length() != this.text.length();
            this.text = clean;
            if (sizeChanged) {
                requestLayout();
            }
            invalidate();
        }
    }

    public void setTextColor(int color) {
        if (textPaint.getColor() != color) {
            textPaint.setColor(color);
            invalidate();
        }
    }

    public void setLed(boolean enabled, int color, int specular) {
        boolean layoutChanged = enabled != this.showLed;
        this.showLed = enabled;
        ledPaint.setColor(color);
        specPaint.setColor(specular);
        if (layoutChanged) {
            requestLayout();
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int ledSpace = showLed ? (scale * 7) : 0;
        int desiredW = getPaddingLeft() + getPaddingRight() + ledSpace + PixelFont.measureWidth(text, scale);
        int desiredH = getPaddingTop() + getPaddingBottom() + PixelFont.lineHeight(scale);
        setMeasuredDimension(
                resolveSize(desiredW, widthMeasureSpec),
                resolveSize(desiredH, heightMeasureSpec)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int h = getHeight();
        float x = getPaddingLeft();
        float ty = (h - PixelFont.lineHeight(scale)) * 0.5f;
        if (showLed) {
            int ledDim = scale * 4;
            float ly = (h - ledDim) * 0.5f;
            canvas.drawRect(x, ly, x + ledDim, ly + ledDim, ledPaint);
            canvas.drawRect(x + scale, ly + scale, x + scale * 2, ly + scale * 2, specPaint);
            x += scale * 7;
        }
        PixelFont.draw(canvas, text, x, ty, scale, textPaint);
    }
}
