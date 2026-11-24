package io.github.taro902.isolationpolicy.ui.pixel;

import android.graphics.Canvas;
import android.graphics.Paint;

public final class PixelFont {

    public static final int GLYPH_W = 5;
    public static final int GLYPH_H = 7;
    public static final int ADVANCE_X = 6;

    private static final long[] GLYPHS_5X7 = {
        0x000000000L, 0x100421084L, 0x00000014AL, 0x015F52BEAL, 0x13C5751E4L,
        0x007341359L, 0x3652AB24CL, 0x000000084L, 0x088842082L, 0x208210888L,
        0x009575480L, 0x0084F9080L, 0x208400000L, 0x0000F8000L, 0x318000000L,
        0x410820841L, 0x3A39ACE2EL, 0x388421184L, 0x7E083062EL, 0x3A213062EL,
        0x085F928C2L, 0x3A210FA1FL, 0x3A318FA0EL, 0x21082083FL, 0x3A317462EL,
        0x38217C62EL, 0x018C03180L, 0x208C03180L, 0x088882082L, 0x001F07C00L,
        0x208208888L, 0x10043062EL, 0x3A17ADE2EL, 0x4631FC62EL, 0x7A31F463EL,
        0x3A308422EL, 0x7A318C63EL, 0x7E10F421FL, 0x4210F421FL, 0x3E319C22EL,
        0x4631FC631L, 0x38842108EL, 0x325210847L, 0x4654C5251L, 0x7E1084210L,
        0x4631AD771L, 0x46319D731L, 0x3A318C62EL, 0x4210F463EL, 0x36558C62EL,
        0x4654F463EL, 0x78217420FL, 0x10842109FL, 0x3A318C631L, 0x11518C631L,
        0x4775AC631L, 0x462A22A31L, 0x108422A31L, 0x7E082083FL, 0x39084210EL,
        0x044222110L, 0x38421084EL, 0x000004544L, 0x7C0000000L, 0x000000088L,
        0x3E2F0B800L, 0x7A318FA10L, 0x3A3083800L, 0x3E318BC21L, 0x3A1F8B800L,
        0x2108E2126L, 0x382F8C5E0L, 0x46318FA10L, 0x388423004L, 0x324211802L,
        0x4A98A4A10L, 0x38842108CL, 0x46B5AE800L, 0x46318F800L, 0x3A318B800L,
        0x421E8C7C0L, 0x042F8C5E0L, 0x4210CD800L, 0x782E83C00L, 0x192847908L,
        0x36718C400L, 0x11518C400L, 0x2AB58C400L, 0x454454400L, 0x382F8C620L,
        0x7D0417C00L, 0x088441082L, 0x108421084L, 0x208411088L, 0x000015500L
    };

    private PixelFont() {
    }

    public static int measureWidth(String text, int scale) {
        if (text == null || text.isEmpty() || scale <= 0) {
            return 0;
        }
        return (text.length() * ADVANCE_X - 1) * scale;
    }

    public static int lineHeight(int scale) {
        return GLYPH_H * scale;
    }

    public static int measureMicroWidth(String text, int scale) {
        return measureWidth(text, scale);
    }

    public static int microHeight(int scale) {
        return lineHeight(scale);
    }

    public static String fitText(String text, int maxPx, int scale) {
        if (text == null || text.isEmpty() || scale <= 0 || maxPx <= 0) {
            return "";
        }
        if (measureWidth(text, scale) <= maxPx) {
            return text;
        }
        int step = ADVANCE_X * scale;
        int maxChars = (maxPx + scale) / step;
        if (maxChars <= 1) {
            return text.substring(0, 1);
        }
        if (maxChars <= 3) {
            return text.substring(0, Math.min(text.length(), maxChars));
        }
        return text.substring(0, Math.min(text.length(), maxChars - 2)) + "..";
    }

    public static String fitMicro(String text, int maxPx, int scale) {
        return fitText(text, maxPx, scale);
    }

    public static void drawText(Canvas canvas, String text, int x, int y, int scale, int color, Paint paint) {
        if (canvas == null || text == null || text.isEmpty() || scale <= 0 || paint == null) {
            return;
        }
        paint.setColor(color);
        int cursorX = x;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            long bits = glyph5x7(text.charAt(i));
            if (bits != 0L) {
                for (int row = 0; row < GLYPH_H; row++) {
                    int rowBits = (int) ((bits >>> (row * 5)) & 0x1FL);
                    if (rowBits == 0) {
                        continue;
                    }
                    int py = y + row * scale;
                    int col = 0;
                    while (col < GLYPH_W) {
                        if ((rowBits & (1 << (4 - col))) != 0) {
                            int start = col;
                            while (col + 1 < GLYPH_W && (rowBits & (1 << (4 - (col + 1)))) != 0) {
                                col++;
                            }
                            canvas.drawRect(
                                cursorX + start * scale,
                                py,
                                cursorX + (col + 1) * scale,
                                py + scale,
                                paint
                            );
                        }
                        col++;
                    }
                }
            }
            cursorX += ADVANCE_X * scale;
        }
    }

    public static void drawTextWithShadow(
        Canvas canvas,
        String text,
        int x,
        int y,
        int scale,
        int color,
        int shadowColor,
        Paint paint
    ) {
        int off = Math.max(2, scale / 2);
        drawText(canvas, text, x + off, y + off, scale, shadowColor, paint);
        drawText(canvas, text, x, y, scale, color, paint);
    }

    public static void drawTextCentered(
        Canvas canvas,
        String text,
        int left,
        int top,
        int right,
        int bottom,
        int scale,
        int color,
        Paint paint
    ) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int tw = measureWidth(text, scale);
        int th = lineHeight(scale);
        int x = left + ((right - left - tw) / 2);
        int y = top + ((bottom - top - th) / 2);
        drawText(canvas, text, x, y, scale, color, paint);
    }

    public static void drawMicro(Canvas canvas, String text, int x, int y, int scale, int color, Paint paint) {
        drawText(canvas, text, x, y, scale, color, paint);
    }

    public static void drawMicroCentered(
        Canvas canvas,
        String text,
        int left,
        int top,
        int right,
        int bottom,
        int scale,
        int color,
        Paint paint
    ) {
        drawTextCentered(canvas, text, left, top, right, bottom, scale, color, paint);
    }

    public static long glyph5x7(char c) {
        if (c >= 32 && c <= 126) {
            return GLYPHS_5X7[c - 32];
        }
        return GLYPHS_5X7['?' - 32];
    }

    public static int glyphRowBits(char c, int row) {
        if (row < 0 || row >= GLYPH_H) {
            return 0;
        }
        return (int) ((glyph5x7(c) >>> (row * 5)) & 0x1FL);
    }
}
