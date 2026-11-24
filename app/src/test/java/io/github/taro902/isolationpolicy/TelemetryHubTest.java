package io.github.taro902.isolationpolicy;

import org.junit.Test;

import io.github.taro902.isolationpolicy.core.PolicyFlags;
import io.github.taro902.isolationpolicy.hook.CompatMatrix;
import io.github.taro902.isolationpolicy.hook.TelemetryHub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TelemetryHubTest {

    @Test
    public void testMetaBitPackingRoundTrip() {
        int meta = TelemetryHub.packMeta(PolicyFlags.PILLAR_OBS, PolicyFlags.VEC_OBS_SETTINGS_PUSH, PolicyFlags.ACT_AUDIT, true, 123);
        assertEquals(PolicyFlags.PILLAR_OBS, TelemetryHub.unpackPillar(meta));
        assertEquals(PolicyFlags.VEC_OBS_SETTINGS_PUSH, TelemetryHub.unpackVector(meta));
        assertEquals(PolicyFlags.ACT_AUDIT, TelemetryHub.unpackAction(meta));
        assertTrue(TelemetryHub.unpackIsAudit(meta));
        assertEquals(123, TelemetryHub.unpackAux(meta));
    }

    @Test
    public void testHealthMask32BitEncoding() {
        TelemetryHub hub = new TelemetryHub();
        for (int p = 0; p < PolicyFlags.PILLAR_COUNT; p++) {
            hub.setPillarStatus(p, TelemetryHub.HEALTH_OK, CompatMatrix.TIER_EXACT);
        }
        hub.setPillarStatus(PolicyFlags.PILLAR_OBS, TelemetryHub.HEALTH_FALLBACK, CompatMatrix.TIER_FALLBACK);
        hub.recordFault(PolicyFlags.PILLAR_DISP, false);
        hub.recordFault(PolicyFlags.PILLAR_INP, true);
        hub.markIpcReady();
        hub.markSettingsReady();

        int mask = hub.computeHealthMask();
        assertEquals(TelemetryHub.HEALTH_OK, TelemetryHub.decodePillarHealth(mask, PolicyFlags.PILLAR_AZ));
        assertEquals(TelemetryHub.HEALTH_FALLBACK, TelemetryHub.decodePillarHealth(mask, PolicyFlags.PILLAR_OBS));
        assertEquals(TelemetryHub.HEALTH_TRIPPED, TelemetryHub.decodePillarHealth(mask, PolicyFlags.PILLAR_INP));
        assertTrue(TelemetryHub.decodePillarHasFault(mask, PolicyFlags.PILLAR_DISP));
        assertTrue(TelemetryHub.decodePillarHasFault(mask, PolicyFlags.PILLAR_INP));
        assertFalse(TelemetryHub.decodePillarHasFault(mask, PolicyFlags.PILLAR_AZ));
        assertEquals(9, TelemetryHub.countHealthyPillars(mask));
        assertTrue((mask & TelemetryHub.HEALTH_BIT_IPC_READY) != 0);
        assertTrue((mask & TelemetryHub.HEALTH_BIT_SETTINGS_READY) != 0);
    }

    @Test
    public void testRingBuffer256WrapAroundOrder() {
        TelemetryHub hub = new TelemetryHub();
        for (int i = 1; i <= 300; i++) {
            hub.recordAt(1000L + i, 10436, i % PolicyFlags.PILLAR_COUNT, PolicyFlags.VEC_SET_CALL_GLOBAL, PolicyFlags.ACT_SANITIZED, false, i);
        }
        TelemetryHub.SnapshotData snap = hub.captureSnapshot();
        assertEquals(TelemetryHub.RING_SIZE, snap.ringTs.length);
        assertEquals(1045L, snap.ringTs[0]);
        assertEquals(1300L, snap.ringTs[TelemetryHub.RING_SIZE - 1]);
        assertEquals(300, TelemetryHub.unpackAux(snap.ringMeta[TelemetryHub.RING_SIZE - 1]));
    }

    @Test
    public void testPerAppMatrix64x10AndAuditSeparation() {
        TelemetryHub hub = new TelemetryHub();
        hub.recordAt(2000L, 10436, PolicyFlags.PILLAR_SET, PolicyFlags.VEC_SET_CALL_GLOBAL, PolicyFlags.ACT_SANITIZED, false, 0);
        hub.recordAt(2001L, 10436, PolicyFlags.PILLAR_SET, PolicyFlags.VEC_SET_LIST_GLOBAL, PolicyFlags.ACT_AUDIT, true, 3);
        hub.recordAt(2002L, 10436, PolicyFlags.PILLAR_USB, PolicyFlags.VEC_USB_STICKY_INTENT, PolicyFlags.ACT_SANITIZED, false, 0);
        hub.recordAt(2003L, 10451, PolicyFlags.PILLAR_A11Y, PolicyFlags.VEC_A11Y_ADD_CLIENT, PolicyFlags.ACT_AUDIT, true, 1);

        assertEquals(1L, hub.getPillarEnforced(PolicyFlags.PILLAR_SET));
        assertEquals(1L, hub.getPillarAudit(PolicyFlags.PILLAR_SET));
        assertEquals(2L, hub.getAppPillarHits(10436, PolicyFlags.PILLAR_SET));
        assertEquals(1L, hub.getAppPillarHits(10436, PolicyFlags.PILLAR_USB));
        assertEquals(1L, hub.getAppAuditHits(10436));
        assertEquals(1L, hub.getAppPillarHits(10451, PolicyFlags.PILLAR_A11Y));
        assertEquals(1L, hub.getAppAuditHits(10451));

        TelemetryHub.SnapshotData snap = hub.captureSnapshot();
        assertEquals(2, snap.appIds.length);
        assertEquals(20, snap.appMatrix.length);
    }

    @Test
    public void testPurgeClearsCountersMatrixAndRing() {
        TelemetryHub hub = new TelemetryHub();
        hub.setPillarStatus(PolicyFlags.PILLAR_AZ, TelemetryHub.HEALTH_OK, CompatMatrix.TIER_EXACT);
        hub.recordAt(3000L, 10436, PolicyFlags.PILLAR_AZ, PolicyFlags.VEC_AZ_HOSTING_CREATE, PolicyFlags.ACT_DOWNGRADED, false, 2);
        hub.purgeCounters();

        TelemetryHub.SnapshotData snap = hub.captureSnapshot();
        assertEquals(0, snap.ringTs.length);
        assertEquals(0, snap.appIds.length);
        assertEquals(0L, hub.getPillarEnforced(PolicyFlags.PILLAR_AZ));
        assertEquals(TelemetryHub.HEALTH_OK, TelemetryHub.decodePillarHealth(snap.healthMask, PolicyFlags.PILLAR_AZ));
    }
}
