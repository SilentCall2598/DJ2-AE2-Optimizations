package dj2.ae2opt.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import dj2.ae2opt.core.Diagnostics;
import dj2.ae2opt.core.EssentiaSimulationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumicenergistics.api.storage.IAEEssentiaStack;


@Mixin(targets = "thaumicenergistics.integration.appeng.grid.EssentiaContainerAdapter", remap = false)
public abstract class MixinEssentiaContainerAdapter {

    @Inject(method = "injectItems(Lthaumicenergistics/api/storage/IAEEssentiaStack;Lappeng/api/config/Actionable;"
            + "Lappeng/api/networking/security/IActionSource;)Lthaumicenergistics/api/storage/IAEEssentiaStack;",
            at = @At("HEAD"), require = 1)
    private void dj2ae2opt$simulateEnter(IAEEssentiaStack input, Actionable type, IActionSource src,
                                          CallbackInfoReturnable<IAEEssentiaStack> cir) {
        if (type == Actionable.SIMULATE) {
            EssentiaSimulationContext.enter();
            Diagnostics.teSimulationGuardEntered();
        }
    }

    @Inject(method = "injectItems(Lthaumicenergistics/api/storage/IAEEssentiaStack;Lappeng/api/config/Actionable;"
            + "Lappeng/api/networking/security/IActionSource;)Lthaumicenergistics/api/storage/IAEEssentiaStack;",
            at = @At("RETURN"), require = 1)
    private void dj2ae2opt$simulateExit(IAEEssentiaStack input, Actionable type, IActionSource src,
                                         CallbackInfoReturnable<IAEEssentiaStack> cir) {
        if (type == Actionable.SIMULATE) {
            EssentiaSimulationContext.exit();
        }
    }
}
