package dj2.ae2opt.core;

import java.util.LinkedHashMap;
import java.util.Map;


public final class DrawerCandidateIndex {

    private static final int[] EMPTY_SLOTS = new int[0];

    private final long[] keys;
    private final int[][] slots;
    private final int mask;
    private final boolean hasZero;
    private final int[] zeroSlots;
    private final int keyCount;
    private final long slotReferenceCount;

    private DrawerCandidateIndex(long[] keys, int[][] slots, boolean hasZero, int[] zeroSlots,
                                  int keyCount, long slotReferenceCount) {
        this.keys = keys;
        this.slots = slots;
        this.mask = keys.length - 1;
        this.hasZero = hasZero;
        this.zeroSlots = zeroSlots;
        this.keyCount = keyCount;
        this.slotReferenceCount = slotReferenceCount;
    }

    public int[] candidatesFor(long key) {
        if (key == 0L) {
            return this.hasZero ? this.zeroSlots : null;
        }
        int i = index(key) & this.mask;
        while (true) {
            final long current = this.keys[i];
            if (current == key) {
                return this.slots[i];
            }
            if (current == 0L) {
                return null;
            }
            i = (i + 1) & this.mask;
        }
    }

    public int keyCount() {
        return this.keyCount;
    }

    public long slotReferenceCount() {
        return this.slotReferenceCount;
    }

    private static int index(long key) {
        final long h = key * -7046029254386353131L;
        return (int) (h ^ (h >>> 32));
    }

    public static final class Builder {

        private final Map<Long, IntList> pending = new LinkedHashMap<Long, IntList>();
        private final int maxKeys;
        private final long maxSlotReferences;
        private long slotReferenceCount;
        private boolean keyCapExceeded;
        private boolean slotReferenceCapExceeded;

        public Builder(int maxKeys, long maxSlotReferences) {
            this.maxKeys = maxKeys;
            this.maxSlotReferences = maxSlotReferences;
        }

        public boolean addOccurrence(int slot, long literalKey, long[] equivalentKeys) {
            if (this.keyCapExceeded || this.slotReferenceCapExceeded) {
                return false;
            }
            if (!this.appendOne(literalKey, slot)) {
                return false;
            }
            for (int i = 0; i < equivalentKeys.length; i++) {
                final long key = equivalentKeys[i];
                if (key == literalKey || this.seenEarlierInOccurrence(equivalentKeys, i, key)) {
                    continue;
                }
                if (!this.appendOne(key, slot)) {
                    return false;
                }
            }
            return true;
        }

        private boolean seenEarlierInOccurrence(long[] equivalentKeys, int upTo, long key) {
            for (int j = 0; j < upTo; j++) {
                if (equivalentKeys[j] == key) {
                    return true;
                }
            }
            return false;
        }

        private boolean appendOne(long key, int slot) {
            IntList list = this.pending.get(key);
            if (list == null) {
                if (this.pending.size() >= this.maxKeys) {
                    this.keyCapExceeded = true;
                    return false;
                }
                list = new IntList();
                this.pending.put(key, list);
            }
            if (this.slotReferenceCount >= this.maxSlotReferences) {
                this.slotReferenceCapExceeded = true;
                return false;
            }
            list.add(slot);
            this.slotReferenceCount++;
            return true;
        }

        public boolean keyCapExceeded() {
            return this.keyCapExceeded;
        }

        public boolean slotReferenceCapExceeded() {
            return this.slotReferenceCapExceeded;
        }

        public boolean capExceeded() {
            return this.keyCapExceeded || this.slotReferenceCapExceeded;
        }

        public DrawerCandidateIndex build() {
            boolean hasZero = false;
            int[] zeroSlots = null;
            int nonZeroCount = 0;
            for (Map.Entry<Long, IntList> entry : this.pending.entrySet()) {
                if (entry.getKey() == 0L) {
                    hasZero = true;
                    zeroSlots = entry.getValue().toArray();
                } else {
                    nonZeroCount++;
                }
            }

            int capacity = 16;
            while (capacity < nonZeroCount * 2) {
                capacity <<= 1;
            }
            final long[] keys = new long[capacity];
            final int[][] slots = new int[capacity][];
            final int mask = capacity - 1;

            for (Map.Entry<Long, IntList> entry : this.pending.entrySet()) {
                final long key = entry.getKey();
                if (key == 0L) {
                    continue;
                }
                int i = index(key) & mask;
                while (keys[i] != 0L) {
                    i = (i + 1) & mask;
                }
                keys[i] = key;
                slots[i] = entry.getValue().toArray();
            }

            return new DrawerCandidateIndex(keys, slots, hasZero,
                    hasZero ? zeroSlots : null, this.pending.size(), this.slotReferenceCount);
        }
    }

    private static final class IntList {
        private int[] values = EMPTY_SLOTS;
        private int size;

        void add(int value) {
            if (this.size == this.values.length) {
                final int[] grown = new int[this.size == 0 ? 4 : this.size * 2];
                System.arraycopy(this.values, 0, grown, 0, this.size);
                this.values = grown;
            }
            this.values[this.size++] = value;
        }

        int[] toArray() {
            if (this.size == this.values.length) {
                return this.values;
            }
            final int[] out = new int[this.size];
            System.arraycopy(this.values, 0, out, 0, this.size);
            return out;
        }
    }
}
