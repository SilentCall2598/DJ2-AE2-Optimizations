import dj2.ae2opt.core.ItemHandlerExtractionStats;
import dj2.ae2opt.core.OptimizationConfig;

import java.util.List;


public final class ItemHandlerExtractionStatsTest {

    static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            System.out.println("pass  " + name);
        } else {
            System.out.println("FAIL  " + name + ": " + detail);
            failures++;
        }
    }

    static final class HandlerAlpha {
    }

    static final class HandlerBeta {
    }

    static final class OwnerOne {
    }

    static final class OwnerTwo {
    }

    static ItemHandlerExtractionStats.HandlerStats find(List<ItemHandlerExtractionStats.HandlerStats> all, Class<?> handlerClass) {
        for (ItemHandlerExtractionStats.HandlerStats stats : all) {
            if (stats.handlerClassName.equals(handlerClass.getName())) {
                return stats;
            }
        }
        return null;
    }

    static void requestsAggregateByHandlerClass() {
        ItemHandlerExtractionStats.record(HandlerAlpha.class, null, true, 3, 10, 10);
        ItemHandlerExtractionStats.record(HandlerAlpha.class, null, true, 5, 4, 4);
        ItemHandlerExtractionStats.record(HandlerAlpha.class, null, false, 40, 8, 0);

        ItemHandlerExtractionStats.HandlerStats stats = find(ItemHandlerExtractionStats.snapshot(), HandlerAlpha.class);
        check("three calls to the same handler class aggregate into one entry",
                stats != null, "expected a HandlerAlpha entry");
        check("request count sums across calls", stats.requests == 3, String.valueOf(stats.requests));
        check("slots examined sums across calls", stats.slotsExamined == 48, String.valueOf(stats.slotsExamined));
        check("successful and unsuccessful are counted separately",
                stats.successfulRequests == 2 && stats.unsuccessfulRequests == 1,
                stats.successfulRequests + "/" + stats.unsuccessfulRequests);
        check("requested and extracted amounts sum across calls",
                stats.requestedAmount == 22 && stats.extractedAmount == 14,
                stats.requestedAmount + "/" + stats.extractedAmount);
    }

    static void differentHandlerClassesTrackedSeparately() {
        ItemHandlerExtractionStats.record(HandlerBeta.class, null, true, 1, 1, 1);
        List<ItemHandlerExtractionStats.HandlerStats> all = ItemHandlerExtractionStats.snapshot();
        check("a different handler class gets its own entry",
                find(all, HandlerBeta.class) != null, "expected a HandlerBeta entry");
        check("an unrelated handler class's stats are unaffected",
                find(all, HandlerAlpha.class).requests == 3, "HandlerAlpha entry changed unexpectedly");
    }

    static void ownerClassIsResolvedOnceAndNotOverwritten() {
        ItemHandlerExtractionStats.record(HandlerBeta.class, OwnerOne.class, true, 1, 0, 0);
        ItemHandlerExtractionStats.record(HandlerBeta.class, OwnerTwo.class, true, 1, 0, 0);
        ItemHandlerExtractionStats.record(HandlerBeta.class, null, true, 1, 0, 0);

        ItemHandlerExtractionStats.HandlerStats stats = find(ItemHandlerExtractionStats.snapshot(), HandlerBeta.class);
        check("the first non-null owner class wins and later calls do not overwrite it",
                OwnerOne.class.getName().equals(stats.ownerClassName), String.valueOf(stats.ownerClassName));
    }

    static void nullHandlerClassIsIgnored() {
        int before = ItemHandlerExtractionStats.trackedClassCount();
        ItemHandlerExtractionStats.record(null, null, true, 5, 5, 5);
        check("a null handler class is never recorded",
                ItemHandlerExtractionStats.trackedClassCount() == before, "tracked class count changed");
    }

    static final class CapClassA {
    }

    static final class CapClassB {
    }

    static final class CapClassC {
    }

    static void capRefusesNewClassesBeyondTheLimitWithoutDroppingExistingOnes() {
        final int savedCap = OptimizationConfig.maxItemHandlerClassesTracked;
        try {
            final int before = ItemHandlerExtractionStats.trackedClassCount();
            OptimizationConfig.maxItemHandlerClassesTracked = before + 2;

            ItemHandlerExtractionStats.record(CapClassA.class, null, true, 1, 0, 0);
            check("cap is not yet reported as overflowing while strictly under the limit",
                    !ItemHandlerExtractionStats.isOverflowing(), "expected not overflowing yet");

            ItemHandlerExtractionStats.record(CapClassB.class, null, true, 1, 0, 0);
            check("the cap admits new classes up to the limit",
                    find(ItemHandlerExtractionStats.snapshot(), CapClassB.class) != null,
                    "expected CapClassB to be tracked");
            check("reaching the limit exactly is not yet reported as overflowing (nothing dropped yet)",
                    !ItemHandlerExtractionStats.isOverflowing(), "expected not overflowing until a class is refused");

            final long overflowBefore = ItemHandlerExtractionStats.overflowRequests();
            ItemHandlerExtractionStats.record(CapClassC.class, null, true, 7, 0, 0);
            check("a class beyond the cap is not tracked individually",
                    find(ItemHandlerExtractionStats.snapshot(), CapClassC.class) == null,
                    "expected CapClassC to be refused, not tracked");
            check("a refused class is still counted in the overflow bucket",
                    ItemHandlerExtractionStats.overflowRequests() == overflowBefore + 1,
                    String.valueOf(ItemHandlerExtractionStats.overflowRequests()));
            check("an already-tracked class is unaffected once the cap is hit",
                    find(ItemHandlerExtractionStats.snapshot(), CapClassA.class) != null,
                    "expected CapClassA to remain tracked");
            check("the cap is now reported as overflowing, since a class was actually refused",
                    ItemHandlerExtractionStats.isOverflowing(), "expected overflowing to be true");
        } finally {
            OptimizationConfig.maxItemHandlerClassesTracked = savedCap;
        }
    }

    public static void main(String[] args) {
        requestsAggregateByHandlerClass();
        differentHandlerClassesTrackedSeparately();
        ownerClassIsResolvedOnceAndNotOverwritten();
        nullHandlerClassIsIgnored();
        capRefusesNewClassesBeyondTheLimitWithoutDroppingExistingOnes();

        System.out.println(failures == 0 ? "\nItemHandlerExtractionStatsTest: ALL PASS"
                : "\nItemHandlerExtractionStatsTest: " + failures + " FAILURES");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
