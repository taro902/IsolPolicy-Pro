package io.github.taro902.isolationpolicy.hook;

import android.os.Bundle;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class TelemetryHub {

    public static final int CAPACITY = 64;
    public static final int STATE_NONE = 0;
    public static final int STATE_OK = 1;
    public static final int STATE_FALLBACK = 2;
    public static final int STATE_FAILED = 3;

    private final EventSlot[] ring = new EventSlot[CAPACITY];
    private final AtomicLong writeSeq = new AtomicLong(0L);
    private final AtomicLong[] pillarCounts = new AtomicLong[8];
    private final AtomicInteger hookHealthMask = new AtomicInteger(0);
    private final long bootTimeMs = System.currentTimeMillis();

    public TelemetryHub() {
        for (int i = 0; i < CAPACITY; i++) {
            ring[i] = new EventSlot();
        }
        for (int i = 0; i < pillarCounts.length; i++) {
            pillarCounts[i] = new AtomicLong(0L);
        }
    }

    public void markHookState(int hookBit, int state) {
        if (hookBit < 0 || hookBit > 7) {
            return;
        }
        int shift = hookBit * 2;
        int clearMask = ~(0x03 << shift);
        int encoded = (state & 0x03) << shift;
        int prev;
        int next;
        do {
            prev = hookHealthMask.get();
            next = (prev & clearMask) | encoded;
        } while (!hookHealthMask.compareAndSet(prev, next));
    }

    public int getHookHealthMask() {
        return hookHealthMask.get();
    }

    public void record(int appId, int pillar, int vector, int action) {
        pillarCounts[0].incrementAndGet();
        if (pillar >= 1 && pillar <= 7) {
            pillarCounts[pillar].incrementAndGet();
        }
        long seq = writeSeq.getAndIncrement();
        EventSlot slot = ring[(int) (seq & (CAPACITY - 1))];
        slot.write(seq + 1L, System.currentTimeMillis(), appId, pillar, vector, action);
    }

    public void purge() {
        for (AtomicLong c : pillarCounts) {
            c.set(0L);
        }
        writeSeq.set(0L);
        for (EventSlot slot : ring) {
            slot.clear();
        }
    }

    public Bundle buildSnapshotBundle(PolicyEngine engine) {
        Bundle b = new Bundle();
        long[] counts = new long[8];
        for (int i = 0; i < 8; i++) {
            counts[i] = pillarCounts[i].get();
        }
        b.putLong("total", counts[0]);
        b.putLongArray("counts", counts);
        b.putInt("health", hookHealthMask.get());
        b.putLong("uptime", System.currentTimeMillis() - bootTimeMs);
        b.putInt("guarded", engine.getGuardedCount());
        b.putInt("graph", engine.getIdentityGraphSize());
        b.putInt("api", engine.getApiVersion());
        b.putString("fw", engine.getFrameworkName());
        b.putString("ver", PolicyFlags.VERSION_NAME);

        long head = writeSeq.get();
        int available = (int) Math.min(head, CAPACITY);
        long[] ts = new long[available];
        int[] uids = new int[available];
        String[] pkgs = new String[available];
        int[] pillars = new int[available];
        int[] vectors = new int[available];
        int[] actions = new int[available];

        int outIdx = 0;
        for (int i = 0; i < available; i++) {
            long targetSeq = head - i;
            if (targetSeq <= 0L) {
                break;
            }
            EventSlot slot = ring[(int) ((targetSeq - 1L) & (CAPACITY - 1))];
            long s1 = slot.seq;
            if (s1 <= 0L) {
                continue;
            }
            long t = slot.timestampMs;
            int u = slot.appId;
            int p = slot.pillar;
            int v = slot.vector;
            int a = slot.action;
            long s2 = slot.seq;
            if (s1 != s2) {
                continue;
            }
            ts[outIdx] = t;
            uids[outIdx] = u;
            pkgs[outIdx] = engine.resolvePackageNameForAppId(u);
            pillars[outIdx] = p;
            vectors[outIdx] = v;
            actions[outIdx] = a;
            outIdx++;
        }

        if (outIdx < available) {
            long[] tsTrim = new long[outIdx];
            int[] uidsTrim = new int[outIdx];
            String[] pkgsTrim = new String[outIdx];
            int[] pilTrim = new int[outIdx];
            int[] vecTrim = new int[outIdx];
            int[] actTrim = new int[outIdx];
            System.arraycopy(ts, 0, tsTrim, 0, outIdx);
            System.arraycopy(uids, 0, uidsTrim, 0, outIdx);
            System.arraycopy(pkgs, 0, pkgsTrim, 0, outIdx);
            System.arraycopy(pillars, 0, pilTrim, 0, outIdx);
            System.arraycopy(vectors, 0, vecTrim, 0, outIdx);
            System.arraycopy(actions, 0, actTrim, 0, outIdx);
            ts = tsTrim;
            uids = uidsTrim;
            pkgs = pkgsTrim;
            pillars = pilTrim;
            vectors = vecTrim;
            actions = actTrim;
        }

        b.putLongArray("ev_ts", ts);
        b.putIntArray("ev_uid", uids);
        b.putStringArray("ev_pkg", pkgs);
        b.putIntArray("ev_pil", pillars);
        b.putIntArray("ev_vec", vectors);
        b.putIntArray("ev_act", actions);
        return b;
    }

    private static final class EventSlot {
        volatile long seq;
        long timestampMs;
        int appId;
        int pillar;
        int vector;
        int action;

        void write(long nextSeq, long ts, int uid, int pil, int vec, int act) {
            seq = -1L;
            timestampMs = ts;
            appId = uid;
            pillar = pil;
            vector = vec;
            action = act;
            seq = nextSeq;
        }

        void clear() {
            seq = 0L;
            timestampMs = 0L;
            appId = 0;
            pillar = 0;
            vector = 0;
            action = 0;
        }
    }
}