package dj2.ae2opt.mixin;

import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawer;
import dj2.ae2opt.core.Diagnostics;
import dj2.ae2opt.core.DrawerPresenceIndex;
import dj2.ae2opt.core.OptimizationConfig;
import dj2.ae2opt.core.OreKeyExpander;
import dj2.ae2opt.core.Phase2MatcherContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;


@Mixin(targets = "com.jaquadro.minecraft.storagedrawers.capabilities.DrawerItemRepository", remap = false)
public abstract class MixinDrawerItemRepositoryPhase2Probe {

    @Inject(
            method = "testPredicateExtract(Lcom/jaquadro/minecraft/storagedrawers/api/storage/IDrawer;"
                    + "Lnet/minecraft/item/ItemStack;Ljava/util/function/Predicate;)Z",
            at = @At("RETURN"),
            require = 1)
    private void dj2ae2opt$onPhase2Matcher(IDrawer drawer, ItemStack stack, Predicate<ItemStack> predicate,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (!OptimizationConfig.instrumentNegativePhase2Matchers || !Phase2MatcherContext.active()) {
            return;
        }

        boolean classified = true;
        boolean candidate = false;
        try {
            final ItemStack prototype = drawer == null ? null : drawer.getStoredItemPrototype();
            if (prototype != null && !prototype.isEmpty()) {
                candidate = OreKeyExpander.covers(prototype, DrawerPresenceIndex.key(stack));
            }
        } catch (RuntimeException e) {
            classified = false;
            Diagnostics.phase2MatcherCallClassifyError();
        }

        Phase2MatcherContext.recordMatcherCall(classified && candidate);
        if (classified && !candidate && cir.getReturnValueZ()) {
            Diagnostics.phase2ModelContradiction();
        }
    }
}
