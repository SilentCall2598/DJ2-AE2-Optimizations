package dj2.ae2opt.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;


public final class MixinStatus {

    public static final Logger LOG = LogManager.getLogger("DJ2AE2Opt");
    public static final Logger MIXIN_LOG = LogManager.getLogger("DJ2AE2Opt/Mixin");
    public static final Logger RUNTIME_LOG = LogManager.getLogger("DJ2AE2Opt/Runtime");

    public enum Feature {

        ITEM_REPOSITORY_CACHE("Drawer conversion cache", "MixinItemRepositoryInventoryCache"),
        ITEM_LIST_VERSION("Unchanged-poll skipping (experimental)", "MixinItemList"),
        EXTRACTION_DIAGNOSTICS("Extraction transaction diagnostics", "MixinItemRepositoryAdapterExtraction"),
        POWERED_EXTRACTION_CONTEXT("PoweredExtraction context diagnostics", "MixinPlatformPoweredExtractionDiagnostics"),
        NETWORK_MONITOR("NetworkMonitor diagnostics", "MixinNetworkMonitor"),
        GRID_STORAGE_CACHE("GridStorageCache diagnostics", "MixinGridStorageCache"),
        NETWORK_FAN_OUT_BRACKET("Fan-out request bracket", "MixinNetworkInventoryHandlerFanOut"),
        NETWORK_FAN_OUT_PROBE("Fan-out drawer-probe observer", "MixinItemRepositoryAdapterFanOutProbe"),
        NEGATIVE_FAST_PATH("Drawer negative fast path", "MixinControllerItemRepositoryNegativeExtract"),
        NEGATIVE_INDEX("Drawer presence index", "MixinTileEntityController"),
        NEGATIVE_ATTRS("Drawer conversion probe", "MixinStandardDrawerAttributes"),
        NEGATIVE_EPOCH_STANDARD("Presence invalidation, standard", "MixinStandardDrawerItemChanged"),
        NEGATIVE_EPOCH_COMPACTING("Presence invalidation, compacting", "MixinCompDrawerItemChanged"),
        NEGATIVE_EPOCH_ATTRIBUTES("Presence invalidation, matcher", "MixinStandardDrawerGroupSyncAttributes"),
        NEGATIVE_FRACTIONAL("Compacting drawer support", "MixinFractionalDrawerMarker"),
        NEGATIVE_PHASE2_MATCHERS("Phase-2 matcher-call sampling", "MixinDrawerItemRepositoryPhase2Probe"),
        NEGATIVE_PHASE2_EXIT("Phase-2 matcher-call sample exit", "MixinControllerItemRepositoryPhase2Exit");

        public final String label;
        public final String mixinSimpleName;

        volatile boolean requested;
        volatile boolean pluginConsulted;
        volatile boolean selected;
        volatile boolean applied;
        volatile boolean runtimeHit;
        volatile String target;

        Feature(String label, String mixinSimpleName) {
            this.label = label;
            this.mixinSimpleName = mixinSimpleName;
        }

        public boolean isRequested() {
            return this.requested;
        }

        public boolean isSelected() {
            return this.selected;
        }


        public boolean isDiagnostic() {
            switch (this) {
                case ITEM_REPOSITORY_CACHE:
                case ITEM_LIST_VERSION:
                case NEGATIVE_FAST_PATH:
                case NEGATIVE_INDEX:
                case NEGATIVE_ATTRS:
                case NEGATIVE_EPOCH_STANDARD:
                case NEGATIVE_EPOCH_COMPACTING:
                case NEGATIVE_EPOCH_ATTRIBUTES:
                case NEGATIVE_FRACTIONAL:
                    return false;
                default:
                    return true;
            }
        }

        public boolean isApplied() {
            return this.applied;
        }

        public boolean hasRuntimeHit() {
            return this.runtimeHit;
        }

        public void markRuntimeHit() {
            this.runtimeHit = true;
        }
    }

    private MixinStatus() {
    }


    public static void markRequested(Feature feature, boolean requested) {
        feature.requested = requested;
    }

    public static void markSelected(String mixinClassName, boolean selected) {
        Feature feature = forMixin(mixinClassName);
        if (feature != null) {
            feature.pluginConsulted = true;
            feature.selected = selected;
        }
    }


    public static synchronized void markApplied(String mixinClassName, String targetClassName) {
        Feature feature = forMixin(mixinClassName);
        if (feature == null || feature.applied) {
            return;
        }
        feature.applied = true;
        feature.target = targetClassName;
        MIXIN_LOG.info("APPLIED {}", feature.mixinSimpleName);
        MIXIN_LOG.info("  -> {}", targetClassName);
    }

