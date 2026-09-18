package dj2.ae2opt.core;

import net.minecraft.launchwrapper.Launch;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;


public final class OptimizationConfig {

    public static final String FILE_NAME = "dj2ae2opt.cfg";


    public static boolean optimizeDrawerInventoryPolling = true;


    public static boolean skipUnchangedDrawerPolls = false;


    public static boolean autoDisableTemplateCacheOnLowHitRate = true;


    public static int maxLivePrototypesPerBus = 16384;


    public static boolean instrumentExtractionTransactions = false;


    public static boolean instrumentNegativeCandidateSlots = false;


    public static boolean instrumentNegativePhase2Matchers = false;


    public static boolean instrumentNetworkMonitor = false;


    public static boolean optimizeDrawerNegativeExtraction = false;


    public static boolean instrumentNetworkFanOut = false;


    public static int diagnosticsDumpIntervalSeconds = 0;


    public static String expectedAe2Version = "0.56.4";


    public static String expectedStorageDrawersVersion = "5.5.0";


    public static boolean allowUnverifiedModVersions = false;

    private static boolean loaded;

    private OptimizationConfig() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;

        File file = new File(configDir(), FILE_NAME);
        Properties properties = new Properties();
        if (file.isFile()) {
            InputStream in = null;
            try {
                in = new FileInputStream(file);
                properties.load(in);
            } catch (IOException e) {
                Diagnostics.LOG.warn("Could not read {}, using defaults", file, e);
            } finally {
                closeQuietly(in);
            }
        }

        optimizeDrawerInventoryPolling = bool(properties, "optimizeDrawerInventoryPolling", optimizeDrawerInventoryPolling);
        skipUnchangedDrawerPolls = bool(properties, "skipUnchangedDrawerPolls", skipUnchangedDrawerPolls);
        autoDisableTemplateCacheOnLowHitRate = bool(properties, "autoDisableTemplateCacheOnLowHitRate", autoDisableTemplateCacheOnLowHitRate);
        maxLivePrototypesPerBus = clamp(integer(properties, "maxLivePrototypesPerBus", maxLivePrototypesPerBus), 64, 1048576);
        instrumentExtractionTransactions = bool(properties, "instrumentExtractionTransactions", instrumentExtractionTransactions);
        instrumentNegativeCandidateSlots = bool(properties, "instrumentNegativeCandidateSlots",
                instrumentNegativeCandidateSlots);
        instrumentNegativePhase2Matchers = bool(properties, "instrumentNegativePhase2Matchers",
                instrumentNegativePhase2Matchers);
        instrumentNetworkMonitor = bool(properties, "instrumentNetworkMonitor", instrumentNetworkMonitor);
        instrumentNetworkFanOut = bool(properties, "instrumentNetworkFanOut", instrumentNetworkFanOut);
        optimizeDrawerNegativeExtraction = bool(properties, "optimizeDrawerNegativeExtraction", optimizeDrawerNegativeExtraction);
        diagnosticsDumpIntervalSeconds = clamp(integer(properties, "diagnosticsDumpIntervalSeconds", diagnosticsDumpIntervalSeconds), 0, 86400);
        expectedAe2Version = string(properties, "expectedAe2Version", expectedAe2Version);
        expectedStorageDrawersVersion = string(properties, "expectedStorageDrawersVersion", expectedStorageDrawersVersion);
        allowUnverifiedModVersions = bool(properties, "allowUnverifiedModVersions", allowUnverifiedModVersions);

