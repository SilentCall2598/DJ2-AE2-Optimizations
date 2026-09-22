package dj2.ae2opt.mixin;

import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawer;
import dj2.ae2opt.api.IDictConvertibleProbe;
import dj2.ae2opt.api.IBuiltInFractionalDrawer;
import dj2.ae2opt.api.IDrawerPresenceHolder;
import dj2.ae2opt.core.Diagnostics;
import dj2.ae2opt.core.DrawerCandidateIndex;
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

import java.util.Arrays;


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

    @Unique
    private DrawerCandidateIndex dj2ae2opt$candidateIndex;

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

        final boolean wantCandidateSlots = OptimizationConfig.instrumentNegativeCandidateSlots;
        final boolean wantPhase2Matchers = OptimizationConfig.instrumentNegativePhase2Matchers;
        if (!wantCandidateSlots && !wantPhase2Matchers) {
            return;
        }

        final CandidateSlotSampler.Sample sample = this.dj2ae2opt$sampleCandidateSlots(request);

        if (wantCandidateSlots) {
            Diagnostics.candidateSampled(sample);
        }

        if (wantPhase2Matchers) {
            Diagnostics.phase2MatcherSampleAttempted();
            if (sample.errored) {
                Diagnostics.phase2CandidateModelErrored();
            } else if (sample.refused) {
                Diagnostics.phase2CandidateModelRefused();
            } else {
                Phase2MatcherContext.enter();
            }
        }
    }

    @Unique
    private CandidateSlotSampler.Sample dj2ae2opt$sampleCandidateSlots(final ItemStack request) {
        final int[] slots = this.drawerSlots;
        final long requestKey = DrawerPresenceIndex.key(request);
        if (slots == null) {
            return CandidateSlotSampler.sample(requestKey, null);
        }
        return CandidateSlotSampler.sample(requestKey, new CandidateSlotSampler.SlotSource() {
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

        final boolean wantCandidates = OptimizationConfig.optimizeDrawerCandidateNarrowing && slots != null;
        final DrawerCandidateIndex.Builder candidateBuilder = wantCandidates
                ? new DrawerCandidateIndex.Builder(OptimizationConfig.maxCandidateKeysPerController,
                        OptimizationConfig.maxCandidateSlotReferencesPerController)
                : null;

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
                    if (candidateBuilder != null) {
                        final long literal = DrawerPresenceIndex.key(prototype);
                        final long[] equivalents = OreKeyExpander.equivalentsOf(prototype);
                        candidateBuilder.addOccurrence(slot, literal, equivalents);
                    }
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

        if (candidateBuilder == null) {
            this.dj2ae2opt$candidateIndex = null;
        } else if (!usable) {
            this.dj2ae2opt$candidateIndex = null;
            Diagnostics.candidateIndexBuildFailed();
        } else if (candidateBuilder.keyCapExceeded() || candidateBuilder.slotReferenceCapExceeded()) {
            this.dj2ae2opt$candidateIndex = null;
            if (candidateBuilder.keyCapExceeded()) {
                Diagnostics.candidateIndexKeyCapRefused();
            }
            if (candidateBuilder.slotReferenceCapExceeded()) {
                Diagnostics.candidateIndexSlotRefCapRefused();
            }
        } else {
            this.dj2ae2opt$candidateIndex = candidateBuilder.build();
            Diagnostics.candidateIndexBuilt(this.dj2ae2opt$candidateIndex.keyCount(),
                    this.dj2ae2opt$candidateIndex.slotReferenceCount());
        }
    }

    @Override
    public int[] dj2ae2opt$candidateSlotsFor(ItemStack request) {
        Diagnostics.candidateNarrowingEligible();
        final DrawerCandidateIndex index = this.dj2ae2opt$candidateIndex;
        if (index == null) {
            Diagnostics.candidateNarrowingStockFallback();
            return null;
        }

        final long key = DrawerPresenceIndex.key(request);
        final int[] candidates = index.candidatesFor(key);
        if (candidates == null) {
            Diagnostics.candidateNarrowingInvariantFallback();
            Diagnostics.candidateNarrowingStockFallback();
            return null;
        }

        if (OptimizationConfig.instrumentCandidateIndexVerification
                && Diagnostics.shouldSampleCandidateVerification()
                && !this.dj2ae2opt$verifyCandidates(key, candidates)) {
            Diagnostics.candidateNarrowingStockFallback();
            return null;
        }

        final int fullLength = this.drawerSlots == null ? 0 : this.drawerSlots.length;
        Diagnostics.candidateNarrowingServed(candidates.length, fullLength);
        return candidates;
    }

    @Unique
    private boolean dj2ae2opt$verifyCandidates(long key, int[] indexed) {
        final int[] slots = this.drawerSlots;
        if (slots == null) {
            Diagnostics.candidateVerificationRefused();
            return false;
        }
        try {
            final int[] recomputed = this.dj2ae2opt$recomputeCandidates(slots, key);
            if (recomputed == null) {
                Diagnostics.candidateVerificationRefused();
                return false;
            }
            if (Arrays.equals(recomputed, indexed)) {
                Diagnostics.candidateVerificationVerified();
                return true;
            }
            Diagnostics.candidateVerificationMismatch();
            return false;
        } catch (RuntimeException e) {
            Diagnostics.candidateVerificationErrored();
            return false;
        }
    }

    @Unique
    private int[] dj2ae2opt$recomputeCandidates(int[] slots, long key) {
        int[] out = new int[8];
        int count = 0;
        for (int slot : slots) {
            final IDrawer drawer = this.getDrawer(slot);
            if (drawer == null || !drawer.isEnabled()) {
                continue;
            }
            if (!(drawer instanceof IDictConvertibleProbe) && !(drawer instanceof IBuiltInFractionalDrawer)) {
                return null;
            }
            final ItemStack prototype = drawer.getStoredItemPrototype();
            if (prototype == null || prototype.isEmpty()) {
                continue;
            }
            if (!OreKeyExpander.covers(prototype, key)) {
                continue;
            }
            if (count == out.length) {
                out = Arrays.copyOf(out, out.length * 2);
            }
            out[count++] = slot;
        }
        return Arrays.copyOf(out, count);
    }
}
