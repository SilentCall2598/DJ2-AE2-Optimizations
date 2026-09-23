package dj2.ae2opt.core;

import java.util.IdentityHashMap;
import java.util.Map;


public final class InterfaceTransferContext {

    public enum Kind { EXTRACTION, INSERTION }

    private static final int MAX_DEPTH = 8;

    private static final ThreadLocal<InterfaceTransferContext> CONTEXT = new ThreadLocal<InterfaceTransferContext>() {
        @Override
        protected InterfaceTransferContext initialValue() {
            return new InterfaceTransferContext();
        }
    };

    @SuppressWarnings("unchecked")
    private final Map<Object, Boolean>[] negativeAdapters = new IdentityHashMap[MAX_DEPTH];
    private final Kind[] kinds = new Kind[MAX_DEPTH];
    private int depth;
    private int overflow;

    private InterfaceTransferContext() {
        for (int i = 0; i < MAX_DEPTH; i++) {
            this.negativeAdapters[i] = new IdentityHashMap<Object, Boolean>();
        }
    }

    public static void enterExtraction() {
        enter(Kind.EXTRACTION);
    }

    public static void enterInsertion() {
        enter(Kind.INSERTION);
    }

    private static void enter(Kind kind) {
        InterfaceTransferContext context = CONTEXT.get();
        if (context.depth >= MAX_DEPTH) {
            context.overflow++;
            return;
        }
        context.negativeAdapters[context.depth].clear();
        context.kinds[context.depth] = kind;
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
            context.kinds[context.depth - 1] = null;
            context.depth--;
        }
    }

    public static boolean isActive(Kind kind) {
        InterfaceTransferContext context = CONTEXT.get();
        if (context.depth <= 0 || context.overflow > 0) {
            return false;
        }
        return context.kinds[context.depth - 1] == kind;
    }

    public static void recordNegativeSimulate(Kind kind, Object adapter) {
        InterfaceTransferContext context = CONTEXT.get();
        if (context.depth <= 0 || context.overflow > 0) {
            return;
        }
        if (context.kinds[context.depth - 1] != kind) {
            return;
        }
        context.negativeAdapters[context.depth - 1].put(adapter, Boolean.TRUE);
    }

    public static boolean wasNegativeSimulate(Kind kind, Object adapter) {
        InterfaceTransferContext context = CONTEXT.get();
        if (context.depth <= 0 || context.overflow > 0) {
            return false;
        }
        if (context.kinds[context.depth - 1] != kind) {
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
                context.kinds[i] = null;
            }
            context.depth = 0;
            context.overflow = 0;
        }
        return leaked;
    }
}
