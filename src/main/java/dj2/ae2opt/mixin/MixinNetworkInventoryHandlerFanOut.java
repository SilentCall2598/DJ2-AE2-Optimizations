package dj2.ae2opt.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import dj2.ae2opt.core.NetworkRequestContext;
import dj2.ae2opt.core.OptimizationConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(targets = "appeng.me.storage.NetworkInventoryHandler", remap = false)
public abstract class MixinNetworkInventoryHandlerFanOut {

    private static final String EXTRACT = "extractItems(Lappeng/api/storage/data/IAEStack;"
            + "Lappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)"
            + "Lappeng/api/storage/data/IAEStack;";

    private static final String INJECT = "injectItems(Lappeng/api/storage/data/IAEStack;"
            + "Lappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)"
            + "Lappeng/api/storage/data/IAEStack;";

    @Inject(method = EXTRACT, at = @At("HEAD"), require = 1, allow = 1)
    private void dj2ae2opt$enterExtract(IAEStack request, Actionable mode, IActionSource src,
                                     CallbackInfoReturnable<IAEStack> cir) {
        if (OptimizationConfig.instrumentNetworkFanOut) {
            NetworkRequestContext.enter(
                    NetworkRequestContext.kindFor(true, mode == Actionable.SIMULATE),
                    request instanceof IAEItemStack);
        }
    }

    @Inject(method = EXTRACT, at = @At("RETURN"), require = 1)
    private void dj2ae2opt$exitExtract(IAEStack request, Actionable mode, IActionSource src,
                                    CallbackInfoReturnable<IAEStack> cir) {
        if (OptimizationConfig.instrumentNetworkFanOut) {
            NetworkRequestContext.exit();
        }
    }


    @Inject(method = INJECT, at = @At("HEAD"), require = 1, allow = 1)
    private void dj2ae2opt$enterInject(IAEStack input, Actionable mode, IActionSource src,
                                    CallbackInfoReturnable<IAEStack> cir) {
        if (OptimizationConfig.instrumentNetworkFanOut) {
            NetworkRequestContext.enter(
                    NetworkRequestContext.kindFor(false, mode == Actionable.SIMULATE),
                    input instanceof IAEItemStack);
        }
    }

    @Inject(method = INJECT, at = @At("RETURN"), require = 1)
    private void dj2ae2opt$exitInject(IAEStack input, Actionable mode, IActionSource src,
                                   CallbackInfoReturnable<IAEStack> cir) {
        if (OptimizationConfig.instrumentNetworkFanOut) {
            NetworkRequestContext.exit();
        }
    }
}
