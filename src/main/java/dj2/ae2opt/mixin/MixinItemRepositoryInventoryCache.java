package dj2.ae2opt.mixin;

import appeng.api.AEApi;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEItemStack;
import com.jaquadro.minecraft.storagedrawers.api.capabilities.IItemRepository;
import dj2.ae2opt.core.Diagnostics;
import dj2.ae2opt.core.InventoryDiff;
import dj2.ae2opt.core.PrototypeEntry;
import dj2.ae2opt.core.OptimizationConfig;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


@Mixin(targets = "appeng.parts.misc.ItemRepositoryAdapter$InventoryCache", remap = false)
public abstract class MixinItemRepositoryInventoryCache {

    @Shadow
    private IItemList<IAEItemStack> currentlyCached;

    @Shadow
    @Final
    private IItemRepository iItemRepository;

    @Unique
    private IdentityHashMap<ItemStack, PrototypeEntry> dj2ae2opt$templates;

    @Unique
    private boolean dj2ae2opt$templatesDisabled;

    @Unique
    private int dj2ae2opt$pollLookups;

    @Unique
    private int dj2ae2opt$pollHits;

    @Unique
    private static final int DJ2AE2OPT$WARMUP = 0;

    @Unique
    private static final int DJ2AE2OPT$JUDGING = 1;

    @Unique
    private static final int DJ2AE2OPT$CONFIRMED = 2;

    @Unique
    private static final int DJ2AE2OPT$GIVEN_UP = 3;

    @Unique
    private int dj2ae2opt$stability;

    @Unique
    private int dj2ae2opt$judgedRebuilds;

    @Unique
    private long dj2ae2opt$judgedLookups;

    @Unique
    private long dj2ae2opt$judgedHits;

    @Unique
    private IdentityHashMap<ItemStack, DJ2AE2OPT$SteadyCounter> dj2ae2opt$steadyCounters;

    @Unique
    private long dj2ae2opt$steadyGeneration;

    @Unique
    private boolean dj2ae2opt$steadyStateDisabled;

