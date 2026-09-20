package dj2.ae2opt.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(targets = "appeng.parts.misc.ItemHandlerAdapter", remap = false)
public abstract class MixinBadInvokeRedirect {

    @Redirect(
            method = "extractItems(Lappeng/api/storage/data/IAEItemStack;Lappeng/api/config/Actionable;"
                    + "Lappeng/api/networking/security/IActionSource;)Lappeng/api/storage/data/IAEItemStack;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/items/IItemHandler;notARealMethod(I)Lnet/minecraft/item/ItemStack;"),
            require = 1)
    private ItemStack bad(IItemHandler handler, int slot) {
        return null;
    }
}
