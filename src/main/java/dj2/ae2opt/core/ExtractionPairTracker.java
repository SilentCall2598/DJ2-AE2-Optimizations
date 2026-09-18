package dj2.ae2opt.core;


public final class ExtractionPairTracker {

    public static final int SIM_FIRST = 0;
    public static final int SIM_REPEAT_SAME_REQUEST = 1;
    public static final int SIM_SUPERSEDED = 2;

    public static final int MOD_NO_MATCH = 0;
    public static final int MOD_MATCHES_SIM_RESULT = 1;
    public static final int MOD_MATCHES_SIM_REQUEST = 2;
    public static final int MOD_MATCHES_TYPE_DIFFERENT_AMOUNT = 3;
    public static final int MOD_POWERED_CONTEXT = 16;

    private boolean pending;
    private Object source;
    private Object typeKey;
    private long requested;
    private long returned;
    private long tick;
    private long poweredContextId;


    public boolean expireBefore(long currentTick) {
        if (this.pending && this.tick != currentTick) {
            this.pending = false;
            return true;
        }
        return false;
    }


    public int observeSimulate(Object source, Object typeKey, long requested, long returned,
                               long tick, long poweredContextId) {
        int result = SIM_FIRST;
        if (this.pending) {
            if (this.tick == tick
                    && this.source == source
                    && this.typeKey == typeKey
                    && this.requested == requested
                    && this.poweredContextId == poweredContextId) {
                result = SIM_REPEAT_SAME_REQUEST;
            } else {
                result = SIM_SUPERSEDED;
            }
        }

        this.pending = true;
        this.source = source;
        this.typeKey = typeKey;
        this.requested = requested;
        this.returned = returned;
        this.tick = tick;
        this.poweredContextId = poweredContextId;
        return result;
    }


    public int observeModulate(Object source, Object typeKey, long requested,
                               long tick, long poweredContextId) {
        if (!this.pending) {
            return MOD_NO_MATCH;
        }

        boolean same = this.tick == tick
                && this.source == source
                && this.typeKey == typeKey;

        if (!same) {
            this.pending = false;
            return MOD_NO_MATCH;
        }

        boolean powered = this.poweredContextId != 0L || poweredContextId != 0L;
        if (powered && (this.poweredContextId == 0L
                || poweredContextId == 0L
                || this.poweredContextId != poweredContextId)) {
            this.pending = false;
            return MOD_NO_MATCH;
        }

        int relation;
        if (requested == this.returned) {
            relation = MOD_MATCHES_SIM_RESULT;
        } else if (requested == this.requested) {
            relation = MOD_MATCHES_SIM_REQUEST;
        } else {
            relation = MOD_MATCHES_TYPE_DIFFERENT_AMOUNT;
        }
        this.pending = false;
        return powered ? relation | MOD_POWERED_CONTEXT : relation;
    }

    public static boolean isPoweredPair(int pair) {
        return (pair & MOD_POWERED_CONTEXT) != 0;
    }

    public static int amountRelation(int pair) {
        return pair & ~MOD_POWERED_CONTEXT;
    }

    public boolean hasPending() {
        return this.pending;
    }
}
