import de.ellpeck.actuallyadditions.mod.tile.TileEntityCompost;
import de.ellpeck.actuallyadditions.mod.tile.TileEntityGiantChestLarge;
import de.ellpeck.actuallyadditions.mod.tile.TileEntityInventoryBase;
import dj2.ae2opt.api.IExternalHandlerPresenceHolder;
import dj2.ae2opt.api.IExternalHandlerPresenceSource;
import dj2.ae2opt.core.ExternalHandlerPresenceIndex;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;


public final class ExternalHandlerAuthorizationScopeTest {

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

    static Object shadowOwner(IItemHandler inv) throws ReflectiveOperationException {
        final Field field = inv.getClass().getDeclaredField("this$0");
        field.setAccessible(true);
        return field.get(inv);
    }

    static void giantChestLargeOwnerMatchesItself() throws ReflectiveOperationException {
        final TileEntityGiantChestLarge owner = new TileEntityGiantChestLarge();
        final Object shadowed = shadowOwner(owner.inv);
        check("TileStackHandler's synthetic this$0 is exactly its owning TileEntityGiantChestLarge",
                shadowed == owner, String.valueOf(shadowed));
    }

    static void unrelatedInventoryBaseOwnerIsNotGiantChestLarge() throws ReflectiveOperationException {
        final TileEntityCompost owner = new TileEntityCompost();
        final Object shadowed = shadowOwner(owner.inv);
        check("an unrelated TileEntityInventoryBase's this$0 is not a TileEntityGiantChestLarge",
                shadowed == owner && !(shadowed instanceof TileEntityGiantChestLarge),
                String.valueOf(shadowed));
    }

    static final class FakeOwnedHandler implements ExternalHandlerPresenceIndex.SlotSource,
            IExternalHandlerPresenceHolder {
        final TileEntityInventoryBase owner;
        final ExternalHandlerPresenceIndex index = new ExternalHandlerPresenceIndex();
        final List<ItemStack> slots = new ArrayList<ItemStack>();

        FakeOwnedHandler(TileEntityInventoryBase owner) {
            this.owner = owner;
        }

        int addSlot(ItemStack item) {
            this.slots.add(item);
            return this.slots.size() - 1;
        }

        @Override
        public int dj2ae2opt$slots() {
            return this.slots.size();
        }

        @Override
        public ItemStack dj2ae2opt$stackInSlot(int slot) {
            return this.slots.get(slot);
        }

        @Override
        public boolean dj2ae2opt$isAuthorized() {
            return this.owner != null && this.owner.getClass() == TileEntityGiantChestLarge.class;
        }

        @Override
        public boolean dj2ae2opt$mightContain(ItemStack request) {
            if (!this.dj2ae2opt$isAuthorized()) {
                return true;
            }
            return this.index.mightContain(this, request);
        }
    }

    static void authorizedOwnerCanProveAbsence() {
        final FakeOwnedHandler handler = new FakeOwnedHandler(new TileEntityGiantChestLarge());
        check("the audited Large Storage Crate is authorized", handler.dj2ae2opt$isAuthorized(), "expected true");
        handler.addSlot(stack(Items.DIAMOND, 0));
        check("the audited Large Storage Crate reports an unrelated item as absent",
                !handler.dj2ae2opt$mightContain(stack(Items.IRON_INGOT, 0)), "expected false");
        check("the audited Large Storage Crate still reports a stored item as present",
                handler.dj2ae2opt$mightContain(stack(Items.DIAMOND, 0)), "expected true");
    }

    static void unauthorizedOwnerNeverProvesAbsence() {
        final FakeOwnedHandler handler = new FakeOwnedHandler(new TileEntityCompost());
        check("an unrelated Actually Additions inventory is not authorized",
                !handler.dj2ae2opt$isAuthorized(), "expected false");
        check("an unrelated Actually Additions inventory never proves absence, even when empty",
                handler.dj2ae2opt$mightContain(stack(Items.IRON_INGOT, 0)), "expected true (declined)");
    }

    static final class FakeGiantChestLargeSubclass extends TileEntityGiantChestLarge {
    }

