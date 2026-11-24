package io.github.taro902.isolationpolicy.ui;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.taro902.isolationpolicy.core.PolicyFlags;
import io.github.taro902.isolationpolicy.radar.ManifestRadar;
import io.github.taro902.isolationpolicy.ui.pixel.PixelBadgeView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelIconView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelKeycapButton;
import io.github.taro902.isolationpolicy.ui.pixel.PixelMetrics;
import io.github.taro902.isolationpolicy.ui.pixel.PixelPalette;
import io.github.taro902.isolationpolicy.ui.pixel.PixelPanelView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelSpriteAtlas;
import io.github.taro902.isolationpolicy.ui.pixel.PixelTagBarView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelTextView;

public final class AppPolicyAdapter extends RecyclerView.Adapter<AppPolicyAdapter.RowHolder> {

    public static final String PAYLOAD_FLAGS = "flags";
    public static final String PAYLOAD_HITS = "hits";
    public static final String PAYLOAD_ICON = "icon";

    private static final int ICON_CACHE_MAX_BYTES = 4 * 1024 * 1024;
    private static final int NATIVE_ICON_PX = 64;

    public interface OnAppActionListener {
        void onFlagsUpdated(String packageName, int newFlags);
        void onOpenInspector(AppRow row);
    }

    public static final class IconPair {
        public final Bitmap nativeBmp;
        public final Bitmap pixelBmp;

        public IconPair(Bitmap nativeBmp, Bitmap pixelBmp) {
            this.nativeBmp = nativeBmp;
            this.pixelBmp = pixelBmp;
        }
    }

    public static final class AppRow {
        public final String packageName;
        public final String label;
        public final int appId;
        public final boolean isSystem;
        public final int flags;
        public final ManifestRadar.ScanReport report;
        public final long totalHits;
        public final long auditHits;
        public final long[] pillarHits;

        public AppRow(
            String packageName,
            String label,
            int appId,
            boolean isSystem,
            int flags,
            ManifestRadar.ScanReport report,
            long totalHits,
            long auditHits,
            long[] pillarHits
        ) {
            this.packageName = packageName;
            this.label = label != null ? label : packageName;
            this.appId = appId;
            this.isSystem = isSystem;
            this.flags = PolicyFlags.sanitize(flags);
            this.report = report;
            this.totalHits = totalHits;
            this.auditHits = auditHits;
            this.pillarHits = pillarHits != null ? pillarHits : new long[PolicyFlags.PILLAR_COUNT];
        }
    }

    private final PackageManager pm;
    private final PixelMetrics m;
    private PixelPalette palette;
    private boolean pixelIcons;
    private final OnAppActionListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService iconPool = Executors.newFixedThreadPool(2);
    private final Set<String> pendingIcons = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final LruCache<String, IconPair> iconCache = new LruCache<String, IconPair>(ICON_CACHE_MAX_BYTES) {
        @Override
        protected int sizeOf(String key, IconPair value) {
            int bytes = 0;
            if (value != null && value.nativeBmp != null) {
                bytes += value.nativeBmp.getByteCount();
            }
            if (value != null && value.pixelBmp != null) {
                bytes += value.pixelBmp.getByteCount();
            }
            return Math.max(1024, bytes);
        }
    };

    private List<AppRow> rows = new ArrayList<AppRow>();
    private volatile boolean destroyed = false;

    public AppPolicyAdapter(
        Context ctx,
        PixelMetrics metrics,
        PixelPalette pal,
        boolean usePixelIcons,
        OnAppActionListener actionListener
    ) {
        this.pm = ctx.getPackageManager();
        this.m = metrics;
        this.palette = pal;
        this.pixelIcons = usePixelIcons;
        this.listener = actionListener;
        setHasStableIds(true);
    }

    public void setPalette(PixelPalette pal) {
        this.palette = pal;
        notifyDataSetChanged();
    }

