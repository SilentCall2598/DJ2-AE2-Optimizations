package dj2.ae2opt.mixin;

import com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityController;
import net.minecraft.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Predicate;


@Mixin(targets = "com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityController$ItemRepository",
       remap = false)
public abstract class MixinBadRedirect {

    @Redirect(
            method = "extractItem(Lnet/minecraft/item/ItemStack;IZLjava/util/function/Predicate;)"
                    + "Lnet/minecraft/item/ItemStack;",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/jaquadro/minecraft/storagedrawers/block/tile/TileEntityController;"
                            + "notARealArray:[I",
                    opcode = Opcodes.GETFIELD),
            require = 1)
    private int[] bad(TileEntityController controller, ItemStack stack, int amount, boolean simulate,
                      Predicate<ItemStack> predicate) {
        return null;
    }
}
