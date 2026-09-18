package dj2.ae2opt.mixin;

import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawer;
import dj2.ae2opt.api.IDictConvertibleProbe;
import dj2.ae2opt.api.IBuiltInFractionalDrawer;
import dj2.ae2opt.api.IDrawerPresenceHolder;
import dj2.ae2opt.core.Diagnostics;
import dj2.ae2opt.core.DrawerPresenceIndex;
import dj2.ae2opt.core.CandidateSlotSampler;
import dj2.ae2opt.core.OptimizationConfig;
import dj2.ae2opt.core.OreKeyExpander;
import dj2.ae2opt.core.Phase2MatcherContext;
import dj2.ae2opt.core.LongKeySet;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(targets = "com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityController", remap = false)
public abstract class MixinTileEntityController implements IDrawerPresenceHolder {

    @Shadow
    protected int[] drawerSlots;

    @Shadow
    public abstract IDrawer getDrawer(int slot);

    @Unique
    private LongKeySet dj2ae2opt$keys;

    @Unique
    private long dj2ae2opt$indexEpoch;

    @Unique
    private boolean dj2ae2opt$topologyDirty;

    @Unique
    private boolean dj2ae2opt$indexUsable;

    @Inject(method = "updateCache()V", at = @At("RETURN"), require = 1)
    private void dj2ae2opt$onTopologyRebuilt(CallbackInfo ci) {
        this.dj2ae2opt$topologyDirty = true;
    }


    @Override
    public void dj2ae2opt$sampleKeyPresentFallback(final ItemStack request) {
        if (!this.dj2ae2opt$indexUsable) {
            return;
        }
        if (!Diagnostics.shouldSampleNegativeCandidate()) {
            return;
        }

        if (OptimizationConfig.instrumentNegativeCandidateSlots) {
            this.dj2ae2opt$sampleCandidateSlots(request);
        }
        if (OptimizationConfig.instrumentNegativePhase2Matchers) {
            Phase2MatcherContext.enter();
        }
    }

    @Unique
    private void dj2ae2opt$sampleCandidateSlots(final ItemStack request) {
        final int[] slots = this.drawerSlots;
        if (slots == null) {
            Diagnostics.candidateSampleRefused();
            return;
        }
        final long requestKey = DrawerPresenceIndex.key(request);
        final CandidateSlotSampler.Sample sample =
                CandidateSlotSampler.sample(requestKey, new CandidateSlotSampler.SlotSource() {
                    @Override
                    public int count() {
                        return slots.length;
                    }

                    @Override
                    public int slotAt(int i) {
                        return slots[i];
                    }

                    @Override
                    public boolean supported(int i) {
                        final IDrawer drawer = getDrawer(slots[i]);
                        if (drawer == null || !drawer.isEnabled()) {
                            return true;
                        }
                        return drawer instanceof IDictConvertibleProbe
                                || drawer instanceof IBuiltInFractionalDrawer;
                    }

                    @Override
                    public boolean covers(int i, long key) {
                        final ItemStack prototype = dj2ae2opt$prototypeAt(i);
                        return prototype != null && OreKeyExpander.covers(prototype, key);
                    }

                    @Override
                    public boolean literal(int i, long key) {
                        final ItemStack prototype = dj2ae2opt$prototypeAt(i);
                        return prototype != null && DrawerPresenceIndex.key(prototype) == key;
                    }

                    private ItemStack dj2ae2opt$prototypeAt(int i) {
                        final IDrawer drawer = getDrawer(slots[i]);
                        if (drawer == null || !drawer.isEnabled()) {
                            return null;
                        }
                        final ItemStack prototype = drawer.getStoredItemPrototype();
                        return prototype == null || prototype.isEmpty() ? null : prototype;
                    }
                });
        Diagnostics.candidateSampled(sample);
    }

    @Override
    public void dj2ae2opt$markTopologyDirty() {
        this.dj2ae2opt$topologyDirty = true;
    }

    @Override
    public boolean dj2ae2opt$mightContain(ItemStack request) {
        if (this.dj2ae2opt$keys == null
                || this.dj2ae2opt$topologyDirty
                || this.dj2ae2opt$indexEpoch != DrawerPresenceIndex.epoch()) {
            this.dj2ae2opt$rebuild();
        }
        if (!this.dj2ae2opt$indexUsable) {
            Diagnostics.negativeUnindexableFallback();
            return true;
        }
        if (this.dj2ae2opt$keys.contains(DrawerPresenceIndex.key(request))) {
            Diagnostics.negativeKeyPresentFallback();
            return true;
        }
        return false;
    }

    @Unique
    private void dj2ae2opt$rebuild() {
        final boolean topology = this.dj2ae2opt$topologyDirty;
        final int[] slots = this.drawerSlots;
        final LongKeySet keys = new LongKeySet(slots == null ? 16 : slots.length);
        boolean usable = slots != null;
        int fractionalIndexed = 0;
        int dictConvertible = 0;
        int oreKeys = 0;

        if (slots != null) {
            for (int slot : slots) {
                final IDrawer drawer = this.getDrawer(slot);
                if (drawer == null || !drawer.isEnabled()) {
                    continue;
                }


                final boolean standard = drawer instanceof IDictConvertibleProbe;
                final boolean fractional = drawer instanceof IBuiltInFractionalDrawer;
                if (!standard && !fractional) {
                    Diagnostics.presenceUnknownDrawer();
                    usable = false;
                    break;
                }
                if (standard && ((IDictConvertibleProbe) drawer).dj2ae2opt$isDictConvertible()) {
                    dictConvertible++;
                }
                final ItemStack prototype = drawer.getStoredItemPrototype();
                if (prototype == null || prototype.isEmpty()) {
                    continue;
                }
                if (fractional) {
                    fractionalIndexed++;
                }
                try {
                    oreKeys += OreKeyExpander.addKeys(keys, prototype);
                } catch (RuntimeException e) {


                    usable = false;
                    break;
                }
            }
        }

        this.dj2ae2opt$keys = keys;
        this.dj2ae2opt$indexUsable = usable;
        this.dj2ae2opt$indexEpoch = DrawerPresenceIndex.epoch();
        this.dj2ae2opt$topologyDirty = false;
        Diagnostics.presenceIndexRebuiltMarker();
        Diagnostics.presenceIndexRebuilt(topology, usable, keys.size());
        Diagnostics.presenceIndexComposition(usable, fractionalIndexed, dictConvertible, oreKeys);
    }
}
