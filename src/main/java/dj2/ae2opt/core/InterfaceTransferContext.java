package dj2.ae2opt.core;

import java.util.IdentityHashMap;
import java.util.Map;


public final class InterfaceTransferContext {

    private static final int MAX_DEPTH = 8;

    private static final ThreadLocal<InterfaceTransferContext> CONTEXT = new ThreadLocal<InterfaceTransferContext>() {
        @Override
        protected InterfaceTransferContext initialValue() {
            return new InterfaceTransferContext();
        }
    };

    @SuppressWarnings("unchecked")
    private final Map<Object, Boolean>[] negativeAdapters = new IdentityHashMap[MAX_DEPTH];
    private int depth;
    private int overflow;

    private InterfaceTransferContext() {
        for (int i = 0; i < MAX_DEPTH; i++) {
            this.negativeAdapters[i] = new IdentityHashMap<Object, Boolean>();
        }
    }

    public static void enter() {
        InterfaceTransferContext context = CONTEXT.get();
        if (context.depth >= MAX_DEPTH) {
            context.overflow++;
            return;
        }
        context.negativeAdapters[context.depth].clear();
        context.depth++;
    }

    public static void exit() {
        InterfaceTransferContext context = CONTEXT.get();
        if (context.overflow > 0) {
            context.overflow--;
            return;
        }
        if (context.depth > 0) {
            context.negativeAdapters[context.depth - 1].clear();
            context.depth--;
        }
    }

    public static boolean isActive() {
        InterfaceTransferContext context = CONTEXT.get();
        return context.depth > 0 && context.overflow == 0;
    }

    public static void recordNegativeSimulate(Object adapter) {
        InterfaceTransferContext context = CONTEXT.get();
        if (context.depth <= 0 || context.overflow > 0) {
            return;
        }
        context.negativeAdapters[context.depth - 1].put(adapter, Boolean.TRUE);
    }

    public static boolean wasNegativeSimulate(Object adapter) {
        InterfaceTransferContext context = CONTEXT.get();
        if (context.depth <= 0 || context.overflow > 0) {
            return false;
        }
        return context.negativeAdapters[context.depth - 1].containsKey(adapter);
    }

    public static int resetAtServerTickEnd() {
        InterfaceTransferContext context = CONTEXT.get();
        int leaked = context.depth + context.overflow;
        if (leaked != 0) {
            for (int i = 0; i < MAX_DEPTH; i++) {
                context.negativeAdapters[i].clear();
            }
            context.depth = 0;
            context.overflow = 0;
        }
        return leaked;
    }
}
