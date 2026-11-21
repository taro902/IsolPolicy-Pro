package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;

public final class PixelIcons {

    public static final int[] SHIELD_10 = {
            0b0011111100,
            0b0111111110,
            0b1100110011,
            0b1100110011,
            0b1111111111,
            0b1100110011,
            0b0110110110,
            0b0011111100,
            0b0001111000,
            0b0000110000
    };

    public static final int[] SCOPE_10 = {
            0b0000000000,
            0b0000100000,
            0b0000110000,
            0b0001010000,
            0b1111001011,
            0b0000001010,
            0b0000001100,
            0b0000001000,
            0b1111111111,
            0b0000000000
    };

    public static final int[] RADAR_10 = {
            0b0011111100,
            0b0100000010,
            0b1001111001,
            0b1010001101,
            0b1010110101,
            0b1010110101,
            0b1010000101,
            0b1001111001,
            0b0100000010,
            0b0011111100
    };

    public static final int[] VAULT_10 = {
            0b1111111111,
            0b1000000001,
            0b1011111101,
            0b1010000101,
            0b1010110101,
            0b1010110101,
            0b1010000101,
            0b1011111101,
            0b1000000001,
            0b1111111111
    };

    public static final int[] BOLT_8 = {
            0b00011100,
            0b00111000,
            0b01110000,
            0b11111110,
            0b00011100,
            0b00111000,
            0b01100000,
            0b11000000
    };

    public static final int[] WARN_8 = {
            0b00011000,
            0b00111100,
            0b00100100,
            0b01100110,
            0b01100110,
            0b11111111,
            0b11100111,
            0b11111111
    };

    public static final int[] LOCK_8 = {
            0b00111100,
            0b01000010,
            0b01000010,
            0b11111111,
            0b11100111,
            0b11100111,
            0b11111111,
            0b11111111
    };

    public static final int[] CHECK_8 = {
            0b00000000,
            0b00000001,
            0b00000011,
            0b10000110,
            0b11001100,
            0b01111000,
            0b00110000,
            0b00000000
    };

    public static final int[] REFRESH_8 = {
            0b00111110,
            0b01000011,
            0b10000001,
            0b10000000,
            0b00000001,
            0b10000001,
            0b11000010,
            0b01111100
    };

    public static final int[] TRASH_8 = {
            0b00111100,
            0b11111111,
            0b01000010,
            0b01011010,
            0b01011010,
            0b01011010,
            0b01000010,
            0b01111110
    };

    private PixelIcons() {
    }

    public static void drawBitmask(
            Canvas canvas,
            int[] rows,
            int gridSize,
            float left,
            float top,
            int pixelSize,
            Paint paint
    ) {
        if (rows == null || rows.length == 0) {
            return;
        }
        int rowCount = Math.min(rows.length, gridSize);
        for (int r = 0; r < rowCount; r++) {
            int bits = rows[r];
            if (bits == 0) {
                continue;
            }
            float y = top + (r * pixelSize);
            for (int c = 0; c < gridSize; c++) {
                int mask = 1 << (gridSize - 1 - c);
                if ((bits & mask) != 0) {
                    float x = left + (c * pixelSize);
                    canvas.drawRect(x, y, x + pixelSize, y + pixelSize, paint);
                }
            }
        }
    }

    public static Bitmap renderBitmap(int[] rows, int gridSize, int pixelSize, int color) {
        int dim = gridSize * pixelSize;
        Bitmap bmp = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint();
        p.setAntiAlias(false);
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        drawBitmask(c, rows, gridSize, 0f, 0f, pixelSize, p);
        return bmp;
    }

    public static BitmapDrawable createDrawable(Resources res, int[] rows, int gridSize, int pixelSize, int color) {
        Bitmap bmp = renderBitmap(rows, gridSize, pixelSize, color);
        BitmapDrawable d = new BitmapDrawable(res, bmp);
        d.setFilterBitmap(false);
        d.setAntiAlias(false);
        return d;
    }
}