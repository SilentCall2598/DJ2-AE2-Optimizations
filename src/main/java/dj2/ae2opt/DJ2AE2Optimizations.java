package dj2.ae2opt;

import dj2.ae2opt.core.CompatibilityCheck;
import dj2.ae2opt.core.Constants;
import dj2.ae2opt.core.Diagnostics;
import dj2.ae2opt.core.DrawerPresenceIndex;
import dj2.ae2opt.core.MixinStatus;
import dj2.ae2opt.core.OreKeyExpander;
import dj2.ae2opt.core.OptimizationCommand;
import dj2.ae2opt.core.OptimizationConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.oredict.OreDictionary;

@Mod(modid = Constants.MOD_ID,
     name = Constants.MOD_NAME,
     version = Constants.VERSION,
     acceptableRemoteVersions = "*",
     dependencies = "required-after:mixinbooter;required-after:appliedenergistics2;required-after:storagedrawers")
public final class DJ2AE2Optimizations {

    private long nextDumpAt;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        OptimizationConfig.load();
        CompatibilityCheck.run();

        for (String line : MixinStatus.selectionLines()) {
            MixinStatus.LOG.info(line);
        }

        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new OptimizationCommand());
    }


    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        MixinStatus.log(MixinStatus.LOG, "Mixin/runtime status", MixinStatus.statusLines());

        if (MixinStatus.hasUnavailableProductionFeatures()) {
            MixinStatus.LOG.warn("A requested OPTIMIZATION mixin did not apply. This is not a missing "
                    + "diagnostic - the corresponding gameplay path is running unmodified stock "
                    + "behavior instead of the optimization that was asked for. Its target class was "
                    + "likely loaded before late mixins were prepared - search the log for "
                    + "'loaded too early'.");
        }

        if (MixinStatus.hasUnavailableDiagnostics()) {
            MixinStatus.LOG.warn("A diagnostic mixin was enabled but is not installed. Its target "
                    + "class was loaded before late mixins were prepared - search the log for "
                    + "'loaded too early'. Any measurement that depends on it is missing, not zero.");
            if (MixinStatus.Feature.POWERED_EXTRACTION_CONTEXT.isRequested()
                    && !MixinStatus.Feature.POWERED_EXTRACTION_CONTEXT.isApplied()) {
                MixinStatus.LOG.warn("Specifically: extraction SIMULATE/MODULATE pairs cannot be "
                        + "tied to one Platform.poweredExtraction call in this environment, so all "
                        + "pairs reported are the weaker same-tick/same-source heuristic.");
            }
        }
    }


    @SubscribeEvent
    public void onOreRegistered(OreDictionary.OreRegisterEvent event) {
        if (OptimizationConfig.optimizeDrawerNegativeExtraction) {
            OreKeyExpander.invalidate();
            DrawerPresenceIndex.bumpEpochForAttributes();
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Diagnostics.serverTick();
        if (OptimizationConfig.diagnosticsDumpIntervalSeconds <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < this.nextDumpAt) {
            return;
        }
        this.nextDumpAt = now + OptimizationConfig.diagnosticsDumpIntervalSeconds * 1000L;
        Diagnostics.dump("periodic");
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        MixinStatus.log(MixinStatus.LOG, "Mixin/runtime status", MixinStatus.statusLines());
        Diagnostics.dump("server stopping");
    }
}
