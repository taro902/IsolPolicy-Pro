package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class PixelHeaderView extends View {

    public interface OnPaletteTapListener {
        void onCyclePalette();
    }

    public static final int STATUS_OFFLINE = 0;
    public static final int STATUS_LOCKED = 1;
    public static final int STATUS_AUDIT = 2;
    public static final int STATUS_DEGRADED = 3;

    private final PixelMetrics m;
    private final Paint paint = PixelDraw.newPixelPaint();
    private PixelPalette palette;
    private OnPaletteTapListener paletteListener;

    private int status = STATUS_OFFLINE;
    private String versionText = "2.2.1";
    private String subtitleLine = "IPC WAIT  GUARD 0";
    private String hooksBadge = "0/10";
    private int paletteBtnLeft = 0;

    public PixelHeaderView(Context ctx, PixelMetrics metrics, PixelPalette pal) {
        super(ctx);
        this.m = metrics;
        this.palette = pal;
        setClickable(true);
    }

    public void setPalette(PixelPalette pal) {
        this.palette = pal;
        invalidate();
    }

    public void setOnPaletteTapListener(OnPaletteTapListener l) {
        this.paletteListener = l;
    }

    public void bindStatus(
        boolean connected,
        String version,
        int healthyHooks,
        boolean hasTripped,
        int guardedCount,
        long enforcedHits,
        long auditHits
    ) {
        this.versionText = (version != null && !version.isEmpty()) ? version : "2.2.1";
        this.hooksBadge = healthyHooks + "/" + PolicyFlags.PILLAR_COUNT;
        if (!connected) {
            this.status = STATUS_OFFLINE;
            this.subtitleLine = "IPC WAIT  GUARD " + guardedCount;
        } else if (hasTripped || healthyHooks < PolicyFlags.PILLAR_COUNT) {
            this.status = STATUS_DEGRADED;
            this.subtitleLine = "GUARD " + guardedCount + "  ENF " + enforcedHits + "  AUD " + auditHits;
        } else if (auditHits > 0 && enforcedHits == 0) {
            this.status = STATUS_AUDIT;
            this.subtitleLine = "AUDIT  GUARD " + guardedCount + "  AUD " + auditHits;
        } else {
            this.status = STATUS_LOCKED;
            this.subtitleLine = "GUARD " + guardedCount + "  ENF " + enforcedHits + "  AUD " + auditHits;
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = m.dp(68);
        setMeasuredDimension(resolveSize(m.dp(360), widthMeasureSpec), resolveSize(h, heightMeasureSpec));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            if (event.getX() >= paletteBtnLeft && paletteListener != null) {
                if (PixelKeycapButton.isHapticsEnabled()) {
                    try {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    } catch (Throwable ignored) {
                    }
                }
                paletteListener.onCyclePalette();
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
        if (w < u * 24 || h < u * 16) {
            return;
        }

        PixelDraw.drawBayerFill(canvas, 0, 0, w, h - u, palette.surface, palette.ditherDot, 3, u, paint);
        PixelDraw.drawDivider(canvas, 0, h - u, w, u, palette.border, paint);

        String[] shieldSprite;
        int ledColor;
        switch (status) {
            case STATUS_LOCKED:
                shieldSprite = PixelSpriteAtlas.SHIELD_LOCKED;
                ledColor = palette.accent;
                break;
            case STATUS_AUDIT:
                shieldSprite = PixelSpriteAtlas.SHIELD_AUDIT;
                ledColor = palette.audit;
                break;
            case STATUS_DEGRADED:
                shieldSprite = PixelSpriteAtlas.SHIELD_WARN;
                ledColor = palette.warn;
                break;
            default:
                shieldSprite = PixelSpriteAtlas.SHIELD_OFF;
                ledColor = palette.danger;
                break;
        }

        int shieldScale = Math.max(3, u + 1);
        int shieldSz = 16 * shieldScale;
        int sx = u * 4;
        int sy = (h - u - shieldSz) / 2;
        PixelSpriteAtlas.drawSprite(canvas, shieldSprite, sx, sy, shieldScale, palette.ink, ledColor, paint);

        int pillSc = m.microScale;
        int pillTextH = PixelFont.lineHeight(pillSc);
        int ctrlScale = Math.max(2, u - 1);
        int ctrlSz = 9 * ctrlScale;
        int pillH = Math.max(pillTextH, ctrlSz) + u * 5;
        int pillTop = (h - u - pillH) / 2;

        int palTextW = PixelFont.measureWidth(palette.shortName, pillSc);
        int palBtnW = ctrlSz + palTextW + u * 7;
        paletteBtnLeft = w - u * 4 - palBtnW;
        PixelDraw.drawNotchedBox(canvas, paletteBtnLeft, pillTop, w - u * 4, pillTop + pillH, u, palette.keyFace, palette.borderHi, paint);
        PixelSpriteAtlas.drawSprite(
            canvas,
            PixelSpriteAtlas.CTRL_PALETTE,
            paletteBtnLeft + u * 2,
            pillTop + (pillH - ctrlSz) / 2,
            ctrlScale,
            palette.ink,
            palette.accent,
            paint
        );
        PixelFont.drawText(
            canvas,
            palette.shortName,
            paletteBtnLeft + u * 4 + ctrlSz,
            pillTop + (pillH - pillTextH) / 2,
            pillSc,
            palette.ink,
            paint
        );

        int ledSz = u * 3;
        int hookTextW = PixelFont.measureWidth(hooksBadge, pillSc);
        int hookPillW = ledSz + hookTextW + u * 7;
        int hookPillLeft = paletteBtnLeft - u * 2 - hookPillW;
        PixelDraw.drawNotchedBox(canvas, hookPillLeft, pillTop, paletteBtnLeft - u * 2, pillTop + pillH, u, palette.well, ledColor, paint);
        PixelDraw.drawLed(canvas, hookPillLeft + u * 2, pillTop + (pillH - ledSz) / 2, ledSz, u, ledColor, palette.border, paint);
        PixelFont.drawText(
            canvas,
            hooksBadge,
            hookPillLeft + u * 4 + ledSz,
            pillTop + (pillH - pillTextH) / 2,
            pillSc,
            ledColor,
            paint
        );

        int textLeft = sx + shieldSz + u * 4;
        int textMaxW = Math.max(0, hookPillLeft - u * 3 - textLeft);
        String rawTitle = "ISOLPOLICY " + versionText;
        int titleSc = PixelFont.measureWidth(rawTitle, m.fontScale) <= textMaxW ? m.fontScale : m.smallFontScale;
        int titleH = PixelFont.lineHeight(titleSc);
        int subSc = m.microScale;
        int subH = PixelFont.lineHeight(subSc);
        int totalH = titleH + u * 2 + subH;
        int topY = (h - u - totalH) / 2;

        String title = PixelFont.fitText(rawTitle, textMaxW, titleSc);
        PixelFont.drawTextWithShadow(canvas, title, textLeft, topY, titleSc, palette.ink, palette.shadow, paint);
        String sub = PixelFont.fitText(subtitleLine, textMaxW, subSc);
        PixelFont.drawText(canvas, sub, textLeft, topY + titleH + u * 2, subSc, palette.inkMuted, paint);
    }
}
