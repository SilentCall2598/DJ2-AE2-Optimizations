import appeng.api.config.FuzzyMode;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;

import dj2.ae2opt.core.PrototypeEntry;

import io.netty.buffer.ByteBuf;

import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;


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

    static ItemStack stack(Item item, int meta) {
        return new ItemStack(item, 1, meta);
    }

    static final class FakeAEItemStack implements IAEItemStack {
        final ItemStack definition;

        FakeAEItemStack(ItemStack definition) {
            this.definition = definition;
        }

        @Override
        public ItemStack getDefinition() {
            return this.definition;
        }

        @Override
        public ItemStack createItemStack() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasTagCompound() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(IAEItemStack other) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IAEItemStack copy() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Item getItem() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getItemDamage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean sameOre(IAEItemStack other) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSameType(IAEItemStack other) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSameType(ItemStack other) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(ItemStack other) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ItemStack getCachedItemStack(long amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setCachedItemStack(ItemStack stack) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getStackSize() {
            throw new UnsupportedOperationException();
        }

        @Override
        public IAEItemStack setStackSize(long size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getCountRequestable() {
            throw new UnsupportedOperationException();
        }

        @Override
        public IAEItemStack setCountRequestable(long size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCraftable() {
            throw new UnsupportedOperationException();
        }

        @Override
        public IAEItemStack setCraftable(boolean craftable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IAEItemStack reset() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isMeaningful() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void incStackSize(long amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void decStackSize(long amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void incCountRequestable(long amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void decCountRequestable(long amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeToNBT(NBTTagCompound nbt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean fuzzyComparison(IAEItemStack other, FuzzyMode mode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeToPacket(ByteBuf buf) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IAEItemStack empty() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isItem() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isFluid() {
            throw new UnsupportedOperationException();
        }

        @Override
        public IStorageChannel<IAEItemStack> getChannel() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ItemStack asItemStackRepresentation() {
            throw new UnsupportedOperationException();
        }
    }

    static IAEItemStack template(ItemStack prototype) {
        return new FakeAEItemStack(prototype.copy());
    }

    static final class FakeCapabilityProvider implements ICapabilityProvider, INBTSerializable<NBTBase> {
        int state;

        FakeCapabilityProvider(int state) {
            this.state = state;
        }

        @Override
        public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
            return false;
        }

        @Override
        public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
            return null;
        }

        @Override
        public NBTBase serializeNBT() {
            return new NBTTagInt(this.state);
        }

        @Override
        public void deserializeNBT(NBTBase nbt) {
            this.state = ((NBTTagInt) nbt).getInt();
        }
    }

    static void setCapabilityState(ItemStack stack, int state) throws ReflectiveOperationException {
        final Map<ResourceLocation, ICapabilityProvider> providers = new HashMap<ResourceLocation, ICapabilityProvider>();
        providers.put(new ResourceLocation("dj2ae2opttest", "state"), new FakeCapabilityProvider(state));
        final CapabilityDispatcher dispatcher = new CapabilityDispatcher(providers, null);
        final Field field = ItemStack.class.getDeclaredField("capabilities");
        field.setAccessible(true);
        field.set(stack, dispatcher);
    }

    static void taglessMatchesTagless() {
        final ItemStack prototype = stack(Items.DIAMOND, 0);
        final PrototypeEntry entry = new PrototypeEntry(prototype, template(prototype));
        check("a tagless prototype matches itself on the next poll",
                entry.matches(stack(Items.DIAMOND, 0)), "expected true");
    }

    static void differentItemDoesNotMatch() {
        final ItemStack prototype = stack(Items.DIAMOND, 0);
        final PrototypeEntry entry = new PrototypeEntry(prototype, template(prototype));
        check("a different item does not match",
                !entry.matches(stack(Items.IRON_INGOT, 0)), "expected false");
    }

    static void differentMetaDoesNotMatch() {
        final Item wool = Item.getItemFromBlock(Blocks.WOOL);
        final ItemStack prototype = stack(wool, 0);
        final PrototypeEntry entry = new PrototypeEntry(prototype, template(prototype));
        check("a different meta does not match",
                !entry.matches(stack(wool, 1)), "expected false");
    }

    static void equalContentDifferentReferenceMatches() {
        final ItemStack prototype = stack(Items.DIAMOND, 0);
        final NBTTagCompound tagA = new NBTTagCompound();
        tagA.setInteger("level", 3);
        prototype.setTagCompound(tagA);
        final PrototypeEntry entry = new PrototypeEntry(prototype, template(prototype));

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
        final PrototypeEntry entry = new PrototypeEntry(prototype, template(prototype));

        check("before mutation, the same live prototype still matches",
                entry.matches(prototype), "expected true");

        tag.setInteger("level", 4);

        check("mutating the same NBTTagCompound object in place is detected as a content change",
                !entry.matches(prototype), "expected false (identity unchanged, content changed)");
    }

    static void tagAddedAfterConstructionDoesNotMatch() {
        final ItemStack prototype = stack(Items.DIAMOND, 0);
        final PrototypeEntry entry = new PrototypeEntry(prototype, template(prototype));

        final ItemStack withTag = stack(Items.DIAMOND, 0);
        withTag.setTagCompound(new NBTTagCompound());

        check("a tag appearing where there was none before does not match",
                !entry.matches(withTag), "expected false");
    }

    static void tagRemovedAfterConstructionDoesNotMatch() {
        final ItemStack prototype = stack(Items.DIAMOND, 0);
        prototype.setTagCompound(new NBTTagCompound());
        final PrototypeEntry entry = new PrototypeEntry(prototype, template(prototype));

        check("a tag disappearing where there was one before does not match",
                !entry.matches(stack(Items.DIAMOND, 0)), "expected false");
    }

    static void differentCapabilityStateDoesNotMatch() throws ReflectiveOperationException {
        final ItemStack prototype = stack(Items.GOLD_INGOT, 0);
        final IAEItemStack template = template(prototype);
        setCapabilityState(template.getDefinition(), 1);
        final PrototypeEntry entry = new PrototypeEntry(prototype, template);

        final ItemStack pollSameState = stack(Items.GOLD_INGOT, 0);
        setCapabilityState(pollSameState, 1);
        check("a polled stack with matching capability state still matches",
                entry.matches(pollSameState), "expected true");

        final ItemStack pollDifferentState = stack(Items.GOLD_INGOT, 0);
        setCapabilityState(pollDifferentState, 2);
        check("a polled stack with different capability state does not match",
                !entry.matches(pollDifferentState), "expected false");

        final ItemStack pollNoCapabilities = stack(Items.GOLD_INGOT, 0);
        check("a polled stack with no capabilities does not match a cached one that has them",
                !entry.matches(pollNoCapabilities), "expected false");
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Bootstrap.register();

        taglessMatchesTagless();
        differentItemDoesNotMatch();
        differentMetaDoesNotMatch();
        equalContentDifferentReferenceMatches();
        inPlaceMutationOfTheSameTagIsDetected();
        tagAddedAfterConstructionDoesNotMatch();
        tagRemovedAfterConstructionDoesNotMatch();
        differentCapabilityStateDoesNotMatch();

        System.out.println(failures == 0 ? "\nPrototypeEntryTest: ALL PASS"
                : "\nPrototypeEntryTest: " + failures + " FAILURES");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
