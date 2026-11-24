package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

public final class PixelKeycapButton extends View {

    public static final int STATE_OFF = 0;
    public static final int STATE_ON = 1;
    public static final int STATE_WARN = 2;
    public static final int STATE_DANGER = 3;
    public static final int STATE_AUDIT = 4;
    public static final int STATE_ACCENT = 5;

    private static volatile boolean hapticsEnabled = true;

    private final PixelMetrics m;
    private final Paint paint = PixelDraw.newPixelPaint();
    private PixelPalette palette;
    private String label = "";
    private String fitted = "";
    private String[] iconSprite;
    private int state = STATE_OFF;
    private int textScale;
    private boolean compact = false;
    private boolean pressedDown = false;
    private int lastFitWidth = -1;

    public PixelKeycapButton(Context ctx, PixelMetrics metrics, PixelPalette pal) {
        super(ctx);
        this.m = metrics;
        this.palette = pal;
        this.textScale = metrics.smallFontScale;
        setClickable(true);
        setFocusable(true);
    }

    public static void setHapticsEnabled(boolean enabled) {
        hapticsEnabled = enabled;
    }

    public static boolean isHapticsEnabled() {
        return hapticsEnabled;
    }

    public void setPalette(PixelPalette pal) {
        this.palette = pal;
        invalidate();
    }

    public void setLabel(String text) {
        String safe = text != null ? text : "";
        if (!safe.equals(this.label)) {
            this.label = safe;
            this.lastFitWidth = -1;
            requestLayout();
            invalidate();
        }
    }

    public void setIconSprite(String[] sprite) {
        this.iconSprite = sprite;
        this.lastFitWidth = -1;
        requestLayout();
        invalidate();
    }

    public void setKeyState(int newState) {
        if (this.state != newState) {
            this.state = newState;
            invalidate();
        }
    }

    public int getKeyState() {
        return state;
    }

    public void setCompact(boolean isCompact) {
        this.compact = isCompact;
        this.textScale = isCompact ? m.smallFontScale : m.fontScale;
        this.lastFitWidth = -1;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int u = m.u;
        int iconW = iconSprite != null ? (iconSprite[0].length() * Math.max(1, u - 1) + u * 2) : 0;
        int textW = PixelFont.measureWidth(label, textScale);
        int padX = compact ? u * 3 : u * 4;
        int minW = textW + iconW + padX * 2 + u * 2;
        int minH = compact ? (PixelFont.lineHeight(textScale) + u * 7) : (PixelFont.lineHeight(textScale) + u * 9);
        setMeasuredDimension(resolveSize(minW, widthMeasureSpec), resolveSize(minH, heightMeasureSpec));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pressedDown = true;
            invalidate();
        } else if (action == MotionEvent.ACTION_UP) {
            if (pressedDown && hapticsEnabled) {
                try {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                } catch (Throwable ignored) {
                }
            }
            pressedDown = false;
            invalidate();
        } else if (action == MotionEvent.ACTION_CANCEL) {
            pressedDown = false;
            invalidate();
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int u = m.u;
        if (w < u * 6 || h < u * 6) {
            return;
        }

        int lipH = m.keycapLip;
        int shiftY = pressedDown ? u : 0;
        int capTop = shiftY;
        int capBottom = h - (pressedDown ? u : lipH);

        int faceColor;
        int borderColor;
        int textColor;
        int iconAccent;

        switch (state) {
            case STATE_ON:
            case STATE_ACCENT:
                faceColor = palette.accent;
                borderColor = palette.borderHi;
                textColor = palette.inkOnAccent;
                iconAccent = palette.inkOnAccent;
                break;
            case STATE_WARN:
                faceColor = palette.warn;
                borderColor = palette.borderHi;
                textColor = palette.inkOnAccent;
                iconAccent = palette.inkOnAccent;
                break;
            case STATE_DANGER:
                faceColor = palette.danger;
                borderColor = palette.borderHi;
                textColor = palette.inkOnAccent;
                iconAccent = palette.inkOnAccent;
                break;
            case STATE_AUDIT:
                faceColor = palette.audit;
                borderColor = palette.borderHi;
                textColor = palette.inkOnAccent;
                iconAccent = palette.inkOnAccent;
                break;
            default:
                faceColor = palette.keyFace;
                borderColor = palette.border;
                textColor = palette.ink;
                iconAccent = palette.accent;
                break;
        }

        if (!isEnabled()) {
            faceColor = palette.well;
            borderColor = palette.border;
            textColor = palette.inkMuted;
            iconAccent = palette.inkMuted;
        }

        paint.setColor(palette.keyLip);
        canvas.drawRect(u, capBottom - u, w - u, h, paint);
        paint.setColor(palette.shadow);
        canvas.drawRect(u, h - u, w - u, h, paint);

        PixelDraw.drawNotchedBox(canvas, 0, capTop, w, capBottom, u, faceColor, borderColor, paint);

        if (state == STATE_OFF && isEnabled()) {
            paint.setColor(palette.borderHi);
            canvas.drawRect(u * 2, capTop + u, w - u * 2, capTop + u * 2, paint);
        }

        int innerLeft = u * 2;
        int innerRight = w - u * 2;
        int innerW = innerRight - innerLeft;
        int iconScale = Math.max(1, u - 1);
        int iconPx = iconSprite != null ? iconSprite[0].length() * iconScale : 0;
        int gap = (iconSprite != null && !label.isEmpty()) ? u * 2 : 0;
        int maxTextW = Math.max(0, innerW - iconPx - gap);

        if (maxTextW != lastFitWidth) {
            fitted = PixelFont.fitText(label, maxTextW, textScale);
            lastFitWidth = maxTextW;
        }

        int tw = PixelFont.measureWidth(fitted, textScale);
        int totalW = iconPx + gap + tw;
        int startX = innerLeft + Math.max(0, (innerW - totalW) / 2);
        int centerY = capTop + (capBottom - capTop) / 2;

        if (iconSprite != null) {
            int ih = iconSprite.length * iconScale;
            PixelSpriteAtlas.drawSprite(canvas, iconSprite, startX, centerY - ih / 2, iconScale, textColor, iconAccent, paint);
            startX += iconPx + gap;
        }
        if (!fitted.isEmpty()) {
            int th = PixelFont.lineHeight(textScale);
            PixelFont.drawText(canvas, fitted, startX, centerY - th / 2, textScale, textColor, paint);
        }
    }
}
