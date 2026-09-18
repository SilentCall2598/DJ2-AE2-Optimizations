package dj2.ae2opt.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import dj2.ae2opt.core.Diagnostics;
import dj2.ae2opt.core.ExtractionContext;
import dj2.ae2opt.core.MixinStatus;
import dj2.ae2opt.core.OptimizationConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(targets = "appeng.util.Platform", remap = false)
public abstract class MixinPlatformPoweredExtractionDiagnostics {

    @Inject(
            method = "poweredExtraction(Lappeng/api/networking/energy/IEnergySource;Lappeng/api/storage/IMEInventory;Lappeng/api/storage/data/IAEStack;Lappeng/api/networking/security/IActionSource;Lappeng/api/config/Actionable;)Lappeng/api/storage/data/IAEStack;",
            at = @At("HEAD"),
            require = 1,
            allow = 1)
    private static void dj2ae2opt$beginPoweredExtraction(IEnergySource energy, IMEInventory inventory,
                                                       IAEStack request, IActionSource src,
                                                       Actionable mode,
                                                       CallbackInfoReturnable<IAEStack> cir) {
        if (!OptimizationConfig.instrumentExtractionTransactions || !(request instanceof IAEItemStack)) {
            return;
        }

        ExtractionContext.enterPoweredExtraction();
        MixinStatus.Feature.POWERED_EXTRACTION_CONTEXT.markRuntimeHit();
        Diagnostics.poweredExtractionEntered(mode == Actionable.SIMULATE);
    }

    @Inject(
            method = "poweredExtraction(Lappeng/api/networking/energy/IEnergySource;Lappeng/api/storage/IMEInventory;Lappeng/api/storage/data/IAEStack;Lappeng/api/networking/security/IActionSource;Lappeng/api/config/Actionable;)Lappeng/api/storage/data/IAEStack;",
            at = @At("RETURN"),


            require = 1)
    private static void dj2ae2opt$endPoweredExtraction(IEnergySource energy, IMEInventory inventory,
                                                     IAEStack request, IActionSource src,
                                                     Actionable mode,
                                                     CallbackInfoReturnable<IAEStack> cir) {
        if (!OptimizationConfig.instrumentExtractionTransactions || !(request instanceof IAEItemStack)) {
            return;
        }
        ExtractionContext.exitPoweredExtraction();
    }
}
