package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

public final class PixelIconView extends View {

    private final PixelMetrics m;
    private final Paint framePaint = PixelDraw.newPixelPaint();
    private final Paint bmpPaint = PixelDraw.newPixelPaint();
    private final Rect srcRect = new Rect();
    private final Rect dstRect = new Rect();
    private PixelPalette palette;
    private Bitmap nativeBitmap;
    private Bitmap pixelBitmap;
    private boolean pixelMode = true;
    private boolean guardedBorder = false;

    public PixelIconView(Context ctx, PixelMetrics metrics, PixelPalette pal) {
        super(ctx);
        this.m = metrics;
        this.palette = pal;
    }

    public void setPalette(PixelPalette pal) {
        this.palette = pal;
        invalidate();
    }

    public void setPixelMode(boolean enabled) {
        if (this.pixelMode != enabled) {
            this.pixelMode = enabled;
            bmpPaint.setFilterBitmap(!enabled);
            bmpPaint.setAntiAlias(!enabled);
            invalidate();
        }
    }

    public void setGuardedBorder(boolean guarded) {
        if (this.guardedBorder != guarded) {
            this.guardedBorder = guarded;
            invalidate();
        }
    }

    public void setBitmaps(Bitmap nativeBmp, Bitmap quantized16) {
        this.nativeBitmap = nativeBmp;
        this.pixelBitmap = quantized16;
        invalidate();
    }

    public static Bitmap quantizeTo16(Bitmap src) {
        if (src == null || src.isRecycled()) {
            return null;
        }
        try {
            return Bitmap.createScaledBitmap(src, 16, 16, false);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int u = m.u;
        if (w < u * 6 || h < u * 6) {
            return;
        }
        int bdr = guardedBorder ? palette.accent : palette.border;
        PixelDraw.drawNotchedBox(canvas, 0, 0, w, h, u, palette.well, bdr, framePaint);

        Bitmap target = pixelMode ? (pixelBitmap != null ? pixelBitmap : nativeBitmap) : nativeBitmap;
        if (target != null && !target.isRecycled()) {
            srcRect.set(0, 0, target.getWidth(), target.getHeight());
            dstRect.set(u * 2, u * 2, w - u * 2, h - u * 2);
            bmpPaint.setFilterBitmap(!pixelMode);
            bmpPaint.setAntiAlias(!pixelMode);
            canvas.drawBitmap(target, srcRect, dstRect, bmpPaint);
        } else {
            int sc = Math.max(1, (w - u * 6) / 14);
            int sz = 14 * sc;
            PixelSpriteAtlas.drawSprite(
                canvas,
                PixelSpriteAtlas.DOCK_GUARD,
                (w - sz) / 2,
                (h - sz) / 2,
                sc,
                palette.inkMuted,
                palette.accent,
                framePaint
            );
        }
    }
}
