package io.github.mhmrdd.isolationpolicy;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class MainActivity extends Activity {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final long FILTER_DEBOUNCE_MS = 60L;
    private static final long TEST_BIND_TIMEOUT_MS = 6000L;

    private static final int SERVICE_FLAG_ISOLATED = 0x00000002;
    private static final int SERVICE_FLAG_APP_ZYGOTE = 0x00000008;

    private static final int FILTER_ALL = 0;
    private static final int FILTER_THREATS = 1;
    private static final int FILTER_SELECTED = 2;

    private static final int MODE_AUTO = 0;
    private static final int MODE_DOWNGRADE = 1;
    private static final int MODE_DROP = 2;

    private static final AtomicBoolean sListenerRegistered = new AtomicBoolean(false);
    private static volatile XposedService sXposedService;
    private static volatile WeakReference<MainActivity> sActiveActivity = new WeakReference<>(null);

    private TextView mStatus;
    private EditText mSearch;
    private CheckBox mModeDropToggle;
    private CheckBox mAllowSystemToggle;
    private Button mBtnFilterAll;
    private Button mBtnFilterThreats;
    private Button mBtnFilterSelected;
    private Button mBtnAutoSelect;
    private RecyclerView mList;
    private ProgressBar mProgress;
    private Button mApply;

    private final Set<String> mDenied = new TreeSet<>();
    private final Set<String> mDropOverrides = new TreeSet<>();
    private final Set<String> mDowngradeOverrides = new TreeSet<>();
    private boolean mModeDrop = false;
    private boolean mAllowSystem = false;
    private int mCategoryFilter = FILTER_ALL;

    private final Adapter mAdapter = new Adapter();
    private int mEligibleCount = 0;
    private int mAppZygoteThreatCount = 0;
    private int mIsolatedCount = 0;
    private String mTestHeadline = "isolated service : binding ...";
    private String mTestDetail = "";
    private final Handler mUi = new Handler(Looper.getMainLooper());
    private final HandlerThread mBgThread = new HandlerThread("isolpolicy-bg");
    private Handler mBg;
    private String mFilter = "";
    private final Runnable mApplyFilter = new Runnable() {
        @Override
        public void run() {
            mAdapter.setFilter(mFilter, mCategoryFilter);
        }
    };
    private ServiceConnection mTestConnection;
    private Runnable mTestTimeout;
    private long mBindStartRealtime = 0L;

    private static void ensureServiceListener() {
        if (sListenerRegistered.compareAndSet(false, true)) {
            XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
                @Override
                public void onServiceBind(@NonNull XposedService service) {
                    sXposedService = service;
                    final MainActivity act = sActiveActivity.get();
                    if (act != null) {
                        act.mUi.post(new Runnable() {
                            @Override
                            public void run() {
                                act.loadDenied();
                                act.saveDeniedInternal(false);
                                act.mAdapter.notifyDataSetChanged();
                                act.refreshStatus();
                            }
                        });
                    }
                }

                @Override
                public void onServiceDied(@NonNull XposedService service) {
                    sXposedService = null;
                    final MainActivity act = sActiveActivity.get();
                    if (act != null) {
                        act.mUi.post(new Runnable() {
                            @Override
                            public void run() {
                                act.refreshStatus();
                            }
                        });
                    }
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sActiveActivity = new WeakReference<>(this);
        setContentView(buildLayout());

        mBgThread.start();
        mBg = new Handler(mBgThread.getLooper());

        ensureServiceListener();

        loadDenied();
        updateFilterButtonsUi();
        refreshStatus();
        mBg.post(new ScanRunnable());
        runTest();
    }

    @Override
    protected void onDestroy() {
        if (sActiveActivity.get() == this) {
            sActiveActivity = new WeakReference<>(null);
        }
        cancelPendingTest();
        mBgThread.quitSafely();
        super.onDestroy();
    }

    private SharedPreferences localPrefs() {
        return getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
    }

    private SharedPreferences activePrefs() {
        XposedService svc = sXposedService;
        if (svc != null) {
            try {
                return svc.getRemotePreferences(Constants.PREFS_NAME);
            } catch (Throwable ignored) {}
        }
        return localPrefs();
    }

    private void loadDenied() {
        mDenied.clear();
        mDropOverrides.clear();
        mDowngradeOverrides.clear();

        SharedPreferences p = activePrefs();
        Set<String> stored = p.getStringSet(Constants.KEY_DENIED, null);
        if (stored == null || stored.isEmpty()) {
            stored = localPrefs().getStringSet(Constants.KEY_DENIED, null);
        }
        if (stored != null) {
            mDenied.addAll(stored);
        }
        mDenied.add(Constants.APPLICATION_ID);

        Set<String> drops = p.getStringSet(Constants.KEY_DROP_OVERRIDES, null);
        if (drops == null) {
            drops = localPrefs().getStringSet(Constants.KEY_DROP_OVERRIDES, null);
        }
        if (drops != null) {
            mDropOverrides.addAll(drops);
        }

        Set<String> downs = p.getStringSet(Constants.KEY_DOWNGRADE_OVERRIDES, null);
        if (downs == null) {
            downs = localPrefs().getStringSet(Constants.KEY_DOWNGRADE_OVERRIDES, null);
        }
        if (downs != null) {
            mDowngradeOverrides.addAll(downs);
        }

        mModeDrop = p.getBoolean(Constants.KEY_MODE_DROP,
                localPrefs().getBoolean(Constants.KEY_MODE_DROP, false));
        mAllowSystem = p.getBoolean(Constants.KEY_ALLOW_SYSTEM,
                localPrefs().getBoolean(Constants.KEY_ALLOW_SYSTEM, false));

        if (mModeDropToggle != null) {
            mModeDropToggle.setOnCheckedChangeListener(null);
            mModeDropToggle.setChecked(mModeDrop);
            mModeDropToggle.setOnCheckedChangeListener(new ModeDropCheckListener());
        }
        if (mAllowSystemToggle != null) {
            mAllowSystemToggle.setOnCheckedChangeListener(null);
            mAllowSystemToggle.setChecked(mAllowSystem);
            mAllowSystemToggle.setOnCheckedChangeListener(new AllowSystemCheckListener());
        }
    }

    private int getPackageMode(String pkg) {
        if (mDropOverrides.contains(pkg)) return MODE_DROP;
        if (mDowngradeOverrides.contains(pkg)) return MODE_DOWNGRADE;
        return MODE_AUTO;
    }

    private void cyclePackageMode(String pkg) {
        int current = getPackageMode(pkg);
        mDropOverrides.remove(pkg);
        mDowngradeOverrides.remove(pkg);
        if (current == MODE_AUTO) {
            mDowngradeOverrides.add(pkg);
        } else if (current == MODE_DOWNGRADE) {
            mDropOverrides.add(pkg);
        }
    }

    private void saveDeniedInternal(boolean showToast) {
        Set<String> outDenied = new HashSet<>(mDenied);
        outDenied.add(Constants.APPLICATION_ID);
        Set<String> outDrop = new HashSet<>(mDropOverrides);
        Set<String> outDown = new HashSet<>(mDowngradeOverrides);

        localPrefs().edit()
                .putStringSet(Constants.KEY_DENIED, outDenied)
                .putStringSet(Constants.KEY_DROP_OVERRIDES, outDrop)
                .putStringSet(Constants.KEY_DOWNGRADE_OVERRIDES, outDown)
                .putBoolean(Constants.KEY_MODE_DROP, mModeDrop)
                .putBoolean(Constants.KEY_ALLOW_SYSTEM, mAllowSystem)
                .apply();

        XposedService svc = sXposedService;
        if (svc != null) {
            try {
                SharedPreferences rp = svc.getRemotePreferences(Constants.PREFS_NAME);
                rp.edit()
                        .putStringSet(Constants.KEY_DENIED, outDenied)
                        .putStringSet(Constants.KEY_DROP_OVERRIDES, outDrop)
                        .putStringSet(Constants.KEY_DOWNGRADE_OVERRIDES, outDown)
                        .putBoolean(Constants.KEY_MODE_DROP, mModeDrop)
                        .putBoolean(Constants.KEY_ALLOW_SYSTEM, mAllowSystem)
                        .commit();
                if (showToast) {
                    Toast.makeText(this, "Synced to system_server (" + Math.max(outDenied.size() - 1, 0) + " targets)", Toast.LENGTH_SHORT).show();
                }
                return;
            } catch (Throwable ignored) {}
        }
        if (showToast) {
            Toast.makeText(this, "Saved locally", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportConfigToClipboard() {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 2);
            root.put("mode_drop", mModeDrop);
            root.put("allow_system", mAllowSystem);
            root.put("denied", new JSONArray(mDenied));
            root.put("drop_overrides", new JSONArray(mDropOverrides));
            root.put("downgrade_overrides", new JSONArray(mDowngradeOverrides));
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("IsolPolicy-Config", root.toString()));
                Toast.makeText(this, "Policy JSON copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void importConfigFromClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemCount() == 0) {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
                return;
            }
            CharSequence cs = cm.getPrimaryClip().getItemAt(0).getText();
            if (cs == null) return;
            JSONObject root = new JSONObject(cs.toString().trim());
            JSONArray deniedArr = root.optJSONArray("denied");
            if (deniedArr == null) {
                Toast.makeText(this, "Invalid config JSON in clipboard", Toast.LENGTH_SHORT).show();
                return;
            }
            mDenied.clear();
            for (int i = 0; i < deniedArr.length(); i++) {
                String s = deniedArr.optString(i, null);
                if (s != null && !s.isEmpty()) mDenied.add(s);
            }
            mDenied.add(Constants.APPLICATION_ID);

            mDropOverrides.clear();
            JSONArray dropArr = root.optJSONArray("drop_overrides");
            if (dropArr != null) {
                for (int i = 0; i < dropArr.length(); i++) {
                    String s = dropArr.optString(i, null);
                    if (s != null && !s.isEmpty()) mDropOverrides.add(s);
                }
            }

            mDowngradeOverrides.clear();
            JSONArray downArr = root.optJSONArray("downgrade_overrides");
            if (downArr != null) {
                for (int i = 0; i < downArr.length(); i++) {
                    String s = downArr.optString(i, null);
                    if (s != null && !s.isEmpty()) mDowngradeOverrides.add(s);
                }
            }

            mModeDrop = root.optBoolean("mode_drop", mModeDrop);
            mAllowSystem = root.optBoolean("allow_system", mAllowSystem);
            if (mModeDropToggle != null) mModeDropToggle.setChecked(mModeDrop);
            if (mAllowSystemToggle != null) mAllowSystemToggle.setChecked(mAllowSystem);

            saveDeniedInternal(false);
            rescan();
            Toast.makeText(this, "Imported & synced (" + Math.max(mDenied.size() - 1, 0) + " targets)", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Clipboard does not contain valid IsolPolicy JSON", Toast.LENGTH_SHORT).show();
        }
    }

    private void autoSelectThreats() {
        int added = 0;
        for (Item it : mAdapter.mAll) {
            if (it.kind == Item.Kind.SELF) continue;
            boolean selectable = it.kind == Item.Kind.USER || (mAllowSystem && it.kind == Item.Kind.SYSTEM);
            if (!selectable) continue;
            if (it.hasAppZygote || it.hasPreload) {
                if (mDenied.add(it.pkg)) {
                    added++;
                }
            }
        }
        mAdapter.reSortAndFilter();
        refreshStatus();
        Toast.makeText(this, added > 0 ? ("Auto-selected " + added + " new threat package(s)") : "All detected threat packages are already selected", Toast.LENGTH_SHORT).show();
    }

    private static String extractManifestZygotePreload(PackageManager pm, ApplicationInfo ai) {
        XmlResourceParser parser = null;
        try {
            Resources res = pm.getResourcesForApplication(ai);
            AssetManager assets = res.getAssets();
            parser = assets.openXmlResourceParser("AndroidManifest.xml");
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("application".equals(tag)) {
                        String preload = parser.getAttributeValue(ANDROID_NS, "zygotePreloadName");
                        if (preload != null && !preload.isEmpty()) {
                            return preload;
                        }
                        break;
                    }
                }
                eventType = parser.next();
            }
        } catch (Throwable ignored) {
        } finally {
            if (parser != null) {
                try {
                    parser.close();
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private class ScanRunnable implements Runnable {
        @Override
        public void run() {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> all = pm.getInstalledApplications(0);
            List<Item> built = new ArrayList<>(all.size());
            int eligible = 0;
            int threats = 0;
            int isolatedTotal = 0;
            String selfPkg = Constants.APPLICATION_ID;

            for (ApplicationInfo ai : all) {
                boolean isSelf = selfPkg.equals(ai.packageName);
                boolean isSystem = ai.uid < 10000
                        || ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                            && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0);
                Item.Kind k = isSelf ? Item.Kind.SELF
                        : isSystem ? Item.Kind.SYSTEM
                        : Item.Kind.USER;

                boolean hasAppZygote = false;
                boolean hasIsolated = false;
                int appZygoteSvcCount = 0;

                try {
                    PackageInfo pi = pm.getPackageInfo(ai.packageName, PackageManager.GET_SERVICES);
                    if (pi != null && pi.services != null) {
                        for (ServiceInfo si : pi.services) {
                            if (si == null) continue;
                            if ((si.flags & SERVICE_FLAG_ISOLATED) != 0) {
                                hasIsolated = true;
                            }
                            if ((si.flags & SERVICE_FLAG_APP_ZYGOTE) != 0) {
                                hasAppZygote = true;
                                appZygoteSvcCount++;
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                String preloadClass = null;
                if (hasAppZygote || hasIsolated) {
                    preloadClass = extractManifestZygotePreload(pm, ai);
                }
                boolean hasPreload = preloadClass != null && !preloadClass.isEmpty();

                String label;
                try {
                    label = ai.loadLabel(pm).toString();
                } catch (Throwable t) {
                    label = ai.packageName;
                }
                Drawable icon = null;
                try {
                    icon = ai.loadIcon(pm);
                } catch (Throwable ignored) {}

                built.add(new Item(ai.packageName, label, icon, k, hasAppZygote, hasPreload, hasIsolated, appZygoteSvcCount, preloadClass));
                boolean selectable = k == Item.Kind.USER || (mAllowSystem && k == Item.Kind.SYSTEM);
                if (selectable) {
                    eligible++;
                    if (hasAppZygote || hasPreload) threats++;
                    else if (hasIsolated) isolatedTotal++;
                }
            }
            Collections.sort(built, ITEM_COMPARATOR);
            final int eligibleCount = eligible;
            final int threatCount = threats;
            final int isoCount = isolatedTotal;
            mUi.post(new ApplyScanResult(built, eligibleCount, threatCount, isoCount));
        }
    }

    private class ApplyScanResult implements Runnable {
        private final List<Item> mResult;
        private final int mEligible;
        private final int mThreats;
        private final int mIso;

        ApplyScanResult(List<Item> r, int e, int t, int iso) {
            this.mResult = r;
            this.mEligible = e;
            this.mThreats = t;
            this.mIso = iso;
        }

        @Override
        public void run() {
            mEligibleCount = mEligible;
            mAppZygoteThreatCount = mThreats;
            mIsolatedCount = mIso;
            mAdapter.setItems(mResult);
            updateFilterButtonsUi();
            refreshStatus();
            if (mProgress != null) mProgress.setVisibility(View.GONE);
            if (mList != null) mList.setVisibility(View.VISIBLE);
        }
    }

    private void rescan() {
        if (mProgress != null) mProgress.setVisibility(View.VISIBLE);
        if (mList != null) mList.setVisibility(View.GONE);
        mAdapter.setItems(Collections.<Item>emptyList());
        mEligibleCount = 0;
        refreshStatus();
        mBg.post(new ScanRunnable());
    }

    private void refreshStatus() {
        StringBuilder sb = new StringBuilder();
        XposedService svc = sXposedService;
        boolean active = ModuleStatus.isActive() || svc != null;
        sb.append("module           : ").append(active ? "active" : "disabled");
        if (svc != null) {
            try {
                sb.append(" (").append(svc.getFrameworkName())
                  .append(" API ").append(svc.getApiVersion()).append(")");
            } catch (Throwable ignored) {}
        }
        sb.append('\n');
        sb.append("hook layers      : 4/4 (byAppZygote + usesAppZygote + startProcess*)\n");
        sb.append("default engine   : ").append(mModeDrop ? "hard drop (skip fork)" : "silent downgrade (isolated_app)").append('\n');
        sb.append("threat radar     : ").append(mAppZygoteThreatCount).append(" app_zygote/preload  |  ").append(mIsolatedCount).append(" isolated\n");
        sb.append(mTestHeadline).append('\n');
        if (!mTestDetail.isEmpty()) sb.append(mTestDetail).append('\n');
        sb.append("eligible: ").append(mEligibleCount)
                .append("    selected: ").append(Math.max(mDenied.size() - 1, 0))
                .append("    overrides: ").append(mDropOverrides.size() + mDowngradeOverrides.size());
        mStatus.setText(sb.toString());
    }

    private void runTest() {
        mTestHeadline = "isolated service : binding ...";
        mTestDetail = "";
        refreshStatus();
        cancelPendingTest();
        mBindStartRealtime = SystemClock.elapsedRealtime();
        final ServiceConnection conn = new ServiceConnection() {
            private boolean done;

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                if (done) return;
                done = true;
                long elapsed = Math.max(1L, SystemClock.elapsedRealtime() - mBindStartRealtime);
                deliverTestResult(service, elapsed);
                cancelPendingTest();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {}

            @Override
            public void onNullBinding(ComponentName name) {
                if (done) return;
                done = true;
                mTestHeadline = "isolated service : not bound";
                mTestDetail = "reason           : null binding";
                refreshStatus();
                cancelPendingTest();
            }
        };
        mTestConnection = conn;
        Intent intent = new Intent(this, IsolationPolicyService.class);
        boolean bound;
        try {
            bound = bindIsolatedService(intent, Context.BIND_AUTO_CREATE,
                    "isolationpolicy", getMainExecutor(), conn);
        } catch (Throwable t) {
            bound = false;
            mTestHeadline = "isolated service : not bound";
            mTestDetail = "reason           : bind threw " + t.getClass().getSimpleName();
            refreshStatus();
        }
        if (!bound) {
            mTestConnection = null;
            mTestHeadline = "isolated service : not bound";
            mTestDetail = "reason           : bindIsolatedService returned false";
            refreshStatus();
            return;
        }
        mTestTimeout = new Runnable() {
            @Override
            public void run() {
                if (mTestConnection != conn) return;
                try {
                    unbindService(conn);
                } catch (Throwable ignored) {}
                mTestConnection = null;
                mTestTimeout = null;
                mTestHeadline = "isolated service : not bound";
                mTestDetail = "reason           : connection timed out (hard drop active)";
                refreshStatus();
            }
        };
        mUi.postDelayed(mTestTimeout, TEST_BIND_TIMEOUT_MS);
    }

    private void cancelPendingTest() {
        if (mTestTimeout != null) {
            mUi.removeCallbacks(mTestTimeout);
            mTestTimeout = null;
        }
        if (mTestConnection != null) {
            try {
                unbindService(mTestConnection);
            } catch (Throwable ignored) {}
            mTestConnection = null;
        }
    }

    private void deliverTestResult(IBinder service, long elapsedMs) {
        if (service == null) {
            mTestHeadline = "isolated service : not bound";
            mTestDetail = "reason           : null service";
            refreshStatus();
            return;
        }
        try {
            IIsolationPolicyService server = IIsolationPolicyService.Stub.asInterface(service);
            String report = server.getReport();
            mTestHeadline = "isolated service : bound in " + elapsedMs + " ms";
            mTestDetail = report != null ? report : "";
        } catch (RemoteException e) {
            mTestHeadline = "isolated service : transact failed";
            mTestDetail = "reason           : " + e;
        }
        refreshStatus();
    }

    private void updateFilterButtonsUi() {
        int activeBg = Color.parseColor("#FF0277BD");
        int idleBg = Color.parseColor("#FF2C2C2C");
        int textC = Color.parseColor("#FFE8E8E8");

        if (mBtnFilterAll != null) {
            mBtnFilterAll.setBackgroundColor(mCategoryFilter == FILTER_ALL ? activeBg : idleBg);
            mBtnFilterAll.setTextColor(textC);
            mBtnFilterAll.setText("All (" + mEligibleCount + ")");
        }
        if (mBtnFilterThreats != null) {
            mBtnFilterThreats.setBackgroundColor(mCategoryFilter == FILTER_THREATS ? activeBg : idleBg);
            mBtnFilterThreats.setTextColor(textC);
            mBtnFilterThreats.setText("Threats (" + mAppZygoteThreatCount + ")");
        }
        if (mBtnFilterSelected != null) {
            mBtnFilterSelected.setBackgroundColor(mCategoryFilter == FILTER_SELECTED ? activeBg : idleBg);
            mBtnFilterSelected.setTextColor(textC);
            mBtnFilterSelected.setText("Selected (" + Math.max(mDenied.size() - 1, 0) + ")");
        }
    }

    private View buildLayout() {
        int gray = Color.parseColor("#FF161618");
        int faint = Color.parseColor("#FF242428");
        int textC = Color.parseColor("#FFE8E8E8");
        int sub = Color.parseColor("#FFA0A0A8");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(gray);
        int pad = dp(10);
        root.setPadding(pad, pad, pad, pad);

        mStatus = new TextView(this);
        mStatus.setTypeface(Typeface.MONOSPACE);
        mStatus.setTextColor(textC);
        mStatus.setTextSize(11.5f);
        mStatus.setBackgroundColor(faint);
        mStatus.setPadding(dp(10), dp(8), dp(10), dp(8));
        mStatus.setTextIsSelectable(true);
        root.addView(mStatus, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout optionsRow = new LinearLayout(this);
        optionsRow.setOrientation(LinearLayout.HORIZONTAL);
        optionsRow.setBackgroundColor(faint);
        optionsRow.setPadding(dp(6), dp(2), dp(6), dp(2));

        mModeDropToggle = new CheckBox(this);
        mModeDropToggle.setText("Global Hard Drop");
        mModeDropToggle.setTextColor(textC);
        mModeDropToggle.setTextSize(12f);
        mModeDropToggle.setOnCheckedChangeListener(new ModeDropCheckListener());
        optionsRow.addView(mModeDropToggle, new LinearLayout.LayoutParams(0, WRAP, 1f));

        mAllowSystemToggle = new CheckBox(this);
        mAllowSystemToggle.setText("Unlock System Apps");
        mAllowSystemToggle.setTextColor(textC);
        mAllowSystemToggle.setTextSize(12f);
        mAllowSystemToggle.setOnCheckedChangeListener(new AllowSystemCheckListener());
        optionsRow.addView(mAllowSystemToggle, new LinearLayout.LayoutParams(0, WRAP, 1f));

        LinearLayout.LayoutParams opLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        opLp.topMargin = dp(6);
        root.addView(optionsRow, opLp);

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);

        mBtnFilterAll = new Button(this);
        mBtnFilterAll.setTextSize(11f);
        mBtnFilterAll.setAllCaps(false);
        mBtnFilterAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCategoryFilter = FILTER_ALL;
                updateFilterButtonsUi();
                mAdapter.setFilter(mFilter, mCategoryFilter);
            }
        });
        filterRow.addView(mBtnFilterAll, new LinearLayout.LayoutParams(0, dp(36), 1f));

        mBtnFilterThreats = new Button(this);
        mBtnFilterThreats.setTextSize(11f);
        mBtnFilterThreats.setAllCaps(false);
        mBtnFilterThreats.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCategoryFilter = FILTER_THREATS;
                updateFilterButtonsUi();
                mAdapter.setFilter(mFilter, mCategoryFilter);
            }
        });
        LinearLayout.LayoutParams ftLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        ftLp.leftMargin = dp(4);
        filterRow.addView(mBtnFilterThreats, ftLp);

        mBtnFilterSelected = new Button(this);
        mBtnFilterSelected.setTextSize(11f);
        mBtnFilterSelected.setAllCaps(false);
        mBtnFilterSelected.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCategoryFilter = FILTER_SELECTED;
                updateFilterButtonsUi();
                mAdapter.setFilter(mFilter, mCategoryFilter);
            }
        });
        LinearLayout.LayoutParams fsLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        fsLp.leftMargin = dp(4);
        filterRow.addView(mBtnFilterSelected, fsLp);

        mBtnAutoSelect = new Button(this);
        mBtnAutoSelect.setText("+Threats");
        mBtnAutoSelect.setTextSize(11f);
        mBtnAutoSelect.setAllCaps(false);
        mBtnAutoSelect.setBackgroundColor(Color.parseColor("#FFD84315"));
        mBtnAutoSelect.setTextColor(textC);
        mBtnAutoSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autoSelectThreats();
                updateFilterButtonsUi();
            }
        });
        LinearLayout.LayoutParams asLp = new LinearLayout.LayoutParams(WRAP, dp(36));
        asLp.leftMargin = dp(4);
        filterRow.addView(mBtnAutoSelect, asLp);

        LinearLayout.LayoutParams frLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        frLp.topMargin = dp(6);
        root.addView(filterRow, frLp);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);

        mSearch = new EditText(this);
        mSearch.setHint("search name, package, or threat");
        mSearch.setTextColor(textC);
        mSearch.setHintTextColor(sub);
        mSearch.setTextSize(13f);
        mSearch.setSingleLine(true);
        mSearch.setBackgroundColor(faint);
        mSearch.setPadding(dp(10), dp(8), dp(10), dp(8));
        mSearch.addTextChangedListener(new SearchWatcher());
        searchRow.addView(mSearch, new LinearLayout.LayoutParams(0, WRAP, 1f));

        Button refresh = new Button(this);
        refresh.setText("Scan");
        refresh.setTextSize(12f);
        refresh.setAllCaps(false);
        refresh.setOnClickListener(new RefreshClick());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(WRAP, WRAP);
        rp.leftMargin = dp(6);
        searchRow.addView(refresh, rp);

        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(MATCH, WRAP);
        sp.topMargin = dp(6);
        root.addView(searchRow, sp);

        FrameLayout listArea = new FrameLayout(this);
        listArea.setBackgroundColor(faint);
        mList = new RecyclerView(this);
        mList.setLayoutManager(new LinearLayoutManager(this));
        mList.setAdapter(mAdapter);
        mList.setHasFixedSize(true);
        mList.setVisibility(View.GONE);
        listArea.addView(mList, new FrameLayout.LayoutParams(MATCH, MATCH));
        mProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        mProgress.setIndeterminate(true);
        mProgress.setIndeterminateTintList(ColorStateList.valueOf(Color.parseColor("#FF03A9F4")));
        FrameLayout.LayoutParams pgp = new FrameLayout.LayoutParams(WRAP, WRAP);
        pgp.gravity = Gravity.CENTER;
        listArea.addView(mProgress, pgp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        lp.topMargin = dp(6);
        root.addView(listArea, lp);

        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnExport = new Button(this);
        btnExport.setText("Export");
        btnExport.setTextSize(12f);
        btnExport.setAllCaps(false);
        btnExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportConfigToClipboard();
            }
        });
        bottomRow.addView(btnExport, new LinearLayout.LayoutParams(WRAP, WRAP));

        Button btnImport = new Button(this);
        btnImport.setText("Import");
        btnImport.setTextSize(12f);
        btnImport.setAllCaps(false);
        btnImport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importConfigFromClipboard();
            }
        });
        LinearLayout.LayoutParams impLp = new LinearLayout.LayoutParams(WRAP, WRAP);
        impLp.leftMargin = dp(4);
        bottomRow.addView(btnImport, impLp);

        mApply = new Button(this);
        mApply.setText("Apply changes (Hot-Reload)");
        mApply.setTextSize(13f);
        mApply.setAllCaps(false);
        mApply.setBackgroundColor(Color.parseColor("#FF2E7D32"));
        mApply.setTextColor(textC);
        mApply.setOnClickListener(new ApplyClick());
        LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        brp.leftMargin = dp(6);
        bottomRow.addView(mApply, brp);

        LinearLayout.LayoutParams botLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        botLp.topMargin = dp(6);
        root.addView(bottomRow, botLp);

        return root;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final class Item {
        enum Kind { USER, SYSTEM, SELF }
        final String pkg;
        final String label;
        final String labelLower;
        final String pkgLower;
        final Drawable icon;
        final Kind kind;
        final boolean hasAppZygote;
        final boolean hasPreload;
        final boolean hasIsolated;
        final int appZygoteSvcCount;
        final String preloadClass;
        final String preloadLower;

        Item(String pkg, String label, Drawable icon, Kind kind,
             boolean hasAppZygote, boolean hasPreload, boolean hasIsolated, int svcCount, String preloadClass) {
            this.pkg = pkg;
            this.label = label;
            this.labelLower = label.toLowerCase();
            this.pkgLower = pkg.toLowerCase();
            this.icon = icon;
            this.kind = kind;
            this.hasAppZygote = hasAppZygote;
            this.hasPreload = hasPreload;
            this.hasIsolated = hasIsolated;
            this.appZygoteSvcCount = svcCount;
            this.preloadClass = preloadClass != null ? preloadClass : "";
            this.preloadLower = this.preloadClass.toLowerCase();
        }
    }

    private final java.util.Comparator<Item> ITEM_COMPARATOR = new java.util.Comparator<Item>() {
        @Override
        public int compare(Item a, Item b) {
            int oa = order(a);
            int ob = order(b);
            if (oa != ob) return oa - ob;
            int byLabel = a.labelLower.compareTo(b.labelLower);
            if (byLabel != 0) return byLabel;
            return a.pkg.compareTo(b.pkg);
        }

        private int order(Item it) {
            if (it.kind == Item.Kind.SELF) return 0;
            if (mDenied.contains(it.pkg)) return 1;
            if (it.hasAppZygote) return 2;
            if (it.hasPreload) return 3;
            if (it.hasIsolated && it.kind == Item.Kind.USER) return 4;
            if (it.kind == Item.Kind.USER) return 5;
            return 6;
        }
    };

    private class Adapter extends RecyclerView.Adapter<RowVH> {
        private final List<Item> mAll = new ArrayList<>();
        private final List<Item> mShown = new ArrayList<>();
        private String mAdapterFilter = "";
        private int mCatFilter = FILTER_ALL;

        void setItems(List<Item> items) {
            mAll.clear();
            mAll.addAll(items);
            recompute();
        }

        void setFilter(String f, int cat) {
            mAdapterFilter = f == null ? "" : f.toLowerCase();
            mCatFilter = cat;
            recompute();
        }

        void reSortAndFilter() {
            Collections.sort(mAll, ITEM_COMPARATOR);
            recompute();
        }

        private void recompute() {
            mShown.clear();
            for (Item it : mAll) {
                if (mCatFilter == FILTER_THREATS && !it.hasAppZygote && !it.hasPreload && it.kind != Item.Kind.SELF) {
                    continue;
                }
                if (mCatFilter == FILTER_SELECTED && !mDenied.contains(it.pkg)) {
                    continue;
                }
                if (!mAdapterFilter.isEmpty()) {
                    boolean matchText = it.labelLower.contains(mAdapterFilter)
                            || it.pkgLower.contains(mAdapterFilter)
                            || (!it.preloadLower.isEmpty() && it.preloadLower.contains(mAdapterFilter))
                            || (it.hasAppZygote && "app_zygote".contains(mAdapterFilter))
                            || (it.hasPreload && "preload".contains(mAdapterFilter))
                            || (it.hasIsolated && "isolated".contains(mAdapterFilter));
                    if (!matchText) continue;
                }
                mShown.add(it);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RowVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RowVH(buildRowView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RowVH holder, int position) {
            holder.bind(mShown.get(position));
        }

        @Override
        public int getItemCount() {
            return mShown.size();
        }
    }

    private View buildRowView(Context ctx) {
        int activeC = Color.parseColor("#FFE8E8E8");
        int subC = Color.parseColor("#FFA0A0A0");

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(8);
        row.setPadding(pad, dp(6), pad, dp(6));
        row.setLayoutParams(new RecyclerView.LayoutParams(MATCH, WRAP));

        ImageView icon = new ImageView(ctx);
        icon.setId(android.R.id.icon);
        int iconSize = dp(38);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(iconSize, iconSize);
        ip.rightMargin = dp(10);
        row.addView(icon, ip);

        LinearLayout vbox = new LinearLayout(ctx);
        vbox.setOrientation(LinearLayout.VERTICAL);

        TextView label = new TextView(ctx);
        label.setId(android.R.id.text1);
        label.setTextSize(14.5f);
        label.setTextColor(activeC);
        label.setTypeface(label.getTypeface(), Typeface.BOLD);

        TextView pkg = new TextView(ctx);
        pkg.setId(android.R.id.text2);
        pkg.setTextSize(11.5f);
        pkg.setTextColor(subC);
        pkg.setTypeface(Typeface.MONOSPACE);

        TextView badge = new TextView(ctx);
        badge.setId(android.R.id.summary);
        badge.setTextSize(10.5f);
        badge.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        badge.setPadding(0, dp(2), 0, 0);

        vbox.addView(label);
        vbox.addView(pkg);
        vbox.addView(badge);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        row.addView(vbox, vp);

        TextView modePill = new TextView(ctx);
        modePill.setId(android.R.id.button1);
        modePill.setTextSize(10.5f);
        modePill.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        modePill.setGravity(Gravity.CENTER);
        modePill.setPadding(dp(8), dp(4), dp(8), dp(4));
        LinearLayout.LayoutParams mpLp = new LinearLayout.LayoutParams(WRAP, WRAP);
        mpLp.rightMargin = dp(6);
        row.addView(modePill, mpLp);

        CheckBox cb = new CheckBox(ctx);
        cb.setId(android.R.id.checkbox);
        row.addView(cb);

        return row;
    }

    private class RowVH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        final TextView pkg;
        final TextView badge;
        final TextView modePill;
        final CheckBox cb;
        Item bound;

        RowVH(View v) {
            super(v);
            icon = v.findViewById(android.R.id.icon);
            label = v.findViewById(android.R.id.text1);
            pkg = v.findViewById(android.R.id.text2);
            badge = v.findViewById(android.R.id.summary);
            modePill = v.findViewById(android.R.id.button1);
            cb = v.findViewById(android.R.id.checkbox);

            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (cb.isEnabled()) cb.setChecked(!cb.isChecked());
                }
            });

            modePill.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (bound == null || !mDenied.contains(bound.pkg)) return;
                    cyclePackageMode(bound.pkg);
                    bindModePill(bound);
                    refreshStatus();
                }
            });
        }

        private void bindModePill(Item it) {
            if (!mDenied.contains(it.pkg)) {
                modePill.setVisibility(View.GONE);
                return;
            }
            modePill.setVisibility(View.VISIBLE);
            int mode = getPackageMode(it.pkg);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(6));
            if (mode == MODE_DOWNGRADE) {
                modePill.setText("DOWNGRADE");
                modePill.setTextColor(Color.parseColor("#FF80D8FF"));
                bg.setColor(Color.parseColor("#FF01579B"));
            } else if (mode == MODE_DROP) {
                modePill.setText("HARD DROP");
                modePill.setTextColor(Color.parseColor("#FFFF8A80"));
                bg.setColor(Color.parseColor("#FFB71C1C"));
            } else {
                modePill.setText("AUTO");
                modePill.setTextColor(Color.parseColor("#FFB0BEC5"));
                bg.setColor(Color.parseColor("#FF37474F"));
            }
            modePill.setBackground(bg);
        }

        void bind(Item it) {
            bound = it;
            int active = Color.parseColor("#FFE8E8E8");
            int dim = Color.parseColor("#FF686870");
            int sub = Color.parseColor("#FFA0A0A8");
            icon.setImageDrawable(it.icon);

            String prefix = it.kind == Item.Kind.SELF ? "[self] "
                    : it.kind == Item.Kind.SYSTEM ? "[sys] "
                    : "";
            boolean selectable = it.kind == Item.Kind.USER
                    || (mAllowSystem && it.kind == Item.Kind.SYSTEM);
            label.setText(prefix + it.label);
            label.setTextColor(selectable ? active : dim);
            pkg.setText(it.pkg);
            pkg.setTextColor(selectable ? sub : dim);

            StringBuilder bText = new StringBuilder();
            if (it.hasAppZygote) {
                bText.append("[APP_ZYGOTE x").append(it.appZygoteSvcCount).append("] ");
            }
            if (it.hasPreload) {
                String shortPreload = it.preloadClass;
                int dot = shortPreload.lastIndexOf('.');
                if (dot >= 0 && dot + 1 < shortPreload.length()) {
                    shortPreload = shortPreload.substring(dot + 1);
                }
                bText.append("[PRELOAD: ").append(shortPreload).append("] ");
            }
            if (!it.hasAppZygote && !it.hasPreload && it.hasIsolated) {
                bText.append("[ISOLATED_SVC]");
            }

            if (bText.length() > 0) {
                badge.setVisibility(View.VISIBLE);
                badge.setText(bText.toString().trim());
                if (it.hasAppZygote) {
                    badge.setTextColor(Color.parseColor("#FFFF5252"));
                } else if (it.hasPreload) {
                    badge.setTextColor(Color.parseColor("#FFFFAB40"));
                } else {
                    badge.setTextColor(Color.parseColor("#FF40C4FF"));
                }
            } else {
                badge.setVisibility(View.GONE);
            }

            cb.setOnCheckedChangeListener(null);
            if (it.kind == Item.Kind.SELF) {
                cb.setChecked(true);
                cb.setEnabled(false);
            } else if (!selectable) {
                cb.setChecked(mDenied.contains(it.pkg));
                cb.setEnabled(false);
            } else {
                cb.setChecked(mDenied.contains(it.pkg));
                cb.setEnabled(true);
            }

            bindModePill(it);

            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton btn, boolean on) {
                    if (bound == null) return;
                    boolean allowed = bound.kind == Item.Kind.USER
                            || (mAllowSystem && bound.kind == Item.Kind.SYSTEM);
                    if (!allowed) return;
                    if (on) {
                        mDenied.add(bound.pkg);
                    } else {
                        mDenied.remove(bound.pkg);
                        mDropOverrides.remove(bound.pkg);
                        mDowngradeOverrides.remove(bound.pkg);
                    }
                    bindModePill(bound);
                    updateFilterButtonsUi();
                    refreshStatus();
                }
            });
        }
    }

    private class ModeDropCheckListener implements CompoundButton.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            mModeDrop = isChecked;
            refreshStatus();
        }
    }

    private class AllowSystemCheckListener implements CompoundButton.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            mAllowSystem = isChecked;
            rescan();
        }
    }

    private class ApplyClick implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            saveDeniedInternal(true);
            mAdapter.reSortAndFilter();
            updateFilterButtonsUi();
            refreshStatus();
            runTest();
        }
    }

    private class SearchWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void afterTextChanged(Editable s) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            mFilter = s.toString();
            mUi.removeCallbacks(mApplyFilter);
            mUi.postDelayed(mApplyFilter, FILTER_DEBOUNCE_MS);
        }
    }

    private class RefreshClick implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            rescan();
            runTest();
        }
    }
}
