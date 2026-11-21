package io.github.taro902.isolationpolicy.hook;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;

import io.github.libxposed.api.XposedModule;
import io.github.taro902.isolationpolicy.core.Logger;
import io.github.taro902.isolationpolicy.core.PolicyCodec;
import io.github.taro902.isolationpolicy.core.PolicyFlags;
import io.github.taro902.isolationpolicy.core.PolicySnapshot;

public final class PolicyEngine {

    private static final String TAG = "PolicyEngine";
    private static final int GRAPH_CAPACITY = 256;
    private static final int GRAPH_MASK = GRAPH_CAPACITY - 1;
    private static final int GRAPH_PROBE_LIMIT = 8;
    private static final int LIVE_MAP_LIMIT = 512;

    private final AtomicReference<PolicySnapshot> snapshotRef = new AtomicReference<>(PolicySnapshot.EMPTY);
    private final AtomicLong revisionCounter = new AtomicLong(1L);
    private final AtomicInteger verifiedSelfAppId = new AtomicInteger(-1);
    private final AtomicLongArray identityGraph = new AtomicLongArray(GRAPH_CAPACITY);
    private final ConcurrentHashMap<Integer, String> liveAppIdToPkg = new ConcurrentHashMap<>(64);
    private final ConcurrentHashMap<String, Integer> livePkgToAppId = new ConcurrentHashMap<>(64);
    private final ConcurrentHashMap<Integer, Integer> liveFlagOverlay = new ConcurrentHashMap<>(64);

    private volatile SharedPreferences remotePrefs;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
    private volatile int apiVersion = 102;
    private volatile String frameworkName = "LSPosed";

    public void init(XposedModule module) {
        try {
            apiVersion = module.getApiVersion();
            String fw = module.getFrameworkName();
            if (fw != null && !fw.isEmpty()) {
                frameworkName = fw;
            }
            ApplicationInfo modInfo = module.getModuleApplicationInfo();
            if (modInfo.uid >= 10000) {
                verifiedSelfAppId.set(modInfo.uid % 100000);
            }
        } catch (Throwable t) {
            Logger.e(TAG, "metadata init failed", t);
        }

        try {
            SharedPreferences prefs = module.getRemotePreferences(PolicyFlags.PREFS_GROUP);
            remotePrefs = prefs;
            reloadFromPrefs(prefs);
            prefListener = (sp, key) -> reloadFromPrefs(sp);
            prefs.registerOnSharedPreferenceChangeListener(prefListener);
        } catch (Throwable t) {
            Logger.e(TAG, "remote prefs bind failed", t);
        }
    }

    public void reloadFromPrefs(SharedPreferences prefs) {
        if (prefs == null) {
            return;
        }
        try {
            String policyRaw = prefs.getString(PolicyFlags.KEY_POLICY_MAP, "");
            String appIdRaw = prefs.getString(PolicyFlags.KEY_APPID_MAP, "");
            String legacyModesRaw = prefs.getString(PolicyFlags.KEY_LEGACY_V20_MODES, "");
            Set<String> legacyTargets = null;
            try {
                legacyTargets = prefs.getStringSet(PolicyFlags.KEY_LEGACY_V20_TARGETS, null);
            } catch (ClassCastException ignored) {
            }
            int prefSelfAppId = prefs.getInt(PolicyFlags.KEY_SELF_APP_ID, -1);
            int defaultAz = prefs.getInt(PolicyFlags.KEY_DEFAULT_AZ, PolicyFlags.AZ_OFF);
            long rev = revisionCounter.incrementAndGet();

            if (verifiedSelfAppId.get() < 10000 && prefSelfAppId >= 10000) {
                verifiedSelfAppId.compareAndSet(-1, prefSelfAppId);
            }

            PolicySnapshot next = PolicyCodec.decodeSnapshot(
                    policyRaw,
                    appIdRaw,
                    legacyModesRaw,
                    legacyTargets,
                    prefSelfAppId,
                    defaultAz,
                    rev
            );
            snapshotRef.set(next);
            liveFlagOverlay.clear();
        } catch (Throwable t) {
            Logger.e(TAG, "reload snapshot failed", t);
        }
    }

    public boolean isVerifiedSelfUid(int callingUid) {
        if (callingUid < 10000) {
            return false;
        }
        int callingAppId = callingUid % 100000;
        if (callingAppId < 10000 || callingAppId >= 90000) {
            return false;
        }
        int self = verifiedSelfAppId.get();
        if (self >= 10000) {
            return callingAppId == self;
        }
        int snapSelf = snapshotRef.get().selfAppId;
        return snapSelf >= 10000 && callingAppId == snapSelf;
    }

    public int resolveEffectiveAppId(int rawUid) {
        if (rawUid < 10000) {
            return -1;
        }
        int appId = rawUid % 100000;
        if (appId < 10000) {
            return -1;
        }
        if (appId < 90000) {
            return appId;
        }
        int mapped = lookupIdentityGraph(rawUid);
        if (mapped >= 10000) {
            return mapped;
        }
        if (rawUid != appId) {
            return lookupIdentityGraph(appId);
        }
        return -1;
    }

    public int resolveFlagsForUid(int rawUid) {
        int appId = resolveEffectiveAppId(rawUid);
        if (appId < 10000) {
            return 0;
        }
        PolicySnapshot snap = snapshotRef.get();
        int direct = snap.flagsForAppId(appId);
        if (direct != 0) {
            return direct;
        }
        Integer cached = liveFlagOverlay.get(appId);
        if (cached != null) {
            return cached;
        }
        String pkg = liveAppIdToPkg.get(appId);
        if (pkg == null) {
            pkg = snap.packageForAppId(appId);
        }
        if (pkg != null) {
            int pkgFl = snap.flagsForPackage(pkg);
            if (liveFlagOverlay.size() < LIVE_MAP_LIMIT) {
                liveFlagOverlay.put(appId, pkgFl);
            }
            return pkgFl;
        }
        return 0;
    }

