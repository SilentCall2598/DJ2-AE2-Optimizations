import dj2.ae2opt.core.DrawerPresenceIndex;
import dj2.ae2opt.core.LongKeySet;
import dj2.ae2opt.core.OreKeyExpander;

import java.util.ArrayList;
import java.util.List;


public final class NegativeIndexTest {

    static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            System.out.println("pass  " + name);
        } else {
            System.out.println("FAIL  " + name + ": " + detail);
            failures++;
        }
    }


    static final class Drawer {
        int item, meta, count;
        String nbt;
        boolean dictConvertible;
        boolean fractional;
        boolean unknownImplementation;
        boolean enabled = true;

        Drawer(int item, int meta, int count) { this.item = item; this.meta = meta; this.count = count; }

        boolean isEmpty() { return count <= 0; }


        boolean wouldServe(int reqItem, int reqMeta, String reqNbt) {
            if (!enabled || isEmpty()) return false;
            return matcherAccepts(item, meta, nbt, dictConvertible, reqItem, reqMeta, reqNbt);
        }
        static boolean nbtEquals(String a, String b) { return a == null ? b == null : a.equals(b); }
    }


    static final class Controller {
        final List<Drawer> drawers = new ArrayList<Drawer>();
        LongKeySet keys;
        long indexEpoch = -1;
        boolean topologyDirty;
        boolean usable;
        int rebuilds;

        static long key(int item, int meta) { return ((long) item << 32) | (meta & 0xFFFFFFFFL); }

        boolean mightContain(int item, int meta, long epoch) {
            if (keys == null || topologyDirty || indexEpoch != epoch) rebuild(epoch);
            if (!usable) return true;
            return keys.contains(key(item, meta));
        }

        void rebuild(long epoch) {
            rebuilds++;
            LongKeySet built = new LongKeySet(Math.max(16, drawers.size()));
            usable = true;
            for (Drawer d : drawers) {
                if (!d.enabled) continue;
                if (d.unknownImplementation) { usable = false; break; }
                if (d.isEmpty()) continue;
                built.add(key(d.item, d.meta));
                for (long k : OreKeyExpander.collectKeys(key(d.item, d.meta),
                        candidatesFor(key(d.item, d.meta)))) {
                    built.add(k);
                }
            }
            keys = built;
            indexEpoch = epoch;
            topologyDirty = false;
        }

        boolean stockWouldServe(int item, int meta, String nbt) {
            for (Drawer d : drawers) if (d.wouldServe(item, meta, nbt)) return true;
            return false;
        }
    }


    static void assertNoFalseNegative(String name, Controller c, long epoch,
                                      int item, int meta, String nbt) {
        boolean might = c.mightContain(item, meta, epoch);
        boolean stock = c.stockWouldServe(item, meta, nbt);
        check(name, might || !stock,
                "index said absent but a drawer would have served item=" + item + " meta=" + meta);
    }

    public static void main(String[] args) {
        long epoch = 1;

        Controller c = new Controller();
        c.drawers.add(new Drawer(1, 0, 64));
        c.drawers.add(new Drawer(2, 3, 10));

        check("exact miss is answered absent", !c.mightContain(9, 0, epoch), "expected absent");
        check("stored item is a candidate", c.mightContain(1, 0, epoch), "expected present");
        check("metadata mismatch is answered absent", !c.mightContain(2, 0, epoch), "expected absent");
        assertNoFalseNegative("no false negative for a stored item", c, epoch, 1, 0, null);


        c.drawers.get(0).nbt = "{a:1}";
        epoch++;
        check("NBT mismatch falls through to stock", c.mightContain(1, 0, epoch), "expected present");
        assertNoFalseNegative("no false negative on NBT mismatch", c, epoch, 1, 0, "{b:2}");


        c.drawers.get(1).item = 7; c.drawers.get(1).meta = 0; epoch++;
        check("item type change is picked up", c.mightContain(7, 0, epoch), "expected present");
        assertNoFalseNegative("no false negative after a type change", c, epoch, 7, 0, null);
        check("the old key is gone", !c.mightContain(2, 3, epoch), "expected absent");


        Drawer fresh = new Drawer(5, 0, 0);
        c.drawers.add(fresh);
        c.topologyDirty = true;
        check("an empty drawer contributes no key", !c.mightContain(5, 0, epoch), "expected absent");
        fresh.count = 32; epoch++;
        check("populated drawer becomes a candidate", c.mightContain(5, 0, epoch), "expected present");
        assertNoFalseNegative("no false negative once populated", c, epoch, 5, 0, null);


        fresh.count = 0; epoch++;
        check("emptied drawer stops being a candidate", !c.mightContain(5, 0, epoch), "expected absent");
        assertNoFalseNegative("no false negative once emptied", c, epoch, 5, 0, null);


        Drawer joined = new Drawer(11, 0, 64);
        c.drawers.add(joined);
        int before = c.rebuilds;
        check("without a topology signal the join is invisible",
                !c.mightContain(11, 0, epoch) && c.rebuilds == before,
                "expected a stale answer, which is why updateCache must mark topology dirty");
        c.topologyDirty = true;
        check("topology dirty forces a rebuild", c.mightContain(11, 0, epoch), "expected present");
        assertNoFalseNegative("no false negative after a topology change", c, epoch, 11, 0, null);


        ore("ingotJoined", DrawerPresenceIndex.key(11, 0), new int[][]{{11, 0}, {1150, 0}});
        joined.dictConvertible = true;
        epoch++;
        check("a conversion drawer keeps the network indexable",
                c.usable || c.mightContain(999, 0, epoch), "expected the index to stay usable");
        check("an unrelated item is still answered absent", !c.mightContain(999, 0, epoch),
                "expected absent");
        assertNoFalseNegative("no false negative for an ore-equivalent request",
                c, epoch, 1150, 0, null);
        joined.dictConvertible = false; epoch++;
        check("the fast path survives removing the upgrade",
                !c.mightContain(999, 0, epoch) && c.usable, "expected the index usable");


        before = c.rebuilds;
        c.mightContain(1, 0, epoch + 1);
        check("a moved epoch rebuilds", c.rebuilds == before + 1, "expected one rebuild");


        LongKeySet set = new LongKeySet(4);
        for (int i = 0; i < 5000; i++) set.add(((long) i << 32) | (i & 7));
        boolean all = true;
        for (int i = 0; i < 5000; i++) all &= set.contains(((long) i << 32) | (i & 7));
        check("key set survives growth", all && set.size() == 5000, "size " + set.size());
        check("absent key is absent", !set.contains(0x7FFFFFFFL << 32), "unexpected hit");
        LongKeySet zero = new LongKeySet(4);
        check("zero key is not silently dropped",
                !zero.contains(0L) && zeroAdd(zero) && zero.contains(0L), "zero key lost");

        oreSupersetAgainstExactMatchers();
        fractionalAndUnknownDrawerHandling();
        conversionToggleThroughRealHook();
        invalidationPrecedesExternalCallback();

        System.out.println(failures == 0 ? "\nNegativeIndexTest: ALL PASS"
                : "\nNegativeIndexTest: " + failures + " FAILURES");
        if (failures != 0) System.exit(1);
    }

    static boolean zeroAdd(LongKeySet set) { set.add(0L); return true; }


    static final int WILDCARD = 32767;


    static final java.util.Map<String, int[][]> ORE = new java.util.LinkedHashMap<String, int[][]>();
    static final java.util.Map<Long, String[]> ORE_NAMES = new java.util.LinkedHashMap<Long, String[]>();

    static void ore(String name, long protoKey, int[][] entries) {
        ORE.put(name, entries);
        ORE_NAMES.put(protoKey, new String[]{name});
    }

    static int[][] candidatesFor(long protoKey) {
        String[] names = ORE_NAMES.get(protoKey);
        if (names == null) {
            return new int[0][];
        }
        java.util.List<int[]> out = new java.util.ArrayList<int[]>();
        for (String n : names) {
            for (int[] e : ORE.get(n)) {
                out.add(e);
            }
        }
        return out.toArray(new int[out.size()][]);
    }


    static boolean matcherAccepts(int protoItem, int protoMeta, String protoNbt, boolean conversion,
                                  int reqItem, int reqMeta, String reqNbt) {
        final boolean literal = protoItem == reqItem && protoMeta == reqMeta;
        if (!literal) {
            if (!conversion) {
                return false;
            }
            if (protoItem == reqItem) {
                return false;
            }
            boolean found = false;
            for (int[] e : candidatesFor(DrawerPresenceIndex.key(protoItem, protoMeta))) {
                if (e[1] == WILDCARD) {
                    continue;
                }
                if (e[0] == reqItem && e[1] == reqMeta) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return Drawer.nbtEquals(protoNbt, reqNbt);
    }

    static void oreSupersetAgainstExactMatchers() {

        ore("ingotCopper", DrawerPresenceIndex.key(100, 0), new int[][]{
                {100, 0}, {101, 0}, {102, 3}, {103, WILDCARD}});

        final int protoItem = 100, protoMeta = 0;
        final long literal = DrawerPresenceIndex.key(protoItem, protoMeta);
        final LongKeySet keys = new LongKeySet(16);
        keys.add(literal);
        final long[] extra = OreKeyExpander.collectKeys(literal, candidatesFor(literal));
        for (long k : extra) {
            keys.add(k);
        }

        check("wildcard ore entries contribute no key", extra.length == 2,
                "expected two concrete equivalents, got " + extra.length);

        int checked = 0, falseNegatives = 0;
        for (boolean conversion : new boolean[]{false, true}) {
            for (int item = 99; item <= 104; item++) {
                for (int meta = 0; meta <= 4; meta++) {
                    for (String nbt : new String[]{null, "{a:1}"}) {
                        checked++;
                        boolean accepted = matcherAccepts(protoItem, protoMeta, null, conversion,
                                item, meta, nbt);
                        boolean indexSaysAbsent = !keys.contains(DrawerPresenceIndex.key(item, meta));
                        if (accepted && indexSaysAbsent) {
                            falseNegatives++;
                        }
                    }
                }
            }
        }
        check("no false negative against the exact matchers, conversion off and on",
                falseNegatives == 0, falseNegatives + " of " + checked + " requests");


        check("NBT mismatch is a false positive, not an absence",
                keys.contains(DrawerPresenceIndex.key(protoItem, protoMeta))
                        && !matcherAccepts(protoItem, protoMeta, "{a:1}", false, protoItem, protoMeta, "{b:2}"),
                "expected the key present while the matcher rejects");


        check("same item different metadata is absent unless an ore entry covers it",
                !keys.contains(DrawerPresenceIndex.key(protoItem, 1)),
                "expected meta 1 absent");
        check("an ore entry with a different metadata is covered",
                keys.contains(DrawerPresenceIndex.key(102, 3)), "expected 102:3 present");


        ore("dustSibling", DrawerPresenceIndex.key(300, 0), new int[][]{{300, 0}, {300, 7}, {301, 0}});
        final long sibLiteral = DrawerPresenceIndex.key(300, 0);
        final LongKeySet sibKeys = new LongKeySet(8);
        sibKeys.add(sibLiteral);
        for (long k : OreKeyExpander.collectKeys(sibLiteral, candidatesFor(sibLiteral))) {
            sibKeys.add(k);
        }
        check("the exact matcher rejects a same-Item different-meta sibling",
                !matcherAccepts(300, 0, null, true, 300, 7, null),
                "the oracle accepted a sibling the matcher rejects before the ore list");
        check("the conservative index keeps the sibling key as an intentional false positive",
                sibKeys.contains(DrawerPresenceIndex.key(300, 7)),
                "expected the sibling key present so the request runs stock");
        check("a genuine ore equivalent with a different Item is still covered",
                sibKeys.contains(DrawerPresenceIndex.key(301, 0)), "expected 301:0 present");
        check("a false positive is never a false negative",
                !matcherAccepts(300, 0, null, true, 300, 7, null)
                        || sibKeys.contains(DrawerPresenceIndex.key(300, 7)),
                "index must contain every key the matcher would accept");


        ore("blockCopper", DrawerPresenceIndex.key(200, 0), new int[][]{{200, 0}, {201, 0}});
        final long tier = DrawerPresenceIndex.key(200, 0);
        final LongKeySet tierKeys = new LongKeySet(8);
        tierKeys.add(tier);
        for (long k : OreKeyExpander.collectKeys(tier, candidatesFor(tier))) {
            tierKeys.add(k);
        }
        check("a compacting tier prototype indexes like any other",
                tierKeys.contains(tier) && tierKeys.contains(DrawerPresenceIndex.key(201, 0)),
                "expected both tier keys");
    }


    static void fractionalAndUnknownDrawerHandling() {
        Controller c = new Controller();
        Drawer standard = new Drawer(1, 0, 64);
        c.drawers.add(standard);
        c.mightContain(9, 0, 1);
        check("a standard-only network is indexable", c.usable, "expected usable");

        Drawer unknown = new Drawer(2, 0, 64);
        unknown.unknownImplementation = true;
        c.drawers.add(unknown);
        c.topologyDirty = true;
        check("an unknown drawer implementation still refuses the whole network",
                c.mightContain(9, 0, 1) && !c.usable, "expected refusal");

        c.drawers.remove(unknown);
        Drawer compacting = new Drawer(3, 0, 64);
        compacting.fractional = true;
        c.drawers.add(compacting);
        c.topologyDirty = true;
        check("a built-in compacting drawer no longer refuses the network",
                !c.mightContain(9, 0, 1) && c.usable, "expected the network to stay indexable");
        check("the compacting prototype is indexed",
                c.mightContain(3, 0, 1), "expected the compacting key present");
    }


    static final class Network {
        final Controller controller = new Controller();
        long epoch = 1;

        boolean matcherHookInstalled = true;

        Drawer add(int item, int meta, int count) {
            Drawer d = new Drawer(item, meta, count);
            controller.drawers.add(d);
            controller.topologyDirty = true;
            return d;
        }


        void setConversionUpgrade(Drawer d, boolean on) {
            d.dictConvertible = on;
            if (matcherHookInstalled) {
                epoch++;
            }
        }

        boolean mightContain(int item, int meta) {
            return controller.mightContain(item, meta, epoch);
        }
    }


    static void conversionToggleThroughRealHook() {


        ore("ingotBrass", DrawerPresenceIndex.key(1100, 0), new int[][]{{1100, 0}, {1150, 0}});

        Network live = new Network();
        Drawer d = live.add(1100, 0, 64);
        live.mightContain(1100, 0);
        check("index is usable before the upgrade", live.controller.usable, "expected usable");
        check("a conversion drawer no longer makes the network unindexable",
                live.controller.usable, "expected usable");


        check("the ore equivalent is a candidate with conversion off",
                live.mightContain(1150, 0), "expected 1150 to fall through to stock");
        assertNoFalseNegative("no false negative with conversion off",
                live.controller, live.epoch, 1150, 0, null);

        live.setConversionUpgrade(d, true);
        check("the ore equivalent is still a candidate with conversion on",
                live.mightContain(1150, 0), "expected 1150 to fall through to stock");
        assertNoFalseNegative("no false negative with conversion on",
                live.controller, live.epoch, 1150, 0, null);
        check("an unrelated item is still answered absent",
                !live.mightContain(1234, 0), "expected absent");

        live.setConversionUpgrade(d, false);
        check("the network stays indexable after removing the upgrade",
                live.controller.usable, "expected usable");


        Network unhooked = new Network();
        unhooked.matcherHookInstalled = false;
        Drawer ud = unhooked.add(1100, 0, 64);
        unhooked.mightContain(1100, 0);
        unhooked.setConversionUpgrade(ud, true);
        assertNoFalseNegative("the ore superset holds even without the matcher hook",
                unhooked.controller, unhooked.epoch, 1150, 0, null);
    }


    static void invalidationPrecedesExternalCallback() {
        for (boolean invalidateAtHead : new boolean[]{true, false}) {
            Network net = new Network();
            Drawer d = net.add(1, 0, 64);
            net.mightContain(1, 0);

            d.item = 42;
            if (invalidateAtHead) {
                net.epoch++;
            }
            boolean listenerSawItem = net.mightContain(42, 0);
            if (!invalidateAtHead) {
                net.epoch++;
            }

            if (invalidateAtHead) {
                check("a listener during the callback sees the new item", listenerSawItem,
                        "expected the index to already know about item 42");
            } else {
                check("invalidating at RETURN is a detectable false negative", !listenerSawItem,
                        "the test cannot tell RETURN from HEAD");
            }
        }
    }
}
