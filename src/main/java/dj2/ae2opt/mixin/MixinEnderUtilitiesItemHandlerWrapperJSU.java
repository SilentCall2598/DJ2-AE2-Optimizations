package dj2.ae2opt.mixin;

import dj2.ae2opt.api.IExternalHandlerPresenceHolder;
import dj2.ae2opt.api.IExternalHandlerPresenceSource;
import dj2.ae2opt.core.MixinStatus;
import fi.dy.masa.enderutilities.inventory.ItemStackHandlerTileEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "fi.dy.masa.enderutilities.tileentity.TileEntityJSU$ItemHandlerWrapperJSU",
        remap = false)
public abstract class MixinEnderUtilitiesItemHandlerWrapperJSU implements IExternalHandlerPresenceHolder {

    @Shadow
    @Final
    private ItemStackHandlerTileEntity itemHandlerBase;

    @Override
    public boolean dj2ae2opt$isAuthorized() {
        return this.itemHandlerBase instanceof IExternalHandlerPresenceSource;
    }

    @Override
    public boolean dj2ae2opt$mightContain(ItemStack request) {
        MixinStatus.Feature.ENDER_UTILITIES_JSU_AUTHORIZATION.markRuntimeHit();
        final ItemStackHandlerTileEntity base = this.itemHandlerBase;
        if (base instanceof IExternalHandlerPresenceSource) {
            return ((IExternalHandlerPresenceSource) base).dj2ae2opt$mightContain(request);
        }
        return true;
    }
}