    @Inject(method = "update()Ljava/util/List;", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void dj2ae2opt$update(CallbackInfoReturnable<List<IAEItemStack>> cir) {
        if (!OptimizationConfig.optimizeDrawerInventoryPolling) {
            return;
        }

        final Collection<IItemRepository.ItemRecord> records;
        try {
            records = this.iItemRepository.getAllItems();
        } catch (Throwable t) {
            Diagnostics.pollFailed(t);
            return;
        }
        if (records == null) {
            return;
        }
        Diagnostics.drawerOptimizedPathHit();

        final int count = records.size();

        if (!this.dj2ae2opt$templatesDisabled && count > OptimizationConfig.maxLivePrototypesPerBus) {
            Diagnostics.templateCacheOversized(count, OptimizationConfig.maxLivePrototypesPerBus);
            this.dj2ae2opt$disableTemplates();
        }

        this.dj2ae2opt$pollLookups = 0;
        this.dj2ae2opt$pollHits = 0;

        final boolean steadyEligible = OptimizationConfig.optimizeDrawerSteadyStatePolling
                && !this.dj2ae2opt$templatesDisabled
                && !this.dj2ae2opt$steadyStateDisabled
                && this.dj2ae2opt$stability == DJ2AE2OPT$CONFIRMED;

        if (steadyEligible) {
            Diagnostics.steadyStateEligible();
            try {
                final List<IAEItemStack> changes = this.dj2ae2opt$steadyStateUpdate(records, count);
                this.dj2ae2opt$judgeIdentityStability();
                Diagnostics.steadyStateServed(changes.size());
                cir.setReturnValue(changes);
                return;
            } catch (Throwable t) {
                Diagnostics.steadyStateInvariantFailure(t);
                this.dj2ae2opt$steadyStateDisabled = true;
                this.dj2ae2opt$steadyCounters = null;
            }
        }

        final IdentityHashMap<ItemStack, PrototypeEntry> templates = this.dj2ae2opt$templates;
        final boolean needPruneSnapshot = !this.dj2ae2opt$templatesDisabled
                && templates != null
                && templates.size() > dj2ae2opt$pruneThreshold(count);

        final ItemStack[] protos = needPruneSnapshot ? new ItemStack[count] : null;

        final IItemList<IAEItemStack> currentlyOnStorage =
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createList();

        int index = 0;
        for (IItemRepository.ItemRecord rec : records) {
            final ItemStack prototype = rec.itemPrototype;
            final long size = rec.count;

            if (index < count && protos != null) {
                protos[index] = prototype;
            }
            index++;

            if (prototype == null || prototype.isEmpty()) {
                Diagnostics.malformedPrototypeFallback();
                return;
            }

            final IAEItemStack stack;
            if (this.dj2ae2opt$templatesDisabled) {

                final IAEItemStack converted = AEItemStack.fromItemStack(prototype);
                if (converted == null) {
                    Diagnostics.malformedPrototypeFallback();
                    return;
                }
                Diagnostics.fallbackConversion();
                stack = converted.setStackSize(size);
            } else {
                final IAEItemStack template = this.dj2ae2opt$template(prototype);
                if (template == null) {
                    Diagnostics.malformedPrototypeFallback();
                    return;
                }

                stack = template.copy().setStackSize(size);
            }
            currentlyOnStorage.add(stack);
        }

        final List<IAEItemStack> changes = new ArrayList<IAEItemStack>();

        final boolean useDirectDiff = OptimizationConfig.optimizeDrawerInventoryDiff
                && !this.dj2ae2opt$templatesDisabled
                && this.dj2ae2opt$stability == DJ2AE2OPT$CONFIRMED;

        if (useDirectDiff) {
            InventoryDiff.compute(
                    currentlyOnStorage, this.currentlyCached::findPrecise,
                    this.currentlyCached, currentlyOnStorage::findPrecise,
                    IAEItemStack::getStackSize,
                    (identity, delta) -> identity.copy().setStackSize(delta),
                    changes);
            Diagnostics.directDiffPollUsed();
        } else {
            for (IAEItemStack is : this.currentlyCached) {
                is.setStackSize(-is.getStackSize());
            }
            for (IAEItemStack is : currentlyOnStorage) {
                this.currentlyCached.add(is);
            }
            for (IAEItemStack is : this.currentlyCached) {
                if (is.getStackSize() != 0L) {
                    changes.add(is);
                }
            }
        }
        this.currentlyCached = currentlyOnStorage;

        final boolean aligned = index == count;

        if (needPruneSnapshot && aligned && protos != null) {
            this.dj2ae2opt$pruneTemplates(protos, count);
        }

        this.dj2ae2opt$judgeIdentityStability();

        Diagnostics.pollRebuilt(changes.size());
        cir.setReturnValue(changes);
    }

    @Unique
    private List<IAEItemStack> dj2ae2opt$steadyStateUpdate(Collection<IItemRepository.ItemRecord> records, int count) {
        IdentityHashMap<ItemStack, DJ2AE2OPT$SteadyCounter> counters = this.dj2ae2opt$steadyCounters;
        if (counters == null) {
            counters = new IdentityHashMap<ItemStack, DJ2AE2OPT$SteadyCounter>();
            this.dj2ae2opt$steadyCounters = counters;
        }

        final long generation = ++this.dj2ae2opt$steadyGeneration;

        for (IItemRepository.ItemRecord rec : records) {
            final ItemStack prototype = rec.itemPrototype;
            final long size = rec.count;

            if (prototype == null || prototype.isEmpty()) {
                throw new IllegalStateException("malformed prototype in steady-state poll");
            }

            final IAEItemStack template = this.dj2ae2opt$template(prototype);
            if (template == null) {
                throw new IllegalStateException("unconvertible prototype in steady-state poll");
            }

            final ItemStack definition = template.getDefinition();
            DJ2AE2OPT$SteadyCounter counter = counters.get(definition);
            if (counter == null) {
                counter = new DJ2AE2OPT$SteadyCounter();
                counters.put(definition, counter);
            }
            if (counter.generation != generation) {
                counter.generation = generation;
                counter.representative = template;
                counter.value = size;
            } else {
                counter.value += size;
            }
            Diagnostics.steadyStateRecordExamined();
        }

        final List<IAEItemStack> changes = new ArrayList<IAEItemStack>();
        final IItemList<IAEItemStack> cached = this.currentlyCached;

        for (Map.Entry<ItemStack, DJ2AE2OPT$SteadyCounter> entry : counters.entrySet()) {
            final DJ2AE2OPT$SteadyCounter counter = entry.getValue();
            if (counter.generation != generation) {
                continue;
            }
            final IAEItemStack existing = cached.findPrecise(counter.representative);
            if (existing != null) {
                final long delta = counter.value - existing.getStackSize();
                if (delta != 0L) {
                    existing.incStackSize(delta);
                    changes.add(existing.copy().setStackSize(delta));
                    Diagnostics.steadyStateCorrection();
                }
            } else {
                final IAEItemStack fresh = counter.representative.copy().setStackSize(counter.value);
                cached.add(fresh);
                changes.add(fresh.copy());
                Diagnostics.steadyStatePrototypeAdded();
            }
        }

        for (IAEItemStack existing : cached) {
            final ItemStack existingDefinition = existing.getDefinition();
            final DJ2AE2OPT$SteadyCounter counter = counters.get(existingDefinition);
            if ((counter == null || counter.generation != generation) && existing.getStackSize() != 0L) {
                final long old = existing.getStackSize();
                existing.setStackSize(0L);
                changes.add(existing.copy().setStackSize(-old));
                Diagnostics.steadyStatePrototypeRemoved();
            }
        }

        if (counters.size() > dj2ae2opt$pruneThreshold(count)) {
            final Iterator<Map.Entry<ItemStack, DJ2AE2OPT$SteadyCounter>> pruneIt = counters.entrySet().iterator();
            int removed = 0;
            while (pruneIt.hasNext()) {
                if (pruneIt.next().getValue().generation != generation) {
                    pruneIt.remove();
                    removed++;
                }
            }
            if (removed > 0) {
                Diagnostics.steadyStateCountersPruned(removed);
            }
        }

        return changes;
    }

    private static final class DJ2AE2OPT$SteadyCounter {
        private long value;
        private long generation;
        private IAEItemStack representative;
    }

    @Unique
    private IAEItemStack dj2ae2opt$template(ItemStack prototype) {
        IdentityHashMap<ItemStack, PrototypeEntry> templates = this.dj2ae2opt$templates;
        if (templates == null) {
            templates = new IdentityHashMap<ItemStack, PrototypeEntry>();
            this.dj2ae2opt$templates = templates;
        }

        this.dj2ae2opt$pollLookups++;
        final PrototypeEntry entry = templates.get(prototype);
        if (entry != null && entry.matches(prototype)) {
            this.dj2ae2opt$pollHits++;
            Diagnostics.templateHit();
            return entry.template;
        }

        Diagnostics.templateMiss();
        final IAEItemStack template = AEItemStack.fromItemStack(prototype);
        if (template == null) {
            return null;
        }
        templates.put(prototype, new PrototypeEntry(prototype, template));
        return template;
    }


    @Unique
    private void dj2ae2opt$judgeIdentityStability() {
        final int state = this.dj2ae2opt$stability;
        if (state == DJ2AE2OPT$CONFIRMED || state == DJ2AE2OPT$GIVEN_UP) {
            return;
        }
        if (state == DJ2AE2OPT$WARMUP) {
            this.dj2ae2opt$stability = DJ2AE2OPT$JUDGING;
            return;
        }
        if (!OptimizationConfig.autoDisableTemplateCacheOnLowHitRate) {
            this.dj2ae2opt$stability = DJ2AE2OPT$CONFIRMED;
            return;
        }

        this.dj2ae2opt$judgedRebuilds++;
        this.dj2ae2opt$judgedLookups += this.dj2ae2opt$pollLookups;
        this.dj2ae2opt$judgedHits += this.dj2ae2opt$pollHits;

        if (this.dj2ae2opt$judgedRebuilds < 3 || this.dj2ae2opt$judgedLookups < 512L) {
            return;
        }

        if (this.dj2ae2opt$judgedHits * 4L >= this.dj2ae2opt$judgedLookups) {
            this.dj2ae2opt$stability = DJ2AE2OPT$CONFIRMED;
            Diagnostics.templateCacheConfirmed(this.dj2ae2opt$judgedHits, this.dj2ae2opt$judgedLookups);
            return;
        }

        Diagnostics.templateCacheDisabled(this.dj2ae2opt$judgedHits, this.dj2ae2opt$judgedLookups);
        this.dj2ae2opt$disableTemplates();
    }

    @Unique
    private void dj2ae2opt$disableTemplates() {
        this.dj2ae2opt$templatesDisabled = true;
        this.dj2ae2opt$stability = DJ2AE2OPT$GIVEN_UP;
        this.dj2ae2opt$templates = null;
        this.dj2ae2opt$steadyStateDisabled = true;
        this.dj2ae2opt$steadyCounters = null;
    }

    @Unique
    private static int dj2ae2opt$pruneThreshold(int liveCount) {
        return liveCount * 2 + 16;
    }


    @Unique
    private void dj2ae2opt$pruneTemplates(ItemStack[] protos, int count) {
        final IdentityHashMap<ItemStack, PrototypeEntry> templates = this.dj2ae2opt$templates;
        if (templates == null || templates.size() <= dj2ae2opt$pruneThreshold(count)) {
            return;
        }

        final IdentityHashMap<ItemStack, PrototypeEntry> kept =
                new IdentityHashMap<ItemStack, PrototypeEntry>(count + 16);
        for (int i = 0; i < count; i++) {
            final ItemStack prototype = protos[i];
            if (prototype == null) {
                continue;
            }
            final PrototypeEntry entry = templates.get(prototype);
            if (entry != null) {
                kept.put(prototype, entry);
            }
        }

        Diagnostics.templateCachePruned(templates.size() - kept.size());
        this.dj2ae2opt$templates = kept;
    }
}
