package io.github.taro902.isolationpolicy.core;

import android.util.SparseArray;
import android.util.SparseIntArray;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class PolicySnapshot {

    public static final PolicySnapshot EMPTY = new PolicySnapshot(
            new SparseIntArray(0),
            Collections.emptyMap(),
            new SparseArray<>(0),
            -1,
            PolicyFlags.AZ_OFF,
            0L
    );

    public final SparseIntArray appIdFlags;
    public final Map<String, Integer> pkgFlags;
    public final SparseArray<String> appIdToPkg;
    public final int selfAppId;
    public final int defaultAzMode;
    public final long revision;

    public PolicySnapshot(
            SparseIntArray appIdFlags,
            Map<String, Integer> pkgFlags,
            SparseArray<String> appIdToPkg,
            int selfAppId,
            int defaultAzMode,
            long revision
    ) {
        this.appIdFlags = appIdFlags != null ? appIdFlags : new SparseIntArray(0);
        this.pkgFlags = pkgFlags != null
                ? Collections.unmodifiableMap(new HashMap<>(pkgFlags))
                : Collections.emptyMap();
        this.appIdToPkg = appIdToPkg != null ? appIdToPkg : new SparseArray<>(0);
        this.selfAppId = selfAppId;
        this.defaultAzMode = defaultAzMode & PolicyFlags.AZ_MASK;
        this.revision = revision;
    }

    public int flagsForAppId(int appId) {
        if (appId < 10000) {
            return 0;
        }
        return appIdFlags.get(appId, 0);
    }

    public int flagsForPackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return 0;
        }
        Integer boxed = pkgFlags.get(packageName);
        return boxed != null ? boxed : 0;
    }

    public String packageForAppId(int appId) {
        return appIdToPkg.get(appId);
    }

    public boolean hasAnyPolicy() {
        return appIdFlags.size() > 0 || !pkgFlags.isEmpty() || defaultAzMode != PolicyFlags.AZ_OFF;
    }

    public int guardedCount() {
        return pkgFlags.size();
    }
}