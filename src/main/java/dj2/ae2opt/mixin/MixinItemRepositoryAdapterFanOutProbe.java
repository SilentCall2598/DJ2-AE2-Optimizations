package dj2.ae2opt.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import dj2.ae2opt.core.Diagnostics;
import dj2.ae2opt.core.NetworkRequestContext;
import dj2.ae2opt.core.OptimizationConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(targets = "appeng.parts.misc.ItemRepositoryAdapter", remap = false)
public abstract class MixinItemRepositoryAdapterFanOutProbe {

    @Inject(
            method = "extractItems(Lappeng/api/storage/data/IAEItemStack;"
                    + "Lappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)"
                    + "Lappeng/api/storage/data/IAEItemStack;",
            at = @At("RETURN"),
            require = 1)
    private void dj2ae2opt$recordFanOutProbe(IAEItemStack request, Actionable mode, IActionSource src,
                                          CallbackInfoReturnable<IAEItemStack> cir) {
        if (!OptimizationConfig.instrumentNetworkFanOut) {
            return;
        }
        Diagnostics.fanOutProbeObserved();
        final IAEItemStack result = cir.getReturnValue();
        NetworkRequestContext.recordProbe(result == null || result.getStackSize() <= 0L);
    }
}
