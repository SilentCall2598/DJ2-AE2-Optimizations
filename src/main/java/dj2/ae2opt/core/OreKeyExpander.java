package dj2.ae2opt.core;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public final class OreKeyExpander {

    private static final long[] NONE = new long[0];


    private static final Map<Long, long[]> CACHE = new ConcurrentHashMap<Long, long[]>();

    private OreKeyExpander() {
    }

    public static void invalidate() {
        CACHE.clear();
    }


    public static int addKeys(LongKeySet keys, ItemStack prototype) {
        final long literal = DrawerPresenceIndex.key(prototype);
        keys.add(literal);

        final long[] equivalents = equivalents(literal, prototype);
        for (long key : equivalents) {
            keys.add(key);
        }
        return equivalents.length;
    }


    public static long[] collectKeys(long literal, int[][] candidates) {
        if (candidates == null || candidates.length == 0) {
            return NONE;
        }
        final Set<Long> found = new LinkedHashSet<Long>();
        for (int[] candidate : candidates) {
            if (candidate == null || candidate.length < 2) {
                continue;
            }
            if (candidate[1] == OreDictionary.WILDCARD_VALUE) {
                continue;
            }
            final long key = DrawerPresenceIndex.key(candidate[0], candidate[1]);
            if (key != literal) {
                found.add(key);
            }
        }
        if (found.isEmpty()) {
            return NONE;
        }
        final long[] result = new long[found.size()];
        int i = 0;
        for (Long key : found) {
            result[i++] = key;
        }
        return result;
    }


    public static long[] equivalentsOf(ItemStack prototype) {
        final long literal = DrawerPresenceIndex.key(prototype);
        return equivalents(literal, prototype);
    }


    public static boolean covers(ItemStack prototype, long requestKey) {
        final long literal = DrawerPresenceIndex.key(prototype);
        if (literal == requestKey) {
            return true;
        }
        for (long key : equivalents(literal, prototype)) {
            if (key == requestKey) {
                return true;
            }
        }
        return false;
    }

    private static long[] equivalents(long literal, ItemStack prototype) {
        final long[] cached = CACHE.get(literal);
        if (cached != null) {
            return cached;
        }

        long[] result = NONE;
        try {
            final int[] ids = OreDictionary.getOreIDs(prototype);
            if (ids != null && ids.length > 0) {
                final java.util.List<int[]> candidates = new java.util.ArrayList<int[]>();
                for (int id : ids) {
                    final String name = OreDictionary.getOreName(id);
                    if (name == null) {
                        continue;
                    }
                    final Iterable<ItemStack> ores = OreDictionary.getOres(name);
                    if (ores == null) {
                        continue;
                    }
                    for (ItemStack alternative : ores) {
                        if (alternative == null || alternative.isEmpty()) {
                            continue;
                        }
                        candidates.add(new int[]{
                                net.minecraft.item.Item.getIdFromItem(alternative.getItem()),
                                alternative.getMetadata()});
                    }
                }
                result = collectKeys(literal, candidates.toArray(new int[candidates.size()][]));
            }
        } catch (Throwable t) {
            Diagnostics.oreExpansionFailed(t);
            throw new IllegalStateException("ore expansion failed", t);
        }

        CACHE.put(literal, result);
        return result;
    }
}
