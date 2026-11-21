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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import io.github.taro902.isolationpolicy.core.PolicyCodec;
import io.github.taro902.isolationpolicy.core.PolicyFlags;
import io.github.taro902.isolationpolicy.radar.ManifestRadar;
import io.github.taro902.isolationpolicy.radar.TelemetryClient;
import io.github.taro902.isolationpolicy.ui.pixel.PixelChipView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelDrawable;
import io.github.taro902.isolationpolicy.ui.pixel.PixelIconQuantizer;
import io.github.taro902.isolationpolicy.ui.pixel.PixelIcons;
import io.github.taro902.isolationpolicy.ui.pixel.PixelScopeView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelTheme;

@SuppressWarnings("deprecation")
public final class MainActivity extends Activity {

    private static final String UI_PREFS = "isolpolicy_ui_state";
    private static final String KEY_UI_THEME = "theme_mode";
    private static final String KEY_UI_PIXEL_ICON = "pixel_icons";
    private static final String KEY_UI_POLL_MS = "poll_ms";
    private static final String KEY_UI_SHOW_SYS = "show_sys";
    private static final String KEY_LOCAL_POLICY = "local_policy_backup";

    private static final int TAB_GUARD = 0;
    private static final int TAB_SCOPE = 1;
    private static final int TAB_RADAR = 2;
    private static final int TAB_VAULT = 3;

    private static final int FILTER_ALL = 0;
    private static final int FILTER_THREATS = 1;
    private static final int FILTER_GUARDED = 2;
    private static final int FILTER_FINANCE = 3;
    private static final int FILTER_SYSTEM = 4;

    private static final int REQ_EXPORT_JSON = 7101;
    private static final int REQ_IMPORT_JSON = 7102;

    private static final AtomicBoolean SERVICE_LISTENER_REGISTERED = new AtomicBoolean(false);
    private static volatile XposedService sBoundService;
    private static volatile WeakReference<MainActivity> sActiveActivity = new WeakReference<>(null);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, Integer> policyMap = new HashMap<>();
    private final List<AppPolicyAdapter.AppItem> allApps = new ArrayList<>();

    private PixelTheme theme;
    private PixelIconQuantizer iconQuantizer;
    private SharedPreferences uiPrefs;
    private AppPolicyAdapter adapter;

    private int activeTab = TAB_GUARD;
    private int activeFilter = FILTER_ALL;
    private int scopePillarFilter = 0;
    private String searchQuery = "";
    private boolean showSystemApps;
    private boolean pixelizeIcons = true;
    private int pollIntervalMs = 1500;
    private boolean streamPaused;
    private boolean activityResumed;
    private long lastTotalCount = -1L;

    private TelemetryClient.SnapshotData lastSnapshot = TelemetryClient.SnapshotData.DISCONNECTED;

    private FrameLayout contentHost;
    private View tabGuardView;
    private View tabScopeView;
    private View tabRadarView;
    private View tabVaultView;
    private final LinearLayout[] dockButtons = new LinearLayout[4];

    private TextView tvStatusBanner;
    private TextView tvPillarStats;
    private PixelScopeView scopeView;
    private final PixelChipView[] filterChips = new PixelChipView[5];

    private TextView tvScopeHeader;
    private TextView tvHookMatrix;
    private TextView tvStreamLog;
    private PixelChipView btnPauseStream;
    private final PixelChipView[] scopeFilterChips = new PixelChipView[8];

    private TextView tvDiagOutput;
    private TextView tvRadarSummary;
    private LinearLayout radarThreatContainer;

    private PixelChipView btnThemeToggle;
    private PixelChipView btnIconToggle;
    private PixelChipView btnPollToggle;
    private PixelChipView btnSysToggle;

    private final Runnable searchDebounceTask = this::applyFilterAndBind;
    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            if (!activityResumed) {
                return;
            }
            pollTelemetryOnce();
            if (pollIntervalMs > 0) {
                mainHandler.postDelayed(this, pollIntervalMs);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sActiveActivity = new WeakReference<>(this);

        uiPrefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
        int themeMode = uiPrefs.getInt(KEY_UI_THEME, PixelTheme.MODE_OBSIDIAN);
        pixelizeIcons = uiPrefs.getBoolean(KEY_UI_PIXEL_ICON, true);
        pollIntervalMs = uiPrefs.getInt(KEY_UI_POLL_MS, 1500);
        showSystemApps = uiPrefs.getBoolean(KEY_UI_SHOW_SYS, false);

        theme = new PixelTheme(this, themeMode);
        iconQuantizer = new PixelIconQuantizer();

        applySystemBarColors();
        loadPoliciesFromAvailableStore();
        buildRootUi();
        registerXposedServiceSingleton();
        loadInstalledAppsAsync();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sActiveActivity = new WeakReference<>(this);
        activityResumed = true;
        mainHandler.removeCallbacks(pollTask);
        mainHandler.post(pollTask);
    }

    @Override
    protected void onPause() {
        super.onPause();
        activityResumed = false;
        mainHandler.removeCallbacks(pollTask);
        mainHandler.removeCallbacks(searchDebounceTask);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        iconQuantizer.shutdown();
        bgExecutor.shutdownNow();
    }

    private void applySystemBarColors() {
        Window w = getWindow();
        if (w == null) {
            return;
        }
        w.setStatusBarColor(theme.bgCanvas);
        w.setNavigationBarColor(theme.bgPanel);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController ctrl = w.getInsetsController();
            if (ctrl != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                ctrl.setSystemBarsAppearance(theme.isDark() ? 0 : mask, mask);
            }
        } else {
            View decor = w.getDecorView();
            int vis = decor.getSystemUiVisibility();
            if (theme.isDark()) {
                vis &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                vis |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decor.setSystemUiVisibility(vis);
        }
    }

