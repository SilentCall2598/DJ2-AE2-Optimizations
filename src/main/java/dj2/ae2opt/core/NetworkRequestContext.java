package dj2.ae2opt.core;


public final class NetworkRequestContext {

    public static final int EXTRACT_SIMULATE = 0;
    public static final int EXTRACT_MODULATE = 1;
    public static final int INJECT_SIMULATE = 2;
    public static final int INJECT_MODULATE = 3;
    public static final int KINDS = 4;

    private static final int MAX_DEPTH = 16;

    private static final ThreadLocal<NetworkRequestContext> CONTEXT = new ThreadLocal<NetworkRequestContext>() {
        @Override
        protected NetworkRequestContext initialValue() {
            return new NetworkRequestContext();
        }
    };

    private final int[] kind = new int[MAX_DEPTH];
    private final boolean[] counted = new boolean[MAX_DEPTH];
    private final int[] probes = new int[MAX_DEPTH];
    private final int[] zeroProbes = new int[MAX_DEPTH];
    private int depth;
    private int overflow;

    private NetworkRequestContext() {
    }

    public static int kindFor(boolean extract, boolean simulate) {
        if (extract) {
            return simulate ? EXTRACT_SIMULATE : EXTRACT_MODULATE;
        }
        return simulate ? INJECT_SIMULATE : INJECT_MODULATE;
    }


    public static void enter(int requestKind, boolean counting) {
        NetworkRequestContext context = CONTEXT.get();
        if (context.depth >= MAX_DEPTH) {
            context.overflow++;
            return;
        }
        int slot = context.depth++;
        context.kind[slot] = requestKind;
        context.counted[slot] = counting;
        context.probes[slot] = 0;
        context.zeroProbes[slot] = 0;
    }


    public static void exit() {
        NetworkRequestContext context = CONTEXT.get();
        if (context.overflow > 0) {
            context.overflow--;
            return;
        }
        if (context.depth <= 0) {
            return;
        }
        int slot = --context.depth;
        if (context.counted[slot]) {
            Diagnostics.networkRequestCompleted(context.kind[slot], context.probes[slot],
                    context.zeroProbes[slot], context.hasCountingFrame());
        }
    }


    public static void recordProbe(boolean returnedNothing) {
        NetworkRequestContext context = CONTEXT.get();
        int slot = context.innermostCountingFrame();
        if (slot < 0) {
            Diagnostics.probeOutsideNetworkRequest();
            return;
        }
        context.probes[slot]++;
        if (returnedNothing) {
            context.zeroProbes[slot]++;
        }
    }

    private int innermostCountingFrame() {
        for (int i = this.depth - 1; i >= 0; i--) {
            if (this.counted[i]) {
                return i;
            }
        }
        return -1;
    }

    private boolean hasCountingFrame() {
        return this.innermostCountingFrame() >= 0;
    }


    public static int currentDepth() {
        return CONTEXT.get().depth;
    }


    public static void resetForTickEnd() {
        NetworkRequestContext context = CONTEXT.get();
        if (context.depth != 0 || context.overflow != 0) {
            context.depth = 0;
            context.overflow = 0;
            Diagnostics.networkContextReset();
        }
    }
}
