package io.github.taro902.isolationpolicy.ui;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.taro902.isolationpolicy.core.PolicyFlags;
import io.github.taro902.isolationpolicy.radar.ManifestRadar;
import io.github.taro902.isolationpolicy.radar.TelemetryClient;
import io.github.taro902.isolationpolicy.ui.pixel.PixelBadgeView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelKeycapButton;
import io.github.taro902.isolationpolicy.ui.pixel.PixelMetrics;
import io.github.taro902.isolationpolicy.ui.pixel.PixelPalette;
import io.github.taro902.isolationpolicy.ui.pixel.PixelPanelView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelTagBarView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelTextView;

public final class ConsoleAdapters {

    private ConsoleAdapters() {
    }

    public static final class RadarItem {
        public final String packageName;
        public final String label;
        public final ManifestRadar.ScanReport report;
        public final int currentFlags;

        public RadarItem(String packageName, String label, ManifestRadar.ScanReport report, int currentFlags) {
            this.packageName = packageName;
            this.label = label;
            this.report = report;
            this.currentFlags = currentFlags;
        }
    }

    public static final class EventLogAdapter extends RecyclerView.Adapter<EventLogAdapter.Holder> {
        private final PixelMetrics m;
        private PixelPalette palette;
        private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
        private List<TelemetryClient.EventRow> items = Collections.emptyList();
        private Map<Integer, String> names = Collections.emptyMap();

        public EventLogAdapter(PixelMetrics m, PixelPalette pal) {
            this.m = m;
            this.palette = pal;
        }

        public void setPalette(PixelPalette pal) {
            this.palette = pal;
            notifyDataSetChanged();
        }

        public void submit(List<TelemetryClient.EventRow> next, Map<Integer, String> nameMap) {
            this.items = next != null ? next : Collections.emptyList();
            this.names = nameMap != null ? nameMap : Collections.emptyMap();
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(parent.getContext(), m, palette);
        }

        @Override
        public void onBindViewHolder(Holder h, int pos) {
            TelemetryClient.EventRow ev = items.get(pos);
            h.rowBox.setBackgroundColor((pos & 1) == 0 ? palette.surface : palette.well);
            h.leftTxt.setPalette(palette);
            h.rightTxt.setPalette(palette);
            String app = names.get(ev.appId);
            if (app == null || app.isEmpty()) {
                app = "U" + ev.appId;
            }
            h.leftTxt.setColorRole(ev.isAudit ? PixelTextView.ROLE_AUDIT : PixelTextView.ROLE_ACCENT);
            h.leftTxt.setText(fmt.format(new Date(ev.timestampMs))
                + (ev.isAudit ? " AUD " : " ENF ")
                + PolicyFlags.pillarCode(ev.pillar)
                + " " + PolicyFlags.vectorName(ev.vector));
            h.rightTxt.setColorRole(PixelTextView.ROLE_INK);
            h.rightTxt.setText(app.toUpperCase(Locale.US)
                + "  " + PolicyFlags.actionName(ev.action)
                + (ev.aux > 0 ? (" X" + ev.aux) : ""));
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final LinearLayout rowBox;
            final PixelTextView leftTxt;
            final PixelTextView rightTxt;

            Holder(Context ctx, PixelMetrics m, PixelPalette pal) {
                super(new LinearLayout(ctx));
                rowBox = (LinearLayout) itemView;
                rowBox.setOrientation(LinearLayout.VERTICAL);
                rowBox.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ));
                rowBox.setPadding(m.u * 3, m.u * 2, m.u * 3, m.u * 2);

                leftTxt = new PixelTextView(ctx, m, pal);
                leftTxt.setMicroMode(true);
                leftTxt.setScale(m.microScale);
                rowBox.addView(leftTxt, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ));

