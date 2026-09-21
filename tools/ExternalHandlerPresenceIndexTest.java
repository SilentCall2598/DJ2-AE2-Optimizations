import dj2.ae2opt.core.ExternalHandlerPresenceIndex;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.List;


public final class ExternalHandlerPresenceIndexTest {

    static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            System.out.println("pass  " + name);
        } else {
            System.out.println("FAIL  " + name + ": " + detail);
            failures++;
        }
    }

    static final class FakeSlots implements ExternalHandlerPresenceIndex.SlotSource {
        List<ItemStack> slots = new ArrayList<ItemStack>();
        int rebuildCalls;
        boolean throwOnScan;

        int addSlot(ItemStack stack) {
            this.slots.add(stack);
            return this.slots.size() - 1;
        }

        @Override
        public int dj2ae2opt$slots() {
            return this.slots.size();
        }

        @Override
        public ItemStack dj2ae2opt$stackInSlot(int slot) {
            this.rebuildCalls++;
            if (this.throwOnScan) {
                throw new RuntimeException("simulated broken handler");
            }
            return this.slots.get(slot);
        }
    }

    static ItemStack stack(Item item, int meta) {
        return new ItemStack(item, 1, meta);
    }

    static void presentItemIsFound() {
        final FakeSlots slots = new FakeSlots();
        slots.addSlot(stack(Items.DIAMOND, 0));
        final ExternalHandlerPresenceIndex index = new ExternalHandlerPresenceIndex();
        check("a stored item is reported as might-contain",
                index.mightContain(slots, stack(Items.DIAMOND, 0)), "expected true");
    }

    static void absentItemIsNotFound() {
        final FakeSlots slots = new FakeSlots();
        slots.addSlot(stack(Items.DIAMOND, 0));
        final ExternalHandlerPresenceIndex index = new ExternalHandlerPresenceIndex();
        check("an unrelated item is reported as definitely absent",
                !index.mightContain(slots, stack(Items.IRON_INGOT, 0)), "expected false");
    }

    static void emptySlotsContributeNoKey() {
        final FakeSlots slots = new FakeSlots();
        slots.addSlot(ItemStack.EMPTY);
        slots.addSlot(stack(Item.getItemFromBlock(Blocks.STONE), 0));
        final ExternalHandlerPresenceIndex index = new ExternalHandlerPresenceIndex();
        check("an empty slot contributes no candidate key",
                !index.mightContain(slots, stack(Items.DIAMOND, 0)), "expected false");
        check("a populated slot among empty ones is still found",
                index.mightContain(slots, stack(Item.getItemFromBlock(Blocks.STONE), 0)), "expected true");
    }

    static void nbtIsNotPartOfTheKey() {
        final FakeSlots slots = new FakeSlots();
        final ItemStack withTag = stack(Items.DIAMOND, 0);
        withTag.setTagCompound(new NBTTagCompound());
        slots.addSlot(withTag);
        final ExternalHandlerPresenceIndex index = new ExternalHandlerPresenceIndex();
        check("a request with different NBT than the stored stack is still a conservative might-contain",
                index.mightContain(slots, stack(Items.DIAMOND, 0)), "expected true (false positive allowed)");
    }

    static void noRebuildWithoutInvalidation() {
        final FakeSlots slots = new FakeSlots();
        slots.addSlot(stack(Items.DIAMOND, 0));
        final ExternalHandlerPresenceIndex index = new ExternalHandlerPresenceIndex();
        index.mightContain(slots, stack(Items.DIAMOND, 0));
        final int callsAfterFirst = slots.rebuildCalls;
        index.mightContain(slots, stack(Items.DIAMOND, 0));
        index.mightContain(slots, stack(Items.IRON_INGOT, 0));
        check("repeated queries do not rescan the handler without an invalidation signal",
                slots.rebuildCalls == callsAfterFirst, slots.rebuildCalls + " vs " + callsAfterFirst);
    }

    static void markDirtyForcesRebuildOnNextQuery() {
        final FakeSlots slots = new FakeSlots();
        final int slot = slots.addSlot(ItemStack.EMPTY);
        final ExternalHandlerPresenceIndex index = new ExternalHandlerPresenceIndex();
        check("a value not yet present is absent before the change",
                !index.mightContain(slots, stack(Items.DIAMOND, 0)), "expected false");

        slots.slots.set(slot, stack(Items.DIAMOND, 0));
        index.markDirty();
        check("the same value is found once markDirty forces a rescan",
                index.mightContain(slots, stack(Items.DIAMOND, 0)), "expected true");
    }

    static void aThrowingScanFailsOpen() {
        final FakeSlots slots = new FakeSlots();
        slots.addSlot(stack(Items.DIAMOND, 0));
        slots.throwOnScan = true;
        final ExternalHandlerPresenceIndex index = new ExternalHandlerPresenceIndex();
        check("a handler that throws while being scanned fails open rather than propagating",
                index.mightContain(slots, stack(Items.IRON_INGOT, 0)), "expected true (fail open)");
    }

    static void aNullOrEmptyRequestFailsOpen() {
        final FakeSlots slots = new FakeSlots();
        slots.addSlot(stack(Items.DIAMOND, 0));
        final ExternalHandlerPresenceIndex index = new ExternalHandlerPresenceIndex();
        check("a null request fails open", index.mightContain(slots, null), "expected true");
        check("an empty request fails open",
                index.mightContain(slots, ItemStack.EMPTY), "expected true");
    }

    public static void main(String[] args) {
        Bootstrap.register();

        presentItemIsFound();
        absentItemIsNotFound();
        emptySlotsContributeNoKey();
        nbtIsNotPartOfTheKey();
        noRebuildWithoutInvalidation();
        markDirtyForcesRebuildOnNextQuery();
        aThrowingScanFailsOpen();
        aNullOrEmptyRequestFailsOpen();

        System.out.println(failures == 0 ? "\nExternalHandlerPresenceIndexTest: ALL PASS"
                : "\nExternalHandlerPresenceIndexTest: " + failures + " FAILURES");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
