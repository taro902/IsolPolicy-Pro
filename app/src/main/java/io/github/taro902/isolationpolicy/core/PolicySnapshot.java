package io.github.taro902.isolationpolicy.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class PolicySnapshot {

    public static final int FIRST_APP_UID = 10000;
    private static final int EMPTY_KEY = Integer.MIN_VALUE;
    private static final int FIB_MULTIPLIER = 0x9E3779B9;

    public static final PolicySnapshot EMPTY = new PolicySnapshot(
        0L,
        PolicyFlags.AZ_OFF,
        -1,
        FlatIntIntMap.EMPTY,
        Collections.<String, Integer>emptyMap(),
        new int[16],
        new String[16],
        15,
        28,
        0
    );

    public final long version;
    public final int defaultAzMode;
    public final int selfAppId;
    private final FlatIntIntMap appIdFlags;
    private final Map<String, Integer> pkgFlags;
    private final int[] pkgLookupKeys;
    private final String[] pkgLookupNames;
    private final int pkgMask;
    private final int pkgShift;
    public final int guardedCount;

    private PolicySnapshot(
        long version,
        int defaultAzMode,
        int selfAppId,
        FlatIntIntMap appIdFlags,
        Map<String, Integer> pkgFlags,
        int[] pkgLookupKeys,
        String[] pkgLookupNames,
        int pkgMask,
        int pkgShift,
        int guardedCount
    ) {
        this.version = version;
        this.defaultAzMode = defaultAzMode & PolicyFlags.AZ_MASK;
        this.selfAppId = selfAppId;
        this.appIdFlags = appIdFlags;
        this.pkgFlags = pkgFlags;
        this.pkgLookupKeys = pkgLookupKeys;
        this.pkgLookupNames = pkgLookupNames;
        this.pkgMask = pkgMask;
        this.pkgShift = pkgShift;
        this.guardedCount = guardedCount;
    }

    public static PolicySnapshot create(
        long version,
        int defaultAzMode,
        int selfAppId,
        Map<String, Integer> packagePolicies,
        Map<Integer, Integer> appIdPolicies,
        Map<Integer, String> appIdToPackage
    ) {
        int safeDefault = defaultAzMode & PolicyFlags.AZ_MASK;
        Map<String, Integer> cleanPkg = new HashMap<String, Integer>();
        int activeCount = 0;
        if (packagePolicies != null) {
            for (Map.Entry<String, Integer> e : packagePolicies.entrySet()) {
                String pkg = e.getKey();
                Integer val = e.getValue();
                if (pkg != null && !pkg.isEmpty() && val != null) {
                    int clean = PolicyFlags.sanitize(val);
                    if (PolicyFlags.isActive(clean)) {
                        cleanPkg.put(pkg, clean);
                        activeCount++;
                    }
                }
            }
        }

        int expectedIds = appIdPolicies != null ? appIdPolicies.size() : 0;
        FlatIntIntMap.Builder idBuilder = FlatIntIntMap.newBuilder(expectedIds);
        if (appIdPolicies != null) {
            for (Map.Entry<Integer, Integer> e : appIdPolicies.entrySet()) {
                Integer idObj = e.getKey();
                Integer flagObj = e.getValue();
                if (idObj != null && flagObj != null) {
                    int appId = idObj;
                    int clean = PolicyFlags.sanitize(flagObj);
                    if (appId >= FIRST_APP_UID && PolicyFlags.isActive(clean)) {
                        idBuilder.put(appId, clean);
                    }
                }
            }
        }

        int pkgTableCount = appIdToPackage != null ? appIdToPackage.size() : 0;
        int cap = 16;
        int minCap = Math.max(16, pkgTableCount * 2 + 1);
        while (cap < minCap && cap < (1 << 20)) {
            cap <<= 1;
        }
        int[] pKeys = new int[cap];
        String[] pNames = new String[cap];
        Arrays.fill(pKeys, EMPTY_KEY);
        int pMask = cap - 1;
        int pShift = 32 - Integer.numberOfTrailingZeros(cap);
        if (appIdToPackage != null) {
            for (Map.Entry<Integer, String> e : appIdToPackage.entrySet()) {
                Integer idObj = e.getKey();
                String pkg = e.getValue();
                if (idObj != null && pkg != null && !pkg.isEmpty()) {
                    int appId = idObj;
                    if (appId >= FIRST_APP_UID) {
                        int idx = ((appId * FIB_MULTIPLIER) >>> pShift) & pMask;
                        while (pKeys[idx] != EMPTY_KEY && pKeys[idx] != appId) {
                            idx = (idx + 1) & pMask;
                        }
                        pKeys[idx] = appId;
                        pNames[idx] = pkg;
                    }
                }
            }
        }

        return new PolicySnapshot(
            version,
            safeDefault,
            selfAppId,
            idBuilder.build(),
            Collections.unmodifiableMap(cleanPkg),
            pKeys,
            pNames,
            pMask,
            pShift,
            activeCount
        );
    }

    public int flagsForAppId(int appId) {
        if (appId < FIRST_APP_UID) {
            return 0;
        }
        int flags = appIdFlags.get(appId, -1);
        if (flags >= 0) {
            return flags;
        }
        return defaultAzMode;
    }

    public int flagsForPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return defaultAzMode;
        }
        Integer flags = pkgFlags.get(packageName);
        if (flags != null) {
            return flags;
        }
        return defaultAzMode;
    }

    public String packageForAppId(int appId) {
        if (appId < FIRST_APP_UID) {
            return null;
        }
        int idx = ((appId * FIB_MULTIPLIER) >>> pkgShift) & pkgMask;
        while (true) {
            int k = pkgLookupKeys[idx];
            if (k == appId) {
                return pkgLookupNames[idx];
            }
            if (k == EMPTY_KEY) {
                return null;
            }
            idx = (idx + 1) & pkgMask;
        }
    }

    public boolean hasAnyGuarded() {
        return guardedCount > 0 || appIdFlags.size() > 0 || defaultAzMode != PolicyFlags.AZ_OFF;
    }

    public int mappedAppIdCount() {
        return appIdFlags.size();
    }
}
