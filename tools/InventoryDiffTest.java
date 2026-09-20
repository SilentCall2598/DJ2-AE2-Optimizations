import dj2.ae2opt.core.InventoryDiff;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


public final class InventoryDiffTest {

    static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            System.out.println("pass  " + name);
        } else {
            System.out.println("FAIL  " + name + ": " + detail);
            failures++;
        }
    }


    static final class Entry {
        final long key;
        long size;

        Entry(long key, long size) {
            this.key = key;
            this.size = size;
        }

        Entry copy() {
            return new Entry(this.key, this.size);
        }

        Entry withSize(long newSize) {
            return new Entry(this.key, newSize);
        }

        public String toString() {
            return "(" + this.key + ":" + this.size + ")";
        }
    }


    static final class FakeItemList {
        final Map<Long, Entry> records = new LinkedHashMap<Long, Entry>();

        void add(Entry e) {
            final Entry existing = this.records.get(e.key);
            if (existing != null) {
                existing.size += e.size;
            } else {
                this.records.put(e.key, e.copy());
            }
        }

        Entry findPrecise(Entry query) {
            return this.records.get(query.key);
        }

        List<Entry> meaningfulEntries() {
            final List<Entry> out = new ArrayList<Entry>();
            for (Entry e : this.records.values()) {
                if (e.size != 0L) {
                    out.add(e);
                }
            }
            return out;
        }
    }


    static List<Entry> newDiff(FakeItemList oldList, FakeItemList newList) {
        final List<Entry> changes = new ArrayList<Entry>();
        InventoryDiff.compute(
                newList.meaningfulEntries(), oldList::findPrecise,
                oldList.meaningfulEntries(), newList::findPrecise,
                e -> e.size,
                (identity, delta) -> identity.withSize(delta),
                changes);
        return changes;
    }

    static List<Entry> stockDiff(FakeItemList oldList, FakeItemList newList) {
        final Map<Long, Entry> merged = new LinkedHashMap<Long, Entry>();
        for (Entry e : oldList.meaningfulEntries()) {
            merged.put(e.key, new Entry(e.key, -e.size));
        }
        for (Entry e : newList.meaningfulEntries()) {
            final Entry existing = merged.get(e.key);
            if (existing != null) {
                existing.size += e.size;
            } else {
                merged.put(e.key, new Entry(e.key, e.size));
            }
        }
        final List<Entry> changes = new ArrayList<Entry>();
        for (Entry e : merged.values()) {
            if (e.size != 0L) {
                changes.add(e);
            }
        }
        return changes;
    }

    static Map<Long, Long> byKey(List<Entry> entries) {
        final Map<Long, Long> out = new LinkedHashMap<Long, Long>();
        for (Entry e : entries) {
            final Long previous = out.get(e.key);
            out.put(e.key, (previous == null ? 0L : previous) + e.size);
        }
        return out;
    }

    static boolean sameNetEffect(List<Entry> a, List<Entry> b) {
        return byKey(a).equals(byKey(b));
    }


    static void increaseIsReported() {
        final FakeItemList oldList = new FakeItemList();
        oldList.add(new Entry(1, 5));
        final FakeItemList newList = new FakeItemList();
        newList.add(new Entry(1, 8));
        final List<Entry> changes = newDiff(oldList, newList);
        check("a count increase is reported as a positive delta",
                sameNetEffect(changes, stockDiff(oldList, newList)), changes.toString());
        check("the increase delta value is exactly +3",
                byKey(changes).get(1L) == 3L, changes.toString());
    }

    static void decreaseIsReported() {
        final FakeItemList oldList = new FakeItemList();
        oldList.add(new Entry(1, 5));
        final FakeItemList newList = new FakeItemList();
        newList.add(new Entry(1, 2));
        final List<Entry> changes = newDiff(oldList, newList);
        check("a count decrease is reported as a negative delta",
                sameNetEffect(changes, stockDiff(oldList, newList)), changes.toString());
        check("the decrease delta value is exactly -3",
                byKey(changes).get(1L) == -3L, changes.toString());
    }

    static void newPrototypeIsReported() {
        final FakeItemList oldList = new FakeItemList();
        final FakeItemList newList = new FakeItemList();
        newList.add(new Entry(1, 5));
        final List<Entry> changes = newDiff(oldList, newList);
        check("a brand new value is reported as its full amount",
                sameNetEffect(changes, stockDiff(oldList, newList)), changes.toString());
        check("the new-value delta is exactly +5",
                byKey(changes).get(1L) == 5L, changes.toString());
    }

    static void removedPrototypeIsReported() {
        final FakeItemList oldList = new FakeItemList();
        oldList.add(new Entry(1, 5));
        final FakeItemList newList = new FakeItemList();
        final List<Entry> changes = newDiff(oldList, newList);
        check("a fully removed value is reported as its full negative amount",
                sameNetEffect(changes, stockDiff(oldList, newList)), changes.toString());
        check("the removal delta is exactly -5",
                byKey(changes).get(1L) == -5L, changes.toString());
    }

    static void unchangedProducesNoDelta() {
        final FakeItemList oldList = new FakeItemList();
        oldList.add(new Entry(1, 5));
        final FakeItemList newList = new FakeItemList();
        newList.add(new Entry(1, 5));
        final List<Entry> changes = newDiff(oldList, newList);
        check("an unchanged value produces no changes entry", changes.isEmpty(), changes.toString());
    }

    static void bootstrapFromEmptyCacheTreatsEverythingAsNew() {
        final FakeItemList oldList = new FakeItemList();
        final FakeItemList newList = new FakeItemList();
        newList.add(new Entry(1, 5));
        newList.add(new Entry(2, 7));
        final List<Entry> changes = newDiff(oldList, newList);
        check("a rebuild from an empty cache reports every value as new",
                sameNetEffect(changes, stockDiff(oldList, newList)), changes.toString());
        check("both new values are present with their full amounts",
                byKey(changes).get(1L) == 5L && byKey(changes).get(2L) == 7L, changes.toString());
    }

    static void mixedAddChangeRemoveUnchangedInOnePoll() {
        final FakeItemList oldList = new FakeItemList();
        oldList.add(new Entry(1, 5));
        oldList.add(new Entry(2, 10));
        oldList.add(new Entry(3, 4));
        final FakeItemList newList = new FakeItemList();
        newList.add(new Entry(1, 5));
        newList.add(new Entry(2, 6));
        newList.add(new Entry(4, 9));
        final List<Entry> changes = newDiff(oldList, newList);
        check("a mixed poll (unchanged, decreased, removed, added) matches the stock oracle",
                sameNetEffect(changes, stockDiff(oldList, newList)), changes.toString());
        final Map<Long, Long> byKey = byKey(changes);
        check("value 1 (unchanged) is absent from changes", !byKey.containsKey(1L), changes.toString());
        check("value 2 (decreased) is -4", byKey.get(2L) == -4L, changes.toString());
        check("value 3 (removed) is -4", byKey.get(3L) == -4L, changes.toString());
        check("value 4 (added) is +9", byKey.get(4L) == 9L, changes.toString());
    }

    static void modulateMutationBetweenPollsIsNotDoubleCounted() {
        final FakeItemList oldList = new FakeItemList();
        oldList.add(new Entry(1, 10));

        oldList.findPrecise(new Entry(1, 0)).size -= 3;

        final FakeItemList newList = new FakeItemList();
        newList.add(new Entry(1, 7));

        final List<Entry> changes = newDiff(oldList, newList);
        check("a value already adjusted in place by an instant MODULATE mutation "
                + "produces no further change when the poll confirms the same total",
                changes.isEmpty(), changes.toString());
    }

    static void modulateMutationToExactZeroLeavesALingeringEntry() {
        final FakeItemList oldList = new FakeItemList();
        oldList.add(new Entry(1, 5));

        oldList.findPrecise(new Entry(1, 0)).size = 0L;

        final FakeItemList newList = new FakeItemList();

        final List<Entry> changes = newDiff(oldList, newList);
        check("a value already zeroed in place by an instant MODULATE mutation "
                + "is not reported again when the drawer disappears from the poll",
                changes.isEmpty(), changes.toString());
    }

    static void partialModulateThenFurtherRealChangeReportsOnlyTheRemainder() {
        final FakeItemList oldList = new FakeItemList();
        oldList.add(new Entry(1, 10));

        oldList.findPrecise(new Entry(1, 0)).size -= 3;

        final FakeItemList newList = new FakeItemList();
        newList.add(new Entry(1, 4));

        final List<Entry> changes = newDiff(oldList, newList);
        check("a poll after a partial instant MODULATE mutation reports only the additional change",
                sameNetEffect(changes, stockDiff(oldList, newList)), changes.toString());
        check("the additional reported delta is exactly -3 (7 -> 4)",
                byKey(changes).get(1L) == -3L, changes.toString());
    }

    static void duplicateEquivalentPrototypesShareOneAggregatedValue() {
        final FakeItemList oldList = new FakeItemList();
        oldList.add(new Entry(1, 5));
        oldList.add(new Entry(1, 3));

        final FakeItemList newList = new FakeItemList();
        newList.add(new Entry(1, 8));
        newList.add(new Entry(1, 6));

        final List<Entry> changes = newDiff(oldList, newList);
        check("two drawer identities sharing one AE value are already merged before diffing, "
                + "so the diff reports one correct net delta, not a double-counted one",
                sameNetEffect(changes, stockDiff(oldList, newList)), changes.toString());
        check("the combined delta is exactly +6 (8 old -> 14 new)",
                byKey(changes).get(1L) == 6L, changes.toString());
    }


    static final class Drawer {
        long key;
        long count;
        boolean present = true;

        Drawer(long key, long count) {
            this.key = key;
            this.count = count;
        }
    }

    static final class SimulatedInventoryCache {
        FakeItemList currentlyCached = new FakeItemList();

        List<Entry> update(List<Drawer> drawers, boolean useNewAlgorithm) {
            final FakeItemList currentlyOnStorage = new FakeItemList();
            for (Drawer d : drawers) {
                if (!d.present || d.count <= 0) {
                    continue;
                }
                currentlyOnStorage.add(new Entry(d.key, d.count));
            }

            final List<Entry> changes = useNewAlgorithm
                    ? newDiff(this.currentlyCached, currentlyOnStorage)
                    : stockDiff(this.currentlyCached, currentlyOnStorage);

            this.currentlyCached = currentlyOnStorage;
            return changes;
        }

        void applyModulateExtraction(long key, long amount) {
            final Entry cached = this.currentlyCached.findPrecise(new Entry(key, 0));
            if (cached != null) {
                cached.size -= amount;
            }
        }
    }


    static void integrationAcrossTicksMatchesStockOracleExactly() {
        final SimulatedInventoryCache newSide = new SimulatedInventoryCache();
        final SimulatedInventoryCache stockSide = new SimulatedInventoryCache();

        final List<Drawer> drawersA = new ArrayList<Drawer>();
        final Drawer alpha = new Drawer(100, 10);
        final Drawer beta = new Drawer(100, 5);
        final Drawer gamma = new Drawer(200, 20);
        drawersA.add(alpha);
        drawersA.add(beta);
        drawersA.add(gamma);

        final List<Entry> tick1New = newSide.update(drawersA, true);
        final List<Entry> tick1Stock = stockSide.update(drawersA, false);
        check("tick 1 (bootstrap with duplicate/equivalent prototypes) matches the stock oracle",
                sameNetEffect(tick1New, tick1Stock), tick1New + " vs " + tick1Stock);

        alpha.count = 12;
        newSide.applyModulateExtraction(200, 4);
        stockSide.applyModulateExtraction(200, 4);
        gamma.count = 16;

        final List<Entry> tick2New = newSide.update(drawersA, true);
        final List<Entry> tick2Stock = stockSide.update(drawersA, false);
        check("tick 2 (increase plus an instant MODULATE extraction already reconciled by the poll) "
                + "matches the stock oracle",
                sameNetEffect(tick2New, tick2Stock), tick2New + " vs " + tick2Stock);

        beta.present = false;

        final List<Entry> tick3New = newSide.update(drawersA, true);
        final List<Entry> tick3Stock = stockSide.update(drawersA, false);
        check("tick 3 (one of two duplicate drawers emptied) matches the stock oracle",
                sameNetEffect(tick3New, tick3Stock), tick3New + " vs " + tick3Stock);

        alpha.present = false;

        final List<Entry> tick4New = newSide.update(drawersA, true);
        final List<Entry> tick4Stock = stockSide.update(drawersA, false);
        check("tick 4 (the value's last remaining drawer emptied) matches the stock oracle",
                sameNetEffect(tick4New, tick4Stock), tick4New + " vs " + tick4Stock);

        final Drawer delta = new Drawer(300, 40);
        drawersA.add(delta);

        final List<Entry> tick5New = newSide.update(drawersA, true);
        final List<Entry> tick5Stock = stockSide.update(drawersA, false);
        check("tick 5 (a brand new prototype joins) matches the stock oracle",
                sameNetEffect(tick5New, tick5Stock), tick5New + " vs " + tick5Stock);
    }


    static void randomizedTickSweepMatchesStockOracle() {
        final Random rnd = new Random(20260919L);
        int totalTicks = 0;
        int mismatches = 0;

        for (int trial = 0; trial < 200; trial++) {
            final SimulatedInventoryCache newSide = new SimulatedInventoryCache();
            final SimulatedInventoryCache stockSide = new SimulatedInventoryCache();
            final List<Drawer> drawers = new ArrayList<Drawer>();
            final int drawerCount = 2 + rnd.nextInt(10);
            for (int i = 0; i < drawerCount; i++) {
                drawers.add(new Drawer(100 + rnd.nextInt(6), 1 + rnd.nextInt(50)));
            }

            for (int tick = 0; tick < 30; tick++) {
                for (Drawer d : drawers) {
                    final int action = rnd.nextInt(10);
                    if (action < 3) {
                        d.count = Math.max(0, d.count + (rnd.nextInt(21) - 10));
                    } else if (action < 5) {
                        d.present = !d.present;
                    } else if (action == 5 && d.present && d.count > 0) {
                        final long extraction = 1 + rnd.nextInt((int) Math.min(d.count, 10));
                        newSide.applyModulateExtraction(d.key, extraction);
                        stockSide.applyModulateExtraction(d.key, extraction);
                        d.count -= extraction;
                    }
                }

                final List<Entry> newChanges = newSide.update(drawers, true);
                final List<Entry> stockChanges = stockSide.update(drawers, false);
                totalTicks++;
                if (!sameNetEffect(newChanges, stockChanges)) {
                    mismatches++;
                }
                if (!newSide.currentlyCached.records.keySet()
                        .equals(stockSide.currentlyCached.records.keySet())) {
                    mismatches++;
                }
            }
        }

        check("the new diff matches the stock oracle across " + totalTicks
                + " randomized ticks with mixed increases, decreases, removals, rejoins, "
                + "and instant MODULATE extractions", mismatches == 0, mismatches + " mismatched ticks");
    }


    public static void main(String[] args) {
        increaseIsReported();
        decreaseIsReported();
        newPrototypeIsReported();
        removedPrototypeIsReported();
        unchangedProducesNoDelta();
        bootstrapFromEmptyCacheTreatsEverythingAsNew();
        mixedAddChangeRemoveUnchangedInOnePoll();
        modulateMutationBetweenPollsIsNotDoubleCounted();
        modulateMutationToExactZeroLeavesALingeringEntry();
        partialModulateThenFurtherRealChangeReportsOnlyTheRemainder();
        duplicateEquivalentPrototypesShareOneAggregatedValue();
        integrationAcrossTicksMatchesStockOracleExactly();
        randomizedTickSweepMatchesStockOracle();

        System.out.println(failures == 0 ? "\nInventoryDiffTest: ALL PASS"
                : "\nInventoryDiffTest: " + failures + " FAILURES");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
