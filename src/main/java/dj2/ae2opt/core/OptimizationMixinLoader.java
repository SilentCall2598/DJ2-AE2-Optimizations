package dj2.ae2opt.core;

import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.ArrayList;
import java.util.List;


public final class OptimizationMixinLoader implements ILateMixinLoader {

    public static final String OPTIMIZATION_CONFIG = "mixins.dj2ae2opt.json";
    public static final String EXTRACTION_CONFIG = "mixins.dj2ae2opt.extraction.json";
    public static final String NETWORK_MONITOR_CONFIG = "mixins.dj2ae2opt.networkmonitor.json";
    public static final String FAN_OUT_CONFIG = "mixins.dj2ae2opt.fanout.json";
    public static final String NEGATIVE_EXTRACT_CONFIG = "mixins.dj2ae2opt.negativeextract.json";
    public static final String NEGATIVE_PHASE2_CONFIG = "mixins.dj2ae2opt.negativephase2.json";

    @Override
    public List<String> getMixinConfigs() {
        OptimizationConfig.load();

        final boolean cache = OptimizationConfig.optimizeDrawerInventoryPolling;
        final boolean skip = cache && OptimizationConfig.skipUnchangedDrawerPolls;
        final boolean extraction = OptimizationConfig.instrumentExtractionTransactions;
        final boolean monitor = OptimizationConfig.instrumentNetworkMonitor;
        final boolean fanOut = OptimizationConfig.instrumentNetworkFanOut;
        final boolean negative = OptimizationConfig.optimizeDrawerNegativeExtraction;
        final boolean phase2Matchers = negative && OptimizationConfig.instrumentNegativePhase2Matchers;

        MixinStatus.markRequested(MixinStatus.Feature.ITEM_REPOSITORY_CACHE, cache);
        MixinStatus.markRequested(MixinStatus.Feature.ITEM_LIST_VERSION, skip);
        MixinStatus.markRequested(MixinStatus.Feature.EXTRACTION_DIAGNOSTICS, extraction);
        MixinStatus.markRequested(MixinStatus.Feature.POWERED_EXTRACTION_CONTEXT, extraction);
        MixinStatus.markRequested(MixinStatus.Feature.NETWORK_MONITOR, monitor);
        MixinStatus.markRequested(MixinStatus.Feature.GRID_STORAGE_CACHE, monitor);
        MixinStatus.markRequested(MixinStatus.Feature.NETWORK_FAN_OUT_BRACKET, fanOut);
        MixinStatus.markRequested(MixinStatus.Feature.NETWORK_FAN_OUT_PROBE, fanOut);
        MixinStatus.markRequested(MixinStatus.Feature.NEGATIVE_FAST_PATH, negative);
        MixinStatus.markRequested(MixinStatus.Feature.NEGATIVE_INDEX, negative);
        MixinStatus.markRequested(MixinStatus.Feature.NEGATIVE_ATTRS, negative);
        MixinStatus.markRequested(MixinStatus.Feature.NEGATIVE_EPOCH_STANDARD, negative);
        MixinStatus.markRequested(MixinStatus.Feature.NEGATIVE_EPOCH_COMPACTING, negative);
        MixinStatus.markRequested(MixinStatus.Feature.NEGATIVE_EPOCH_ATTRIBUTES, negative);
        MixinStatus.markRequested(MixinStatus.Feature.NEGATIVE_FRACTIONAL, negative);
        MixinStatus.markRequested(MixinStatus.Feature.NEGATIVE_PHASE2_MATCHERS, phase2Matchers);
        MixinStatus.markRequested(MixinStatus.Feature.NEGATIVE_PHASE2_EXIT, phase2Matchers);

        List<String> configs = new ArrayList<String>(6);
        configs.add(OPTIMIZATION_CONFIG);
        if (extraction) {
            configs.add(EXTRACTION_CONFIG);
        }
        if (monitor) {
            configs.add(NETWORK_MONITOR_CONFIG);
        }
        if (fanOut) {
            configs.add(FAN_OUT_CONFIG);
        }
        if (negative) {
            configs.add(NEGATIVE_EXTRACT_CONFIG);
        }
        if (phase2Matchers) {
            configs.add(NEGATIVE_PHASE2_CONFIG);
        }
        return configs;
    }
}
