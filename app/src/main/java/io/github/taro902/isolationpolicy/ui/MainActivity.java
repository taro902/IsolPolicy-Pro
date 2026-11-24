package io.github.taro902.isolationpolicy.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import io.github.taro902.isolationpolicy.BuildConfig;
import io.github.taro902.isolationpolicy.core.PolicyCodec;
import io.github.taro902.isolationpolicy.core.PolicyFlags;
import io.github.taro902.isolationpolicy.hook.PolicyEngine;
import io.github.taro902.isolationpolicy.hook.TelemetryHub;
import io.github.taro902.isolationpolicy.radar.ManifestRadar;
import io.github.taro902.isolationpolicy.radar.TelemetryClient;
import io.github.taro902.isolationpolicy.ui.pixel.PixelCoherenceBarView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelCounterStrip;
import io.github.taro902.isolationpolicy.ui.pixel.PixelDockView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelHeaderView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelHealthMatrixView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelKeycapButton;
import io.github.taro902.isolationpolicy.ui.pixel.PixelMetrics;
import io.github.taro902.isolationpolicy.ui.pixel.PixelPalette;
import io.github.taro902.isolationpolicy.ui.pixel.PixelPanelView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelScopeView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelSearchBarView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelSpriteAtlas;
import io.github.taro902.isolationpolicy.ui.pixel.PixelTextView;

public final class MainActivity extends Activity {

    private static final int REQ_EXPORT_JSON = 2201;
    private static final int REQ_IMPORT_JSON = 2202;
    private static final int FILTER_ALL = 0;
    private static final int FILTER_GUARDED = 1;
    private static final int FILTER_AUDIT = 2;
    private static final int FILTER_FINANCE = 3;
    private static final int FILTER_SANDBOX = 4;
    private static final int SCOPE_MODE_ALL = 0;
    private static final int SCOPE_MODE_ENF = 1;
    private static final int SCOPE_MODE_AUD = 2;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean activityDestroyed = false;

    private PixelMetrics m;
    private PixelPalette palette;
    private SharedPreferences uiPrefs;
    private volatile XposedService xposedService;
    private volatile SharedPreferences remotePrefs;

    private int themeMode = PixelPalette.MODE_CRT;
    private boolean pixelIcons = true;
    private boolean hapticEnabled = true;
    private boolean showSystemApps = false;
    private int pollIntervalMs = 2000;
    private int defaultAzMode = PolicyFlags.AZ_OFF;

    private int activeTab = 0;
    private int guardFilter = FILTER_ALL;
    private String searchQuery = "";
    private int scopePillarFilter = -1;
    private int scopeModeFilter = SCOPE_MODE_ALL;

    private final LinkedHashMap<String, Integer> policyMap = new LinkedHashMap<>();
    private final HashMap<String, Integer> pkgToAppId = new HashMap<>();
    private final HashMap<Integer, String> appIdToName = new HashMap<>();
    private final List<InstalledApp> installedApps = new ArrayList<>();
    private final HashMap<String, ManifestRadar.ScanReport> radarReports = new HashMap<>();
    private TelemetryClient.TelemetrySnapshot lastTelemetry = TelemetryClient.TelemetrySnapshot.offline();
    private String certSha256 = "UNAVAILABLE";

    private final List<PixelKeycapButton> themedKeys = new ArrayList<>();
    private final List<PixelPanelView> themedPanels = new ArrayList<>();
    private final List<PixelTextView> themedTexts = new ArrayList<>();

    private LinearLayout rootLayout;
    private PixelHeaderView headerView;
    private PixelTextView statusBanner;
    private PixelDockView dockView;

    private LinearLayout guardTab;
    private PixelSearchBarView searchBar;
    private PixelKeycapButton[] filterBtns;
    private PixelKeycapButton btnShowSys;
    private AppPolicyAdapter guardAdapter;

    private LinearLayout scopeTab;
    private PixelScopeView scopeView;
    private PixelCounterStrip counterStrip;
    private PixelKeycapButton btnScopeAll;
    private PixelKeycapButton btnScopeEnf;
    private PixelKeycapButton btnScopeAud;
    private ConsoleAdapters.EventLogAdapter scopeAdapter;

    private LinearLayout radarTab;
    private PixelTextView radarSummaryTxt;
    private ConsoleAdapters.RadarAdapter radarAdapter;

    private ScrollView vaultTab;
    private PixelHealthMatrixView healthMatrixView;
    private PixelCoherenceBarView coherenceBarView;
    private PixelKeycapButton[] paletteBtns;
    private PixelKeycapButton btnToggleIcons;
    private PixelKeycapButton btnToggleHaptic;
    private PixelKeycapButton btnPollRate;
    private PixelKeycapButton btnDefaultAz;
    private PixelTextView infoFrameworkTxt;
    private PixelTextView infoIpcTxt;
    private PixelTextView infoCertTxt;

