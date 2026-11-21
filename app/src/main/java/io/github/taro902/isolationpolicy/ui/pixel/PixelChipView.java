package io.github.taro902.isolationpolicy.ui.pixel;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

public final class PixelChipView extends View {

    private final PixelTheme theme;
    private final Paint bgPaint = new Paint();
    private final Paint fillPaint = new Paint();
    private final Paint borderPaint = new Paint();
    private final Paint bevelTopPaint = new Paint();
    private final Paint bevelBotPaint = new Paint();
    private final Paint textPaint = new Paint();

    private String label = "";
    private boolean active;
    private boolean pressed;
    private int scanStep = 3;
    private ValueAnimator animator;

    public PixelChipView(Context context, PixelTheme theme) {
        super(context);
        this.theme = theme;
        setClickable(true);
        setFocusable(true);

        bgPaint.setAntiAlias(false);
        bgPaint.setStyle(Paint.Style.FILL);

        fillPaint.setAntiAlias(false);
        fillPaint.setStyle(Paint.Style.FILL);

        borderPaint.setAntiAlias(false);
        borderPaint.setStyle(Paint.Style.FILL);

        bevelTopPaint.setAntiAlias(false);
        bevelTopPaint.setStyle(Paint.Style.FILL);

        bevelBotPaint.setAntiAlias(false);
        bevelBotPaint.setStyle(Paint.Style.FILL);

        textPaint.setAntiAlias(true);
        textPaint.setTypeface(theme.monoBold);
        textPaint.setTextSize(theme.dp(10.5f));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFontFeatureSettings("tnum");

        applyState("OFF", theme.chipOffBg, theme.chipOffBg, theme.borderOuter, theme.chipOffInk, false, false);
    }

    public void applyState(
            String nextLabel,
            int baseBg,
            int activeFill,
            int borderColor,
            int inkColor,
            boolean isActive,
            boolean animate
    ) {
        boolean widthChanged = !nextLabel.equals(this.label);
        boolean stateChanged = isActive != this.active || fillPaint.getColor() != activeFill;

        this.label = nextLabel;
        this.active = isActive;
        bgPaint.setColor(baseBg);
        fillPaint.setColor(activeFill);
        borderPaint.setColor(borderColor);
        bevelTopPaint.setColor(theme.bevelLight);
        bevelBotPaint.setColor(theme.bevelDark);
        textPaint.setColor(inkColor);

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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int u = theme.px;
        int textW = (int) Math.ceil(textPaint.measureText(label));
        int desiredW = Math.max(theme.dp(40), textW + u * 8);
        int desiredH = Math.max(theme.dp(26), u * 11);
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
        if (w <= u * 4 || h <= u * 4) {
            return;
        }

        canvas.drawRect(u, 0, w - u, u, borderPaint);
        canvas.drawRect(u, h - u, w - u, h, borderPaint);
        canvas.drawRect(0, u, u, h - u, borderPaint);
        canvas.drawRect(w - u, u, w, h - u, borderPaint);

        canvas.drawRect(u, u, w - u, h - u, bgPaint);

        if (active) {
            int innerW = w - u * 2;
            int fillW = (scanStep >= 3) ? innerW : ((innerW * scanStep) / 3);
            if (fillW > 0) {
                canvas.drawRect(u, u, u + fillW, h - u, fillPaint);
            }
            canvas.drawRect(u, u, u * 2, h - u, borderPaint);
        }

        Paint topP = pressed ? bevelBotPaint : bevelTopPaint;
        Paint botP = pressed ? bevelTopPaint : bevelBotPaint;
        canvas.drawRect(u, u, w - u, u * 2, topP);
        canvas.drawRect(u, h - u * 2, w - u, h - u, botP);

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float cx = w * 0.5f + (pressed ? u * 0.5f : 0f);
        float cy = (h - fm.ascent - fm.descent) * 0.5f + (pressed ? u * 0.5f : 0f);
        canvas.drawText(label, cx, cy, textPaint);
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
