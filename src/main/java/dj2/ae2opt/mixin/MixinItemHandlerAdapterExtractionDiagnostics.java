package dj2.ae2opt.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import dj2.ae2opt.core.Diagnostics;
import dj2.ae2opt.core.ItemHandlerExtractionStats;
import dj2.ae2opt.core.MixinStatus;
import dj2.ae2opt.core.OptimizationConfig;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(targets = "appeng.parts.misc.ItemHandlerAdapter", remap = false)
public abstract class MixinItemHandlerAdapterExtractionDiagnostics {

    @Shadow
    @Final
    private IItemHandler itemHandler;

    @Unique
    private int dj2ae2opt$slotsExaminedThisCall;

    @Unique
    private boolean dj2ae2opt$classResolved;

    @Unique
    private Class<?> dj2ae2opt$handlerClass;

    @Unique
    private Class<?> dj2ae2opt$ownerClass;

    @Inject(
            method = "extractItems(Lappeng/api/storage/data/IAEItemStack;Lappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)Lappeng/api/storage/data/IAEItemStack;",
            at = @At("HEAD"), require = 1)
    private void dj2ae2opt$resetSlotCounter(IAEItemStack request, Actionable mode, IActionSource src,
                                             CallbackInfoReturnable<IAEItemStack> cir) {
        this.dj2ae2opt$slotsExaminedThisCall = 0;
    }

    @Redirect(
            method = "extractItems(Lappeng/api/storage/data/IAEItemStack;Lappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)Lappeng/api/storage/data/IAEItemStack;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/items/IItemHandler;getStackInSlot(I)Lnet/minecraft/item/ItemStack;"),
            require = 1)
    private ItemStack dj2ae2opt$countSlotExamined(IItemHandler handler, int slot) {
        this.dj2ae2opt$slotsExaminedThisCall++;
        return handler.getStackInSlot(slot);
    }

    @Inject(
            method = "extractItems(Lappeng/api/storage/data/IAEItemStack;Lappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)Lappeng/api/storage/data/IAEItemStack;",
            at = @At("RETURN"), require = 1)
    private void dj2ae2opt$observeExtraction(IAEItemStack request, Actionable mode, IActionSource src,
                                              CallbackInfoReturnable<IAEItemStack> cir) {
        if (!OptimizationConfig.instrumentItemHandlerExtraction || request == null) {
            return;
        }

        MixinStatus.Feature.ITEM_HANDLER_EXTRACTION.markRuntimeHit();

        if (!this.dj2ae2opt$classResolved) {
            this.dj2ae2opt$resolveHandlerClass();
        }

        final IAEItemStack result = cir.getReturnValue();
        final boolean successful = result != null;
        final long requested = Math.max(0L, request.getStackSize());
        final long extracted = successful ? Math.max(0L, result.getStackSize()) : 0L;

        ItemHandlerExtractionStats.record(this.dj2ae2opt$handlerClass, this.dj2ae2opt$ownerClass,
                successful, this.dj2ae2opt$slotsExaminedThisCall, requested, extracted);
        Diagnostics.itemHandlerExtractionObserved();
    }

    @Unique
    private void dj2ae2opt$resolveHandlerClass() {
        this.dj2ae2opt$classResolved = true;
        final IItemHandler handler = this.itemHandler;
        if (handler == null) {
            return;
        }
        this.dj2ae2opt$handlerClass = handler.getClass();
        if (handler instanceof InvWrapper) {
            final Object inv = ((InvWrapper) handler).getInv();
            if (inv != null) {
                this.dj2ae2opt$ownerClass = inv.getClass();
            }
        }
    }
}
