package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Shader;
import android.graphics.Typeface;

public final class PixelPalette {

    public static final int MODE_CRT = 0;
    public static final int MODE_PAPER = 1;
    public static final int MODE_AMBER = 2;
    public static final int MODE_POCKET = 3;

    public final int mode;
    public final String name;
    public final float density;
    public final int px;
    public final int fontScale;
    public final int smallFontScale;
    public final int titleFontScale;

    public final int bgCanvas;
    public final int bgPanel;
    public final int bgCard;
    public final int bgCardGuarded;
    public final int bgInset;
    public final int bgDitherDot;

    public final int outline;
    public final int shadow;
    public final int highlight;
    public final int shade;
    public final int divider;

    public final int inkPrimary;
    public final int inkSecondary;
    public final int inkMuted;

    public final int ok;
    public final int okBg;
    public final int okLip;
    public final int info;
    public final int infoBg;
    public final int infoLip;
    public final int warn;
    public final int warnBg;
    public final int warnLip;
    public final int danger;
    public final int dangerBg;
    public final int dangerLip;
    public final int focus;
    public final int focusBg;
    public final int focusLip;

    public final int keyOffBg;
    public final int keyOffLip;
    public final int keyOffInk;
    public final int ledOff;
    public final int ledSpec;

    public final Typeface monoRegular;
    public final Typeface monoBold;
    public final BitmapShader ditherShader;
    public final BitmapShader railShader;

    public PixelPalette(Context context, int mode) {
        this.mode = (mode >= 0 && mode <= 3) ? mode : MODE_CRT;
        this.density = context.getResources().getDisplayMetrics().density;
        this.px = Math.max(2, Math.round(density * 1.45f));
        this.fontScale = Math.max(2, Math.round(density * 0.95f));
        this.smallFontScale = Math.max(2, Math.round(density * 0.75f));
        this.titleFontScale = Math.max(3, Math.round(density * 1.35f));
        this.monoRegular = Typeface.MONOSPACE;
        this.monoBold = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);

