package io.github.taro902.isolationpolicy.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.taro902.isolationpolicy.core.PolicyFlags;
import io.github.taro902.isolationpolicy.radar.ManifestRadar;
import io.github.taro902.isolationpolicy.radar.TelemetryClient;
import io.github.taro902.isolationpolicy.ui.pixel.PixelChipView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelDrawable;
import io.github.taro902.isolationpolicy.ui.pixel.PixelTheme;

public final class AppInspectorDialog {

    private AppInspectorDialog() {
    }

    public static void show(
            Context context,
            PixelTheme theme,
            AppPolicyAdapter.AppItem item,
            TelemetryClient.SnapshotData snapshot,
            PresetCallback callback
    ) {
        int u = theme.px;
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(PixelDrawable.panel(theme));
        root.setPadding(u * 5, u * 5, u * 5, u * 5);

        TextView header = new TextView(context);
        header.setTypeface(theme.monoBold);
        header.setTextSize(13.5f);
        header.setTextColor(theme.accentOk);
        header.setText("INSPECT // " + item.label.toUpperCase(Locale.ROOT));
        root.addView(header);

        TextView sub = new TextView(context);
        sub.setTypeface(theme.monoRegular);
        sub.setTextSize(10.5f);
        sub.setTextColor(theme.inkSecondary);
        sub.setText(item.packageName + "\nUID=" + item.uid + " APPID=" + item.appId
                + " MASK=0x" + Integer.toHexString(item.flags).toUpperCase(Locale.ROOT)
                + " SCORE=" + item.report.threatScore + "/100");
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subLp.topMargin = u * 2;
        root.addView(sub, subLp);

        ScrollView scroll = new ScrollView(context);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                theme.dp(270)
        );
        scrollLp.topMargin = u * 4;
        root.addView(scroll, scrollLp);

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackground(PixelDrawable.inset(theme));
        body.setPadding(u * 4, u * 4, u * 4, u * 4);
        scroll.addView(body);

        TextView reportView = new TextView(context);
        reportView.setTypeface(theme.monoRegular);
        reportView.setTextSize(10.5f);
        reportView.setTextColor(theme.inkPrimary);
        reportView.setTextIsSelectable(true);
        reportView.setFontFeatureSettings("tnum");
        reportView.setText(buildInspectorReport(item, snapshot));
        body.addView(reportView);

        LinearLayout presetRow1 = new LinearLayout(context);
        presetRow1.setOrientation(LinearLayout.HORIZONTAL);
        presetRow1.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams r1Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        r1Lp.topMargin = u * 4;
        root.addView(presetRow1, r1Lp);

        LinearLayout presetRow2 = new LinearLayout(context);
        presetRow2.setOrientation(LinearLayout.HORIZONTAL);
        presetRow2.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams r2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        r2Lp.topMargin = u * 2;
        root.addView(presetRow2, r2Lp);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(root)
                .create();

        PixelChipView btnRec = makeActionChip(context, theme, "AUTO-REC", theme.accentOk, theme.accentOkBg);
        btnRec.setOnClickListener(v -> {
            int rec = item.report.recommendedFlags != 0 ? item.report.recommendedFlags : PolicyFlags.PRESET_BANKING_STRICT;
            callback.onApplyFlags(item.packageName, rec);
            dialog.dismiss();
        });
        addWeighted(presetRow1, btnRec, 0);

        PixelChipView btnWallet = makeActionChip(context, theme, "ALIPAY/DANA", theme.accentInfo, theme.accentInfoBg);
        btnWallet.setOnClickListener(v -> {
            callback.onApplyFlags(item.packageName, PolicyFlags.PRESET_WALLET_ALIPAY);
            dialog.dismiss();
        });
        addWeighted(presetRow1, btnWallet, u * 2);

