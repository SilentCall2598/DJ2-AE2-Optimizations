import dj2.ae2opt.core.PrototypeEntry;

import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;


public final class PrototypeEntryTest {

    static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            System.out.println("pass  " + name);
        } else {
            System.out.println("FAIL  " + name + ": " + detail);
            failures++;
        }
    }

    static ItemStack stack(net.minecraft.item.Item item, int meta) {
        return new ItemStack(item, 1, meta);
    }

    static void taglessMatchesTagless() {
        final ItemStack prototype = stack(Items.DIAMOND, 0);
        final PrototypeEntry entry = new PrototypeEntry(prototype, null);
        check("a tagless prototype matches itself on the next poll",
                entry.matches(stack(Items.DIAMOND, 0)), "expected true");
    }

    static void differentItemDoesNotMatch() {
        final ItemStack prototype = stack(Items.DIAMOND, 0);
        final PrototypeEntry entry = new PrototypeEntry(prototype, null);
        check("a different item does not match",
                !entry.matches(stack(Items.IRON_INGOT, 0)), "expected false");
    }

    static void differentMetaDoesNotMatch() {
        final Item wool = Item.getItemFromBlock(Blocks.WOOL);
        final ItemStack prototype = stack(wool, 0);
        final PrototypeEntry entry = new PrototypeEntry(prototype, null);
        check("a different meta does not match",
                !entry.matches(stack(wool, 1)), "expected false");
    }

    static void equalContentDifferentReferenceMatches() {
        final ItemStack prototype = stack(Items.DIAMOND, 0);
        final NBTTagCompound tagA = new NBTTagCompound();
        tagA.setInteger("level", 3);
        prototype.setTagCompound(tagA);
        final PrototypeEntry entry = new PrototypeEntry(prototype, null);

        final ItemStack polled = stack(Items.DIAMOND, 0);
        final NBTTagCompound tagB = new NBTTagCompound();
        tagB.setInteger("level", 3);
        polled.setTagCompound(tagB);

        check("equal NBT content on a distinct NBTTagCompound object still matches",
                entry.matches(polled), "expected true (same content, different reference)");
    }

    static void inPlaceMutationOfTheSameTagIsDetected() {
        final ItemStack prototype = stack(Items.DIAMOND, 0);
        final NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("level", 3);
        prototype.setTagCompound(tag);
        final PrototypeEntry entry = new PrototypeEntry(prototype, null);

        check("before mutation, the same live prototype still matches",
                entry.matches(prototype), "expected true");

        tag.setInteger("level", 4);

        check("mutating the same NBTTagCompound object in place is detected as a content change",
                !entry.matches(prototype), "expected false (identity unchanged, content changed)");
    }

    static void tagAddedAfterConstructionDoesNotMatch() {
        final ItemStack prototype = stack(Items.DIAMOND, 0);
        final PrototypeEntry entry = new PrototypeEntry(prototype, null);

        final ItemStack withTag = stack(Items.DIAMOND, 0);
        withTag.setTagCompound(new NBTTagCompound());

        check("a tag appearing where there was none before does not match",
                !entry.matches(withTag), "expected false");
    }

    static void tagRemovedAfterConstructionDoesNotMatch() {
        final ItemStack prototype = stack(Items.DIAMOND, 0);
        prototype.setTagCompound(new NBTTagCompound());
        final PrototypeEntry entry = new PrototypeEntry(prototype, null);

        check("a tag disappearing where there was one before does not match",
                !entry.matches(stack(Items.DIAMOND, 0)), "expected false");
    }

    public static void main(String[] args) {
        Bootstrap.register();

        taglessMatchesTagless();
        differentItemDoesNotMatch();
        differentMetaDoesNotMatch();
        equalContentDifferentReferenceMatches();
        inPlaceMutationOfTheSameTagIsDetected();
        tagAddedAfterConstructionDoesNotMatch();
        tagRemovedAfterConstructionDoesNotMatch();

        System.out.println(failures == 0 ? "\nPrototypeEntryTest: ALL PASS"
                : "\nPrototypeEntryTest: " + failures + " FAILURES");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
