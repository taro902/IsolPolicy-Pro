package io.github.taro902.isolationpolicy;

import org.junit.Test;

import io.github.taro902.isolationpolicy.core.PolicyFlags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PolicyFlagsTest {

    @Test
    public void testLegacyBitLayoutPreserved() {
        assertEquals(0x03, PolicyFlags.AZ_MASK);
        assertEquals(0x04, PolicyFlags.FLAG_ISO_FOLD);
        assertEquals(0x08, PolicyFlags.FLAG_DEV_COHERENT);
        assertEquals(0x10, PolicyFlags.FLAG_A11Y_COHERENT);
        assertEquals(0x20, PolicyFlags.FLAG_PKG_CHANGES);
        assertEquals(0x40, PolicyFlags.FLAG_DISP_STEALTH);
        assertEquals(0x7F, PolicyFlags.LEGACY_V21_MASK);
    }

    @Test
    public void testNewV22BitsAndSanitize() {
        assertEquals(0x80, PolicyFlags.FLAG_PROC_SURFACE);
        assertEquals(0x100, PolicyFlags.FLAG_INPUT_SURFACE);
        assertEquals(0x200, PolicyFlags.FLAG_OBS_COHERENCE);
        assertEquals(0x400, PolicyFlags.FLAG_AUDIT_ONLY);
        assertEquals(0x7FF, PolicyFlags.ALL_SHIELDS_MASK);
        assertEquals(0x7FF, PolicyFlags.sanitize(0xFFFFFFFF));
        assertEquals(0x000, PolicyFlags.sanitize(0xFFFFF800));
    }

    @Test
    public void testPresetsExactMasks() {
        assertEquals(0x3E, PolicyFlags.PRESET_WALLET_ALIPAY);
        assertEquals(0x7A, PolicyFlags.PRESET_BANKING_STRICT);
        assertEquals(0x3FA, PolicyFlags.PRESET_BANKING_MAX);
        assertEquals(0x7FA, PolicyFlags.PRESET_AUDIT_BANK);
        assertTrue(PolicyFlags.isAuditOnly(PolicyFlags.PRESET_AUDIT_BANK));
        assertFalse(PolicyFlags.hasFlag(PolicyFlags.PRESET_BANKING_MAX, PolicyFlags.FLAG_ISO_FOLD));
    }

    @Test
    public void testAuditOnlyWithoutPillarsIsInactive() {
        assertFalse(PolicyFlags.isActive(PolicyFlags.FLAG_AUDIT_ONLY));
        assertTrue(PolicyFlags.isActive(PolicyFlags.FLAG_AUDIT_ONLY | PolicyFlags.FLAG_DEV_COHERENT));
    }

    @Test
    public void testWithAzModeAndToggle() {
        int base = PolicyFlags.FLAG_DEV_COHERENT | PolicyFlags.FLAG_PKG_CHANGES;
        int withDrop = PolicyFlags.withAzMode(base, PolicyFlags.AZ_HARD_DROP);
        assertEquals(PolicyFlags.AZ_HARD_DROP, PolicyFlags.azMode(withDrop));
        assertTrue(PolicyFlags.hasFlag(withDrop, PolicyFlags.FLAG_DEV_COHERENT));
        int toggled = PolicyFlags.toggleFlag(withDrop, PolicyFlags.FLAG_PROC_SURFACE);
        assertTrue(PolicyFlags.hasFlag(toggled, PolicyFlags.FLAG_PROC_SURFACE));
        int off = PolicyFlags.toggleFlag(toggled, PolicyFlags.FLAG_PROC_SURFACE);
        assertFalse(PolicyFlags.hasFlag(off, PolicyFlags.FLAG_PROC_SURFACE));
    }

    @Test
    public void testCountActivePillars() {
        assertEquals(0, PolicyFlags.countActivePillars(PolicyFlags.PRESET_NONE));
        assertEquals(6, PolicyFlags.countActivePillars(PolicyFlags.PRESET_BANKING_STRICT));
        assertEquals(9, PolicyFlags.countActivePillars(PolicyFlags.PRESET_BANKING_MAX));
        assertEquals(10, PolicyFlags.countActivePillars(PolicyFlags.PRESET_AUDIT_MAX));
    }

    @Test
    public void testFormatAndSummarize() {
        assertEquals("0x07A", PolicyFlags.formatMaskHex(PolicyFlags.PRESET_BANKING_STRICT));
        assertEquals("0x7FA", PolicyFlags.formatMaskHex(PolicyFlags.PRESET_AUDIT_BANK));
        assertEquals("OFF", PolicyFlags.summarize(0));
        String s = PolicyFlags.summarize(PolicyFlags.PRESET_AUDIT_BANK);
        assertTrue(s.contains("AZ:DOWN"));
        assertTrue(s.contains("PROC"));
        assertTrue(s.contains("INP"));
        assertTrue(s.contains("OBS"));
        assertTrue(s.contains("AUDIT"));
    }

    @Test
    public void testPillarAndVectorNamesValid() {
        assertEquals(10, PolicyFlags.PILLAR_COUNT);
        for (int p = 0; p < PolicyFlags.PILLAR_COUNT; p++) {
            assertFalse(PolicyFlags.pillarCode(p).equals("UNK"));
        }
        assertEquals("SET_LIST_GLOBAL", PolicyFlags.vectorName(PolicyFlags.VEC_SET_LIST_GLOBAL));
        assertEquals("OBS_PUSH_URI", PolicyFlags.vectorName(PolicyFlags.VEC_OBS_SETTINGS_PUSH));
        assertEquals("AUDIT", PolicyFlags.actionName(PolicyFlags.ACT_AUDIT));
    }
}