    private final Runnable hideBannerTask = () -> {
        if (statusBanner != null) {
            statusBanner.setVisibility(View.GONE);
        }
    };

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            if (!activityDestroyed) {
                pollTelemetryAsync();
                mainHandler.postDelayed(this, pollIntervalMs);
            }
        }
    };

    private static final class InstalledApp {
        final String packageName;
        final String label;
        final int appId;
        final boolean isSystem;

        InstalledApp(String packageName, String label, int appId, boolean isSystem) {
            this.packageName = packageName;
            this.label = label;
            this.appId = appId;
            this.isSystem = isSystem;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        m = PixelMetrics.from(this);
        uiPrefs = getSharedPreferences(PolicyFlags.UI_PREFS, MODE_PRIVATE);
        themeMode = uiPrefs.getInt(PolicyFlags.KEY_UI_THEME, PixelPalette.MODE_CRT);
        pixelIcons = uiPrefs.getBoolean(PolicyFlags.KEY_UI_PIXEL_ICON, true);
        hapticEnabled = uiPrefs.getBoolean(PolicyFlags.KEY_UI_HAPTIC, true);
        showSystemApps = uiPrefs.getBoolean(PolicyFlags.KEY_UI_SHOW_SYS, false);
        pollIntervalMs = Math.max(1500, Math.min(5000, uiPrefs.getInt(PolicyFlags.KEY_UI_POLL_MS, 2000)));
        palette = PixelPalette.of(themeMode);
        PixelKeycapButton.setHapticsEnabled(hapticEnabled);

        buildLayout();
        setContentView(rootLayout);
        applySystemBarColors();

        certSha256 = readSigningCertSha256();
        String localRaw = uiPrefs.getString(PolicyFlags.KEY_LOCAL_POLICY, "");
        if (localRaw != null && !localRaw.isEmpty()) {
            policyMap.putAll(PolicyCodec.decodePolicyMap(localRaw));
        }
        registerXposedServiceListener();
        loadAppsAndScanAsync();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mainHandler.removeCallbacks(pollTask);
        mainHandler.post(pollTask);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(pollTask);
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        if (guardAdapter != null) {
            guardAdapter.shutdown();
        }
        bgExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT_JSON) {
            String payload = PolicyCodec.exportJson(BuildConfig.VERSION_NAME, defaultAzMode, policyMap);
            runBg(() -> {
                boolean ok = writeUriText(uri, payload);
                postUi(() -> showBanner(ok ? "EXPORTED JSON BACKUP" : "EXPORT FAILED", !ok));
            });
        } else if (requestCode == REQ_IMPORT_JSON) {
            runBg(() -> {
                String text = readUriText(uri);
                postUi(() -> applyImportedJson(text));
            });
        }
    }

    private void registerXposedServiceListener() {
        try {
            XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
                @Override
                public void onServiceBind(XposedService service) {
                    xposedService = service;
                    try {
                        SharedPreferences rp = service.getRemotePreferences(PolicyFlags.PREFS_GROUP);
                        remotePrefs = rp;
                        syncInitialFromRemote(rp);
                    } catch (Throwable ignored) {
                    }
                    postUi(() -> {
                        refreshAllViews();
                        pollTelemetryAsync();
                    });
                }

                @Override
                public void onServiceDied(XposedService service) {
                    xposedService = null;
                    remotePrefs = null;
                    postUi(() -> refreshAllViews());
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void syncInitialFromRemote(SharedPreferences rp) {
        if (rp == null) {
            return;
        }
        try {
            int remDefault = rp.getInt(PolicyFlags.KEY_DEFAULT_AZ, PolicyFlags.AZ_OFF) & PolicyFlags.AZ_MASK;
            LinkedHashMap<String, Integer> remoteMap = PolicyCodec.decodePolicyMap(rp.getString(PolicyFlags.KEY_POLICY_MAP, ""));
            if (remoteMap.isEmpty()) {
                remoteMap = PolicyCodec.decodeLegacyV20(
                    rp.getString(PolicyFlags.KEY_LEGACY_V20_MODES, ""),
                    rp.getStringSet(PolicyFlags.KEY_LEGACY_V20_TARGETS, null)
                );
            }
            if (!remoteMap.isEmpty()) {
                synchronized (policyMap) {
                    policyMap.clear();
                    policyMap.putAll(remoteMap);
                }
                defaultAzMode = remDefault;
                uiPrefs.edit().putString(PolicyFlags.KEY_LOCAL_POLICY, PolicyCodec.encodePolicyMap(policyMap)).apply();
            } else if (!policyMap.isEmpty()) {
                persistPolicies();
            } else {
                rp.edit().putInt(PolicyFlags.KEY_SELF_APP_ID, PolicyEngine.toAppId(getApplicationInfo().uid)).apply();
            }
        } catch (Throwable ignored) {
        }
    }

    private void persistPolicies() {
        String encodedPkg;
        String encodedIds;
        synchronized (policyMap) {
            encodedPkg = PolicyCodec.encodePolicyMap(policyMap);
            encodedIds = PolicyCodec.encodeAppIdMap(policyMap, pkgToAppId);
        }
        uiPrefs.edit().putString(PolicyFlags.KEY_LOCAL_POLICY, encodedPkg).apply();
        SharedPreferences rp = remotePrefs;
        if (rp != null) {
            try {
                rp.edit()
                    .putString(PolicyFlags.KEY_POLICY_MAP, encodedPkg)
                    .putString(PolicyFlags.KEY_APPID_MAP, encodedIds)
                    .putInt(PolicyFlags.KEY_DEFAULT_AZ, defaultAzMode)
                    .putInt(PolicyFlags.KEY_SELF_APP_ID, PolicyEngine.toAppId(getApplicationInfo().uid))
                    .apply();
            } catch (Throwable ignored) {
            }
        }
    }

    private void buildLayout() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(palette.bg);
        rootLayout.setFitsSystemWindows(true);

        headerView = new PixelHeaderView(this, m, palette);
        headerView.setOnPaletteTapListener(() -> setThemeMode(PixelPalette.nextMode(themeMode)));
        rootLayout.addView(headerView, lpMW(0));

        statusBanner = newText(true, PixelTextView.ROLE_ACCENT);
        statusBanner.setAlignCenter(true);
        statusBanner.setPadding(m.u * 3, m.u * 2, m.u * 3, m.u * 2);
        statusBanner.setVisibility(View.GONE);
        rootLayout.addView(statusBanner, lpMW(0));

        FrameLayout contentFrame = new FrameLayout(this);
        rootLayout.addView(contentFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        buildGuardTab();
        buildScopeTab();
        buildRadarTab();
        buildVaultTab();

        FrameLayout.LayoutParams fill = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        contentFrame.addView(guardTab, fill);
        contentFrame.addView(scopeTab, fill);
        contentFrame.addView(radarTab, fill);
        contentFrame.addView(vaultTab, fill);

        dockView = new PixelDockView(this, m, palette);
        dockView.setOnTabSelectedListener(this::switchTab);
        rootLayout.addView(dockView, lpMW(0));
        switchTab(0);
    }

    private void buildGuardTab() {
        int u = m.u;
        guardTab = new LinearLayout(this);
        guardTab.setOrientation(LinearLayout.VERTICAL);

        LinearLayout ctrlBox = new LinearLayout(this);
        ctrlBox.setOrientation(LinearLayout.VERTICAL);
        ctrlBox.setPadding(u * 3, u * 3, u * 3, u * 2);
        guardTab.addView(ctrlBox, lpMW(0));

        searchBar = new PixelSearchBarView(this, m, palette);
        searchBar.setOnQueryListener(q -> {
            searchQuery = q != null ? q.trim().toLowerCase(Locale.US) : "";
            refreshGuardList();
        });
        ctrlBox.addView(searchBar, lpMW(0));

        LinearLayout filterRow = newRow(ctrlBox, u * 2);
        String[] labels = {"ALL", "GUARD", "AUDIT", "FIN", "SAFE"};
        filterBtns = new PixelKeycapButton[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            PixelKeycapButton b = newKey(labels[i], null, true);
            b.setOnClickListener(v -> { guardFilter = idx; updateFilterButtons(); refreshGuardList(); });
            filterBtns[i] = b;
            filterRow.addView(b);
        }
        btnShowSys = newKey("SYS", null, false);
        btnShowSys.setOnClickListener(v -> {
            showSystemApps = !showSystemApps;
            uiPrefs.edit().putBoolean(PolicyFlags.KEY_UI_SHOW_SYS, showSystemApps).apply();
            updateFilterButtons();
            refreshGuardList();
        });
        filterRow.addView(btnShowSys);

        LinearLayout batchRow = newRow(ctrlBox, u * 2);
        PixelKeycapButton btnAuto = newKey("AUTO BANK", PixelSpriteAtlas.CTRL_CHECK, true);
        btnAuto.setOnClickListener(v -> applyFinanceBatch(false));
        PixelKeycapButton btnAudit = newKey("AUDIT BANK", PixelSpriteAtlas.CTRL_AUDIT, true);
        btnAudit.setOnClickListener(v -> applyFinanceBatch(true));
        PixelKeycapButton btnClear = newKey("CLEAR", null, false);
        btnClear.setOnClickListener(v -> {
            synchronized (policyMap) {
                policyMap.clear();
            }
            persistPolicies();
            refreshAllViews();
            showBanner("CLEARED ALL PROFILES", false);
        });
        batchRow.addView(btnAuto);
        batchRow.addView(btnAudit);
        batchRow.addView(btnClear);

        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setItemAnimator(null);
        guardAdapter = new AppPolicyAdapter(this, m, palette, pixelIcons, new AppPolicyAdapter.OnAppActionListener() {
            @Override
            public void onFlagsUpdated(String packageName, int newFlags) {
                updateAppFlags(packageName, newFlags);
            }

            @Override
            public void onOpenInspector(AppPolicyAdapter.AppRow row) {
                AppInspectorDialog.show(
                    MainActivity.this, m, palette, row,
                    guardAdapter.getCachedIcon(row.packageName), pixelIcons,
                    (pkg, nextFlags) -> updateAppFlags(pkg, nextFlags)
                );
            }
        });
        rv.setAdapter(guardAdapter);
        guardTab.addView(rv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        updateFilterButtons();
    }

    private void buildScopeTab() {
        int u = m.u;
        scopeTab = new LinearLayout(this);
        scopeTab.setOrientation(LinearLayout.VERTICAL);
        scopeTab.setPadding(u * 3, u * 3, u * 3, u * 2);

        scopeView = new PixelScopeView(this, m, palette);
        scopeTab.addView(scopeView, lpMW(0));

        counterStrip = new PixelCounterStrip(this, m, palette);
        counterStrip.setOnPillarFilterListener(p -> { scopePillarFilter = p; refreshScopeEvents(); });
        scopeTab.addView(counterStrip, lpMW(u * 2));

        LinearLayout modeRow = newRow(scopeTab, u * 2);
        btnScopeAll = newKey("ALL", null, true);
        btnScopeEnf = newKey("ENF", null, true);
        btnScopeAud = newKey("AUD", null, true);
        PixelKeycapButton btnExp = newKey("EXPORT", PixelSpriteAtlas.CTRL_EXPORT, true);
        PixelKeycapButton btnPurge = newKey("PURGE", null, false);

        btnScopeAll.setOnClickListener(v -> { scopeModeFilter = SCOPE_MODE_ALL; updateScopeButtons(); refreshScopeEvents(); });
        btnScopeEnf.setOnClickListener(v -> { scopeModeFilter = SCOPE_MODE_ENF; updateScopeButtons(); refreshScopeEvents(); });
        btnScopeAud.setOnClickListener(v -> { scopeModeFilter = SCOPE_MODE_AUD; updateScopeButtons(); refreshScopeEvents(); });
        btnExp.setOnClickListener(v -> {
            copyToClipboard("IsolPolicy Trace", lastTelemetry.formatTraceReport(appIdToName));
            showBanner("TRACE COPIED TO CLIPBOARD", false);
        });
        btnPurge.setOnClickListener(v -> {
            scopeView.resetPeaks();
            runBg(() -> {
                boolean ok = TelemetryClient.purge(MainActivity.this);
                postUi(() -> { showBanner(ok ? "COUNTERS PURGED" : "IPC OFFLINE", !ok); pollTelemetryAsync(); });
            });
        });
        modeRow.addView(btnScopeAll);
        modeRow.addView(btnScopeEnf);
        modeRow.addView(btnScopeAud);
        modeRow.addView(btnExp);
        modeRow.addView(btnPurge);

        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setItemAnimator(null);
        scopeAdapter = new ConsoleAdapters.EventLogAdapter(m, palette);
        rv.setAdapter(scopeAdapter);
        LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        srLp.topMargin = u * 2;
        scopeTab.addView(rv, srLp);
        updateScopeButtons();
    }

    private void buildRadarTab() {
        int u = m.u;
        radarTab = new LinearLayout(this);
        radarTab.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(u * 3, u * 3, u * 3, u * 2);
        radarTab.addView(bar, lpMW(0));

        radarSummaryTxt = newText(true, PixelTextView.ROLE_ACCENT);
        radarSummaryTxt.setText("SCANNING APKS");
        bar.addView(radarSummaryTxt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        PixelKeycapButton btnRescan = new PixelKeycapButton(this, m, palette);
        btnRescan.setCompact(true);
        btnRescan.setLabel("RESCAN");
        btnRescan.setIconSprite(PixelSpriteAtlas.CTRL_REFRESH);
        btnRescan.setOnClickListener(v -> { loadAppsAndScanAsync(); showBanner("RESCANNING PACKAGES", false); });
        themedKeys.add(btnRescan);
        bar.addView(btnRescan, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setItemAnimator(null);
        radarAdapter = new ConsoleAdapters.RadarAdapter(m, palette, (pkg, flags) -> {
            updateAppFlags(pkg, flags);
            showBanner("APPLIED PROFILE TO " + pkg, false);
        });
        rv.setAdapter(radarAdapter);
        radarTab.addView(rv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void buildVaultTab() {
        int u = m.u;
        vaultTab = new ScrollView(this);
        vaultTab.setFillViewport(true);

        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(u * 3, u * 3, u * 3, u * 6);
        vaultTab.addView(c, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        PixelPanelView pHealth = newPanel(c, "SYSTEM SERVER HOOK MATRIX", "10 PILLARS", 0);
        healthMatrixView = new PixelHealthMatrixView(this, m, palette);
        pHealth.addView(healthMatrixView, lpMW(0));

        PixelPanelView pCoh = newPanel(c, "HOST VS GUARDED COHERENCE", "LIVE", u * 3);
        coherenceBarView = new PixelCoherenceBarView(this, m, palette);
        pCoh.addView(coherenceBarView, lpMW(0));

        PixelPanelView pDisp = newPanel(c, "HARDWARE DISPLAY AND ENGINE", "6 PALETTES", u * 3);
        paletteBtns = new PixelKeycapButton[PixelPalette.MODE_COUNT];
        for (int r = 0; r < 2; r++) {
            LinearLayout row = newRow(pDisp, r > 0 ? u * 2 : 0);
            for (int col = 0; col < 3; col++) {
                final int mode = r * 3 + col;
                PixelKeycapButton pb = newKey(PixelPalette.of(mode).shortName, null, col < 2);
                pb.setPalettePreview(PixelPalette.of(mode));
                pb.setOnClickListener(v -> setThemeMode(mode));
                paletteBtns[mode] = pb;
                row.addView(pb);
            }
        }
        LinearLayout o1 = newRow(pDisp, u * 3);
        btnToggleIcons = newKey("ICON PIXEL-16", null, true);
        btnToggleIcons.setOnClickListener(v -> {
            pixelIcons = !pixelIcons;
            uiPrefs.edit().putBoolean(PolicyFlags.KEY_UI_PIXEL_ICON, pixelIcons).apply();
            guardAdapter.setPixelIcons(pixelIcons);
            refreshVaultPanel();
        });
        btnToggleHaptic = newKey("HAPTIC ON", null, false);
        btnToggleHaptic.setOnClickListener(v -> {
            hapticEnabled = !hapticEnabled;
            PixelKeycapButton.setHapticsEnabled(hapticEnabled);
            uiPrefs.edit().putBoolean(PolicyFlags.KEY_UI_HAPTIC, hapticEnabled).apply();
            refreshVaultPanel();
        });
        o1.addView(btnToggleIcons);
        o1.addView(btnToggleHaptic);

        LinearLayout o2 = newRow(pDisp, u * 2);
        btnPollRate = newKey("POLL 2.0S", null, true);
        btnPollRate.setOnClickListener(v -> {
            pollIntervalMs = pollIntervalMs <= 1500 ? 2000 : (pollIntervalMs <= 2000 ? 3500 : (pollIntervalMs <= 3500 ? 5000 : 1500));
            uiPrefs.edit().putInt(PolicyFlags.KEY_UI_POLL_MS, pollIntervalMs).apply();
            refreshVaultPanel();
        });
        btnDefaultAz = newKey("DEF AZ OFF", null, false);
        btnDefaultAz.setOnClickListener(v -> {
            defaultAzMode = (defaultAzMode + 1) & PolicyFlags.AZ_MASK;
            persistPolicies();
            refreshVaultPanel();
        });
        o2.addView(btnPollRate);
        o2.addView(btnDefaultAz);

        PixelPanelView pVault = newPanel(c, "POLICY VAULT", "JSON V2.2", u * 3);
        LinearLayout bk1 = newRow(pVault, 0);
        PixelKeycapButton btnExp = newKey("EXPORT FILE", PixelSpriteAtlas.CTRL_EXPORT, true);
        btnExp.setOnClickListener(v -> launchSaf(Intent.ACTION_CREATE_DOCUMENT, "application/json", "isolpolicy-v" + BuildConfig.VERSION_NAME + "-backup.json", REQ_EXPORT_JSON));
        PixelKeycapButton btnImp = newKey("IMPORT FILE", PixelSpriteAtlas.CTRL_IMPORT, false);
        btnImp.setOnClickListener(v -> launchSaf(Intent.ACTION_OPEN_DOCUMENT, "*/*", null, REQ_IMPORT_JSON));
        bk1.addView(btnExp);
        bk1.addView(btnImp);

        LinearLayout bk2 = newRow(pVault, u * 2);
        PixelKeycapButton btnCopy = newKey("COPY JSON", null, true);
        btnCopy.setOnClickListener(v -> {
            copyToClipboard("IsolPolicy JSON", PolicyCodec.exportJson(BuildConfig.VERSION_NAME, defaultAzMode, policyMap));
            showBanner("JSON COPIED TO CLIPBOARD", false);
        });
        PixelKeycapButton btnPaste = newKey("PASTE JSON", null, false);
        btnPaste.setOnClickListener(v -> applyImportedJson(readClipboardText()));
        bk2.addView(btnCopy);
        bk2.addView(btnPaste);

        PixelPanelView pInfo = newPanel(c, "MODULE IDENTITY", "V" + BuildConfig.VERSION_NAME, u * 3);
        infoFrameworkTxt = newText(true, PixelTextView.ROLE_MUTED);
        infoIpcTxt = newText(true, PixelTextView.ROLE_MUTED);
        infoCertTxt = newText(true, PixelTextView.ROLE_MUTED);
        pInfo.addView(infoFrameworkTxt, lpMW(u));
        pInfo.addView(infoIpcTxt, lpMW(u * 2));
        pInfo.addView(infoCertTxt, lpMW(u * 2));

        PixelKeycapButton btnCert = new PixelKeycapButton(this, m, palette);
        btnCert.setCompact(true);
        btnCert.setLabel("COPY SIGNING CERT SHA256");
        btnCert.setOnClickListener(v -> {
            copyToClipboard("IsolPolicy Cert SHA-256", certSha256);
            showBanner("CERT SHA256 COPIED", false);
        });
        themedKeys.add(btnCert);
        pInfo.addView(btnCert, lpMW(u * 3));
    }

    private void launchSaf(String action, String mime, String title, int reqCode) {
        Intent i = new Intent(action);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        if (title != null) {
            i.putExtra(Intent.EXTRA_TITLE, title);
        }
        try {
            startActivityForResult(i, reqCode);
        } catch (Throwable ignored) {
            showBanner("NO DOCUMENT PICKER", true);
        }
    }

    private LinearLayout.LayoutParams lpMW(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    private LinearLayout newRow(ViewGroup parent, int topMargin) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, lpMW(topMargin));
        return row;
    }

    private PixelPanelView newPanel(ViewGroup parent, String title, String right, int topMargin) {
        PixelPanelView p = new PixelPanelView(this, m, palette);
        p.setHeader(title, right);
        themedPanels.add(p);
        parent.addView(p, lpMW(topMargin));
        return p;
    }

    private PixelTextView newText(boolean micro, int role) {
        PixelTextView tv = new PixelTextView(this, m, palette);
        tv.setMicroMode(micro);
        tv.setScale(micro ? m.microScale : m.fontScale);
        tv.setColorRole(role);
        themedTexts.add(tv);
        return tv;
    }

    private PixelKeycapButton newKey(String label, String[] icon, boolean rightGap) {
        PixelKeycapButton b = new PixelKeycapButton(this, m, palette);
        b.setCompact(true);
        b.setLabel(label);
        if (icon != null) {
            b.setIconSprite(icon);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (rightGap) {
            lp.rightMargin = m.u * 2;
        }
        b.setLayoutParams(lp);
        themedKeys.add(b);
        return b;
    }

    private void switchTab(int tab) {
        activeTab = tab;
        dockView.setActiveTab(tab);
        guardTab.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        scopeTab.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        radarTab.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
        vaultTab.setVisibility(tab == 3 ? View.VISIBLE : View.GONE);
        if (tab == 1) {
            refreshScopeEvents();
        } else if (tab == 2) {
            refreshRadarList();
        } else if (tab == 3) {
            refreshVaultPanel();
        }
    }

    private void setThemeMode(int mode) {
        themeMode = ((mode % PixelPalette.MODE_COUNT) + PixelPalette.MODE_COUNT) % PixelPalette.MODE_COUNT;
        palette = PixelPalette.of(themeMode);
        uiPrefs.edit().putInt(PolicyFlags.KEY_UI_THEME, themeMode).apply();
        rootLayout.setBackgroundColor(palette.bg);
        headerView.setPalette(palette);
        dockView.setPalette(palette);
        searchBar.setPalette(palette);
        scopeView.setPalette(palette);
        counterStrip.setPalette(palette);
        healthMatrixView.setPalette(palette);
        coherenceBarView.setPalette(palette);
        for (PixelKeycapButton b : themedKeys) {
            b.setPalette(palette);
        }
        for (PixelPanelView p : themedPanels) {
            p.setPalette(palette);
        }
        for (PixelTextView tv : themedTexts) {
            tv.setPalette(palette);
        }
        guardAdapter.setPalette(palette);
        scopeAdapter.setPalette(palette);
        radarAdapter.setPalette(palette);
        refreshVaultPanel();
        applySystemBarColors();
    }

    private void applySystemBarColors() {
        try {
            Window win = getWindow();
            if (win == null) {
                return;
            }
            win.setStatusBarColor(palette.surface);
            win.setNavigationBarColor(palette.surface);
            View decor = win.getDecorView();
            if (decor != null) {
                decor.post(() -> {
                    try {
                        if (Build.VERSION.SDK_INT >= 30) {
                            WindowInsetsController ctrl = win.getInsetsController();
                            if (ctrl != null) {
                                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                                ctrl.setSystemBarsAppearance(palette.isLight ? mask : 0, mask);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private void loadAppsAndScanAsync() {
        runBg(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> all = pm.getInstalledApplications(0);
            ArrayList<InstalledApp> loaded = new ArrayList<>(all.size());
            HashMap<String, Integer> idMap = new HashMap<>(all.size());
            HashMap<Integer, String> revNames = new HashMap<>(all.size());
            HashMap<String, ManifestRadar.ScanReport> reports = new HashMap<>(all.size());

            for (ApplicationInfo ai : all) {
                if (ai == null || ai.packageName == null) {
                    continue;
                }
                boolean isSys = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                String lbl = ai.packageName;
                try {
                    CharSequence cs = pm.getApplicationLabel(ai);
                    if (cs != null && cs.length() > 0) {
                        lbl = cs.toString();
                    }
                } catch (Throwable ignored) {
                }
                int appId = PolicyEngine.toAppId(ai.uid);
                loaded.add(new InstalledApp(ai.packageName, lbl, appId, isSys));
                idMap.put(ai.packageName, appId);
                if (!revNames.containsKey(appId)) {
                    revNames.put(appId, lbl);
                }
                if (!isSys || policyMap.containsKey(ai.packageName)) {
                    reports.put(ai.packageName, ManifestRadar.scanInstalledApp(pm, ai, lbl));
                } else {
                    reports.put(ai.packageName, ManifestRadar.buildReport(ai.packageName, lbl, 0, 0, 0, Collections.emptyList()));
                }
            }

            postUi(() -> {
                installedApps.clear();
                installedApps.addAll(loaded);
                pkgToAppId.clear();
                pkgToAppId.putAll(idMap);
                appIdToName.clear();
                appIdToName.putAll(revNames);
                radarReports.clear();
                radarReports.putAll(reports);
                if (!policyMap.isEmpty()) {
                    persistPolicies();
                }
                refreshAllViews();
            });
        });
    }

    private void pollTelemetryAsync() {
        runBg(() -> {
            TelemetryClient.TelemetrySnapshot snap = TelemetryClient.fetchSnapshot(MainActivity.this);
            postUi(() -> {
                lastTelemetry = snap;
                refreshHeader();
                if (activeTab == 0) {
                    refreshGuardList();
                } else if (activeTab == 1) {
                    refreshScopeEvents();
                } else if (activeTab == 3) {
                    refreshVaultPanel();
                }
            });
        });
    }

    private void updateAppFlags(String packageName, int newFlags) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        int clean = PolicyFlags.sanitize(newFlags);
        synchronized (policyMap) {
            if (PolicyFlags.isActive(clean)) {
                policyMap.put(packageName, clean);
            } else {
                policyMap.remove(packageName);
            }
        }
        persistPolicies();
        refreshHeader();
        refreshGuardList();
        if (activeTab == 2) {
            refreshRadarList();
        }
    }

    private void applyFinanceBatch(boolean auditMode) {
        int applied = 0;
        synchronized (policyMap) {
            for (InstalledApp app : installedApps) {
                ManifestRadar.ScanReport rep = radarReports.get(app.packageName);
                if (rep != null && rep.isAutoEligible()) {
                    int flags = auditMode ? (rep.recommendedFlags | PolicyFlags.FLAG_AUDIT_ONLY) : rep.recommendedFlags;
                    policyMap.put(app.packageName, PolicyFlags.sanitize(flags));
                    applied++;
                }
            }
        }
        persistPolicies();
        refreshAllViews();
        showBanner((auditMode ? "AUDIT ARMED " : "AUTO ARMED ") + applied + " FINANCE APPS", false);
    }

    private void applyImportedJson(String json) {
        PolicyCodec.ImportResult res = PolicyCodec.importJson(json);
        if (!res.valid) {
            showBanner("IMPORT ERROR " + res.error, true);
            return;
        }
        synchronized (policyMap) {
            policyMap.clear();
            policyMap.putAll(res.policies);
        }
        defaultAzMode = res.defaultAzMode;
        persistPolicies();
        refreshAllViews();
        showBanner("IMPORTED " + res.importedCount + " PROFILES", false);
    }

    private void refreshAllViews() {
        refreshHeader();
        refreshGuardList();
        refreshScopeEvents();
        refreshRadarList();
        refreshVaultPanel();
    }

    private void refreshHeader() {
        TelemetryClient.TelemetrySnapshot s = lastTelemetry;
        boolean hasTripped = false;
        for (int p = 0; p < PolicyFlags.PILLAR_COUNT; p++) {
            if (TelemetryHub.decodePillarHealth(s.healthMask, p) == TelemetryHub.HEALTH_TRIPPED) {
                hasTripped = true;
                break;
            }
        }
        int guarded;
        synchronized (policyMap) {
            guarded = policyMap.size();
        }
        headerView.bindStatus(
            s.connected,
            s.connected && !s.moduleVersion.isEmpty() ? s.moduleVersion : BuildConfig.VERSION_NAME,
            s.healthyPillarCount(),
            hasTripped,
            guarded,
            s.totalEnforced(),
            s.totalAudit()
        );
        dockView.setScopeHasEvents(!s.events.isEmpty());
    }

    private void updateFilterButtons() {
        for (int i = 0; i < filterBtns.length; i++) {
            filterBtns[i].setKeyState(guardFilter == i
                ? (i == FILTER_AUDIT ? PixelKeycapButton.STATE_AUDIT : PixelKeycapButton.STATE_ON)
                : PixelKeycapButton.STATE_OFF);
        }
        btnShowSys.setKeyState(showSystemApps ? PixelKeycapButton.STATE_WARN : PixelKeycapButton.STATE_OFF);
    }

    private void refreshGuardList() {
        ArrayList<AppPolicyAdapter.AppRow> out = new ArrayList<>();
        TelemetryClient.TelemetrySnapshot snap = lastTelemetry;
        for (InstalledApp app : installedApps) {
            int flags;
            synchronized (policyMap) {
                Integer v = policyMap.get(app.packageName);
                flags = v != null ? v : 0;
            }
            if (app.isSystem && !showSystemApps && !PolicyFlags.isActive(flags)) {
                continue;
            }
            ManifestRadar.ScanReport rep = radarReports.get(app.packageName);
            if (!matchesGuardFilter(flags, rep)) {
                continue;
            }
            if (!searchQuery.isEmpty()
                && !app.label.toLowerCase(Locale.US).contains(searchQuery)
                && !app.packageName.toLowerCase(Locale.US).contains(searchQuery)
            ) {
                continue;
            }
            out.add(new AppPolicyAdapter.AppRow(
                app.packageName, app.label, app.appId, app.isSystem, flags, rep,
                snap.getAppTotalHits(app.appId), snap.getAppAuditHits(app.appId), snap.getAppPillars(app.appId)
            ));
        }
        Collections.sort(out, (a, b) -> {
            boolean aAct = PolicyFlags.isActive(a.flags);
            boolean bAct = PolicyFlags.isActive(b.flags);
            if (aAct != bAct) {
                return aAct ? -1 : 1;
            }
            if (a.totalHits != b.totalHits) {
                return Long.compare(b.totalHits, a.totalHits);
            }
            boolean aFin = a.report != null && a.report.isFinance();
            boolean bFin = b.report != null && b.report.isFinance();
            if (aFin != bFin) {
                return aFin ? -1 : 1;
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        guardAdapter.submitList(out);
    }

    private boolean matchesGuardFilter(int flags, ManifestRadar.ScanReport rep) {
        switch (guardFilter) {
            case FILTER_GUARDED:
                return PolicyFlags.isActive(flags);
            case FILTER_AUDIT:
                return PolicyFlags.isActive(flags) && PolicyFlags.isAuditOnly(flags);
            case FILTER_FINANCE:
                return rep != null && rep.isFinance();
            case FILTER_SANDBOX:
                return rep != null && rep.isSandbox();
            default:
                return true;
        }
    }

    private void updateScopeButtons() {
        btnScopeAll.setKeyState(scopeModeFilter == SCOPE_MODE_ALL ? PixelKeycapButton.STATE_ON : PixelKeycapButton.STATE_OFF);
        btnScopeEnf.setKeyState(scopeModeFilter == SCOPE_MODE_ENF ? PixelKeycapButton.STATE_ON : PixelKeycapButton.STATE_OFF);
        btnScopeAud.setKeyState(scopeModeFilter == SCOPE_MODE_AUD ? PixelKeycapButton.STATE_AUDIT : PixelKeycapButton.STATE_OFF);
    }

    private void refreshScopeEvents() {
        TelemetryClient.TelemetrySnapshot snap = lastTelemetry;
        scopeView.bindEvents(snap.events, snap.totalEnforced(), snap.totalAudit());
        counterStrip.bindTotals(snap.pillarEnforced, snap.pillarAudit);

        ArrayList<TelemetryClient.EventRow> filtered = new ArrayList<>();
        for (int i = snap.events.size() - 1; i >= 0; i--) {
            TelemetryClient.EventRow ev = snap.events.get(i);
            if ((scopePillarFilter >= 0 && ev.pillar != scopePillarFilter)
                || (scopeModeFilter == SCOPE_MODE_ENF && ev.isAudit)
                || (scopeModeFilter == SCOPE_MODE_AUD && !ev.isAudit)
            ) {
                continue;
            }
            filtered.add(ev);
        }
        scopeAdapter.submit(filtered, appIdToName);
    }

    private void refreshRadarList() {
        int fin = 0;
        int gen = 0;
        int sbx = 0;
        ArrayList<ConsoleAdapters.RadarItem> list = new ArrayList<>();
        for (InstalledApp app : installedApps) {
            if (app.isSystem) {
                continue;
            }
            ManifestRadar.ScanReport rep = radarReports.get(app.packageName);
            if (rep == null) {
                continue;
            }
            if (rep.isFinance()) {
                fin++;
            } else if (rep.isSandbox()) {
                sbx++;
            } else {
                gen++;
            }
            if (rep.isFinance() || rep.isSandbox() || !rep.sdkTags.isEmpty() || rep.appZygoteServiceCount > 0 || rep.isolatedServiceCount > 0) {
                int curFlags;
                synchronized (policyMap) {
                    Integer v = policyMap.get(app.packageName);
                    curFlags = v != null ? v : 0;
                }
                list.add(new ConsoleAdapters.RadarItem(app.packageName, app.label, rep, curFlags));
            }
        }
        Collections.sort(list, (a, b) -> {
            int oa = a.report.isFinance() ? 0 : (a.report.isSandbox() ? 2 : 1);
            int ob = b.report.isFinance() ? 0 : (b.report.isSandbox() ? 2 : 1);
            if (oa != ob) {
                return oa - ob;
            }
            if (a.report.riskLevel != b.report.riskLevel) {
                return b.report.riskLevel - a.report.riskLevel;
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        radarSummaryTxt.setText("FIN " + fin + "  GEN " + gen + "  SAFE " + sbx);
        radarAdapter.submit(list);
    }

    private void refreshVaultPanel() {
        TelemetryClient.TelemetrySnapshot s = lastTelemetry;
        healthMatrixView.bind(s.connected, s.healthMask, s.pillarTiers, s.pillarFaults, s.apiVersion, s.graphCount);
        coherenceBarView.bind(s.connected, s.hostDev, s.hostAdb, s.hostAdbWifi, s.hostA11y, s.selfAppId, s.graphCount);

        for (int i = 0; i < paletteBtns.length; i++) {
            paletteBtns[i].setKeyState(i == themeMode ? PixelKeycapButton.STATE_ON : PixelKeycapButton.STATE_OFF);
        }
        btnToggleIcons.setLabel(pixelIcons ? "ICON PIXEL-16" : "ICON NATIVE");
        btnToggleIcons.setKeyState(pixelIcons ? PixelKeycapButton.STATE_ON : PixelKeycapButton.STATE_OFF);
        btnToggleHaptic.setLabel(hapticEnabled ? "HAPTIC ON" : "HAPTIC OFF");
        btnToggleHaptic.setKeyState(hapticEnabled ? PixelKeycapButton.STATE_ON : PixelKeycapButton.STATE_OFF);
        btnPollRate.setLabel("POLL " + (pollIntervalMs / 1000f) + "S");
        btnDefaultAz.setLabel("DEF AZ " + PolicyFlags.azLabel(defaultAzMode));
        btnDefaultAz.setKeyState(defaultAzMode != PolicyFlags.AZ_OFF ? PixelKeycapButton.STATE_WARN : PixelKeycapButton.STATE_OFF);

        XposedService xs = xposedService;
        if (xs != null) {
            String fw = "XPOSED OK";
            try {
                fw = "XPOSED " + xs.getFrameworkName().toUpperCase(Locale.US) + " API " + xs.getApiVersion();
            } catch (Throwable ignored) {
            }
            infoFrameworkTxt.setColorRole(PixelTextView.ROLE_ACCENT);
            infoFrameworkTxt.setText(fw);
        } else {
            infoFrameworkTxt.setColorRole(PixelTextView.ROLE_DANGER);
            infoFrameworkTxt.setText("XPOSED SERVICE UNBOUND");
        }

        if (s.connected) {
            infoIpcTxt.setColorRole(PixelTextView.ROLE_ACCENT);
            infoIpcTxt.setText("IPC AUTHENTICATED  SNAP V" + s.snapshotVersion + "  HOOKS " + s.healthyPillarCount() + "/10");
        } else {
            infoIpcTxt.setColorRole(PixelTextView.ROLE_WARN);
            infoIpcTxt.setText("IPC OFFLINE  ENABLE IN LSPOSED AND REBOOT");
        }

        String shortCert = certSha256.length() > 24
            ? certSha256.substring(0, 16) + ".." + certSha256.substring(certSha256.length() - 8)
            : certSha256;
        infoCertTxt.setText("CERT SHA256 " + shortCert);
    }

    private void showBanner(String msg, boolean isError) {
        statusBanner.setText(msg);
        statusBanner.setColorRole(isError ? PixelTextView.ROLE_DANGER : PixelTextView.ROLE_ACCENT);
        statusBanner.setBackgroundColor(palette.well);
        statusBanner.setVisibility(View.VISIBLE);
        mainHandler.removeCallbacks(hideBannerTask);
        mainHandler.postDelayed(hideBannerTask, 2800L);
    }

    private void runBg(Runnable task) {
        if (activityDestroyed || bgExecutor.isShutdown()) {
            return;
        }
        try {
            bgExecutor.execute(() -> {
                if (!activityDestroyed) {
                    try {
                        task.run();
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void postUi(Runnable task) {
        if (activityDestroyed) {
            return;
        }
        mainHandler.post(() -> {
            if (!activityDestroyed && !isFinishing()) {
                try {
                    task.run();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private void copyToClipboard(String label, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText(label, text));
            }
        } catch (Throwable ignored) {
        }
    }

    private String readClipboardText() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
                return cs != null ? cs.toString() : "";
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private boolean writeUriText(Uri uri, String text) {
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os == null) {
                return false;
            }
            os.write(text.getBytes(StandardCharsets.UTF_8));
            os.flush();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String readUriText(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) {
                return "";
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
                if (bos.size() > 1024 * 1024) {
                    break;
                }
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String readSigningCertSha256() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            SigningInfo si = pi != null ? pi.signingInfo : null;
            Signature[] sigs = si != null ? si.getApkContentsSigners() : null;
            if (sigs != null && sigs.length > 0 && sigs[0] != null) {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(sigs[0].toByteArray());
                StringBuilder sb = new StringBuilder(digest.length * 2);
                for (byte value : digest) {
                    int b = value & 0xFF;
                    if (b < 16) {
                        sb.append('0');
                    }
                    sb.append(Integer.toHexString(b).toUpperCase(Locale.US));
                }
                return sb.toString();
            }
        } catch (Throwable ignored) {
        }
        return "UNAVAILABLE";
    }
}
