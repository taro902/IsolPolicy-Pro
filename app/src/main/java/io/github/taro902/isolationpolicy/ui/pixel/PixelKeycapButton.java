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
    private PixelPalette previewPalette;
    private String label = "";
    private String fitted = "";
    private int fittedScale = 3;
    private String[] iconSprite;
    private int state = STATE_OFF;
    private boolean compact = false;
    private boolean pressedDown = false;
    private int lastFitWidth = -1;

    public PixelKeycapButton(Context ctx, PixelMetrics metrics, PixelPalette pal) {
        super(ctx);
        this.m = metrics;
        this.palette = pal;
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

    public void setPalettePreview(PixelPalette preview) {
        this.previewPalette = preview;
        this.lastFitWidth = -1;
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
        this.lastFitWidth = -1;
        requestLayout();
        invalidate();
    }

    private int preferredScale() {
        return compact ? m.microScale : m.smallFontScale;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int u = m.u;
        int sc = preferredScale();
        int iconSc = Math.max(2, u - 1);
        int iconW = iconSprite != null ? (iconSprite[0].length() * iconSc + u * 2) : 0;
        int swatchW = previewPalette != null ? (u * 10) : 0;
        int textW = PixelFont.measureWidth(label, sc);
        int minW = textW + iconW + swatchW + u * 8;
        int minH = compact ? m.dp(40) : m.dp(46);
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

        int iconScale = Math.max(2, u - 1);
        int iconPx = iconSprite != null ? iconSprite[0].length() * iconScale : 0;
        int swatchW = previewPalette != null ? (u * 9) : 0;
        int leadPx = previewPalette != null ? swatchW : iconPx;
        int gap = (leadPx > 0 && !label.isEmpty()) ? u * 2 : 0;
        int maxTextW = Math.max(0, innerW - leadPx - gap);

        if (maxTextW != lastFitWidth) {
            int pref = m.smallFontScale;
            if (PixelFont.measureWidth(label, pref) <= maxTextW) {
                fittedScale = pref;
                fitted = label;
            } else {
                fittedScale = m.microScale;
                fitted = PixelFont.fitText(label, maxTextW, fittedScale);
            }
            lastFitWidth = maxTextW;
        }

        int tw = PixelFont.measureWidth(fitted, fittedScale);
        int totalW = leadPx + gap + tw;
        int startX = innerLeft + Math.max(0, (innerW - totalW) / 2);
        int centerY = capTop + (capBottom - capTop) / 2;

        if (previewPalette != null) {
            int swH = u * 4;
            int swY = centerY - swH / 2;
            paint.setColor(borderColor);
            canvas.drawRect(startX, swY - u / 2, startX + u * 9, swY + swH + u / 2, paint);
            paint.setColor(previewPalette.bg);
            canvas.drawRect(startX + u / 2, swY, startX + u * 3, swY + swH, paint);
            paint.setColor(previewPalette.surface);
            canvas.drawRect(startX + u * 3, swY, startX + u * 6, swY + swH, paint);
            paint.setColor(previewPalette.accent);
            canvas.drawRect(startX + u * 6, swY, startX + u * 9 - u / 2, swY + swH, paint);
            startX += swatchW + gap;
        } else if (iconSprite != null) {
            int ih = iconSprite.length * iconScale;
            PixelSpriteAtlas.drawSprite(canvas, iconSprite, startX, centerY - ih / 2, iconScale, textColor, iconAccent, paint);
            startX += iconPx + gap;
        }
        if (!fitted.isEmpty()) {
            int th = PixelFont.lineHeight(fittedScale);
            PixelFont.drawText(canvas, fitted, startX, centerY - th / 2, fittedScale, textColor, paint);
        }
    }
}
