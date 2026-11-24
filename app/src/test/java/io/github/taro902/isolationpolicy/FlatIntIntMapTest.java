package io.github.taro902.isolationpolicy;

import org.junit.Test;

import io.github.taro902.isolationpolicy.core.FlatIntIntMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FlatIntIntMapTest {

    @Test
    public void testEmptyMap() {
        assertEquals(0, FlatIntIntMap.EMPTY.size());
        assertEquals(-1, FlatIntIntMap.EMPTY.get(10436, -1));
        assertFalse(FlatIntIntMap.EMPTY.containsKey(10436));
    }

    @Test
    public void testSingleAndOverwrite() {
        FlatIntIntMap map = FlatIntIntMap.newBuilder(4)
            .put(10436, 0x7A)
            .put(10451, 0x3E)
            .put(10436, 0x3FA)
            .build();
        assertEquals(2, map.size());
        assertEquals(0x3FA, map.get(10436, -1));
        assertEquals(0x3E, map.get(10451, -1));
        assertEquals(-1, map.get(10999, -1));
    }

    @Test
    public void testHighVolumeRehashAndLoadFactor() {
        FlatIntIntMap.Builder b = FlatIntIntMap.newBuilder(4);
        for (int i = 0; i < 600; i++) {
            b.put(10000 + i * 3, (i & 0x7FF));
        }
        FlatIntIntMap map = b.build();
        assertEquals(600, map.size());
        assertTrue(map.capacity() >= 1200);
        assertEquals(0, map.capacity() & (map.capacity() - 1));
        for (int i = 0; i < 600; i++) {
            assertEquals(i & 0x7FF, map.get(10000 + i * 3, -1));
            assertFalse(map.containsKey(10001 + i * 3));
        }
    }

    @Test
    public void testZeroAndNegativeKeys() {
        FlatIntIntMap map = FlatIntIntMap.newBuilder(4)
            .put(0, 7)
            .put(-1, 9)
            .put(FlatIntIntMap.EMPTY_KEY, 99)
            .build();
        assertEquals(2, map.size());
        assertEquals(7, map.get(0, -1));
        assertEquals(9, map.get(-1, -1));
        assertEquals(-1, map.get(FlatIntIntMap.EMPTY_KEY, -1));
    }

    @Test
    public void testCopyKeysExactCount() {
        FlatIntIntMap map = FlatIntIntMap.newBuilder(4)
            .put(10100, 1)
            .put(10200, 2)
            .put(10300, 3)
            .build();
        int[] keys = map.copyKeys();
        assertEquals(3, keys.length);
        int sum = keys[0] + keys[1] + keys[2];
        assertEquals(30600, sum);
    }

    @Test
    public void testDenseCollisionPattern() {
        FlatIntIntMap.Builder b = FlatIntIntMap.newBuilder(8);
        for (int k = 10000; k < 10128; k++) {
            b.put(k, k ^ 0x55);
        }
        FlatIntIntMap map = b.build();
        for (int k = 10000; k < 10128; k++) {
            assertEquals(k ^ 0x55, map.get(k, -1));
        }
    }
}
