package dj2.ae2opt.mixin;

import dj2.ae2opt.api.IVersionedItemList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.atomic.AtomicInteger;


@Mixin(targets = "appeng.util.item.ItemList", remap = false)
public abstract class MixinItemList implements IVersionedItemList {

    @Shadow
    @Final
    private AtomicInteger version;

    @Override
    public int dj2ae2opt$listVersion() {
        return this.version.get();
    }
}
