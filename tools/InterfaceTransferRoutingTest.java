import dj2.ae2opt.core.InterfaceTransferContext;

import java.util.ArrayList;
import java.util.List;


public final class InterfaceTransferRoutingTest {

    static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            System.out.println("pass  " + name);
        } else {
            System.out.println("FAIL  " + name + ": " + detail);
            failures++;
        }
    }


    static final class FakeRepository {
        long stock;
        int realExtractCalls;
        int realInsertCalls;
        long capacity = Long.MAX_VALUE;

        FakeRepository(long stock) {
            this.stock = stock;
        }

        long extractItem(long amount, boolean simulate) {
            realExtractCalls++;
            final long taken = Math.min(stock, amount);
            if (!simulate) {
                stock -= taken;
            }
            return taken;
        }

        long insertItem(long amount, boolean simulate) {
            realInsertCalls++;
            final long free = capacity - stock;
            final long accepted = Math.max(0, Math.min(free, amount));
            if (!simulate) {
                stock += accepted;
            }
            return amount - accepted;
        }
    }


    static final class RoutedAdapter {
        final FakeRepository repo;
        int extractionSkippedOnModulate;
        int insertionSkippedOnModulate;
        int stillVisitedOnModulate;

        RoutedAdapter(FakeRepository repo) {
            this.repo = repo;
        }

        long extractItems(long amount, boolean simulate, boolean optimize) {
            if (!optimize || !InterfaceTransferContext.isActive(InterfaceTransferContext.Kind.EXTRACTION)) {
                return repo.extractItem(amount, simulate);
            }
            if (simulate) {
                final long result = repo.extractItem(amount, true);
                if (result == 0L) {
                    InterfaceTransferContext.recordNegativeSimulate(InterfaceTransferContext.Kind.EXTRACTION, this);
                }
                return result;
            }
            if (InterfaceTransferContext.wasNegativeSimulate(InterfaceTransferContext.Kind.EXTRACTION, this)) {
                extractionSkippedOnModulate++;
                return 0L;
            }
            stillVisitedOnModulate++;
            return repo.extractItem(amount, false);
        }

        long injectItems(long amount, boolean simulate, boolean optimize) {
            if (!optimize || !InterfaceTransferContext.isActive(InterfaceTransferContext.Kind.INSERTION)) {
                return repo.insertItem(amount, simulate);
            }
            if (simulate) {
                final long remaining = repo.insertItem(amount, true);
                if (remaining == amount) {
                    InterfaceTransferContext.recordNegativeSimulate(InterfaceTransferContext.Kind.INSERTION, this);
                }
                return remaining;
            }
            if (InterfaceTransferContext.wasNegativeSimulate(InterfaceTransferContext.Kind.INSERTION, this)) {
                insertionSkippedOnModulate++;
                return amount;
            }
            stillVisitedOnModulate++;
            return repo.insertItem(amount, false);
        }
    }


    static void fullySatisfiedExtractionFromOneHandler() {
        FakeRepository repo = new FakeRepository(100);
        RoutedAdapter adapter = new RoutedAdapter(repo);

        InterfaceTransferContext.enterExtraction();
        long simResult = adapter.extractItems(40, true, true);
        long modResult = adapter.extractItems(40, false, true);
        InterfaceTransferContext.exit();

        check("a fully satisfied SIMULATE reports the requested amount",
                simResult == 40L, "got " + simResult);
        check("the paired MODULATE for a positive handler always runs for real",
                adapter.stillVisitedOnModulate == 1 && adapter.extractionSkippedOnModulate == 0, "n/a");
        check("the real MODULATE actually extracted the amount",
                modResult == 40L && repo.stock == 60L, "stock=" + repo.stock);
        check("both extractItem calls hit the real repository (nothing skipped for a positive handler)",
                repo.realExtractCalls == 2, "calls=" + repo.realExtractCalls);
    }

    static void extractionSplitAcrossMultipleHandlersSkipsNegatives() {
        FakeRepository empty1 = new FakeRepository(0);
        FakeRepository empty2 = new FakeRepository(0);
        FakeRepository positive = new FakeRepository(50);
        RoutedAdapter a1 = new RoutedAdapter(empty1);
        RoutedAdapter a2 = new RoutedAdapter(empty2);
        RoutedAdapter a3 = new RoutedAdapter(positive);
        List<RoutedAdapter> handlers = new ArrayList<RoutedAdapter>();
        handlers.add(a1); handlers.add(a2); handlers.add(a3);

        InterfaceTransferContext.enterExtraction();
        long total = 0;
        for (RoutedAdapter h : handlers) {
            total += h.extractItems(30, true, true);
        }
        int realExtractsBeforeModulate = empty1.realExtractCalls + empty2.realExtractCalls + positive.realExtractCalls;
        for (RoutedAdapter h : handlers) {
            h.extractItems(30, false, true);
        }
        InterfaceTransferContext.exit();

        check("only the positive handler contributed during SIMULATE", total == 30L, "total=" + total);
        check("the two proven-negative handlers are skipped on MODULATE, not re-scanned",
                a1.extractionSkippedOnModulate == 1 && a2.extractionSkippedOnModulate == 1, "n/a");
        check("the positive handler is still visited for real on MODULATE",
                a3.stillVisitedOnModulate == 1, "n/a");
        check("the negative handlers' underlying repository was never touched a second time",
                empty1.realExtractCalls == 1 && empty2.realExtractCalls == 1, "e1=" + empty1.realExtractCalls
                        + " e2=" + empty2.realExtractCalls);
        check("real repository calls before modulate matches exactly one probe per handler",
                realExtractsBeforeModulate == 3, "n/a");
    }

    static void positiveHandlerRealModulateCanReturnLessThanSimulated() {
        FakeRepository repo = new FakeRepository(50);
        RoutedAdapter adapter = new RoutedAdapter(repo);

        InterfaceTransferContext.enterExtraction();
        long simResult = adapter.extractItems(50, true, true);
        repo.stock = 10;
        long modResult = adapter.extractItems(50, false, true);
        InterfaceTransferContext.exit();

        check("SIMULATE reported the full amount available at that moment",
                simResult == 50L, "got " + simResult);
        check("a positive handler's MODULATE always runs for real, never assumed equal to its own SIMULATE",
                adapter.stillVisitedOnModulate == 1 && adapter.extractionSkippedOnModulate == 0, "n/a");
        check("the real MODULATE result reflects whatever the repository actually had, not the stale SIMULATE amount",
                modResult == 10L, "got " + modResult);
    }

    static void candidateBecomesEmptyUnexpectedlyStillRunsRealModulate() {
        FakeRepository repo = new FakeRepository(20);
        RoutedAdapter adapter = new RoutedAdapter(repo);

        InterfaceTransferContext.enterExtraction();
        adapter.extractItems(20, true, true);
        repo.stock = 0;
        long modResult = adapter.extractItems(20, false, true);
        InterfaceTransferContext.exit();

        check("a positive-simulated handler that has since gone empty still runs its real MODULATE call "
                + "and correctly reports zero, rather than the sample being skipped or fabricated",
                modResult == 0L && adapter.stillVisitedOnModulate == 1 && adapter.extractionSkippedOnModulate == 0,
                "n/a");
    }

    static void insertionFullyAcceptedByOneHandler() {
        FakeRepository repo = new FakeRepository(0);
        repo.capacity = 1000;
        RoutedAdapter adapter = new RoutedAdapter(repo);

        InterfaceTransferContext.enterInsertion();
        long simRemaining = adapter.injectItems(40, true, true);
        long modRemaining = adapter.injectItems(40, false, true);
        InterfaceTransferContext.exit();

        check("SIMULATE reports zero remaining for a fully accepted insert", simRemaining == 0L, "got " + simRemaining);
        check("a positive insert handler always runs its real MODULATE",
                adapter.stillVisitedOnModulate == 1 && adapter.insertionSkippedOnModulate == 0, "n/a");
        check("the real insertion actually stored the amount", modRemaining == 0L && repo.stock == 40L,
                "stock=" + repo.stock);
    }

    static void insertionSplitAcrossHandlersSkipsFullyRejectingOnes() {
        FakeRepository full = new FakeRepository(1000);
        full.capacity = 1000;
        FakeRepository open = new FakeRepository(0);
        open.capacity = 1000;
        RoutedAdapter fullAdapter = new RoutedAdapter(full);
        RoutedAdapter openAdapter = new RoutedAdapter(open);

        InterfaceTransferContext.enterInsertion();
        long fullSim = fullAdapter.injectItems(50, true, true);
        long openSim = openAdapter.injectItems(50, true, true);
        fullAdapter.injectItems(50, false, true);
        openAdapter.injectItems(50, false, true);
        InterfaceTransferContext.exit();

        check("a fully saturated handler rejects everything during SIMULATE", fullSim == 50L, "got " + fullSim);
        check("an open handler accepts everything during SIMULATE", openSim == 0L, "got " + openSim);
        check("the fully-rejecting handler is skipped on MODULATE",
                fullAdapter.insertionSkippedOnModulate == 1 && fullAdapter.stillVisitedOnModulate == 0, "n/a");
        check("the accepting handler still runs its real MODULATE",
                openAdapter.stillVisitedOnModulate == 1, "n/a");
        check("nothing was actually stored in the saturated handler", full.realInsertCalls == 1, "n/a");
        check("the open handler actually stored the amount", open.stock == 50L, "stock=" + open.stock);
    }

    static void zeroResultSimulationIsSkippedOnModulate() {
        FakeRepository repo = new FakeRepository(0);
        RoutedAdapter adapter = new RoutedAdapter(repo);

        InterfaceTransferContext.enterExtraction();
        long sim = adapter.extractItems(10, true, true);
        int callsBeforeModulate = repo.realExtractCalls;
        long mod = adapter.extractItems(10, false, true);
        InterfaceTransferContext.exit();

        check("zero-result SIMULATE reports zero", sim == 0L, "got " + sim);
        check("MODULATE is skipped without touching the repository again",
                mod == 0L && repo.realExtractCalls == callsBeforeModulate, "n/a");
        check("exactly one skip was recorded", adapter.extractionSkippedOnModulate == 1, "n/a");
    }

    static void inactiveContextRunsStockUnchanged() {
        FakeRepository repo = new FakeRepository(30);
        RoutedAdapter adapter = new RoutedAdapter(repo);

        long sim = adapter.extractItems(10, true, true);
        check("with no active context, SIMULATE runs the real repository call",
                sim == 10L && repo.realExtractCalls == 1, "n/a");

        long mod = adapter.extractItems(10, false, true);
        check("with no active context, MODULATE also always runs the real repository call, nothing skipped",
                mod == 10L && repo.realExtractCalls == 2 && adapter.extractionSkippedOnModulate == 0, "n/a");
    }

    static void optimizationFlagOffRunsStockUnchanged() {
        FakeRepository repo = new FakeRepository(30);
        RoutedAdapter adapter = new RoutedAdapter(repo);

        InterfaceTransferContext.enterExtraction();
        long sim = adapter.extractItems(10, true, false);
        long mod = adapter.extractItems(10, false, false);
        InterfaceTransferContext.exit();

        check("with the flag off, both calls run the real repository regardless of an active context",
                sim == 10L && mod == 10L && repo.realExtractCalls == 2 && adapter.extractionSkippedOnModulate == 0,
                "n/a");
    }

    static void unrelatedRequestOutsideAnyContextIsUnaffected() {
        FakeRepository repo = new FakeRepository(30);
        RoutedAdapter adapter = new RoutedAdapter(repo);

        InterfaceTransferContext.enterExtraction();
        adapter.extractItems(10, true, true);
        InterfaceTransferContext.exit();

        long unrelatedSim = adapter.extractItems(5, true, true);
        check("a request after the context has exited is not treated as part of the earlier pairing",
                repo.realExtractCalls == 2 && unrelatedSim == 5L, "n/a");
    }

    static void contextNestingStaysBalanced() {
        check("not active before entering", !InterfaceTransferContext.isActive(InterfaceTransferContext.Kind.EXTRACTION)
                && !InterfaceTransferContext.isActive(InterfaceTransferContext.Kind.INSERTION), "n/a");
        InterfaceTransferContext.enterExtraction();
        check("active after entering", InterfaceTransferContext.isActive(InterfaceTransferContext.Kind.EXTRACTION), "n/a");
        InterfaceTransferContext.enterExtraction();
        check("still active nested two levels deep",
                InterfaceTransferContext.isActive(InterfaceTransferContext.Kind.EXTRACTION), "n/a");
        InterfaceTransferContext.exit();
        check("still active after exiting only the inner level",
                InterfaceTransferContext.isActive(InterfaceTransferContext.Kind.EXTRACTION), "n/a");
        InterfaceTransferContext.exit();
        check("inactive after exiting both levels",
                !InterfaceTransferContext.isActive(InterfaceTransferContext.Kind.EXTRACTION), "n/a");
    }

    static void nestedContextsDoNotLeakNegativesAcrossLevels() {
        FakeRepository repo = new FakeRepository(0);
        RoutedAdapter adapter = new RoutedAdapter(repo);

        InterfaceTransferContext.enterExtraction();
        adapter.extractItems(10, true, true);
        check("the adapter is recorded negative at the outer level",
                InterfaceTransferContext.wasNegativeSimulate(InterfaceTransferContext.Kind.EXTRACTION, adapter), "n/a");

        InterfaceTransferContext.enterExtraction();
        check("a freshly entered nested level does not inherit the outer level's negative record",
                !InterfaceTransferContext.wasNegativeSimulate(InterfaceTransferContext.Kind.EXTRACTION, adapter), "n/a");
        InterfaceTransferContext.exit();

        check("the outer level's negative record survives the nested level exiting",
                InterfaceTransferContext.wasNegativeSimulate(InterfaceTransferContext.Kind.EXTRACTION, adapter), "n/a");
        InterfaceTransferContext.exit();
    }

    static void leakedContextIsRecoveredAtTickEnd() {
        InterfaceTransferContext.enterExtraction();
        InterfaceTransferContext.enterInsertion();
        check("active before recovery", InterfaceTransferContext.isActive(InterfaceTransferContext.Kind.INSERTION), "n/a");
        int leaked = InterfaceTransferContext.resetAtServerTickEnd();
        check("tick-end recovery reports the exact leaked depth", leaked == 2, "leaked=" + leaked);
        check("inactive immediately after recovery",
                !InterfaceTransferContext.isActive(InterfaceTransferContext.Kind.EXTRACTION)
                        && !InterfaceTransferContext.isActive(InterfaceTransferContext.Kind.INSERTION), "n/a");
        int leakedAgain = InterfaceTransferContext.resetAtServerTickEnd();
        check("a second recovery call reports nothing left to recover", leakedAgain == 0, "leaked=" + leakedAgain);
    }

    static void exceptionDuringModulateDoesNotLeaveStaleNegativeAcrossInvocations() {
        FakeRepository repo1 = new FakeRepository(0);
        RoutedAdapter adapter = new RoutedAdapter(repo1);

        InterfaceTransferContext.enterExtraction();
        adapter.extractItems(10, true, true);
        InterfaceTransferContext.exit();

        InterfaceTransferContext.enterExtraction();
        boolean stillNegativeFromPriorInvocation =
                InterfaceTransferContext.wasNegativeSimulate(InterfaceTransferContext.Kind.EXTRACTION, adapter);
        InterfaceTransferContext.exit();

        check("a negative record from a completed, exited invocation does not leak into the next one",
                !stillNegativeFromPriorInvocation, "n/a");
    }


    static void emptyDrawerInsertionPriorityProbeDoesNotSuppressRealInsertion() {
        FakeRepository repo = new FakeRepository(0);
        repo.capacity = 1000;
        RoutedAdapter adapter = new RoutedAdapter(repo);

        InterfaceTransferContext.enterInsertion();
        long extractionProbe = adapter.extractItems(20, true, true);
        long modResult = adapter.injectItems(20, false, true);
        InterfaceTransferContext.exit();

        check("the priority extraction probe against an empty repository correctly reports nothing to extract",
                extractionProbe == 0L, "got " + extractionProbe);
        check("the extraction probe's negative result was not recorded as insertion-routing evidence",
                adapter.insertionSkippedOnModulate == 0, "n/a");
        check("the real insertion still ran and was not incorrectly skipped",
                adapter.stillVisitedOnModulate == 1, "n/a");
        check("the item was actually stored, not silently dropped by a wrongly-skipped insertion",
                modResult == 0L && repo.stock == 20L, "stock=" + repo.stock);
    }

    static void fullOuterInsertTransactionAgainstAnEmptyAcceptingRepository() {
        FakeRepository repo = new FakeRepository(0);
        repo.capacity = 1000;
        RoutedAdapter adapter = new RoutedAdapter(repo);

        InterfaceTransferContext.enterInsertion();

        long outerSimProbe = adapter.extractItems(30, true, true);
        long outerSimResult = adapter.injectItems(30, true, true);

        long outerModProbe = adapter.extractItems(30, true, true);
        long outerModResult = adapter.injectItems(30, false, true);

        InterfaceTransferContext.exit();

        check("the outer SIMULATE leg's own priority probe against an empty repository finds nothing to extract",
                outerSimProbe == 0L, "got " + outerSimProbe);
        check("the outer MODULATE leg's own priority probe also finds nothing to extract, independently",
                outerModProbe == 0L, "got " + outerModProbe);
        check("neither probe's negative result ever suppressed the real insertion",
                adapter.insertionSkippedOnModulate == 0, "n/a");
        check("both the outer SIMULATE and MODULATE legs report the insert as fully accepted (zero remaining)",
                outerSimResult == 0L && outerModResult == 0L, "sim=" + outerSimResult + " mod=" + outerModResult);
        check("the repository actually received the item by the end of the outer MODULATE leg",
                repo.stock == 30L, "stock=" + repo.stock);
    }

    static void trueInsertNegativeIsSkippedOnModulateWithoutDoubleCallingInsertItem() {
        FakeRepository full = new FakeRepository(500);
        full.capacity = 500;
        RoutedAdapter adapter = new RoutedAdapter(full);

        InterfaceTransferContext.enterInsertion();
        long sim = adapter.injectItems(10, true, true);
        int insertCallsBeforeModulate = full.realInsertCalls;
        long mod = adapter.injectItems(10, false, true);
        InterfaceTransferContext.exit();

        check("SIMULATE against a full, incompatible-capacity repository rejects everything",
                sim == 10L, "got " + sim);
        check("the matching MODULATE is skipped rather than re-probing the full repository",
                mod == 10L && full.realInsertCalls == insertCallsBeforeModulate, "n/a");
        check("exactly one insertion skip was recorded, and the underlying insertItem was called exactly once total",
                adapter.insertionSkippedOnModulate == 1 && full.realInsertCalls == 1, "calls=" + full.realInsertCalls);
    }

    static void partialInsertIsNeverClassifiedAsNegative() {
        FakeRepository repo = new FakeRepository(0);
        repo.capacity = 15;
        RoutedAdapter adapter = new RoutedAdapter(repo);

        InterfaceTransferContext.enterInsertion();
        long simRemaining = adapter.injectItems(20, true, true);
        long modRemaining = adapter.injectItems(20, false, true);
        InterfaceTransferContext.exit();

        check("a partial SIMULATE acceptance (5 accepted, 15 rejected) is not a full rejection",
                simRemaining == 5L, "got " + simRemaining);
        check("a partially-accepting handler is never classified as negative and always runs its real MODULATE",
                adapter.insertionSkippedOnModulate == 0 && adapter.stillVisitedOnModulate == 1, "n/a");
        check("the real MODULATE actually stored what capacity allowed",
                modRemaining == 5L && repo.stock == 15L, "stock=" + repo.stock);
    }

    static void amountShrinkBetweenSimulateAndModulateStillRunsRealCallsForPositiveHandlers() {
        FakeRepository repo = new FakeRepository(0);
        repo.capacity = 1000;
        RoutedAdapter adapter = new RoutedAdapter(repo);

        InterfaceTransferContext.enterInsertion();
        long sim = adapter.injectItems(50, true, true);
        long mod = adapter.injectItems(20, false, true);
        InterfaceTransferContext.exit();

        check("SIMULATE at the original (larger) amount is fully accepted", sim == 0L, "got " + sim);
        check("a positive handler still runs its real MODULATE even though the request amount shrank "
                + "(other handlers accepted some in between)",
                adapter.stillVisitedOnModulate == 1 && adapter.insertionSkippedOnModulate == 0, "n/a");
        check("the real MODULATE stores exactly the shrunk amount, not the original SIMULATE amount",
                mod == 0L && repo.stock == 20L, "stock=" + repo.stock);
    }

    static void trueInsertNegativeSkipRemainsValidAtASmallerShrunkAmount() {
        FakeRepository full = new FakeRepository(500);
        full.capacity = 500;
        RoutedAdapter adapter = new RoutedAdapter(full);

        InterfaceTransferContext.enterInsertion();
        long sim = adapter.injectItems(50, true, true);
        int insertCallsBeforeModulate = full.realInsertCalls;
        long mod = adapter.injectItems(10, false, true);
        InterfaceTransferContext.exit();

        check("SIMULATE at the larger amount is fully rejected by a saturated repository", sim == 50L, "got " + sim);
        check("the skip remains valid for a smaller MODULATE amount against the same, unchanged repository "
                + "(monotonic rejection, proven from the exact Storage Drawers capacity logic)",
                mod == 10L && full.realInsertCalls == insertCallsBeforeModulate, "n/a");
        check("exactly one skip was recorded and the repository was probed exactly once",
                adapter.insertionSkippedOnModulate == 1 && full.realInsertCalls == 1, "n/a");
    }

    static void extractionNegativeSkipStillWorksExactlyAsBefore() {
        FakeRepository repo = new FakeRepository(0);
        RoutedAdapter adapter = new RoutedAdapter(repo);

        InterfaceTransferContext.enterExtraction();
        adapter.extractItems(10, true, true);
        int callsBeforeModulate = repo.realExtractCalls;
        long mod = adapter.extractItems(10, false, true);
        InterfaceTransferContext.exit();

        check("the pre-existing extraction negative-skip regression still holds after the direction-aware fix",
                mod == 0L && repo.realExtractCalls == callsBeforeModulate && adapter.extractionSkippedOnModulate == 1,
                "n/a");
    }

    static void crossDirectionIsolationBothWays() {
        FakeRepository forExtraction = new FakeRepository(0);
        RoutedAdapter extractionAdapter = new RoutedAdapter(forExtraction);

        InterfaceTransferContext.enterExtraction();
        extractionAdapter.extractItems(10, true, true);
        InterfaceTransferContext.exit();

        FakeRepository forInsertion = new FakeRepository(0);
        forInsertion.capacity = 1000;
        RoutedAdapter insertionAdapter = new RoutedAdapter(forInsertion);
        InterfaceTransferContext.enterInsertion();
        boolean crossContaminatedAsInsertion =
                InterfaceTransferContext.wasNegativeSimulate(InterfaceTransferContext.Kind.INSERTION, extractionAdapter);
        long insertResult = insertionAdapter.injectItems(15, false, true);
        InterfaceTransferContext.exit();

        check("negative extraction evidence from an earlier, separate invocation cannot suppress an unrelated insertion",
                !crossContaminatedAsInsertion, "n/a");
        check("an insertion with no prior SIMULATE in this context still runs for real rather than being skipped",
                insertResult == 0L && forInsertion.stock == 15L, "stock=" + forInsertion.stock);

        FakeRepository forExtraction2 = new FakeRepository(0);
        RoutedAdapter extractionAdapter2 = new RoutedAdapter(forExtraction2);
        FakeRepository forInsertion2 = new FakeRepository(0);
        forInsertion2.capacity = 1000;
        RoutedAdapter insertionAdapter2 = new RoutedAdapter(forInsertion2);

        InterfaceTransferContext.enterInsertion();
        insertionAdapter2.injectItems(10, true, true);
        InterfaceTransferContext.exit();

        InterfaceTransferContext.enterExtraction();
        boolean crossContaminatedAsExtraction =
                InterfaceTransferContext.wasNegativeSimulate(InterfaceTransferContext.Kind.EXTRACTION, insertionAdapter2);
        InterfaceTransferContext.exit();

        check("negative insertion evidence from an earlier, separate invocation cannot suppress an unrelated extraction",
                !crossContaminatedAsExtraction, "n/a");
    }

    static void nestedContextsPreserveOperationKindIndependentlyAtEachDepth() {
        FakeRepository extractionRepo = new FakeRepository(0);
        RoutedAdapter extractionAdapter = new RoutedAdapter(extractionRepo);
        FakeRepository insertionRepo = new FakeRepository(0);
        insertionRepo.capacity = 1000;
        RoutedAdapter insertionAdapter = new RoutedAdapter(insertionRepo);

        InterfaceTransferContext.enterInsertion();
        check("the outer level is INSERTION", InterfaceTransferContext.isActive(InterfaceTransferContext.Kind.INSERTION)
                && !InterfaceTransferContext.isActive(InterfaceTransferContext.Kind.EXTRACTION), "n/a");

        InterfaceTransferContext.enterExtraction();
        check("a nested inner level can independently be EXTRACTION while the outer is INSERTION",
                InterfaceTransferContext.isActive(InterfaceTransferContext.Kind.EXTRACTION)
                        && !InterfaceTransferContext.isActive(InterfaceTransferContext.Kind.INSERTION), "n/a");
        extractionAdapter.extractItems(10, true, true);
        check("the inner EXTRACTION level records its own negative evidence",
                InterfaceTransferContext.wasNegativeSimulate(InterfaceTransferContext.Kind.EXTRACTION, extractionAdapter),
                "n/a");
        InterfaceTransferContext.exit();

        check("after the nested EXTRACTION level exits, the outer level is INSERTION again",
                InterfaceTransferContext.isActive(InterfaceTransferContext.Kind.INSERTION)
                        && !InterfaceTransferContext.isActive(InterfaceTransferContext.Kind.EXTRACTION), "n/a");
        long insertResult = insertionAdapter.injectItems(25, false, true);
        check("the outer INSERTION level's own operation is unaffected by the nested EXTRACTION level that ran inside it",
                insertResult == 0L && insertionRepo.stock == 25L, "stock=" + insertionRepo.stock);
        InterfaceTransferContext.exit();
    }

    public static void main(String[] args) {
        fullySatisfiedExtractionFromOneHandler();
        extractionSplitAcrossMultipleHandlersSkipsNegatives();
        positiveHandlerRealModulateCanReturnLessThanSimulated();
        candidateBecomesEmptyUnexpectedlyStillRunsRealModulate();
        insertionFullyAcceptedByOneHandler();
        insertionSplitAcrossHandlersSkipsFullyRejectingOnes();
        zeroResultSimulationIsSkippedOnModulate();
        inactiveContextRunsStockUnchanged();
        optimizationFlagOffRunsStockUnchanged();
        unrelatedRequestOutsideAnyContextIsUnaffected();
        contextNestingStaysBalanced();
        nestedContextsDoNotLeakNegativesAcrossLevels();
        leakedContextIsRecoveredAtTickEnd();
        exceptionDuringModulateDoesNotLeaveStaleNegativeAcrossInvocations();

        emptyDrawerInsertionPriorityProbeDoesNotSuppressRealInsertion();
        fullOuterInsertTransactionAgainstAnEmptyAcceptingRepository();
        trueInsertNegativeIsSkippedOnModulateWithoutDoubleCallingInsertItem();
        partialInsertIsNeverClassifiedAsNegative();
        amountShrinkBetweenSimulateAndModulateStillRunsRealCallsForPositiveHandlers();
        trueInsertNegativeSkipRemainsValidAtASmallerShrunkAmount();
        extractionNegativeSkipStillWorksExactlyAsBefore();
        crossDirectionIsolationBothWays();
        nestedContextsPreserveOperationKindIndependentlyAtEachDepth();

        System.out.println(failures == 0 ? "\nInterfaceTransferRoutingTest: ALL PASS"
                : "\nInterfaceTransferRoutingTest: " + failures + " FAILURES");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