    public void setPixelIcons(boolean usePixel) {
        if (this.pixelIcons != usePixel) {
            this.pixelIcons = usePixel;
            notifyDataSetChanged();
        }
    }

    public IconPair getCachedIcon(String packageName) {
        return packageName != null ? iconCache.get(packageName) : null;
    }

    public void submitList(final List<AppRow> nextList) {
        final List<AppRow> oldList = this.rows;
        final List<AppRow> safeNext = nextList != null ? nextList : Collections.<AppRow>emptyList();
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return safeNext.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldList.get(oldPos).packageName.equals(safeNext.get(newPos).packageName);
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                AppRow a = oldList.get(oldPos);
                AppRow b = safeNext.get(newPos);
                return a.flags == b.flags
                    && a.totalHits == b.totalHits
                    && a.auditHits == b.auditHits
                    && Arrays.equals(a.pillarHits, b.pillarHits)
                    && a.report == b.report;
            }

            @Override
            public Object getChangePayload(int oldPos, int newPos) {
                AppRow a = oldList.get(oldPos);
                AppRow b = safeNext.get(newPos);
                if (a.flags != b.flags) {
                    return PAYLOAD_FLAGS;
                }
                if (a.totalHits != b.totalHits || a.auditHits != b.auditHits) {
                    return PAYLOAD_HITS;
                }
                return null;
            }
        });
        this.rows = new ArrayList<AppRow>(safeNext);
        diff.dispatchUpdatesTo(this);
    }

    public void shutdown() {
        destroyed = true;
        iconPool.shutdownNow();
        iconCache.evictAll();
    }

    @Override
    public long getItemId(int position) {
        return rows.get(position).packageName.hashCode();
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @Override
    public RowHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new RowHolder(parent.getContext(), m, palette);
    }

    @Override
    public void onBindViewHolder(RowHolder holder, int position) {
        bindRow(holder, rows.get(position), null);
    }

    @Override
    public void onBindViewHolder(RowHolder holder, int position, List<Object> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            bindRow(holder, rows.get(position), null);
            return;
        }
        bindRow(holder, rows.get(position), payloads);
    }

    private void bindRow(final RowHolder h, final AppRow row, List<Object> payloads) {
        h.boundPkg = row.packageName;
        h.applyPalette(palette);
        h.iconView.setPixelMode(pixelIcons);

        boolean full = (payloads == null || payloads.isEmpty());
        boolean updFlags = full || payloads.contains(PAYLOAD_FLAGS);
        boolean updHits = full || payloads.contains(PAYLOAD_HITS) || payloads.contains(PAYLOAD_FLAGS);
        boolean updIcon = full || payloads.contains(PAYLOAD_ICON);

        if (full) {
            h.nameText.setText(row.label.toUpperCase());
            h.pkgText.setText(row.packageName);
        }

        if (updFlags) {
            boolean active = PolicyFlags.isActive(row.flags);
            boolean audit = PolicyFlags.isAuditOnly(row.flags);
            h.panel.setHighlighted(active);
            h.iconView.setGuardedBorder(active);

            int az = PolicyFlags.azMode(row.flags);
            h.btnAz.setLabel(az == PolicyFlags.AZ_OFF ? "AZ" : PolicyFlags.azLabel(az));
            if (az == PolicyFlags.AZ_HARD_DROP) {
                h.btnAz.setKeyState(PixelKeycapButton.STATE_DANGER);
            } else if (az == PolicyFlags.AZ_DOWNGRADE) {
                h.btnAz.setKeyState(PixelKeycapButton.STATE_ON);
            } else if (az == PolicyFlags.AZ_AUTO) {
                h.btnAz.setKeyState(PixelKeycapButton.STATE_WARN);
            } else {
                h.btnAz.setKeyState(PixelKeycapButton.STATE_OFF);
            }

            h.btnDev.setKeyState(PolicyFlags.hasFlag(row.flags, PolicyFlags.FLAG_DEV_COHERENT)
                ? PixelKeycapButton.STATE_ON : PixelKeycapButton.STATE_OFF);
            h.btnA11y.setKeyState(PolicyFlags.hasFlag(row.flags, PolicyFlags.FLAG_A11Y_COHERENT)
                ? PixelKeycapButton.STATE_ON : PixelKeycapButton.STATE_OFF);
            h.btnPkg.setKeyState(PolicyFlags.hasFlag(row.flags, PolicyFlags.FLAG_PKG_CHANGES)
                ? PixelKeycapButton.STATE_ON : PixelKeycapButton.STATE_OFF);
            h.btnAudit.setKeyState(audit ? PixelKeycapButton.STATE_AUDIT : PixelKeycapButton.STATE_OFF);

            int extraCount = 0;
            if (PolicyFlags.hasFlag(row.flags, PolicyFlags.FLAG_ISO_FOLD)) {
                extraCount++;
            }
            if (PolicyFlags.hasFlag(row.flags, PolicyFlags.FLAG_DISP_STEALTH)) {
                extraCount++;
            }
            if (PolicyFlags.hasFlag(row.flags, PolicyFlags.FLAG_PROC_SURFACE)) {
                extraCount++;
            }
            if (PolicyFlags.hasFlag(row.flags, PolicyFlags.FLAG_INPUT_SURFACE)) {
                extraCount++;
            }
            if (PolicyFlags.hasFlag(row.flags, PolicyFlags.FLAG_OBS_COHERENCE)) {
                extraCount++;
            }
            h.btnMore.setLabel(extraCount > 0 ? "+" + extraCount : "ALL");
            h.btnMore.setKeyState(extraCount > 0 ? PixelKeycapButton.STATE_WARN : PixelKeycapButton.STATE_OFF);

            h.tagBar.clearTags();
            if (row.report != null) {
                if (row.report.isFinance()) {
                    h.tagBar.addTag("FINANCE", PixelBadgeView.TONE_WARN);
                } else if (row.report.isSandbox()) {
                    h.tagBar.addTag("SANDBOX", PixelBadgeView.TONE_MUTED);
                }
                for (int i = 0; i < row.report.sdkTags.size() && i < 2; i++) {
                    h.tagBar.addTag(row.report.sdkTags.get(i), PixelBadgeView.TONE_DANGER);
                }
                if (row.report.appZygoteServiceCount > 0) {
                    h.tagBar.addTag("AZ:" + row.report.appZygoteServiceCount, PixelBadgeView.TONE_ACCENT);
                }
                if (row.report.isolatedServiceCount > 0) {
                    h.tagBar.addTag("ISO:" + row.report.isolatedServiceCount, PixelBadgeView.TONE_MUTED);
                }
            }
            if (audit && active) {
                h.tagBar.addTag("DRY-RUN", PixelBadgeView.TONE_AUDIT);
            } else if (active) {
                h.tagBar.addTag(PolicyFlags.countActivePillars(row.flags) + "/10", PixelBadgeView.TONE_ACCENT);
            }
        }

        if (updHits) {
            boolean active = PolicyFlags.isActive(row.flags);
            boolean audit = PolicyFlags.isAuditOnly(row.flags);
            if (row.totalHits > 0L) {
                if (audit || (row.auditHits > 0L && row.auditHits >= row.totalHits)) {
                    h.hitBadge.bind("AUD " + row.totalHits, PixelBadgeView.TONE_AUDIT, true);
                } else {
                    h.hitBadge.bind("HIT " + row.totalHits, PixelBadgeView.TONE_ACCENT, true);
                }
            } else if (active) {
                h.hitBadge.bind(audit ? "AUDIT" : "ARMED", audit ? PixelBadgeView.TONE_AUDIT : PixelBadgeView.TONE_ACCENT, true);
            } else {
                h.hitBadge.bind("OFF", PixelBadgeView.TONE_MUTED, false);
            }
        }

        if (updIcon) {
            IconPair pair = iconCache.get(row.packageName);
            if (pair != null) {
                h.iconView.setBitmaps(pair.nativeBmp, pair.pixelBmp);
            } else {
                h.iconView.setBitmaps(null, null);
                loadIconAsync(row.packageName);
            }
        }

        h.topArea.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onOpenInspector(row);
                }
            }
        });
        h.btnAz.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    int nextAz = (PolicyFlags.azMode(row.flags) + 1) & PolicyFlags.AZ_MASK;
                    listener.onFlagsUpdated(row.packageName, PolicyFlags.withAzMode(row.flags, nextAz));
                }
            }
        });
        h.btnDev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onFlagsUpdated(row.packageName, PolicyFlags.toggleFlag(row.flags, PolicyFlags.FLAG_DEV_COHERENT));
                }
            }
        });
        h.btnA11y.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onFlagsUpdated(row.packageName, PolicyFlags.toggleFlag(row.flags, PolicyFlags.FLAG_A11Y_COHERENT));
                }
            }
        });
        h.btnPkg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onFlagsUpdated(row.packageName, PolicyFlags.toggleFlag(row.flags, PolicyFlags.FLAG_PKG_CHANGES));
                }
            }
        });
        h.btnAudit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    int next = PolicyFlags.toggleFlag(row.flags, PolicyFlags.FLAG_AUDIT_ONLY);
                    if (PolicyFlags.isAuditOnly(next) && !PolicyFlags.isActive(next)) {
                        next = PolicyFlags.PRESET_AUDIT_BANK;
                    }
                    listener.onFlagsUpdated(row.packageName, next);
                }
            }
        });
        h.btnMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onOpenInspector(row);
                }
            }
        });
    }

    private void loadIconAsync(final String packageName) {
        if (destroyed || packageName == null || !pendingIcons.add(packageName)) {
            return;
        }
        try {
            iconPool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Drawable d = pm.getApplicationIcon(packageName);
                        Bitmap nativeBmp = drawableToBitmap(d, NATIVE_ICON_PX);
                        Bitmap pixelBmp = PixelIconView.quantizeTo16(nativeBmp);
                        if (nativeBmp != null) {
                            iconCache.put(packageName, new IconPair(nativeBmp, pixelBmp));
                            if (!destroyed) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (destroyed) {
                                            return;
                                        }
                                        for (int i = 0; i < rows.size(); i++) {
                                            if (packageName.equals(rows.get(i).packageName)) {
                                                notifyItemChanged(i, PAYLOAD_ICON);
                                                break;
                                            }
                                        }
                                    }
                                });
                            }
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        pendingIcons.remove(packageName);
                    }
                }
            });
        } catch (Throwable ignored) {
            pendingIcons.remove(packageName);
        }
    }

    private static Bitmap drawableToBitmap(Drawable d, int sizePx) {
        if (d == null) {
            return null;
        }
        try {
            if (d instanceof BitmapDrawable) {
                Bitmap src = ((BitmapDrawable) d).getBitmap();
                if (src != null && !src.isRecycled()) {
                    if (src.getWidth() <= sizePx && src.getHeight() <= sizePx) {
                        return src;
                    }
                    return Bitmap.createScaledBitmap(src, sizePx, sizePx, true);
                }
            }
            Bitmap out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            d.setBounds(0, 0, sizePx, sizePx);
            d.draw(c);
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static final class RowHolder extends RecyclerView.ViewHolder {
        final PixelPanelView panel;
        final LinearLayout topArea;
        final PixelIconView iconView;
        final PixelTextView nameText;
        final PixelTextView pkgText;
        final PixelBadgeView hitBadge;
        final PixelTagBarView tagBar;
        final PixelKeycapButton btnAz;
        final PixelKeycapButton btnDev;
        final PixelKeycapButton btnA11y;
        final PixelKeycapButton btnPkg;
        final PixelKeycapButton btnAudit;
        final PixelKeycapButton btnMore;
        String boundPkg = "";

        RowHolder(Context ctx, PixelMetrics m, PixelPalette pal) {
            super(createOuter(ctx, m));
            LinearLayout outer = (LinearLayout) itemView;
            int u = m.u;

            panel = new PixelPanelView(ctx, m, pal);
            outer.addView(panel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            topArea = new LinearLayout(ctx);
            topArea.setOrientation(LinearLayout.HORIZONTAL);
            topArea.setGravity(Gravity.CENTER_VERTICAL);
            panel.addView(topArea, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            iconView = new PixelIconView(ctx, m, pal);
            int iconPx = m.dp(42);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconPx, iconPx);
            iconLp.rightMargin = u * 3;
            topArea.addView(iconView, iconLp);

            LinearLayout titleCol = new LinearLayout(ctx);
            titleCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            titleLp.rightMargin = u * 2;
            topArea.addView(titleCol, titleLp);

            nameText = new PixelTextView(ctx, m, pal);
            nameText.setScale(m.fontScale);
            nameText.setColorRole(PixelTextView.ROLE_INK);
            titleCol.addView(nameText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            pkgText = new PixelTextView(ctx, m, pal);
            pkgText.setMicroMode(true);
            pkgText.setScale(m.microScale);
            pkgText.setColorRole(PixelTextView.ROLE_MUTED);
            LinearLayout.LayoutParams pkgLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            pkgLp.topMargin = u * 2;
            titleCol.addView(pkgText, pkgLp);

            hitBadge = new PixelBadgeView(ctx, m, pal);
            hitBadge.setMicro(true);
            topArea.addView(hitBadge, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            tagBar = new PixelTagBarView(ctx, m, pal);
            LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            tagLp.topMargin = u * 2;
            panel.addView(tagBar, tagLp);

            LinearLayout keyRow = new LinearLayout(ctx);
            keyRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams keyRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            keyRowLp.topMargin = u * 3;
            panel.addView(keyRow, keyRowLp);

            btnAz = createRowKey(ctx, m, pal, "AZ", null, true);
            btnDev = createRowKey(ctx, m, pal, "DEV", null, true);
            btnA11y = createRowKey(ctx, m, pal, "A11Y", null, true);
            btnPkg = createRowKey(ctx, m, pal, "PKG", null, true);
            btnAudit = createRowKey(ctx, m, pal, "AUD", null, true);
            btnMore = createRowKey(ctx, m, pal, "ALL", PixelSpriteAtlas.CTRL_MORE, false);

            keyRow.addView(btnAz);
            keyRow.addView(btnDev);
            keyRow.addView(btnA11y);
            keyRow.addView(btnPkg);
            keyRow.addView(btnAudit);
            keyRow.addView(btnMore);
        }

        void applyPalette(PixelPalette pal) {
            panel.setPalette(pal);
            iconView.setPalette(pal);
            nameText.setPalette(pal);
            pkgText.setPalette(pal);
            hitBadge.setPalette(pal);
            tagBar.setPalette(pal);
            btnAz.setPalette(pal);
            btnDev.setPalette(pal);
            btnA11y.setPalette(pal);
            btnPkg.setPalette(pal);
            btnAudit.setPalette(pal);
            btnMore.setPalette(pal);
        }

        private static LinearLayout createOuter(Context ctx, PixelMetrics m) {
            LinearLayout outer = new LinearLayout(ctx);
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            ));
            outer.setPadding(m.u * 3, m.u * 2, m.u * 3, m.u * 2);
            return outer;
        }

        private static PixelKeycapButton createRowKey(
            Context ctx,
            PixelMetrics m,
            PixelPalette pal,
            String label,
            String[] icon,
            boolean rightGap
        ) {
            PixelKeycapButton b = new PixelKeycapButton(ctx, m, pal);
            b.setCompact(true);
            b.setLabel(label);
            if (icon != null) {
                b.setIconSprite(icon);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (rightGap) {
                lp.rightMargin = m.u * 2;
            }
            b.setLayoutParams(lp);
            return b;
        }
    }
}
