package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;

public final class PixelMetrics {

    public static final int SCALE_COMPACT = 0;
    public static final int SCALE_BALANCED = 1;
    public static final int SCALE_LARGE = 2;
    public static final int SCALE_COUNT = 3;

    public final float density;
    public final int scaleMode;
    public final int u;
    public final int border;
    public final int shadow;
    public final int padSm;
    public final int padMd;
    public final int padLg;
    public final int microScale;
    public final int smallFontScale;
    public final int fontScale;
    public final int titleFontScale;
    public final int keycapLip;

    private PixelMetrics(float density, int mode) {
        this.density = density > 0f ? density : 2.75f;
        this.scaleMode = clampScaleMode(mode);
        int baseU = Math.max(2, Math.min(4, Math.round(this.density * 1.12f)));
        if (this.scaleMode == SCALE_COMPACT) {
            this.u = baseU;
            this.microScale = Math.max(2, baseU - 1);
            this.smallFontScale = baseU;
            this.fontScale = baseU;
            this.titleFontScale = baseU + 1;
        } else if (this.scaleMode == SCALE_LARGE) {
            int largeU = Math.max(3, Math.min(5, Math.round(this.density * 1.45f)));
            this.u = largeU;
            this.microScale = Math.max(2, largeU - 1);
            this.smallFontScale = largeU;
            this.fontScale = largeU + 1;
            this.titleFontScale = largeU + 2;
        } else {
            this.u = baseU;
            this.microScale = baseU;
            this.smallFontScale = baseU;
            this.fontScale = baseU + 1;
            this.titleFontScale = baseU + 2;
        }
        this.border = this.u;
        this.shadow = this.u;
        this.padSm = this.u * 2;
        this.padMd = this.u * 3;
        this.padLg = this.u * 4;
        this.keycapLip = this.u * 2;
    }

    public static int clampScaleMode(int mode) {
        if (mode <= SCALE_COMPACT) {
            return SCALE_COMPACT;
        }
        if (mode >= SCALE_LARGE) {
            return SCALE_LARGE;
        }
        return SCALE_BALANCED;
    }

    public static String scaleLabel(int mode) {
        int m = clampScaleMode(mode);
        if (m == SCALE_COMPACT) {
            return "COMPACT";
        }
        if (m == SCALE_LARGE) {
            return "LARGE";
        }
        return "BALANCED";
    }

    public static PixelMetrics from(Context ctx) {
        return from(ctx, SCALE_BALANCED);
    }

    public static PixelMetrics from(Context ctx, int scaleMode) {
        float d = 2.75f;
        if (ctx != null && ctx.getResources() != null && ctx.getResources().getDisplayMetrics() != null) {
            d = ctx.getResources().getDisplayMetrics().density;
        }
        return new PixelMetrics(d, scaleMode);
    }

    public int dp(int dpVal) {
        return Math.round(dpVal * density);
    }

    public int keycapHeight(boolean compact) {
        if (scaleMode == SCALE_COMPACT) {
            return dp(compact ? 34 : 38);
        }
        if (scaleMode == SCALE_LARGE) {
            return dp(compact ? 42 : 46);
        }
        return dp(compact ? 36 : 40);
    }

    public int headerHeight() {
        if (scaleMode == SCALE_COMPACT) {
            return dp(52);
        }
        if (scaleMode == SCALE_LARGE) {
            return dp(68);
        }
        return dp(58);
    }

    public int dockHeight() {
        if (scaleMode == SCALE_COMPACT) {
            return dp(50);
        }
        if (scaleMode == SCALE_LARGE) {
            return dp(64);
        }
        return dp(56);
    }

    public int iconSize() {
        if (scaleMode == SCALE_COMPACT) {
            return dp(38);
        }
        if (scaleMode == SCALE_LARGE) {
            return dp(48);
        }
        return dp(42);
    }

    public int scopeHeight() {
        if (scaleMode == SCALE_COMPACT) {
            return dp(124);
        }
        if (scaleMode == SCALE_LARGE) {
            return dp(158);
        }
        return dp(138);
    }

    public int heatmapHeight() {
        if (scaleMode == SCALE_COMPACT) {
            return dp(96);
        }
        if (scaleMode == SCALE_LARGE) {
            return dp(122);
        }
        return dp(108);
    }

    public int searchHeight() {
        if (scaleMode == SCALE_COMPACT) {
            return dp(42);
        }
        if (scaleMode == SCALE_LARGE) {
            return dp(48);
        }
        return dp(44);
    }

    public float searchTextSp() {
        if (scaleMode == SCALE_COMPACT) {
            return 12.5f;
        }
        if (scaleMode == SCALE_LARGE) {
            return 14.5f;
        }
        return 13.5f;
    }

    public int snap(int pxVal) {
        if (pxVal <= 0) {
            return 0;
        }
        int rem = pxVal % u;
        return rem == 0 ? pxVal : (pxVal + (u - rem));
    }

    public int units(int count) {
        return count * u;
    }
}