        if (this.mode == MODE_PAPER) {
            name = "DMG PAPER";
            bgCanvas = 0xFFE7E3D4;
            bgPanel = 0xFFF2EFE4;
            bgCard = 0xFFFAF8F0;
            bgCardGuarded = 0xFFEBF2E4;
            bgInset = 0xFFDBD6C5;
            bgDitherDot = 0xFFD0CBB9;

            outline = 0xFF2B2F36;
            shadow = 0xFFB8B29F;
            highlight = 0xFFFFFFFF;
            shade = 0xFFC8C2B0;
            divider = 0xFFCFCAB8;

            inkPrimary = 0xFF171A1F;
            inkSecondary = 0xFF424954;
            inkMuted = 0xFF727882;

            ok = 0xFF1A6B3C;
            okBg = 0xFFD2ECD9;
            okLip = 0xFF114828;
            info = 0xFF1E56A0;
            infoBg = 0xFFD4E5F7;
            infoLip = 0xFF143A6E;
            warn = 0xFFA15C13;
            warnBg = 0xFFF7E5C8;
            warnLip = 0xFF6E3D0B;
            danger = 0xFFA62C2B;
            dangerBg = 0xFFF6D6D5;
            dangerLip = 0xFF701D1C;
            focus = 0xFF543C8A;
            focusBg = 0xFFE4DCF5;
            focusLip = 0xFF392761;

            keyOffBg = 0xFFE5E1D2;
            keyOffLip = 0xFFB5AF9B;
            keyOffInk = 0xFF4E5560;
            ledOff = 0xFFBDB8A6;
            ledSpec = 0xFFFFFFFF;
        } else if (this.mode == MODE_AMBER) {
            name = "AMBER CRT";
            bgCanvas = 0xFF110D08;
            bgPanel = 0xFF1B140C;
            bgCard = 0xFF231A10;
            bgCardGuarded = 0xFF2F2212;
            bgInset = 0xFF0C0905;
            bgDitherDot = 0xFF2D2113;

            outline = 0xFF755220;
            shadow = 0xFF060402;
            highlight = 0xFF9C6D2B;
            shade = 0xFF140F09;
            divider = 0xFF3A2914;

            inkPrimary = 0xFFFFC14D;
            inkSecondary = 0xFFC78F32;
            inkMuted = 0xFF7D5A22;

            ok = 0xFFFFB000;
            okBg = 0xFF382609;
            okLip = 0xFF8C5D00;
            info = 0xFFFFD166;
            infoBg = 0xFF33260F;
            infoLip = 0xFF8C6E29;
            warn = 0xFFFF922B;
            warnBg = 0xFF3B200B;
            warnLip = 0xFF944C0E;
            danger = 0xFFFF6B35;
            dangerBg = 0xFF3D190D;
            dangerLip = 0xFF8F3314;
            focus = 0xFFE8A838;
            focusBg = 0xFF30220C;
            focusLip = 0xFF7A5618;

            keyOffBg = 0xFF19120B;
            keyOffLip = 0xFF0B0804;
            keyOffInk = 0xFF8F692A;
            ledOff = 0xFF3B2A14;
            ledSpec = 0xFFFFF0C2;
        } else if (this.mode == MODE_POCKET) {
            name = "POCKET OLIVE";
            bgCanvas = 0xFFCADC9F;
            bgPanel = 0xFFBDCE94;
            bgCard = 0xFFD4E5AB;
            bgCardGuarded = 0xFFB0C882;
            bgInset = 0xFF9BBC0F;
            bgDitherDot = 0xFF8BAC0F;

            outline = 0xFF0F380F;
            shadow = 0xFF306230;
            highlight = 0xFFE2F0BC;
            shade = 0xFF8BAC0F;
            divider = 0xFF8BAC0F;

            inkPrimary = 0xFF0F380F;
            inkSecondary = 0xFF1E4B1E;
            inkMuted = 0xFF306230;

            ok = 0xFF0F380F;
            okBg = 0xFF9BBC0F;
            okLip = 0xFF0F380F;
            info = 0xFF0F380F;
            infoBg = 0xFF9BBC0F;
            infoLip = 0xFF0F380F;
            warn = 0xFF0F380F;
            warnBg = 0xFF8BAC0F;
            warnLip = 0xFF0F380F;
            danger = 0xFF0F380F;
            dangerBg = 0xFF8BAC0F;
            dangerLip = 0xFF0F380F;
            focus = 0xFF0F380F;
            focusBg = 0xFF9BBC0F;
            focusLip = 0xFF0F380F;

            keyOffBg = 0xFFBDCE94;
            keyOffLip = 0xFF306230;
            keyOffInk = 0xFF306230;
            ledOff = 0xFF8BAC0F;
            ledSpec = 0xFFD4E5AB;
        } else {
            name = "CRT PHOSPHOR";
            bgCanvas = 0xFF0B0E14;
            bgPanel = 0xFF131822;
            bgCard = 0xFF1A2130;
            bgCardGuarded = 0xFF172726;
            bgInset = 0xFF090C11;
            bgDitherDot = 0xFF1F2838;

            outline = 0xFF36455E;
            shadow = 0xFF040608;
            highlight = 0xFF475A7A;
            shade = 0xFF0E131B;
            divider = 0xFF253042;

            inkPrimary = 0xFFE6EEF8;
            inkSecondary = 0xFF94A6BF;
            inkMuted = 0xFF5D6E87;

            ok = 0xFF4AE38A;
            okBg = 0xFF112D21;
            okLip = 0xFF1C6B41;
            info = 0xFF4CC2FF;
            infoBg = 0xFF10283B;
            infoLip = 0xFF1C5E85;
            warn = 0xFFF5B742;
            warnBg = 0xFF30240E;
            warnLip = 0xFF87611B;
            danger = 0xFFF25C6B;
            dangerBg = 0xFF33141B;
            dangerLip = 0xFF852631;
            focus = 0xFFA78BFA;
            focusBg = 0xFF221A3B;
            focusLip = 0xFF554094;

            keyOffBg = 0xFF131924;
            keyOffLip = 0xFF090C12;
            keyOffInk = 0xFF72839C;
            ledOff = 0xFF273245;
            ledSpec = 0xFFFFFFFF;
        }

        ditherShader = createBayerShader(bgPanel, bgDitherDot, Math.max(2, px / 2));
        railShader = createBayerShader(okBg, ok, Math.max(2, px / 2));
    }

    public int u(int units) {
        return units * px;
    }

    public int dp(float dp) {
        return Math.round(dp * density);
    }

    public boolean isDark() {
        return mode == MODE_CRT || mode == MODE_AMBER;
    }

    public static String nameForMode(int m) {
        switch (m) {
            case MODE_PAPER:
                return "DMG PAPER";
            case MODE_AMBER:
                return "AMBER CRT";
            case MODE_POCKET:
                return "POCKET OLIVE";
            default:
                return "CRT PHOSPHOR";
        }
    }

    public static int[] swatchForMode(int m) {
        switch (m) {
            case MODE_PAPER:
                return new int[]{0xFFE7E3D4, 0xFF1A6B3C, 0xFF1E56A0, 0xFFA15C13};
            case MODE_AMBER:
                return new int[]{0xFF110D08, 0xFFFFB000, 0xFFFF922B, 0xFF755220};
            case MODE_POCKET:
                return new int[]{0xFFCADC9F, 0xFF9BBC0F, 0xFF306230, 0xFF0F380F};
            default:
                return new int[]{0xFF0B0E14, 0xFF4AE38A, 0xFF4CC2FF, 0xFFF5B742};
        }
    }

    private static BitmapShader createBayerShader(int baseColor, int dotColor, int cellPx) {
        int c = Math.max(2, cellPx);
        int dim = c * 2;
        Bitmap bmp = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < dim; y++) {
            for (int x = 0; x < dim; x++) {
                boolean isDot = (x < c && y < c);
                bmp.setPixel(x, y, isDot ? dotColor : baseColor);
            }
        }
        return new BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
    }
}
