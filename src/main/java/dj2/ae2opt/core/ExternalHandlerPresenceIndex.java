package dj2.ae2opt.core;

import net.minecraft.item.ItemStack;

public final class ExternalHandlerPresenceIndex {

    public interface SlotSource {
        int dj2ae2opt$slots();

        ItemStack dj2ae2opt$stackInSlot(int slot);
    }

    private LongKeySet keys;
    private boolean dirty = true;
    private boolean usable = true;

    public void markDirty() {
        this.dirty = true;
    }

    public boolean mightContain(SlotSource source, ItemStack request) {
        if (this.dirty) {
            this.rebuild(source);
        }
        if (!this.usable || request == null || request.isEmpty()) {
            return true;
        }
        return this.keys.contains(DrawerPresenceIndex.key(request));
    }

    private void rebuild(SlotSource source) {
        this.dirty = false;
        try {
            final int slots = source.dj2ae2opt$slots();
            final LongKeySet built = new LongKeySet(Math.max(16, slots));
            for (int i = 0; i < slots; i++) {
                final ItemStack stack = source.dj2ae2opt$stackInSlot(i);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                built.add(DrawerPresenceIndex.key(stack));
            }
            this.keys = built;
            this.usable = true;
        } catch (RuntimeException e) {
            this.keys = null;
            this.usable = false;
            Diagnostics.externalHandlerPresenceBuildFailed();
        }
    }
}
