package dj2.ae2opt.api;

import net.minecraft.item.ItemStack;


public interface IDrawerPresenceHolder {


    boolean dj2ae2opt$mightContain(ItemStack request);


    void dj2ae2opt$sampleKeyPresentFallback(ItemStack request);


    void dj2ae2opt$markTopologyDirty();
}