        PixelChipView btnBank = makeActionChip(context, theme, "BANK STRICT", theme.accentWarn, theme.accentWarnBg);
        btnBank.setOnClickListener(v -> {
            callback.onApplyFlags(item.packageName, PolicyFlags.PRESET_BANKING_STRICT);
            dialog.dismiss();
        });
        addWeighted(presetRow2, btnBank, 0);

        PixelChipView btnClear = makeActionChip(context, theme, "CLEAR ALL", theme.accentDanger, theme.accentDangerBg);
        btnClear.setOnClickListener(v -> {
            callback.onApplyFlags(item.packageName, PolicyFlags.PRESET_NONE);
            dialog.dismiss();
        });
        addWeighted(presetRow2, btnClear, u * 2);

        PixelChipView btnClose = makeActionChip(context, theme, "CLOSE", theme.inkSecondary, theme.bgCard);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        addWeighted(presetRow2, btnClose, u * 2);

        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private static PixelChipView makeActionChip(Context ctx, PixelTheme t, String label, int accent, int bg) {
        PixelChipView chip = new PixelChipView(ctx, t);
        chip.applyState(label, t.chipOffBg, bg, accent, accent, true, false);
        return chip;
    }

    private static void addWeighted(LinearLayout row, PixelChipView chip, int startMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(startMargin);
        row.addView(chip, lp);
    }

    private static String buildInspectorReport(AppPolicyAdapter.AppItem item, TelemetryClient.SnapshotData snap) {
        ManifestRadar.ScanReport r = item.report;
        StringBuilder sb = new StringBuilder();
        sb.append("[STATIC AXML & ELF SURFACE]\n");
        sb.append("AppZygote      : ").append(r.usesAppZygote ? "DETECTED" : "NONE").append('\n');
        if (r.zygotePreloadName != null && !r.zygotePreloadName.isEmpty()) {
            sb.append("Preload Class  : ").append(r.zygotePreloadName).append('\n');
        }
        sb.append("Internal Iso   : ").append(r.isolatedServices.size()).append('\n');
        sb.append("External Iso   : ").append(r.externalIsolatedCount).append(" (WEBVIEW PROTECTED)\n");
        sb.append("QueryAllPkgs   : ").append(r.queriesAllPackages ? "YES" : "NO").append('\n');
        sb.append("Finance Class  : ").append(r.financeLikely ? "YES" : "NO").append('\n');

        if (!r.antifraudSdks.isEmpty()) {
            sb.append("Antifraud Libs : ").append(String.join(", ", r.antifraudSdks)).append('\n');
        } else {
            sb.append("Antifraud Libs : NONE\n");
        }

        if (!r.appZygoteServices.isEmpty()) {
            sb.append("\n[APPZYGOTE SERVICES]\n");
            for (String s : r.appZygoteServices) {
                sb.append(" - ").append(s).append('\n');
            }
        }
        if (!r.isolatedServices.isEmpty()) {
            sb.append("\n[INTERNAL ISOLATED SERVICES]\n");
            for (String s : r.isolatedServices) {
                sb.append(" - ").append(s).append('\n');
            }
        }

        sb.append("\n[LIVE INTERCEPTIONS]\n");
        int shown = 0;
        if (snap != null && snap.connected && !snap.events.isEmpty()) {
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
            for (TelemetryClient.EventRecord ev : snap.events) {
                if (ev.appId == item.appId || item.packageName.equals(ev.packageName)) {
                    sb.append(fmt.format(new Date(ev.timestampMs)))
                            .append(" | ")
                            .append(PolicyFlags.pillarName(ev.pillar))
                            .append(" | ")
                            .append(PolicyFlags.vectorName(ev.vector))
                            .append(" -> ")
                            .append(PolicyFlags.actionName(ev.action))
                            .append('\n');
                    shown++;
                    if (shown >= 12) {
                        break;
                    }
                }
            }
        }
        if (shown == 0) {
            sb.append("No recent events in system_server ring buffer.\n");
        }
        return sb.toString().trim();
    }

    public interface PresetCallback {
        void onApplyFlags(String packageName, int newFlags);
    }
}