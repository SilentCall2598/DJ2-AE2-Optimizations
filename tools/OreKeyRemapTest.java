import dj2.ae2opt.DJ2AE2Optimizations;
import dj2.ae2opt.core.DrawerPresenceIndex;
import dj2.ae2opt.core.LongKeySet;
import dj2.ae2opt.core.OreKeyExpander;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLModIdMappingEvent;
import net.minecraftforge.oredict.OreDictionary;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public final class OreKeyRemapTest {

    static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            System.out.println("pass  " + name);
        } else {
            System.out.println("FAIL  " + name + ": " + detail);
            failures++;
        }
    }

    static boolean contains(long[] keys, long key) {
        for (long k : keys) {
            if (k == key) {
                return true;
            }
        }
        return false;
    }

    static Method idMappingHandler() {
        for (Method m : DJ2AE2Optimizations.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Mod.EventHandler.class)
                    && m.getParameterTypes().length == 1
                    && m.getParameterTypes()[0] == FMLModIdMappingEvent.class) {
                return m;
            }
        }
        return null;
    }

    static void fire(Method handler, boolean frozen) throws ReflectiveOperationException {
        final Map<ResourceLocation, Map<ResourceLocation, Integer[]>> remaps =
                new HashMap<ResourceLocation, Map<ResourceLocation, Integer[]>>();
        handler.invoke(new DJ2AE2Optimizations(), new FMLModIdMappingEvent(remaps, frozen));
    }

    static void staleEquivalentsAreRefreshedByAnIdMappingEvent() throws ReflectiveOperationException {
        final ItemStack iron = new ItemStack(Items.IRON_INGOT);
        final long goldKey = DrawerPresenceIndex.key(new ItemStack(Items.GOLD_INGOT));
        final long diamondKey = DrawerPresenceIndex.key(new ItemStack(Items.DIAMOND));

        OreDictionary.registerOre("dj2RemapShared", Items.IRON_INGOT);
        OreDictionary.registerOre("dj2RemapShared", Items.GOLD_INGOT);
        check("the first expansion sees the registered ore-equivalent",
                contains(OreKeyExpander.equivalentsOf(iron), goldKey), "gold key missing");

        OreDictionary.registerOre("dj2RemapShared", Items.DIAMOND);
        check("without an invalidation signal the cached expansion is stale",
                !contains(OreKeyExpander.equivalentsOf(iron), diamondKey),
                "expected the cached result to still omit the new equivalent");

        final LongKeySet staleIndex = new LongKeySet(16);
        OreKeyExpander.addKeys(staleIndex, iron);
        check("a presence index built from the stale expansion would report the equivalent absent",
                !staleIndex.contains(diamondKey), "expected the stale index to omit the equivalent");

        final Method handler = idMappingHandler();
        check("the mod class declares an @Mod.EventHandler for FMLModIdMappingEvent", handler != null,
                "no handler found; FML would never deliver registry remaps to this mod");
        if (handler == null) {
            return;
        }

        final long epochBefore = DrawerPresenceIndex.epoch();
        fire(handler, true);
        check("a frozen-state mapping event with an empty remap set still invalidates the expansion",
                contains(OreKeyExpander.equivalentsOf(iron), diamondKey), "equivalent still missing");
        check("the mapping event bumps the presence epoch so every controller rebuilds",
                DrawerPresenceIndex.epoch() > epochBefore, "epoch unchanged");

        final LongKeySet freshIndex = new LongKeySet(16);
        OreKeyExpander.addKeys(freshIndex, iron);
        check("a presence index rebuilt after the mapping event contains the equivalent",
                freshIndex.contains(diamondKey), "equivalent missing after rebuild");

        OreDictionary.registerOre("dj2RemapShared", Items.EMERALD);
        final long emeraldKey = DrawerPresenceIndex.key(new ItemStack(Items.EMERALD));
        final long epochBeforeSnapshot = DrawerPresenceIndex.epoch();
        fire(handler, false);
        check("a save-snapshot mapping event also invalidates the expansion",
                contains(OreKeyExpander.equivalentsOf(iron), emeraldKey), "equivalent still missing");
        check("a save-snapshot mapping event also bumps the presence epoch",
                DrawerPresenceIndex.epoch() > epochBeforeSnapshot, "epoch unchanged");
    }

    static void literalKeyTracksTheCurrentNumericId() {
        final ItemStack iron = new ItemStack(Items.IRON_INGOT);
        check("the presence key is derived from the live numeric item id",
                DrawerPresenceIndex.key(iron) == DrawerPresenceIndex.key(Item.getIdFromItem(Items.IRON_INGOT), 0),
                "key does not match Item.getIdFromItem");
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Bootstrap.register();

        literalKeyTracksTheCurrentNumericId();
        staleEquivalentsAreRefreshedByAnIdMappingEvent();

        System.out.println(failures == 0 ? "\nOreKeyRemapTest: ALL PASS"
                : "\nOreKeyRemapTest: " + failures + " FAILURES");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
