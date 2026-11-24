package io.github.taro902.isolationpolicy.core;

import java.util.Arrays;

public final class FlatIntIntMap {

    public static final int EMPTY_KEY = Integer.MIN_VALUE;
    private static final int FIB_MULTIPLIER = 0x9E3779B9;
    public static final FlatIntIntMap EMPTY = new FlatIntIntMap(new int[16], new int[16], 15, 28, 0);

    private final int[] keys;
    private final int[] vals;
    private final int mask;
    private final int shift;
    private final int size;

    private FlatIntIntMap(int[] keys, int[] vals, int mask, int shift, int size) {
        this.keys = keys;
        this.vals = vals;
        this.mask = mask;
        this.shift = shift;
        this.size = size;
    }

    public int get(int key, int defaultValue) {
        if (size == 0 || key == EMPTY_KEY) {
            return defaultValue;
        }
        int idx = ((key * FIB_MULTIPLIER) >>> shift) & mask;
        while (true) {
            int k = keys[idx];
            if (k == key) {
                return vals[idx];
            }
            if (k == EMPTY_KEY) {
                return defaultValue;
            }
            idx = (idx + 1) & mask;
        }
    }

    public boolean containsKey(int key) {
        if (size == 0 || key == EMPTY_KEY) {
            return false;
        }
        int idx = ((key * FIB_MULTIPLIER) >>> shift) & mask;
        while (true) {
            int k = keys[idx];
            if (k == key) {
                return true;
            }
            if (k == EMPTY_KEY) {
                return false;
            }
            idx = (idx + 1) & mask;
        }
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return keys.length;
    }

    public int[] copyKeys() {
        int[] out = new int[size];
        int p = 0;
        for (int k : keys) {
            if (k != EMPTY_KEY && p < size) {
                out[p++] = k;
            }
        }
        return out;
    }

    public static Builder newBuilder(int expectedEntries) {
        return new Builder(expectedEntries);
    }

    public static final class Builder {
        private int[] keys;
        private int[] vals;
        private int mask;
        private int shift;
        private int size;

        public Builder(int expectedEntries) {
            int cap = tableSizeFor(Math.max(8, expectedEntries));
            initTable(cap);
        }

        private void initTable(int cap) {
            keys = new int[cap];
            vals = new int[cap];
            Arrays.fill(keys, EMPTY_KEY);
            mask = cap - 1;
            shift = 32 - Integer.numberOfTrailingZeros(cap);
            size = 0;
        }

        public Builder put(int key, int value) {
            if (key == EMPTY_KEY) {
                return this;
            }
            if ((size + 1) * 2 >= keys.length) {
                rehash(keys.length << 1);
            }
            insertInto(keys, vals, mask, shift, key, value);
            return this;
        }

        private void insertInto(int[] kArr, int[] vArr, int m, int sh, int key, int value) {
            int idx = ((key * FIB_MULTIPLIER) >>> sh) & m;
            while (true) {
                int existing = kArr[idx];
                if (existing == EMPTY_KEY) {
                    kArr[idx] = key;
                    vArr[idx] = value;
                    size++;
                    return;
                }
                if (existing == key) {
                    vArr[idx] = value;
                    return;
                }
                idx = (idx + 1) & m;
            }
        }

        private void rehash(int newCap) {
            int[] oldK = keys;
            int[] oldV = vals;
            initTable(newCap);
            for (int i = 0; i < oldK.length; i++) {
                int k = oldK[i];
                if (k != EMPTY_KEY) {
                    insertInto(keys, vals, mask, shift, k, oldV[i]);
                }
            }
        }

        public FlatIntIntMap build() {
            if (size == 0) {
                return EMPTY;
            }
            return new FlatIntIntMap(keys, vals, mask, shift, size);
        }

        private static int tableSizeFor(int expected) {
            int minCap = Math.max(16, expected * 2 + 1);
            int cap = 16;
            while (cap < minCap && cap < (1 << 20)) {
                cap <<= 1;
            }
            return cap;
        }
    }
}
