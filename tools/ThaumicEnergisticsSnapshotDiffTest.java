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
        TOPOLOGY_CHANGED, FAIL_OPEN_NO_BASELINE, DELTA_POSTED, NO_DELTA
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

        Outcome onNeighborChanged(Object currentContainer, FakeEssentiaList currentSnapshot) {
            if (currentContainer != this.lastConnectedContainer) {
                this.lastConnectedContainer = currentContainer;
                this.lastSnapshot = null;
                return new Outcome(OutcomeKind.TOPOLOGY_CHANGED, null);
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
        randomizedSweepNeverDiffsAcrossATopologyChange();

        System.out.println(failures == 0 ? "\nThaumicEnergisticsSnapshotDiffTest: ALL PASS"
                : "\nThaumicEnergisticsSnapshotDiffTest: " + failures + " FAILURES");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
