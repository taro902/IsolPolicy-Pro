package io.github.taro902.isolationpolicy.ui;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.github.taro902.isolationpolicy.core.PolicyFlags;
import io.github.taro902.isolationpolicy.radar.ManifestRadar;
import io.github.taro902.isolationpolicy.ui.pixel.PixelChipView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelDrawable;
import io.github.taro902.isolationpolicy.ui.pixel.PixelIconQuantizer;
import io.github.taro902.isolationpolicy.ui.pixel.PixelTheme;
import io.github.taro902.isolationpolicy.ui.pixel.PixelThreatBar;

public final class AppPolicyAdapter extends RecyclerView.Adapter<AppPolicyAdapter.Holder> {

    private static final String PAYLOAD_FLAGS = "FLAGS";

    private final PixelTheme theme;
    private final PackageManager pm;
    private final PixelIconQuantizer iconQuantizer;
    private final ActionListener listener;
    private final List<AppItem> items = new ArrayList<>();
    private boolean pixelizeIcons;

    public AppPolicyAdapter(
            PixelTheme theme,
            PackageManager pm,
            PixelIconQuantizer iconQuantizer,
            boolean pixelizeIcons,
            ActionListener listener
    ) {
        this.theme = theme;
        this.pm = pm;
        this.iconQuantizer = iconQuantizer;
        this.pixelizeIcons = pixelizeIcons;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setPixelizeIcons(boolean pixelize) {
        if (this.pixelizeIcons != pixelize) {
            this.pixelizeIcons = pixelize;
            notifyItemRangeChanged(0, items.size());
        }
    }

    public void submitList(List<AppItem> nextList) {
        List<AppItem> copy = nextList != null ? new ArrayList<>(nextList) : new ArrayList<>();
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return items.size();
            }

            @Override
            public int getNewListSize() {
                return copy.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return items.get(oldPos).packageName.equals(copy.get(newPos).packageName);
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                AppItem o = items.get(oldPos);
                AppItem n = copy.get(newPos);
                return o.flags == n.flags
                        && o.report.threatScore == n.report.threatScore
                        && o.report.usesAppZygote == n.report.usesAppZygote
                        && o.report.isolatedServices.size() == n.report.isolatedServices.size();
            }

            @Override
            public Object getChangePayload(int oldPos, int newPos) {
                AppItem o = items.get(oldPos);
                AppItem n = copy.get(newPos);
                if (o.flags != n.flags) {
                    return PAYLOAD_FLAGS;
                }
                return null;
            }
        });
        items.clear();
        items.addAll(copy);
        diff.dispatchUpdatesTo(this);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).packageName.hashCode();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context ctx = parent.getContext();
        int u = theme.px;

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        RecyclerView.LayoutParams rootLp = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rootLp.setMargins(0, 0, 0, u * 3);
        card.setLayoutParams(rootLp);
        card.setPadding(u * 4, u * 4, u * 4, u * 4);

        LinearLayout topRow = new LinearLayout(ctx);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(topRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout iconBox = new LinearLayout(ctx);
        iconBox.setGravity(Gravity.CENTER);
        iconBox.setPadding(u * 2, u * 2, u * 2, u * 2);
        iconBox.setBackground(PixelDrawable.inset(theme));
        int iconDim = theme.dp(36);
        ImageView iconView = new ImageView(ctx);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconBox.addView(iconView, new LinearLayout.LayoutParams(iconDim, iconDim));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        iconLp.setMarginEnd(u * 4);
        topRow.addView(iconBox, iconLp);

        LinearLayout metaCol = new LinearLayout(ctx);
        metaCol.setOrientation(LinearLayout.VERTICAL);
        topRow.addView(metaCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout titleLine = new LinearLayout(ctx);
        titleLine.setOrientation(LinearLayout.HORIZONTAL);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        metaCol.addView(titleLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView labelView = new TextView(ctx);
        labelView.setTypeface(theme.monoBold);
        labelView.setTextSize(12.5f);
        labelView.setTextColor(theme.inkPrimary);
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        titleLine.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        PixelThreatBar threatBar = new PixelThreatBar(ctx, theme);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        barLp.setMarginStart(u * 3);
        titleLine.addView(threatBar, barLp);

        TextView pkgView = new TextView(ctx);
        pkgView.setTypeface(theme.monoRegular);
        pkgView.setTextSize(10f);
        pkgView.setTextColor(theme.inkSecondary);
        pkgView.setSingleLine(true);
        pkgView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        pkgView.setFontFeatureSettings("tnum");
        metaCol.addView(pkgView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView tagView = new TextView(ctx);
        tagView.setTypeface(theme.monoBold);
        tagView.setTextSize(9.5f);
        tagView.setSingleLine(true);
        tagView.setEllipsize(TextUtils.TruncateAt.END);
        metaCol.addView(tagView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        HorizontalScrollView chipScroll = new HorizontalScrollView(ctx);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setOverScrollMode(HorizontalScrollView.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        scrollLp.topMargin = u * 3;
        card.addView(chipScroll, scrollLp);

        LinearLayout chipRow = new LinearLayout(ctx);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setGravity(Gravity.CENTER_VERTICAL);
        chipScroll.addView(chipRow);

        PixelChipView chipAz = new PixelChipView(ctx, theme);
        PixelChipView chipIso = new PixelChipView(ctx, theme);
        PixelChipView chipDev = new PixelChipView(ctx, theme);
        PixelChipView chipA11y = new PixelChipView(ctx, theme);
        PixelChipView chipPkg = new PixelChipView(ctx, theme);
        PixelChipView chipDisp = new PixelChipView(ctx, theme);

        PixelChipView[] allChips = {chipAz, chipIso, chipDev, chipA11y, chipPkg, chipDisp};
        for (int i = 0; i < allChips.length; i++) {
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (i > 0) {
                clp.setMarginStart(u * 2);
            }
            chipRow.addView(allChips[i], clp);
        }

        return new Holder(card, topRow, iconView, labelView, pkgView, tagView, threatBar,
                chipAz, chipIso, chipDev, chipA11y, chipPkg, chipDisp);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        AppItem item = items.get(position);
        h.labelView.setText(item.label);
        h.pkgView.setText(item.packageName + " | u0_a" + Math.max(0, item.appId - 10000));
        h.threatBar.setThreatScore(item.report.threatScore);

        String tagSummary = buildThreatSummary(item.report);
        if (tagSummary.isEmpty()) {
            h.tagView.setText("CLEAN SURFACE");
            h.tagView.setTextColor(theme.inkMuted);
        } else {
            h.tagView.setText(tagSummary);
            h.tagView.setTextColor(item.report.threatScore >= 60 ? theme.accentDanger : theme.accentWarn);
        }

        iconQuantizer.loadInto(h.iconView, pm, item.applicationInfo, pixelizeIcons, theme.dp(36));

        h.topRow.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                listener.onInspectRequested(items.get(pos));
            }
        });

        bindChips(h, item, false);
        bindChipClicks(h);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_FLAGS)) {
            bindChips(h, items.get(position), true);
            return;
        }
        onBindViewHolder(h, position);
    }

    private void bindChips(Holder h, AppItem item, boolean animate) {
        int fl = item.flags;
        h.card.setBackground(PixelDrawable.card(theme, PolicyFlags.isActive(fl)));

        int az = PolicyFlags.getAzMode(fl);
        String azTxt = "AZ:" + PolicyFlags.azLabel(az);
        if (az == PolicyFlags.AZ_HARD_DROP) {
            h.chipAz.applyState(azTxt, theme.chipOffBg, theme.accentDangerBg, theme.accentDanger, theme.accentDanger, true, animate);
        } else if (az == PolicyFlags.AZ_DOWNGRADE) {
            h.chipAz.applyState(azTxt, theme.chipOffBg, theme.accentOkBg, theme.accentOk, theme.accentOk, true, animate);
        } else if (az == PolicyFlags.AZ_AUTO) {
            h.chipAz.applyState(azTxt, theme.chipOffBg, theme.accentWarnBg, theme.accentWarn, theme.accentWarn, true, animate);
        } else {
            h.chipAz.applyState(azTxt, theme.chipOffBg, theme.chipOffBg, theme.borderOuter, theme.chipOffInk, false, false);
        }

        bindToggleChip(h.chipIso, "ISO", PolicyFlags.has(fl, PolicyFlags.FLAG_ISO_FOLD), theme.accentInfo, theme.accentInfoBg, animate);
        bindToggleChip(h.chipDev, "DEV", PolicyFlags.has(fl, PolicyFlags.FLAG_DEV_COHERENT), theme.accentOk, theme.accentOkBg, animate);
        bindToggleChip(h.chipA11y, "A11Y", PolicyFlags.has(fl, PolicyFlags.FLAG_A11Y_COHERENT), theme.accentFocus, theme.accentFocusBg, animate);
        bindToggleChip(h.chipPkg, "PKG", PolicyFlags.has(fl, PolicyFlags.FLAG_PKG_CHANGES), theme.accentWarn, theme.accentWarnBg, animate);
        bindToggleChip(h.chipDisp, "DISP", PolicyFlags.has(fl, PolicyFlags.FLAG_DISP_STEALTH), theme.accentInfo, theme.accentInfoBg, animate);
    }

    private void bindToggleChip(
            PixelChipView chip,
            String label,
            boolean on,
            int accent,
            int accentBg,
            boolean animate
    ) {
        if (on) {
            chip.applyState(label, theme.chipOffBg, accentBg, accent, accent, true, animate);
        } else {
            chip.applyState(label, theme.chipOffBg, theme.chipOffBg, theme.borderOuter, theme.chipOffInk, false, false);
        }
    }

    private void bindChipClicks(Holder h) {
        h.chipAz.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                AppItem it = items.get(pos);
                listener.onFlagsChanged(it.packageName, PolicyFlags.cycleAzMode(it.flags));
            }
        });
        h.chipIso.setOnClickListener(v -> toggleBitAt(h, PolicyFlags.FLAG_ISO_FOLD));
        h.chipDev.setOnClickListener(v -> toggleBitAt(h, PolicyFlags.FLAG_DEV_COHERENT));
        h.chipA11y.setOnClickListener(v -> toggleBitAt(h, PolicyFlags.FLAG_A11Y_COHERENT));
        h.chipPkg.setOnClickListener(v -> toggleBitAt(h, PolicyFlags.FLAG_PKG_CHANGES));
        h.chipDisp.setOnClickListener(v -> toggleBitAt(h, PolicyFlags.FLAG_DISP_STEALTH));
    }

    private void toggleBitAt(Holder h, int bit) {
        int pos = h.getBindingAdapterPosition();
        if (pos != RecyclerView.NO_POSITION) {
            AppItem it = items.get(pos);
            listener.onFlagsChanged(it.packageName, PolicyFlags.toggle(it.flags, bit));
        }
    }

    private static String buildThreatSummary(ManifestRadar.ScanReport r) {
        StringBuilder sb = new StringBuilder();
        if (r.usesAppZygote) {
            sb.append("[APPZYGOTE] ");
        }
        if (!r.isolatedServices.isEmpty()) {
            sb.append("[ISO:").append(r.isolatedServices.size()).append("] ");
        }
        if (r.financeLikely) {
            sb.append("[FINANCE] ");
        }
        for (String sdk : r.antifraudSdks) {
            sb.append('[').append(sdk).append("] ");
        }
        return sb.toString().trim();
    }

    public interface ActionListener {
        void onFlagsChanged(String packageName, int newFlags);

        void onInspectRequested(AppItem item);
    }

    public static final class AppItem {
        public final String packageName;
        public final String label;
        public final int uid;
        public final int appId;
        public final boolean isSystem;
        public final ApplicationInfo applicationInfo;
        public final int flags;
        public final ManifestRadar.ScanReport report;

        public AppItem(
                String packageName,
                String label,
                int uid,
                boolean isSystem,
                ApplicationInfo applicationInfo,
                int flags,
                ManifestRadar.ScanReport report
        ) {
            this.packageName = packageName;
            this.label = label;
            this.uid = uid;
            this.appId = uid >= 10000 ? (uid % 100000) : uid;
            this.isSystem = isSystem;
            this.applicationInfo = applicationInfo;
            this.flags = flags;
            this.report = report != null ? report : ManifestRadar.ScanReport.EMPTY;
        }

        public AppItem withFlags(int nextFlags) {
            return new AppItem(packageName, label, uid, isSystem, applicationInfo, nextFlags, report);
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final LinearLayout card;
        final LinearLayout topRow;
        final ImageView iconView;
        final TextView labelView;
        final TextView pkgView;
        final TextView tagView;
        final PixelThreatBar threatBar;
        final PixelChipView chipAz;
        final PixelChipView chipIso;
        final PixelChipView chipDev;
        final PixelChipView chipA11y;
        final PixelChipView chipPkg;
        final PixelChipView chipDisp;

        Holder(
                LinearLayout card,
                LinearLayout topRow,
                ImageView iconView,
                TextView labelView,
                TextView pkgView,
                TextView tagView,
                PixelThreatBar threatBar,
                PixelChipView chipAz,
                PixelChipView chipIso,
                PixelChipView chipDev,
                PixelChipView chipA11y,
                PixelChipView chipPkg,
                PixelChipView chipDisp
        ) {
            super(card);
            this.card = card;
            this.topRow = topRow;
            this.iconView = iconView;
            this.labelView = labelView;
            this.pkgView = pkgView;
            this.tagView = tagView;
            this.threatBar = threatBar;
            this.chipAz = chipAz;
            this.chipIso = chipIso;
            this.chipDev = chipDev;
            this.chipA11y = chipA11y;
            this.chipPkg = chipPkg;
            this.chipDisp = chipDisp;
        }
    }
}