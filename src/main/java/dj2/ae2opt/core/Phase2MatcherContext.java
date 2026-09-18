package dj2.ae2opt.core;


public final class Phase2MatcherContext {

    private static final int MAX_DEPTH = 8;

    private static final ThreadLocal<Phase2MatcherContext> CONTEXT = new ThreadLocal<Phase2MatcherContext>() {
        @Override
        protected Phase2MatcherContext initialValue() {
            return new Phase2MatcherContext();
        }
    };

    private final int[] matcherCalls = new int[MAX_DEPTH];
    private final int[] candidateCalls = new int[MAX_DEPTH];
    private int depth;
    private int overflow;

    private Phase2MatcherContext() {
    }

    public static void enter() {
        Phase2MatcherContext context = CONTEXT.get();
        if (context.depth >= MAX_DEPTH) {
            context.overflow++;
            return;
        }
        if (context.depth > 0) {
            Diagnostics.phase2ContextNested();
        }
        int slot = context.depth++;
        context.matcherCalls[slot] = 0;
        context.candidateCalls[slot] = 0;
        Diagnostics.phase2MatcherSampleEntered();
    }

    public static boolean active() {
        return CONTEXT.get().depth > 0;
    }

    public static void recordMatcherCall(boolean candidateMember) {
        Phase2MatcherContext context = CONTEXT.get();
        if (context.depth <= 0) {
            return;
        }
        int slot = context.depth - 1;
        context.matcherCalls[slot]++;
        if (candidateMember) {
            context.candidateCalls[slot]++;
        }
    }

    public static void exit() {
        Phase2MatcherContext context = CONTEXT.get();
        if (context.overflow > 0) {
            context.overflow--;
            return;
        }
        if (context.depth <= 0) {
            return;
        }
        int slot = --context.depth;
        Diagnostics.phase2MatcherSampleCompleted(context.matcherCalls[slot], context.candidateCalls[slot]);
    }

    public static int resetAtServerTickEnd() {
        Phase2MatcherContext context = CONTEXT.get();
        int leaked = context.depth;
        if (leaked != 0 || context.overflow != 0) {
            context.depth = 0;
            context.overflow = 0;
        }
        return leaked;
    }
}
