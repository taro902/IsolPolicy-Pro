package io.github.taro902.isolationpolicy.hook;

import android.os.Bundle;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

import io.github.taro902.isolationpolicy.core.PolicyFlags;
import io.github.taro902.isolationpolicy.core.PolicySnapshot;

public final class TelemetryHub {

    public static final int RING_SIZE = 256;
    private static final int RING_MASK = RING_SIZE - 1;
    public static final int MAX_TRACKED_APPS = 64;

    public static final int HEALTH_OFF = 0;
    public static final int HEALTH_OK = 1;
    public static final int HEALTH_FALLBACK = 2;
    public static final int HEALTH_TRIPPED = 3;

    public static final int HEALTH_BIT_IPC_READY = 1 << 30;
    public static final int HEALTH_BIT_SETTINGS_READY = 1 << 31;

    public static final String KEY_VERSION = "ver";
    public static final String KEY_API_VERSION = "api";
    public static final String KEY_SELF_APP_ID = "self_id";
    public static final String KEY_HEALTH_MASK = "health_mask";
    public static final String KEY_PILLAR_TIERS = "pillar_tiers";
    public static final String KEY_PILLAR_FAULTS = "pillar_faults";
    public static final String KEY_PILLAR_ENFORCED = "pillar_enf";
    public static final String KEY_PILLAR_AUDIT = "pillar_aud";
    public static final String KEY_GRAPH_COUNT = "graph_cnt";
    public static final String KEY_GUARDED_COUNT = "guarded_cnt";
    public static final String KEY_SNAPSHOT_VER = "snap_ver";
    public static final String KEY_RING_TS = "ring_ts";
    public static final String KEY_RING_APP = "ring_app";
    public static final String KEY_RING_META = "ring_meta";
    public static final String KEY_APP_IDS = "app_ids";
    public static final String KEY_APP_MATRIX = "app_matrix";
    public static final String KEY_APP_AUDIT = "app_audit";
    public static final String KEY_HOST_DEV = "host_dev";
    public static final String KEY_HOST_ADB = "host_adb";
    public static final String KEY_HOST_ADB_WIFI = "host_adb_wifi";
    public static final String KEY_HOST_A11Y = "host_a11y";
    public static final String KEY_DEOPT_COUNT = "deopt_cnt";

    private final long[] ringTs = new long[RING_SIZE];
    private final int[] ringApp = new int[RING_SIZE];
    private final int[] ringMeta = new int[RING_SIZE];
    private final AtomicIntegerArray ringSeq = new AtomicIntegerArray(RING_SIZE);
    private final AtomicLong writeCursor = new AtomicLong(0L);

    private final AtomicLongArray pillarEnforced = new AtomicLongArray(PolicyFlags.PILLAR_COUNT);
    private final AtomicLongArray pillarAudit = new AtomicLongArray(PolicyFlags.PILLAR_COUNT);
    private final AtomicIntegerArray pillarHealth = new AtomicIntegerArray(PolicyFlags.PILLAR_COUNT);
    private final AtomicIntegerArray pillarTiers = new AtomicIntegerArray(PolicyFlags.PILLAR_COUNT);
    private final AtomicIntegerArray pillarFaults = new AtomicIntegerArray(PolicyFlags.PILLAR_COUNT);

    private final AtomicIntegerArray appSlots = new AtomicIntegerArray(MAX_TRACKED_APPS);
    private final AtomicLongArray appMatrix = new AtomicLongArray(MAX_TRACKED_APPS * PolicyFlags.PILLAR_COUNT);
    private final AtomicLongArray appAuditCounts = new AtomicLongArray(MAX_TRACKED_APPS);

    private final AtomicInteger engineFlags = new AtomicInteger(0);
    private volatile int frameworkApiVersion = 102;

    public static final class SnapshotData {
        public final int healthMask;
        public final int[] tiers;
        public final int[] faults;
        public final long[] enforcedTotals;
        public final long[] auditTotals;
        public final long[] ringTs;
        public final int[] ringApp;
        public final int[] ringMeta;
        public final int[] appIds;
        public final long[] appMatrix;
        public final long[] appAudit;

        SnapshotData(
            int healthMask,
            int[] tiers,
            int[] faults,
            long[] enforcedTotals,
            long[] auditTotals,
            long[] ringTs,
            int[] ringApp,
            int[] ringMeta,
            int[] appIds,
            long[] appMatrix,
            long[] appAudit
        ) {
            this.healthMask = healthMask;
            this.tiers = tiers;
            this.faults = faults;
            this.enforcedTotals = enforcedTotals;
            this.auditTotals = auditTotals;
            this.ringTs = ringTs;
            this.ringApp = ringApp;
            this.ringMeta = ringMeta;
            this.appIds = appIds;
            this.appMatrix = appMatrix;
            this.appAudit = appAudit;
        }
    }

