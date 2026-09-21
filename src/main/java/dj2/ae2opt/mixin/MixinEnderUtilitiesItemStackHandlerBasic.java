package dj2.ae2opt.mixin;

import dj2.ae2opt.api.IExternalHandlerPresenceSource;
import dj2.ae2opt.core.Diagnostics;
import dj2.ae2opt.core.ExternalHandlerPresenceIndex;
import dj2.ae2opt.core.MixinStatus;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(targets = "fi.dy.masa.enderutilities.inventory.ItemStackHandlerBasic",
        remap = false)
public abstract class MixinEnderUtilitiesItemStackHandlerBasic implements IExternalHandlerPresenceSource,
        ExternalHandlerPresenceIndex.SlotSource {

    @Shadow
    public abstract int getSlots();

    @Shadow
    public abstract ItemStack getStackInSlot(int slot);

    @Unique
    private final ExternalHandlerPresenceIndex dj2ae2opt$index = new ExternalHandlerPresenceIndex();

    @Inject(method = "onContentsChanged(I)V", at = @At("HEAD"), require = 1)
    private void dj2ae2opt$onContentsChanged(int slot, CallbackInfo ci) {
        this.dj2ae2opt$index.markDirty();
        MixinStatus.Feature.ENDER_UTILITIES_INTEGRATION.markRuntimeHit();
        Diagnostics.externalHandlerPresenceInvalidated();
    }

    @Inject(method = "deserializeNBT(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("HEAD"), require = 1)
    private void dj2ae2opt$onDeserializeNBT(NBTTagCompound nbt, CallbackInfo ci) {
        this.dj2ae2opt$index.markDirty();
    }

    @Override
    public boolean dj2ae2opt$mightContain(ItemStack request) {
        return this.dj2ae2opt$index.mightContain(this, request);
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