    public int resolveFlagsForUidOrPackage(int rawUid, String packageName) {
        int flags = resolveFlagsForUid(rawUid);
        if (flags != 0) {
            return flags;
        }
        if (packageName != null && !packageName.isEmpty()) {
            return snapshotRef.get().flagsForPackage(packageName);
        }
        return 0;
    }

    public int getAzModeForPackageOrUid(String packageName, int rawUid) {
        int flags = resolveFlagsForUidOrPackage(rawUid, packageName);
        int az = PolicyFlags.getAzMode(flags);
        if (az != PolicyFlags.AZ_OFF) {
            return az;
        }
        int defAz = snapshotRef.get().defaultAzMode;
        if (defAz != PolicyFlags.AZ_OFF && packageName != null && !PolicyFlags.MODULE_PACKAGE.equals(packageName)) {
            int appId = rawUid >= 10000 ? (rawUid % 100000) : -1;
            if (appId < 0 || appId >= 10000) {
                return defAz;
            }
        }
        return PolicyFlags.AZ_OFF;
    }

    public boolean hasFlag(int rawUid, int flagBit) {
        return (resolveFlagsForUid(rawUid) & flagBit) != 0;
    }

    public boolean hasFlagForUidOrPackage(int rawUid, String packageName, int flagBit) {
        return (resolveFlagsForUidOrPackage(rawUid, packageName) & flagBit) != 0;
    }

    public void registerLiveProcess(int processUid, String packageName, int ownerUid) {
        int ownerAppId = ownerUid >= 10000 ? (ownerUid % 100000) : -1;
        int procAppId = processUid >= 10000 ? (processUid % 100000) : -1;

        if (ownerAppId >= 10000 && ownerAppId < 90000 && packageName != null && !packageName.isEmpty()) {
            if (PolicyFlags.MODULE_PACKAGE.equals(packageName)) {
                verifiedSelfAppId.set(ownerAppId);
            }
            if (liveAppIdToPkg.size() >= LIVE_MAP_LIMIT) {
                liveAppIdToPkg.clear();
                livePkgToAppId.clear();
                liveFlagOverlay.clear();
            }
            liveAppIdToPkg.put(ownerAppId, packageName);
            livePkgToAppId.put(packageName, ownerAppId);
            int pkgFl = snapshotRef.get().flagsForPackage(packageName);
            liveFlagOverlay.put(ownerAppId, pkgFl);
        }

        if (procAppId >= 90000 && ownerAppId >= 10000 && ownerAppId < 90000) {
            putIdentityGraph(processUid, ownerAppId);
        }
    }

    public void evictIsolatedUid(int uid) {
        if (uid < 90000) {
            return;
        }
        int base = mixIndex(uid);
        for (int step = 0; step < GRAPH_PROBE_LIMIT; step++) {
            int idx = (base + step) & GRAPH_MASK;
            long packed = identityGraph.get(idx);
            if (packed == 0L) {
                return;
            }
            int key = (int) (packed >>> 32);
            if (key == uid) {
                identityGraph.compareAndSet(idx, packed, 0L);
                return;
            }
        }
    }

    public String resolvePackageNameForAppId(int appId) {
        if (appId < 10000) {
            return "uid:" + appId;
        }
        String pkg = snapshotRef.get().packageForAppId(appId);
        if (pkg != null && !pkg.isEmpty()) {
            return pkg;
        }
        pkg = liveAppIdToPkg.get(appId);
        if (pkg != null && !pkg.isEmpty()) {
            return pkg;
        }
        return "u0_a" + (appId - 10000);
    }

    public int getGuardedCount() {
        return snapshotRef.get().guardedCount();
    }

    public int getIdentityGraphSize() {
        int count = 0;
        for (int i = 0; i < GRAPH_CAPACITY; i++) {
            if (identityGraph.get(i) != 0L) {
                count++;
            }
        }
        return count;
    }

    public int getApiVersion() {
        return apiVersion;
    }

    public String getFrameworkName() {
        return frameworkName;
    }

    private void putIdentityGraph(int isoUid, int ownerAppId) {
        long target = (((long) isoUid) << 32) | (ownerAppId & 0xFFFFFFFFL);
        int base = mixIndex(isoUid);
        int freeSlot = -1;
        for (int step = 0; step < GRAPH_PROBE_LIMIT; step++) {
            int idx = (base + step) & GRAPH_MASK;
            long existing = identityGraph.get(idx);
            if (existing == 0L) {
                if (freeSlot < 0) {
                    freeSlot = idx;
                }
                break;
            }
            int key = (int) (existing >>> 32);
            if (key == isoUid) {
                identityGraph.set(idx, target);
                return;
            }
        }
        if (freeSlot >= 0) {
            identityGraph.set(freeSlot, target);
        } else {
            identityGraph.set(base, target);
        }
    }

    private int lookupIdentityGraph(int isoUid) {
        int base = mixIndex(isoUid);
        for (int step = 0; step < GRAPH_PROBE_LIMIT; step++) {
            int idx = (base + step) & GRAPH_MASK;
            long packed = identityGraph.get(idx);
            if (packed == 0L) {
                return -1;
            }
            int key = (int) (packed >>> 32);
            if (key == isoUid) {
                return (int) (packed & 0xFFFFFFFFL);
            }
        }
        return -1;
    }

    private static int mixIndex(int key) {
        int h = key * 0x9E3779B9;
        return (h ^ (h >>> 16)) & GRAPH_MASK;
    }
}