    public static int packMeta(int pillar, int vectorCode, int actionCode, boolean isAudit, int aux) {
        int p = pillar & 0x0F;
        int v = (vectorCode & 0xFF) << 4;
        int a = (actionCode & 0x0F) << 12;
        int aud = isAudit ? (1 << 16) : 0;
        int x = (aux & 0x7FFF) << 17;
        return p | v | a | aud | x;
    }

    public static int unpackPillar(int meta) {
        return meta & 0x0F;
    }

    public static int unpackVector(int meta) {
        return (meta >>> 4) & 0xFF;
    }

    public static int unpackAction(int meta) {
        return (meta >>> 12) & 0x0F;
    }

    public static boolean unpackIsAudit(int meta) {
        return (meta & (1 << 16)) != 0;
    }

    public static int unpackAux(int meta) {
        return (meta >>> 17) & 0x7FFF;
    }

    public void setFrameworkApiVersion(int apiVersion) {
        if (apiVersion > 0) {
            this.frameworkApiVersion = apiVersion;
        }
    }

    public int getFrameworkApiVersion() {
        return frameworkApiVersion;
    }

    public void setPillarStatus(int pillar, int healthState, int tier) {
        if (pillar < 0 || pillar >= PolicyFlags.PILLAR_COUNT) {
            return;
        }
        pillarHealth.set(pillar, healthState & 0x03);
        if (tier > 0) {
            int cur = pillarTiers.get(pillar);
            if (cur == 0 || tier > cur) {
                pillarTiers.set(pillar, tier);
            }
        }
    }

    public void markIpcReady() {
        updateEngineBit(HEALTH_BIT_IPC_READY);
    }

    public void markSettingsReady() {
        updateEngineBit(HEALTH_BIT_SETTINGS_READY);
    }

    private void updateEngineBit(int bit) {
        while (true) {
            int cur = engineFlags.get();
            int next = cur | bit;
            if (cur == next || engineFlags.compareAndSet(cur, next)) {
                return;
            }
        }
    }

    public void recordFault(int pillar, boolean tripped) {
        if (pillar < 0 || pillar >= PolicyFlags.PILLAR_COUNT) {
            return;
        }
        pillarFaults.incrementAndGet(pillar);
        if (tripped) {
            pillarHealth.set(pillar, HEALTH_TRIPPED);
        }
    }

    public HookGuard createGuard(int pillar, String label) {
        return new HookGuard(pillar, label, new HookGuard.FaultListener() {
            @Override
            public void onFault(int p, int consecutiveFaults, boolean tripped) {
                recordFault(p, tripped);
            }
        });
    }

    public int computeHealthMask() {
        int mask = engineFlags.get() & (HEALTH_BIT_IPC_READY | HEALTH_BIT_SETTINGS_READY);
        for (int p = 0; p < PolicyFlags.PILLAR_COUNT; p++) {
            int state = pillarHealth.get(p) & 0x03;
            mask |= (state << (p * 2));
            if (pillarFaults.get(p) > 0) {
                mask |= (1 << (20 + p));
            }
        }
        return mask;
    }

    public static int decodePillarHealth(int healthMask, int pillar) {
        if (pillar < 0 || pillar >= PolicyFlags.PILLAR_COUNT) {
            return HEALTH_OFF;
        }
        return (healthMask >>> (pillar * 2)) & 0x03;
    }

    public static boolean decodePillarHasFault(int healthMask, int pillar) {
        if (pillar < 0 || pillar >= PolicyFlags.PILLAR_COUNT) {
            return false;
        }
        return (healthMask & (1 << (20 + pillar))) != 0;
    }

    public static int countHealthyPillars(int healthMask) {
        int count = 0;
        for (int p = 0; p < PolicyFlags.PILLAR_COUNT; p++) {
            int st = decodePillarHealth(healthMask, p);
            if (st == HEALTH_OK || st == HEALTH_FALLBACK) {
                count++;
            }
        }
        return count;
    }

    public void record(int appId, int pillar, int vectorCode, int actionCode, boolean isAudit, int aux) {
        recordAt(System.currentTimeMillis(), appId, pillar, vectorCode, actionCode, isAudit, aux);
    }

