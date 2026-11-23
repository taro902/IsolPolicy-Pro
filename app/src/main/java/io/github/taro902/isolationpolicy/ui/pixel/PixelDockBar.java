package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

public final class PixelDockBar extends View {

    private static final String[] LABELS = {"GUARD", "SCOPE", "RADAR", "VAULT"};
    private static final String[][] SPRITES = {
            PixelSpriteAtlas.TAB_GUARD_12,
            PixelSpriteAtlas.TAB_SCOPE_12,
            PixelSpriteAtlas.TAB_RADAR_12,
            PixelSpriteAtlas.TAB_VAULT_12
    };

    private final PixelPalette pal;
    private final Paint bgPaint = new Paint();
    private final Paint ditherPaint = new Paint();
    private final Paint activeBgPaint = new Paint();
    private final Paint borderPaint = new Paint();
    private final Paint ledPaint = new Paint();
    private final Paint spritePaint = new Paint();
    private final Paint textPaint = new Paint();

    private int activeIndex;
    private OnTabSelectedListener listener;

    public PixelDockBar(Context context, PixelPalette pal) {
        super(context);
        this.pal = pal;
        setClickable(true);

        bgPaint.setAntiAlias(false);
        bgPaint.setColor(pal.bgCanvas);

        ditherPaint.setAntiAlias(false);
        ditherPaint.setShader(pal.ditherShader);

        activeBgPaint.setAntiAlias(false);
        activeBgPaint.setColor(pal.bgCard);

        borderPaint.setAntiAlias(false);
        borderPaint.setColor(pal.outline);

        ledPaint.setAntiAlias(false);
        ledPaint.setColor(pal.ok);

        spritePaint.setAntiAlias(false);
        textPaint.setAntiAlias(false);
    }

    public void setOnTabSelectedListener(OnTabSelectedListener listener) {
        this.listener = listener;
    }

    public void setActiveTab(int index) {
        if (index >= 0 && index < 4 && index != activeIndex) {
            activeIndex = index;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int u = pal.px;
        int spriteDim = 12 * Math.max(2, pal.smallFontScale);
        int textH = PixelFont.lineHeight(pal.smallFontScale);
        int desiredH = spriteDim + textH + u * 11;
        setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                resolveSize(desiredH, heightMeasureSpec)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int u = pal.px;
        int pop = u * 2;

        canvas.drawRect(0, 0, w, pop, bgPaint);
        canvas.drawRect(0, pop, w, h, ditherPaint);
        canvas.drawRect(0, pop, w, pop + u, borderPaint);

        int gap = u;
        int padX = u * 2;
        int tabW = (w - padX * 2 - gap * 3) / 4;
        int spScale = Math.max(2, pal.smallFontScale);
        int spDim = 12 * spScale;
        int fs = pal.smallFontScale;
        int th = PixelFont.lineHeight(fs);

        for (int i = 0; i < 4; i++) {
            boolean sel = (i == activeIndex);
            int l = padX + i * (tabW + gap);
            int r = l + tabW;
            int t = sel ? 0 : (pop + u);
            int b = h - u;

            if (sel) {
                canvas.drawRect(l + u, t + u, r - u, b, activeBgPaint);
                canvas.drawRect(l + u * 2, t, r - u * 2, t + u, ledPaint);
                canvas.drawRect(l + u, t + u, r - u, t + u * 2, ledPaint);
                canvas.drawRect(l, t + u * 2, l + u, b, borderPaint);
                canvas.drawRect(r - u, t + u * 2, r, b, borderPaint);
                canvas.drawRect(l + u, t + u, l + u * 2, t + u * 2, borderPaint);
                canvas.drawRect(r - u * 2, t + u, r - u, t + u * 2, borderPaint);
            } else {
                canvas.drawRect(l + u, t, r - u, t + u, borderPaint);
                canvas.drawRect(l, t + u, l + u, b, borderPaint);
                canvas.drawRect(r - u, t + u, r, b, borderPaint);
            }

            float cx = (l + r) * 0.5f;
            int contentH = spDim + u * 2 + th;
            float sy = t + ((b - t) - contentH) * 0.5f + (sel ? u : 0);

            int c1 = sel ? pal.ok : pal.inkMuted;
            int c2 = sel ? pal.info : pal.inkSecondary;
            int c3 = sel ? pal.outline : pal.shadow;
            PixelSpriteAtlas.drawSprite(canvas, SPRITES[i], cx - spDim * 0.5f, sy, spScale, c1, c2, c3, spritePaint);

            textPaint.setColor(sel ? pal.ok : pal.inkMuted);
            PixelFont.drawCentered(canvas, LABELS[i], cx, sy + spDim + u * 2 + th * 0.5f, fs, textPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            int w = getWidth();
            if (w > 0) {
                int idx = Math.max(0, Math.min(3, (int) ((event.getX() * 4f) / w)));
                if (idx != activeIndex) {
                    activeIndex = idx;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    performClick();
                    invalidate();
                    if (listener != null) {
                        listener.onTabSelected(idx);
                    }
                }
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    public interface OnTabSelectedListener {
        void onTabSelected(int tabIndex);
    }
}
