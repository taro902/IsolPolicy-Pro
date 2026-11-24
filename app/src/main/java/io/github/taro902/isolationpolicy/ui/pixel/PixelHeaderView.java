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
    private String versionText = "2.2.0";
    private String subtitleLine = "OFFLINE  HOOKS 0/10";
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
        this.versionText = (version != null && !version.isEmpty()) ? version : "2.2.0";
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
        int u = m.u;
        int h = Math.max(u * 21, 16 * Math.max(2, u - 1) + u * 8);
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
        if (w < u * 20 || h < u * 12) {
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

        int shieldScale = Math.max(2, u - 1);
        int shieldSz = 16 * shieldScale;
        int sx = u * 3;
        int sy = (h - u - shieldSz) / 2;
        PixelSpriteAtlas.drawSprite(canvas, shieldSprite, sx, sy, shieldScale, palette.ink, ledColor, paint);

        int palBtnW = PixelFont.measureMicroWidth(palette.shortName, m.microScale) + u * 13;
        int palBtnH = u * 11;
        paletteBtnLeft = w - u * 3 - palBtnW;
        int palBtnTop = (h - u - palBtnH) / 2;
        PixelDraw.drawNotchedBox(canvas, paletteBtnLeft, palBtnTop, w - u * 3, palBtnTop + palBtnH, u, palette.keyFace, palette.border, paint);

        int ctrlScale = Math.max(1, u - 1);
        int ctrlSz = 9 * ctrlScale;
        PixelSpriteAtlas.drawSprite(
            canvas,
            PixelSpriteAtlas.CTRL_PALETTE,
            paletteBtnLeft + u * 2,
            palBtnTop + (palBtnH - ctrlSz) / 2,
            ctrlScale,
            palette.ink,
            palette.accent,
            paint
        );
        PixelFont.drawMicro(
            canvas,
            palette.shortName,
            paletteBtnLeft + u * 3 + ctrlSz,
            palBtnTop + (palBtnH - PixelFont.microHeight(m.microScale)) / 2,
            m.microScale,
            palette.ink,
            paint
        );

        int hookPillW = PixelFont.measureMicroWidth(hooksBadge, m.microScale) + u * 8;
        int hookPillLeft = paletteBtnLeft - u * 2 - hookPillW;
        PixelDraw.drawNotchedBox(canvas, hookPillLeft, palBtnTop, paletteBtnLeft - u * 2, palBtnTop + palBtnH, u, palette.well, ledColor, paint);
        PixelDraw.drawLed(canvas, hookPillLeft + u * 2, palBtnTop + (palBtnH - u * 3) / 2, u * 3, u, ledColor, palette.border, paint);
        PixelFont.drawMicro(
            canvas,
            hooksBadge,
            hookPillLeft + u * 6,
            palBtnTop + (palBtnH - PixelFont.microHeight(m.microScale)) / 2,
            m.microScale,
            ledColor,
            paint
        );

        int textLeft = sx + shieldSz + u * 3;
        int textMaxW = Math.max(0, hookPillLeft - u * 2 - textLeft);
        int titleSc = m.fontScale;
        int microSc = m.microScale;
        int titleH = PixelFont.lineHeight(titleSc);
        int subH = PixelFont.microHeight(microSc);
        int totalH = titleH + u * 2 + subH;
        int topY = (h - u - totalH) / 2;

        String title = PixelFont.fitText("ISOLPOLICY " + versionText, textMaxW, titleSc);
        PixelFont.drawText(canvas, title, textLeft, topY, titleSc, palette.ink, paint);
        String sub = PixelFont.fitMicro(subtitleLine, textMaxW, microSc);
        PixelFont.drawMicro(canvas, sub, textLeft, topY + titleH + u * 2, microSc, palette.inkMuted, paint);
    }
}
