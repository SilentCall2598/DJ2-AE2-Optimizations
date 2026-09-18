package dj2.ae2opt.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import dj2.ae2opt.core.Diagnostics;
import dj2.ae2opt.core.ExtractionContext;
import dj2.ae2opt.core.ExtractionPairTracker;
import dj2.ae2opt.core.MixinStatus;
import dj2.ae2opt.core.OptimizationConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(targets = "appeng.parts.misc.ItemRepositoryAdapter", remap = false)
public abstract class MixinItemRepositoryAdapterExtraction {

    @Unique
    private ExtractionPairTracker dj2ae2opt$pairTracker;

    @Inject(
            method = "extractItems(Lappeng/api/storage/data/IAEItemStack;Lappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)Lappeng/api/storage/data/IAEItemStack;",
            at = @At("RETURN"),

            require = 1)
    private void dj2ae2opt$observeExtraction(IAEItemStack request, Actionable mode, IActionSource src,
                                          CallbackInfoReturnable<IAEItemStack> cir) {
        if (!OptimizationConfig.instrumentExtractionTransactions || request == null || mode == null) {
            return;
        }

        MixinStatus.Feature.EXTRACTION_DIAGNOSTICS.markRuntimeHit();
        Diagnostics.extractionDiagnosticsHit();

        if (this.dj2ae2opt$pairTracker == null) {
            this.dj2ae2opt$pairTracker = new ExtractionPairTracker();
        }

        final long tick = Diagnostics.serverTickCounter();
        if (this.dj2ae2opt$pairTracker.expireBefore(tick)) {
            Diagnostics.extractionSimulationExpired();
        }


        final long requested = Math.min((long) Integer.MAX_VALUE, Math.max(0L, request.getStackSize()));
        final IAEItemStack result = cir.getReturnValue();
        final long returned = result == null ? 0L : Math.max(0L, result.getStackSize());


        final Object typeKey = request.getDefinition();
        final long poweredContextId = ExtractionContext.currentPoweredExtractionId();

        Diagnostics.extractionCall(mode == Actionable.SIMULATE, requested, returned,
                src != null && src.player().isPresent(), src != null && src.machine().isPresent(),
                poweredContextId != 0L);

        if (mode == Actionable.SIMULATE) {
            int observation = this.dj2ae2opt$pairTracker.observeSimulate(
                    src, typeKey, requested, returned, tick, poweredContextId);
            if (observation == ExtractionPairTracker.SIM_REPEAT_SAME_REQUEST) {
                Diagnostics.extractionRepeatedSimulation();
            } else if (observation == ExtractionPairTracker.SIM_SUPERSEDED) {
                Diagnostics.extractionSimulationSuperseded();
            }
        } else {
            int pair = this.dj2ae2opt$pairTracker.observeModulate(
                    src, typeKey, requested, tick, poweredContextId);
            Diagnostics.extractionModulatePair(pair);
        }
    }
}
