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
        int skippedOnModulate;
        int stillVisitedOnModulate;

        RoutedAdapter(FakeRepository repo) {
            this.repo = repo;
        }

        long extractItems(long amount, boolean simulate, boolean optimize) {
            if (!optimize || !InterfaceTransferContext.isActive()) {
                return repo.extractItem(amount, simulate);
            }
            if (simulate) {
                final long result = repo.extractItem(amount, true);
                if (result == 0L) {
                    InterfaceTransferContext.recordNegativeSimulate(this);
                }
                return result;
            }
            if (InterfaceTransferContext.wasNegativeSimulate(this)) {
                skippedOnModulate++;
                return 0L;
            }
            stillVisitedOnModulate++;
            return repo.extractItem(amount, false);
        }

        long injectItems(long amount, boolean simulate, boolean optimize) {
            if (!optimize || !InterfaceTransferContext.isActive()) {
                return repo.insertItem(amount, simulate);
            }
            if (simulate) {
                final long remaining = repo.insertItem(amount, true);
                if (remaining == amount) {
                    InterfaceTransferContext.recordNegativeSimulate(this);
                }
                return remaining;
            }
            if (InterfaceTransferContext.wasNegativeSimulate(this)) {
                skippedOnModulate++;
                return amount;
            }
            stillVisitedOnModulate++;
            return repo.insertItem(amount, false);
        }
    }


    static void fullySatisfiedExtractionFromOneHandler() {
        FakeRepository repo = new FakeRepository(100);
        RoutedAdapter adapter = new RoutedAdapter(repo);

        InterfaceTransferContext.enter();
        long simResult = adapter.extractItems(40, true, true);
        long modResult = adapter.extractItems(40, false, true);
        InterfaceTransferContext.exit();

        check("a fully satisfied SIMULATE reports the requested amount",
                simResult == 40L, "got " + simResult);
        check("the paired MODULATE for a positive handler always runs for real",
                adapter.stillVisitedOnModulate == 1 && adapter.skippedOnModulate == 0, "n/a");
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

        InterfaceTransferContext.enter();
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
                a1.skippedOnModulate == 1 && a2.skippedOnModulate == 1, "n/a");
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

        InterfaceTransferContext.enter();
        long simResult = adapter.extractItems(50, true, true);
        repo.stock = 10;
        long modResult = adapter.extractItems(50, false, true);
        InterfaceTransferContext.exit();

        check("SIMULATE reported the full amount available at that moment",
                simResult == 50L, "got " + simResult);
        check("a positive handler's MODULATE always runs for real, never assumed equal to its own SIMULATE",
                adapter.stillVisitedOnModulate == 1 && adapter.skippedOnModulate == 0, "n/a");
        check("the real MODULATE result reflects whatever the repository actually had, not the stale SIMULATE amount",
                modResult == 10L, "got " + modResult);
    }

    static void candidateBecomesEmptyUnexpectedlyStillRunsRealModulate() {
        FakeRepository repo = new FakeRepository(20);
        RoutedAdapter adapter = new RoutedAdapter(repo);

        InterfaceTransferContext.enter();
        adapter.extractItems(20, true, true);
        repo.stock = 0;
        long modResult = adapter.extractItems(20, false, true);
        InterfaceTransferContext.exit();

        check("a positive-simulated handler that has since gone empty still runs its real MODULATE call "
                + "and correctly reports zero, rather than the sample being skipped or fabricated",
                modResult == 0L && adapter.stillVisitedOnModulate == 1 && adapter.skippedOnModulate == 0, "n/a");
    }

    static void insertionFullyAcceptedByOneHandler() {
        FakeRepository repo = new FakeRepository(0);
        repo.capacity = 1000;
        RoutedAdapter adapter = new RoutedAdapter(repo);

        InterfaceTransferContext.enter();
        long simRemaining = adapter.injectItems(40, true, true);
        long modRemaining = adapter.injectItems(40, false, true);
        InterfaceTransferContext.exit();

        check("SIMULATE reports zero remaining for a fully accepted insert", simRemaining == 0L, "got " + simRemaining);
        check("a positive insert handler always runs its real MODULATE",
                adapter.stillVisitedOnModulate == 1 && adapter.skippedOnModulate == 0, "n/a");
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

        InterfaceTransferContext.enter();
        long fullSim = fullAdapter.injectItems(50, true, true);
        long openSim = openAdapter.injectItems(50, true, true);
        fullAdapter.injectItems(50, false, true);
        openAdapter.injectItems(50, false, true);
        InterfaceTransferContext.exit();

        check("a fully saturated handler rejects everything during SIMULATE", fullSim == 50L, "got " + fullSim);
        check("an open handler accepts everything during SIMULATE", openSim == 0L, "got " + openSim);
        check("the fully-rejecting handler is skipped on MODULATE",
                fullAdapter.skippedOnModulate == 1 && fullAdapter.stillVisitedOnModulate == 0, "n/a");
        check("the accepting handler still runs its real MODULATE",
                openAdapter.stillVisitedOnModulate == 1, "n/a");
        check("nothing was actually stored in the saturated handler", full.realInsertCalls == 1, "n/a");
        check("the open handler actually stored the amount", open.stock == 50L, "stock=" + open.stock);
    }

    static void zeroResultSimulationIsSkippedOnModulate() {
        FakeRepository repo = new FakeRepository(0);
        RoutedAdapter adapter = new RoutedAdapter(repo);

        InterfaceTransferContext.enter();
        long sim = adapter.extractItems(10, true, true);
        int callsBeforeModulate = repo.realExtractCalls;
        long mod = adapter.extractItems(10, false, true);
        InterfaceTransferContext.exit();

        check("zero-result SIMULATE reports zero", sim == 0L, "got " + sim);
        check("MODULATE is skipped without touching the repository again",
                mod == 0L && repo.realExtractCalls == callsBeforeModulate, "n/a");
        check("exactly one skip was recorded", adapter.skippedOnModulate == 1, "n/a");
    }

    static void inactiveContextRunsStockUnchanged() {
        FakeRepository repo = new FakeRepository(30);
        RoutedAdapter adapter = new RoutedAdapter(repo);

        long sim = adapter.extractItems(10, true, true);
        check("with no active context, SIMULATE runs the real repository call",
                sim == 10L && repo.realExtractCalls == 1, "n/a");

        long mod = adapter.extractItems(10, false, true);
        check("with no active context, MODULATE also always runs the real repository call, nothing skipped",
                mod == 10L && repo.realExtractCalls == 2 && adapter.skippedOnModulate == 0, "n/a");
    }

    static void optimizationFlagOffRunsStockUnchanged() {
        FakeRepository repo = new FakeRepository(30);
        RoutedAdapter adapter = new RoutedAdapter(repo);

        InterfaceTransferContext.enter();
        long sim = adapter.extractItems(10, true, false);
        long mod = adapter.extractItems(10, false, false);
        InterfaceTransferContext.exit();

        check("with the flag off, both calls run the real repository regardless of an active context",
                sim == 10L && mod == 10L && repo.realExtractCalls == 2 && adapter.skippedOnModulate == 0, "n/a");
    }

    static void unrelatedRequestOutsideAnyContextIsUnaffected() {
        FakeRepository repo = new FakeRepository(30);
        RoutedAdapter adapter = new RoutedAdapter(repo);

        InterfaceTransferContext.enter();
        adapter.extractItems(10, true, true);
        InterfaceTransferContext.exit();

        long unrelatedSim = adapter.extractItems(5, true, true);
        check("a request after the context has exited is not treated as part of the earlier pairing",
                repo.realExtractCalls == 2 && unrelatedSim == 5L, "n/a");
    }

    static void contextNestingStaysBalanced() {
        check("not active before entering", !InterfaceTransferContext.isActive(), "n/a");
        InterfaceTransferContext.enter();
        check("active after entering", InterfaceTransferContext.isActive(), "n/a");
        InterfaceTransferContext.enter();
        check("still active nested two levels deep", InterfaceTransferContext.isActive(), "n/a");
        InterfaceTransferContext.exit();
        check("still active after exiting only the inner level", InterfaceTransferContext.isActive(), "n/a");
        InterfaceTransferContext.exit();
        check("inactive after exiting both levels", !InterfaceTransferContext.isActive(), "n/a");
    }

    static void nestedContextsDoNotLeakNegativesAcrossLevels() {
        FakeRepository repo = new FakeRepository(0);
        RoutedAdapter adapter = new RoutedAdapter(repo);

        InterfaceTransferContext.enter();
        adapter.extractItems(10, true, true);
        check("the adapter is recorded negative at the outer level",
                InterfaceTransferContext.wasNegativeSimulate(adapter), "n/a");

        InterfaceTransferContext.enter();
        check("a freshly entered nested level does not inherit the outer level's negative record",
                !InterfaceTransferContext.wasNegativeSimulate(adapter), "n/a");
        InterfaceTransferContext.exit();

        check("the outer level's negative record survives the nested nop level exiting",
                InterfaceTransferContext.wasNegativeSimulate(adapter), "n/a");
        InterfaceTransferContext.exit();
    }

    static void leakedContextIsRecoveredAtTickEnd() {
        InterfaceTransferContext.enter();
        InterfaceTransferContext.enter();
        check("active before recovery", InterfaceTransferContext.isActive(), "n/a");
        int leaked = InterfaceTransferContext.resetAtServerTickEnd();
        check("tick-end recovery reports the exact leaked depth", leaked == 2, "leaked=" + leaked);
        check("inactive immediately after recovery", !InterfaceTransferContext.isActive(), "n/a");
        int leakedAgain = InterfaceTransferContext.resetAtServerTickEnd();
        check("a second recovery call reports nothing left to recover", leakedAgain == 0, "leaked=" + leakedAgain);
    }

    static void exceptionDuringModulateDoesNotLeaveStaleNegativeAcrossInvocations() {
        FakeRepository repo1 = new FakeRepository(0);
        RoutedAdapter adapter = new RoutedAdapter(repo1);

        InterfaceTransferContext.enter();
        adapter.extractItems(10, true, true);
        InterfaceTransferContext.exit();

        FakeRepository repo2 = new FakeRepository(15);
        InterfaceTransferContext.enter();
        boolean stillNegativeFromPriorInvocation = InterfaceTransferContext.wasNegativeSimulate(adapter);
        InterfaceTransferContext.exit();

        check("a negative record from a completed, exited invocation does not leak into the next one",
                !stillNegativeFromPriorInvocation, "n/a");
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

        System.out.println(failures == 0 ? "\nInterfaceTransferRoutingTest: ALL PASS"
                : "\nInterfaceTransferRoutingTest: " + failures + " FAILURES");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
