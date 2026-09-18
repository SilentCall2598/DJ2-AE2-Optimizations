package dj2.ae2opt.mixin;

import appeng.api.AEApi;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEItemStack;
import com.jaquadro.minecraft.storagedrawers.api.capabilities.IItemRepository;
import dj2.ae2opt.api.IVersionedItemList;
import dj2.ae2opt.core.Diagnostics;
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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;


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
    private ItemStack[] dj2ae2opt$snapshotProtos;

    @Unique
    private long[] dj2ae2opt$snapshotCounts;

    @Unique
    private int dj2ae2opt$snapshotLength;

    @Unique
    private int dj2ae2opt$cachedListVersion;

    @Unique
    private boolean dj2ae2opt$haveListVersion;

    @Unique
    private IAEItemStack[] dj2ae2opt$cachedRefs;

    @Unique
    private long[] dj2ae2opt$cachedSizes;

    @Unique
    private int dj2ae2opt$cachedEntryCount;

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

        final boolean needSkipSnapshot = OptimizationConfig.skipUnchangedDrawerPolls && !this.dj2ae2opt$templatesDisabled;

        if (needSkipSnapshot
                && this.dj2ae2opt$repositoryUnchanged(count, records)
                && this.dj2ae2opt$cachedListUnchanged()
                && this.dj2ae2opt$cachedContentsUnchanged()) {
            Diagnostics.pollSkipped();
            cir.setReturnValue(Collections.<IAEItemStack>emptyList());
            return;
        }

        final IdentityHashMap<ItemStack, PrototypeEntry> templates = this.dj2ae2opt$templates;
        final boolean needPruneSnapshot = !this.dj2ae2opt$templatesDisabled
                && templates != null
                && templates.size() > dj2ae2opt$pruneThreshold(count);

        final ItemStack[] protos = (needSkipSnapshot || needPruneSnapshot) ? new ItemStack[count] : null;
        final long[] counts = needSkipSnapshot ? new long[count] : null;

        final IItemList<IAEItemStack> currentlyOnStorage =
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createList();

        this.dj2ae2opt$pollLookups = 0;
        this.dj2ae2opt$pollHits = 0;

        int index = 0;
        for (IItemRepository.ItemRecord rec : records) {
            final ItemStack prototype = rec.itemPrototype;
            final long size = rec.count;

            if (index < count) {
                if (protos != null) {
                    protos[index] = prototype;
                }
                if (counts != null) {
                    counts[index] = size;
                }
            }
            index++;

            if (prototype == null || prototype.isEmpty()) {
                Diagnostics.emptyPrototypeSkipped();
                continue;
            }

            final IAEItemStack stack;
            if (this.dj2ae2opt$templatesDisabled) {

                final IAEItemStack converted = AEItemStack.fromItemStack(prototype);
                if (converted == null) {
                    continue;
                }
                Diagnostics.fallbackConversion();
                stack = converted.setStackSize(size);
            } else {
                final IAEItemStack template = this.dj2ae2opt$template(prototype);
                if (template == null) {
                    continue;
                }

                stack = template.copy().setStackSize(size);
            }
            currentlyOnStorage.add(stack);
        }

        final List<IAEItemStack> changes = new ArrayList<IAEItemStack>();

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
        this.currentlyCached = currentlyOnStorage;

        final boolean aligned = index == count;

        if (needSkipSnapshot && aligned) {
            this.dj2ae2opt$snapshotProtos = protos;
            this.dj2ae2opt$snapshotCounts = counts;
            this.dj2ae2opt$snapshotLength = count;
            this.dj2ae2opt$rememberListVersion(currentlyOnStorage);
            this.dj2ae2opt$rememberCacheContents(currentlyOnStorage, count);
        } else {
            this.dj2ae2opt$forgetSkipState();
        }

        if (needPruneSnapshot && aligned && protos != null) {
            this.dj2ae2opt$pruneTemplates(protos, count);
        }

        this.dj2ae2opt$judgeIdentityStability();

        Diagnostics.pollRebuilt(changes.size());
        cir.setReturnValue(changes);
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
        this.dj2ae2opt$forgetSkipState();
    }

    @Unique
    private void dj2ae2opt$forgetSkipState() {
        this.dj2ae2opt$snapshotProtos = null;
        this.dj2ae2opt$snapshotCounts = null;
        this.dj2ae2opt$snapshotLength = 0;
        this.dj2ae2opt$haveListVersion = false;
        this.dj2ae2opt$cachedRefs = null;
        this.dj2ae2opt$cachedSizes = null;
        this.dj2ae2opt$cachedEntryCount = 0;
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


    @Unique
    private boolean dj2ae2opt$repositoryUnchanged(int count, Collection<IItemRepository.ItemRecord> records) {
        final ItemStack[] protos = this.dj2ae2opt$snapshotProtos;
        if (protos == null || this.dj2ae2opt$snapshotLength != count || protos.length < count) {
            return false;
        }
        final long[] counts = this.dj2ae2opt$snapshotCounts;
        int index = 0;
        for (IItemRepository.ItemRecord rec : records) {
            if (index >= count || rec.itemPrototype != protos[index] || rec.count != counts[index]) {
                return false;
            }
            index++;
        }
        return index == count;
    }


    @Unique
    private void dj2ae2opt$rememberCacheContents(IItemList<IAEItemStack> list, int capacity) {
        final IAEItemStack[] refs = new IAEItemStack[capacity];
        final long[] sizes = new long[capacity];

        int index = 0;
        for (IAEItemStack is : list) {
            if (index >= capacity) {
                this.dj2ae2opt$cachedRefs = null;
                this.dj2ae2opt$cachedSizes = null;
                this.dj2ae2opt$cachedEntryCount = 0;
                return;
            }
            refs[index] = is;
            sizes[index] = is.getStackSize();
            index++;
        }

        this.dj2ae2opt$cachedRefs = refs;
        this.dj2ae2opt$cachedSizes = sizes;
        this.dj2ae2opt$cachedEntryCount = index;
    }

    @Unique
    private boolean dj2ae2opt$cachedContentsUnchanged() {
        final IAEItemStack[] refs = this.dj2ae2opt$cachedRefs;
        if (refs == null) {
            return false;
        }
        final long[] sizes = this.dj2ae2opt$cachedSizes;
        for (int i = 0, n = this.dj2ae2opt$cachedEntryCount; i < n; i++) {
            if (refs[i].getStackSize() != sizes[i]) {
                return false;
            }
        }
        return true;
    }


    @Unique
    private boolean dj2ae2opt$cachedListUnchanged() {
        if (!this.dj2ae2opt$haveListVersion) {
            return false;
        }
        final IItemList<IAEItemStack> cached = this.currentlyCached;
        return cached instanceof IVersionedItemList
                && ((IVersionedItemList) cached).dj2ae2opt$listVersion() == this.dj2ae2opt$cachedListVersion;
    }

    @Unique
    private void dj2ae2opt$rememberListVersion(IItemList<IAEItemStack> list) {
        if (list instanceof IVersionedItemList) {
            this.dj2ae2opt$cachedListVersion = ((IVersionedItemList) list).dj2ae2opt$listVersion();
            this.dj2ae2opt$haveListVersion = true;
        } else {
            this.dj2ae2opt$haveListVersion = false;
        }
    }
}
