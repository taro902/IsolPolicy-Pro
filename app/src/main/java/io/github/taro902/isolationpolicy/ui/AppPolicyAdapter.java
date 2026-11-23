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
import io.github.taro902.isolationpolicy.ui.pixel.PixelChrome;
import io.github.taro902.isolationpolicy.ui.pixel.PixelIconQuantizer;
import io.github.taro902.isolationpolicy.ui.pixel.PixelKeycapView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelPalette;
import io.github.taro902.isolationpolicy.ui.pixel.PixelTagBarView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelThreatBar;

public final class AppPolicyAdapter extends RecyclerView.Adapter<AppPolicyAdapter.Holder> {

    private static final String PAYLOAD_FLAGS = "FLAGS";
    private static final String PAYLOAD_HITS = "HITS";

    private final PixelPalette pal;
    private final PackageManager pm;
    private final PixelIconQuantizer iconQuantizer;
    private final ActionListener listener;
    private final List<AppItem> items = new ArrayList<>();
    private boolean pixelizeIcons;

    public AppPolicyAdapter(
            PixelPalette pal,
            PackageManager pm,
            PixelIconQuantizer iconQuantizer,
            boolean pixelizeIcons,
            ActionListener listener
    ) {
        this.pal = pal;
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
                        && o.liveHits == n.liveHits
                        && o.report.threatScore == n.report.threatScore
                        && o.report.isSandbox == n.report.isSandbox;
            }

