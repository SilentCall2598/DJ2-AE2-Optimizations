package dj2.ae2opt.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(targets = "appeng.parts.misc.ItemRepositoryAdapter", remap = false)
public abstract class MixinBadAllow {
    @Inject(
            method = "extractItems(Lappeng/api/storage/data/IAEItemStack;"
                    + "Lappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)"
                    + "Lappeng/api/storage/data/IAEItemStack;",
            at = @At("RETURN"), require = 1, allow = 1)
    private void bad(IAEItemStack r, Actionable m, IActionSource s, CallbackInfoReturnable<IAEItemStack> cir) { }

    @org.spongepowered.asm.mixin.Shadow
    private int notARealField;
}
