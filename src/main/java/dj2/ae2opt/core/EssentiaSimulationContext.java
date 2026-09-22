package dj2.ae2opt.core;


public final class EssentiaSimulationContext {

    private static final int MAX_DEPTH = 8;

    private static final ThreadLocal<EssentiaSimulationContext> CONTEXT = new ThreadLocal<EssentiaSimulationContext>() {
        @Override
        protected EssentiaSimulationContext initialValue() {
            return new EssentiaSimulationContext();
        }
    };

    private int depth;
    private int overflow;

    private EssentiaSimulationContext() {
    }

    public static void enter() {
        EssentiaSimulationContext context = CONTEXT.get();
        if (context.depth >= MAX_DEPTH) {
            context.overflow++;
            return;
        }
        context.depth++;
    }

    public static void exit() {
        EssentiaSimulationContext context = CONTEXT.get();
        if (context.overflow > 0) {
            context.overflow--;
            return;
        }
        if (context.depth > 0) {
            context.depth--;
        }
    }

    public static boolean isActive() {
        return CONTEXT.get().depth > 0;
    }

    public static int resetAtServerTickEnd() {
        EssentiaSimulationContext context = CONTEXT.get();
        int leaked = context.depth + context.overflow;
        if (leaked != 0) {
            context.depth = 0;
            context.overflow = 0;
        }
        return leaked;
    }
}
