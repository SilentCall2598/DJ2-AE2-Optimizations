package dj2.ae2opt.mixin;

import com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityController;
import dj2.ae2opt.api.IDrawerPresenceHolder;
import dj2.ae2opt.core.Diagnostics;
import dj2.ae2opt.core.OptimizationConfig;
import net.minecraft.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Predicate;


@Mixin(targets = "com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityController$ItemRepository",
       remap = false)
public abstract class MixinControllerItemRepositoryNegativeExtract {

    @Unique
    private static final int[] DJ2AE2OPT$NO_SLOTS = new int[0];

    @Shadow
    @Final
    private TileEntityController this$0;

    @Redirect(
            method = "extractItem(Lnet/minecraft/item/ItemStack;IZLjava/util/function/Predicate;)"
                    + "Lnet/minecraft/item/ItemStack;",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/jaquadro/minecraft/storagedrawers/block/tile/TileEntityController;"
                            + "drawerSlots:[I",
                    opcode = Opcodes.GETFIELD),
            require = 1)
    private int[] dj2ae2opt$suppressFallbackScan(TileEntityController controller, ItemStack stack,
                                              int amount, boolean simulate,
                                              Predicate<ItemStack> predicate) {
        final IDrawerPresenceHolder holder =
                controller instanceof IDrawerPresenceHolder ? (IDrawerPresenceHolder) controller : null;
        if (holder == null) {
            Diagnostics.negativeHolderMissing();
            return this.dj2ae2opt$stockSlots(controller);
        }
        if (!OptimizationConfig.optimizeDrawerNegativeExtraction) {
            return dj2ae2opt$stockSlots(controller);
        }
        if (predicate != null) {
            Diagnostics.negativePredicateFallback();
            return dj2ae2opt$stockSlots(controller);
        }
        if (stack == null || stack.isEmpty()) {
            return dj2ae2opt$stockSlots(controller);
        }

        Diagnostics.negativeConsidered();
        if (holder.dj2ae2opt$mightContain(stack)) {
            if (OptimizationConfig.instrumentNegativeCandidateSlots) {
                holder.dj2ae2opt$sampleKeyPresentFallback(stack);
            }
            return dj2ae2opt$stockSlots(controller);
        }

        Diagnostics.negativeServed();
        return DJ2AE2OPT$NO_SLOTS;
    }


    @Unique
    private int[] dj2ae2opt$stockSlots(TileEntityController controller) {
        final int[] slots = controller.getAccessibleDrawerSlots();
        return slots == null ? DJ2AE2OPT$NO_SLOTS : slots;
    }
}
