package io.github.taro902.isolationpolicy.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.taro902.isolationpolicy.core.PolicyFlags;
import io.github.taro902.isolationpolicy.radar.ManifestRadar;
import io.github.taro902.isolationpolicy.radar.TelemetryClient;
import io.github.taro902.isolationpolicy.ui.pixel.PixelChrome;
import io.github.taro902.isolationpolicy.ui.pixel.PixelKeycapView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelPalette;
import io.github.taro902.isolationpolicy.ui.pixel.PixelSpriteAtlas;
import io.github.taro902.isolationpolicy.ui.pixel.PixelTextView;

public final class AppInspectorDialog {

    private AppInspectorDialog() {
    }

    public static void show(
            Context context,
            PixelPalette pal,
            AppPolicyAdapter.AppItem item,
            TelemetryClient.SnapshotData snapshot,
            PresetCallback callback
    ) {
        int u = pal.px;
        final int[] liveFlags = {item.flags};

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(PixelChrome.modal(pal));
        root.setPadding(u * 4, u * 4, u * 5, u * 5);

        LinearLayout titleBar = new LinearLayout(context);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setBackground(PixelChrome.ditherBar(pal));
        titleBar.setPadding(u * 4, u * 3, u * 3, u * 3);
        root.addView(titleBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        PixelTextView titleTxt = new PixelTextView(context, "PACKAGE INSPECTOR", pal.ok, pal.fontScale);
        titleTxt.setLed(true, pal.ok, pal.ledSpec);
        titleBar.addView(titleTxt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView closeBtn = new ImageView(context);
        closeBtn.setImageDrawable(PixelSpriteAtlas.createDrawable(
                context.getResources(),
                PixelSpriteAtlas.CLOSE_7,
                Math.max(2, pal.smallFontScale),
                pal.danger,
                pal.danger,
                pal.outline
        ));
        closeBtn.setPadding(u * 3, u * 3, u * 3, u * 3);
        closeBtn.setBackground(PixelChrome.inset(pal));
        titleBar.addView(closeBtn);

        TextView appTitle = new TextView(context);
        appTitle.setTypeface(pal.monoBold);
        appTitle.setTextSize(13.5f);
        appTitle.setTextColor(pal.inkPrimary);
        appTitle.setText(item.label);
        LinearLayout.LayoutParams atLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        atLp.topMargin = u * 3;
        root.addView(appTitle, atLp);

        TextView sub = new TextView(context);
        sub.setTypeface(pal.monoRegular);
        sub.setTextSize(10f);
        sub.setTextColor(pal.inkSecondary);
        sub.setFontFeatureSettings("tnum");
        sub.setText(item.packageName + "\nUID " + item.uid + "  APPID " + item.appId
                + "  MASK 0x" + Integer.toHexString(item.flags).toUpperCase(Locale.ROOT)
                + "  SCORE " + item.report.threatScore + "/100");
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subLp.topMargin = u;
        root.addView(sub, subLp);

        LinearLayout liveRow1 = new LinearLayout(context);
        liveRow1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lr1 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lr1.topMargin = u * 3;
        root.addView(liveRow1, lr1);

        LinearLayout liveRow2 = new LinearLayout(context);
        liveRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lr2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lr2.topMargin = u * 2;
        root.addView(liveRow2, lr2);

        PixelKeycapView kAz = new PixelKeycapView(context, pal, true);
        PixelKeycapView kIso = new PixelKeycapView(context, pal, true);
        PixelKeycapView kDev = new PixelKeycapView(context, pal, true);
        PixelKeycapView kA11y = new PixelKeycapView(context, pal, true);
        PixelKeycapView kPkg = new PixelKeycapView(context, pal, true);
        PixelKeycapView kDisp = new PixelKeycapView(context, pal, true);

        addWeighted(liveRow1, kAz, 0);
        addWeighted(liveRow1, kIso, u * 2);
        addWeighted(liveRow1, kDev, u * 2);
        addWeighted(liveRow2, kA11y, 0);
        addWeighted(liveRow2, kPkg, u * 2);
        addWeighted(liveRow2, kDisp, u * 2);

        Runnable syncKeys = () -> {
            int fl = liveFlags[0];
            int az = PolicyFlags.getAzMode(fl);
            String azTxt = "AZ:" + PolicyFlags.azLabel(az);
            if (az == PolicyFlags.AZ_HARD_DROP) {
                kAz.applyAccent(azTxt, pal.danger, pal.dangerBg, pal.dangerLip, true, true);
            } else if (az == PolicyFlags.AZ_DOWNGRADE) {
                kAz.applyAccent(azTxt, pal.ok, pal.okBg, pal.okLip, true, true);
            } else if (az == PolicyFlags.AZ_AUTO) {
                kAz.applyAccent(azTxt, pal.warn, pal.warnBg, pal.warnLip, true, true);
            } else {
                kAz.applyAccent(azTxt, pal.outline, pal.keyOffBg, pal.keyOffLip, false, false);
            }
            kIso.applyAccent("ISO", pal.info, pal.infoBg, pal.infoLip, PolicyFlags.has(fl, PolicyFlags.FLAG_ISO_FOLD), true);
            kDev.applyAccent("DEV", pal.ok, pal.okBg, pal.okLip, PolicyFlags.has(fl, PolicyFlags.FLAG_DEV_COHERENT), true);
            kA11y.applyAccent("A11Y", pal.focus, pal.focusBg, pal.focusLip, PolicyFlags.has(fl, PolicyFlags.FLAG_A11Y_COHERENT), true);
            kPkg.applyAccent("PKG", pal.warn, pal.warnBg, pal.warnLip, PolicyFlags.has(fl, PolicyFlags.FLAG_PKG_CHANGES), true);
            kDisp.applyAccent("DISP", pal.info, pal.infoBg, pal.infoLip, PolicyFlags.has(fl, PolicyFlags.FLAG_DISP_STEALTH), true);
            sub.setText(item.packageName + "\nUID " + item.uid + "  APPID " + item.appId
                    + "  MASK 0x" + Integer.toHexString(fl).toUpperCase(Locale.ROOT)
                    + "  SCORE " + item.report.threatScore + "/100");
        };
        syncKeys.run();

        kAz.setOnClickListener(v -> {
            liveFlags[0] = PolicyFlags.cycleAzMode(liveFlags[0]);
            callback.onApplyFlags(item.packageName, liveFlags[0]);
            syncKeys.run();
        });
        kIso.setOnClickListener(v -> {
            liveFlags[0] = PolicyFlags.toggle(liveFlags[0], PolicyFlags.FLAG_ISO_FOLD);
            callback.onApplyFlags(item.packageName, liveFlags[0]);
            syncKeys.run();
        });
        kDev.setOnClickListener(v -> {
            liveFlags[0] = PolicyFlags.toggle(liveFlags[0], PolicyFlags.FLAG_DEV_COHERENT);
            callback.onApplyFlags(item.packageName, liveFlags[0]);
            syncKeys.run();
        });
        kA11y.setOnClickListener(v -> {
            liveFlags[0] = PolicyFlags.toggle(liveFlags[0], PolicyFlags.FLAG_A11Y_COHERENT);
            callback.onApplyFlags(item.packageName, liveFlags[0]);
            syncKeys.run();
        });
        kPkg.setOnClickListener(v -> {
            liveFlags[0] = PolicyFlags.toggle(liveFlags[0], PolicyFlags.FLAG_PKG_CHANGES);
            callback.onApplyFlags(item.packageName, liveFlags[0]);
            syncKeys.run();
        });
        kDisp.setOnClickListener(v -> {
            liveFlags[0] = PolicyFlags.toggle(liveFlags[0], PolicyFlags.FLAG_DISP_STEALTH);
            callback.onApplyFlags(item.packageName, liveFlags[0]);
            syncKeys.run();
        });

        ScrollView scroll = new ScrollView(context);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                pal.dp(220)
        );
        scrollLp.topMargin = u * 3;
        root.addView(scroll, scrollLp);

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackground(PixelChrome.inset(pal));
        body.setPadding(u * 4, u * 4, u * 4, u * 4);
        scroll.addView(body);

        TextView reportView = new TextView(context);
        reportView.setTypeface(pal.monoRegular);
        reportView.setTextSize(10f);
        reportView.setTextColor(pal.inkPrimary);
        reportView.setTextIsSelectable(true);
        reportView.setFontFeatureSettings("tnum");
        reportView.setText(buildInspectorReport(item, snapshot));
        body.addView(reportView);

        LinearLayout presetRow = new LinearLayout(context);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams prLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        prLp.topMargin = u * 3;
        root.addView(presetRow, prLp);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(root)
                .create();

        closeBtn.setOnClickListener(v -> dialog.dismiss());

        PixelKeycapView btnWallet = makeBtn(context, pal, "WALLET", pal.info, pal.infoBg, pal.infoLip);
        btnWallet.setOnClickListener(v -> {
            liveFlags[0] = PolicyFlags.AZ_DOWNGRADE | PolicyFlags.FLAG_DEV_COHERENT | PolicyFlags.FLAG_A11Y_COHERENT | PolicyFlags.FLAG_PKG_CHANGES;
            callback.onApplyFlags(item.packageName, liveFlags[0]);
            syncKeys.run();
        });
        addWeighted(presetRow, btnWallet, 0);

        PixelKeycapView btnBank = makeBtn(context, pal, "BANKING", pal.ok, pal.okBg, pal.okLip);
        btnBank.setOnClickListener(v -> {
            liveFlags[0] = PolicyFlags.PRESET_BANKING_STRICT;
            callback.onApplyFlags(item.packageName, liveFlags[0]);
            syncKeys.run();
        });
        addWeighted(presetRow, btnBank, u * 2);

        PixelKeycapView btnClear = makeBtn(context, pal, "CLEAR", pal.danger, pal.dangerBg, pal.dangerLip);
        btnClear.setOnClickListener(v -> {
            liveFlags[0] = PolicyFlags.PRESET_NONE;
            callback.onApplyFlags(item.packageName, liveFlags[0]);
            syncKeys.run();
        });
        addWeighted(presetRow, btnClear, u * 2);

        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private static PixelKeycapView makeBtn(Context ctx, PixelPalette p, String lbl, int acc, int bg, int lip) {
        PixelKeycapView k = new PixelKeycapView(ctx, p, false);
        k.setSmallText(true);
        k.applyAccent(lbl, acc, bg, lip, true, false);
        return k;
    }

    private static void addWeighted(LinearLayout row, PixelKeycapView key, int startMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(startMargin);
        row.addView(key, lp);
    }

    private static String buildInspectorReport(AppPolicyAdapter.AppItem item, TelemetryClient.SnapshotData snap) {
        ManifestRadar.ScanReport r = item.report;
        StringBuilder sb = new StringBuilder();
        sb.append("SURFACE AUDIT\n");
        sb.append("Category       : ").append(r.isSandbox ? "WEB OR MEDIA SANDBOX" : (r.financeLikely ? "FINANCIAL TARGET" : "STANDARD APP")).append('\n');
        sb.append("AppZygote      : ").append(r.usesAppZygote ? "PRESENT" : "NONE").append('\n');
        if (r.zygotePreloadName != null && !r.zygotePreloadName.isEmpty()) {
            sb.append("Preload Class  : ").append(r.zygotePreloadName).append('\n');
        }
        sb.append("Internal Iso   : ").append(r.isolatedServices.size()).append('\n');
        sb.append("External Iso   : ").append(r.externalIsolatedCount).append(" (PROTECTED)\n");
        sb.append("QueryAllPkgs   : ").append(r.queriesAllPackages ? "YES" : "NO").append('\n');
        if (!r.antifraudSdks.isEmpty()) {
            sb.append("Antifraud SDK  : ").append(String.join(", ", r.antifraudSdks)).append('\n');
        } else {
            sb.append("Antifraud SDK  : NONE\n");
        }

        if (!r.appZygoteServices.isEmpty()) {
            sb.append("\nAPPZYGOTE SERVICES\n");
            for (String s : r.appZygoteServices) {
                sb.append("  ").append(s).append('\n');
            }
        }
        if (!r.isolatedServices.isEmpty()) {
            sb.append("\nINTERNAL ISOLATED SERVICES\n");
            for (String s : r.isolatedServices) {
                sb.append("  ").append(s).append('\n');
            }
        }

        sb.append("\nRECENT INTERCEPTIONS\n");
        int shown = 0;
        if (snap != null && snap.connected && !snap.events.isEmpty()) {
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
            for (TelemetryClient.EventRecord ev : snap.events) {
                if (ev.appId == item.appId || item.packageName.equals(ev.packageName)) {
                    sb.append(fmt.format(new Date(ev.timestampMs)))
                            .append("  ")
                            .append(PolicyFlags.pillarName(ev.pillar))
                            .append("  ")
                            .append(PolicyFlags.vectorName(ev.vector))
                            .append(" -> ")
                            .append(PolicyFlags.actionName(ev.action))
                            .append('\n');
                    shown++;
                    if (shown >= 14) {
                        break;
                    }
                }
            }
        }
        if (shown == 0) {
            sb.append("No recent events recorded in ring buffer.\n");
        }
        return sb.toString().trim();
    }

    public interface PresetCallback {
        void onApplyFlags(String packageName, int newFlags);
    }
}
