package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;

public final class PixelSearchBarView extends LinearLayout {

    public interface OnQueryListener {
        void onQueryChanged(String query);
    }

    private final PixelMetrics m;
    private final Paint paint = PixelDraw.newPixelPaint();
    private final EditText input;
    private final View clearBtn;
    private final View iconSpacer;
    private PixelPalette palette;
    private OnQueryListener listener;

    public PixelSearchBarView(Context ctx, PixelMetrics metrics, PixelPalette pal) {
        super(ctx);
        this.m = metrics;
        this.palette = pal;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setWillNotDraw(false);
        int u = m.u;
        setPadding(u * 3, u * 2, u * 2, u * 2);
        setMinimumHeight(m.dp(48));

        iconSpacer = new View(ctx) {
            @Override
            protected void onDraw(Canvas canvas) {
                int sc = Math.max(2, m.u - 1);
                int sz = 9 * sc;
                PixelSpriteAtlas.drawSprite(
                    canvas,
                    PixelSpriteAtlas.CTRL_SEARCH,
                    (getWidth() - sz) / 2,
                    (getHeight() - sz) / 2,
                    sc,
                    palette.inkMuted,
                    palette.accent,
                    paint
                );
            }
        };
        addView(iconSpacer, new LayoutParams(m.dp(30), m.dp(30)));

        input = new EditText(ctx);
        input.setBackgroundColor(0x00000000);
        input.setSingleLine(true);
        input.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint("SEARCH PACKAGE OR APP");
        input.setPadding(u * 2, u * 2, u * 2, u * 2);
        addView(input, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        clearBtn = new View(ctx) {
            @Override
            protected void onDraw(Canvas canvas) {
                int sc = Math.max(2, m.u - 1);
                int sz = 9 * sc;
                PixelSpriteAtlas.drawSprite(
                    canvas,
                    PixelSpriteAtlas.CTRL_CLOSE,
                    (getWidth() - sz) / 2,
                    (getHeight() - sz) / 2,
                    sc,
                    palette.inkMuted,
                    palette.danger,
                    paint
                );
            }
        };
        clearBtn.setVisibility(GONE);
        clearBtn.setOnClickListener(v -> input.setText(""));
        addView(clearBtn, new LayoutParams(m.dp(34), m.dp(30)));

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s != null ? s.toString() : "";
                clearBtn.setVisibility(q.isEmpty() ? GONE : VISIBLE);
                if (listener != null) {
                    listener.onQueryChanged(q);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        applyPalette();
    }

    public void setPalette(PixelPalette pal) {
        this.palette = pal;
        applyPalette();
        invalidate();
        iconSpacer.invalidate();
        clearBtn.invalidate();
    }

    public void setOnQueryListener(OnQueryListener l) {
        this.listener = l;
    }

    public void setHint(String hint) {
        input.setHint(hint != null ? hint : "");
    }

    public String getQuery() {
        CharSequence cs = input.getText();
        return cs != null ? cs.toString() : "";
    }

    private void applyPalette() {
        input.setTextColor(palette.ink);
        input.setHintTextColor(palette.inkMuted);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        PixelDraw.drawRecessedWell(canvas, 0, 0, getWidth(), getHeight(), m.u, palette, paint);
    }
}
