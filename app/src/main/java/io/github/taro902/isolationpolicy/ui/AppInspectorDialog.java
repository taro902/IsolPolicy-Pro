package io.github.taro902.isolationpolicy.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import io.github.taro902.isolationpolicy.core.PolicyFlags;
import io.github.taro902.isolationpolicy.radar.ManifestRadar;
import io.github.taro902.isolationpolicy.ui.pixel.PixelAppHeatmapBar;
import io.github.taro902.isolationpolicy.ui.pixel.PixelBadgeView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelIconView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelKeycapButton;
import io.github.taro902.isolationpolicy.ui.pixel.PixelMetrics;
import io.github.taro902.isolationpolicy.ui.pixel.PixelPalette;
import io.github.taro902.isolationpolicy.ui.pixel.PixelPanelView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelSpriteAtlas;
import io.github.taro902.isolationpolicy.ui.pixel.PixelTagBarView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelTextView;

public final class AppInspectorDialog {

    public interface OnFlagsChangedListener {
        void onFlagsChanged(String packageName, int newFlags);
    }

    private AppInspectorDialog() {
    }

    public static void show(
        Context ctx,
        final PixelMetrics m,
        final PixelPalette pal,
        final AppPolicyAdapter.AppRow row,
        AppPolicyAdapter.IconPair iconPair,
        boolean usePixelIcons,
        final OnFlagsChangedListener listener
    ) {
        if (ctx == null || row == null) {
            return;
        }
        final Dialog dialog = new Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final int[] currentFlags = new int[]{row.flags};

        ScrollView scroll = new ScrollView(ctx);
        scroll.setFillViewport(true);
        scroll.setPadding(m.u * 4, m.u * 4, m.u * 4, m.u * 4);

        final PixelPanelView root = new PixelPanelView(ctx, m, pal);
        root.setHeader("APP INSPECTOR", PolicyFlags.formatMaskHex(currentFlags[0]));
        scroll.addView(root, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        int u = m.u;

        LinearLayout hdr = new LinearLayout(ctx);
        hdr.setOrientation(LinearLayout.HORIZONTAL);
        hdr.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(hdr, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        final PixelIconView iconView = new PixelIconView(ctx, m, pal);
        iconView.setPixelMode(usePixelIcons);
        iconView.setGuardedBorder(PolicyFlags.isActive(currentFlags[0]));
        if (iconPair != null) {
            iconView.setBitmaps(iconPair.nativeBmp, iconPair.pixelBmp);
        }
        int iconSz = m.dp(48);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSz, iconSz);
        iconLp.rightMargin = u * 3;
        hdr.addView(iconView, iconLp);

        LinearLayout infoCol = new LinearLayout(ctx);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        hdr.addView(infoCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        PixelTextView titleTxt = new PixelTextView(ctx, m, pal);
        titleTxt.setScale(m.fontScale);
        titleTxt.setText(row.label.toUpperCase());
        infoCol.addView(titleTxt, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        PixelTextView pkgTxt = new PixelTextView(ctx, m, pal);
        pkgTxt.setMicroMode(true);
        pkgTxt.setScale(m.microScale);
        pkgTxt.setColorRole(PixelTextView.ROLE_MUTED);
        pkgTxt.setText(row.packageName);
        LinearLayout.LayoutParams pkgLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        pkgLp.topMargin = u * 2;
        infoCol.addView(pkgTxt, pkgLp);

        final PixelTextView metaTxt = new PixelTextView(ctx, m, pal);
        metaTxt.setMicroMode(true);
        metaTxt.setScale(m.microScale);
        metaTxt.setColorRole(PixelTextView.ROLE_ACCENT);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        metaLp.topMargin = u * 2;
        infoCol.addView(metaTxt, metaLp);

        PixelKeycapButton closeBtn = new PixelKeycapButton(ctx, m, pal);
        closeBtn.setCompact(true);
        closeBtn.setIconSprite(PixelSpriteAtlas.CTRL_CLOSE);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        hdr.addView(closeBtn, new LinearLayout.LayoutParams(u * 13, LinearLayout.LayoutParams.WRAP_CONTENT));

        if (row.report != null) {
            PixelTagBarView radarTags = new PixelTagBarView(ctx, m, pal);
            ManifestRadar.ScanReport rep = row.report;
            int catTone = rep.isFinance() ? PixelBadgeView.TONE_WARN : PixelBadgeView.TONE_MUTED;
            radarTags.addTag(rep.categoryLabel(), catTone);
            radarTags.addTag("RISK " + rep.riskLabel(), rep.riskLevel >= ManifestRadar.RISK_HIGH ? PixelBadgeView.TONE_DANGER : PixelBadgeView.TONE_MUTED);
            if (rep.appZygoteServiceCount > 0) {
                radarTags.addTag("AZ:" + rep.appZygoteServiceCount, PixelBadgeView.TONE_ACCENT);
            }
            if (rep.isolatedServiceCount > 0) {
                radarTags.addTag("ISO:" + rep.isolatedServiceCount, PixelBadgeView.TONE_MUTED);
            }
            for (int i = 0; i < rep.sdkTags.size() && i < 3; i++) {
                radarTags.addTag(rep.sdkTags.get(i), PixelBadgeView.TONE_DANGER);
            }
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            rLp.topMargin = u * 3;
            root.addView(radarTags, rLp);
        }

        PixelTextView hmLabel = new PixelTextView(ctx, m, pal);
        hmLabel.setMicroMode(true);
        hmLabel.setScale(m.microScale);
        hmLabel.setColorRole(PixelTextView.ROLE_MUTED);
        hmLabel.setText("PER-PILLAR TELEMETRY HITS  TOTAL " + row.totalHits);
        LinearLayout.LayoutParams hmLblLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        hmLblLp.topMargin = u * 4;
        root.addView(hmLabel, hmLblLp);

        final PixelAppHeatmapBar heatmap = new PixelAppHeatmapBar(ctx, m, pal);
        heatmap.bind(row.pillarHits, PolicyFlags.isAuditOnly(currentFlags[0]));
        LinearLayout.LayoutParams hmLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        hmLp.topMargin = u * 2;
        root.addView(heatmap, hmLp);

        PixelTextView matLabel = new PixelTextView(ctx, m, pal);
        matLabel.setMicroMode(true);
        matLabel.setScale(m.microScale);
        matLabel.setColorRole(PixelTextView.ROLE_MUTED);
        matLabel.setText("10-PILLAR POLICY MATRIX");
        LinearLayout.LayoutParams matLblLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        matLblLp.topMargin = u * 4;
        root.addView(matLabel, matLblLp);

        final PixelKeycapButton[] pillarBtns = new PixelKeycapButton[PolicyFlags.PILLAR_COUNT];
        for (int r = 0; r < 2; r++) {
            LinearLayout rowBox = new LinearLayout(ctx);
            rowBox.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            rowLp.topMargin = u * 2;
            root.addView(rowBox, rowLp);

            for (int c = 0; c < 5; c++) {
                final int pillar = r * 5 + c;
                PixelKeycapButton btn = new PixelKeycapButton(ctx, m, pal);
                btn.setCompact(true);
                btn.setLabel(PolicyFlags.pillarCode(pillar));
                btn.setIconSprite(PixelSpriteAtlas.pillarIcon(pillar));
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (c < 4) {
                    blp.rightMargin = u * 2;
                }
                rowBox.addView(btn, blp);
                pillarBtns[pillar] = btn;
            }
        }

        final PixelKeycapButton auditBtn = new PixelKeycapButton(ctx, m, pal);
        auditBtn.setLabel("SHADOW AUDIT DRY-RUN");
        auditBtn.setIconSprite(PixelSpriteAtlas.CTRL_AUDIT);
        LinearLayout.LayoutParams audLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        audLp.topMargin = u * 3;
        root.addView(auditBtn, audLp);

        PixelTextView presetLabel = new PixelTextView(ctx, m, pal);
        presetLabel.setMicroMode(true);
        presetLabel.setScale(m.microScale);
        presetLabel.setColorRole(PixelTextView.ROLE_MUTED);
        presetLabel.setText("QUICK PRESETS");
        LinearLayout.LayoutParams preLblLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        preLblLp.topMargin = u * 4;
        root.addView(presetLabel, preLblLp);

        LinearLayout preRow = new LinearLayout(ctx);
        preRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams preRowLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        preRowLp.topMargin = u * 2;
        root.addView(preRow, preRowLp);

        final PixelKeycapButton preOff = makePresetBtn(ctx, m, pal, "OFF", true);
        final PixelKeycapButton preStd = makePresetBtn(ctx, m, pal, "STD", true);
        final PixelKeycapButton preMax = makePresetBtn(ctx, m, pal, "MAX", true);
        final PixelKeycapButton preAud = makePresetBtn(ctx, m, pal, "AUD", true);
        final PixelKeycapButton preIso = makePresetBtn(ctx, m, pal, "ISO", false);
        preRow.addView(preOff);
        preRow.addView(preStd);
        preRow.addView(preMax);
        preRow.addView(preAud);
        preRow.addView(preIso);

        final Runnable refreshUi = new Runnable() {
            @Override
            public void run() {
                int f = currentFlags[0];
                root.setHeader("APP INSPECTOR", PolicyFlags.formatMaskHex(f));
                iconView.setGuardedBorder(PolicyFlags.isActive(f));
                metaTxt.setText("APPID " + row.appId + "  " + PolicyFlags.summarize(f));
                heatmap.bind(row.pillarHits, PolicyFlags.isAuditOnly(f));

                int az = PolicyFlags.azMode(f);
                pillarBtns[PolicyFlags.PILLAR_AZ].setLabel(az == PolicyFlags.AZ_OFF ? "AZ" : PolicyFlags.azLabel(az));
                if (az == PolicyFlags.AZ_HARD_DROP) {
                    pillarBtns[PolicyFlags.PILLAR_AZ].setKeyState(PixelKeycapButton.STATE_DANGER);
                } else if (az == PolicyFlags.AZ_DOWNGRADE) {
                    pillarBtns[PolicyFlags.PILLAR_AZ].setKeyState(PixelKeycapButton.STATE_ON);
                } else if (az == PolicyFlags.AZ_AUTO) {
                    pillarBtns[PolicyFlags.PILLAR_AZ].setKeyState(PixelKeycapButton.STATE_WARN);
                } else {
                    pillarBtns[PolicyFlags.PILLAR_AZ].setKeyState(PixelKeycapButton.STATE_OFF);
                }

                for (int p = 1; p < PolicyFlags.PILLAR_COUNT; p++) {
                    boolean en = PolicyFlags.isPillarEnabled(f, p);
                    int st = en ? (p == PolicyFlags.PILLAR_ISO ? PixelKeycapButton.STATE_WARN : PixelKeycapButton.STATE_ON) : PixelKeycapButton.STATE_OFF;
                    pillarBtns[p].setKeyState(st);
                }

                boolean isAud = PolicyFlags.isAuditOnly(f);
                auditBtn.setKeyState(isAud ? PixelKeycapButton.STATE_AUDIT : PixelKeycapButton.STATE_OFF);
                auditBtn.setLabel(isAud ? "SHADOW AUDIT ON  OBSERVE ONLY" : "SHADOW AUDIT OFF  ENFORCING");
            }
        };

        final OnFlagsChangedListener apply = new OnFlagsChangedListener() {
            @Override
            public void onFlagsChanged(String pkg, int nextFlags) {
                currentFlags[0] = PolicyFlags.sanitize(nextFlags);
                refreshUi.run();
                if (listener != null) {
                    listener.onFlagsChanged(pkg, currentFlags[0]);
                }
            }
        };

        pillarBtns[PolicyFlags.PILLAR_AZ].setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int nextAz = (PolicyFlags.azMode(currentFlags[0]) + 1) & PolicyFlags.AZ_MASK;
                apply.onFlagsChanged(row.packageName, PolicyFlags.withAzMode(currentFlags[0], nextAz));
            }
        });
        for (int p = 1; p < PolicyFlags.PILLAR_COUNT; p++) {
            final int bit = PolicyFlags.pillarFlagBit(p);
            pillarBtns[p].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    apply.onFlagsChanged(row.packageName, PolicyFlags.toggleFlag(currentFlags[0], bit));
                }
            });
        }

        auditBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int next = PolicyFlags.toggleFlag(currentFlags[0], PolicyFlags.FLAG_AUDIT_ONLY);
                if (PolicyFlags.isAuditOnly(next) && !PolicyFlags.isActive(next)) {
                    next = PolicyFlags.PRESET_AUDIT_BANK;
                }
                apply.onFlagsChanged(row.packageName, next);
            }
        });

        preOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                apply.onFlagsChanged(row.packageName, PolicyFlags.PRESET_NONE);
            }
        });
        preStd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                apply.onFlagsChanged(row.packageName, PolicyFlags.PRESET_BANKING_STRICT);
            }
        });
        preMax.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                apply.onFlagsChanged(row.packageName, PolicyFlags.PRESET_BANKING_MAX);
            }
        });
        preAud.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                apply.onFlagsChanged(row.packageName, PolicyFlags.PRESET_AUDIT_BANK);
            }
        });
        preIso.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                apply.onFlagsChanged(row.packageName, PolicyFlags.PRESET_WALLET_ALIPAY);
            }
        });

        refreshUi.run();
        dialog.setContentView(scroll);
        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(0x00000000));
            win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private static PixelKeycapButton makePresetBtn(
        Context ctx,
        PixelMetrics m,
        PixelPalette pal,
        String lbl,
        boolean rightGap
    ) {
        PixelKeycapButton b = new PixelKeycapButton(ctx, m, pal);
        b.setCompact(true);
        b.setLabel(lbl);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (rightGap) {
            lp.rightMargin = m.u * 2;
        }
        b.setLayoutParams(lp);
        return b;
    }
}
