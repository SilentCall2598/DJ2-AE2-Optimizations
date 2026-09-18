package dj2.ae2opt.mixin;

import appeng.api.networking.events.MENetworkCellArrayUpdate;
import dj2.ae2opt.core.Diagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(targets = "appeng.me.cache.GridStorageCache", remap = false)
public abstract class MixinGridStorageCache {

    @Inject(method = "cellUpdate", at = @At("HEAD"), require = 1, allow = 1)
    private void dj2ae2opt$cellUpdateStart(MENetworkCellArrayUpdate ev, CallbackInfo ci) {
        Diagnostics.cellUpdateEnter(ev != null);
    }


    @Inject(method = "cellUpdate", at = @At("RETURN"), require = 1)
    private void dj2ae2opt$cellUpdateEnd(MENetworkCellArrayUpdate ev, CallbackInfo ci) {
        Diagnostics.cellUpdateExit();
    }
}
