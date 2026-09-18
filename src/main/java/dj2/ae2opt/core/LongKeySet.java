package dj2.ae2opt.core;


public final class LongKeySet {

    private long[] table;
    private int mask;
    private int size;
    private boolean hasZero;

    public LongKeySet(int expected) {
        int capacity = 16;
        while (capacity < expected * 2) {
            capacity <<= 1;
        }
        this.table = new long[capacity];
        this.mask = capacity - 1;
    }

    public int size() {
        return this.size + (this.hasZero ? 1 : 0);
    }

    public void add(long key) {
        if (key == 0L) {
            this.hasZero = true;
            return;
        }
        if ((this.size + 1) * 2 > this.table.length) {
            this.grow();
        }
        int i = index(key) & this.mask;
        while (true) {
            long current = this.table[i];
            if (current == key) {
                return;
            }
            if (current == 0L) {
                this.table[i] = key;
                this.size++;
                return;
            }
            i = (i + 1) & this.mask;
        }
    }

    public boolean contains(long key) {
        if (key == 0L) {
            return this.hasZero;
        }
        int i = index(key) & this.mask;
        while (true) {
            long current = this.table[i];
            if (current == key) {
                return true;
            }
            if (current == 0L) {
                return false;
            }
            i = (i + 1) & this.mask;
        }
    }

    private void grow() {
        final long[] old = this.table;
        this.table = new long[old.length << 1];
        this.mask = this.table.length - 1;
        this.size = 0;
        for (long key : old) {
            if (key != 0L) {
                this.add(key);
            }
        }
    }

    private static int index(long key) {
        long h = key * -7046029254386353131L;
        return (int) (h ^ (h >>> 32));
    }
}