                rightTxt = new PixelTextView(ctx, m, pal);
                rightTxt.setMicroMode(true);
                rightTxt.setScale(m.microScale);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                );
                lp.topMargin = m.u;
                rowBox.addView(rightTxt, lp);
            }
        }
    }

    public static final class RadarAdapter extends RecyclerView.Adapter<RadarAdapter.Holder> {
        public interface OnRadarApplyListener {
            void onApplyRadar(String pkg, int flags);
        }

        private final PixelMetrics m;
        private PixelPalette palette;
        private final OnRadarApplyListener listener;
        private List<RadarItem> items = Collections.emptyList();

        public RadarAdapter(PixelMetrics m, PixelPalette pal, OnRadarApplyListener l) {
            this.m = m;
            this.palette = pal;
            this.listener = l;
        }

        public void setPalette(PixelPalette pal) {
            this.palette = pal;
            notifyDataSetChanged();
        }

        public void submit(List<RadarItem> next) {
            this.items = next != null ? next : Collections.emptyList();
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(parent.getContext(), m, palette);
        }

        @Override
        public void onBindViewHolder(Holder h, int pos) {
            RadarItem item = items.get(pos);
            ManifestRadar.ScanReport rep = item.report;
            h.panel.setPalette(palette);
            h.titleTxt.setPalette(palette);
            h.pkgTxt.setPalette(palette);
            h.tagBar.setPalette(palette);
            h.btnApply.setPalette(palette);
            h.btnAudit.setPalette(palette);

            h.panel.setHighlighted(rep.isFinance());
            h.titleTxt.setText(item.label.toUpperCase(Locale.US));
            h.pkgTxt.setText(item.packageName);

            h.tagBar.clearTags();
            if (rep.isFinance()) {
                h.tagBar.addTag("FINANCE", PixelBadgeView.TONE_WARN);
            } else if (rep.isSandbox()) {
                h.tagBar.addTag("SANDBOX SAFE OFF", PixelBadgeView.TONE_MUTED);
            } else {
                h.tagBar.addTag("GENERAL", PixelBadgeView.TONE_MUTED);
            }
            if (rep.riskLevel >= ManifestRadar.RISK_HIGH) {
                h.tagBar.addTag(rep.riskLabel(), PixelBadgeView.TONE_DANGER);
            }
            for (int i = 0; i < rep.sdkTags.size() && i < 3; i++) {
                h.tagBar.addTag(rep.sdkTags.get(i), PixelBadgeView.TONE_DANGER);
            }
            if (rep.appZygoteServiceCount > 0) {
                h.tagBar.addTag("AZ:" + rep.appZygoteServiceCount, PixelBadgeView.TONE_ACCENT);
            }
            if (rep.isolatedServiceCount > 0) {
                h.tagBar.addTag("ISO:" + rep.isolatedServiceCount, PixelBadgeView.TONE_MUTED);
            }

            if (rep.isSandbox() || !PolicyFlags.isActive(rep.recommendedFlags)) {
                h.btnApply.setEnabled(false);
                h.btnApply.setLabel("SAFE OFF");
                h.btnApply.setKeyState(PixelKeycapButton.STATE_OFF);
                h.btnAudit.setEnabled(false);
                h.btnAudit.setLabel("EXEMPT");
                h.btnAudit.setKeyState(PixelKeycapButton.STATE_OFF);
            } else {
                h.btnApply.setEnabled(true);
                boolean matched = item.currentFlags == rep.recommendedFlags;
                h.btnApply.setLabel(matched ? "ARMED" : ("APPLY " + rep.recommendLabel));
                h.btnApply.setKeyState(matched ? PixelKeycapButton.STATE_ON : PixelKeycapButton.STATE_WARN);
                h.btnApply.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onApplyRadar(item.packageName, rep.recommendedFlags);
                    }
                });

                h.btnAudit.setEnabled(true);
                boolean audOn = PolicyFlags.isAuditOnly(item.currentFlags);
                h.btnAudit.setLabel(audOn ? "AUDITING" : "DRY-RUN");
                h.btnAudit.setKeyState(audOn ? PixelKeycapButton.STATE_AUDIT : PixelKeycapButton.STATE_OFF);
                h.btnAudit.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onApplyRadar(item.packageName, rep.recommendedFlags | PolicyFlags.FLAG_AUDIT_ONLY);
                    }
                });
            }
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final PixelPanelView panel;
            final PixelTextView titleTxt;
            final PixelTextView pkgTxt;
            final PixelTagBarView tagBar;
            final PixelKeycapButton btnApply;
            final PixelKeycapButton btnAudit;

            Holder(Context ctx, PixelMetrics m, PixelPalette pal) {
                super(createWrap(ctx, m));
                LinearLayout wrap = (LinearLayout) itemView;
                int u = m.u;

                panel = new PixelPanelView(ctx, m, pal);
                wrap.addView(panel, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ));

                titleTxt = new PixelTextView(ctx, m, pal);
                titleTxt.setScale(m.fontScale);
                panel.addView(titleTxt, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ));

                pkgTxt = new PixelTextView(ctx, m, pal);
                pkgTxt.setMicroMode(true);
                pkgTxt.setScale(m.microScale);
                pkgTxt.setColorRole(PixelTextView.ROLE_MUTED);
                LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                );
                pLp.topMargin = u;
                panel.addView(pkgTxt, pLp);

                tagBar = new PixelTagBarView(ctx, m, pal);
                LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                );
                tLp.topMargin = u * 2;
                panel.addView(tagBar, tLp);

                LinearLayout btnRow = new LinearLayout(ctx);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                );
                bLp.topMargin = u * 3;
                panel.addView(btnRow, bLp);

                btnApply = new PixelKeycapButton(ctx, m, pal);
                btnApply.setCompact(true);
                LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f);
                aLp.rightMargin = u * 2;
                btnRow.addView(btnApply, aLp);

                btnAudit = new PixelKeycapButton(ctx, m, pal);
                btnAudit.setCompact(true);
                btnRow.addView(btnAudit, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            }

            private static LinearLayout createWrap(Context ctx, PixelMetrics m) {
                LinearLayout w = new LinearLayout(ctx);
                w.setOrientation(LinearLayout.VERTICAL);
                w.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ));
                w.setPadding(m.u * 3, m.u * 2, m.u * 3, m.u * 2);
                return w;
            }
        }
    }
}
