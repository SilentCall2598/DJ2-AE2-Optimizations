import dj2.ae2opt.core.EssentiaSimulationContext;
import dj2.ae2opt.core.InventoryDiff;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


public final class ThaumicEnergisticsSnapshotDiffTest {

    static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            System.out.println("pass  " + name);
        } else {
            System.out.println("FAIL  " + name + ": " + detail);
            failures++;
        }
    }


    static final class FakeEssentiaStack {
        final int aspectId;
        long amount;

        FakeEssentiaStack(int aspectId, long amount) {
            this.aspectId = aspectId;
            this.amount = amount;
        }

        FakeEssentiaStack copy() {
            return new FakeEssentiaStack(this.aspectId, this.amount);
        }

        FakeEssentiaStack withAmount(long newAmount) {
            return new FakeEssentiaStack(this.aspectId, newAmount);
        }

        public String toString() {
            return "(" + this.aspectId + ":" + this.amount + ")";
        }
    }


    static final class FakeEssentiaList {
        final Map<Integer, FakeEssentiaStack> records = new LinkedHashMap<Integer, FakeEssentiaStack>();

        void add(FakeEssentiaStack s) {
            final FakeEssentiaStack existing = this.records.get(s.aspectId);
            if (existing != null) {
                existing.amount += s.amount;
            } else {
                this.records.put(s.aspectId, s.copy());
            }
        }

        FakeEssentiaStack findPrecise(FakeEssentiaStack query) {
            return this.records.get(query.aspectId);
        }

        List<FakeEssentiaStack> meaningfulEntries() {
            final List<FakeEssentiaStack> out = new ArrayList<FakeEssentiaStack>();
            for (FakeEssentiaStack s : this.records.values()) {
                if (s.amount != 0L) {
                    out.add(s);
                }
            }
            return out;
        }
    }


    enum OutcomeKind {
        TOPOLOGY_CHANGED, FAIL_OPEN_NO_BASELINE, DELTA_POSTED, NO_DELTA, SIMULATION_SUPPRESSED,
        FAIL_OPEN_GUARD_UNAVAILABLE
    }

    static final class Outcome {
        final OutcomeKind kind;
        final List<FakeEssentiaStack> deltas;

        Outcome(OutcomeKind kind, List<FakeEssentiaStack> deltas) {
            this.kind = kind;
            this.deltas = deltas;
        }
    }


    static final class FakeBus {
        Object lastConnectedContainer;
        FakeEssentiaList lastSnapshot;
        boolean simulationActive;
        boolean guardApplied = true;

        void triggerFullUpdate() {
            this.lastSnapshot = null;
        }

        Outcome onNeighborChanged(Object currentContainer, FakeEssentiaList currentSnapshot) {
            if (this.simulationActive) {
                return new Outcome(OutcomeKind.SIMULATION_SUPPRESSED, null);
            }

            if (currentContainer != this.lastConnectedContainer) {
                this.lastConnectedContainer = currentContainer;
                this.lastSnapshot = null;
                return new Outcome(OutcomeKind.TOPOLOGY_CHANGED, null);
            }

            if (!this.guardApplied) {
                return new Outcome(OutcomeKind.FAIL_OPEN_GUARD_UNAVAILABLE, null);
            }

            final FakeEssentiaList previousSnapshot = this.lastSnapshot;
            if (previousSnapshot == null) {
                this.lastSnapshot = currentSnapshot;
                return new Outcome(OutcomeKind.FAIL_OPEN_NO_BASELINE, null);
            }

            final List<FakeEssentiaStack> deltas = new ArrayList<FakeEssentiaStack>();
            InventoryDiff.compute(
                    currentSnapshot.meaningfulEntries(), previousSnapshot::findPrecise,
                    previousSnapshot.meaningfulEntries(), currentSnapshot::findPrecise,
                    s -> s.amount,
                    (identity, delta) -> identity.withAmount(delta),
                    deltas);
            this.lastSnapshot = currentSnapshot;
            return deltas.isEmpty() ? new Outcome(OutcomeKind.NO_DELTA, deltas)
                    : new Outcome(OutcomeKind.DELTA_POSTED, deltas);
        }
    }


    static Map<Integer, Long> byAspect(List<FakeEssentiaStack> entries) {
        final Map<Integer, Long> out = new LinkedHashMap<Integer, Long>();
        for (FakeEssentiaStack s : entries) {
            final Long previous = out.get(s.aspectId);
            out.put(s.aspectId, (previous == null ? 0L : previous) + s.amount);
        }
        return out;
    }


    static void firstNotificationAfterAttachIsTopologyChanged() {
        final FakeBus bus = new FakeBus();
        final FakeEssentiaList snapshot = new FakeEssentiaList();
        snapshot.add(new FakeEssentiaStack(1, 10));
        final Outcome outcome = bus.onNeighborChanged("containerA", snapshot);
        check("the first notification against a freshly attached container is a topology change",
                outcome.kind == OutcomeKind.TOPOLOGY_CHANGED, String.valueOf(outcome.kind));
    }

    static void firstSameContainerCallAfterTopologyChangeFailsOpenAndCapturesBaseline() {
        final FakeBus bus = new FakeBus();
        final FakeEssentiaList snapshot1 = new FakeEssentiaList();
        snapshot1.add(new FakeEssentiaStack(1, 10));
        bus.onNeighborChanged("containerA", snapshot1);

        final FakeEssentiaList snapshot2 = new FakeEssentiaList();
        snapshot2.add(new FakeEssentiaStack(1, 12));
        final Outcome outcome = bus.onNeighborChanged("containerA", snapshot2);
        check("the first same-container call with no prior baseline falls open instead of guessing a delta",
                outcome.kind == OutcomeKind.FAIL_OPEN_NO_BASELINE, String.valueOf(outcome.kind));
    }

    static void subsequentSameContainerCallReportsPreciseDelta() {
        final FakeBus bus = new FakeBus();
        final FakeEssentiaList snapshot1 = new FakeEssentiaList();
        snapshot1.add(new FakeEssentiaStack(1, 10));
        bus.onNeighborChanged("containerA", snapshot1);
        bus.onNeighborChanged("containerA", snapshot1);

        final FakeEssentiaList snapshot2 = new FakeEssentiaList();
        snapshot2.add(new FakeEssentiaStack(1, 16));
        final Outcome outcome = bus.onNeighborChanged("containerA", snapshot2);
        check("a genuine content change against a known baseline reports a precise delta",
                outcome.kind == OutcomeKind.DELTA_POSTED, String.valueOf(outcome.kind));
        check("the reported delta is exactly +6 (10 -> 16)",
                byAspect(outcome.deltas).get(1) == 6L, outcome.deltas.toString());
    }

    static void unchangedContentReportsNoDelta() {
        final FakeBus bus = new FakeBus();
        final FakeEssentiaList snapshot = new FakeEssentiaList();
        snapshot.add(new FakeEssentiaStack(1, 10));
        bus.onNeighborChanged("containerA", snapshot);
        bus.onNeighborChanged("containerA", snapshot);

        final Outcome outcome = bus.onNeighborChanged("containerA", snapshot);
        check("a notification with no content change reports no delta at all",
                outcome.kind == OutcomeKind.NO_DELTA, String.valueOf(outcome.kind));
        check("the empty delta list is actually empty", outcome.deltas.isEmpty(), outcome.deltas.toString());
    }

    static void emptiedContainerReportsFullNegativeDelta() {
        final FakeBus bus = new FakeBus();
        final FakeEssentiaList snapshot1 = new FakeEssentiaList();
        snapshot1.add(new FakeEssentiaStack(1, 10));
        bus.onNeighborChanged("containerA", snapshot1);
        bus.onNeighborChanged("containerA", snapshot1);

        final FakeEssentiaList snapshot2 = new FakeEssentiaList();
        final Outcome outcome = bus.onNeighborChanged("containerA", snapshot2);
        check("draining an aspect fully reports its full negative amount",
                outcome.kind == OutcomeKind.DELTA_POSTED, String.valueOf(outcome.kind));
        check("the drained delta is exactly -10",
                byAspect(outcome.deltas).get(1) == -10L, outcome.deltas.toString());
    }

    static void newAspectAddedReportsPositiveDelta() {
        final FakeBus bus = new FakeBus();
        final FakeEssentiaList snapshot1 = new FakeEssentiaList();
        snapshot1.add(new FakeEssentiaStack(1, 10));
        bus.onNeighborChanged("containerA", snapshot1);
        bus.onNeighborChanged("containerA", snapshot1);

        final FakeEssentiaList snapshot2 = new FakeEssentiaList();
        snapshot2.add(new FakeEssentiaStack(1, 10));
        snapshot2.add(new FakeEssentiaStack(2, 5));
        final Outcome outcome = bus.onNeighborChanged("containerA", snapshot2);
        check("a brand new aspect appearing is reported as its full amount",
                outcome.kind == OutcomeKind.DELTA_POSTED, String.valueOf(outcome.kind));
        check("only the new aspect is reported, unchanged aspect 1 is absent",
                byAspect(outcome.deltas).size() == 1 && byAspect(outcome.deltas).get(2) == 5L,
                outcome.deltas.toString());
    }

    static void topologyChangeToADifferentContainerResetsBaseline() {
        final FakeBus bus = new FakeBus();
        final FakeEssentiaList snapshotA = new FakeEssentiaList();
        snapshotA.add(new FakeEssentiaStack(1, 10));
        bus.onNeighborChanged("containerA", snapshotA);
        bus.onNeighborChanged("containerA", snapshotA);

        final FakeEssentiaList snapshotB = new FakeEssentiaList();
        snapshotB.add(new FakeEssentiaStack(1, 999));
        final Outcome switched = bus.onNeighborChanged("containerB", snapshotB);
        check("connecting a different container is a topology change, not a 989-unit delta",
                switched.kind == OutcomeKind.TOPOLOGY_CHANGED, String.valueOf(switched.kind));

        final Outcome afterSwitch = bus.onNeighborChanged("containerB", snapshotB);
        check("the call right after a topology change falls open on the new container too, "
                + "instead of diffing against the old container's stale snapshot",
                afterSwitch.kind == OutcomeKind.FAIL_OPEN_NO_BASELINE, String.valueOf(afterSwitch.kind));
    }

    static void reconnectingTheSameContainerAfterATripDoesNotLeakTheOldBaseline() {
        final FakeBus bus = new FakeBus();
        final FakeEssentiaList snapshotA1 = new FakeEssentiaList();
        snapshotA1.add(new FakeEssentiaStack(1, 10));
        bus.onNeighborChanged("containerA", snapshotA1);
        bus.onNeighborChanged("containerA", snapshotA1);

        final FakeEssentiaList snapshotB = new FakeEssentiaList();
        snapshotB.add(new FakeEssentiaStack(1, 3));
        bus.onNeighborChanged("containerB", snapshotB);

        final FakeEssentiaList snapshotA2 = new FakeEssentiaList();
        snapshotA2.add(new FakeEssentiaStack(1, 10));
        final Outcome outcome = bus.onNeighborChanged("containerA", snapshotA2);
        check("reconnecting the original container after a topology trip is itself a topology "
                + "change (identity-based, not content-based), never a stale diff against A's old snapshot",
                outcome.kind == OutcomeKind.TOPOLOGY_CHANGED, String.valueOf(outcome.kind));
    }


    static void simulateInjectRoundTripIsFullySuppressed() {
        final FakeBus bus = new FakeBus();
        final FakeEssentiaList baseline = new FakeEssentiaList();
        baseline.add(new FakeEssentiaStack(1, 100));
        bus.onNeighborChanged("containerA", baseline);
        bus.onNeighborChanged("containerA", baseline);

        bus.simulationActive = true;

        final FakeEssentiaList temporarilyAdded = new FakeEssentiaList();
        temporarilyAdded.add(new FakeEssentiaStack(1, 110));
        final Outcome addOutcome = bus.onNeighborChanged("containerA", temporarilyAdded);
        check("a SIMULATE injectItems' temporary addToContainer mutation is fully suppressed, "
                + "not reported as an incremental delta",
                addOutcome.kind == OutcomeKind.SIMULATION_SUPPRESSED, String.valueOf(addOutcome.kind));
        check("a suppressed notification posts no deltas at all", addOutcome.deltas == null, "n/a");

        final FakeEssentiaList undone = new FakeEssentiaList();
        undone.add(new FakeEssentiaStack(1, 100));
        final Outcome undoOutcome = bus.onNeighborChanged("containerA", undone);
        check("the matching takeFromContainer undo notification is also fully suppressed",
                undoOutcome.kind == OutcomeKind.SIMULATION_SUPPRESSED, String.valueOf(undoOutcome.kind));

        bus.simulationActive = false;

        check("the persistent baseline was never advanced to the temporary simulated state, still 100",
                bus.lastSnapshot.findPrecise(new FakeEssentiaStack(1, 0)).amount == 100L, "n/a");

        final FakeEssentiaList realChange = new FakeEssentiaList();
        realChange.add(new FakeEssentiaStack(1, 105));
        final Outcome realOutcome = bus.onNeighborChanged("containerA", realChange);
        check("the next real external change after the suppressed round trip reports its own exact delta",
                realOutcome.kind == OutcomeKind.DELTA_POSTED, String.valueOf(realOutcome.kind));
        check("the real delta is exactly +5 (100 -> 105), uncontaminated by the suppressed simulation",
                byAspect(realOutcome.deltas).get(1) == 5L, realOutcome.deltas.toString());
    }

    static void simulationContextNestingStaysBalanced() {
        check("not active before entering", !EssentiaSimulationContext.isActive(), "n/a");
        EssentiaSimulationContext.enter();
        check("active after entering", EssentiaSimulationContext.isActive(), "n/a");
        EssentiaSimulationContext.enter();
        check("still active while nested two levels deep", EssentiaSimulationContext.isActive(), "n/a");
        EssentiaSimulationContext.exit();
        check("still active after exiting only the inner level", EssentiaSimulationContext.isActive(), "n/a");
        EssentiaSimulationContext.exit();
        check("inactive after exiting both levels", !EssentiaSimulationContext.isActive(), "n/a");
    }

    static void simulationContextLeakIsRecoveredAtTickEnd() {
        EssentiaSimulationContext.enter();
        EssentiaSimulationContext.enter();
        check("active before recovery", EssentiaSimulationContext.isActive(), "n/a");
        final int leaked = EssentiaSimulationContext.resetAtServerTickEnd();
        check("tick-end recovery reports the exact leaked depth", leaked == 2, "leaked=" + leaked);
        check("inactive immediately after recovery", !EssentiaSimulationContext.isActive(), "n/a");
        final int leakedAgain = EssentiaSimulationContext.resetAtServerTickEnd();
        check("a second recovery call reports nothing left to recover", leakedAgain == 0,
                "leaked=" + leakedAgain);
    }

    static void simulationContextOverflowStaysBalanced() {
        for (int i = 0; i < 8; i++) {
            EssentiaSimulationContext.enter();
        }
        check("still active at the depth cap", EssentiaSimulationContext.isActive(), "n/a");
        EssentiaSimulationContext.enter();
        EssentiaSimulationContext.enter();
        check("still active past the depth cap, the overflow is absorbed rather than lost",
                EssentiaSimulationContext.isActive(), "n/a");
        for (int i = 0; i < 10; i++) {
            EssentiaSimulationContext.exit();
        }
        check("exiting exactly as many times as entered, including overflow, returns to inactive",
                !EssentiaSimulationContext.isActive(), "n/a");
    }


    static void fullUpdateInvalidatesBaselineWhenContentBecomesHidden() {
        final FakeBus bus = new FakeBus();
        final FakeEssentiaList visible = new FakeEssentiaList();
        visible.add(new FakeEssentiaStack(1, 50));
        bus.onNeighborChanged("containerA", visible);
        bus.onNeighborChanged("containerA", visible);

        bus.triggerFullUpdate();

        final FakeEssentiaList nowHidden = new FakeEssentiaList();
        final Outcome outcome = bus.onNeighborChanged("containerA", nowHidden);
        check("the notification right after a legitimate full update (access/storage-filter making "
                + "content invisible) falls open instead of reporting the visibility change as a "
                + "content delta",
                outcome.kind == OutcomeKind.FAIL_OPEN_NO_BASELINE, String.valueOf(outcome.kind));
    }

    static void fullUpdateInvalidatesBaselineWhenContentBecomesVisible() {
        final FakeBus bus = new FakeBus();
        final FakeEssentiaList hidden = new FakeEssentiaList();
        bus.onNeighborChanged("containerA", hidden);
        bus.onNeighborChanged("containerA", hidden);

        bus.triggerFullUpdate();

        final FakeEssentiaList nowVisible = new FakeEssentiaList();
        nowVisible.add(new FakeEssentiaStack(1, 40));
        final Outcome outcome = bus.onNeighborChanged("containerA", nowVisible);
        check("a config change that newly reveals content also falls open after a full update, not "
                + "reporting a phantom delta for content AE2 already learned about through the broad "
                + "refresh",
                outcome.kind == OutcomeKind.FAIL_OPEN_NO_BASELINE, String.valueOf(outcome.kind));
    }

    static void noDuplicateDeltaAfterFullUpdateAndBaselineReestablishes() {
        final FakeBus bus = new FakeBus();
        final FakeEssentiaList before = new FakeEssentiaList();
        before.add(new FakeEssentiaStack(1, 50));
        bus.onNeighborChanged("containerA", before);
        bus.onNeighborChanged("containerA", before);

        bus.triggerFullUpdate();

        final FakeEssentiaList afterConfigChange = new FakeEssentiaList();
        afterConfigChange.add(new FakeEssentiaStack(1, 50));
        final Outcome reestablish = bus.onNeighborChanged("containerA", afterConfigChange);
        check("the call right after a full update establishes a fresh baseline instead of posting "
                + "anything, even though the underlying amount did not actually change",
                reestablish.kind == OutcomeKind.FAIL_OPEN_NO_BASELINE, String.valueOf(reestablish.kind));

        final Outcome next = bus.onNeighborChanged("containerA", afterConfigChange);
        check("incremental mode resumes cleanly on the following call, reporting no delta for "
                + "genuinely unchanged content, not a duplicate of what the broad refresh already "
                + "covered",
                next.kind == OutcomeKind.NO_DELTA, String.valueOf(next.kind));
    }


    static void guardUnavailableFallsOpenWithoutCapturingBaseline() {
        final FakeBus bus = new FakeBus();
        bus.guardApplied = false;
        final FakeEssentiaList content = new FakeEssentiaList();
        content.add(new FakeEssentiaStack(1, 50));

        final Outcome first = bus.onNeighborChanged("containerA", content);
        check("the first notification against a freshly attached container is still a topology "
                + "change regardless of guard availability",
                first.kind == OutcomeKind.TOPOLOGY_CHANGED, String.valueOf(first.kind));

        final Outcome second = bus.onNeighborChanged("containerA", content);
        check("the next same-container notification while the guard is unavailable falls open "
                + "instead of capturing a baseline",
                second.kind == OutcomeKind.FAIL_OPEN_GUARD_UNAVAILABLE, String.valueOf(second.kind));
        check("no baseline was captured while the guard was unavailable",
                bus.lastSnapshot == null, "n/a");

        final Outcome third = bus.onNeighborChanged("containerA", content);
        check("the guard remaining unavailable keeps falling open on every subsequent "
                + "same-container call, not just the first one after it was noticed",
                third.kind == OutcomeKind.FAIL_OPEN_GUARD_UNAVAILABLE, String.valueOf(third.kind));
    }

    static void guardBecomingUnavailableDoesNotMutateAnExistingBaseline() {
        final FakeBus bus = new FakeBus();
        final FakeEssentiaList established = new FakeEssentiaList();
        established.add(new FakeEssentiaStack(1, 50));
        bus.onNeighborChanged("containerA", established);
        bus.onNeighborChanged("containerA", established);

        bus.guardApplied = false;

        final FakeEssentiaList changed = new FakeEssentiaList();
        changed.add(new FakeEssentiaStack(1, 80));
        final Outcome outcome = bus.onNeighborChanged("containerA", changed);
        check("a same-container notification after the guard becomes unavailable falls open "
                + "instead of diffing or advancing the existing baseline",
                outcome.kind == OutcomeKind.FAIL_OPEN_GUARD_UNAVAILABLE, String.valueOf(outcome.kind));
        check("the existing baseline is left exactly as it was (50), neither advanced to the new "
                + "content nor otherwise mutated",
                bus.lastSnapshot.findPrecise(new FakeEssentiaStack(1, 0)).amount == 50L, "n/a");
    }

    static void guardBecomingAvailableLaterResumesIncrementalModeCleanly() {
        final FakeBus bus = new FakeBus();
        bus.guardApplied = false;

        final FakeEssentiaList content1 = new FakeEssentiaList();
        content1.add(new FakeEssentiaStack(1, 30));
        bus.onNeighborChanged("containerA", content1);
        final Outcome whileUnavailable = bus.onNeighborChanged("containerA", content1);
        check("while the guard is unavailable, notifications fall open and capture no baseline",
                whileUnavailable.kind == OutcomeKind.FAIL_OPEN_GUARD_UNAVAILABLE
                        && bus.lastSnapshot == null, String.valueOf(whileUnavailable.kind));

        bus.guardApplied = true;

        final FakeEssentiaList content2 = new FakeEssentiaList();
        content2.add(new FakeEssentiaStack(1, 30));
        final Outcome firstAfterAvailable = bus.onNeighborChanged("containerA", content2);
        check("the first notification after the guard becomes available establishes a fresh "
                + "baseline instead of guessing a delta",
                firstAfterAvailable.kind == OutcomeKind.FAIL_OPEN_NO_BASELINE,
                String.valueOf(firstAfterAvailable.kind));

        final FakeEssentiaList content3 = new FakeEssentiaList();
        content3.add(new FakeEssentiaStack(1, 45));
        final Outcome realChange = bus.onNeighborChanged("containerA", content3);
        check("the next real content change after the baseline is established reports its exact "
                + "signed delta",
                realChange.kind == OutcomeKind.DELTA_POSTED, String.valueOf(realChange.kind));
        check("the delta is exactly +15 (30 -> 45)",
                byAspect(realChange.deltas).get(1) == 15L, realChange.deltas.toString());
    }


    static void randomizedSweepNeverDiffsAcrossATopologyChange() {
        final Random rnd = new Random(20260922L);
        int totalCalls = 0;
        int violations = 0;

        for (int trial = 0; trial < 200; trial++) {
            final FakeBus bus = new FakeBus();
            Object currentContainer = "containerA";
            final FakeEssentiaList content = new FakeEssentiaList();
            content.add(new FakeEssentiaStack(1, 10 + rnd.nextInt(50)));

            bus.onNeighborChanged(currentContainer, content);

            for (int call = 0; call < 40; call++) {
                final boolean topologyChange = rnd.nextInt(10) == 0;
                if (topologyChange) {
                    currentContainer = currentContainer == "containerA" ? "containerB" : "containerA";
                } else {
                    for (FakeEssentiaStack s : content.records.values()) {
                        s.amount = Math.max(0, s.amount + (rnd.nextInt(21) - 10));
                    }
                }
                final Outcome outcome = bus.onNeighborChanged(currentContainer, content);
                totalCalls++;
                if (topologyChange && outcome.kind != OutcomeKind.TOPOLOGY_CHANGED) {
                    violations++;
                }
                if (!topologyChange && outcome.kind == OutcomeKind.TOPOLOGY_CHANGED) {
                    violations++;
                }
            }
        }

        check("across " + totalCalls + " randomized calls, a topology change is reported if and only if "
                + "the connected container identity actually changed", violations == 0,
                violations + " violation(s)");
    }


    public static void main(String[] args) {
        firstNotificationAfterAttachIsTopologyChanged();
        firstSameContainerCallAfterTopologyChangeFailsOpenAndCapturesBaseline();
        subsequentSameContainerCallReportsPreciseDelta();
        unchangedContentReportsNoDelta();
        emptiedContainerReportsFullNegativeDelta();
        newAspectAddedReportsPositiveDelta();
        topologyChangeToADifferentContainerResetsBaseline();
        reconnectingTheSameContainerAfterATripDoesNotLeakTheOldBaseline();
        simulateInjectRoundTripIsFullySuppressed();
        simulationContextNestingStaysBalanced();
        simulationContextLeakIsRecoveredAtTickEnd();
        simulationContextOverflowStaysBalanced();
        fullUpdateInvalidatesBaselineWhenContentBecomesHidden();
        fullUpdateInvalidatesBaselineWhenContentBecomesVisible();
        noDuplicateDeltaAfterFullUpdateAndBaselineReestablishes();
        guardUnavailableFallsOpenWithoutCapturingBaseline();
        guardBecomingUnavailableDoesNotMutateAnExistingBaseline();
        guardBecomingAvailableLaterResumesIncrementalModeCleanly();
        randomizedSweepNeverDiffsAcrossATopologyChange();

        System.out.println(failures == 0 ? "\nThaumicEnergisticsSnapshotDiffTest: ALL PASS"
                : "\nThaumicEnergisticsSnapshotDiffTest: " + failures + " FAILURES");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
