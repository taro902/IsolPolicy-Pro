package io.github.taro902.isolationpolicy.hook;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;

import io.github.libxposed.api.XposedInterface;
import io.github.taro902.isolationpolicy.core.Logger;
import io.github.taro902.isolationpolicy.core.PolicyCodec;
import io.github.taro902.isolationpolicy.core.PolicyFlags;
import io.github.taro902.isolationpolicy.core.PolicySnapshot;

public final class PolicyEngine {

    public static final int PER_USER_RANGE = 100000;
    public static final int FIRST_APP_UID = 10000;
    public static final int FIRST_APP_ZYGOTE_ISOLATED_UID = 90000;
    public static final int LAST_ISOLATED_UID = 99999;

    public static final int GRAPH_SIZE = 256;
    private static final int GRAPH_MASK = GRAPH_SIZE - 1;
    private static final long EMPTY_SLOT = 0L;
    private static final long TOMBSTONE_SLOT = -1L;

    private final AtomicReference<PolicySnapshot> snapshotRef = new AtomicReference<PolicySnapshot>(PolicySnapshot.EMPTY);
    private final AtomicLong snapshotVersion = new AtomicLong(0L);
    private final AtomicLongArray identityGraph = new AtomicLongArray(GRAPH_SIZE);
    private final AtomicInteger graphActiveCount = new AtomicInteger(0);
    private volatile int verifiedSelfAppId = -1;
    private volatile SharedPreferences remotePrefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
        new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                reloadFromPreferences(sharedPreferences);
            }
        };

    public void attachModule(XposedInterface module) {
        if (module == null) {
            return;
        }
        try {
            ApplicationInfo selfInfo = module.getModuleApplicationInfo();
            if (selfInfo != null && selfInfo.uid >= FIRST_APP_UID) {
                verifiedSelfAppId = selfInfo.uid % PER_USER_RANGE;
            }
        } catch (Throwable t) {
            Logger.e("resolve self appId failed", t);
        }
        try {
            SharedPreferences prefs = module.getRemotePreferences(PolicyFlags.PREFS_GROUP);
            if (prefs != null) {
                remotePrefs = prefs;
                reloadFromPreferences(prefs);
                try {
                    prefs.registerOnSharedPreferenceChangeListener(prefListener);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            Logger.e("attach remote preferences failed", t);
        }
    }

    public void reloadFromPreferences(SharedPreferences prefs) {
        if (prefs == null) {
            return;
        }
        try {
            int defaultAz = PolicyFlags.AZ_OFF;
            try {
                defaultAz = prefs.getInt(PolicyFlags.KEY_DEFAULT_AZ, PolicyFlags.AZ_OFF) & PolicyFlags.AZ_MASK;
            } catch (Throwable ignored) {
            }

            int prefSelf = -1;
            try {
                prefSelf = prefs.getInt(PolicyFlags.KEY_SELF_APP_ID, -1);
            } catch (Throwable ignored) {
            }
            if (verifiedSelfAppId < FIRST_APP_UID && prefSelf >= FIRST_APP_UID) {
                verifiedSelfAppId = prefSelf % PER_USER_RANGE;
            }

            String rawPkg = null;
            String rawAppId = null;
            try {
                rawPkg = prefs.getString(PolicyFlags.KEY_POLICY_MAP, "");
                rawAppId = prefs.getString(PolicyFlags.KEY_APPID_MAP, "");
            } catch (Throwable ignored) {
            }

            LinkedHashMap<String, Integer> pkgMap = PolicyCodec.decodePolicyMap(rawPkg);
            if (pkgMap.isEmpty()) {
                String legacyModes = null;
                Set<String> legacyTargets = null;
                try {
                    legacyModes = prefs.getString(PolicyFlags.KEY_LEGACY_V20_MODES, "");
                    legacyTargets = prefs.getStringSet(PolicyFlags.KEY_LEGACY_V20_TARGETS, null);
                } catch (Throwable ignored) {
                }
                pkgMap = PolicyCodec.decodeLegacyV20(legacyModes, legacyTargets);
            }

            HashMap<Integer, Integer> idFlags = new HashMap<Integer, Integer>();
            HashMap<Integer, String> idPkg = new HashMap<Integer, String>();
            PolicyCodec.decodeAppIdMap(rawAppId, idFlags, idPkg);

            installSnapshot(defaultAz, pkgMap, idFlags, idPkg);
        } catch (Throwable t) {
            Logger.e("reloadFromPreferences failed", t);
        }
    }

    public void installSnapshot(
        int defaultAzMode,
        Map<String, Integer> pkgPolicies,
        Map<Integer, Integer> appIdPolicies,
        Map<Integer, String> appIdToPackage
    ) {
        long ver = snapshotVersion.incrementAndGet();
        PolicySnapshot next = PolicySnapshot.create(
            ver,
            defaultAzMode,
            verifiedSelfAppId,
            pkgPolicies,
            appIdPolicies,
            appIdToPackage
        );
        snapshotRef.set(next);
    }

    public PolicySnapshot getSnapshot() {
        return snapshotRef.get();
    }

    public int getVerifiedSelfAppId() {
        return verifiedSelfAppId;
    }

    public void setVerifiedSelfAppIdForTest(int appId) {
        this.verifiedSelfAppId = appId;
    }

    public static int toAppId(int uid) {
        if (uid < 0) {
            return -1;
        }
        return uid % PER_USER_RANGE;
    }

    public static boolean isIsolatedAppId(int appId) {
        return appId >= FIRST_APP_ZYGOTE_ISOLATED_UID && appId <= LAST_ISOLATED_UID;
    }

    public static boolean isIsolatedUid(int uid) {
        if (uid < 0) {
            return false;
        }
        return isIsolatedAppId(uid % PER_USER_RANGE);
    }

    public int resolveEffectiveAppId(int callingUid) {
        if (callingUid < 0) {
            return -1;
        }
        int appId = callingUid % PER_USER_RANGE;
        if (appId < FIRST_APP_UID) {
            return appId;
        }
        if (isIsolatedAppId(appId)) {
            int owner = lookupOwnerAppId(callingUid);
            if (owner < FIRST_APP_UID && callingUid != appId) {
                owner = lookupOwnerAppId(appId);
            }
            return owner >= FIRST_APP_UID ? owner : -1;
        }
        return appId;
    }

    public int resolvePolicy(int callingUid) {
        if (callingUid < FIRST_APP_UID) {
            return 0;
        }
        int appId = callingUid % PER_USER_RANGE;
        if (appId < FIRST_APP_UID) {
            return 0;
        }
        PolicySnapshot snap = snapshotRef.get();
        if (!snap.hasAnyGuarded()) {
            return 0;
        }
        if (isIsolatedAppId(appId)) {
            int owner = lookupOwnerAppId(callingUid);
            if (owner < FIRST_APP_UID && callingUid != appId) {
                owner = lookupOwnerAppId(appId);
            }
            if (owner < FIRST_APP_UID) {
                return 0;
            }
            return snap.flagsForAppId(owner);
        }
        return snap.flagsForAppId(appId);
    }

    public int resolvePolicyForPackageOrUid(String packageName, int uid) {
        PolicySnapshot snap = snapshotRef.get();
        if (!snap.hasAnyGuarded()) {
            return 0;
        }
        int fromUid = uid >= FIRST_APP_UID ? resolvePolicy(uid) : 0;
        int fromPkg = (packageName != null && !packageName.isEmpty()) ? snap.flagsForPackage(packageName) : 0;
        if (fromUid == 0) {
            return fromPkg;
        }
        if (fromPkg == 0) {
            return fromUid;
        }
        if (fromUid == snap.defaultAzMode && fromPkg != snap.defaultAzMode) {
            return fromPkg;
        }
        return fromUid;
    }

    public boolean registerIsolatedUid(int isoUid, int ownerUidOrAppId) {
        if (!isIsolatedUid(isoUid) || ownerUidOrAppId < FIRST_APP_UID) {
            return false;
        }
        int ownerAppId = ownerUidOrAppId % PER_USER_RANGE;
        if (ownerAppId < FIRST_APP_UID || isIsolatedAppId(ownerAppId)) {
            return false;
        }
        long packed = packEntry(isoUid, ownerAppId);
        int base = hashSlot(isoUid);

        for (int attempt = 0; attempt < 4; attempt++) {
            int firstTombstone = -1;
            for (int i = 0; i < GRAPH_SIZE; i++) {
                int slot = (base + i) & GRAPH_MASK;
                long cur = identityGraph.get(slot);
                if (cur == EMPTY_SLOT) {
                    int target = firstTombstone >= 0 ? firstTombstone : slot;
                    long expected = firstTombstone >= 0 ? TOMBSTONE_SLOT : EMPTY_SLOT;
                    if (identityGraph.compareAndSet(target, expected, packed)) {
                        graphActiveCount.incrementAndGet();
                        return true;
                    }
                    break;
                }
                if (cur == TOMBSTONE_SLOT) {
                    if (firstTombstone < 0) {
                        firstTombstone = slot;
                    }
                    continue;
                }
                if (unpackIsoUid(cur) == isoUid) {
                    if (cur == packed) {
                        return true;
                    }
                    if (identityGraph.compareAndSet(slot, cur, packed)) {
                        return true;
                    }
                    break;
                }
            }
            if (firstTombstone >= 0) {
                if (identityGraph.compareAndSet(firstTombstone, TOMBSTONE_SLOT, packed)) {
                    graphActiveCount.incrementAndGet();
                    return true;
                }
            }
        }
        long prev = identityGraph.getAndSet(base, packed);
        if (prev == EMPTY_SLOT || prev == TOMBSTONE_SLOT) {
            graphActiveCount.incrementAndGet();
        }
        return true;
    }

    public int lookupOwnerAppId(int isoUid) {
        if (!isIsolatedUid(isoUid)) {
            return -1;
        }
        int base = hashSlot(isoUid);
        for (int i = 0; i < GRAPH_SIZE; i++) {
            int slot = (base + i) & GRAPH_MASK;
            long cur = identityGraph.get(slot);
            if (cur == EMPTY_SLOT) {
                return -1;
            }
            if (cur == TOMBSTONE_SLOT) {
                continue;
            }
            if (unpackIsoUid(cur) == isoUid) {
                return unpackOwnerAppId(cur);
            }
        }
        return -1;
    }

    public boolean evictIsolatedUid(int isoUid) {
        if (!isIsolatedUid(isoUid)) {
            return false;
        }
        int base = hashSlot(isoUid);
        for (int i = 0; i < GRAPH_SIZE; i++) {
            int slot = (base + i) & GRAPH_MASK;
            long cur = identityGraph.get(slot);
            if (cur == EMPTY_SLOT) {
                return false;
            }
            if (cur == TOMBSTONE_SLOT) {
                continue;
            }
            if (unpackIsoUid(cur) == isoUid) {
                if (identityGraph.compareAndSet(slot, cur, TOMBSTONE_SLOT)) {
                    decrementActiveCount();
                    return true;
                }
                i--;
            }
        }
        return false;
    }

    public void clearIdentityGraph() {
        for (int i = 0; i < GRAPH_SIZE; i++) {
            identityGraph.set(i, EMPTY_SLOT);
        }
        graphActiveCount.set(0);
    }

    public int getActiveGraphSize() {
        return Math.max(0, graphActiveCount.get());
    }

    private void decrementActiveCount() {
        while (true) {
            int cur = graphActiveCount.get();
            if (cur <= 0) {
                return;
            }
            if (graphActiveCount.compareAndSet(cur, cur - 1)) {
                return;
            }
        }
    }

    static int hashSlot(int isoUid) {
        int h = isoUid * 0x9E3779B9;
        return (h ^ (h >>> 16)) & GRAPH_MASK;
    }

    private static long packEntry(int isoUid, int ownerAppId) {
        return (((long) isoUid) << 32) | (ownerAppId & 0xFFFFFFFFL);
    }

    private static int unpackIsoUid(long packed) {
        return (int) (packed >>> 32);
    }

    private static int unpackOwnerAppId(long packed) {
        return (int) (packed & 0xFFFFFFFFL);
    }
}