            @Override
            public Object getChangePayload(int oldPos, int newPos) {
                AppItem o = items.get(oldPos);
                AppItem n = copy.get(newPos);
                if (o.flags != n.flags) {
                    return PAYLOAD_FLAGS;
                }
                if (o.liveHits != n.liveHits) {
                    return PAYLOAD_HITS;
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
        int u = pal.px;

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        RecyclerView.LayoutParams rootLp = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rootLp.setMargins(0, 0, 0, u * 3);
        card.setLayoutParams(rootLp);

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
        iconBox.setBackground(PixelChrome.inset(pal));
        int iconDim = pal.dp(36);
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
        labelView.setTypeface(pal.monoBold);
        labelView.setTextSize(13f);
        labelView.setTextColor(pal.inkPrimary);
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        titleLine.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        PixelThreatBar threatBar = new PixelThreatBar(ctx, pal);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        barLp.setMarginStart(u * 2);
        titleLine.addView(threatBar, barLp);

        TextView pkgView = new TextView(ctx);
        pkgView.setTypeface(pal.monoRegular);
        pkgView.setTextSize(10f);
        pkgView.setTextColor(pal.inkSecondary);
        pkgView.setSingleLine(true);
        pkgView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        pkgView.setFontFeatureSettings("tnum");
        metaCol.addView(pkgView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        PixelTagBarView tagBar = new PixelTagBarView(ctx, pal);
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        tagLp.topMargin = u * 2;
        metaCol.addView(tagBar, tagLp);

        HorizontalScrollView keyScroll = new HorizontalScrollView(ctx);
        keyScroll.setHorizontalScrollBarEnabled(false);
        keyScroll.setOverScrollMode(HorizontalScrollView.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        scrollLp.topMargin = u * 3;
        card.addView(keyScroll, scrollLp);

        LinearLayout keyRow = new LinearLayout(ctx);
        keyRow.setOrientation(LinearLayout.HORIZONTAL);
        keyRow.setGravity(Gravity.CENTER_VERTICAL);
        keyScroll.addView(keyRow);

        PixelKeycapView kAz = new PixelKeycapView(ctx, pal, true);
        PixelKeycapView kIso = new PixelKeycapView(ctx, pal, true);
        PixelKeycapView kDev = new PixelKeycapView(ctx, pal, true);
        PixelKeycapView kA11y = new PixelKeycapView(ctx, pal, true);
        PixelKeycapView kPkg = new PixelKeycapView(ctx, pal, true);
        PixelKeycapView kDisp = new PixelKeycapView(ctx, pal, true);

        PixelKeycapView[] keys = {kAz, kIso, kDev, kA11y, kPkg, kDisp};
        for (int i = 0; i < keys.length; i++) {
            keys[i].setSmallText(false);
            LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (i > 0) {
                klp.setMarginStart(u * 2);
            }
            keyRow.addView(keys[i], klp);
        }

        return new Holder(card, topRow, iconView, labelView, pkgView, tagBar, threatBar,
                kAz, kIso, kDev, kA11y, kPkg, kDisp);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        AppItem item = items.get(position);
        h.labelView.setText(item.label);
        h.pkgView.setText(item.packageName + "  u0_a" + Math.max(0, item.appId - 10000));
        h.threatBar.setMetrics(item.report.threatScore, item.report.isSandbox, item.liveHits);

        bindTags(h.tagBar, item.report);
        iconQuantizer.loadInto(h.iconView, pm, item.applicationInfo, pixelizeIcons, pal.dp(36));

        h.topRow.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                listener.onInspectRequested(items.get(pos));
            }
        });

        bindKeycaps(h, item, false);
        bindClicks(h);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            AppItem item = items.get(position);
            if (payloads.contains(PAYLOAD_FLAGS)) {
                bindKeycaps(h, item, true);
            }
            if (payloads.contains(PAYLOAD_HITS)) {
                h.threatBar.setMetrics(item.report.threatScore, item.report.isSandbox, item.liveHits);
            }
            return;
        }
        onBindViewHolder(h, position);
    }

    private void bindTags(PixelTagBarView bar, ManifestRadar.ScanReport r) {
        bar.clearTags();
        if (r.isSandbox) {
            bar.addTag("SANDBOX", pal.bgInset, pal.outline, pal.inkMuted);
            if (r.usesAppZygote) {
                bar.addTag("WEB ZYGOTE", pal.bgInset, pal.outline, pal.inkMuted);
            }
            if (!r.isolatedServices.isEmpty()) {
                bar.addTag("ISO " + r.isolatedServices.size(), pal.bgInset, pal.outline, pal.inkMuted);
            }
            bar.commitTags();
            return;
        }
        if (r.financeLikely) {
            bar.addTag("FINANCE", pal.okBg, pal.ok, pal.ok);
        }
        if (r.usesAppZygote) {
            bar.addTag("APPZYGOTE", pal.dangerBg, pal.danger, pal.danger);
        }
        if (!r.isolatedServices.isEmpty()) {
            bar.addTag("ISO " + r.isolatedServices.size(), pal.warnBg, pal.warn, pal.warn);
        }
        for (String sdk : r.antifraudSdks) {
            bar.addTag(sdk, pal.dangerBg, pal.danger, pal.danger);
        }
        if (!r.financeLikely && !r.usesAppZygote && r.isolatedServices.isEmpty() && r.antifraudSdks.isEmpty()) {
            bar.addTag("CLEAN", pal.bgInset, pal.outline, pal.inkMuted);
        }
        bar.commitTags();
    }

    private void bindKeycaps(Holder h, AppItem item, boolean animate) {
        int fl = item.flags;
        boolean guarded = PolicyFlags.isActive(fl);
        int u = pal.px;
        h.card.setBackground(PixelChrome.card(pal, guarded));
        h.card.setPadding(guarded ? u * 8 : u * 4, u * 4, u * 5, u * 5);

        int az = PolicyFlags.getAzMode(fl);
        String azTxt = "AZ:" + PolicyFlags.azLabel(az);
        if (az == PolicyFlags.AZ_HARD_DROP) {
            h.kAz.applyAccent(azTxt, pal.danger, pal.dangerBg, pal.dangerLip, true, animate);
        } else if (az == PolicyFlags.AZ_DOWNGRADE) {
            h.kAz.applyAccent(azTxt, pal.ok, pal.okBg, pal.okLip, true, animate);
        } else if (az == PolicyFlags.AZ_AUTO) {
            h.kAz.applyAccent(azTxt, pal.warn, pal.warnBg, pal.warnLip, true, animate);
        } else {
            h.kAz.applyAccent(azTxt, pal.outline, pal.keyOffBg, pal.keyOffLip, false, false);
        }

        h.kIso.applyAccent("ISO", pal.info, pal.infoBg, pal.infoLip, PolicyFlags.has(fl, PolicyFlags.FLAG_ISO_FOLD), animate);
        h.kDev.applyAccent("DEV", pal.ok, pal.okBg, pal.okLip, PolicyFlags.has(fl, PolicyFlags.FLAG_DEV_COHERENT), animate);
        h.kA11y.applyAccent("A11Y", pal.focus, pal.focusBg, pal.focusLip, PolicyFlags.has(fl, PolicyFlags.FLAG_A11Y_COHERENT), animate);
        h.kPkg.applyAccent("PKG", pal.warn, pal.warnBg, pal.warnLip, PolicyFlags.has(fl, PolicyFlags.FLAG_PKG_CHANGES), animate);
        h.kDisp.applyAccent("DISP", pal.info, pal.infoBg, pal.infoLip, PolicyFlags.has(fl, PolicyFlags.FLAG_DISP_STEALTH), animate);
    }

    private void bindClicks(Holder h) {
        h.kAz.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                AppItem it = items.get(pos);
                listener.onFlagsChanged(it.packageName, PolicyFlags.cycleAzMode(it.flags));
            }
        });
        h.kIso.setOnClickListener(v -> toggleBitAt(h, PolicyFlags.FLAG_ISO_FOLD));
        h.kDev.setOnClickListener(v -> toggleBitAt(h, PolicyFlags.FLAG_DEV_COHERENT));
        h.kA11y.setOnClickListener(v -> toggleBitAt(h, PolicyFlags.FLAG_A11Y_COHERENT));
        h.kPkg.setOnClickListener(v -> toggleBitAt(h, PolicyFlags.FLAG_PKG_CHANGES));
        h.kDisp.setOnClickListener(v -> toggleBitAt(h, PolicyFlags.FLAG_DISP_STEALTH));
    }

    private void toggleBitAt(Holder h, int bit) {
        int pos = h.getBindingAdapterPosition();
        if (pos != RecyclerView.NO_POSITION) {
            AppItem it = items.get(pos);
            listener.onFlagsChanged(it.packageName, PolicyFlags.toggle(it.flags, bit));
        }
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
        public final int liveHits;
        public final ManifestRadar.ScanReport report;

        public AppItem(
                String packageName,
                String label,
                int uid,
                boolean isSystem,
                ApplicationInfo applicationInfo,
                int flags,
                int liveHits,
                ManifestRadar.ScanReport report
        ) {
            this.packageName = packageName;
            this.label = label;
            this.uid = uid;
            this.appId = uid >= 10000 ? (uid % 100000) : uid;
            this.isSystem = isSystem;
            this.applicationInfo = applicationInfo;
            this.flags = flags;
            this.liveHits = Math.max(0, liveHits);
            this.report = report != null ? report : ManifestRadar.ScanReport.EMPTY;
        }

        public AppItem withFlags(int nextFlags) {
            return new AppItem(packageName, label, uid, isSystem, applicationInfo, nextFlags, liveHits, report);
        }

        public AppItem withHits(int nextHits) {
            return new AppItem(packageName, label, uid, isSystem, applicationInfo, flags, nextHits, report);
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final LinearLayout card;
        final LinearLayout topRow;
        final ImageView iconView;
        final TextView labelView;
        final TextView pkgView;
        final PixelTagBarView tagBar;
        final PixelThreatBar threatBar;
        final PixelKeycapView kAz;
        final PixelKeycapView kIso;
        final PixelKeycapView kDev;
        final PixelKeycapView kA11y;
        final PixelKeycapView kPkg;
        final PixelKeycapView kDisp;

        Holder(
                LinearLayout card,
                LinearLayout topRow,
                ImageView iconView,
                TextView labelView,
                TextView pkgView,
                PixelTagBarView tagBar,
                PixelThreatBar threatBar,
                PixelKeycapView kAz,
                PixelKeycapView kIso,
                PixelKeycapView kDev,
                PixelKeycapView kA11y,
                PixelKeycapView kPkg,
                PixelKeycapView kDisp
        ) {
            super(card);
            this.card = card;
            this.topRow = topRow;
            this.iconView = iconView;
            this.labelView = labelView;
            this.pkgView = pkgView;
            this.tagBar = tagBar;
            this.threatBar = threatBar;
            this.kAz = kAz;
            this.kIso = kIso;
            this.kDev = kDev;
            this.kA11y = kA11y;
            this.kPkg = kPkg;
            this.kDisp = kDisp;
        }
    }
}