    public void recordAt(long timestampMs, int appId, int pillar, int vectorCode, int actionCode, boolean isAudit, int aux) {
        if (pillar < 0 || pillar >= PolicyFlags.PILLAR_COUNT) {
            return;
        }
        if (isAudit) {
            pillarAudit.incrementAndGet(pillar);
        } else {
            pillarEnforced.incrementAndGet(pillar);
        }

        if (appId >= PolicySnapshot.FIRST_APP_UID) {
            int appIdx = findOrAllocAppSlot(appId);
            if (appIdx >= 0) {
                appMatrix.incrementAndGet(appIdx * PolicyFlags.PILLAR_COUNT + pillar);
                if (isAudit) {
                    appAuditCounts.incrementAndGet(appIdx);
                }
            }
        }

        long seqNum = writeCursor.getAndIncrement();
        int slot = (int) (seqNum & RING_MASK);
        ringSeq.incrementAndGet(slot);
        ringTs[slot] = timestampMs;
        ringApp[slot] = appId;
        ringMeta[slot] = packMeta(pillar, vectorCode, actionCode, isAudit, aux);
        ringSeq.incrementAndGet(slot);
    }

    private int findOrAllocAppSlot(int appId) {
        for (int i = 0; i < MAX_TRACKED_APPS; i++) {
            int existing = appSlots.get(i);
            if (existing == appId) {
                return i;
            }
            if (existing == 0) {
                if (appSlots.compareAndSet(i, 0, appId)) {
                    return i;
                }
                if (appSlots.get(i) == appId) {
                    return i;
                }
            }
        }
        return -1;
    }

    public long getAppPillarHits(int appId, int pillar) {
        if (appId < PolicySnapshot.FIRST_APP_UID || pillar < 0 || pillar >= PolicyFlags.PILLAR_COUNT) {
            return 0L;
        }
        for (int i = 0; i < MAX_TRACKED_APPS; i++) {
            int existing = appSlots.get(i);
            if (existing == appId) {
                return appMatrix.get(i * PolicyFlags.PILLAR_COUNT + pillar);
            }
            if (existing == 0) {
                return 0L;
            }
        }
        return 0L;
    }

    public long getAppAuditHits(int appId) {
        if (appId < PolicySnapshot.FIRST_APP_UID) {
            return 0L;
        }
        for (int i = 0; i < MAX_TRACKED_APPS; i++) {
            int existing = appSlots.get(i);
            if (existing == appId) {
                return appAuditCounts.get(i);
            }
            if (existing == 0) {
                return 0L;
            }
        }
        return 0L;
    }

    public void purgeCounters() {
        for (int p = 0; p < PolicyFlags.PILLAR_COUNT; p++) {
            pillarEnforced.set(p, 0L);
            pillarAudit.set(p, 0L);
        }
        for (int i = 0; i < MAX_TRACKED_APPS; i++) {
            appSlots.set(i, 0);
            appAuditCounts.set(i, 0L);
        }
        for (int i = 0; i < MAX_TRACKED_APPS * PolicyFlags.PILLAR_COUNT; i++) {
            appMatrix.set(i, 0L);
        }
        writeCursor.set(0L);
        for (int i = 0; i < RING_SIZE; i++) {
            ringSeq.set(i, 0);
            ringTs[i] = 0L;
            ringApp[i] = 0;
            ringMeta[i] = 0;
        }
    }

    public long getPillarEnforced(int pillar) {
        if (pillar < 0 || pillar >= PolicyFlags.PILLAR_COUNT) {
            return 0L;
        }
        return pillarEnforced.get(pillar);
    }

    public long getPillarAudit(int pillar) {
        if (pillar < 0 || pillar >= PolicyFlags.PILLAR_COUNT) {
            return 0L;
        }
        return pillarAudit.get(pillar);
    }

