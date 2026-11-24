package io.github.taro902.isolationpolicy.radar;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.taro902.isolationpolicy.core.PolicyFlags;
import io.github.taro902.isolationpolicy.hook.TelemetryHub;

public final class TelemetryClient {

    private TelemetryClient() {
    }

    public static final class EventRow {
        public final long timestampMs;
        public final int appId;
        public final int pillar;
        public final int vector;
        public final int action;
        public final boolean isAudit;
        public final int aux;

        public EventRow(long timestampMs, int appId, int meta) {
            this.timestampMs = timestampMs;
            this.appId = appId;
            this.pillar = TelemetryHub.unpackPillar(meta);
            this.vector = TelemetryHub.unpackVector(meta);
            this.action = TelemetryHub.unpackAction(meta);
            this.isAudit = TelemetryHub.unpackIsAudit(meta);
            this.aux = TelemetryHub.unpackAux(meta);
        }
    }

    public static final class TelemetrySnapshot {
        public final boolean connected;
        public final String moduleVersion;
        public final int apiVersion;
        public final int selfAppId;
        public final int healthMask;
        public final int[] pillarTiers;
        public final int[] pillarFaults;
        public final long[] pillarEnforced;
        public final long[] pillarAudit;
        public final int graphCount;
        public final int guardedCount;
        public final long snapshotVersion;
        public final int hostDev;
        public final int hostAdb;
        public final int hostAdbWifi;
        public final int hostA11y;
        public final List<EventRow> events;
        public final Map<Integer, long[]> appPillarMatrix;
        public final Map<Integer, Long> appAuditTotals;

        public TelemetrySnapshot(
            boolean connected,
            String moduleVersion,
            int apiVersion,
            int selfAppId,
            int healthMask,
            int[] pillarTiers,
            int[] pillarFaults,
            long[] pillarEnforced,
            long[] pillarAudit,
            int graphCount,
            int guardedCount,
            long snapshotVersion,
            int hostDev,
            int hostAdb,
            int hostAdbWifi,
            int hostA11y,
            List<EventRow> events,
            Map<Integer, long[]> appPillarMatrix,
            Map<Integer, Long> appAuditTotals
        ) {
            this.connected = connected;
            this.moduleVersion = moduleVersion != null ? moduleVersion : "";
            this.apiVersion = apiVersion;
            this.selfAppId = selfAppId;
            this.healthMask = healthMask;
            this.pillarTiers = ensureIntArray(pillarTiers, PolicyFlags.PILLAR_COUNT);
            this.pillarFaults = ensureIntArray(pillarFaults, PolicyFlags.PILLAR_COUNT);
            this.pillarEnforced = ensureLongArray(pillarEnforced, PolicyFlags.PILLAR_COUNT);
            this.pillarAudit = ensureLongArray(pillarAudit, PolicyFlags.PILLAR_COUNT);
            this.graphCount = graphCount;
            this.guardedCount = guardedCount;
            this.snapshotVersion = snapshotVersion;
            this.hostDev = hostDev;
            this.hostAdb = hostAdb;
            this.hostAdbWifi = hostAdbWifi;
            this.hostA11y = hostA11y;
            this.events = events != null ? Collections.unmodifiableList(events) : Collections.<EventRow>emptyList();
            this.appPillarMatrix = appPillarMatrix != null ? Collections.unmodifiableMap(appPillarMatrix) : Collections.<Integer, long[]>emptyMap();
            this.appAuditTotals = appAuditTotals != null ? Collections.unmodifiableMap(appAuditTotals) : Collections.<Integer, Long>emptyMap();
        }

        public static TelemetrySnapshot offline() {
            return new TelemetrySnapshot(
                false,
                "",
                0,
                -1,
                0,
                null,
                null,
                null,
                null,
                0,
                0,
                0L,
                -1,
                -1,
                -1,
                -1,
                Collections.<EventRow>emptyList(),
                Collections.<Integer, long[]>emptyMap(),
                Collections.<Integer, Long>emptyMap()
            );
        }

        public int healthyPillarCount() {
            return TelemetryHub.countHealthyPillars(healthMask);
        }

        public long totalEnforced() {
            long sum = 0L;
            for (int i = 0; i < pillarEnforced.length; i++) {
                sum += pillarEnforced[i];
            }
            return sum;
        }

