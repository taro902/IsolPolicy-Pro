package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;

public final class PixelMetrics {

    public final float density;
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

    private PixelMetrics(float density) {
        this.density = density > 0f ? density : 2.75f;
        int rawU = Math.round(this.density * 1.12f);
        this.u = Math.max(2, Math.min(4, rawU));
        this.border = this.u;
        this.shadow = this.u;
        this.padSm = this.u * 2;
        this.padMd = this.u * 3;
        this.padLg = this.u * 4;
        this.microScale = Math.max(2, this.u);
        this.smallFontScale = Math.max(2, this.u - 1);
        this.fontScale = this.u;
        this.titleFontScale = this.u + 1;
        this.keycapLip = this.u * 2;
    }

    public static PixelMetrics from(Context ctx) {
        float d = 2.75f;
        if (ctx != null && ctx.getResources() != null && ctx.getResources().getDisplayMetrics() != null) {
            d = ctx.getResources().getDisplayMetrics().density;
        }
        return new PixelMetrics(d);
    }

    public int dp(int dpVal) {
        return Math.round(dpVal * density);
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
