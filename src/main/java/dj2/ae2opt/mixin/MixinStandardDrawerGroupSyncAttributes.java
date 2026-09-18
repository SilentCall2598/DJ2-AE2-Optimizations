package dj2.ae2opt.mixin;

import dj2.ae2opt.core.DrawerPresenceIndex;
import dj2.ae2opt.core.OptimizationConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(targets = "com.jaquadro.minecraft.storagedrawers.block.tile.tiledata.StandardDrawerGroup", remap = false)
public abstract class MixinStandardDrawerGroupSyncAttributes {

    @Inject(method = "syncAttributes()V", at = @At("RETURN"), require = 1)
    private void dj2ae2opt$invalidateOnMatcherChange(CallbackInfo ci) {
        if (OptimizationConfig.optimizeDrawerNegativeExtraction) {
            DrawerPresenceIndex.bumpEpochForAttributes();
        }
    }
}
