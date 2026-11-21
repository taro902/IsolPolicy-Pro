package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Typeface;

public final class PixelTheme {

    public static final int MODE_OBSIDIAN = 0;
    public static final int MODE_PAPER = 1;

    public final int mode;
    public final float density;
    public final int px;

    public final int bgCanvas;
    public final int bgPanel;
    public final int bgCard;
    public final int bgCardActive;
    public final int bgInput;
    public final int bevelLight;
    public final int bevelDark;
    public final int borderOuter;
    public final int divider;

    public final int inkPrimary;
    public final int inkSecondary;
    public final int inkMuted;

    public final int accentOk;
    public final int accentOkBg;
    public final int accentWarn;
    public final int accentWarnBg;
    public final int accentDanger;
    public final int accentDangerBg;
    public final int accentInfo;
    public final int accentInfoBg;
    public final int accentFocus;
    public final int accentFocusBg;

    public final int chipOffBg;
    public final int chipOffInk;

    public final Typeface monoRegular;
    public final Typeface monoBold;

    public PixelTheme(Context context, int mode) {
        this.mode = mode;
        this.density = context.getResources().getDisplayMetrics().density;
        this.px = Math.max(2, Math.round(density * 1.5f));
        this.monoRegular = Typeface.MONOSPACE;
        this.monoBold = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);

        if (mode == MODE_PAPER) {
            bgCanvas = 0xFFE6E4DA;
            bgPanel = 0xFFF2F0E8;
            bgCard = 0xFFF9F8F2;
            bgCardActive = 0xFFECEAD9;
            bgInput = 0xFFDDDAD0;
            bevelLight = 0xFFFFFFFF;
            bevelDark = 0xFFB5B09E;
            borderOuter = 0xFF8C8776;
            divider = 0xFFCBC7B8;

            inkPrimary = 0xFF14181F;
            inkSecondary = 0xFF3E4652;
            inkMuted = 0xFF6B7280;

            accentOk = 0xFF15803D;
            accentOkBg = 0xFFD6F1DF;
            accentWarn = 0xFFB45309;
            accentWarnBg = 0xFFFAE6C3;
            accentDanger = 0xFFB91C1C;
            accentDangerBg = 0xFFF9D8D8;
            accentInfo = 0xFF0369A1;
            accentInfoBg = 0xFFD4ECF9;
            accentFocus = 0xFF6D28D9;
            accentFocusBg = 0xFFE6DCFB;

            chipOffBg = 0xFFE2E0D6;
            chipOffInk = 0xFF5C636E;
        } else {
            bgCanvas = 0xFF0D0F12;
            bgPanel = 0xFF14181F;
            bgCard = 0xFF1A2029;
            bgCardActive = 0xFF212936;
            bgInput = 0xFF101318;
            bevelLight = 0xFF323C4D;
            bevelDark = 0xFF06080B;
            borderOuter = 0xFF283140;
            divider = 0xFF222A36;

            inkPrimary = 0xFFE6ECF5;
            inkSecondary = 0xFF94A3B8;
            inkMuted = 0xFF5D6B80;

            accentOk = 0xFF4ADE80;
            accentOkBg = 0xFF122C1E;
            accentWarn = 0xFFFBBF24;
            accentWarnBg = 0xFF2E2410;
            accentDanger = 0xFFF87171;
            accentDangerBg = 0xFF301519;
            accentInfo = 0xFF38BDF8;
            accentInfoBg = 0xFF112634;
            accentFocus = 0xFFA78BFA;
            accentFocusBg = 0xFF231B36;

            chipOffBg = 0xFF11151C;
            chipOffInk = 0xFF64748B;
        }
    }

    public int u(int units) {
        return units * px;
    }

    public int dp(float dp) {
        return Math.round(dp * density);
    }

    public boolean isDark() {
        return mode == MODE_OBSIDIAN;
    }
}