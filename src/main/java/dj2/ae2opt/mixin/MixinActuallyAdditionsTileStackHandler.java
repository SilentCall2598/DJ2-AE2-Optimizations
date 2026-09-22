package dj2.ae2opt.mixin;

import de.ellpeck.actuallyadditions.mod.tile.TileEntityGiantChestLarge;
import de.ellpeck.actuallyadditions.mod.tile.TileEntityInventoryBase;
import dj2.ae2opt.api.IExternalHandlerPresenceHolder;
import dj2.ae2opt.core.Diagnostics;
import dj2.ae2opt.core.ExternalHandlerPresenceIndex;
import dj2.ae2opt.core.MixinStatus;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(targets = "de.ellpeck.actuallyadditions.mod.tile.TileEntityInventoryBase$TileStackHandler",
        remap = false)
public abstract class MixinActuallyAdditionsTileStackHandler extends ItemStackHandler
        implements IExternalHandlerPresenceHolder, ExternalHandlerPresenceIndex.SlotSource {

    @Shadow
    @Final
    private TileEntityInventoryBase this$0;

    @Unique
    private ExternalHandlerPresenceIndex dj2ae2opt$index;

    @Unique
    private boolean dj2ae2opt$isAuthorizedOwner() {
        final TileEntityInventoryBase owner = this.this$0;
        return owner != null && owner.getClass() == TileEntityGiantChestLarge.class;
    }

    @Inject(method = "onContentsChanged(I)V", at = @At("HEAD"), require = 1)
    private void dj2ae2opt$onContentsChanged(int slot, CallbackInfo ci) {
        if (!this.dj2ae2opt$isAuthorizedOwner()) {
            return;
        }
        final ExternalHandlerPresenceIndex index = this.dj2ae2opt$index;
        if (index == null) {
            return;
        }
        index.markDirty();
        Diagnostics.externalHandlerPresenceInvalidated();
    }

    @Override
    public boolean dj2ae2opt$isAuthorized() {
        return this.dj2ae2opt$isAuthorizedOwner();
    }

    @Override
    public boolean dj2ae2opt$mightContain(ItemStack request) {
        if (!this.dj2ae2opt$isAuthorizedOwner()) {
            return true;
        }
        MixinStatus.Feature.ACTUALLY_ADDITIONS_INTEGRATION.markRuntimeHit();
        ExternalHandlerPresenceIndex index = this.dj2ae2opt$index;
        if (index == null) {
            index = new ExternalHandlerPresenceIndex();
            this.dj2ae2opt$index = index;
        }
        return index.mightContain(this, request);
    }

    @Override
    public int dj2ae2opt$slots() {
        return this.getSlots();
    }

    @Override
    public ItemStack dj2ae2opt$stackInSlot(int slot) {
        return this.getStackInSlot(slot);
    }
}