        public long totalAudit() {
            long sum = 0L;
            for (int i = 0; i < pillarAudit.length; i++) {
                sum += pillarAudit[i];
            }
            return sum;
        }

        public long[] getAppPillars(int appId) {
            long[] row = appPillarMatrix.get(Integer.valueOf(appId));
            return row != null ? row : new long[PolicyFlags.PILLAR_COUNT];
        }

        public long getAppTotalHits(int appId) {
            long[] row = appPillarMatrix.get(Integer.valueOf(appId));
            if (row == null) {
                return 0L;
            }
            long sum = 0L;
            for (int i = 0; i < row.length; i++) {
                sum += row[i];
            }
            return sum;
        }

        public long getAppAuditHits(int appId) {
            Long v = appAuditTotals.get(Integer.valueOf(appId));
            return v != null ? v.longValue() : 0L;
        }

        public String formatTraceReport(Map<Integer, String> appNames) {
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
            StringBuilder sb = new StringBuilder(1024);
            sb.append("IsolPolicy ").append(moduleVersion.isEmpty() ? "2.2.0" : moduleVersion);
            sb.append(" API=").append(apiVersion);
            sb.append(" HOOKS=").append(healthyPillarCount()).append("/").append(PolicyFlags.PILLAR_COUNT);
            sb.append(" ENF=").append(totalEnforced());
            sb.append(" AUD=").append(totalAudit());
            sb.append(" GRAPH=").append(graphCount).append('\n');
            for (int i = events.size() - 1; i >= 0; i--) {
                EventRow ev = events.get(i);
                String app = appNames != null ? appNames.get(Integer.valueOf(ev.appId)) : null;
                if (app == null || app.isEmpty()) {
                    app = "uid_" + ev.appId;
                }
                sb.append(fmt.format(new Date(ev.timestampMs)))
                    .append(' ')
                    .append(ev.isAudit ? "AUD" : "ENF")
                    .append(' ')
                    .append(PolicyFlags.pillarCode(ev.pillar))
                    .append(' ')
                    .append(PolicyFlags.vectorName(ev.vector))
                    .append(' ')
                    .append(PolicyFlags.actionName(ev.action))
                    .append(' ')
                    .append(app);
                if (ev.aux > 0) {
                    sb.append(" x").append(ev.aux);
                }
                sb.append('\n');
            }
            return sb.toString();
        }
    }

    public static TelemetrySnapshot fetchSnapshot(Context context) {
        if (context == null) {
            return TelemetrySnapshot.offline();
        }
        Bundle res = invokeIpc(context, PolicyFlags.IPC_CMD_SNAP);
        if (res == null || !res.containsKey(TelemetryHub.KEY_HEALTH_MASK)) {
            return TelemetrySnapshot.offline();
        }
        return parseBundle(res);
    }

    public static boolean purge(Context context) {
        if (context == null) {
            return false;
        }
        Bundle res = invokeIpc(context, PolicyFlags.IPC_CMD_PURGE);
        return res != null && res.getBoolean("ok", false);
    }

    public static boolean ping(Context context) {
        if (context == null) {
            return false;
        }
        Bundle res = invokeIpc(context, PolicyFlags.IPC_CMD_PING);
        return res != null && res.containsKey(TelemetryHub.KEY_HEALTH_MASK);
    }

