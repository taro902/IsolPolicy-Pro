package io.github.taro902.isolationpolicy.ui.pixel;

public final class PixelPalette {

    public static final int MODE_CRT = 0;
    public static final int MODE_PAPER = 1;
    public static final int MODE_AMBER = 2;
    public static final int MODE_POCKET = 3;
    public static final int MODE_SLATE = 4;
    public static final int MODE_SEPIA = 5;
    public static final int MODE_COUNT = 6;

    public final int mode;
    public final String name;
    public final String shortName;
    public final boolean isLight;
    public final int bg;
    public final int surface;
    public final int surfaceAlt;
    public final int well;
    public final int border;
    public final int borderHi;
    public final int shadow;
    public final int ink;
    public final int inkMuted;
    public final int inkOnAccent;
    public final int accent;
    public final int accentDim;
    public final int warn;
    public final int danger;
    public final int audit;
    public final int keyFace;
    public final int keyLip;
    public final int ditherDot;

    private PixelPalette(
        int mode,
        String name,
        String shortName,
        boolean isLight,
        int bg,
        int surface,
        int surfaceAlt,
        int well,
        int border,
        int borderHi,
        int shadow,
        int ink,
        int inkMuted,
        int inkOnAccent,
        int accent,
        int accentDim,
        int warn,
        int danger,
        int audit,
        int keyFace,
        int keyLip,
        int ditherDot
    ) {
        this.mode = mode;
        this.name = name;
        this.shortName = shortName;
        this.isLight = isLight;
        this.bg = bg;
        this.surface = surface;
        this.surfaceAlt = surfaceAlt;
        this.well = well;
        this.border = border;
        this.borderHi = borderHi;
        this.shadow = shadow;
        this.ink = ink;
        this.inkMuted = inkMuted;
        this.inkOnAccent = inkOnAccent;
        this.accent = accent;
        this.accentDim = accentDim;
        this.warn = warn;
        this.danger = danger;
        this.audit = audit;
        this.keyFace = keyFace;
        this.keyLip = keyLip;
        this.ditherDot = ditherDot;
    }

    private static final PixelPalette[] PALETTES = {
        new PixelPalette(
            MODE_CRT,
            "CRT PHOSPHOR",
            "CRT",
            false,
            0xFF0E1311,
            0xFF151D19,
            0xFF1B2620,
            0xFF0A0F0D,
            0xFF2E473A,
            0xFF456B57,
            0xFF060908,
            0xFFE2F2E8,
            0xFF98B8A6,
            0xFF09140E,
            0xFF4CE08A,
            0xFF1D4D33,
            0xFFF0C040,
            0xFFFF6B6B,
            0xFF5CC8FF,
            0xFF1E2A24,
            0xFF0D1410,
            0xFF24382E
        ),
        new PixelPalette(
            MODE_PAPER,
            "DMG PAPER",
            "PAPER",
            true,
            0xFFEDE9DC,
            0xFFE4DFCE,
            0xFFD9D3C0,
            0xFFF5F2E8,
            0xFF4F5952,
            0xFF28302B,
            0xFF9E9886,
            0xFF1A211D,
            0xFF48544D,
            0xFFF5F2E8,
            0xFF15653A,
            0xFFB8D4C2,
            0xFF8A5200,
            0xFFA82020,
            0xFF1B5496,
            0xFFEEEADF,
            0xFFAFA996,
            0xFFC8C2B0
        ),
        new PixelPalette(
            MODE_AMBER,
            "AMBER CRT",
            "AMBER",
            false,
            0xFF14100C,
            0xFF1E1812,
            0xFF271F17,
            0xFF0D0A07,
            0xFF59432C,
            0xFF85623E,
            0xFF080604,
            0xFFFCE8C8,
            0xFFC4A478,
            0xFF1A1107,
            0xFFF0A830,
            0xFF543A14,
            0xFFFFE066,
            0xFFFF705C,
            0xFF66C7E8,
            0xFF2C2319,
            0xFF120E09,
            0xFF3A2D20
        ),
        new PixelPalette(
            MODE_POCKET,
            "POCKET OLIVE",
            "OLIVE",
            true,
            0xFFCADC9F,
            0xFFBFD291,
            0xFFB2C682,
            0xFFD4E4AE,
            0xFF2B522B,
            0xFF0F380F,
            0xFF829958,
            0xFF0F380F,
            0xFF264D26,
            0xFFD4E4AE,
            0xFF144514,
            0xFF9BB870,
            0xFF4A4808,
            0xFF6E2012,
            0xFF134252,
            0xFFCDE0A3,
            0xFF8EA362,
            0xFFA6BA78
        ),
        new PixelPalette(
            MODE_SLATE,
            "SLATE TOKYO",
            "SLATE",
            false,
            0xFF11151C,
            0xFF19202B,
            0xFF212A38,
            0xFF0C0F14,
            0xFF36455C,
            0xFF50678A,
            0xFF07090D,
            0xFFE4ECF7,
            0xFF95A4BA,
            0xFF0A111A,
            0xFF5CC8FF,
            0xFF1E3C54,
            0xFFF2B94B,
            0xFFFF6B7A,
            0xFFB894FF,
            0xFF242E3E,
            0xFF10151E,
            0xFF2B374A
        ),
        new PixelPalette(
            MODE_SEPIA,
            "SEPIA TERM",
            "SEPIA",
            true,
            0xFFF4ECE1,
            0xFFEAE0CE,
            0xFFDFD3BD,
            0xFFFAF4EB,
            0xFF6B5B4A,
            0xFF3E3125,
            0xFFB5A48C,
            0xFF281E15,
            0xFF5A4B3C,
            0xFFFAF4EB,
            0xFF22603C,
            0xFFC4D6C4,
            0xFF88480A,
            0xFFA62B24,
            0xFF28508C,
            0xFFF2E9DC,
            0xFFBBA990,
            0xFFD4C6B0
        )
    };

    public static PixelPalette of(int mode) {
        int safe = ((mode % MODE_COUNT) + MODE_COUNT) % MODE_COUNT;
        return PALETTES[safe];
    }

    public static int nextMode(int current) {
        return (current + 1) % MODE_COUNT;
    }
}
