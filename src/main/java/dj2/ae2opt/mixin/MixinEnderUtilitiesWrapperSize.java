package dj2.ae2opt.mixin;

import dj2.ae2opt.api.IExternalHandlerPresenceHolder;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;


@Mixin(targets = "fi.dy.masa.enderutilities.inventory.wrapper.ItemHandlerWrapperSize",
        remap = false)
public abstract class MixinEnderUtilitiesWrapperSize implements IExternalHandlerPresenceHolder {

    @Shadow
    @Final
    protected IItemHandler baseHandler;

    @Override
    public boolean dj2ae2opt$mightContain(ItemStack request) {
        final IItemHandler base = this.baseHandler;
        if (base instanceof IExternalHandlerPresenceHolder) {
            return ((IExternalHandlerPresenceHolder) base).dj2ae2opt$mightContain(request);
        }
        return true;
    }
}
