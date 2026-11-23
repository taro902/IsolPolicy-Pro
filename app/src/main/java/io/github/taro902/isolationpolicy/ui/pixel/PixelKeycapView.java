package io.github.taro902.isolationpolicy.ui.pixel;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

public final class PixelKeycapView extends View {

    private final PixelPalette pal;
    private final Paint outlinePaint = new Paint();
    private final Paint bgPaint = new Paint();
    private final Paint fillPaint = new Paint();
    private final Paint lipPaint = new Paint();
    private final Paint hiPaint = new Paint();
    private final Paint inkPaint = new Paint();
    private final Paint ledPaint = new Paint();
    private final Paint specPaint = new Paint();

    private String label = "";
    private boolean active;
    private boolean showLed;
    private boolean pressed;
    private int scanStep = 3;
    private int fontScale;
    private int[] swatches;
    private ValueAnimator animator;

    public PixelKeycapView(Context context, PixelPalette pal, boolean showLed) {
        super(context);
        this.pal = pal;
        this.showLed = showLed;
        this.fontScale = pal.fontScale;
        setClickable(true);
        setFocusable(true);

        Paint[] all = {outlinePaint, bgPaint, fillPaint, lipPaint, hiPaint, inkPaint, ledPaint, specPaint};
        for (Paint p : all) {
            p.setAntiAlias(false);
            p.setStyle(Paint.Style.FILL);
        }
        hiPaint.setColor(pal.highlight);
        specPaint.setColor(pal.ledSpec);

        applyState("OFF", pal.keyOffBg, pal.keyOffBg, pal.keyOffLip, pal.outline, pal.keyOffInk, pal.ledOff, false, false);
    }

    public void setSmallText(boolean small) {
        int next = small ? pal.smallFontScale : pal.fontScale;
        if (next != this.fontScale) {
            this.fontScale = next;
            requestLayout();
            invalidate();
        }
    }

    public void setSwatches(int[] colors) {
        this.swatches = colors;
        requestLayout();
        invalidate();
    }

    public void applyState(
            String nextLabel,
            int baseBg,
            int activeFill,
            int lipColor,
            int borderColor,
            int inkColor,
            int ledColor,
            boolean isActive,
            boolean animate
    ) {
        boolean widthChanged = !nextLabel.equals(this.label);
        boolean stateChanged = isActive != this.active || fillPaint.getColor() != activeFill;

        this.label = nextLabel;
        this.active = isActive;
        bgPaint.setColor(baseBg);
        fillPaint.setColor(activeFill);
        lipPaint.setColor(lipColor);
        outlinePaint.setColor(borderColor);
        inkPaint.setColor(inkColor);
        ledPaint.setColor(ledColor);

        if (animator != null) {
            animator.cancel();
            animator = null;
        }

        if (animate && stateChanged && isActive) {
            scanStep = 1;
            ValueAnimator va = ValueAnimator.ofInt(1, 3);
            va.setDuration(90L);
            va.addUpdateListener(a -> {
                int s = (Integer) a.getAnimatedValue();
                if (s != scanStep) {
                    scanStep = s;
                    invalidate();
                }
            });
            animator = va;
            va.start();
        } else {
            scanStep = 3;
        }

        if (widthChanged) {
            requestLayout();
        }
        invalidate();
    }

