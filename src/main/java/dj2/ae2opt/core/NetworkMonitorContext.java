package dj2.ae2opt.core;


public final class NetworkMonitorContext {

    private static final int MAX_DEPTH = 8;

    private static final ThreadLocal<NetworkMonitorContext> CONTEXT = new ThreadLocal<NetworkMonitorContext>() {
        @Override
        protected NetworkMonitorContext initialValue() {
            return new NetworkMonitorContext();
        }
    };

    private final long[] forceUpdateStarts = new long[MAX_DEPTH];
    private int cellUpdateDepth;
    private int cellUpdateOverflow;
    private int forceUpdateDepth;
    private int forceUpdateOverflow;

    private NetworkMonitorContext() {
    }

    public static void cellUpdateEnter() {
        NetworkMonitorContext context = CONTEXT.get();
        if (context.cellUpdateDepth >= MAX_DEPTH) {
            context.cellUpdateOverflow++;
            return;
        }
        context.cellUpdateDepth++;
    }

    public static void cellUpdateExit() {
        NetworkMonitorContext context = CONTEXT.get();
        if (context.cellUpdateOverflow > 0) {
            context.cellUpdateOverflow--;
            return;
        }
        if (context.cellUpdateDepth > 0) {
            context.cellUpdateDepth--;
        }
    }

    public static boolean insideCellUpdate() {
        return CONTEXT.get().cellUpdateDepth > 0;
    }

    public static void forceUpdateEnter() {
        NetworkMonitorContext context = CONTEXT.get();
        if (context.forceUpdateDepth >= MAX_DEPTH) {
            context.forceUpdateOverflow++;
            return;
        }
        context.forceUpdateStarts[context.forceUpdateDepth++] = System.nanoTime();
    }

    public static long forceUpdateExit() {
        NetworkMonitorContext context = CONTEXT.get();
        if (context.forceUpdateOverflow > 0) {
            context.forceUpdateOverflow--;
            return 0L;
        }
        if (context.forceUpdateDepth <= 0) {
            return 0L;
        }
        return System.nanoTime() - context.forceUpdateStarts[--context.forceUpdateDepth];
    }

    public static int resetAtServerTickEnd() {
        NetworkMonitorContext context = CONTEXT.get();
        int leaked = context.cellUpdateDepth + context.forceUpdateDepth
                + context.cellUpdateOverflow + context.forceUpdateOverflow;
        if (leaked != 0) {
            context.cellUpdateDepth = 0;
            context.cellUpdateOverflow = 0;
            context.forceUpdateDepth = 0;
            context.forceUpdateOverflow = 0;
        }
        return leaked;
    }
}
