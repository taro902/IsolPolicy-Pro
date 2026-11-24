package io.github.taro902.isolationpolicy;

import org.junit.Test;

import java.util.HashMap;

import io.github.taro902.isolationpolicy.core.PolicyFlags;
import io.github.taro902.isolationpolicy.hook.PolicyEngine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class IdentityGraphTombstoneTest {

    @Test
    public void testCollisionEvictionPreservesProbeChain() {
        PolicyEngine engine = new PolicyEngine();
        int first = 99001;
        int firstSlot = computeSlot(first);
        int colliding = -1;
        for (int u = 99002; u <= 99999; u++) {
            if (computeSlot(u) == firstSlot) {
                colliding = u;
                break;
            }
        }
        assertTrue(colliding > first);

        assertTrue(engine.registerIsolatedUid(first, 10436));
        assertTrue(engine.registerIsolatedUid(colliding, 10451));
        assertEquals(2, engine.getActiveGraphSize());

        assertTrue(engine.evictIsolatedUid(first));
        assertEquals(1, engine.getActiveGraphSize());
        assertEquals(-1, engine.lookupOwnerAppId(first));
        assertEquals(10451, engine.lookupOwnerAppId(colliding));
    }

    @Test
    public void testTombstoneSlotReuseNoDuplicates() {
        PolicyEngine engine = new PolicyEngine();
        int a = 99010;
        int slotA = computeSlot(a);
        int b = -1;
        int c = -1;
        for (int u = 99011; u <= 99999; u++) {
            if (computeSlot(u) == slotA) {
                if (b < 0) {
                    b = u;
                } else {
                    c = u;
                    break;
                }
            }
        }
        assertTrue(b > 0 && c > 0);
        engine.registerIsolatedUid(a, 10400);
        engine.registerIsolatedUid(b, 10401);
        engine.evictIsolatedUid(a);
        engine.registerIsolatedUid(b, 10402);
        assertEquals(10402, engine.lookupOwnerAppId(b));
        assertEquals(1, engine.getActiveGraphSize());

        engine.registerIsolatedUid(c, 10403);
        assertEquals(10403, engine.lookupOwnerAppId(c));
        assertEquals(10402, engine.lookupOwnerAppId(b));
        assertEquals(2, engine.getActiveGraphSize());
    }

    @Test
    public void testResolvePolicyPropagatesThroughIsolatedUid() {
        PolicyEngine engine = new PolicyEngine();
        HashMap<String, Integer> pkgMap = new HashMap<String, Integer>();
        pkgMap.put("id.dana", PolicyFlags.PRESET_BANKING_MAX);
        HashMap<Integer, Integer> idMap = new HashMap<Integer, Integer>();
        idMap.put(10436, PolicyFlags.PRESET_BANKING_MAX);
        HashMap<Integer, String> idPkg = new HashMap<Integer, String>();
        idPkg.put(10436, "id.dana");
        engine.installSnapshot(PolicyFlags.AZ_OFF, pkgMap, idMap, idPkg);

        engine.registerIsolatedUid(99042, 10436);
        assertEquals(PolicyFlags.PRESET_BANKING_MAX, engine.resolvePolicy(99042));
        assertEquals(10436, engine.resolveEffectiveAppId(99042));
    }

    @Test
    public void testMultiUserWorkProfileAndFastPathSystemUid() {
        PolicyEngine engine = new PolicyEngine();
        HashMap<String, Integer> pkgMap = new HashMap<String, Integer>();
        pkgMap.put("com.bca", PolicyFlags.PRESET_BANKING_STRICT);
        HashMap<Integer, Integer> idMap = new HashMap<Integer, Integer>();
        idMap.put(10436, PolicyFlags.PRESET_BANKING_STRICT);
        engine.installSnapshot(PolicyFlags.AZ_DOWNGRADE, pkgMap, idMap, null);

        assertEquals(0, engine.resolvePolicy(0));
        assertEquals(0, engine.resolvePolicy(1000));
        assertEquals(0, engine.resolvePolicy(101000));
        assertEquals(PolicyFlags.PRESET_BANKING_STRICT, engine.resolvePolicy(1010436));

        engine.registerIsolatedUid(1099015, 1010436);
        assertEquals(PolicyFlags.PRESET_BANKING_STRICT, engine.resolvePolicy(1099015));
        assertEquals(10436, engine.resolveEffectiveAppId(1099015));
    }

    @Test
    public void testAppZygoteIsolatedRangeSupported() {
        PolicyEngine engine = new PolicyEngine();
        assertTrue(PolicyEngine.isIsolatedUid(90000));
        assertTrue(PolicyEngine.isIsolatedUid(98999));
        assertTrue(PolicyEngine.isIsolatedUid(99000));
        assertTrue(PolicyEngine.isIsolatedUid(99999));
        assertFalse(PolicyEngine.isIsolatedUid(10436));
        assertFalse(PolicyEngine.isIsolatedUid(1000));
        assertTrue(engine.registerIsolatedUid(90012, 10436));
        assertEquals(10436, engine.lookupOwnerAppId(90012));
    }

    @Test
    public void testInvalidRegistrationsRejected() {
        PolicyEngine engine = new PolicyEngine();
        assertFalse(engine.registerIsolatedUid(10436, 10451));
        assertFalse(engine.registerIsolatedUid(99001, 1000));
        assertFalse(engine.registerIsolatedUid(99001, 99002));
        assertEquals(0, engine.getActiveGraphSize());
    }

    @Test
    public void testResolvePackageOverridesDefaultAz() {
        PolicyEngine engine = new PolicyEngine();
        HashMap<String, Integer> pkgMap = new HashMap<String, Integer>();
        pkgMap.put("id.co.bri.brimo", PolicyFlags.PRESET_BANKING_MAX);
        engine.installSnapshot(PolicyFlags.AZ_AUTO, pkgMap, null, null);
        assertEquals(PolicyFlags.PRESET_BANKING_MAX, engine.resolvePolicyForPackageOrUid("id.co.bri.brimo", 10777));
    }

    private static int computeSlot(int isoUid) {
        int h = isoUid * 0x9E3779B9;
        return (h ^ (h >>> 16)) & (PolicyEngine.GRAPH_SIZE - 1);
    }
}
