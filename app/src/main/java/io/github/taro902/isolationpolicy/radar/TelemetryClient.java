package io.github.taro902.isolationpolicy.radar;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class TelemetryClient {

    private TelemetryClient() {
    }

    public static SnapshotData fetchSnapshot(Context context) {
        try {
            Bundle extras = new Bundle(1);
            extras.putString(PolicyFlags.IPC_ARG_CMD, PolicyFlags.IPC_CMD_SNAPSHOT);
            Bundle res = context.getContentResolver().call(
                    Settings.Global.CONTENT_URI,
                    PolicyFlags.IPC_METHOD,
                    PolicyFlags.IPC_CMD_SNAPSHOT,
                    extras
            );
            if (res == null || !res.containsKey("health")) {
                return SnapshotData.DISCONNECTED;
            }
            long total = res.getLong("total", 0L);
            long[] counts = res.getLongArray("counts");
            if (counts == null || counts.length < 8) {
                counts = new long[8];
            }
            int health = res.getInt("health", 0);
            long uptime = res.getLong("uptime", 0L);
            int guarded = res.getInt("guarded", 0);
            int graph = res.getInt("graph", 0);
            int api = res.getInt("api", 102);
            String fw = res.getString("fw", "LSPosed");
            String ver = res.getString("ver", PolicyFlags.VERSION_NAME);

            long[] ts = res.getLongArray("ev_ts");
            int[] uids = res.getIntArray("ev_uid");
            String[] pkgs = res.getStringArray("ev_pkg");
            int[] pils = res.getIntArray("ev_pil");
            int[] vecs = res.getIntArray("ev_vec");
            int[] acts = res.getIntArray("ev_act");

            List<EventRecord> events = new ArrayList<>();
            if (ts != null && uids != null && pkgs != null && pils != null && vecs != null && acts != null) {
                int n = Math.min(ts.length, Math.min(uids.length, Math.min(pkgs.length, Math.min(pils.length, Math.min(vecs.length, acts.length)))));
                for (int i = 0; i < n; i++) {
                    events.add(new EventRecord(ts[i], uids[i], pkgs[i], pils[i], vecs[i], acts[i]));
                }
            }
            return new SnapshotData(true, total, counts, health, uptime, guarded, graph, api, fw, ver, events);
        } catch (Throwable ignored) {
            return SnapshotData.DISCONNECTED;
        }
    }

    public static boolean purge(Context context) {
        try {
            Bundle extras = new Bundle(1);
            extras.putString(PolicyFlags.IPC_ARG_CMD, PolicyFlags.IPC_CMD_PURGE);
            Bundle res = context.getContentResolver().call(
                    Settings.Global.CONTENT_URI,
                    PolicyFlags.IPC_METHOD,
                    PolicyFlags.IPC_CMD_PURGE,
                    extras
            );
            return res != null && res.getBoolean("ok", false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static DiagnosticResult runSelfDiagnostic(Context context, boolean serviceBound, int serviceApi, String serviceFw) {
        boolean ipcAlive = false;
        int healthMask = 0;
        String remoteVer = "-";
        try {
            Bundle extras = new Bundle(1);
            extras.putString(PolicyFlags.IPC_ARG_CMD, PolicyFlags.IPC_CMD_PING);
            Bundle res = context.getContentResolver().call(
                    Settings.Global.CONTENT_URI,
                    PolicyFlags.IPC_METHOD,
                    PolicyFlags.IPC_CMD_PING,
                    extras
            );
            if (res != null && res.getBoolean("ok", false)) {
                ipcAlive = true;
                healthMask = res.getInt("health", 0);
                remoteVer = res.getString("ver", PolicyFlags.VERSION_NAME);
            }
        } catch (Throwable ignored) {
        }

        boolean devOn = Settings.Global.getInt(context.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
        boolean adbOn = Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0) != 0;
        boolean a11yOn = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 0;

        return new DiagnosticResult(serviceBound, serviceApi, serviceFw, ipcAlive, healthMask, remoteVer, devOn, adbOn, a11yOn);
    }

    public static int decodeHookState(int healthMask, int hookBit) {
        return (healthMask >>> (hookBit * 2)) & 0x03;
    }

    public static int countHealthyHooks(int healthMask) {
        int ok = 0;
        for (int i = 0; i < 7; i++) {
            int st = decodeHookState(healthMask, i);
            if (st == 1 || st == 2) {
                ok++;
            }
        }
        return ok;
    }

    public static final class EventRecord {
        public final long timestampMs;
        public final int appId;
        public final String packageName;
        public final int pillar;
        public final int vector;
        public final int action;

        public EventRecord(long timestampMs, int appId, String packageName, int pillar, int vector, int action) {
            this.timestampMs = timestampMs;
            this.appId = appId;
            this.packageName = packageName != null ? packageName : ("uid:" + appId);
            this.pillar = pillar;
            this.vector = vector;
            this.action = action;
        }
    }

    public static final class SnapshotData {
        public static final SnapshotData DISCONNECTED = new SnapshotData(
                false, 0L, new long[8], 0, 0L, 0, 0, 0, "OFFLINE", "-", Collections.emptyList()
        );

        public final boolean connected;
        public final long total;
        public final long[] counts;
        public final int healthMask;
        public final long uptimeMs;
        public final int guardedCount;
        public final int graphSize;
        public final int apiVersion;
        public final String frameworkName;
        public final String versionName;
        public final List<EventRecord> events;

        public SnapshotData(
                boolean connected,
                long total,
                long[] counts,
                int healthMask,
                long uptimeMs,
                int guardedCount,
                int graphSize,
                int apiVersion,
                String frameworkName,
                String versionName,
                List<EventRecord> events
        ) {
            this.connected = connected;
            this.total = total;
            this.counts = counts;
            this.healthMask = healthMask;
            this.uptimeMs = uptimeMs;
            this.guardedCount = guardedCount;
            this.graphSize = graphSize;
            this.apiVersion = apiVersion;
            this.frameworkName = frameworkName;
            this.versionName = versionName;
            this.events = Collections.unmodifiableList(events);
        }
    }

    public static final class DiagnosticResult {
        public final boolean serviceBound;
        public final int serviceApi;
        public final String serviceFw;
        public final boolean ipcAlive;
        public final int healthMask;
        public final String remoteVer;
        public final boolean hostDevOn;
        public final boolean hostAdbOn;
        public final boolean hostA11yOn;

        public DiagnosticResult(
                boolean serviceBound,
                int serviceApi,
                String serviceFw,
                boolean ipcAlive,
                int healthMask,
                String remoteVer,
                boolean hostDevOn,
                boolean hostAdbOn,
                boolean hostA11yOn
        ) {
            this.serviceBound = serviceBound;
            this.serviceApi = serviceApi;
            this.serviceFw = serviceFw;
            this.ipcAlive = ipcAlive;
            this.healthMask = healthMask;
            this.remoteVer = remoteVer;
            this.hostDevOn = hostDevOn;
            this.hostAdbOn = hostAdbOn;
            this.hostA11yOn = hostA11yOn;
        }
    }
}