package dj2.ae2opt.mixin;

import dj2.ae2opt.core.DrawerPresenceIndex;
import dj2.ae2opt.core.OptimizationConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(targets = "com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityDrawersStandard$StandardDrawerData", remap = false)
public abstract class MixinStandardDrawerItemChanged {

    @Inject(method = "onItemChanged()V", at = @At("HEAD"), require = 1, allow = 1)
    private void dj2ae2opt$invalidatePresence(CallbackInfo ci) {
        if (OptimizationConfig.optimizeDrawerNegativeExtraction) {
            DrawerPresenceIndex.bumpEpochForStandard();
        }
    }
}