    private static Bundle invokeIpc(Context context, String cmd) {
        ContentResolver cr = context.getContentResolver();
        if (cr == null) {
            return null;
        }
        Bundle extras = new Bundle();
        extras.putString(PolicyFlags.IPC_ARG_CMD, cmd);
        try {
            Bundle res = cr.call(Settings.Global.CONTENT_URI, PolicyFlags.IPC_METHOD, cmd, extras);
            if (res != null && (res.containsKey(TelemetryHub.KEY_HEALTH_MASK) || res.getBoolean("ok", false))) {
                return res;
            }
        } catch (Throwable ignored) {
        }
        try {
            return cr.call(Settings.Global.CONTENT_URI, PolicyFlags.IPC_METHOD_LEGACY_V21, cmd, extras);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static TelemetrySnapshot parseBundle(Bundle res) {
        if (res == null) {
            return TelemetrySnapshot.offline();
        }
        String ver = res.getString(TelemetryHub.KEY_VERSION, "");
        int api = res.getInt(TelemetryHub.KEY_API_VERSION, 0);
        int selfId = res.getInt(TelemetryHub.KEY_SELF_APP_ID, -1);
        int health = res.getInt(TelemetryHub.KEY_HEALTH_MASK, 0);
        int[] tiers = res.getIntArray(TelemetryHub.KEY_PILLAR_TIERS);
        int[] faults = res.getIntArray(TelemetryHub.KEY_PILLAR_FAULTS);
        long[] enf = res.getLongArray(TelemetryHub.KEY_PILLAR_ENFORCED);
        long[] aud = res.getLongArray(TelemetryHub.KEY_PILLAR_AUDIT);
        int graphCnt = res.getInt(TelemetryHub.KEY_GRAPH_COUNT, 0);
        int guardedCnt = res.getInt(TelemetryHub.KEY_GUARDED_COUNT, 0);
        long snapVer = res.getLong(TelemetryHub.KEY_SNAPSHOT_VER, 0L);
        int hostDev = res.getInt(TelemetryHub.KEY_HOST_DEV, -1);
        int hostAdb = res.getInt(TelemetryHub.KEY_HOST_ADB, -1);
        int hostWifi = res.getInt(TelemetryHub.KEY_HOST_ADB_WIFI, -1);
        int hostA11y = res.getInt(TelemetryHub.KEY_HOST_A11Y, -1);

        long[] rTs = res.getLongArray(TelemetryHub.KEY_RING_TS);
        int[] rApp = res.getIntArray(TelemetryHub.KEY_RING_APP);
        int[] rMeta = res.getIntArray(TelemetryHub.KEY_RING_META);
        ArrayList<EventRow> rows = new ArrayList<EventRow>();
        if (rTs != null && rApp != null && rMeta != null) {
            int n = Math.min(rTs.length, Math.min(rApp.length, rMeta.length));
            for (int i = 0; i < n; i++) {
                if (rTs[i] > 0L) {
                    rows.add(new EventRow(rTs[i], rApp[i], rMeta[i]));
                }
            }
        }

        int[] appIds = res.getIntArray(TelemetryHub.KEY_APP_IDS);
        long[] flatMatrix = res.getLongArray(TelemetryHub.KEY_APP_MATRIX);
        long[] appAudArr = res.getLongArray(TelemetryHub.KEY_APP_AUDIT);
        HashMap<Integer, long[]> matrixMap = new HashMap<Integer, long[]>();
        HashMap<Integer, Long> auditMap = new HashMap<Integer, Long>();
        if (appIds != null && flatMatrix != null) {
            for (int i = 0; i < appIds.length; i++) {
                int off = i * PolicyFlags.PILLAR_COUNT;
                if (off + PolicyFlags.PILLAR_COUNT <= flatMatrix.length) {
                    long[] slice = new long[PolicyFlags.PILLAR_COUNT];
                    System.arraycopy(flatMatrix, off, slice, 0, PolicyFlags.PILLAR_COUNT);
                    matrixMap.put(Integer.valueOf(appIds[i]), slice);
                }
                if (appAudArr != null && i < appAudArr.length) {
                    auditMap.put(Integer.valueOf(appIds[i]), Long.valueOf(appAudArr[i]));
                }
            }
        }

        return new TelemetrySnapshot(
            true,
            ver,
            api,
            selfId,
            health,
            tiers,
            faults,
            enf,
            aud,
            graphCnt,
            guardedCnt,
            snapVer,
            hostDev,
            hostAdb,
            hostWifi,
            hostA11y,
            rows,
            matrixMap,
            auditMap
        );
    }

    private static int[] ensureIntArray(int[] src, int len) {
        int[] out = new int[len];
        if (src != null) {
            System.arraycopy(src, 0, out, 0, Math.min(src.length, len));
        }
        return out;
    }

    private static long[] ensureLongArray(long[] src, int len) {
        long[] out = new long[len];
        if (src != null) {
            System.arraycopy(src, 0, out, 0, Math.min(src.length, len));
        }
        return out;
    }
}