    public SnapshotData captureSnapshot() {
        int mask = computeHealthMask();
        int[] tiers = new int[PolicyFlags.PILLAR_COUNT];
        int[] faults = new int[PolicyFlags.PILLAR_COUNT];
        long[] enf = new long[PolicyFlags.PILLAR_COUNT];
        long[] aud = new long[PolicyFlags.PILLAR_COUNT];
        for (int p = 0; p < PolicyFlags.PILLAR_COUNT; p++) {
            tiers[p] = pillarTiers.get(p);
            faults[p] = pillarFaults.get(p);
            enf[p] = pillarEnforced.get(p);
            aud[p] = pillarAudit.get(p);
        }

        long cursor = writeCursor.get();
        int count = (int) Math.min(cursor, (long) RING_SIZE);
        long[] outTs = new long[count];
        int[] outApp = new int[count];
        int[] outMeta = new int[count];
        long startSeq = cursor - count;
        int written = 0;
        for (int i = 0; i < count; i++) {
            int slot = (int) ((startSeq + i) & RING_MASK);
            for (int retry = 0; retry < 4; retry++) {
                int s1 = ringSeq.get(slot);
                if ((s1 & 1) != 0) {
                    continue;
                }
                long ts = ringTs[slot];
                int app = ringApp[slot];
                int meta = ringMeta[slot];
                int s2 = ringSeq.get(slot);
                if (s1 == s2 && ts > 0L) {
                    outTs[written] = ts;
                    outApp[written] = app;
                    outMeta[written] = meta;
                    written++;
                    break;
                }
            }
        }
        if (written < count) {
            long[] trimTs = new long[written];
            int[] trimApp = new int[written];
            int[] trimMeta = new int[written];
            System.arraycopy(outTs, 0, trimTs, 0, written);
            System.arraycopy(outApp, 0, trimApp, 0, written);
            System.arraycopy(outMeta, 0, trimMeta, 0, written);
            outTs = trimTs;
            outApp = trimApp;
            outMeta = trimMeta;
        }

        int activeApps = 0;
        for (int i = 0; i < MAX_TRACKED_APPS; i++) {
            if (appSlots.get(i) >= PolicySnapshot.FIRST_APP_UID) {
                activeApps++;
            }
        }
        int[] ids = new int[activeApps];
        long[] matrix = new long[activeApps * PolicyFlags.PILLAR_COUNT];
        long[] appAud = new long[activeApps];
        int pos = 0;
        for (int i = 0; i < MAX_TRACKED_APPS && pos < activeApps; i++) {
            int id = appSlots.get(i);
            if (id >= PolicySnapshot.FIRST_APP_UID) {
                ids[pos] = id;
                appAud[pos] = appAuditCounts.get(i);
                int srcOff = i * PolicyFlags.PILLAR_COUNT;
                int dstOff = pos * PolicyFlags.PILLAR_COUNT;
                for (int p = 0; p < PolicyFlags.PILLAR_COUNT; p++) {
                    matrix[dstOff + p] = appMatrix.get(srcOff + p);
                }
                pos++;
            }
        }

        return new SnapshotData(mask, tiers, faults, enf, aud, outTs, outApp, outMeta, ids, matrix, appAud);
    }

    public Bundle exportBundle(
        PolicyEngine engine,
        String moduleVersion,
        int hostDev,
        int hostAdb,
        int hostAdbWifi,
        int hostA11y
    ) {
        SnapshotData snap = captureSnapshot();
        PolicySnapshot policySnap = engine != null ? engine.getSnapshot() : PolicySnapshot.EMPTY;
        Bundle b = new Bundle();
        b.putString(KEY_VERSION, moduleVersion != null ? moduleVersion : "2.2.0");
        b.putInt(KEY_API_VERSION, frameworkApiVersion);
        b.putInt(KEY_SELF_APP_ID, engine != null ? engine.getVerifiedSelfAppId() : -1);
        b.putInt(KEY_HEALTH_MASK, snap.healthMask);
        b.putIntArray(KEY_PILLAR_TIERS, snap.tiers);
        b.putIntArray(KEY_PILLAR_FAULTS, snap.faults);
        b.putLongArray(KEY_PILLAR_ENFORCED, snap.enforcedTotals);
        b.putLongArray(KEY_PILLAR_AUDIT, snap.auditTotals);
        b.putInt(KEY_GRAPH_COUNT, engine != null ? engine.getActiveGraphSize() : 0);
        b.putInt(KEY_GUARDED_COUNT, policySnap.guardedCount);
        b.putLong(KEY_SNAPSHOT_VER, policySnap.version);
        b.putLongArray(KEY_RING_TS, snap.ringTs);
        b.putIntArray(KEY_RING_APP, snap.ringApp);
        b.putIntArray(KEY_RING_META, snap.ringMeta);
        b.putIntArray(KEY_APP_IDS, snap.appIds);
        b.putLongArray(KEY_APP_MATRIX, snap.appMatrix);
        b.putLongArray(KEY_APP_AUDIT, snap.appAudit);
        b.putInt(KEY_HOST_DEV, hostDev);
        b.putInt(KEY_HOST_ADB, hostAdb);
        b.putInt(KEY_HOST_ADB_WIFI, hostAdbWifi);
        b.putInt(KEY_HOST_A11Y, hostA11y);
        b.putInt(KEY_DEOPT_COUNT, CompatMatrix.getDeoptimizedCount());
        return b;
    }
}
