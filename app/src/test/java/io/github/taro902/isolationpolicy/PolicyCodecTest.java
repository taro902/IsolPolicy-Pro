package io.github.taro902.isolationpolicy;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;

import io.github.taro902.isolationpolicy.core.PolicyCodec;
import io.github.taro902.isolationpolicy.core.PolicyFlags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PolicyCodecTest {

    @Test
    public void testPolicyMapWireRoundTrip() {
        LinkedHashMap<String, Integer> src = new LinkedHashMap<String, Integer>();
        src.put("id.dana", PolicyFlags.PRESET_BANKING_STRICT);
        src.put("com.shopee.id", PolicyFlags.PRESET_AUDIT_BANK);
        src.put("com.ignored.off", 0);
        String wire = PolicyCodec.encodePolicyMap(src);
        LinkedHashMap<String, Integer> dec = PolicyCodec.decodePolicyMap(wire);
        assertEquals(2, dec.size());
        assertEquals(Integer.valueOf(PolicyFlags.PRESET_BANKING_STRICT), dec.get("id.dana"));
        assertEquals(Integer.valueOf(PolicyFlags.PRESET_AUDIT_BANK), dec.get("com.shopee.id"));
    }

    @Test
    public void testAppIdMapMergesSharedUid() {
        HashMap<String, Integer> pkgFlags = new HashMap<String, Integer>();
        pkgFlags.put("com.bank.a", PolicyFlags.FLAG_DEV_COHERENT);
        pkgFlags.put("com.bank.b", PolicyFlags.FLAG_A11Y_COHERENT | PolicyFlags.FLAG_OBS_COHERENCE);
        HashMap<String, Integer> pkgToId = new HashMap<String, Integer>();
        pkgToId.put("com.bank.a", 10555);
        pkgToId.put("com.bank.b", 10555);

        String wire = PolicyCodec.encodeAppIdMap(pkgFlags, pkgToId);
        HashMap<Integer, Integer> outFlags = new HashMap<Integer, Integer>();
        HashMap<Integer, String> outPkg = new HashMap<Integer, String>();
        PolicyCodec.decodeAppIdMap(wire, outFlags, outPkg);

        int expected = PolicyFlags.FLAG_DEV_COHERENT | PolicyFlags.FLAG_A11Y_COHERENT | PolicyFlags.FLAG_OBS_COHERENCE;
        assertEquals(Integer.valueOf(expected), outFlags.get(10555));
        assertTrue(outPkg.get(10555) != null);
    }

    @Test
    public void testJsonV22RoundTrip() {
        LinkedHashMap<String, Integer> src = new LinkedHashMap<String, Integer>();
        src.put("id.dana", PolicyFlags.PRESET_BANKING_MAX);
        src.put("com.bca", PolicyFlags.PRESET_AUDIT_BANK);
        String json = PolicyCodec.exportJson("2.2.0", PolicyFlags.AZ_AUTO, src);
        PolicyCodec.ImportResult res = PolicyCodec.importJson(json);
        assertTrue(res.valid);
        assertEquals("2.2.0", res.sourceVersion);
        assertEquals(PolicyFlags.AZ_AUTO, res.defaultAzMode);
        assertEquals(2, res.importedCount);
        assertEquals(Integer.valueOf(PolicyFlags.PRESET_BANKING_MAX), res.policies.get("id.dana"));
        assertEquals(Integer.valueOf(PolicyFlags.PRESET_AUDIT_BANK), res.policies.get("com.bca"));
    }

    @Test
    public void testImportV21JsonBackwardCompat() {
        String v21 = "{\"schema\":\"isolpolicy\",\"version\":\"2.1.1\",\"defaultAzMode\":0,\"policies\":{\"id.dana\":{\"flags\":122,\"az\":2,\"iso\":false,\"dev\":true,\"a11y\":true,\"pkg\":true,\"disp\":true}}}";
        PolicyCodec.ImportResult res = PolicyCodec.importJson(v21);
        assertTrue(res.valid);
        assertEquals(Integer.valueOf(PolicyFlags.PRESET_BANKING_STRICT), res.policies.get("id.dana"));
    }

    @Test
    public void testImportBooleanOnlyObject() {
        String raw = "{\"policies\":{\"com.octo.mobile\":{\"az\":2,\"dev\":true,\"proc\":true,\"inp\":true,\"obs\":true,\"audit\":true}}}";
        PolicyCodec.ImportResult res = PolicyCodec.importJson(raw);
        assertTrue(res.valid);
        int expected = PolicyFlags.AZ_DOWNGRADE
            | PolicyFlags.FLAG_DEV_COHERENT
            | PolicyFlags.FLAG_PROC_SURFACE
            | PolicyFlags.FLAG_INPUT_SURFACE
            | PolicyFlags.FLAG_OBS_COHERENCE
            | PolicyFlags.FLAG_AUDIT_ONLY;
        assertEquals(Integer.valueOf(expected), res.policies.get("com.octo.mobile"));
    }

    @Test
    public void testImportLegacyV20ModesAndTargets() {
        String v20 = "{\"version\":\"2.0.0\",\"modes\":{\"id.dana\":3},\"targets\":[\"com.bca\"]}";
        PolicyCodec.ImportResult res = PolicyCodec.importJson(v20);
        assertTrue(res.valid);
        assertEquals(Integer.valueOf(PolicyFlags.AZ_HARD_DROP), res.policies.get("id.dana"));
        assertEquals(Integer.valueOf(PolicyFlags.AZ_DOWNGRADE), res.policies.get("com.bca"));
    }

    @Test
    public void testDecodeLegacyV20Prefs() {
        HashSet<String> targets = new HashSet<String>(Arrays.asList("com.bca", "id.dana"));
        LinkedHashMap<String, Integer> map = PolicyCodec.decodeLegacyV20("id.dana=3;", targets);
        assertEquals(Integer.valueOf(PolicyFlags.AZ_HARD_DROP), map.get("id.dana"));
        assertEquals(Integer.valueOf(PolicyFlags.AZ_DOWNGRADE), map.get("com.bca"));
    }

    @Test
    public void testRejectMalformedAndEmptyJson() {
        assertFalse(PolicyCodec.importJson(null).valid);
        assertFalse(PolicyCodec.importJson("").valid);
        assertFalse(PolicyCodec.importJson("{bad}").valid);
        assertFalse(PolicyCodec.importJson("{\"unrelated\":1}").valid);
        assertFalse(PolicyCodec.importJson("[1,2,3]").valid);
    }

    @Test
    public void testRejectInvalidPackageTokensInWire() {
        LinkedHashMap<String, Integer> dec = PolicyCodec.decodePolicyMap("good.pkg=122;bad pkg=122;ok.two=62");
        assertEquals(2, dec.size());
        assertTrue(dec.containsKey("good.pkg"));
        assertTrue(dec.containsKey("ok.two"));
    }
}
