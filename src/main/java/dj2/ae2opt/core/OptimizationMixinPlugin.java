package dj2.ae2opt.core;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;


public final class OptimizationMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
        OptimizationConfig.load();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean selected = select(mixinClassName);
        MixinStatus.markSelected(mixinClassName, selected);
        return selected;
    }

    private boolean select(String mixinClassName) {
        if (!OptimizationConfig.allowUnverifiedModVersions
                && CompatibilityCheck.checkEarly() == CompatibilityCheck.Support.UNSUPPORTED) {
            return false;
        }
        if (mixinClassName.endsWith("MixinItemRepositoryInventoryCache")) {
            return OptimizationConfig.optimizeDrawerInventoryPolling;
        }
        if (mixinClassName.endsWith("MixinItemRepositoryAdapterExtraction")
                || mixinClassName.endsWith("MixinPlatformPoweredExtractionDiagnostics")) {
            return OptimizationConfig.instrumentExtractionTransactions;
        }
        if (mixinClassName.endsWith("MixinStandardDrawerGroupSyncAttributes")
                || mixinClassName.endsWith("MixinFractionalDrawerMarker")) {
            return OptimizationConfig.optimizeDrawerNegativeExtraction;
        }
        if (mixinClassName.endsWith("MixinNetworkMonitor")
                || mixinClassName.endsWith("MixinGridStorageCache")) {
            return OptimizationConfig.instrumentNetworkMonitor;
        }
        if (mixinClassName.endsWith("MixinControllerItemRepositoryNegativeExtract")
                || mixinClassName.endsWith("MixinTileEntityController")
                || mixinClassName.endsWith("MixinStandardDrawerAttributes")
                || mixinClassName.endsWith("MixinStandardDrawerItemChanged")
                || mixinClassName.endsWith("MixinCompDrawerItemChanged")) {
            return OptimizationConfig.optimizeDrawerNegativeExtraction;
        }
        if (mixinClassName.endsWith("MixinNetworkInventoryHandlerFanOut")
                || mixinClassName.endsWith("MixinItemRepositoryAdapterFanOutProbe")) {
            return OptimizationConfig.instrumentNetworkFanOut;
        }
        if (mixinClassName.endsWith("MixinDrawerItemRepositoryPhase2Probe")
                || mixinClassName.endsWith("MixinControllerItemRepositoryPhase2Exit")) {
            return OptimizationConfig.optimizeDrawerNegativeExtraction
                    && OptimizationConfig.instrumentNegativePhase2Matchers;
        }
        if (mixinClassName.endsWith("MixinItemHandlerAdapterExtractionDiagnostics")) {
            return OptimizationConfig.instrumentItemHandlerExtraction;
        }
        if (mixinClassName.endsWith("MixinItemHandlerAdapterNegativeExtract")) {
            return OptimizationConfig.optimizeExternalItemHandlerNegativeExtraction;
        }
        if (mixinClassName.endsWith("MixinEnderUtilitiesItemStackHandlerBasic")
                || mixinClassName.endsWith("MixinEnderUtilitiesItemHandlerWrapperJSU")) {
            return OptimizationConfig.optimizeExternalItemHandlerNegativeExtraction
                    && CompatibilityCheck.isUsable(CompatibilityCheck.checkEnderUtilities());
        }
        if (mixinClassName.endsWith("MixinActuallyAdditionsTileStackHandler")) {
            return OptimizationConfig.optimizeExternalItemHandlerNegativeExtraction
                    && CompatibilityCheck.isUsable(CompatibilityCheck.checkActuallyAdditions());
        }
        if (mixinClassName.endsWith("MixinPartEssentiaStorageBus")
                || mixinClassName.endsWith("MixinEssentiaContainerAdapter")) {
            return OptimizationConfig.optimizeThaumicEnergisticsIncrementalUpdate
                    && CompatibilityCheck.isUsable(CompatibilityCheck.checkThaumicEnergistics());
        }
        if (mixinClassName.endsWith("MixinDualityInterfaceTransferRouting")
                || mixinClassName.endsWith("MixinItemRepositoryAdapterInterfaceRouting")) {
            return OptimizationConfig.optimizeInterfaceTransferRouting;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        MixinStatus.markApplied(mixinClassName, targetClassName);
    }
}