    private static void registerXposedServiceSingleton() {
        if (!SERVICE_LISTENER_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        try {
            XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
                @Override
                public void onServiceBind(@NonNull XposedService service) {
                    sBoundService = service;
                    MainActivity act = sActiveActivity.get();
                    if (act != null && !act.isFinishing()) {
                        act.mainHandler.post(act::onXposedServiceReady);
                    }
                }

                @Override
                public void onServiceDied(@NonNull XposedService service) {
                    if (sBoundService == service) {
                        sBoundService = null;
                    }
                    MainActivity act = sActiveActivity.get();
                    if (act != null && !act.isFinishing()) {
                        act.mainHandler.post(act::updateHeaderFromSnapshot);
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void onXposedServiceReady() {
        loadPoliciesFromAvailableStore();
        syncPoliciesToStores();
        rebuildAppFlagsFromMap();
        applyFilterAndBind();
        pollTelemetryOnce();
    }

    private void loadPoliciesFromAvailableStore() {
        Map<String, Integer> loaded = null;
        XposedService svc = sBoundService;
        if (svc != null) {
            try {
                SharedPreferences rp = svc.getRemotePreferences(PolicyFlags.PREFS_GROUP);
                String raw = rp.getString(PolicyFlags.KEY_POLICY_MAP, "");
                loaded = PolicyCodec.decodePolicyMap(raw);
                if (loaded.isEmpty()) {
                    String legacyModes = rp.getString(PolicyFlags.KEY_LEGACY_V20_MODES, "");
                    if (legacyModes != null && !legacyModes.isEmpty()) {
                        PolicyCodec.ImportResult mig = PolicyCodec.importJson("{\"modes\":" + legacyModes + "}");
                        if (mig.valid) {
                            loaded = mig.policies;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (loaded == null || loaded.isEmpty()) {
            String backup = uiPrefs.getString(KEY_LOCAL_POLICY, "");
            Map<String, Integer> localMap = PolicyCodec.decodePolicyMap(backup);
            if (!localMap.isEmpty()) {
                loaded = localMap;
            }
        }
        if (loaded != null) {
            policyMap.clear();
            policyMap.putAll(loaded);
        }
    }

    private void syncPoliciesToStores() {
        String encodedPolicy = PolicyCodec.encodePolicyMap(policyMap);
        uiPrefs.edit().putString(KEY_LOCAL_POLICY, encodedPolicy).apply();

        SparseIntArray appIdFlags = new SparseIntArray();
        SparseArray<String> appIdToPkg = new SparseArray<>();
        for (AppPolicyAdapter.AppItem item : allApps) {
            Integer fl = policyMap.get(item.packageName);
            if (fl != null && fl != 0 && item.appId >= 10000) {
                appIdFlags.put(item.appId, fl);
                appIdToPkg.put(item.appId, item.packageName);
            }
        }
        String encodedAppIds = PolicyCodec.encodeAppIdMap(appIdFlags, appIdToPkg);
        int selfAppId = Process.myUid() % 100000;

        XposedService svc = sBoundService;
        if (svc != null) {
            try {
                SharedPreferences rp = svc.getRemotePreferences(PolicyFlags.PREFS_GROUP);
                rp.edit()
                        .putString(PolicyFlags.KEY_POLICY_MAP, encodedPolicy)
                        .putString(PolicyFlags.KEY_APPID_MAP, encodedAppIds)
                        .putInt(PolicyFlags.KEY_SELF_APP_ID, selfAppId)
                        .putInt(PolicyFlags.KEY_DEFAULT_AZ, PolicyFlags.AZ_OFF)
                        .apply();
            } catch (Throwable ignored) {
            }
        }
    }

    private void buildRootUi() {
        int u = theme.px;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.bgCanvas);
        root.setFitsSystemWindows(true);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(u * 5, u * 4, u * 5, u * 3);
        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ImageView brandIcon = new ImageView(this);
        brandIcon.setImageDrawable(PixelIcons.createDrawable(getResources(), PixelIcons.SHIELD_10, 10, u, theme.accentOk));
        LinearLayout.LayoutParams biLp = new LinearLayout.LayoutParams(u * 10, u * 10);
        biLp.setMarginEnd(u * 3);
        topBar.addView(brandIcon, biLp);

        TextView brandTitle = new TextView(this);
        brandTitle.setTypeface(theme.monoBold);
        brandTitle.setTextSize(14f);
        brandTitle.setTextColor(theme.inkPrimary);
        brandTitle.setText("ISOLPOLICY " + PolicyFlags.VERSION_NAME);
        topBar.addView(brandTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView apiTag = new TextView(this);
        apiTag.setTypeface(theme.monoBold);
        apiTag.setTextSize(10f);
        apiTag.setTextColor(theme.accentInfo);
        apiTag.setText("API102 // SYS");
        topBar.addView(apiTag);

        contentHost = new FrameLayout(this);
        root.addView(contentHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        tabGuardView = buildGuardTab();
        tabScopeView = buildScopeTab();
        tabRadarView = buildRadarTab();
        tabVaultView = buildVaultTab();

        contentHost.addView(tabGuardView);
        contentHost.addView(tabScopeView);
        contentHost.addView(tabRadarView);
        contentHost.addView(tabVaultView);

        LinearLayout dock = buildDockBar();
        root.addView(dock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(root);
        selectTab(TAB_GUARD);
    }

    private View buildGuardTab() {
        int u = theme.px;
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(u * 4, 0, u * 4, u * 2);

        LinearLayout deck = new LinearLayout(this);
        deck.setOrientation(LinearLayout.VERTICAL);
        deck.setBackground(PixelDrawable.panel(theme));
        deck.setPadding(u * 4, u * 4, u * 4, u * 4);
        wrap.addView(deck, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout deckTop = new LinearLayout(this);
        deckTop.setOrientation(LinearLayout.HORIZONTAL);
        deckTop.setGravity(Gravity.CENTER_VERTICAL);
        deck.addView(deckTop, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout statusCol = new LinearLayout(this);
        statusCol.setOrientation(LinearLayout.VERTICAL);
        deckTop.addView(statusCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        tvStatusBanner = new TextView(this);
        tvStatusBanner.setTypeface(theme.monoBold);
        tvStatusBanner.setTextSize(11.5f);
        tvStatusBanner.setTextColor(theme.accentWarn);
        tvStatusBanner.setFontFeatureSettings("tnum");
        tvStatusBanner.setText("DAEMON: PROBING...");
        statusCol.addView(tvStatusBanner);

        tvPillarStats = new TextView(this);
        tvPillarStats.setTypeface(theme.monoRegular);
        tvPillarStats.setTextSize(10f);
        tvPillarStats.setTextColor(theme.inkSecondary);
        tvPillarStats.setFontFeatureSettings("tnum");
        tvPillarStats.setText("AZ:0 ISO:0 SET:0 USB:0 A11Y:0 PKG:0 DISP:0");
        LinearLayout.LayoutParams psLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        psLp.topMargin = u * 2;
        statusCol.addView(tvPillarStats, psLp);

        scopeView = new PixelScopeView(this, theme);
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(theme.dp(110), theme.dp(40));
        scLp.setMarginStart(u * 3);
        deckTop.addView(scopeView, scLp);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        srLp.topMargin = u * 3;
        wrap.addView(searchRow, srLp);

        EditText searchBox = new EditText(this);
        searchBox.setBackground(PixelDrawable.inset(theme));
        searchBox.setTypeface(theme.monoRegular);
        searchBox.setTextSize(12f);
        searchBox.setTextColor(theme.inkPrimary);
        searchBox.setHintTextColor(theme.inkMuted);
        searchBox.setHint("FILTER APP / PACKAGE...");
        searchBox.setSingleLine(true);
        searchBox.setPadding(u * 4, u * 3, u * 4, u * 3);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s != null ? s.toString().trim().toLowerCase(Locale.ROOT) : "";
                mainHandler.removeCallbacks(searchDebounceTask);
                mainHandler.postDelayed(searchDebounceTask, 80L);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        searchRow.addView(searchBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        PixelChipView btnAutoThreats = new PixelChipView(this, theme);
        btnAutoThreats.applyState("+THREATS", theme.chipOffBg, theme.accentWarnBg, theme.accentWarn, theme.accentWarn, true, false);
        btnAutoThreats.setOnClickListener(v -> applyRecommendedToAllThreats());
        LinearLayout.LayoutParams atLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        atLp.setMarginStart(u * 2);
        searchRow.addView(btnAutoThreats, atLp);

        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams fsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        fsLp.topMargin = u * 3;
        fsLp.bottomMargin = u * 3;
        wrap.addView(filterScroll, fsLp);

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterScroll.addView(filterRow);

        String[] labels = {"ALL", "THREATS", "GUARDED", "FINANCE", "SYSTEM"};
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            PixelChipView fc = new PixelChipView(this, theme);
            fc.setOnClickListener(v -> {
                if (idx == FILTER_SYSTEM && !showSystemApps) {
                    showSystemApps = true;
                    uiPrefs.edit().putBoolean(KEY_UI_SHOW_SYS, true).apply();
                    loadInstalledAppsAsync();
                }
                activeFilter = idx;
                refreshFilterChips();
                applyFilterAndBind();
            });
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (i > 0) {
                flp.setMarginStart(u * 2);
            }
            filterRow.addView(fc, flp);
            filterChips[i] = fc;
        }
        refreshFilterChips();

        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setItemAnimator(null);
        rv.setHasFixedSize(false);
        adapter = new AppPolicyAdapter(theme, getPackageManager(), iconQuantizer, pixelizeIcons, new AppPolicyAdapter.ActionListener() {
            @Override
            public void onFlagsChanged(String packageName, int newFlags) {
                updatePackageFlags(packageName, newFlags);
            }

            @Override
            public void onInspectRequested(AppPolicyAdapter.AppItem item) {
                AppInspectorDialog.show(MainActivity.this, theme, item, lastSnapshot, MainActivity.this::updatePackageFlags);
            }
        });
        rv.setAdapter(adapter);
        wrap.addView(rv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        return wrap;
    }

    private View buildScopeTab() {
        int u = theme.px;
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(u * 4, 0, u * 4, u * 2);

        LinearLayout healthCard = new LinearLayout(this);
        healthCard.setOrientation(LinearLayout.VERTICAL);
        healthCard.setBackground(PixelDrawable.panel(theme));
        healthCard.setPadding(u * 4, u * 4, u * 4, u * 4);
        wrap.addView(healthCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        tvScopeHeader = new TextView(this);
        tvScopeHeader.setTypeface(theme.monoBold);
        tvScopeHeader.setTextSize(11.5f);
        tvScopeHeader.setTextColor(theme.accentInfo);
        tvScopeHeader.setFontFeatureSettings("tnum");
        tvScopeHeader.setText("HOOK HEALTH MATRIX // WAITING FOR IPC");
        healthCard.addView(tvScopeHeader);

        tvHookMatrix = new TextView(this);
        tvHookMatrix.setTypeface(theme.monoRegular);
        tvHookMatrix.setTextSize(10.5f);
        tvHookMatrix.setTextColor(theme.inkPrimary);
        tvHookMatrix.setFontFeatureSettings("tnum");
        tvHookMatrix.setText("AZ[--] ISO[--] SET[--] USB[--] A11Y[--] PKG[--] DISP[--]");
        LinearLayout.LayoutParams hmLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        hmLp.topMargin = u * 2;
        healthCard.addView(tvHookMatrix, hmLp);

        LinearLayout ctrlRow = new LinearLayout(this);
        ctrlRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        crLp.topMargin = u * 3;
        wrap.addView(ctrlRow, crLp);

        btnPauseStream = new PixelChipView(this, theme);
        btnPauseStream.applyState("LIVE", theme.chipOffBg, theme.accentOkBg, theme.accentOk, theme.accentOk, true, false);
        btnPauseStream.setOnClickListener(v -> {
            streamPaused = !streamPaused;
            if (streamPaused) {
                btnPauseStream.applyState("PAUSED", theme.chipOffBg, theme.accentWarnBg, theme.accentWarn, theme.accentWarn, true, true);
            } else {
                btnPauseStream.applyState("LIVE", theme.chipOffBg, theme.accentOkBg, theme.accentOk, theme.accentOk, true, true);
                renderScopeStream();
            }
        });
        ctrlRow.addView(btnPauseStream);

        PixelChipView btnCopy = new PixelChipView(this, theme);
        btnCopy.applyState("COPY LOG", theme.chipOffBg, theme.accentInfoBg, theme.accentInfo, theme.accentInfo, true, false);
        btnCopy.setOnClickListener(v -> {
            copyTextToClipboard("IsolPolicy Telemetry", tvStreamLog.getText().toString());
            toast("Telemetry copied to clipboard");
        });
        LinearLayout.LayoutParams cpLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cpLp.setMarginStart(u * 2);
        ctrlRow.addView(btnCopy, cpLp);

        PixelChipView btnPurge = new PixelChipView(this, theme);
        btnPurge.applyState("PURGE", theme.chipOffBg, theme.accentDangerBg, theme.accentDanger, theme.accentDanger, true, false);
        btnPurge.setOnClickListener(v -> bgExecutor.execute(() -> {
            boolean ok = TelemetryClient.purge(getApplicationContext());
            mainHandler.post(() -> {
                scopeView.clear();
                lastTotalCount = 0L;
                toast(ok ? "Ring buffer purged" : "IPC offline");
                pollTelemetryOnce();
            });
        }));
        LinearLayout.LayoutParams pgLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pgLp.setMarginStart(u * 2);
        ctrlRow.addView(btnPurge, pgLp);

        HorizontalScrollView pilScroll = new HorizontalScrollView(this);
        pilScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams psLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        psLp.topMargin = u * 3;
        psLp.bottomMargin = u * 3;
        wrap.addView(pilScroll, psLp);

        LinearLayout pilRow = new LinearLayout(this);
        pilRow.setOrientation(LinearLayout.HORIZONTAL);
        pilScroll.addView(pilRow);

        String[] pNames = {"ALL", "AZ", "ISO", "SET", "USB", "A11Y", "PKG", "DISP"};
        for (int i = 0; i < pNames.length; i++) {
            final int pid = i;
            PixelChipView c = new PixelChipView(this, theme);
            c.setOnClickListener(v -> {
                scopePillarFilter = pid;
                refreshScopeFilterChips();
                renderScopeStream();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.setMarginStart(u * 2);
            pilRow.addView(c, lp);
            scopeFilterChips[i] = c;
        }
        refreshScopeFilterChips();

        ScrollView streamScroll = new ScrollView(this);
        streamScroll.setBackground(PixelDrawable.inset(theme));
        streamScroll.setPadding(u * 4, u * 4, u * 4, u * 4);
        wrap.addView(streamScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        tvStreamLog = new TextView(this);
        tvStreamLog.setTypeface(theme.monoRegular);
        tvStreamLog.setTextSize(10.5f);
        tvStreamLog.setTextColor(theme.inkPrimary);
        tvStreamLog.setTextIsSelectable(true);
        tvStreamLog.setFontFeatureSettings("tnum");
        tvStreamLog.setText("Waiting for system_server telemetry stream...");
        streamScroll.addView(tvStreamLog);

        return wrap;
    }

    private View buildRadarTab() {
        int u = theme.px;
        ScrollView scroll = new ScrollView(this);
        scroll.setPadding(u * 4, 0, u * 4, u * 4);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(wrap);

        LinearLayout diagCard = new LinearLayout(this);
        diagCard.setOrientation(LinearLayout.VERTICAL);
        diagCard.setBackground(PixelDrawable.panel(theme));
        diagCard.setPadding(u * 4, u * 4, u * 4, u * 4);
        wrap.addView(diagCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout diagHeader = new LinearLayout(this);
        diagHeader.setOrientation(LinearLayout.HORIZONTAL);
        diagHeader.setGravity(Gravity.CENTER_VERTICAL);
        diagCard.addView(diagHeader);

        TextView title = new TextView(this);
        title.setTypeface(theme.monoBold);
        title.setTextSize(12f);
        title.setTextColor(theme.accentOk);
        title.setText("SELF-COHERENCE VERIFIER");
        diagHeader.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        PixelChipView btnRunDiag = new PixelChipView(this, theme);
        btnRunDiag.applyState("RUN TEST", theme.chipOffBg, theme.accentOkBg, theme.accentOk, theme.accentOk, true, false);
        btnRunDiag.setOnClickListener(v -> runSelfDiagnosticAsync());
        diagHeader.addView(btnRunDiag);

        tvDiagOutput = new TextView(this);
        tvDiagOutput.setTypeface(theme.monoRegular);
        tvDiagOutput.setTextSize(10.5f);
        tvDiagOutput.setTextColor(theme.inkPrimary);
        tvDiagOutput.setBackground(PixelDrawable.inset(theme));
        tvDiagOutput.setPadding(u * 3, u * 3, u * 3, u * 3);
        tvDiagOutput.setFontFeatureSettings("tnum");
        tvDiagOutput.setText("Tap RUN TEST to probe XposedService, Zero-Oracle IPC, 7 hooks, and host DEV/ADB/A11Y state.");
        LinearLayout.LayoutParams doLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        doLp.topMargin = u * 3;
        diagCard.addView(tvDiagOutput, doLp);

        LinearLayout radarCard = new LinearLayout(this);
        radarCard.setOrientation(LinearLayout.VERTICAL);
        radarCard.setBackground(PixelDrawable.panel(theme));
        radarCard.setPadding(u * 4, u * 4, u * 4, u * 4);
        LinearLayout.LayoutParams rcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rcLp.topMargin = u * 4;
        wrap.addView(radarCard, rcLp);

        LinearLayout rHead = new LinearLayout(this);
        rHead.setOrientation(LinearLayout.HORIZONTAL);
        rHead.setGravity(Gravity.CENTER_VERTICAL);
        radarCard.addView(rHead);

        tvRadarSummary = new TextView(this);
        tvRadarSummary.setTypeface(theme.monoBold);
        tvRadarSummary.setTextSize(11.5f);
        tvRadarSummary.setTextColor(theme.accentWarn);
        tvRadarSummary.setText("STATIC THREAT SURFACE");
        rHead.addView(tvRadarSummary, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        PixelChipView btnRescan = new PixelChipView(this, theme);
        btnRescan.applyState("RESCAN", theme.chipOffBg, theme.accentInfoBg, theme.accentInfo, theme.accentInfo, true, false);
        btnRescan.setOnClickListener(v -> {
            ManifestRadar.clearCache();
            loadInstalledAppsAsync();
            toast("Rescanning APK AXML and native libraries...");
        });
        rHead.addView(btnRescan);

        radarThreatContainer = new LinearLayout(this);
        radarThreatContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        tcLp.topMargin = u * 3;
        radarCard.addView(radarThreatContainer, tcLp);

        return scroll;
    }

    private View buildVaultTab() {
        int u = theme.px;
        ScrollView scroll = new ScrollView(this);
        scroll.setPadding(u * 4, 0, u * 4, u * 4);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(wrap);

        LinearLayout presetCard = new LinearLayout(this);
        presetCard.setOrientation(LinearLayout.VERTICAL);
        presetCard.setBackground(PixelDrawable.panel(theme));
        presetCard.setPadding(u * 4, u * 4, u * 4, u * 4);
        wrap.addView(presetCard);

        presetCard.addView(sectionTitle("BATCH PRESETS (FINANCE & THREATS)"));
        LinearLayout pRow1 = new LinearLayout(this);
        pRow1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pr1Lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pr1Lp.topMargin = u * 3;
        presetCard.addView(pRow1, pr1Lp);

        PixelChipView bBank = new PixelChipView(this, theme);
        bBank.applyState("BANK STRICT", theme.chipOffBg, theme.accentOkBg, theme.accentOk, theme.accentOk, true, false);
        bBank.setOnClickListener(v -> applyPresetToFinanceApps(PolicyFlags.PRESET_BANKING_STRICT, "BANK STRICT"));
        addWeightChip(pRow1, bBank, 0);

        PixelChipView bWallet = new PixelChipView(this, theme);
        bWallet.applyState("WALLET/ALIPAY", theme.chipOffBg, theme.accentInfoBg, theme.accentInfo, theme.accentInfo, true, false);
        bWallet.setOnClickListener(v -> applyPresetToFinanceApps(PolicyFlags.PRESET_WALLET_ALIPAY, "WALLET/ALIPAY"));
        addWeightChip(pRow1, bWallet, u * 2);

        LinearLayout pRow2 = new LinearLayout(this);
        pRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pr2Lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pr2Lp.topMargin = u * 2;
        presetCard.addView(pRow2, pr2Lp);

        PixelChipView bMax = new PixelChipView(this, theme);
        bMax.applyState("AUDIT MAX", theme.chipOffBg, theme.accentWarnBg, theme.accentWarn, theme.accentWarn, true, false);
        bMax.setOnClickListener(v -> applyPresetToFinanceApps(PolicyFlags.PRESET_AUDIT_MAX, "AUDIT MAX"));
        addWeightChip(pRow2, bMax, 0);

        PixelChipView bClear = new PixelChipView(this, theme);
        bClear.applyState("RESET ALL", theme.chipOffBg, theme.accentDangerBg, theme.accentDanger, theme.accentDanger, true, false);
        bClear.setOnClickListener(v -> {
            policyMap.clear();
            syncPoliciesToStores();
            rebuildAppFlagsFromMap();
            applyFilterAndBind();
            toast("All package policies cleared");
        });
        addWeightChip(pRow2, bClear, u * 2);

        LinearLayout ioCard = new LinearLayout(this);
        ioCard.setOrientation(LinearLayout.VERTICAL);
        ioCard.setBackground(PixelDrawable.panel(theme));
        ioCard.setPadding(u * 4, u * 4, u * 4, u * 4);
        LinearLayout.LayoutParams ioLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ioLp.topMargin = u * 4;
        wrap.addView(ioCard, ioLp);

        ioCard.addView(sectionTitle("JSON v2.1.0 BACKUP (COMPATIBLE v2.0)"));
        LinearLayout ioRow1 = new LinearLayout(this);
        ioRow1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ior1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ior1.topMargin = u * 3;
        ioCard.addView(ioRow1, ior1);

        PixelChipView btnClipExp = new PixelChipView(this, theme);
        btnClipExp.applyState("COPY JSON", theme.chipOffBg, theme.accentOkBg, theme.accentOk, theme.accentOk, true, false);
        btnClipExp.setOnClickListener(v -> {
            String json = PolicyCodec.exportJson(policyMap, PolicyFlags.AZ_OFF);
            copyTextToClipboard("IsolPolicy JSON", json);
            toast("Exported " + policyMap.size() + " policies to clipboard");
        });
        addWeightChip(ioRow1, btnClipExp, 0);

        PixelChipView btnClipImp = new PixelChipView(this, theme);
        btnClipImp.applyState("PASTE JSON", theme.chipOffBg, theme.accentInfoBg, theme.accentInfo, theme.accentInfo, true, false);
        btnClipImp.setOnClickListener(v -> importJsonFromClipboard());
        addWeightChip(ioRow1, btnClipImp, u * 2);

        LinearLayout ioRow2 = new LinearLayout(this);
        ioRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ior2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ior2.topMargin = u * 2;
        ioCard.addView(ioRow2, ior2);

        PixelChipView btnFileExp = new PixelChipView(this, theme);
        btnFileExp.applyState("SAVE .JSON", theme.chipOffBg, theme.bgCard, theme.borderOuter, theme.inkPrimary, true, false);
        btnFileExp.setOnClickListener(v -> launchExportFilePicker());
        addWeightChip(ioRow2, btnFileExp, 0);

        PixelChipView btnFileImp = new PixelChipView(this, theme);
        btnFileImp.applyState("LOAD .JSON", theme.chipOffBg, theme.bgCard, theme.borderOuter, theme.inkPrimary, true, false);
        btnFileImp.setOnClickListener(v -> launchImportFilePicker());
        addWeightChip(ioRow2, btnFileImp, u * 2);

        LinearLayout uiCard = new LinearLayout(this);
        uiCard.setOrientation(LinearLayout.VERTICAL);
        uiCard.setBackground(PixelDrawable.panel(theme));
        uiCard.setPadding(u * 4, u * 4, u * 4, u * 4);
        LinearLayout.LayoutParams uiLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        uiLp.topMargin = u * 4;
        wrap.addView(uiCard, uiLp);

        uiCard.addView(sectionTitle("INSTRUMENT PREFERENCES"));
        LinearLayout prefRow1 = new LinearLayout(this);
        prefRow1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pf1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pf1.topMargin = u * 3;
        uiCard.addView(prefRow1, pf1);

        btnThemeToggle = new PixelChipView(this, theme);
        btnThemeToggle.setOnClickListener(v -> {
            int nextMode = theme.isDark() ? PixelTheme.MODE_PAPER : PixelTheme.MODE_OBSIDIAN;
            uiPrefs.edit().putInt(KEY_UI_THEME, nextMode).apply();
            recreate();
        });
        addWeightChip(prefRow1, btnThemeToggle, 0);

        btnIconToggle = new PixelChipView(this, theme);
        btnIconToggle.setOnClickListener(v -> {
            pixelizeIcons = !pixelizeIcons;
            uiPrefs.edit().putBoolean(KEY_UI_PIXEL_ICON, pixelizeIcons).apply();
            adapter.setPixelizeIcons(pixelizeIcons);
            refreshVaultPrefChips();
        });
        addWeightChip(prefRow1, btnIconToggle, u * 2);

        LinearLayout prefRow2 = new LinearLayout(this);
        prefRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pf2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pf2.topMargin = u * 2;
        uiCard.addView(prefRow2, pf2);

        btnPollToggle = new PixelChipView(this, theme);
        btnPollToggle.setOnClickListener(v -> {
            if (pollIntervalMs == 1500) {
                pollIntervalMs = 3000;
            } else if (pollIntervalMs == 3000) {
                pollIntervalMs = 0;
            } else {
                pollIntervalMs = 1500;
            }
            uiPrefs.edit().putInt(KEY_UI_POLL_MS, pollIntervalMs).apply();
            refreshVaultPrefChips();
            mainHandler.removeCallbacks(pollTask);
            if (activityResumed && pollIntervalMs > 0) {
                mainHandler.postDelayed(pollTask, pollIntervalMs);
            }
        });
        addWeightChip(prefRow2, btnPollToggle, 0);

        btnSysToggle = new PixelChipView(this, theme);
        btnSysToggle.setOnClickListener(v -> {
            showSystemApps = !showSystemApps;
            uiPrefs.edit().putBoolean(KEY_UI_SHOW_SYS, showSystemApps).apply();
            refreshVaultPrefChips();
            loadInstalledAppsAsync();
        });
        addWeightChip(prefRow2, btnSysToggle, u * 2);

        refreshVaultPrefChips();

        LinearLayout recCard = new LinearLayout(this);
        recCard.setOrientation(LinearLayout.VERTICAL);
        recCard.setBackground(PixelDrawable.inset(theme));
        recCard.setPadding(u * 4, u * 4, u * 4, u * 4);
        LinearLayout.LayoutParams recLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        recLp.topMargin = u * 4;
        wrap.addView(recCard, recLp);

        TextView recInfo = new TextView(this);
        recInfo.setTypeface(theme.monoRegular);
        recInfo.setTextSize(10f);
        recInfo.setTextColor(theme.inkSecondary);
        recInfo.setText("ARCHITECTURE: 100% system_server only (0 bytes injected into target apps).\n"
                + "RECOVERY: /data/adb/service.d/99_isolpolicy_watchdog.sh auto-removes /data/app/*/*taro902.isolationpolicy* if boot_completed > 90s.\n"
                + "HMA-OSS: Keep io.github.taro902.isolationpolicy checked in Blacklist template.");
        recCard.addView(recInfo);

        return scroll;
    }

    private LinearLayout buildDockBar() {
        int u = theme.px;
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setBackgroundColor(theme.bgPanel);
        dock.setPadding(u * 3, u * 3, u * 3, u * 3);

        String[] names = {"GUARD", "SCOPE", "RADAR", "VAULT"};
        int[][] icons = {PixelIcons.SHIELD_10, PixelIcons.SCOPE_10, PixelIcons.RADAR_10, PixelIcons.VAULT_10};

        for (int i = 0; i < 4; i++) {
            final int tabIdx = i;
            LinearLayout btn = new LinearLayout(this);
            btn.setOrientation(LinearLayout.VERTICAL);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(u * 2, u * 3, u * 2, u * 3);
            btn.setClickable(true);

            ImageView iv = new ImageView(this);
            iv.setTag(icons[i]);
            btn.addView(iv, new LinearLayout.LayoutParams(u * 8, u * 8));

            TextView tv = new TextView(this);
            tv.setTypeface(theme.monoBold);
            tv.setTextSize(9.5f);
            tv.setText(names[i]);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tvLp.topMargin = u;
            btn.addView(tv, tvLp);

            btn.setOnClickListener(v -> selectTab(tabIdx));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.setMarginStart(u * 2);
            dock.addView(btn, lp);
            dockButtons[i] = btn;
        }
        return dock;
    }

    private void selectTab(int tabIdx) {
        activeTab = tabIdx;
        tabGuardView.setVisibility(tabIdx == TAB_GUARD ? View.VISIBLE : View.GONE);
        tabScopeView.setVisibility(tabIdx == TAB_SCOPE ? View.VISIBLE : View.GONE);
        tabRadarView.setVisibility(tabIdx == TAB_RADAR ? View.VISIBLE : View.GONE);
        tabVaultView.setVisibility(tabIdx == TAB_VAULT ? View.VISIBLE : View.GONE);

        int u = theme.px;
        for (int i = 0; i < 4; i++) {
            boolean sel = (i == tabIdx);
            LinearLayout btn = dockButtons[i];
            btn.setBackground(PixelDrawable.tab(theme, sel));
            ImageView iv = (ImageView) btn.getChildAt(0);
            TextView tv = (TextView) btn.getChildAt(1);
            int tint = sel ? theme.accentOk : theme.inkMuted;
            int[] mask = (int[]) iv.getTag();
            iv.setImageDrawable(PixelIcons.createDrawable(getResources(), mask, 10, Math.max(2, u), tint));
            tv.setTextColor(tint);
        }

        if (tabIdx == TAB_SCOPE) {
            renderScopeStream();
        } else if (tabIdx == TAB_RADAR) {
            renderRadarThreatList();
        }
    }

    private void refreshFilterChips() {
        String[] labels = {"ALL", "THREATS", "GUARDED", "FINANCE", "SYSTEM"};
        for (int i = 0; i < filterChips.length; i++) {
            boolean on = (i == activeFilter);
            if (on) {
                filterChips[i].applyState(labels[i], theme.chipOffBg, theme.accentOkBg, theme.accentOk, theme.accentOk, true, true);
            } else {
                filterChips[i].applyState(labels[i], theme.chipOffBg, theme.chipOffBg, theme.borderOuter, theme.chipOffInk, false, false);
            }
        }
    }

    private void refreshScopeFilterChips() {
        String[] names = {"ALL", "AZ", "ISO", "SET", "USB", "A11Y", "PKG", "DISP"};
        for (int i = 0; i < scopeFilterChips.length; i++) {
            boolean on = (i == scopePillarFilter);
            if (on) {
                scopeFilterChips[i].applyState(names[i], theme.chipOffBg, theme.accentInfoBg, theme.accentInfo, theme.accentInfo, true, true);
            } else {
                scopeFilterChips[i].applyState(names[i], theme.chipOffBg, theme.chipOffBg, theme.borderOuter, theme.chipOffInk, false, false);
            }
        }
    }

    private void refreshVaultPrefChips() {
        String themeLbl = theme.isDark() ? "THEME:OBSIDIAN" : "THEME:PAPER";
        btnThemeToggle.applyState(themeLbl, theme.chipOffBg, theme.accentFocusBg, theme.accentFocus, theme.accentFocus, true, false);

        String iconLbl = pixelizeIcons ? "ICON:PIXEL-16" : "ICON:NATIVE";
        btnIconToggle.applyState(iconLbl, theme.chipOffBg, theme.accentInfoBg, theme.accentInfo, theme.accentInfo, true, false);

        String pollLbl = pollIntervalMs == 0 ? "POLL:MANUAL" : ("POLL:" + (pollIntervalMs / 1000f) + "S");
        btnPollToggle.applyState(pollLbl, theme.chipOffBg, theme.accentOkBg, theme.accentOk, theme.accentOk, true, false);

        String sysLbl = showSystemApps ? "SYSTEM:UNLOCKED" : "SYSTEM:LOCKED";
        btnSysToggle.applyState(sysLbl, theme.chipOffBg,
                showSystemApps ? theme.accentWarnBg : theme.bgCard,
                showSystemApps ? theme.accentWarn : theme.borderOuter,
                showSystemApps ? theme.accentWarn : theme.inkSecondary,
                showSystemApps, false);
    }

    private void loadInstalledAppsAsync() {
        final boolean includeSys = showSystemApps;
        bgExecutor.execute(() -> {
            PackageManager pm = getPackageManager();
            List<PackageInfo> installed;
            try {
                installed = pm.getInstalledPackages(PackageManager.GET_SERVICES);
            } catch (Throwable t) {
                installed = Collections.emptyList();
            }

            List<AppPolicyAdapter.AppItem> built = new ArrayList<>(installed.size());
            for (PackageInfo pi : installed) {
                if (pi == null || pi.applicationInfo == null) {
                    continue;
                }
                String pkg = pi.packageName;
                if (PolicyFlags.MODULE_PACKAGE.equals(pkg)) {
                    continue;
                }
                boolean isSys = (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                Integer existingFlags = policyMap.get(pkg);
                int fl = existingFlags != null ? existingFlags : 0;
                if (isSys && !includeSys && fl == 0) {
                    continue;
                }

                ManifestRadar.ScanReport report = ManifestRadar.scanPackage(getApplicationContext(), pi);
                CharSequence lblCs = pi.applicationInfo.loadLabel(pm);
                String lbl = lblCs != null ? lblCs.toString() : pkg;

                built.add(new AppPolicyAdapter.AppItem(
                        pkg,
                        lbl,
                        pi.applicationInfo.uid,
                        isSys,
                        pi.applicationInfo,
                        fl,
                        report
                ));
            }

            Collections.sort(built, (a, b) -> {
                boolean aGuard = PolicyFlags.isActive(a.flags);
                boolean bGuard = PolicyFlags.isActive(b.flags);
                if (aGuard != bGuard) return aGuard ? -1 : 1;
                if (a.report.threatScore != b.report.threatScore) {
                    return Integer.compare(b.report.threatScore, a.report.threatScore);
                }
                return a.label.compareToIgnoreCase(b.label);
            });

            mainHandler.post(() -> {
                allApps.clear();
                allApps.addAll(built);
                syncPoliciesToStores();
                applyFilterAndBind();
                if (activeTab == TAB_RADAR) {
                    renderRadarThreatList();
                }
            });
        });
    }

    private void rebuildAppFlagsFromMap() {
        for (int i = 0; i < allApps.size(); i++) {
            AppPolicyAdapter.AppItem item = allApps.get(i);
            Integer fl = policyMap.get(item.packageName);
            int next = fl != null ? fl : 0;
            if (item.flags != next) {
                allApps.set(i, item.withFlags(next));
            }
        }
    }

    private void applyFilterAndBind() {
        List<AppPolicyAdapter.AppItem> out = new ArrayList<>();
        for (AppPolicyAdapter.AppItem item : allApps) {
            if (!matchesFilter(item)) {
                continue;
            }
            if (!searchQuery.isEmpty()) {
                boolean hit = item.label.toLowerCase(Locale.ROOT).contains(searchQuery)
                        || item.packageName.toLowerCase(Locale.ROOT).contains(searchQuery);
                if (!hit) {
                    continue;
                }
            }
            out.add(item);
        }
        adapter.submitList(out);
    }

    private boolean matchesFilter(AppPolicyAdapter.AppItem item) {
        switch (activeFilter) {
            case FILTER_THREATS:
                return item.report.isThreat();
            case FILTER_GUARDED:
                return PolicyFlags.isActive(item.flags);
            case FILTER_FINANCE:
                return item.report.financeLikely;
            case FILTER_SYSTEM:
                return item.isSystem;
            default:
                return true;
        }
    }

    private void updatePackageFlags(String packageName, int newFlags) {
        int clean = newFlags & PolicyFlags.ALL_SHIELDS_MASK;
        if (clean == 0) {
            policyMap.remove(packageName);
        } else {
            policyMap.put(packageName, clean);
        }
        for (int i = 0; i < allApps.size(); i++) {
            AppPolicyAdapter.AppItem item = allApps.get(i);
            if (item.packageName.equals(packageName)) {
                allApps.set(i, item.withFlags(clean));
                break;
            }
        }
        syncPoliciesToStores();
        applyFilterAndBind();
    }

    private void applyRecommendedToAllThreats() {
        int count = 0;
        for (int i = 0; i < allApps.size(); i++) {
            AppPolicyAdapter.AppItem item = allApps.get(i);
            int rec = item.report.recommendedFlags;
            if (rec != 0) {
                int merged = item.flags | rec;
                if (PolicyFlags.getAzMode(item.flags) == PolicyFlags.AZ_OFF && PolicyFlags.getAzMode(rec) != PolicyFlags.AZ_OFF) {
                    merged = PolicyFlags.withAzMode(merged, PolicyFlags.getAzMode(rec));
                }
                if (merged != item.flags) {
                    policyMap.put(item.packageName, merged);
                    allApps.set(i, item.withFlags(merged));
                    count++;
                }
            }
        }
        syncPoliciesToStores();
        applyFilterAndBind();
        toast(count > 0 ? ("Guarded " + count + " threat/finance apps") : "All detected threats already guarded");
    }

    private void applyPresetToFinanceApps(int presetFlags, String label) {
        int count = 0;
        for (int i = 0; i < allApps.size(); i++) {
            AppPolicyAdapter.AppItem item = allApps.get(i);
            if (item.report.financeLikely || item.report.isThreat()) {
                policyMap.put(item.packageName, presetFlags);
                allApps.set(i, item.withFlags(presetFlags));
                count++;
            }
        }
        syncPoliciesToStores();
        applyFilterAndBind();
        toast(label + " applied to " + count + " apps");
    }

    private void pollTelemetryOnce() {
        bgExecutor.execute(() -> {
            TelemetryClient.SnapshotData snap = TelemetryClient.fetchSnapshot(getApplicationContext());
            mainHandler.post(() -> {
                lastSnapshot = snap;
                if (snap.connected) {
                    long delta = (lastTotalCount >= 0L && snap.total >= lastTotalCount)
                            ? (snap.total - lastTotalCount)
                            : 0L;
                    lastTotalCount = snap.total;
                    scopeView.pushDelta(delta);
                } else {
                    scopeView.pushDelta(0L);
                }
                updateHeaderFromSnapshot();
                if (activeTab == TAB_SCOPE && !streamPaused) {
                    renderScopeStream();
                }
            });
        });
    }

    private void updateHeaderFromSnapshot() {
        TelemetryClient.SnapshotData s = lastSnapshot;
        if (s.connected) {
            int okHooks = TelemetryClient.countHealthyHooks(s.healthMask);
            tvStatusBanner.setTextColor(okHooks == 7 ? theme.accentOk : theme.accentWarn);
            tvStatusBanner.setText("ACTIVE | HOOKS " + okHooks + "/7 | GUARD:" + policyMap.size() + " | GRAPH:" + s.graphSize);
            tvPillarStats.setText("AZ:" + s.counts[1]
                    + " ISO:" + s.counts[2]
                    + " SET:" + s.counts[3]
                    + " USB:" + s.counts[4]
                    + " A11Y:" + s.counts[5]
                    + " PKG:" + s.counts[6]
                    + " DISP:" + s.counts[7]);
        } else if (sBoundService != null) {
            tvStatusBanner.setTextColor(theme.accentWarn);
            tvStatusBanner.setText("SERVICE BOUND (REBOOT NEEDED FOR SYSTEM HOOKS)");
            tvPillarStats.setText("GUARDED:" + policyMap.size() + " | FRAMEWORK:" + safeFwName());
        } else {
            tvStatusBanner.setTextColor(theme.accentDanger);
            tvStatusBanner.setText("MODULE INACTIVE // ENABLE IN LSPOSED (SCOPE: SYSTEM)");
            tvPillarStats.setText("GUARDED:" + policyMap.size() + " | LOCAL CONFIG ONLY");
        }
    }

    private String safeFwName() {
        XposedService svc = sBoundService;
        if (svc == null) return "-";
        try {
            return svc.getFrameworkName() + " API " + svc.getApiVersion();
        } catch (Throwable t) {
            return "LSPosed";
        }
    }

    private void renderScopeStream() {
        TelemetryClient.SnapshotData s = lastSnapshot;
        if (!s.connected) {
            tvScopeHeader.setText("HOOK HEALTH MATRIX // IPC OFFLINE");
            tvHookMatrix.setText("Activate module in LSPosed Vector (scope: System Framework) and reboot.");
            tvStreamLog.setText("No live connection to system_server Zero-Oracle IPC gate.");
            return;
        }

        long sec = s.uptimeMs / 1000L;
        tvScopeHeader.setText(s.frameworkName + " API " + s.apiVersion
                + " | UP:" + formatUptime(sec)
                + " | TOT:" + s.total
                + " | GRAPH:" + s.graphSize);

        String[] names = {"AZ", "ISO", "SET", "USB", "A11Y", "PKG", "DISP"};
        StringBuilder hm = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            int st = TelemetryClient.decodeHookState(s.healthMask, i);
            String code = st == 1 ? "OK" : (st == 2 ? "FB" : (st == 3 ? "ER" : "--"));
            if (i > 0) hm.append(' ');
            hm.append(names[i]).append('[').append(code).append(']');
        }
        tvHookMatrix.setText(hm.toString());

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
        int shown = 0;
        for (TelemetryClient.EventRecord ev : s.events) {
            if (scopePillarFilter != 0 && ev.pillar != scopePillarFilter) {
                continue;
            }
            sb.append(fmt.format(new Date(ev.timestampMs)))
                    .append(" | ")
                    .append(PolicyFlags.pillarName(ev.pillar))
                    .append(" | ")
                    .append(shortenPkg(ev.packageName))
                    .append(" | ")
                    .append(PolicyFlags.vectorName(ev.vector))
                    .append(" -> ")
                    .append(PolicyFlags.actionName(ev.action))
                    .append('\n');
            shown++;
        }
        if (shown == 0) {
            tvStreamLog.setText("Ring buffer empty for selected filter. Launch a guarded app to observe live interceptions.");
        } else {
            tvStreamLog.setText(sb.toString().trim());
        }
    }

    private void renderRadarThreatList() {
        radarThreatContainer.removeAllViews();
        int u = theme.px;
        int azCount = 0;
        int isoCount = 0;
        int sdkCount = 0;
        int shown = 0;

        for (AppPolicyAdapter.AppItem item : allApps) {
            if (!item.report.isThreat() && !item.report.financeLikely) {
                continue;
            }
            if (item.report.usesAppZygote) azCount++;
            if (!item.report.isolatedServices.isEmpty()) isoCount++;
            if (!item.report.antifraudSdks.isEmpty()) sdkCount++;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackground(PixelDrawable.card(theme, PolicyFlags.isActive(item.flags)));
            row.setPadding(u * 3, u * 3, u * 3, u * 3);
            row.setOnClickListener(v -> AppInspectorDialog.show(this, theme, item, lastSnapshot, this::updatePackageFlags));

            TextView t1 = new TextView(this);
            t1.setTypeface(theme.monoBold);
            t1.setTextSize(11f);
            t1.setTextColor(PolicyFlags.isActive(item.flags) ? theme.accentOk : theme.accentDanger);
            t1.setText(item.label + " [" + item.report.threatScore + "/100] "
                    + (PolicyFlags.isActive(item.flags) ? "GUARDED" : "EXPOSED"));
            row.addView(t1);

            TextView t2 = new TextView(this);
            t2.setTypeface(theme.monoRegular);
            t2.setTextSize(9.5f);
            t2.setTextColor(theme.inkSecondary);
            StringBuilder detail = new StringBuilder(item.packageName);
            if (item.report.usesAppZygote) detail.append(" | AZ");
            if (!item.report.isolatedServices.isEmpty()) detail.append(" | ISO:").append(item.report.isolatedServices.size());
            if (!item.report.antifraudSdks.isEmpty()) detail.append(" | ").append(String.join(",", item.report.antifraudSdks));
            t2.setText(detail.toString());
            row.addView(t2);

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rlp.bottomMargin = u * 2;
            radarThreatContainer.addView(row, rlp);
            shown++;
        }

        tvRadarSummary.setText("SURFACE: " + shown + " TARGETS (AZ:" + azCount + " ISO:" + isoCount + " SDK:" + sdkCount + ")");
        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setTypeface(theme.monoRegular);
            empty.setTextSize(10.5f);
            empty.setTextColor(theme.inkMuted);
            empty.setText("No high-risk AppZygote / Isolated / Antifraud packages detected.");
            radarThreatContainer.addView(empty);
        }
    }

    private void runSelfDiagnosticAsync() {
        tvDiagOutput.setText("Running coherence probes...");
        final boolean svcOk = sBoundService != null;
        int api = 0;
        String fw = "NONE";
        if (svcOk) {
            try {
                api = sBoundService.getApiVersion();
                fw = sBoundService.getFrameworkName();
            } catch (Throwable ignored) {
            }
        }
        final int fApi = api;
        final String fFw = fw;

        bgExecutor.execute(() -> {
            TelemetryClient.DiagnosticResult d = TelemetryClient.runSelfDiagnostic(getApplicationContext(), svcOk, fApi, fFw);
            StringBuilder sb = new StringBuilder();
            sb.append("XposedService : ").append(d.serviceBound ? ("OK (" + d.serviceFw + " API " + d.serviceApi + ")") : "UNBOUND").append('\n');
            sb.append("ZeroOracleIPC : ").append(d.ipcAlive ? ("AUTHENTICATED (v" + d.remoteVer + ")") : "NO RESPONSE").append('\n');
            sb.append("Active Hooks  : ").append(TelemetryClient.countHealthyHooks(d.healthMask)).append("/7\n");
            sb.append("Host DevOpts  : ").append(d.hostDevOn ? "ON (SPOOFED FOR GUARDED APPS)" : "OFF").append('\n');
            sb.append("Host ADB      : ").append(d.hostAdbOn ? "ON (SPOOFED + USB_STATE SANITIZED)" : "OFF").append('\n');
            sb.append("Host A11y     : ").append(d.hostA11yOn ? "ON (ADDCLIENT + SETTINGS HIDDEN)" : "OFF").append('\n');
            sb.append("Self AppId    : ").append(Process.myUid() % 100000);
            mainHandler.post(() -> tvDiagOutput.setText(sb.toString()));
        });
    }

    private void importJsonFromClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemCount() == 0) {
            toast("Clipboard is empty");
            return;
        }
        CharSequence txt = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
        applyImportedJson(txt != null ? txt.toString() : "");
    }

    private void applyImportedJson(String raw) {
        PolicyCodec.ImportResult res = PolicyCodec.importJson(raw);
        if (!res.valid) {
            toast("Invalid IsolPolicy JSON format");
            return;
        }
        policyMap.clear();
        policyMap.putAll(res.policies);
        syncPoliciesToStores();
        rebuildAppFlagsFromMap();
        applyFilterAndBind();
        toast("Imported " + res.policies.size() + " package policies");
    }

    private void launchExportFilePicker() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "isolpolicy-v2.1.0-backup.json");
        try {
            startActivityForResult(intent, REQ_EXPORT_JSON);
        } catch (Throwable t) {
            toast("File picker unavailable");
        }
    }

    private void launchImportFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_IMPORT_JSON);
        } catch (Throwable t) {
            toast("File picker unavailable");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT_JSON) {
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os != null) {
                    String json = PolicyCodec.exportJson(policyMap, PolicyFlags.AZ_OFF);
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                    toast("Saved " + policyMap.size() + " policies to file");
                }
            } catch (Throwable t) {
                toast("Export failed");
            }
        } else if (requestCode == REQ_IMPORT_JSON) {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is != null) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                    applyImportedJson(sb.toString());
                }
            } catch (Throwable t) {
                toast("Import failed");
            }
        }
    }

    private TextView sectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setTypeface(theme.monoBold);
        tv.setTextSize(11f);
        tv.setTextColor(theme.accentInfo);
        tv.setText(text);
        return tv;
    }

    private static void addWeightChip(LinearLayout row, PixelChipView chip, int startMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(startMargin);
        row.addView(chip, lp);
    }

    private void copyTextToClipboard(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private static String shortenPkg(String pkg) {
        if (pkg == null) return "-";
        if (pkg.length() <= 24) return pkg;
        int dot = pkg.lastIndexOf('.');
        if (dot > 0 && dot < pkg.length() - 1) {
            return ".." + pkg.substring(Math.max(0, dot - 6));
        }
        return pkg.substring(0, 22) + "..";
    }

    private static String formatUptime(long totalSec) {
        long h = totalSec / 3600L;
        long m = (totalSec % 3600L) / 60L;
        long s = totalSec % 60L;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }
}