        write(file);
    }

    private static void write(File file) {
        Writer writer = null;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return;
            }
            writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            writer.write("# DJ2 AE2 Optimizations\n");
            writer.write("# Rewritten in full on every start; edit values, not structure.\n\n");
            writer.write("# Reuse the AE item conversion for an unchanged Storage Drawers prototype instead of\n");
            writer.write("# running AEItemStack.fromItemStack and the AEItemStackRegistry lookup every poll.\n");
            writer.write("optimizeDrawerInventoryPolling = " + optimizeDrawerInventoryPolling + "\n\n");
            writer.write("# EXPERIMENTAL. Skip the rebuild entirely when the drawers report the same prototypes\n");
            writer.write("# and counts as the previous poll and nothing has mutated the cached list since.\n");
            writer.write("# Measured on a real DJ2 base: only 15.2 percent of polls qualified, and proving the\n");
            writer.write("# other 84.8 percent unchanged cost more than the skips saved - it profiled no better\n");
            writer.write("# than stock AE2, and worse than the conversion cache alone. Kept for idle networks.\n");
            writer.write("skipUnchangedDrawerPolls = " + skipUnchangedDrawerPolls + "\n\n");
            writer.write("# Stop using the conversion cache on a storage bus whose prototypes are not stable by\n");
            writer.write("# identity, rather than paying for lookups that never hit.\n");
            writer.write("autoDisableTemplateCacheOnLowHitRate = " + autoDisableTemplateCacheOnLowHitRate + "\n\n");
            writer.write("# Sanity cap on how many live prototypes one storage bus may cache. Entries for\n");
            writer.write("# prototypes the drawers no longer report are pruned on rebuilds, so the cache tracks\n");
            writer.write("# the live set; this only guards against an absurd repository, and exceeding it turns\n");
            writer.write("# the cache off for that bus with a logged reason instead of thrashing it.\n");
            writer.write("maxLivePrototypesPerBus = " + maxLivePrototypesPerBus + "\n\n");
            writer.write("# PHASE 2 DIAGNOSTIC ONLY. Count ItemRepositoryAdapter extraction calls and\n");
            writer.write("# SIMULATE/MODULATE pairing without changing any transaction behaviour. Leave off\n");
            writer.write("# for normal play; enable for a measured 5-10 minute capture.\n");
            writer.write("instrumentExtractionTransactions = " + instrumentExtractionTransactions + "\n\n");
            writer.write("# Sample key-present negative-extraction fallbacks and record how many drawer slots\n");
            writer.write("# a candidate index could have narrowed the stock scan to. Measurement only, one\n");
            writer.write("# request in 512, and nothing it computes is returned to Storage Drawers.\n");
            writer.write("instrumentNegativeCandidateSlots = " + instrumentNegativeCandidateSlots + "\n\n");
            writer.write("instrumentNegativePhase2Matchers = " + instrumentNegativePhase2Matchers + "\n\n");
            writer.write("# Diagnostic counters on NetworkMonitor.forceUpdate and GridStorageCache.cellUpdate.\n");
            writer.write("# Off by default: it is measurement, not an optimisation.\n");
            writer.write("instrumentNetworkMonitor = " + instrumentNetworkMonitor + "\n\n");
            writer.write("# Count drawer-adapter probes per AE2 network request, and separate real extraction\n");
            writer.write("# from the probes AE2 performs while deciding where to insert an item. Answers how\n");
            writer.write("# many drawer repositories one high-level operation actually asks. Diagnostic only.\n");
            writer.write("instrumentNetworkFanOut = " + instrumentNetworkFanOut + "\n\n");
            writer.write("# EXPERIMENTAL, default off. When a Storage Drawers controller provably cannot serve\n");
            writer.write("# a request, answer empty instead of scanning every drawer slot in the network. Stock\n");
            writer.write("# runs unchanged for anything uncertain: a non-null predicate, a network containing a\n");
            writer.write("# unaudited drawer implementation, or an index that is not currently fresh. A\n");
            writer.write("# conversion upgrade no longer disqualifies a network: the index represents its\n");
            writer.write("# ore-dictionary equivalents conservatively instead.\n");
            writer.write("optimizeDrawerNegativeExtraction = " + optimizeDrawerNegativeExtraction + "\n\n");
            writer.write("# Seconds between diagnostic dumps to the server log. 0 disables them.\n");
            writer.write("diagnosticsDumpIntervalSeconds = " + diagnosticsDumpIntervalSeconds + "\n\n");
            writer.write("# The mod versions this build is validated against. The drawer optimisation reads\n");
            writer.write("# private AE2 members by name and relies on Storage Drawers' prototype identity\n");
            writer.write("# behaviour, so on anything else it disables itself instead of guessing. The version\n");
            writer.write("# strings actually found are logged at startup. Leave a value empty to skip its check.\n");
            writer.write("expectedAe2Version = " + expectedAe2Version + "\n");
            writer.write("expectedStorageDrawersVersion = " + expectedStorageDrawersVersion + "\n");
            writer.write("allowUnverifiedModVersions = " + allowUnverifiedModVersions + "\n");
        } catch (IOException e) {
            Diagnostics.LOG.warn("Could not write {}", file, e);
        } finally {
            closeQuietly(writer);
        }
    }

    private static File configDir() {
        File home = Launch.minecraftHome;
        return new File(home == null ? new File(".") : home, "config");
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        String value = trimmed(properties, key);
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return fallback;
    }

    private static String string(Properties properties, String key, String fallback) {
        String value = trimmed(properties, key);
        return value == null ? fallback : value;
    }

    private static int integer(Properties properties, String key, int fallback) {
        String value = trimmed(properties, key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String trimmed(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value == null ? null : value.trim();
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }
}
