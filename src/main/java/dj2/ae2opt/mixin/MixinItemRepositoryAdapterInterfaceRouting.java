package dj2.ae2opt.mixin;

import com.jaquadro.minecraft.storagedrawers.api.capabilities.IItemRepository;
import dj2.ae2opt.core.Diagnostics;
import dj2.ae2opt.core.InterfaceTransferContext;
import dj2.ae2opt.core.MixinStatus;
import dj2.ae2opt.core.OptimizationConfig;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(targets = "appeng.parts.misc.ItemRepositoryAdapter", remap = false)
public abstract class MixinItemRepositoryAdapterInterfaceRouting {

    @Redirect(method = "extractItems(Lappeng/api/storage/data/IAEItemStack;Lappeng/api/config/Actionable;"
            + "Lappeng/api/networking/security/IActionSource;)Lappeng/api/storage/data/IAEItemStack;",
            at = @At(value = "INVOKE",
                    target = "Lcom/jaquadro/minecraft/storagedrawers/api/capabilities/IItemRepository;"
                            + "extractItem(Lnet/minecraft/item/ItemStack;IZ)Lnet/minecraft/item/ItemStack;"),
            require = 1)
    private ItemStack dj2ae2opt$maybeSkipExtract(IItemRepository repo, ItemStack definition, int amount,
                                                   boolean simulate) {
        if (!OptimizationConfig.optimizeInterfaceTransferRouting
                || !InterfaceTransferContext.isActive(InterfaceTransferContext.Kind.EXTRACTION)) {
            return repo.extractItem(definition, amount, simulate);
        }

        Diagnostics.interfaceRoutingHandlerObserved();
        MixinStatus.Feature.INTERFACE_TRANSFER_ROUTING.markRuntimeHit();

        if (simulate) {
            final ItemStack result = repo.extractItem(definition, amount, true);
            if (result.isEmpty()) {
                InterfaceTransferContext.recordNegativeSimulate(InterfaceTransferContext.Kind.EXTRACTION, this);
                Diagnostics.interfaceExtractionRouteNegativeCaptured();
            }
            return result;
        }

        if (InterfaceTransferContext.wasNegativeSimulate(InterfaceTransferContext.Kind.EXTRACTION, this)) {
            Diagnostics.interfaceExtractionHandlerSkippedOnModulate();
            return ItemStack.EMPTY;
        }

        Diagnostics.interfaceHandlerStillVisitedOnModulate();
        return repo.extractItem(definition, amount, false);
    }

    @Redirect(method = "injectItems(Lappeng/api/storage/data/IAEItemStack;Lappeng/api/config/Actionable;"
            + "Lappeng/api/networking/security/IActionSource;)Lappeng/api/storage/data/IAEItemStack;",
            at = @At(value = "INVOKE",
                    target = "Lcom/jaquadro/minecraft/storagedrawers/api/capabilities/IItemRepository;"
                            + "insertItem(Lnet/minecraft/item/ItemStack;Z)Lnet/minecraft/item/ItemStack;"),
            require = 1)
    private ItemStack dj2ae2opt$maybeSkipInsert(IItemRepository repo, ItemStack stack, boolean simulate) {
        if (!OptimizationConfig.optimizeInterfaceTransferRouting
                || !InterfaceTransferContext.isActive(InterfaceTransferContext.Kind.INSERTION)) {
            return repo.insertItem(stack, simulate);
        }

        Diagnostics.interfaceRoutingHandlerObserved();
        MixinStatus.Feature.INTERFACE_TRANSFER_ROUTING.markRuntimeHit();

        if (simulate) {
            final ItemStack result = repo.insertItem(stack, true);
            if (result == stack) {
                InterfaceTransferContext.recordNegativeSimulate(InterfaceTransferContext.Kind.INSERTION, this);
                Diagnostics.interfaceInsertionRouteNegativeCaptured();
            }
            return result;
        }

        if (InterfaceTransferContext.wasNegativeSimulate(InterfaceTransferContext.Kind.INSERTION, this)) {
            Diagnostics.interfaceInsertionHandlerSkippedOnModulate();
            return stack;
        }

        Diagnostics.interfaceHandlerStillVisitedOnModulate();
        return repo.insertItem(stack, false);
    }
}
