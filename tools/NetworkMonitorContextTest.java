import dj2.ae2opt.core.NetworkMonitorContext;


public final class NetworkMonitorContextTest {

    static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            System.out.println("pass  " + name);
        } else {
            System.out.println("FAIL  " + name + ": " + detail);
            failures++;
        }
    }

    static void resetToClean() {
        int leaked;
        do {
            leaked = NetworkMonitorContext.resetAtServerTickEnd();
        } while (leaked != 0);
    }

    static void normalCellUpdateEnterExit() {
        resetToClean();
        check("not inside cellUpdate before entering", !NetworkMonitorContext.insideCellUpdate(), "expected false");
        NetworkMonitorContext.cellUpdateEnter();
        check("inside cellUpdate after entering", NetworkMonitorContext.insideCellUpdate(), "expected true");
        NetworkMonitorContext.cellUpdateExit();
        check("not inside cellUpdate after a matched exit", !NetworkMonitorContext.insideCellUpdate(), "expected false");
        check("a balanced enter/exit leaves nothing to recover",
                NetworkMonitorContext.resetAtServerTickEnd() == 0, "expected 0");
    }

    static void nestedCellUpdate() {
        resetToClean();
        NetworkMonitorContext.cellUpdateEnter();
        NetworkMonitorContext.cellUpdateEnter();
        check("inside cellUpdate at nesting depth 2", NetworkMonitorContext.insideCellUpdate(), "expected true");
        NetworkMonitorContext.cellUpdateExit();
        check("still inside cellUpdate after exiting only the inner frame",
                NetworkMonitorContext.insideCellUpdate(), "expected true");
        NetworkMonitorContext.cellUpdateExit();
        check("not inside cellUpdate after exiting both frames",
                !NetworkMonitorContext.insideCellUpdate(), "expected false");
        check("a fully balanced nested pair leaves nothing to recover",
                NetworkMonitorContext.resetAtServerTickEnd() == 0, "expected 0");
    }

    static void normalForceUpdateTiming() {
        resetToClean();
        NetworkMonitorContext.forceUpdateEnter();
        long elapsed = NetworkMonitorContext.forceUpdateExit();
        check("a matched forceUpdate span reports a non-negative elapsed time", elapsed >= 0L, "was " + elapsed);
        check("a balanced forceUpdate enter/exit leaves nothing to recover",
                NetworkMonitorContext.resetAtServerTickEnd() == 0, "expected 0");
    }

    static void nestedForceUpdate() {
        resetToClean();
        NetworkMonitorContext.forceUpdateEnter();
        NetworkMonitorContext.forceUpdateEnter();
        long inner = NetworkMonitorContext.forceUpdateExit();
        long outer = NetworkMonitorContext.forceUpdateExit();
        check("the inner frame reports a non-negative elapsed time", inner >= 0L, "was " + inner);
        check("the outer frame (which started first) reports at least as much elapsed time as the inner one",
                outer >= inner, "outer=" + outer + " inner=" + inner);
        check("a fully balanced nested forceUpdate pair leaves nothing to recover",
                NetworkMonitorContext.resetAtServerTickEnd() == 0, "expected 0");
    }

    static void depthOverflowStaysBalanced() {
        resetToClean();
        final int attempts = 64;
        for (int i = 0; i < attempts; i++) {
            NetworkMonitorContext.cellUpdateEnter();
        }
        check("still reported inside cellUpdate well past any real nesting depth",
                NetworkMonitorContext.insideCellUpdate(), "expected true");
        for (int i = 0; i < attempts; i++) {
            NetworkMonitorContext.cellUpdateExit();
        }
        check("exiting exactly as many times as entered returns to a clean state",
                !NetworkMonitorContext.insideCellUpdate(), "expected false");
        check("a fully balanced overflow-spanning sequence leaves nothing to recover",
                NetworkMonitorContext.resetAtServerTickEnd() == 0, "expected 0");
    }

    static void extraUnmatchedExitDoesNotUnderflow() {
        resetToClean();
        NetworkMonitorContext.cellUpdateExit();
        NetworkMonitorContext.cellUpdateExit();
        check("an unmatched exit with nothing entered does not go negative or throw",
                !NetworkMonitorContext.insideCellUpdate(), "expected false");
        NetworkMonitorContext.cellUpdateEnter();
        check("a normal enter still works correctly after prior unmatched exits",
                NetworkMonitorContext.insideCellUpdate(), "expected true");
        NetworkMonitorContext.cellUpdateExit();
        check("cleans up normally afterward", !NetworkMonitorContext.insideCellUpdate(), "expected false");
    }

    static void tickEndLeakedDepthRecovery() {
        resetToClean();
        NetworkMonitorContext.cellUpdateEnter();
        NetworkMonitorContext.cellUpdateEnter();
        NetworkMonitorContext.forceUpdateEnter();
        final int leaked = NetworkMonitorContext.resetAtServerTickEnd();
        check("tick-end recovery reports the exact leaked depth across both brackets",
                leaked == 3, "expected 3, was " + leaked);
        check("cellUpdate state is clean immediately after recovery",
                !NetworkMonitorContext.insideCellUpdate(), "expected false");
        check("recovery itself reports nothing left to recover on a second call",
                NetworkMonitorContext.resetAtServerTickEnd() == 0, "expected 0");
    }

    static void tickEndOverflowRecovery() {
        resetToClean();
        final int attempts = 64;
        for (int i = 0; i < attempts; i++) {
            NetworkMonitorContext.cellUpdateEnter();
        }
        for (int i = 0; i < attempts; i++) {
            NetworkMonitorContext.forceUpdateEnter();
        }
        final int leaked = NetworkMonitorContext.resetAtServerTickEnd();
        check("tick-end recovery folds overflow levels into the reported leaked count, not just the depth stack",
                leaked >= attempts * 2, "expected at least " + (attempts * 2) + ", was " + leaked);
        check("cellUpdate state is clean immediately after an overflow recovery",
                !NetworkMonitorContext.insideCellUpdate(), "expected false");
    }

    static void cleanStateAfterRecoveryBehavesNormally() {
        resetToClean();
        NetworkMonitorContext.cellUpdateEnter();
        NetworkMonitorContext.resetAtServerTickEnd();

        NetworkMonitorContext.cellUpdateEnter();
        check("a fresh enter after a recovery is observed normally",
                NetworkMonitorContext.insideCellUpdate(), "expected true");
        NetworkMonitorContext.cellUpdateExit();
        check("a fresh exit after a recovery returns to a clean state",
                !NetworkMonitorContext.insideCellUpdate(), "expected false");
        check("no residual leaked depth remains from before the recovery",
                NetworkMonitorContext.resetAtServerTickEnd() == 0, "expected 0");
    }

    public static void main(String[] args) {
        normalCellUpdateEnterExit();
        nestedCellUpdate();
        normalForceUpdateTiming();
        nestedForceUpdate();
        depthOverflowStaysBalanced();
        extraUnmatchedExitDoesNotUnderflow();
        tickEndLeakedDepthRecovery();
        tickEndOverflowRecovery();
        cleanStateAfterRecoveryBehavesNormally();

        System.out.println(failures == 0 ? "\nNetworkMonitorContextTest: ALL PASS"
                : "\nNetworkMonitorContextTest: " + failures + " FAILURES");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
