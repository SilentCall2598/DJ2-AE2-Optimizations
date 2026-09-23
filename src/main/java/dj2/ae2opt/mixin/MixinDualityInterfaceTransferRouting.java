package dj2.ae2opt.mixin;

import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStack;
import appeng.util.Platform;
import dj2.ae2opt.core.Diagnostics;
import dj2.ae2opt.core.InterfaceTransferContext;
import dj2.ae2opt.core.OptimizationConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(targets = "appeng.helpers.DualityInterface", remap = false)
public abstract class MixinDualityInterfaceTransferRouting {

    @Redirect(method = "usePlan(ILappeng/api/storage/data/IAEItemStack;)Z",
            at = @At(value = "INVOKE",
                    target = "Lappeng/util/Platform;poweredExtraction(Lappeng/api/networking/energy/IEnergySource;"
                            + "Lappeng/api/storage/IMEInventory;Lappeng/api/storage/data/IAEStack;"
                            + "Lappeng/api/networking/security/IActionSource;)Lappeng/api/storage/data/IAEStack;"),
            require = 1)
    private static IAEStack dj2ae2opt$bracketedPoweredExtraction(IEnergySource energy, IMEInventory cell,
                                                                   IAEStack request, IActionSource src) {
        if (!OptimizationConfig.optimizeInterfaceTransferRouting) {
            return Platform.poweredExtraction(energy, cell, request, src);
        }
        Diagnostics.interfacePoweredExtractionObserved();
        InterfaceTransferContext.enter();
        try {
            return Platform.poweredExtraction(energy, cell, request, src);
        } finally {
            InterfaceTransferContext.exit();
        }
    }

    @Redirect(method = "usePlan(ILappeng/api/storage/data/IAEItemStack;)Z",
            at = @At(value = "INVOKE",
                    target = "Lappeng/util/Platform;poweredInsert(Lappeng/api/networking/energy/IEnergySource;"
                            + "Lappeng/api/storage/IMEInventory;Lappeng/api/storage/data/IAEStack;"
                            + "Lappeng/api/networking/security/IActionSource;)Lappeng/api/storage/data/IAEStack;"),
            require = 1)
    private static IAEStack dj2ae2opt$bracketedPoweredInsert(IEnergySource energy, IMEInventory cell,
                                                               IAEStack input, IActionSource src) {
        if (!OptimizationConfig.optimizeInterfaceTransferRouting) {
            return Platform.poweredInsert(energy, cell, input, src);
        }
        Diagnostics.interfacePoweredInsertObserved();
        InterfaceTransferContext.enter();
        try {
            return Platform.poweredInsert(energy, cell, input, src);
        } finally {
            InterfaceTransferContext.exit();
        }
    }
}
