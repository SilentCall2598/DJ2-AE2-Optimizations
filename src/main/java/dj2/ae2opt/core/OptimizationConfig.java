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


    public static boolean optimizeDrawerInventoryDiff = true;


    public static boolean autoDisableTemplateCacheOnLowHitRate = true;


    public static int maxLivePrototypesPerBus = 16384;


    public static boolean instrumentExtractionTransactions = false;


    public static boolean instrumentNegativeCandidateSlots = false;


    public static boolean instrumentNegativePhase2Matchers = false;


    public static boolean instrumentNetworkMonitor = false;


    public static boolean optimizeDrawerNegativeExtraction = true;


    public static boolean optimizeDrawerCandidateNarrowing = true;


    public static boolean instrumentCandidateIndexVerification = false;


    public static int maxCandidateKeysPerController = 16384;


    public static int maxCandidateSlotReferencesPerController = 262144;


    public static boolean instrumentNetworkFanOut = false;


    public static boolean instrumentItemHandlerExtraction = false;


    public static int maxItemHandlerClassesTracked = 256;


    public static boolean optimizeExternalItemHandlerNegativeExtraction = false;


    public static boolean optimizeThaumicEnergisticsIncrementalUpdate = false;


    public static boolean optimizeDrawerSteadyStatePolling = false;


    public static boolean optimizeInterfaceTransferRouting = false;


    public static int diagnosticsDumpIntervalSeconds = 0;


    public static String expectedAe2Version = "0.56.4";


    public static String expectedStorageDrawersVersion = "5.5.0";


    public static String expectedEnderUtilitiesVersion = "0.7.15";


    public static String expectedActuallyAdditionsVersion = "1.12.2-r152";


    public static String expectedThaumicEnergisticsVersion = "2.2.6";


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
        optimizeDrawerInventoryDiff = bool(properties, "optimizeDrawerInventoryDiff", optimizeDrawerInventoryDiff);
        autoDisableTemplateCacheOnLowHitRate = bool(properties, "autoDisableTemplateCacheOnLowHitRate", autoDisableTemplateCacheOnLowHitRate);
        maxLivePrototypesPerBus = clamp(integer(properties, "maxLivePrototypesPerBus", maxLivePrototypesPerBus), 64, 1048576);
        instrumentExtractionTransactions = bool(properties, "instrumentExtractionTransactions", instrumentExtractionTransactions);
        instrumentNegativeCandidateSlots = bool(properties, "instrumentNegativeCandidateSlots",
                instrumentNegativeCandidateSlots);
        instrumentNegativePhase2Matchers = bool(properties, "instrumentNegativePhase2Matchers",
                instrumentNegativePhase2Matchers);
        instrumentNetworkMonitor = bool(properties, "instrumentNetworkMonitor", instrumentNetworkMonitor);
        instrumentNetworkFanOut = bool(properties, "instrumentNetworkFanOut", instrumentNetworkFanOut);
        instrumentItemHandlerExtraction = bool(properties, "instrumentItemHandlerExtraction",
                instrumentItemHandlerExtraction);
        maxItemHandlerClassesTracked = clamp(integer(properties, "maxItemHandlerClassesTracked",
                maxItemHandlerClassesTracked), 8, 16384);
        optimizeExternalItemHandlerNegativeExtraction = bool(properties,
                "optimizeExternalItemHandlerNegativeExtraction", optimizeExternalItemHandlerNegativeExtraction);
        optimizeThaumicEnergisticsIncrementalUpdate = bool(properties,
                "optimizeThaumicEnergisticsIncrementalUpdate", optimizeThaumicEnergisticsIncrementalUpdate);
        optimizeDrawerSteadyStatePolling = bool(properties,
                "optimizeDrawerSteadyStatePolling", optimizeDrawerSteadyStatePolling);
        optimizeInterfaceTransferRouting = bool(properties,
                "optimizeInterfaceTransferRouting", optimizeInterfaceTransferRouting);
        optimizeDrawerNegativeExtraction = bool(properties, "optimizeDrawerNegativeExtraction", optimizeDrawerNegativeExtraction);
        optimizeDrawerCandidateNarrowing = bool(properties, "optimizeDrawerCandidateNarrowing", optimizeDrawerCandidateNarrowing);
        instrumentCandidateIndexVerification = bool(properties, "instrumentCandidateIndexVerification",
                instrumentCandidateIndexVerification);
        maxCandidateKeysPerController = clamp(integer(properties, "maxCandidateKeysPerController", maxCandidateKeysPerController), 64, 1048576);
        maxCandidateSlotReferencesPerController = clamp(integer(properties,
                "maxCandidateSlotReferencesPerController", maxCandidateSlotReferencesPerController), 64, 4194304);
        diagnosticsDumpIntervalSeconds = clamp(integer(properties, "diagnosticsDumpIntervalSeconds", diagnosticsDumpIntervalSeconds), 0, 86400);
        expectedAe2Version = string(properties, "expectedAe2Version", expectedAe2Version);
        expectedStorageDrawersVersion = string(properties, "expectedStorageDrawersVersion", expectedStorageDrawersVersion);
        expectedEnderUtilitiesVersion = string(properties, "expectedEnderUtilitiesVersion", expectedEnderUtilitiesVersion);
        expectedActuallyAdditionsVersion = string(properties, "expectedActuallyAdditionsVersion", expectedActuallyAdditionsVersion);
        expectedThaumicEnergisticsVersion = string(properties, "expectedThaumicEnergisticsVersion", expectedThaumicEnergisticsVersion);
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
            writer.write("# Once prototype identity is confirmed stable, compute the tick diff by reading old\n");
            writer.write("# and new values through IItemList.findPrecise instead of negating every cached entry,\n");
            writer.write("# merging the new poll into it, and discarding the merged structure. Removes that\n");
            writer.write("# per-tick throwaway allocation; falls back to the exact prior cycle whenever identity\n");
            writer.write("# has not yet been confirmed stable for this storage bus.\n");
            writer.write("optimizeDrawerInventoryDiff = " + optimizeDrawerInventoryDiff + "\n\n");
            writer.write("# Stop using the conversion cache on a storage bus whose prototypes are not stable by\n");
            writer.write("# identity, rather than paying for lookups that never hit.\n");
            writer.write("autoDisableTemplateCacheOnLowHitRate = " + autoDisableTemplateCacheOnLowHitRate + "\n\n");
            writer.write("# Sanity cap on how many live prototypes one storage bus may cache. Entries for\n");
            writer.write("# prototypes the drawers no longer report are pruned on rebuilds, so the cache tracks\n");
            writer.write("# the live set; this only guards against an absurd repository, and exceeding it turns\n");
            writer.write("# the cache off for that bus with a logged reason instead of thrashing it.\n");
            writer.write("maxLivePrototypesPerBus = " + maxLivePrototypesPerBus + "\n\n");
            writer.write("# Diagnostic only. Count ItemRepositoryAdapter extraction calls and\n");
            writer.write("# SIMULATE/MODULATE pairing without changing any transaction behavior. Leave off\n");
            writer.write("# for normal play; enable for a measured 5-10 minute capture.\n");
            writer.write("instrumentExtractionTransactions = " + instrumentExtractionTransactions + "\n\n");
            writer.write("# Sample key-present negative-extraction fallbacks and record how many drawer slots\n");
            writer.write("# a candidate index could have narrowed the stock scan to. Measurement only, one\n");
            writer.write("# request in 512, and nothing it computes is returned to Storage Drawers.\n");
            writer.write("instrumentNegativeCandidateSlots = " + instrumentNegativeCandidateSlots + "\n\n");
            writer.write("# Sample key-present negative-extraction fallbacks and count the Storage Drawers\n");
            writer.write("# phase-2 matcher calls they actually make. Diagnostic only; requires\n");
            writer.write("# optimizeDrawerNegativeExtraction.\n");
            writer.write("instrumentNegativePhase2Matchers = " + instrumentNegativePhase2Matchers + "\n\n");
            writer.write("# Diagnostic counters on NetworkMonitor.forceUpdate and GridStorageCache.cellUpdate.\n");
            writer.write("# Off by default: it is measurement, not an optimization.\n");
            writer.write("instrumentNetworkMonitor = " + instrumentNetworkMonitor + "\n\n");
            writer.write("# Count drawer-adapter probes per AE2 network request, and separate real extraction\n");
            writer.write("# from the probes AE2 performs while deciding where to insert an item. Answers how\n");
            writer.write("# many drawer repositories one high-level operation actually asks. Diagnostic only.\n");
            writer.write("instrumentNetworkFanOut = " + instrumentNetworkFanOut + "\n\n");
            writer.write("# Aggregate ItemHandlerAdapter.extractItems calls against generic Forge IItemHandler\n");
            writer.write("# storage buses by concrete handler class: request counts, slots examined, and\n");
            writer.write("# success/failure, to identify which handler classes dominate that cost before any\n");
            writer.write("# optimization is attempted. Diagnostic only; nothing it computes changes extraction.\n");
            writer.write("instrumentItemHandlerExtraction = " + instrumentItemHandlerExtraction + "\n");
            writer.write("maxItemHandlerClassesTracked = " + maxItemHandlerClassesTracked + "\n\n");
            writer.write("# Off by default. Proven-absent short-circuit for ItemHandlerAdapter.extractItems, for\n");
            writer.write("# the specific IItemHandler integrations audited and version-gated below (Ender\n");
            writer.write("# Utilities JSU, Actually Additions Large Storage Crate). Any other handler runs\n");
            writer.write("# unchanged. Falls back to the exact stock scan whenever the index cannot prove\n");
            writer.write("# absence, is not built, or the owning mod is absent or a different version.\n");
            writer.write("optimizeExternalItemHandlerNegativeExtraction = "
                    + optimizeExternalItemHandlerNegativeExtraction + "\n\n");
            writer.write("# Off by default. On an attached-side neighbor notification against a Thaumic\n");
            writer.write("# Energistics essentia storage bus, if the connected container is unchanged, post a\n");
            writer.write("# precise signed essentia delta through AE2's postAlterationOfStoredItems instead of\n");
            writer.write("# the broad MENetworkCellArrayUpdate the stock method posts on every call. A real\n");
            writer.write("# topology change, or any uncertain or unsupported state, falls back to the exact\n");
            writer.write("# stock broad update.\n");
            writer.write("optimizeThaumicEnergisticsIncrementalUpdate = "
                    + optimizeThaumicEnergisticsIncrementalUpdate + "\n\n");
            writer.write("# Off by default. Once a Storage Drawers repository's prototype identity is confirmed\n");
            writer.write("# stable, reconcile currentlyCached in place instead of building a fresh IItemList and\n");
            writer.write("# swapping it in every poll: one repository pass computes each AE value's true current\n");
            writer.write("# total, compares it against the live cached quantity (which already reflects any\n");
            writer.write("# interim MODULATE mutation), and applies only the resulting signed delta. New values\n");
            writer.write("# are still added and vanished values are still zeroed exactly as before. Falls back\n");
            writer.write("# to the exact existing findPrecise-diff path whenever identity is not confirmed\n");
            writer.write("# stable, the steady-state state is not primed yet, or anything looks inconsistent.\n");
            writer.write("# Requires optimizeDrawerInventoryPolling.\n");
            writer.write("optimizeDrawerSteadyStatePolling = " + optimizeDrawerSteadyStatePolling + "\n\n");
            writer.write("# Off by default. DualityInterface.usePlan's powered extraction/insertion normally runs\n");
            writer.write("# a full SIMULATE network traversal and then a full separate MODULATE traversal a few\n");
            writer.write("# microseconds later. A synchronous per-invocation context lets the exact, audited\n");
            writer.write("# ItemRepositoryAdapter handler class skip its own expensive underlying repository\n");
            writer.write("# call on MODULATE when the paired SIMULATE already proved it has none of the\n");
            writer.write("# requested item; a handler that simulated positive always still runs its real\n");
            writer.write("# MODULATE call unchanged. Any handler type outside that exact audited class, or any\n");
            writer.write("# call outside a paired context, runs completely unchanged.\n");
            writer.write("optimizeInterfaceTransferRouting = " + optimizeInterfaceTransferRouting + "\n\n");
            writer.write("# When a Storage Drawers controller provably cannot serve a request, answer empty\n");
            writer.write("# instead of scanning every drawer slot in the network. Stock runs unchanged for\n");
            writer.write("# anything uncertain: a non-null predicate, a network containing an unaudited drawer\n");
            writer.write("# implementation, or an index that is not currently fresh. A conversion upgrade no\n");
            writer.write("# longer disqualifies a network: the index represents its ore-dictionary equivalents\n");
            writer.write("# conservatively instead.\n");
            writer.write("optimizeDrawerNegativeExtraction = " + optimizeDrawerNegativeExtraction + "\n\n");
            writer.write("# When the presence index shows the requested item may be present, scan only the\n");
            writer.write("# drawer slots whose prototype or ore-dictionary equivalents can serve it instead of\n");
            writer.write("# every slot in the network. Requires optimizeDrawerNegativeExtraction. A controller\n");
            writer.write("# over either cap below keeps the full stock scan.\n");
            writer.write("optimizeDrawerCandidateNarrowing = " + optimizeDrawerCandidateNarrowing + "\n");
            writer.write("maxCandidateKeysPerController = " + maxCandidateKeysPerController + "\n");
            writer.write("maxCandidateSlotReferencesPerController = " + maxCandidateSlotReferencesPerController + "\n\n");
            writer.write("# Recompute one narrowed request in 512 from scratch and compare it with the index;\n");
            writer.write("# a mismatch uses the full stock scan for that request. Diagnostic only.\n");
            writer.write("instrumentCandidateIndexVerification = " + instrumentCandidateIndexVerification + "\n\n");
            writer.write("# Seconds between diagnostic dumps to the server log. 0 disables them.\n");
            writer.write("diagnosticsDumpIntervalSeconds = " + diagnosticsDumpIntervalSeconds + "\n\n");
            writer.write("# The mod versions this build is validated against. The drawer optimization reads\n");
            writer.write("# private AE2 members by name and relies on Storage Drawers' prototype identity\n");
            writer.write("# behavior, so on anything else every mixin is declined instead of guessing. The version\n");
            writer.write("# strings actually found are logged at startup. Leave a value empty to skip its check.\n");
            writer.write("expectedAe2Version = " + expectedAe2Version + "\n");
            writer.write("expectedStorageDrawersVersion = " + expectedStorageDrawersVersion + "\n");
            writer.write("# Same idea, for the two optional external IItemHandler integrations above. Neither\n");
            writer.write("# mod is required for this build to work; when absent, their mixins simply do not\n");
            writer.write("# apply. When present at a different version, the integration mixins are skipped\n");
            writer.write("# rather than applied against unverified behavior.\n");
            writer.write("expectedEnderUtilitiesVersion = " + expectedEnderUtilitiesVersion + "\n");
            writer.write("expectedActuallyAdditionsVersion = " + expectedActuallyAdditionsVersion + "\n");
            writer.write("# Same idea, for the Thaumic Energistics essentia storage bus optimization above.\n");
            writer.write("# This is the version the mod's own @Mod annotation reports at runtime, which is\n");
            writer.write("# what Forge actually resolves; it can differ from the version in mcmod.info.\n");
            writer.write("expectedThaumicEnergisticsVersion = " + expectedThaumicEnergisticsVersion + "\n");
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
