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
import io.github.taro902.isolationpolicy.ui.pixel.PixelChrome;
import io.github.taro902.isolationpolicy.ui.pixel.PixelCounterStrip;
import io.github.taro902.isolationpolicy.ui.pixel.PixelDockBar;
import io.github.taro902.isolationpolicy.ui.pixel.PixelIconQuantizer;
import io.github.taro902.isolationpolicy.ui.pixel.PixelKeycapView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelPalette;
import io.github.taro902.isolationpolicy.ui.pixel.PixelScopeView;
import io.github.taro902.isolationpolicy.ui.pixel.PixelSpriteAtlas;
import io.github.taro902.isolationpolicy.ui.pixel.PixelTextView;

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
    private static final int FILTER_FINANCE = 1;
    private static final int FILTER_GUARDED = 2;
    private static final int FILTER_THREATS = 3;
    private static final int FILTER_SYSTEM = 4;

    private static final int REQ_EXPORT_JSON = 7101;
    private static final int REQ_IMPORT_JSON = 7102;

    private static final AtomicBoolean SERVICE_LISTENER_REGISTERED = new AtomicBoolean(false);
    private static volatile XposedService sBoundService;
    private static volatile WeakReference<MainActivity> sActiveActivity = new WeakReference<>(null);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, Integer> policyMap = new HashMap<>();
    private final Map<String, Integer> liveHitsMap = new HashMap<>();
    private final List<AppPolicyAdapter.AppItem> allApps = new ArrayList<>();

    private PixelPalette pal;
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
    private boolean ledPhase;
    private long lastTotalCount = -1L;

    private TelemetryClient.SnapshotData lastSnapshot = TelemetryClient.SnapshotData.DISCONNECTED;

    private FrameLayout contentHost;
    private View tabGuardView;
    private View tabScopeView;
    private View tabRadarView;
    private View tabVaultView;
    private PixelDockBar dockBar;

    private PixelTextView tvTopApiStatus;
    private PixelTextView tvDeckState;
    private PixelTextView tvDeckCounts;
    private PixelScopeView scopeView;
    private PixelCounterStrip counterStrip;
    private EditText searchBox;
    private ImageView btnClearSearch;
    private final PixelKeycapView[] filterKeys = new PixelKeycapView[5];

    private PixelTextView tvScopeUptime;
    private final PixelTextView[] hookTiles = new PixelTextView[7];
    private LinearLayout streamRowsContainer;
    private PixelKeycapView btnPauseStream;
    private final PixelKeycapView[] scopeFilterKeys = new PixelKeycapView[8];
    private String lastCopiedLog = "";

    private LinearLayout diagRowsContainer;
    private PixelTextView tvRadarHeader;
    private LinearLayout radarFinanceContainer;
    private LinearLayout radarSandboxContainer;

    private final PixelKeycapView[] paletteKeys = new PixelKeycapView[4];
    private PixelKeycapView btnIconToggle;
    private PixelKeycapView btnPollToggle;
    private PixelKeycapView btnSysToggle;

    private final Runnable searchDebounceTask = this::applyFilterAndBind;
    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            if (!activityResumed) {
                return;
            }
            ledPhase = !ledPhase;
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
        int themeMode = uiPrefs.getInt(KEY_UI_THEME, PixelPalette.MODE_CRT);
        pixelizeIcons = uiPrefs.getBoolean(KEY_UI_PIXEL_ICON, true);
        pollIntervalMs = uiPrefs.getInt(KEY_UI_POLL_MS, 1500);
        showSystemApps = uiPrefs.getBoolean(KEY_UI_SHOW_SYS, false);

        pal = new PixelPalette(this, themeMode);
        iconQuantizer = new PixelIconQuantizer();

        loadPoliciesFromAvailableStore();
        buildRootUi();
        applySystemBarColors();
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

    private void runBg(Runnable task) {
        if (!bgExecutor.isShutdown()) {
            try {
                bgExecutor.execute(task);
            } catch (Throwable ignored) {
            }
        }
    }

    private void applySystemBarColors() {
        Window w = getWindow();
        if (w == null) {
            return;
        }
        w.setStatusBarColor(pal.bgCanvas);
        w.setNavigationBarColor(pal.bgPanel);
        View decor = w.getDecorView();
        if (decor == null) {
            return;
        }
        decor.post(() -> {
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    WindowInsetsController ctrl = w.getInsetsController();
                    if (ctrl != null) {
                        int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                        ctrl.setSystemBarsAppearance(pal.isDark() ? 0 : mask, mask);
                    }
                } else {
                    int vis = decor.getSystemUiVisibility();
                    if (pal.isDark()) {
                        vis &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    } else {
                        vis |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    }
                    decor.setSystemUiVisibility(vis);
                }
            } catch (Throwable ignored) {
            }
        });
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
        rebuildAppFlagsAndHits();
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
        int u = pal.px;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(pal.bgCanvas);
        root.setFitsSystemWindows(true);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(u * 4, u * 4, u * 4, u * 3);
        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ImageView crest = new ImageView(this);
        crest.setImageDrawable(PixelSpriteAtlas.createDrawable(
                getResources(),
                PixelSpriteAtlas.SHIELD_14,
                Math.max(2, pal.smallFontScale),
                pal.ok,
                pal.info,
                pal.outline
        ));
        LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(
                14 * Math.max(2, pal.smallFontScale),
                14 * Math.max(2, pal.smallFontScale)
        );
        crLp.setMarginEnd(u * 3);
        topBar.addView(crest, crLp);

        PixelTextView titleView = new PixelTextView(this, "ISOLPOLICY", pal.inkPrimary, pal.titleFontScale);
        topBar.addView(titleView);

        PixelTextView verBadge = new PixelTextView(this, PolicyFlags.VERSION_NAME, pal.ok, pal.smallFontScale);
        verBadge.setBackground(PixelChrome.inset(pal));
        verBadge.setPadding(u * 3, u * 2, u * 3, u * 2);
        LinearLayout.LayoutParams vbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        vbLp.setMarginStart(u * 3);
        topBar.addView(verBadge, vbLp);

        View spacer = new View(this);
        topBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        tvTopApiStatus = new PixelTextView(this, "API 102", pal.info, pal.smallFontScale);
        tvTopApiStatus.setLed(true, pal.warn, pal.ledSpec);
        topBar.addView(tvTopApiStatus);

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

        dockBar = new PixelDockBar(this, pal);
        dockBar.setOnTabSelectedListener(this::selectTab);
        root.addView(dockBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(root);
        selectTab(TAB_GUARD);
    }

    private View buildGuardTab() {
        int u = pal.px;
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(u * 4, 0, u * 4, u * 2);

        LinearLayout deck = new LinearLayout(this);
        deck.setOrientation(LinearLayout.VERTICAL);
        deck.setBackground(PixelChrome.panel(pal, true));
        deck.setPadding(u * 4, u * 4, u * 5, u * 5);
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

        tvDeckState = new PixelTextView(this, "PROBING SYSTEM", pal.warn, pal.fontScale);
        tvDeckState.setLed(true, pal.warn, pal.ledSpec);
        statusCol.addView(tvDeckState);

        tvDeckCounts = new PixelTextView(this, "0 GUARDED   0 GRAPH", pal.inkSecondary, pal.smallFontScale);
        LinearLayout.LayoutParams dcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dcLp.topMargin = u * 3;
        statusCol.addView(tvDeckCounts, dcLp);

        scopeView = new PixelScopeView(this, pal);
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(pal.dp(120), pal.dp(44));
        scLp.setMarginStart(u * 3);
        deckTop.addView(scopeView, scLp);

        counterStrip = new PixelCounterStrip(this, pal);
        counterStrip.setOnPillarTapListener(pillarId -> {
            scopePillarFilter = pillarId;
            refreshScopeFilterKeys();
            selectTab(TAB_SCOPE);
        });
        LinearLayout.LayoutParams csLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        csLp.topMargin = u * 3;
        deck.addView(counterStrip, csLp);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        srLp.topMargin = u * 3;
        wrap.addView(searchRow, srLp);

        LinearLayout searchWell = new LinearLayout(this);
        searchWell.setOrientation(LinearLayout.HORIZONTAL);
        searchWell.setGravity(Gravity.CENTER_VERTICAL);
        searchWell.setBackground(PixelChrome.inset(pal));
        searchWell.setPadding(u * 3, u * 2, u * 3, u * 2);
        searchRow.addView(searchWell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView searchIco = new ImageView(this);
        searchIco.setImageDrawable(PixelSpriteAtlas.createDrawable(
                getResources(),
                PixelSpriteAtlas.SEARCH_9,
                Math.max(2, pal.smallFontScale),
                pal.inkMuted,
                pal.info,
                pal.outline
        ));
        LinearLayout.LayoutParams siLp = new LinearLayout.LayoutParams(
                9 * Math.max(2, pal.smallFontScale),
                9 * Math.max(2, pal.smallFontScale)
        );
        siLp.setMarginEnd(u * 2);
        searchWell.addView(searchIco, siLp);

        searchBox = new EditText(this);
        searchBox.setBackground(null);
        searchBox.setTypeface(pal.monoRegular);
        searchBox.setTextSize(12f);
        searchBox.setTextColor(pal.inkPrimary);
        searchBox.setHintTextColor(pal.inkMuted);
        searchBox.setHint("Search app or package");
        searchBox.setSingleLine(true);
        searchBox.setPadding(0, u, 0, u);
        searchWell.addView(searchBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnClearSearch = new ImageView(this);
        btnClearSearch.setImageDrawable(PixelSpriteAtlas.createDrawable(
                getResources(),
                PixelSpriteAtlas.CLOSE_7,
                Math.max(2, pal.smallFontScale),
                pal.danger,
                pal.danger,
                pal.outline
        ));
        btnClearSearch.setPadding(u * 2, u, u, u);
        btnClearSearch.setVisibility(View.GONE);
        btnClearSearch.setOnClickListener(v -> searchBox.setText(""));
        searchWell.addView(btnClearSearch);

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s != null ? s.toString().trim().toLowerCase(Locale.ROOT) : "";
                btnClearSearch.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                mainHandler.removeCallbacks(searchDebounceTask);
                mainHandler.postDelayed(searchDebounceTask, 80L);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        PixelKeycapView btnAutoBank = new PixelKeycapView(this, pal, false);
        btnAutoBank.setSmallText(true);
        btnAutoBank.applyAccent("AUTO BANK", pal.ok, pal.okBg, pal.okLip, true, false);
        btnAutoBank.setOnClickListener(v -> applyRecommendedToActionable());
        LinearLayout.LayoutParams abLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        abLp.setMarginStart(u * 2);
        searchRow.addView(btnAutoBank, abLp);

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

        String[] labels = {"ALL", "FINANCE", "GUARDED", "THREATS", "SYSTEM"};
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            PixelKeycapView fk = new PixelKeycapView(this, pal, false);
            fk.setSmallText(true);
            fk.setOnClickListener(v -> {
                if (idx == FILTER_SYSTEM && !showSystemApps) {
                    showSystemApps = true;
                    uiPrefs.edit().putBoolean(KEY_UI_SHOW_SYS, true).apply();
                    refreshVaultPrefKeys();
                    loadInstalledAppsAsync();
                }
                activeFilter = idx;
                refreshFilterKeys();
                applyFilterAndBind();
            });
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (i > 0) {
                flp.setMarginStart(u * 2);
            }
            filterRow.addView(fk, flp);
            filterKeys[i] = fk;
        }
        refreshFilterKeys();

        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setItemAnimator(null);
        rv.setHasFixedSize(false);
        adapter = new AppPolicyAdapter(pal, getPackageManager(), iconQuantizer, pixelizeIcons, new AppPolicyAdapter.ActionListener() {
            @Override
            public void onFlagsChanged(String packageName, int newFlags) {
                updatePackageFlags(packageName, newFlags);
            }

            @Override
            public void onInspectRequested(AppPolicyAdapter.AppItem item) {
                AppInspectorDialog.show(MainActivity.this, pal, item, lastSnapshot, MainActivity.this::updatePackageFlags);
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
        int u = pal.px;
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(u * 4, 0, u * 4, u * 2);

        LinearLayout healthCard = new LinearLayout(this);
        healthCard.setOrientation(LinearLayout.VERTICAL);
        healthCard.setBackground(PixelChrome.panel(pal, true));
        healthCard.setPadding(u * 4, u * 4, u * 5, u * 5);
        wrap.addView(healthCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        tvScopeUptime = new PixelTextView(this, "HOOK HEALTH MATRIX", pal.info, pal.smallFontScale);
        tvScopeUptime.setLed(true, pal.info, pal.ledSpec);
        healthCard.addView(tvScopeUptime);

        LinearLayout tileRow1 = new LinearLayout(this);
        tileRow1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tr1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tr1.topMargin = u * 3;
        healthCard.addView(tileRow1, tr1);

        LinearLayout tileRow2 = new LinearLayout(this);
        tileRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tr2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tr2.topMargin = u * 2;
        healthCard.addView(tileRow2, tr2);

        String[] names = {"AZ", "ISO", "SET", "USB", "A11Y", "PKG", "DISP"};
        for (int i = 0; i < 7; i++) {
            PixelTextView tile = new PixelTextView(this, names[i] + " --", pal.inkMuted, pal.smallFontScale);
            tile.setLed(true, pal.ledOff, pal.ledSpec);
            tile.setBackground(PixelChrome.inset(pal));
            tile.setPadding(u * 3, u * 2, u * 3, u * 2);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 4) {
                if (i > 0) tlp.setMarginStart(u * 2);
                tileRow1.addView(tile, tlp);
            } else {
                if (i > 4) tlp.setMarginStart(u * 2);
                tileRow2.addView(tile, tlp);
            }
            hookTiles[i] = tile;
        }

        LinearLayout ctrlRow = new LinearLayout(this);
        ctrlRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams crLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        crLp.topMargin = u * 3;
        wrap.addView(ctrlRow, crLp);

        btnPauseStream = new PixelKeycapView(this, pal, true);
        btnPauseStream.setSmallText(true);
        btnPauseStream.applyAccent("LIVE", pal.ok, pal.okBg, pal.okLip, true, false);
        btnPauseStream.setOnClickListener(v -> {
            streamPaused = !streamPaused;
            if (streamPaused) {
                btnPauseStream.applyAccent("PAUSED", pal.warn, pal.warnBg, pal.warnLip, true, true);
            } else {
                btnPauseStream.applyAccent("LIVE", pal.ok, pal.okBg, pal.okLip, true, true);
                renderScopeStream();
            }
        });
        addWeightKey(ctrlRow, btnPauseStream, 0);

        PixelKeycapView btnCopy = new PixelKeycapView(this, pal, false);
        btnCopy.setSmallText(true);
        btnCopy.applyAccent("COPY LOG", pal.info, pal.infoBg, pal.infoLip, true, false);
        btnCopy.setOnClickListener(v -> {
            copyTextToClipboard("IsolPolicy Telemetry", lastCopiedLog);
            toast("Telemetry copied");
        });
        addWeightKey(ctrlRow, btnCopy, u * 2);

        PixelKeycapView btnPurge = new PixelKeycapView(this, pal, false);
        btnPurge.setSmallText(true);
        btnPurge.applyAccent("PURGE", pal.danger, pal.dangerBg, pal.dangerLip, true, false);
        btnPurge.setOnClickListener(v -> runBg(() -> {
            boolean ok = TelemetryClient.purge(getApplicationContext());
            mainHandler.post(() -> {
                scopeView.clear();
                liveHitsMap.clear();
                lastTotalCount = 0L;
                rebuildAppFlagsAndHits();
                applyFilterAndBind();
                toast(ok ? "Ring buffer purged" : "IPC offline");
                pollTelemetryOnce();
            });
        }));
        addWeightKey(ctrlRow, btnPurge, u * 2);

        HorizontalScrollView pilScroll = new HorizontalScrollView(this);
        pilScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams psLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        psLp.topMargin = u * 3;
        psLp.bottomMargin = u * 3;
        wrap.addView(pilScroll, psLp);

        LinearLayout pilRow = new LinearLayout(this);
        pilRow.setOrientation(LinearLayout.HORIZONTAL);
        pilScroll.addView(pilRow);

        String[] pNames = {"ALL", "AZ", "ISO", "SET", "USB", "A11Y", "PKG", "DISP"};
        for (int i = 0; i < pNames.length; i++) {
            final int pid = i;
            PixelKeycapView k = new PixelKeycapView(this, pal, false);
            k.setSmallText(true);
            k.setOnClickListener(v -> {
                scopePillarFilter = pid;
                refreshScopeFilterKeys();
                renderScopeStream();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.setMarginStart(u * 2);
            pilRow.addView(k, lp);
            scopeFilterKeys[i] = k;
        }
        refreshScopeFilterKeys();

        ScrollView streamScroll = new ScrollView(this);
        streamScroll.setBackground(PixelChrome.inset(pal));
        streamScroll.setPadding(u * 3, u * 3, u * 3, u * 3);
        wrap.addView(streamScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        streamRowsContainer = new LinearLayout(this);
        streamRowsContainer.setOrientation(LinearLayout.VERTICAL);
        streamScroll.addView(streamRowsContainer);

        return wrap;
    }

    private View buildRadarTab() {
        int u = pal.px;
        ScrollView scroll = new ScrollView(this);
        scroll.setPadding(u * 4, 0, u * 4, u * 4);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(wrap);

        LinearLayout diagCard = new LinearLayout(this);
        diagCard.setOrientation(LinearLayout.VERTICAL);
        diagCard.setBackground(PixelChrome.panel(pal, true));
        diagCard.setPadding(u * 4, u * 4, u * 5, u * 5);
        wrap.addView(diagCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout diagHeader = new LinearLayout(this);
        diagHeader.setOrientation(LinearLayout.HORIZONTAL);
        diagHeader.setGravity(Gravity.CENTER_VERTICAL);
        diagCard.addView(diagHeader);

        PixelTextView title = new PixelTextView(this, "COHERENCE CHECK", pal.ok, pal.fontScale);
        title.setLed(true, pal.ok, pal.ledSpec);
        diagHeader.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        PixelKeycapView btnRunDiag = new PixelKeycapView(this, pal, false);
        btnRunDiag.setSmallText(true);
        btnRunDiag.applyAccent("RUN CHECK", pal.ok, pal.okBg, pal.okLip, true, false);
        btnRunDiag.setOnClickListener(v -> runSelfDiagnosticAsync());
        diagHeader.addView(btnRunDiag);

        diagRowsContainer = new LinearLayout(this);
        diagRowsContainer.setOrientation(LinearLayout.VERTICAL);
        diagRowsContainer.setBackground(PixelChrome.inset(pal));
        diagRowsContainer.setPadding(u * 3, u * 3, u * 3, u * 3);
        LinearLayout.LayoutParams doLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        doLp.topMargin = u * 3;
        diagCard.addView(diagRowsContainer, doLp);

        addDiagRow("SYSTEM", "Tap RUN CHECK to probe service, IPC, hooks, and host flags", pal.inkSecondary, pal.ledOff);

        LinearLayout radarCard = new LinearLayout(this);
        radarCard.setOrientation(LinearLayout.VERTICAL);
        radarCard.setBackground(PixelChrome.panel(pal, false));
        radarCard.setPadding(u * 4, u * 4, u * 5, u * 5);
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

        tvRadarHeader = new PixelTextView(this, "FINANCE AND SECURITY", pal.warn, pal.fontScale);
        rHead.addView(tvRadarHeader, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        PixelKeycapView btnRescan = new PixelKeycapView(this, pal, false);
        btnRescan.setSmallText(true);
        btnRescan.applyAccent("RESCAN", pal.info, pal.infoBg, pal.infoLip, true, false);
        btnRescan.setOnClickListener(v -> {
            ManifestRadar.clearCache();
            loadInstalledAppsAsync();
            toast("Rescanning installed APKs");
        });
        rHead.addView(btnRescan);

        radarFinanceContainer = new LinearLayout(this);
        radarFinanceContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rfLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rfLp.topMargin = u * 3;
        radarCard.addView(radarFinanceContainer, rfLp);

        PixelTextView sbHead = new PixelTextView(this, "WEB AND MEDIA SANDBOX", pal.inkMuted, pal.smallFontScale);
        LinearLayout.LayoutParams shLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shLp.topMargin = u * 4;
        radarCard.addView(sbHead, shLp);

        radarSandboxContainer = new LinearLayout(this);
        radarSandboxContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rsLp.topMargin = u * 2;
        radarCard.addView(radarSandboxContainer, rsLp);

        return scroll;
    }

    private View buildVaultTab() {
        int u = pal.px;
        ScrollView scroll = new ScrollView(this);
        scroll.setPadding(u * 4, 0, u * 4, u * 4);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(wrap);

        LinearLayout palCard = new LinearLayout(this);
        palCard.setOrientation(LinearLayout.VERTICAL);
        palCard.setBackground(PixelChrome.panel(pal, true));
        palCard.setPadding(u * 4, u * 4, u * 5, u * 5);
        wrap.addView(palCard);

        palCard.addView(new PixelTextView(this, "HARDWARE PALETTE", pal.info, pal.fontScale));

        LinearLayout palRow1 = new LinearLayout(this);
        palRow1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pl1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pl1.topMargin = u * 3;
        palCard.addView(palRow1, pl1);

        LinearLayout palRow2 = new LinearLayout(this);
        palRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pl2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pl2.topMargin = u * 2;
        palCard.addView(palRow2, pl2);

        for (int i = 0; i < 4; i++) {
            final int modeId = i;
            PixelKeycapView pk = new PixelKeycapView(this, pal, false);
            pk.setSmallText(true);
            pk.setSwatches(PixelPalette.swatchForMode(modeId));
            boolean sel = (pal.mode == modeId);
            pk.applyAccent(PixelPalette.nameForMode(modeId), sel ? pal.ok : pal.outline, sel ? pal.okBg : pal.keyOffBg, sel ? pal.okLip : pal.keyOffLip, sel, false);
            pk.setOnClickListener(v -> {
                if (pal.mode != modeId) {
                    uiPrefs.edit().putInt(KEY_UI_THEME, modeId).apply();
                    recreate();
                }
            });
            paletteKeys[i] = pk;
            if (i < 2) {
                addWeightKey(palRow1, pk, i == 0 ? 0 : u * 2);
            } else {
                addWeightKey(palRow2, pk, i == 2 ? 0 : u * 2);
            }
        }

        LinearLayout prefCard = new LinearLayout(this);
        prefCard.setOrientation(LinearLayout.VERTICAL);
        prefCard.setBackground(PixelChrome.panel(pal, false));
        prefCard.setPadding(u * 4, u * 4, u * 5, u * 5);
        LinearLayout.LayoutParams prcLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        prcLp.topMargin = u * 4;
        wrap.addView(prefCard, prcLp);

        prefCard.addView(new PixelTextView(this, "CONSOLE OPTIONS", pal.info, pal.fontScale));

        LinearLayout prefRow = new LinearLayout(this);
        prefRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pf1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pf1.topMargin = u * 3;
        prefCard.addView(prefRow, pf1);

        btnIconToggle = new PixelKeycapView(this, pal, false);
        btnIconToggle.setSmallText(true);
        btnIconToggle.setOnClickListener(v -> {
            pixelizeIcons = !pixelizeIcons;
            uiPrefs.edit().putBoolean(KEY_UI_PIXEL_ICON, pixelizeIcons).apply();
            adapter.setPixelizeIcons(pixelizeIcons);
            refreshVaultPrefKeys();
        });
        addWeightKey(prefRow, btnIconToggle, 0);

        btnPollToggle = new PixelKeycapView(this, pal, false);
        btnPollToggle.setSmallText(true);
        btnPollToggle.setOnClickListener(v -> {
            if (pollIntervalMs == 1500) {
                pollIntervalMs = 3000;
            } else if (pollIntervalMs == 3000) {
                pollIntervalMs = 0;
            } else {
                pollIntervalMs = 1500;
            }
            uiPrefs.edit().putInt(KEY_UI_POLL_MS, pollIntervalMs).apply();
            refreshVaultPrefKeys();
            mainHandler.removeCallbacks(pollTask);
            if (activityResumed && pollIntervalMs > 0) {
                mainHandler.postDelayed(pollTask, pollIntervalMs);
            }
        });
        addWeightKey(prefRow, btnPollToggle, u * 2);

        LinearLayout prefRow2 = new LinearLayout(this);
        prefRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pf2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pf2.topMargin = u * 2;
        prefCard.addView(prefRow2, pf2);

        btnSysToggle = new PixelKeycapView(this, pal, true);
        btnSysToggle.setSmallText(true);
        btnSysToggle.setOnClickListener(v -> {
            showSystemApps = !showSystemApps;
            uiPrefs.edit().putBoolean(KEY_UI_SHOW_SYS, showSystemApps).apply();
            refreshVaultPrefKeys();
            loadInstalledAppsAsync();
        });
        addWeightKey(prefRow2, btnSysToggle, 0);
        refreshVaultPrefKeys();

        LinearLayout presetCard = new LinearLayout(this);
        presetCard.setOrientation(LinearLayout.VERTICAL);
        presetCard.setBackground(PixelChrome.panel(pal, false));
        presetCard.setPadding(u * 4, u * 4, u * 5, u * 5);
        LinearLayout.LayoutParams pcLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pcLp.topMargin = u * 4;
        wrap.addView(presetCard, pcLp);

        presetCard.addView(new PixelTextView(this, "FINANCE BATCH PRESETS", pal.ok, pal.fontScale));

        LinearLayout bRow1 = new LinearLayout(this);
        bRow1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams br1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        br1.topMargin = u * 3;
        presetCard.addView(bRow1, br1);

        PixelKeycapView bBank = new PixelKeycapView(this, pal, false);
        bBank.setSmallText(true);
        bBank.applyAccent("BANK STRICT", pal.ok, pal.okBg, pal.okLip, true, false);
        bBank.setOnClickListener(v -> applyPresetToActionable(PolicyFlags.PRESET_BANKING_STRICT, "BANK STRICT"));
        addWeightKey(bRow1, bBank, 0);

        PixelKeycapView bWallet = new PixelKeycapView(this, pal, false);
        bWallet.setSmallText(true);
        bWallet.applyAccent("WALLET CORE", pal.info, pal.infoBg, pal.infoLip, true, false);
        bWallet.setOnClickListener(v -> applyPresetToActionable(
                PolicyFlags.AZ_DOWNGRADE | PolicyFlags.FLAG_DEV_COHERENT | PolicyFlags.FLAG_A11Y_COHERENT | PolicyFlags.FLAG_PKG_CHANGES,
                "WALLET CORE"
        ));
        addWeightKey(bRow1, bWallet, u * 2);

        LinearLayout bRow2 = new LinearLayout(this);
        bRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams br2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        br2.topMargin = u * 2;
        presetCard.addView(bRow2, br2);

        PixelKeycapView bMax = new PixelKeycapView(this, pal, false);
        bMax.setSmallText(true);
        bMax.applyAccent("AUDIT MAX", pal.warn, pal.warnBg, pal.warnLip, true, false);
        bMax.setOnClickListener(v -> applyPresetToActionable(PolicyFlags.PRESET_AUDIT_MAX, "AUDIT MAX"));
        addWeightKey(bRow2, bMax, 0);

        PixelKeycapView bClear = new PixelKeycapView(this, pal, false);
        bClear.setSmallText(true);
        bClear.applyAccent("RESET ALL", pal.danger, pal.dangerBg, pal.dangerLip, true, false);
        bClear.setOnClickListener(v -> {
            policyMap.clear();
            syncPoliciesToStores();
            rebuildAppFlagsAndHits();
            applyFilterAndBind();
            toast("All policies cleared");
        });
        addWeightKey(bRow2, bClear, u * 2);

        LinearLayout ioCard = new LinearLayout(this);
        ioCard.setOrientation(LinearLayout.VERTICAL);
        ioCard.setBackground(PixelChrome.panel(pal, false));
        ioCard.setPadding(u * 4, u * 4, u * 5, u * 5);
        LinearLayout.LayoutParams ioLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ioLp.topMargin = u * 4;
        wrap.addView(ioCard, ioLp);

        ioCard.addView(new PixelTextView(this, "CONFIG BACKUP JSON", pal.info, pal.fontScale));

        LinearLayout ioRow1 = new LinearLayout(this);
        ioRow1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ir1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ir1.topMargin = u * 3;
        ioCard.addView(ioRow1, ir1);

        PixelKeycapView btnClipExp = new PixelKeycapView(this, pal, false);
        btnClipExp.setSmallText(true);
        btnClipExp.applyAccent("COPY JSON", pal.ok, pal.okBg, pal.okLip, true, false);
        btnClipExp.setOnClickListener(v -> {
            String json = PolicyCodec.exportJson(policyMap, PolicyFlags.AZ_OFF);
            copyTextToClipboard("IsolPolicy JSON", json);
            toast("Exported " + policyMap.size() + " policies");
        });
        addWeightKey(ioRow1, btnClipExp, 0);

        PixelKeycapView btnClipImp = new PixelKeycapView(this, pal, false);
        btnClipImp.setSmallText(true);
        btnClipImp.applyAccent("PASTE JSON", pal.info, pal.infoBg, pal.infoLip, true, false);
        btnClipImp.setOnClickListener(v -> importJsonFromClipboard());
        addWeightKey(ioRow1, btnClipImp, u * 2);

        LinearLayout ioRow2 = new LinearLayout(this);
        ioRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ir2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ir2.topMargin = u * 2;
        ioCard.addView(ioRow2, ir2);

        PixelKeycapView btnFileExp = new PixelKeycapView(this, pal, false);
        btnFileExp.setSmallText(true);
        btnFileExp.applyAccent("SAVE FILE", pal.inkSecondary, pal.keyOffBg, pal.keyOffLip, true, false);
        btnFileExp.setOnClickListener(v -> launchExportFilePicker());
        addWeightKey(ioRow2, btnFileExp, 0);

        PixelKeycapView btnFileImp = new PixelKeycapView(this, pal, false);
        btnFileImp.setSmallText(true);
        btnFileImp.applyAccent("LOAD FILE", pal.inkSecondary, pal.keyOffBg, pal.keyOffLip, true, false);
        btnFileImp.setOnClickListener(v -> launchImportFilePicker());
        addWeightKey(ioRow2, btnFileImp, u * 2);

        return scroll;
    }

    private void selectTab(int tabIdx) {
        activeTab = tabIdx;
        dockBar.setActiveTab(tabIdx);
        tabGuardView.setVisibility(tabIdx == TAB_GUARD ? View.VISIBLE : View.GONE);
        tabScopeView.setVisibility(tabIdx == TAB_SCOPE ? View.VISIBLE : View.GONE);
        tabRadarView.setVisibility(tabIdx == TAB_RADAR ? View.VISIBLE : View.GONE);
        tabVaultView.setVisibility(tabIdx == TAB_VAULT ? View.VISIBLE : View.GONE);

        if (tabIdx == TAB_SCOPE) {
            renderScopeStream();
        } else if (tabIdx == TAB_RADAR) {
            renderRadarSurface();
        }
    }

    private void refreshFilterKeys() {
        String[] labels = {"ALL", "FINANCE", "GUARDED", "THREATS", "SYSTEM"};
        for (int i = 0; i < filterKeys.length; i++) {
            boolean on = (i == activeFilter);
            filterKeys[i].applyAccent(labels[i], pal.ok, pal.okBg, pal.okLip, on, true);
        }
    }

    private void refreshScopeFilterKeys() {
        String[] names = {"ALL", "AZ", "ISO", "SET", "USB", "A11Y", "PKG", "DISP"};
        for (int i = 0; i < scopeFilterKeys.length; i++) {
            boolean on = (i == scopePillarFilter);
            scopeFilterKeys[i].applyAccent(names[i], pal.info, pal.infoBg, pal.infoLip, on, true);
        }
    }

    private void refreshVaultPrefKeys() {
        btnIconToggle.applyAccent(pixelizeIcons ? "ICON:PIXEL 16" : "ICON:NATIVE", pal.info, pal.infoBg, pal.infoLip, true, false);
        String pollLbl = pollIntervalMs == 0 ? "POLL:MANUAL" : ("POLL:" + (pollIntervalMs / 1000f) + "S");
        btnPollToggle.applyAccent(pollLbl, pal.ok, pal.okBg, pal.okLip, true, false);
        btnSysToggle.applyAccent(showSystemApps ? "SYSTEM:UNLOCKED" : "SYSTEM:LOCKED", pal.warn, pal.warnBg, pal.warnLip, showSystemApps, false);
    }

    private void loadInstalledAppsAsync() {
        final boolean includeSys = showSystemApps;
        runBg(() -> {
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
                Integer hits = liveHitsMap.get(pkg);

                built.add(new AppPolicyAdapter.AppItem(
                        pkg,
                        lbl,
                        pi.applicationInfo.uid,
                        isSys,
                        pi.applicationInfo,
                        fl,
                        hits != null ? hits : 0,
                        report
                ));
            }

            Collections.sort(built, (a, b) -> {
                boolean aGuard = PolicyFlags.isActive(a.flags);
                boolean bGuard = PolicyFlags.isActive(b.flags);
                if (aGuard != bGuard) return aGuard ? -1 : 1;
                if (a.report.financeLikely != b.report.financeLikely) return a.report.financeLikely ? -1 : 1;
                if (a.report.isSandbox != b.report.isSandbox) return a.report.isSandbox ? 1 : -1;
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
                    renderRadarSurface();
                }
            });
        });
    }

    private void rebuildAppFlagsAndHits() {
        for (int i = 0; i < allApps.size(); i++) {
            AppPolicyAdapter.AppItem item = allApps.get(i);
            Integer fl = policyMap.get(item.packageName);
            int nextFl = fl != null ? fl : 0;
            Integer h = liveHitsMap.get(item.packageName);
            int nextHits = h != null ? h : 0;
            if (item.flags != nextFl || item.liveHits != nextHits) {
                allApps.set(i, new AppPolicyAdapter.AppItem(
                        item.packageName, item.label, item.uid, item.isSystem,
                        item.applicationInfo, nextFl, nextHits, item.report
                ));
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
            case FILTER_FINANCE:
                return item.report.financeLikely;
            case FILTER_GUARDED:
                return PolicyFlags.isActive(item.flags);
            case FILTER_THREATS:
                return item.report.isThreat();
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
        updateHeaderFromSnapshot();
    }

    private void applyRecommendedToActionable() {
        int count = 0;
        for (int i = 0; i < allApps.size(); i++) {
            AppPolicyAdapter.AppItem item = allApps.get(i);
            if (!item.report.isActionableTarget()) {
                continue;
            }
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
        updateHeaderFromSnapshot();
        toast(count > 0 ? ("Guarded " + count + " finance targets (browsers untouched)") : "All finance targets already guarded");
    }

    private void applyPresetToActionable(int presetFlags, String label) {
        int count = 0;
        for (int i = 0; i < allApps.size(); i++) {
            AppPolicyAdapter.AppItem item = allApps.get(i);
            if (item.report.isActionableTarget()) {
                policyMap.put(item.packageName, presetFlags);
                allApps.set(i, item.withFlags(presetFlags));
                count++;
            }
        }
        syncPoliciesToStores();
        applyFilterAndBind();
        updateHeaderFromSnapshot();
        toast(label + " applied to " + count + " finance targets");
    }

    private void pollTelemetryOnce() {
        runBg(() -> {
            TelemetryClient.SnapshotData snap = TelemetryClient.fetchSnapshot(getApplicationContext());
            Map<String, Integer> hits = new HashMap<>();
            if (snap.connected && snap.events != null) {
                for (TelemetryClient.EventRecord ev : snap.events) {
                    Integer cur = hits.get(ev.packageName);
                    hits.put(ev.packageName, cur != null ? (cur + 1) : 1);
                }
            }
            mainHandler.post(() -> {
                lastSnapshot = snap;
                boolean hitsChanged = !hits.equals(liveHitsMap);
                liveHitsMap.clear();
                liveHitsMap.putAll(hits);

                if (snap.connected) {
                    long delta = (lastTotalCount >= 0L && snap.total >= lastTotalCount)
                            ? (snap.total - lastTotalCount)
                            : 0L;
                    lastTotalCount = snap.total;
                    scopeView.syncFromSnapshot(snap.events, delta);
                    counterStrip.setCounts(snap.counts);
                } else {
                    scopeView.pushDelta(0L);
                }

                updateHeaderFromSnapshot();
                if (hitsChanged) {
                    rebuildAppFlagsAndHits();
                    applyFilterAndBind();
                }
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
            int stColor = okHooks == 7 ? pal.ok : pal.warn;
            tvTopApiStatus.setText("API " + s.apiVersion);
            tvTopApiStatus.setTextColor(stColor);
            tvTopApiStatus.setLed(true, ledPhase ? stColor : pal.info, pal.ledSpec);

            tvDeckState.setText("ACTIVE  " + okHooks + "/7 HOOKS");
            tvDeckState.setTextColor(stColor);
            tvDeckState.setLed(true, stColor, pal.ledSpec);
            tvDeckCounts.setText(policyMap.size() + " GUARDED   " + s.graphSize + " GRAPH   " + s.total + " TOTAL");
        } else if (sBoundService != null) {
            tvTopApiStatus.setText("REBOOT");
            tvTopApiStatus.setTextColor(pal.warn);
            tvTopApiStatus.setLed(true, pal.warn, pal.ledSpec);

            tvDeckState.setText("SERVICE BOUND");
            tvDeckState.setTextColor(pal.warn);
            tvDeckState.setLed(true, pal.warn, pal.ledSpec);
            tvDeckCounts.setText(policyMap.size() + " GUARDED   REBOOT REQUIRED");
        } else {
            tvTopApiStatus.setText("OFFLINE");
            tvTopApiStatus.setTextColor(pal.danger);
            tvTopApiStatus.setLed(true, pal.danger, pal.ledSpec);

            tvDeckState.setText("MODULE INACTIVE");
            tvDeckState.setTextColor(pal.danger);
            tvDeckState.setLed(true, pal.danger, pal.ledSpec);
            tvDeckCounts.setText("ENABLE SYSTEM SCOPE IN LSPOSED");
        }
    }

    private void renderScopeStream() {
        TelemetryClient.SnapshotData s = lastSnapshot;
        String[] names = {"AZ", "ISO", "SET", "USB", "A11Y", "PKG", "DISP"};
        for (int i = 0; i < 7; i++) {
            int st = s.connected ? TelemetryClient.decodeHookState(s.healthMask, i) : 0;
            String code = st == 1 ? "OK" : (st == 2 ? "FB" : (st == 3 ? "ER" : "--"));
            int col = st == 1 ? pal.ok : (st == 2 ? pal.warn : (st == 3 ? pal.danger : pal.inkMuted));
            int led = st == 1 ? pal.ok : (st == 2 ? pal.warn : (st == 3 ? pal.danger : pal.ledOff));
            hookTiles[i].setText(names[i] + " " + code);
            hookTiles[i].setTextColor(col);
            hookTiles[i].setLed(true, led, pal.ledSpec);
        }

        streamRowsContainer.removeAllViews();

        if (!s.connected) {
            tvScopeUptime.setText("IPC OFFLINE");
            tvScopeUptime.setTextColor(pal.danger);
            addStreamLine("Activate IsolPolicy in LSPosed (System Framework) and reboot.", pal.inkMuted, false);
            lastCopiedLog = "";
            return;
        }

        long sec = s.uptimeMs / 1000L;
        tvScopeUptime.setText("UP " + formatUptime(sec) + "   TOT " + s.total + "   GRAPH " + s.graphSize);
        tvScopeUptime.setTextColor(pal.info);

        StringBuilder copyBuf = new StringBuilder();
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
        int shown = 0;
        for (TelemetryClient.EventRecord ev : s.events) {
            if (scopePillarFilter != 0 && ev.pillar != scopePillarFilter) {
                continue;
            }
            String line = fmt.format(new Date(ev.timestampMs))
                    + "  " + padRight(PolicyFlags.pillarName(ev.pillar), 4)
                    + "  " + padRight(shortenPkg(ev.packageName), 18)
                    + "  " + PolicyFlags.vectorName(ev.vector)
                    + " -> " + PolicyFlags.actionName(ev.action);
            copyBuf.append(line).append('\n');
            addStreamLine(line, colorForPillar(ev.pillar), (shown & 1) == 1);
            shown++;
        }
        lastCopiedLog = copyBuf.toString().trim();
        if (shown == 0) {
            addStreamLine("No events in ring buffer for this filter. Launch a guarded app to observe live telemetry.", pal.inkMuted, false);
        }
    }

    private void addStreamLine(String text, int color, boolean zebra) {
        int u = pal.px;
        TextView tv = new TextView(this);
        tv.setTypeface(pal.monoRegular);
        tv.setTextSize(10f);
        tv.setTextColor(color);
        tv.setFontFeatureSettings("tnum");
        tv.setText(text);
        tv.setPadding(u * 2, u * 2, u * 2, u * 2);
        if (zebra) {
            tv.setBackgroundColor(pal.bgPanel);
        }
        streamRowsContainer.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private int colorForPillar(int pillar) {
        switch (pillar) {
            case PolicyFlags.PILLAR_APP_ZYGOTE:
            case PolicyFlags.PILLAR_SETTINGS:
                return pal.ok;
            case PolicyFlags.PILLAR_USB:
            case PolicyFlags.PILLAR_PKG_CHANGE:
                return pal.warn;
            case PolicyFlags.PILLAR_A11Y:
                return pal.focus;
            default:
                return pal.info;
        }
    }

    private void renderRadarSurface() {
        radarFinanceContainer.removeAllViews();
        radarSandboxContainer.removeAllViews();
        int u = pal.px;
        int finCount = 0;
        int sbCount = 0;

        for (AppPolicyAdapter.AppItem item : allApps) {
            if (item.report.isActionableTarget()) {
                finCount++;
                addRadarRow(radarFinanceContainer, item, false, u);
            } else if (item.report.isSandbox && (item.report.usesAppZygote || !item.report.isolatedServices.isEmpty())) {
                sbCount++;
                addRadarRow(radarSandboxContainer, item, true, u);
            }
        }

        tvRadarHeader.setText("TARGETS " + finCount + "  SANDBOX " + sbCount);
        if (finCount == 0) {
            TextView empty = new TextView(this);
            empty.setTypeface(pal.monoRegular);
            empty.setTextSize(10f);
            empty.setTextColor(pal.inkMuted);
            empty.setText("No finance or antifraud packages detected.");
            radarFinanceContainer.addView(empty);
        }
        if (sbCount == 0) {
            TextView empty = new TextView(this);
            empty.setTypeface(pal.monoRegular);
            empty.setTextSize(10f);
            empty.setTextColor(pal.inkMuted);
            empty.setText("No browser or media sandbox packages detected.");
            radarSandboxContainer.addView(empty);
        }
    }

    private void addRadarRow(LinearLayout container, AppPolicyAdapter.AppItem item, boolean isSandbox, int u) {
        boolean guarded = PolicyFlags.isActive(item.flags);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackground(PixelChrome.card(pal, guarded));
        row.setPadding(guarded ? u * 7 : u * 3, u * 3, u * 4, u * 4);
        row.setOnClickListener(v -> AppInspectorDialog.show(this, pal, item, lastSnapshot, this::updatePackageFlags));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(head);

        TextView t1 = new TextView(this);
        t1.setTypeface(pal.monoBold);
        t1.setTextSize(11.5f);
        t1.setTextColor(pal.inkPrimary);
        t1.setText(item.label);
        head.addView(t1, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        String stateLbl = guarded ? "GUARDED" : (isSandbox ? "SAFE OFF" : "EXPOSED");
        int stateCol = guarded ? pal.ok : (isSandbox ? pal.inkMuted : pal.danger);
        PixelTextView badge = new PixelTextView(this, stateLbl, stateCol, pal.smallFontScale);
        badge.setLed(true, stateCol, pal.ledSpec);
        head.addView(badge);

        TextView t2 = new TextView(this);
        t2.setTypeface(pal.monoRegular);
        t2.setTextSize(9.5f);
        t2.setTextColor(pal.inkSecondary);
        StringBuilder detail = new StringBuilder(item.packageName);
        if (item.report.usesAppZygote) detail.append("   AZ");
        if (!item.report.isolatedServices.isEmpty()) detail.append("   ISO:").append(item.report.isolatedServices.size());
        if (!item.report.antifraudSdks.isEmpty()) detail.append("   ").append(String.join(" ", item.report.antifraudSdks));
        t2.setText(detail.toString());
        row.addView(t2);

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = u * 2;
        container.addView(row, rlp);
    }

    private void runSelfDiagnosticAsync() {
        diagRowsContainer.removeAllViews();
        addDiagRow("PROBE", "Checking service, Zero-Oracle IPC, hooks, and host state...", pal.info, pal.info);

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

        runBg(() -> {
            TelemetryClient.DiagnosticResult d = TelemetryClient.runSelfDiagnostic(getApplicationContext(), svcOk, fApi, fFw);
            mainHandler.post(() -> {
                diagRowsContainer.removeAllViews();
                int okCount = TelemetryClient.countHealthyHooks(d.healthMask);
                addDiagRow("SERVICE", d.serviceBound ? (d.serviceFw + " API " + d.serviceApi) : "UNBOUND",
                        d.serviceBound ? pal.ok : pal.danger, d.serviceBound ? pal.ok : pal.danger);
                addDiagRow("IPC GATE", d.ipcAlive ? ("AUTHENTICATED v" + d.remoteVer) : "NO RESPONSE",
                        d.ipcAlive ? pal.ok : pal.danger, d.ipcAlive ? pal.ok : pal.danger);
                addDiagRow("HOOKS", okCount + " OF 7 ATTACHED",
                        okCount == 7 ? pal.ok : pal.warn, okCount == 7 ? pal.ok : pal.warn);
                addDiagRow("DEV OPTS", d.hostDevOn ? "HOST ON (SPOOFED FOR TARGETS)" : "HOST OFF",
                        d.hostDevOn ? pal.warn : pal.ok, d.hostDevOn ? pal.warn : pal.ok);
                addDiagRow("HOST ADB", d.hostAdbOn ? "HOST ON (USB SANITIZED)" : "HOST OFF",
                        d.hostAdbOn ? pal.warn : pal.ok, d.hostAdbOn ? pal.warn : pal.ok);
                addDiagRow("HOST A11Y", d.hostA11yOn ? "HOST ON (HIDDEN FROM TARGETS)" : "HOST OFF",
                        d.hostA11yOn ? pal.warn : pal.ok, d.hostA11yOn ? pal.warn : pal.ok);
                addDiagRow("SELF UID", "APPID " + (Process.myUid() % 100000), pal.info, pal.info);
            });
        });
    }

    private void addDiagRow(String key, String value, int valueColor, int ledColor) {
        int u = pal.px;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, u, 0, u);

        PixelTextView kView = new PixelTextView(this, padRight(key, 9), pal.inkSecondary, pal.smallFontScale);
        kView.setLed(true, ledColor, pal.ledSpec);
        row.addView(kView);

        TextView vView = new TextView(this);
        vView.setTypeface(pal.monoBold);
        vView.setTextSize(10f);
        vView.setTextColor(valueColor);
        vView.setFontFeatureSettings("tnum");
        vView.setText(value);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        vlp.setMarginStart(u * 2);
        row.addView(vView, vlp);

        diagRowsContainer.addView(row);
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
            toast("Invalid IsolPolicy JSON");
            return;
        }
        policyMap.clear();
        policyMap.putAll(res.policies);
        syncPoliciesToStores();
        rebuildAppFlagsAndHits();
        applyFilterAndBind();
        updateHeaderFromSnapshot();
        toast("Imported " + res.policies.size() + " package policies");
    }

    private void launchExportFilePicker() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "isolpolicy-v2.1.1-backup.json");
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
                    toast("Saved " + policyMap.size() + " policies");
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

    private static void addWeightKey(LinearLayout row, PixelKeycapView key, int startMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(startMargin);
        row.addView(key, lp);
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

    private static String padRight(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s;
        StringBuilder sb = new StringBuilder(width);
        sb.append(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String shortenPkg(String pkg) {
        if (pkg == null) return "-";
        if (pkg.length() <= 18) return pkg;
        int dot = pkg.lastIndexOf('.');
        if (dot > 0 && dot < pkg.length() - 1) {
            String tail = pkg.substring(dot + 1);
            if (tail.length() <= 16) {
                return ".." + tail;
            }
        }
        return pkg.substring(0, 16) + "..";
    }

    private static String formatUptime(long totalSec) {
        long h = totalSec / 3600L;
        long m = (totalSec % 3600L) / 60L;
        long s = totalSec % 60L;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }
}
