package io.github.taro902.isolationpolicy.ui.pixel;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.LruCache;

public final class PixelDraw {

    private static final int[] BAYER_4X4 = {
        0, 8, 2, 10,
        12, 4, 14, 6,
        3, 11, 1, 9,
        15, 7, 13, 5
    };

    private static final LruCache<Long, BitmapShader> SHADER_CACHE = new LruCache<Long, BitmapShader>(32);

    private PixelDraw() {
    }

    public static Paint newPixelPaint() {
        Paint p = new Paint();
        p.setAntiAlias(false);
        p.setFilterBitmap(false);
        p.setDither(false);
        p.setStyle(Paint.Style.FILL);
        return p;
    }

    public static BitmapShader getBayerShader(int bgColor, int dotColor, int level, int u) {
        int clampedLevel = Math.max(1, Math.min(15, level));
        int cell = Math.max(1, Math.min(4, u));
        long key = (((long) (bgColor & 0xFFFFFF)) << 36)
            | (((long) (dotColor & 0xFFFFFF)) << 12)
            | ((long) (clampedLevel & 0xF) << 4)
            | (long) (cell & 0xF);
        BitmapShader cached = SHADER_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        int dim = 4 * cell;
        Bitmap bmp = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[dim * dim];
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int c = BAYER_4X4[y * 4 + x] < clampedLevel ? dotColor : bgColor;
                for (int dy = 0; dy < cell; dy++) {
                    int rowOff = (y * cell + dy) * dim + x * cell;
                    for (int dx = 0; dx < cell; dx++) {
                        pixels[rowOff + dx] = c;
                    }
                }
            }
        }
        bmp.setPixels(pixels, 0, dim, 0, 0, dim, dim);
        BitmapShader shader = new BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        SHADER_CACHE.put(key, shader);
        return shader;
    }

    public static void drawBayerFill(
        Canvas canvas,
        int left,
        int top,
        int right,
        int bottom,
        int bgColor,
        int dotColor,
        int level,
        int u,
        Paint paint
    ) {
        if (right <= left || bottom <= top) {
            return;
        }
        BitmapShader shader = getBayerShader(bgColor, dotColor, level, u);
        paint.setShader(shader);
        paint.setColor(0xFFFFFFFF);
        canvas.drawRect(left, top, right, bottom, paint);
        paint.setShader(null);
    }

    public static void drawNotchedBox(
        Canvas canvas,
        int left,
        int top,
        int right,
        int bottom,
        int u,
        int fillColor,
        int borderColor,
        Paint paint
    ) {
        if (right - left < u * 4 || bottom - top < u * 4) {
            paint.setColor(fillColor);
            canvas.drawRect(left, top, right, bottom, paint);
            return;
        }
        paint.setColor(fillColor);
        canvas.drawRect(left + u, top + u, right - u, bottom - u, paint);

        paint.setColor(borderColor);
        canvas.drawRect(left + u, top, right - u, top + u, paint);
        canvas.drawRect(left + u, bottom - u, right - u, bottom, paint);
        canvas.drawRect(left, top + u, left + u, bottom - u, paint);
        canvas.drawRect(right - u, top + u, right, bottom - u, paint);
    }

    public static void drawCard(
        Canvas canvas,
        int left,
        int top,
        int right,
        int bottom,
        int u,
        PixelPalette pal,
        boolean highlighted,
        Paint paint
    ) {
        int r = right - u;
        int b = bottom - u;
        if (r - left < u * 4 || b - top < u * 4) {
            return;
        }
        paint.setColor(pal.shadow);
        canvas.drawRect(left + u, top + u * 2, right, bottom, paint);
        canvas.drawRect(left + u * 2, b, right - u, bottom, paint);

        int bdr = highlighted ? pal.accent : pal.border;
        drawNotchedBox(canvas, left, top, r, b, u, pal.surface, bdr, paint);

        paint.setColor(highlighted ? pal.accent : pal.borderHi);
        canvas.drawRect(left + u * 2, top + u, r - u * 2, top + u * 2, paint);
    }

    public static void drawRecessedWell(
        Canvas canvas,
        int left,
        int top,
        int right,
        int bottom,
        int u,
        PixelPalette pal,
        Paint paint
    ) {
        drawNotchedBox(canvas, left, top, right, bottom, u, pal.well, pal.border, paint);
        paint.setColor(pal.shadow);
        canvas.drawRect(left + u, top + u, right - u, top + u * 2, paint);
    }

    public static void drawLed(
        Canvas canvas,
        int x,
        int y,
        int size,
        int u,
        int ledColor,
        int borderColor,
        Paint paint
    ) {
        paint.setColor(borderColor);
        canvas.drawRect(x, y, x + size, y + size, paint);
        if (size > u * 2) {
            paint.setColor(ledColor);
            canvas.drawRect(x + u, y + u, x + size - u, y + size - u, paint);
            paint.setColor(0x66FFFFFF);
            canvas.drawRect(x + u, y + u, x + u * 2, y + u * 2, paint);
        }
    }

    public static void drawDivider(
        Canvas canvas,
        int left,
        int y,
        int right,
        int u,
        int color,
        Paint paint
    ) {
        paint.setColor(color);
        canvas.drawRect(left, y, right, y + u, paint);
    }
}
