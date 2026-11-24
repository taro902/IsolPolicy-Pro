package io.github.taro902.isolationpolicy;

import org.junit.Test;

import io.github.taro902.isolationpolicy.hook.AccessibilityShield;
import io.github.taro902.isolationpolicy.hook.CompatMatrix;
import io.github.taro902.isolationpolicy.hook.IsolatedFoldShield;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RegressionAuditTest {

    @Test
    public void testAccessibilityAddClientIntPairUpper32BitsStrippedAndLowerPreserved() {
        int stateFlags = 0x1F;
        int relevantEvents = 0x00ABCDEF;
        long packed = (((long) stateFlags) << 32) | (relevantEvents & 0xFFFFFFFFL);

        assertEquals(0x1F, AccessibilityShield.extractStateFlags(packed));
        long patched = AccessibilityShield.stripAddClientStatePair(packed);
        assertEquals(0x04, AccessibilityShield.extractStateFlags(patched));
        assertEquals(relevantEvents, (int) (patched & 0xFFFFFFFFL));
    }

    @Test
    public void testAccessibilityAddClientAllDirtyBitsClearedWithNegativeLowerEvents() {
        long packed = (((long) 0x1B) << 32) | 0xFFFFFFFFL;
        long patched = AccessibilityShield.stripAddClientStatePair(packed);
        assertEquals(0x00, AccessibilityShield.extractStateFlags(patched));
        assertEquals(-1, (int) (patched & 0xFFFFFFFFL));
    }

    @Test
    public void testAccessibilityStripStateFlagsPreservesHighContrast() {
        assertEquals(0x04, AccessibilityShield.stripStateFlags(0x1F));
        assertEquals(0x00, AccessibilityShield.stripStateFlags(0x03));
        assertEquals(0x04, AccessibilityShield.stripStateFlags(0x04));
        assertEquals(0x00, AccessibilityShield.stripStateFlags(0x1B));
    }

    @Test
    public void testIsolatedFoldDynamicInstanceNameGate() {
        assertFalse(IsolatedFoldShield.isDynamicIsolatedInstance("com.alipay.SecSvc", "com.alipay.SecSvc"));
        assertFalse(IsolatedFoldShield.isDynamicIsolatedInstance("com.bank.AuthService", null));
        assertTrue(IsolatedFoldShield.isDynamicIsolatedInstance("com.app.Worker", "com.app.Worker:inst0"));
        assertTrue(IsolatedFoldShield.isDynamicIsolatedInstance("com.app.Worker", "com.app.OtherInstance"));
        assertTrue(IsolatedFoldShield.isDynamicIsolatedInstance(null, "com.vendor.Isolated:42"));
    }

    @Test
    public void testCompatMatrixDeoptimizeNullSafe() {
        assertFalse(CompatMatrix.tryDeoptimize(null, null));
        assertEquals(0, CompatMatrix.deoptimizeCallers(null, Object.class, "toString"));
        assertTrue(CompatMatrix.getDeoptimizedCount() >= 0);
    }
}
