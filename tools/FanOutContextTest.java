import dj2.ae2opt.core.Diagnostics;
import dj2.ae2opt.core.NetworkRequestContext;
import dj2.ae2opt.core.OptimizationConfig;


public final class FanOutContextTest {

    static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            System.out.println("pass  " + name);
        } else {
            System.out.println("FAIL  " + name + ": " + detail);
            failures++;
        }
    }

    static String line(String prefix) {
        for (String l : Diagnostics.summaryLines()) {
            if (l.startsWith(prefix)) {
                return l;
            }
        }
        return "(missing: " + prefix + ")";
    }

    static void probes(int total, int zero) {
        for (int i = 0; i < total; i++) {
            NetworkRequestContext.recordProbe(i < zero);
        }
    }


    static void networkCall(int kind, Object requestAtHead, Object requestAtReturn,
                            int probeCount, int zeroCount) {
        NetworkRequestContext.enter(kind, requestAtHead instanceof String);
        probes(probeCount, zeroCount);
        if (requestAtReturn == null) {

        }
        NetworkRequestContext.exit();
    }

    public static void main(String[] args) {
        OptimizationConfig.instrumentNetworkFanOut = true;
        final String item = "item";

        networkCall(NetworkRequestContext.EXTRACT_SIMULATE, item, item, 21, 20);
        check("extract SIMULATE fan-out counted",
                line("extract SIM       ").contains("1 requests, 21 probes (21.00 per request, max 21)")
                        && line("extract SIM       ").contains("20 returned nothing"),
                line("extract SIM       "));

        networkCall(NetworkRequestContext.EXTRACT_MODULATE, item, item, 21, 20);
        check("extract MODULATE counted separately from SIMULATE",
                line("extract MOD       ").contains("1 requests, 21 probes")
                        && line("extract SIM       ").contains("1 requests, 21 probes"),
                line("extract MOD       "));


        networkCall(NetworkRequestContext.INJECT_MODULATE, item, null, 6, 6);
        check("fully accepted insert still closes its scope",
                NetworkRequestContext.currentDepth() == 0,
                "depth " + NetworkRequestContext.currentDepth());
        check("insert-routing probes are not counted as extraction",
                line("inject  MOD       ").contains("1 requests, 6 probes")
                        && line("extract SIM       ").contains("1 requests, 21 probes"),
                line("inject  MOD       "));


        probes(1, 1);
        check("probe after a closed scope is unattributed, not folded into it",
                line("unattributed").contains("1 probes")
                        && line("inject  MOD       ").contains("1 requests, 6 probes"),
                line("unattributed") + " | " + line("inject  MOD       "));


        NetworkRequestContext.enter(NetworkRequestContext.EXTRACT_SIMULATE, false);
        probes(2, 2);
        NetworkRequestContext.exit();
        check("non-counting frame records nothing",
                line("extract SIM       ").contains("1 requests, 21 probes")
                        && line("unattributed").contains("3 probes"),
                line("extract SIM       ") + " | " + line("unattributed"));


        NetworkRequestContext.enter(NetworkRequestContext.INJECT_SIMULATE, true);
        probes(2, 2);
        NetworkRequestContext.enter(NetworkRequestContext.EXTRACT_SIMULATE, true);
        probes(3, 1);
        NetworkRequestContext.exit();
        probes(1, 1);
        NetworkRequestContext.exit();
        check("probes attributed to the innermost counting frame",
                line("extract SIM       ").contains("2 requests, 24 probes")
                        && line("inject  SIM       ").contains("1 requests, 3 probes"),
                line("extract SIM       ") + " | " + line("inject  SIM       "));
        check("nesting reported", line("context").contains("1 nested requests"), line("context"));


        for (int i = 0; i < 3; i++) {
            networkCall(NetworkRequestContext.EXTRACT_MODULATE, item, item, 5, 5);
        }
        check("histogram prints ranges", line("extract MOD spread").contains("4-7: 3"),
                line("extract MOD spread"));


        NetworkRequestContext.enter(NetworkRequestContext.EXTRACT_SIMULATE, true);
        probes(4, 4);
        NetworkRequestContext.resetForTickEnd();
        check("leaked scope is reset and counted",
                NetworkRequestContext.currentDepth() == 0 && line("context").contains("1 tick-end resets"),
                line("context"));


        for (int i = 0; i < 40; i++) {
            NetworkRequestContext.enter(NetworkRequestContext.EXTRACT_SIMULATE, true);
        }
        for (int i = 0; i < 40; i++) {
            NetworkRequestContext.exit();
        }
        check("stack overflow stays balanced", NetworkRequestContext.currentDepth() == 0,
                "depth " + NetworkRequestContext.currentDepth());

        System.out.println(failures == 0 ? "\nFanOutContextTest: ALL PASS"
                : "\nFanOutContextTest: " + failures + " FAILURES");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
