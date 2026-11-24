package io.github.taro902.isolationpolicy.ui.pixel;

import android.graphics.Canvas;
import android.graphics.Paint;

public final class PixelFont {

    public static final int GLYPH_W = 5;
    public static final int GLYPH_H = 7;
    public static final int ADVANCE_X = 6;

    public static final int MICRO_W = 3;
    public static final int MICRO_H = 5;
    public static final int MICRO_ADVANCE_X = 4;

    private static final long[] GLYPHS_5X7 = {
        0x000000000L, 0x100842104L, 0x00000014AL, 0x00A7C53EAL, 0x13C53A9E4L,
        0x0E6421338L, 0x364A85288L, 0x000000084L, 0x088842082L, 0x208210888L,
        0x004547540L, 0x00421F080L, 0x208200000L, 0x00000F800L, 0x18C000000L,
        0x410820841L, 0x3A2CCAA2EL, 0x388421184L, 0x7E083062EL, 0x3A20B062EL,
        0x0843E92C2L, 0x3A2087A1FL, 0x3A28F4106L, 0x21082083FL, 0x3A28B462EL,
        0x3040BC62EL, 0x00C600CC0L, 0x208600CC0L, 0x088882082L, 0x000F803E0L,
        0x208208888L, 0x10023062EL, 0x3A0BD5E2EL, 0x4628FC62EL, 0x7A28F463EL,
        0x3A284222EL, 0x7248C625CL, 0x7E087421FL, 0x42087421FL, 0x3E28BC22EL,
        0x4628FC631L, 0x38842108EL, 0x324842047L, 0x464A62251L, 0x7E0842210L,
        0x4628AAA71L, 0x46289AA31L, 0x3A28C622EL, 0x4208F463EL, 0x364AA622EL,
        0x464AF463EL, 0x7820B820FL, 0x10842109FL, 0x3A28C6231L, 0x1148C6231L,
        0x46D556231L, 0x464A22A31L, 0x108422A31L, 0x7E082083FL, 0x39084210EL,
        0x044222110L, 0x38421084EL, 0x000004544L, 0x7C0000000L, 0x000000088L,
        0x3E2783800L, 0x7A28F4210L, 0x3A2843800L, 0x3E28BC021L, 0x3A08FC600L,
        0x210872126L, 0x3827C63C0L, 0x4628F4210L, 0x388423004L, 0x324841802L,
        0x4A4A64A10L, 0x38842108CL, 0x46AAA6800L, 0x4628C7800L, 0x3A28C3800L,
        0x420F463C0L, 0x0427C63C0L, 0x420846580L, 0x7820B83C0L, 0x192427908L,
        0x3668C6200L, 0x1148C6200L, 0x295546200L, 0x464A22A00L, 0x3827C6220L,
        0x7E0820BC0L, 0x088841082L, 0x108421084L, 0x208208888L, 0x0000012A0L
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
        if (text == null || text.isEmpty() || scale <= 0) {
            return 0;
        }
        return (text.length() * MICRO_ADVANCE_X - 1) * scale;
    }

    public static int microHeight(int scale) {
        return MICRO_H * scale;
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
        if (text == null || text.isEmpty() || scale <= 0 || maxPx <= 0) {
            return "";
        }
        if (measureMicroWidth(text, scale) <= maxPx) {
            return text;
        }
        int step = MICRO_ADVANCE_X * scale;
        int maxChars = (maxPx + scale) / step;
        if (maxChars <= 1) {
            return text.substring(0, 1);
        }
        return text.substring(0, Math.min(text.length(), maxChars));
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

    public static void drawTextCentered(Canvas canvas, String text, int left, int top, int right, int bottom, int scale, int color, Paint paint) {
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
        if (canvas == null || text == null || text.isEmpty() || scale <= 0 || paint == null) {
            return;
        }
        paint.setColor(color);
        int cursorX = x;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            int bits = glyph3x5(text.charAt(i));
            if (bits != 0) {
                for (int row = 0; row < MICRO_H; row++) {
                    int rowBits = (bits >>> (row * 3)) & 0x07;
                    if (rowBits == 0) {
                        continue;
                    }
                    int py = y + row * scale;
                    for (int col = 0; col < MICRO_W; col++) {
                        if ((rowBits & (1 << (2 - col))) != 0) {
                            int px = cursorX + col * scale;
                            canvas.drawRect(px, py, px + scale, py + scale, paint);
                        }
                    }
                }
            }
            cursorX += MICRO_ADVANCE_X * scale;
        }
    }

    public static void drawMicroCentered(Canvas canvas, String text, int left, int top, int right, int bottom, int scale, int color, Paint paint) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int tw = measureMicroWidth(text, scale);
        int th = microHeight(scale);
        int x = left + ((right - left - tw) / 2);
        int y = top + ((bottom - top - th) / 2);
        drawMicro(canvas, text, x, y, scale, color, paint);
    }

    public static long glyph5x7(char c) {
        if (c >= 32 && c <= 126) {
            return GLYPHS_5X7[c - 32];
        }
        return GLYPHS_5X7['?' - 32];
    }

    private static int glyph3x5(char c) {
        char up = (c >= 'a' && c <= 'z') ? (char) (c - 32) : c;
        switch (up) {
            case ' ': return 0x0000;
            case '%': return 0x588D;
            case '+': return 0x04F0;
            case '-': return 0x01C0;
            case '.': return 0x2000;
            case '/': return 0x4889;
            case '0': return 0x7B6F;
            case '1': return 0x749A;
            case '2': return 0x79CF;
            case '3': return 0x73CF;
            case '4': return 0x13ED;
            case '5': return 0x73E7;
            case '6': return 0x7BE7;
            case '7': return 0x248F;
            case '8': return 0x7BEF;
            case '9': return 0x73EF;
            case ':': return 0x0410;
            case '=': return 0x0E38;
            case 'A': return 0x5BEA;
            case 'B': return 0x6BAE;
            case 'C': return 0x7927;
            case 'D': return 0x6B6E;
            case 'E': return 0x79A7;
            case 'F': return 0x49A7;
            case 'G': return 0x7B67;
            case 'H': return 0x5BED;
            case 'I': return 0x7497;
            case 'J': return 0x7A49;
            case 'K': return 0x5BAD;
            case 'L': return 0x7924;
            case 'M': return 0x5BFD;
            case 'N': return 0x5FFD;
            case 'O': return 0x7B6F;
            case 'P': return 0x49EF;
            case 'Q': return 0x1F6F;
            case 'R': return 0x5BAE;
            case 'S': return 0x73E7;
            case 'T': return 0x2497;
            case 'U': return 0x7B6D;
            case 'V': return 0x2B6D;
            case 'W': return 0x5FED;
            case 'X': return 0x5AED;
            case 'Y': return 0x24AD;
            case 'Z': return 0x790F;
            case '_': return 0x7000;
            default: return 0;
        }
    }
}
