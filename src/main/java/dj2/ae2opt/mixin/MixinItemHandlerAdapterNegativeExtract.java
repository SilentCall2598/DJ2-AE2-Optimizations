package dj2.ae2opt.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import dj2.ae2opt.api.IExternalHandlerPresenceHolder;
import dj2.ae2opt.core.Diagnostics;
import dj2.ae2opt.core.MixinStatus;
import dj2.ae2opt.core.OptimizationConfig;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(targets = "appeng.parts.misc.ItemHandlerAdapter", remap = false)
public abstract class MixinItemHandlerAdapterNegativeExtract {

    @Shadow
    @Final
    private IItemHandler itemHandler;

    @Inject(
            method = "extractItems(Lappeng/api/storage/data/IAEItemStack;Lappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)Lappeng/api/storage/data/IAEItemStack;",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void dj2ae2opt$negativeExtract(IAEItemStack request, Actionable mode, IActionSource src,
                                            CallbackInfoReturnable<IAEItemStack> cir) {
        if (!OptimizationConfig.optimizeExternalItemHandlerNegativeExtraction || request == null) {
            return;
        }

        final IItemHandler handler = this.itemHandler;
        if (!(handler instanceof IExternalHandlerPresenceHolder)) {
            return;
        }

        final IExternalHandlerPresenceHolder holder = (IExternalHandlerPresenceHolder) handler;
        if (!holder.dj2ae2opt$isAuthorized()) {
            Diagnostics.externalHandlerNegativeDeclinedByScope();
            return;
        }

        Diagnostics.externalHandlerNegativeConsidered();

        final ItemStack prototype = request.getDefinition();
        if (holder.dj2ae2opt$mightContain(prototype)) {
            return;
        }

        Diagnostics.externalHandlerNegativeServed();
        MixinStatus.Feature.EXTERNAL_HANDLER_NEGATIVE_EXTRACTION.markRuntimeHit();
        cir.setReturnValue(null);
    }
}
