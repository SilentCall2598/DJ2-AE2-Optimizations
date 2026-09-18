import dj2.ae2opt.core.ExtractionContext;
import dj2.ae2opt.core.ExtractionPairTracker;


public final class ExtractionPairingTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Object source = new Object();
        Object type = new Object();
        Object otherSource = new Object();
        Object otherType = new Object();


        ExtractionPairTracker t = new ExtractionPairTracker();
        check(t.observeSimulate(source, type, 64, 64, 10, 101)
                == ExtractionPairTracker.SIM_FIRST, "first powered simulate");
        int pair = t.observeModulate(source, type, 64, 10, 101);
        check(ExtractionPairTracker.isPoweredPair(pair), "same powered context is high-confidence");
        check(ExtractionPairTracker.amountRelation(pair)
                == ExtractionPairTracker.MOD_MATCHES_SIM_RESULT, "exact result pair");


        t = new ExtractionPairTracker();
        t.observeSimulate(source, type, 100, 80, 20, 102);
        pair = t.observeModulate(source, type, 80, 20, 102);
        check(ExtractionPairTracker.isPoweredPair(pair), "partial pair keeps powered context");
        check(ExtractionPairTracker.amountRelation(pair)
                == ExtractionPairTracker.MOD_MATCHES_SIM_RESULT, "partial simulate result pair");


        t = new ExtractionPairTracker();
        t.observeSimulate(source, type, 100, 100, 30, 103);
        pair = t.observeModulate(source, type, 75, 30, 103);
        check(ExtractionPairTracker.isPoweredPair(pair), "energy-adjusted pair keeps context");
        check(ExtractionPairTracker.amountRelation(pair)
                == ExtractionPairTracker.MOD_MATCHES_TYPE_DIFFERENT_AMOUNT, "energy-adjusted amount");


        t = new ExtractionPairTracker();
        t.observeSimulate(source, type, 64, 64, 40, 201);
        check(t.observeModulate(source, type, 64, 40, 202)
                == ExtractionPairTracker.MOD_NO_MATCH, "different powered invocations cannot pair");


        t = new ExtractionPairTracker();
        t.observeSimulate(source, type, 64, 64, 45, 0);
        pair = t.observeModulate(source, type, 64, 45, 0);
        check(!ExtractionPairTracker.isPoweredPair(pair), "ambient pair is heuristic");
        check(ExtractionPairTracker.amountRelation(pair)
                == ExtractionPairTracker.MOD_MATCHES_SIM_RESULT, "ambient heuristic amount relation");

        t = new ExtractionPairTracker();
        t.observeSimulate(source, type, 64, 64, 50, 301);
        check(t.observeSimulate(source, type, 64, 64, 50, 301)
                == ExtractionPairTracker.SIM_REPEAT_SAME_REQUEST, "repeated simulate in same context");

        t = new ExtractionPairTracker();
        t.observeSimulate(source, type, 64, 64, 55, 401);
        check(t.observeSimulate(source, type, 64, 64, 55, 402)
                == ExtractionPairTracker.SIM_SUPERSEDED,
                "same request in another powered invocation is not a repeat");

        t = new ExtractionPairTracker();
        t.observeSimulate(source, type, 64, 64, 60, 0);
        check(t.observeSimulate(otherSource, type, 64, 64, 60, 0)
                == ExtractionPairTracker.SIM_SUPERSEDED, "source change supersedes");

        t = new ExtractionPairTracker();
        t.observeSimulate(source, type, 64, 64, 70, 0);
        check(t.expireBefore(71), "old tick expires");
        check(t.observeModulate(source, type, 64, 71, 0)
                == ExtractionPairTracker.MOD_NO_MATCH, "expired sim cannot pair");

        t = new ExtractionPairTracker();
        t.observeSimulate(source, type, 64, 64, 80, 0);
        check(t.observeModulate(source, otherType, 64, 80, 0)
                == ExtractionPairTracker.MOD_NO_MATCH, "other type cannot pair");


        long outer = ExtractionContext.enterPoweredExtraction();
        check(outer != 0L && ExtractionContext.currentPoweredExtractionId() == outer,
                "outer context active");
        long inner = ExtractionContext.enterPoweredExtraction();
        check(inner != 0L && inner != outer && ExtractionContext.currentPoweredExtractionId() == inner,
                "nested context active");
        ExtractionContext.exitPoweredExtraction();
        check(ExtractionContext.currentPoweredExtractionId() == outer, "outer context restored");
        ExtractionContext.exitPoweredExtraction();
        check(ExtractionContext.currentPoweredExtractionId() == 0L, "context cleared");

        ExtractionContext.enterPoweredExtraction();
        check(ExtractionContext.resetAtServerTickEnd() == 1, "tick-end leak reset");
        check(ExtractionContext.currentPoweredExtractionId() == 0L, "leaked context cleared");

        System.out.println("ExtractionPairingTest: ALL PASS");
    }
}
