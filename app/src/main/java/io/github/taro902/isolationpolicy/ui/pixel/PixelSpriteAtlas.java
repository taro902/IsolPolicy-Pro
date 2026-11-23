package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;

public final class PixelSpriteAtlas {

    public static final String[] SHIELD_14 = {
            "00033333333000",
            "00311111111300",
            "03122111111130",
            "03121111111130",
            "03111333311130",
            "03113222231130",
            "03113211231130",
            "03113211231130",
            "03111333311130",
            "00311111111300",
            "00311111111300",
            "00031111113000",
            "00003111130000",
            "00000333300000"
    };

    public static final String[] TAB_GUARD_12 = {
            "003333333300",
            "031221111130",
            "031211111130",
            "031112211130",
            "031122221130",
            "031112211130",
            "031111111130",
            "003111111300",
            "003111111300",
            "000311113000",
            "000031130000",
            "000003300000"
    };

    public static final String[] TAB_SCOPE_12 = {
            "033333333330",
            "311111111113",
            "313333333313",
            "313000200313",
            "313002200313",
            "313220022313",
            "313000020313",
            "313333333313",
            "311211111113",
            "033333333330",
            "000311113000",
            "003333333300"
    };

    public static final String[] TAB_RADAR_12 = {
            "000333333000",
            "003111111300",
            "031000021130",
            "310033320013",
            "310310203013",
            "310302203013",
            "310300013013",
            "310033330013",
            "031000000130",
            "003111111300",
            "000333333000",
            "000000000000"
    };

    public static final String[] TAB_VAULT_12 = {
            "033333333330",
            "311111111113",
            "312211111113",
            "311133331113",
            "311322223113",
            "311323323113",
            "311322223113",
            "311133331113",
            "311111111113",
            "311111111113",
            "033333333330",
            "003300003300"
    };

    public static final String[] SEARCH_9 = {
            "001111000",
            "012000100",
            "120000010",
            "100000010",
            "100000010",
            "010000100",
            "001111110",
            "000000111",
            "000000011"
    };

    public static final String[] CLOSE_7 = {
            "1100011",
            "0110110",
            "0011100",
            "0001000",
            "0011100",
            "0110110",
            "1100011"
    };

    private PixelSpriteAtlas() {
    }

    public static void drawSprite(
            Canvas canvas,
            String[] rows,
            float left,
            float top,
            int scale,
            int primaryColor,
            int highlightColor,
            int outlineColor,
            Paint paint
    ) {
        if (rows == null || rows.length == 0 || scale <= 0) {
            return;
        }
        float ox = Math.round(left);
        float oy = Math.round(top);
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            float y = oy + (r * scale);
            for (int c = 0; c < row.length(); c++) {
                char code = row.charAt(c);
                if (code == '0') {
                    continue;
                }
                if (code == '1') {
                    paint.setColor(primaryColor);
                } else if (code == '2') {
                    paint.setColor(highlightColor);
                } else {
                    paint.setColor(outlineColor);
                }
                float x = ox + (c * scale);
                canvas.drawRect(x, y, x + scale, y + scale, paint);
            }
        }
    }

    public static BitmapDrawable createDrawable(
            Resources res,
            String[] rows,
            int scale,
            int primaryColor,
            int highlightColor,
            int outlineColor
    ) {
        int h = rows.length;
        int w = rows[0].length();
        Bitmap bmp = Bitmap.createBitmap(w * scale, h * scale, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint();
        p.setAntiAlias(false);
        p.setStyle(Paint.Style.FILL);
        drawSprite(c, rows, 0f, 0f, scale, primaryColor, highlightColor, outlineColor, p);
        BitmapDrawable d = new BitmapDrawable(res, bmp);
        d.setFilterBitmap(false);
        d.setAntiAlias(false);
        return d;
    }
}
