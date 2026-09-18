package dj2.ae2opt.mixin;

import appeng.api.storage.IStorageChannel;
import dj2.ae2opt.core.Diagnostics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(targets = "appeng.me.cache.NetworkMonitor", remap = false)
public abstract class MixinNetworkMonitor {

    @Shadow
    @Final
    private IStorageChannel myChannel;

    @Inject(method = "setForceUpdate", at = @At("HEAD"), require = 1, allow = 1)
    private void dj2ae2opt$countForceUpdateRequest(boolean forceUpdate, CallbackInfo ci) {
        if (forceUpdate) {
            Diagnostics.forceUpdateRequested(this.dj2ae2opt$channelName());
        }
    }

    @Inject(method = "forceUpdate()V", at = @At("HEAD"), require = 1, allow = 1)
    private void dj2ae2opt$forceUpdateStart(CallbackInfo ci) {
        Diagnostics.forceUpdateEnter();
    }


    @Inject(method = "forceUpdate()V", at = @At("RETURN"), require = 1)
    private void dj2ae2opt$forceUpdateEnd(CallbackInfo ci) {
        Diagnostics.forceUpdateExit(this.dj2ae2opt$channelName());
    }

    @Unique
    private String dj2ae2opt$channelName() {
        return this.myChannel == null ? "unknown" : this.myChannel.getClass().getSimpleName();
    }
}