    private static Feature forMixin(String mixinClassName) {
        for (Feature feature : Feature.values()) {
            if (mixinClassName.endsWith(feature.mixinSimpleName)) {
                return feature;
            }
        }
        return null;
    }

    public static List<String> selectionLines() {
        List<String> lines = new ArrayList<String>();
        lines.add("DJ2 AE2 Optimizations v" + Constants.VERSION);
        lines.add("Target environment: AE2 UEL " + OptimizationConfig.expectedAe2Version
                + " / Storage Drawers " + OptimizationConfig.expectedStorageDrawersVersion);
        lines.add("Features selected:");
        for (Feature feature : Feature.values()) {
            lines.add("  " + pad(feature.label) + (feature.requested ? "ENABLED" : "DISABLED"));
        }
        lines.add("  " + pad("identity stability sampling")
                + (OptimizationConfig.autoDisableTemplateCacheOnLowHitRate ? "ENABLED" : "BYPASSED"));
        lines.add("  " + pad("max live prototypes / bus") + OptimizationConfig.maxLivePrototypesPerBus);
        lines.add("This is what the config asked for. It does not mean a mixin applied.");
        return lines;
    }

    public static List<String> statusLines() {
        List<String> lines = new ArrayList<String>();
        for (Feature feature : Feature.values()) {
            lines.add(feature.label + ":");
            lines.add("  " + pad("requested") + (feature.requested ? "YES" : "NO"));
            if (!feature.requested) {
                continue;
            }

            lines.add("  " + pad("plugin selected") + (feature.pluginConsulted
                    ? (feature.selected ? "YES" : "NO")
                    : "NOT REACHED - Mixin did not ask"));

            lines.add("  " + pad("mixin applied") + (feature.applied
                    ? "YES (" + feature.target + ")"
                    : "NO - the target has not loaded yet, or Mixin skipped it; search the log "
                            + "for 'loaded too early'"));

            if (!feature.applied && feature == Feature.POWERED_EXTRACTION_CONTEXT) {
                lines.add("      on this pack appeng.util.Platform is classloaded before "
                        + "late mixins are prepared,");
                lines.add("      so this bracket is expected to be missing. Extraction "
                        + "pairs are then the");
                lines.add("      same-tick/same-source heuristic, not transaction-scoped.");
            }

            lines.add("  " + pad("runtime hit") + (feature.runtimeHit ? "YES" : "NO" + waitingOn(feature)));
        }
        return lines;
    }

    private static String waitingOn(Feature feature) {
        switch (feature) {
            case EXTRACTION_DIAGNOSTICS:
                return " (waiting for a drawer-backed extraction)";
            case POWERED_EXTRACTION_CONTEXT:
                return " (waiting for Platform.poweredExtraction)";
            case NETWORK_FAN_OUT_BRACKET:
                return " (waiting for a network-level item request)";
            case NETWORK_FAN_OUT_PROBE:
                return " (waiting for a drawer-repository extraction probe)";
            case NEGATIVE_EPOCH_STANDARD:
            case NEGATIVE_EPOCH_COMPACTING:
            case NEGATIVE_EPOCH_ATTRIBUTES:
                return " (waiting for a drawer to change)";
            case NEGATIVE_ATTRS:
                return " (waiting for a presence index rebuild)";
            case NEGATIVE_FAST_PATH:
            case NEGATIVE_INDEX:
                return " (waiting for a drawer controller extraction)";
            case NEGATIVE_PHASE2_MATCHERS:
                return " (waiting for a sampled key-present phase-2 fallback)";
            case NEGATIVE_PHASE2_EXIT:
                return " (waiting for a sampled extraction to return)";
            case ITEM_REPOSITORY_CACHE:
            case ITEM_LIST_VERSION:
                return " (waiting for a storage bus to poll a drawer network)";
            default:
                return "";
        }
    }


    public static boolean hasUnavailableDiagnostics() {
        for (Feature feature : Feature.values()) {
            if (feature.isDiagnostic() && feature.requested && !feature.applied) {
                return true;
            }
        }
        return false;
    }

    public static void log(Logger logger, String heading, List<String> lines) {
        logger.info("===== {} =====", heading);
        for (String line : lines) {
            logger.info(line);
        }
        logger.info("================================");
    }

    private static String pad(String label) {
        StringBuilder builder = new StringBuilder(label);
        while (builder.length() < 32) {
            builder.append('.');
        }
        return builder.append(' ').toString();
    }
}
