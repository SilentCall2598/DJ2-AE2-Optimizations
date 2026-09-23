package dj2.ae2opt.core;

import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;


public final class DrawerPresenceIndex {


    private static final AtomicLong EPOCH = new AtomicLong(1L);

    private DrawerPresenceIndex() {
    }

    public static long epoch() {
        return EPOCH.get();
    }


    public static void bumpEpochForStandard() {
        EPOCH.incrementAndGet();
        Diagnostics.presenceEpochBumped(Diagnostics.EPOCH_STANDARD);
    }


    public static void bumpEpochForCompacting() {
        EPOCH.incrementAndGet();
        Diagnostics.presenceEpochBumped(Diagnostics.EPOCH_COMPACTING);
    }


    public static void bumpEpochForIdRemap() {
        EPOCH.incrementAndGet();
    }

    public static void bumpEpochForAttributes() {
        EPOCH.incrementAndGet();
        Diagnostics.presenceEpochBumped(Diagnostics.EPOCH_ATTRIBUTES);
    }

    public static long key(int itemId, int metadata) {
        return ((long) itemId << 32) | (metadata & 0xFFFFFFFFL);
    }

    public static long key(ItemStack stack) {
        final Item item = stack.getItem();
        return key(Item.getIdFromItem(item), stack.getMetadata());
    }
}