    static void subclassOfGiantChestLargeIsNotAuthorized() {
        final FakeOwnedHandler handler = new FakeOwnedHandler(new FakeGiantChestLargeSubclass());
        check("a subclass of TileEntityGiantChestLarge is not authorized (exact class only, not instanceof)",
                !handler.dj2ae2opt$isAuthorized(), "expected false");
        check("a subclass of TileEntityGiantChestLarge never proves absence, even when empty",
                handler.dj2ae2opt$mightContain(stack(Items.IRON_INGOT, 0)), "expected true (declined)");
    }

    static final class FakeJSUBackingHandler implements ExternalHandlerPresenceIndex.SlotSource,
            IExternalHandlerPresenceSource {
        final ExternalHandlerPresenceIndex index = new ExternalHandlerPresenceIndex();
        final List<ItemStack> slots = new ArrayList<ItemStack>();

        int addSlot(ItemStack item) {
            this.slots.add(item);
            return this.slots.size() - 1;
        }

        @Override
        public int dj2ae2opt$slots() {
            return this.slots.size();
        }

        @Override
        public ItemStack dj2ae2opt$stackInSlot(int slot) {
            return this.slots.get(slot);
        }

        @Override
        public boolean dj2ae2opt$mightContain(ItemStack request) {
            return this.index.mightContain(this, request);
        }
    }

    static final class FakeWrapper implements IExternalHandlerPresenceHolder {
        final Object base;

        FakeWrapper(Object base) {
            this.base = base;
        }

        @Override
        public boolean dj2ae2opt$isAuthorized() {
            return this.base instanceof IExternalHandlerPresenceSource;
        }

        @Override
        public boolean dj2ae2opt$mightContain(ItemStack request) {
            if (this.base instanceof IExternalHandlerPresenceSource) {
                return ((IExternalHandlerPresenceSource) this.base).dj2ae2opt$mightContain(request);
            }
            return true;
        }
    }

    static void jsuWrapperOverIndexedBackingHandlerProvesAbsence() {
        final FakeJSUBackingHandler backing = new FakeJSUBackingHandler();
        backing.addSlot(stack(Items.DIAMOND, 0));
        final FakeWrapper wrapper = new FakeWrapper(backing);
        check("the JSU wrapper over an indexed backing handler is authorized",
                wrapper.dj2ae2opt$isAuthorized(), "expected true");
        check("the JSU wrapper reports an unrelated item as absent through its indexed backing handler",
                !wrapper.dj2ae2opt$mightContain(stack(Items.IRON_INGOT, 0)), "expected false");
        check("the JSU wrapper reports a stored item as present through its indexed backing handler",
                wrapper.dj2ae2opt$mightContain(stack(Items.DIAMOND, 0)), "expected true");
    }

    static void wrapperOverNonIndexedBackingHandlerNeverProvesAbsence() {
        final Object unindexedBacking = new Object();
        final FakeWrapper wrapper = new FakeWrapper(unindexedBacking);
        check("a wrapper whose backing handler does not maintain a presence index is not authorized",
                !wrapper.dj2ae2opt$isAuthorized(), "expected false");
        check("a wrapper whose backing handler does not maintain a presence index never proves absence",
                wrapper.dj2ae2opt$mightContain(stack(Items.IRON_INGOT, 0)), "expected true (declined)");
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Bootstrap.register();

        giantChestLargeOwnerMatchesItself();
        unrelatedInventoryBaseOwnerIsNotGiantChestLarge();
        authorizedOwnerCanProveAbsence();
        unauthorizedOwnerNeverProvesAbsence();
        subclassOfGiantChestLargeIsNotAuthorized();
        jsuWrapperOverIndexedBackingHandlerProvesAbsence();
        wrapperOverNonIndexedBackingHandlerNeverProvesAbsence();

        System.out.println(failures == 0 ? "\nExternalHandlerAuthorizationScopeTest: ALL PASS"
                : "\nExternalHandlerAuthorizationScopeTest: " + failures + " FAILURES");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
