package dj2.ae2opt.core;

import appeng.api.storage.data.IAEItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;


public final class PrototypeEntry {

    public final IAEItemStack template;

    private final Item item;
    private final int damage;
    private final NBTTagCompound tag;

    public PrototypeEntry(ItemStack prototype, IAEItemStack template) {
        this.item = prototype.getItem();
        this.damage = prototype.getItemDamage();
        this.tag = prototype.getTagCompound();
        this.template = template;
    }

    public boolean matches(ItemStack prototype) {
        return prototype.getItem() == this.item
                && prototype.getItemDamage() == this.damage
                && prototype.getTagCompound() == this.tag;
    }
}
