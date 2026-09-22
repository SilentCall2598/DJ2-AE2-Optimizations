package dj2.ae2opt.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.events.MENetworkEvent;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IItemList;
import dj2.ae2opt.core.Diagnostics;
import dj2.ae2opt.core.InventoryDiff;
import dj2.ae2opt.core.MixinStatus;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.api.storage.IEssentiaStorageChannel;
import thaumicenergistics.integration.appeng.grid.EssentiaContainerAdapter;
import thaumicenergistics.item.ItemPartBase;
import thaumicenergistics.part.PartSharedEssentiaBus;

import java.util.ArrayList;
import java.util.List;


@Mixin(targets = "thaumicenergistics.part.PartEssentiaStorageBus", remap = false)
public abstract class MixinPartEssentiaStorageBus extends PartSharedEssentiaBus {

    public MixinPartEssentiaStorageBus(ItemPartBase item) {
        super(item);
    }

    @Shadow
    private EssentiaContainerAdapter getHandler() {
        throw new UnsupportedOperationException();
    }

    @Unique
    private TileEntity dj2ae2opt$lastConnectedTE;

    @Unique
    private IItemList<IAEEssentiaStack> dj2ae2opt$lastSnapshot;

    @Redirect(method = "onNeighborChanged(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;"
            + "Lnet/minecraft/util/math/BlockPos;)V",
            at = @At(value = "INVOKE",
                    target = "Lappeng/api/networking/IGrid;postEvent(Lappeng/api/networking/events/MENetworkEvent;)"
                            + "Lappeng/api/networking/events/MENetworkEvent;"),
            require = 1)
    private MENetworkEvent dj2ae2opt$maybeIncrementalUpdate(IGrid grid, MENetworkEvent event) {
        Diagnostics.teAttachedSideNotification();

        try {
            final TileEntity currentTE = this.getConnectedTE();
            if (currentTE != this.dj2ae2opt$lastConnectedTE) {
                this.dj2ae2opt$lastConnectedTE = currentTE;
                this.dj2ae2opt$lastSnapshot = null;
                Diagnostics.teContainerIdentityChanged();
                return grid.postEvent(event);
            }

            Diagnostics.teSameContainerNotification();

            final EssentiaContainerAdapter handler = this.getHandler();
            if (handler == null) {
                Diagnostics.teFailOpenMissingAccess("no handler for the connected container");
                return grid.postEvent(event);
            }

            final IEssentiaStorageChannel channel = this.getChannel();
            if (channel == null) {
                Diagnostics.teFailOpenMissingAccess("essentia storage channel unavailable");
                return grid.postEvent(event);
            }

            final IItemList<IAEEssentiaStack> currentSnapshot = handler.getAvailableItems(channel.createList());
            final IItemList<IAEEssentiaStack> previousSnapshot = this.dj2ae2opt$lastSnapshot;

            if (previousSnapshot == null) {
                this.dj2ae2opt$lastSnapshot = currentSnapshot;
                Diagnostics.teFailOpenNoBaseline();
                return grid.postEvent(event);
            }

            final List<IAEEssentiaStack> deltas = new ArrayList<IAEEssentiaStack>();
            InventoryDiff.compute(
                    currentSnapshot, previousSnapshot::findPrecise,
                    previousSnapshot, currentSnapshot::findPrecise,
                    IAEEssentiaStack::getStackSize,
                    (identity, delta) -> identity.copy().setStackSize(delta),
                    deltas);

            this.dj2ae2opt$lastSnapshot = currentSnapshot;

            if (deltas.isEmpty()) {
                Diagnostics.teSameContainerNoDelta();
                return event;
            }

            if (this.getGridNode() == null) {
                Diagnostics.teFailOpenMissingAccess("no grid node");
                return grid.postEvent(event);
            }
            final IGrid actualGrid = this.getGridNode().getGrid();
            if (actualGrid == null) {
                Diagnostics.teFailOpenMissingAccess("no grid");
                return grid.postEvent(event);
            }

            final IStorageGrid storageGrid = actualGrid.getCache(IStorageGrid.class);
            if (storageGrid == null) {
                Diagnostics.teFailOpenMissingAccess("no storage grid cache");
                return grid.postEvent(event);
            }

            final IActionSource source = this.source;
            if (source == null) {
                Diagnostics.teFailOpenMissingAccess("no action source");
                return grid.postEvent(event);
            }

            storageGrid.postAlterationOfStoredItems(channel, deltas, source);
            Diagnostics.teSameContainerDelta(deltas.size());
            MixinStatus.Feature.THAUMIC_ENERGISTICS_INTEGRATION.markRuntimeHit();
            return event;
        } catch (Throwable t) {
            Diagnostics.teFailOpenException(t);
            return grid.postEvent(event);
        }
    }
}