    public void applyAccent(String lbl, int accent, int accentBg, int accentLip, boolean isActive, boolean animate) {
        if (isActive) {
            applyState(lbl, pal.keyOffBg, accentBg, accentLip, accent, accent, accent, true, animate);
        } else {
            applyState(lbl, pal.keyOffBg, pal.keyOffBg, pal.keyOffLip, pal.outline, pal.keyOffInk, pal.ledOff, false, false);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int u = pal.px;
        int fs = fontScale;
        int textW = PixelFont.measureWidth(label, fs);
        int extraL = showLed ? (fs * 4 + u * 2) : 0;
        int extraSwatch = (swatches != null && swatches.length == 4) ? (u * 14) : 0;
        int desiredW = Math.max(u * 14, u * 7 + extraL + extraSwatch + textW);
        int desiredH = Math.max(u * 12, PixelFont.lineHeight(fs) + u * 8);
        setMeasuredDimension(
                resolveSize(desiredW, widthMeasureSpec),
                resolveSize(desiredH, heightMeasureSpec)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int u = pal.px;
        if (w <= u * 6 || h <= u * 6) {
            return;
        }

        int lip = u * 2;
        int top = pressed ? lip : 0;
        int bot = h;
        int faceBot = pressed ? (bot - u) : (bot - u * 3);

        canvas.drawRect(u, top + u, w - u, bot - u, bgPaint);

        if (active) {
            int innerW = w - u * 2;
            int fillW = (scanStep >= 3) ? innerW : ((innerW * scanStep) / 3);
            if (fillW > 0) {
                canvas.drawRect(u, top + u, u + fillW, faceBot, fillPaint);
            }
        }

        if (!pressed) {
            canvas.drawRect(u, faceBot + u, w - u, bot - u, lipPaint);
            canvas.drawRect(u, faceBot, w - u, faceBot + u, outlinePaint);
        }

        canvas.drawRect(u * 2, top + u, w - u * 2, top + u * 2, hiPaint);

        canvas.drawRect(u * 2, top, w - u * 2, top + u, outlinePaint);
        canvas.drawRect(u * 2, bot - u, w - u * 2, bot, outlinePaint);
        canvas.drawRect(0, top + u * 2, u, bot - u * 2, outlinePaint);
        canvas.drawRect(w - u, top + u * 2, w, bot - u * 2, outlinePaint);

        canvas.drawRect(u, top + u, u * 2, top + u * 2, outlinePaint);
        canvas.drawRect(w - u * 2, top + u, w - u, top + u * 2, outlinePaint);
        canvas.drawRect(u, bot - u * 2, u * 2, bot - u, outlinePaint);
        canvas.drawRect(w - u * 2, bot - u * 2, w - u, bot - u, outlinePaint);

        int fs = fontScale;
        int faceTop = top + u;
        int faceH = Math.max(1, faceBot - faceTop);
        int textW = PixelFont.measureWidth(label, fs);
        int textH = PixelFont.lineHeight(fs);
        float textY = faceTop + (faceH - textH) * 0.5f;

        if (swatches != null && swatches.length == 4) {
            int swSize = u * 2;
            int swBlockW = swSize * 4 + u * 3;
            int totalContent = swBlockW + u * 2 + textW;
            float startX = (w - totalContent) * 0.5f;
            float sy = faceTop + (faceH - swSize) * 0.5f;
            for (int i = 0; i < 4; i++) {
                ledPaint.setColor(swatches[i]);
                float sx = startX + i * (swSize + u);
                canvas.drawRect(sx - 1, sy - 1, sx + swSize + 1, sy + swSize + 1, outlinePaint);
                canvas.drawRect(sx, sy, sx + swSize, sy + swSize, ledPaint);
            }
            PixelFont.draw(canvas, label, startX + swBlockW + u * 2, textY, fs, inkPaint);
        } else if (showLed) {
            int ledDim = fs * 4;
            int totalContent = ledDim + u * 2 + textW;
            float startX = (w - totalContent) * 0.5f;
            float ly = faceTop + (faceH - ledDim) * 0.5f;
            canvas.drawRect(startX, ly, startX + ledDim, ly + ledDim, ledPaint);
            if (active) {
                canvas.drawRect(startX + fs, ly + fs, startX + fs * 2, ly + fs * 2, specPaint);
            }
            PixelFont.draw(canvas, label, startX + ledDim + u * 2, textY, fs, inkPaint);
        } else {
            PixelFont.drawCentered(canvas, label, w * 0.5f, faceTop + faceH * 0.5f, fs, inkPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pressed = true;
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            pressed = false;
            invalidate();
            float x = event.getX();
            float y = event.getY();
            if (x >= 0 && x <= getWidth() && y >= 0 && y <= getHeight()) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                performClick();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            pressed = false;
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }
}
