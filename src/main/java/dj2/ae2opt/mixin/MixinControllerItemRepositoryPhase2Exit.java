package dj2.ae2opt.mixin;

import dj2.ae2opt.core.OptimizationConfig;
import dj2.ae2opt.core.Phase2MatcherContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;


@Mixin(targets = "com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityController$ItemRepository",
       remap = false)
public abstract class MixinControllerItemRepositoryPhase2Exit {

    @Inject(
            method = "extractItem(Lnet/minecraft/item/ItemStack;IZLjava/util/function/Predicate;)"
                    + "Lnet/minecraft/item/ItemStack;",
            at = @At("RETURN"),
            require = 1)
    private void dj2ae2opt$endPhase2MatcherSample(ItemStack stack, int amount, boolean simulate,
                                               Predicate<ItemStack> predicate,
                                               CallbackInfoReturnable<ItemStack> cir) {
        if (!OptimizationConfig.instrumentNegativePhase2Matchers) {
            return;
        }
        Phase2MatcherContext.exit();
    }
}
