package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

public final class PixelDockView extends View {

    public interface OnTabSelectedListener {
        void onTabSelected(int tabIndex);
    }

    private static final String[] TAB_LABELS = {"GUARD", "SCOPE", "RADAR", "VAULT"};

    private final PixelMetrics m;
    private final Paint paint = PixelDraw.newPixelPaint();
    private PixelPalette palette;
    private OnTabSelectedListener listener;
    private int activeTab = 0;
    private boolean scopeHasEvents = false;

    public PixelDockView(Context ctx, PixelMetrics metrics, PixelPalette pal) {
        super(ctx);
        this.m = metrics;
        this.palette = pal;
        setClickable(true);
    }

    public void setPalette(PixelPalette pal) {
        this.palette = pal;
        invalidate();
    }

    public void setOnTabSelectedListener(OnTabSelectedListener l) {
        this.listener = l;
    }

    public void setActiveTab(int tab) {
        int clamped = Math.max(0, Math.min(3, tab));
        if (this.activeTab != clamped) {
            this.activeTab = clamped;
            invalidate();
        }
    }

    public int getActiveTab() {
        return activeTab;
    }

    public void setScopeHasEvents(boolean hasEvents) {
        if (this.scopeHasEvents != hasEvents) {
            this.scopeHasEvents = hasEvents;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(m.dp(360), widthMeasureSpec), resolveSize(m.dockHeight(), heightMeasureSpec));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            int w = getWidth();
            if (w > 0) {
                int idx = Math.max(0, Math.min(3, (int) (event.getX() * 4f / w)));
                if (PixelKeycapButton.isHapticsEnabled()) {
                    try {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    } catch (Throwable ignored) {
                    }
                }
                setActiveTab(idx);
                if (listener != null) {
                    listener.onTabSelected(idx);
                }
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int u = m.u;
        if (w < u * 24 || h < u * 16) {
            return;
        }

        paint.setColor(palette.surface);
        canvas.drawRect(0, 0, w, h, paint);
        PixelDraw.drawDivider(canvas, 0, 0, w, u, palette.border, paint);

        int slotW = w / 4;
        int iconScale = Math.max(3, u);
        int iconSz = 14 * iconScale;
        int lblSc = m.microScale;
        int lblH = PixelFont.lineHeight(lblSc);

        for (int i = 0; i < 4; i++) {
            int left = i * slotW + u * 2;
            int right = (i == 3 ? w : (i + 1) * slotW) - u * 2;
            int top = u * 2;
            int bottom = h - u * 2;
            boolean sel = (i == activeTab);

            if (sel) {
                PixelDraw.drawNotchedBox(canvas, left, top, right, bottom, u, palette.accentDim, palette.accent, paint);
                PixelDraw.drawBayerFill(canvas, left + u, top + u, right - u, bottom - u, palette.accentDim, palette.ditherDot, 3, u, paint);
                paint.setColor(palette.accent);
                canvas.drawRect(left + u * 2, 0, right - u * 2, u * 2, paint);
            }

            int totalH = iconSz + u * 2 + lblH;
            int contentTop = top + Math.max(u, (bottom - top - totalH) / 2);
            int cx = left + (right - left) / 2;

            int primary = sel ? palette.ink : palette.inkMuted;
            int accent = sel ? palette.accent : palette.borderHi;
            PixelSpriteAtlas.drawSprite(canvas, PixelSpriteAtlas.dockIcon(i), cx - iconSz / 2, contentTop, iconScale, primary, accent, paint);

            if (i == 1 && scopeHasEvents) {
                PixelDraw.drawLed(canvas, cx + iconSz / 2 + u, contentTop, u * 3, u, palette.accent, palette.border, paint);
            }

            int tw = PixelFont.measureWidth(TAB_LABELS[i], lblSc);
            PixelFont.drawText(canvas, TAB_LABELS[i], cx - tw / 2, contentTop + iconSz + u * 2, lblSc, sel ? palette.accent : palette.inkMuted, paint);
        }
    }
}
