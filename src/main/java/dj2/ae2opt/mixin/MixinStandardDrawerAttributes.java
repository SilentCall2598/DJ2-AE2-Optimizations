package dj2.ae2opt.mixin;

import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawerAttributes;
import dj2.ae2opt.api.IDictConvertibleProbe;
import dj2.ae2opt.core.Diagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;


@Mixin(targets = "com.jaquadro.minecraft.storagedrawers.block.tile.tiledata.StandardDrawerGroup$DrawerData",
       remap = false)
public abstract class MixinStandardDrawerAttributes implements IDictConvertibleProbe {


    @Shadow
    IDrawerAttributes attrs;

    @Override
    public boolean dj2ae2opt$isDictConvertible() {
        Diagnostics.conversionProbed();
        return this.attrs == null || this.attrs.isDictConvertible();
    }
}
