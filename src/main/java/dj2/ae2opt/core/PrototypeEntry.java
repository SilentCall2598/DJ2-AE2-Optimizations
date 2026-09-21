package dj2.ae2opt.core;

import appeng.api.storage.data.IAEItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;


public final class PrototypeEntry {

    public final IAEItemStack template;

    private final Item item;
    private final int damage;
    private final NBTTagCompound tagSnapshot;

    public PrototypeEntry(ItemStack prototype, IAEItemStack template) {
        this.item = prototype.getItem();
        this.damage = prototype.getItemDamage();
        final NBTTagCompound tag = prototype.getTagCompound();
        this.tagSnapshot = tag == null ? null : tag.copy();
        this.template = template;
    }

    public boolean matches(ItemStack prototype) {
        if (prototype.getItem() != this.item || prototype.getItemDamage() != this.damage) {
            return false;
        }
        final NBTTagCompound currentTag = prototype.getTagCompound();
        if (this.tagSnapshot == null) {
            return currentTag == null;
        }
        return currentTag != null && this.tagSnapshot.equals(currentTag);
    }
}
