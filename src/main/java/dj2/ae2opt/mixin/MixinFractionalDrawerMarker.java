package dj2.ae2opt.mixin;

import dj2.ae2opt.api.IBuiltInFractionalDrawer;
import org.spongepowered.asm.mixin.Mixin;


@Mixin(targets = "com.jaquadro.minecraft.storagedrawers.block.tile.tiledata."
        + "FractionalDrawerGroup$FractionalDrawer", remap = false)
public abstract class MixinFractionalDrawerMarker implements IBuiltInFractionalDrawer {
}
