import dj2.ae2opt.core.DrawerCandidateIndex;
import dj2.ae2opt.core.DrawerPresenceIndex;
import dj2.ae2opt.core.LongKeySet;
import dj2.ae2opt.core.OptimizationConfig;
import dj2.ae2opt.core.OreKeyExpander;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


public final class CandidateIndexTest {

    static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            System.out.println("pass  " + name);
        } else {
            System.out.println("FAIL  " + name + ": " + detail);
            failures++;
        }
    }

    static final int WILDCARD = 32767;

    static long key(int item, int meta) {
        return DrawerPresenceIndex.key(item, meta);
    }

    static boolean contains(int[] array, int value) {
        for (int v : array) {
            if (v == value) {
                return true;
            }
        }
        return false;
    }


    static final Map<String, int[][]> ORE = new LinkedHashMap<String, int[][]>();
    static final Map<Long, List<String>> ORE_NAMES = new LinkedHashMap<Long, List<String>>();

    static void ore(String name, long protoKey, int[][] entries) {
        ORE.put(name, entries);
        List<String> names = ORE_NAMES.get(protoKey);
        if (names == null) {
            names = new ArrayList<String>();
            ORE_NAMES.put(protoKey, names);
        }
        names.add(name);
    }

    static int[][] oreCandidatesFor(long protoKey) {
        final List<String> names = ORE_NAMES.get(protoKey);
        if (names == null) {
            return new int[0][];
        }
        final List<int[]> out = new ArrayList<int[]>();
        for (String n : names) {
            for (int[] e : ORE.get(n)) {
                out.add(e);
            }
        }
        return out.toArray(new int[out.size()][]);
    }

    static long[] equivalentsFor(long literal) {
        return OreKeyExpander.collectKeys(literal, oreCandidatesFor(literal));
    }

    static boolean covers(long protoKey, long requestKey) {
        if (protoKey == requestKey) {
            return true;
        }
        for (long k : equivalentsFor(protoKey)) {
            if (k == requestKey) {
                return true;
            }
        }
        return false;
    }

    static boolean matcherAccepts(int protoItem, int protoMeta, boolean conversion, int reqItem, int reqMeta) {
        final boolean literal = protoItem == reqItem && protoMeta == reqMeta;
        if (!literal) {
            if (!conversion) {
                return false;
            }
            if (protoItem == reqItem) {
                return false;
            }
            boolean found = false;
            for (int[] e : oreCandidatesFor(key(protoItem, protoMeta))) {
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
        return true;
    }


    static final class Drawer {
        int item, meta, count;
        boolean dictConvertible;
        boolean fractional;
        boolean unknownImplementation;
        boolean throwsOnExpansion;
        boolean enabled = true;

        Drawer(int item, int meta, int count) {
            this.item = item;
            this.meta = meta;
            this.count = count;
        }

        boolean isEmpty() {
            return this.count <= 0;
        }
    }


    static int[] expectedCandidates(int[] slots, Drawer[] byId, long requestKey) {
        final List<Integer> out = new ArrayList<Integer>();
        for (int slot : slots) {
            final Drawer d = byId[slot];
            if (d == null || !d.enabled || d.isEmpty()) {
                continue;
            }
            if (covers(key(d.item, d.meta), requestKey)) {
                out.add(slot);
            }
        }
        final int[] result = new int[out.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = out.get(i);
        }
        return result;
    }


    static final class BuildResult {
        boolean usable;
        boolean keyCapExceeded;
        boolean slotRefCapExceeded;
        DrawerCandidateIndex index;
    }

    static BuildResult buildCandidateIndex(int[] slots, Drawer[] byId, int maxKeys, long maxSlotRefs) {
        final BuildResult result = new BuildResult();
        final DrawerCandidateIndex.Builder builder = new DrawerCandidateIndex.Builder(maxKeys, maxSlotRefs);
        boolean usable = true;
        for (int slot : slots) {
            final Drawer d = byId[slot];
            if (d == null || !d.enabled) {
                continue;
            }
            if (d.unknownImplementation) {
                usable = false;
                break;
            }
            if (d.isEmpty()) {
                continue;
            }
            try {
                if (d.throwsOnExpansion) {
                    throw new IllegalStateException("ore expansion failed");
                }
                final long literal = key(d.item, d.meta);
                final long[] equivalents = equivalentsFor(literal);
                builder.addOccurrence(slot, literal, equivalents);
            } catch (RuntimeException e) {
                usable = false;
                break;
            }
        }
        result.usable = usable;
        result.keyCapExceeded = builder.keyCapExceeded();
        result.slotRefCapExceeded = builder.slotReferenceCapExceeded();
        if (usable && !builder.capExceeded()) {
            result.index = builder.build();
        }
        return result;
    }


    static final class Network {
        final List<Drawer> drawers = new ArrayList<Drawer>();
        boolean topologyDirty;
        long epoch = 1;
        long builtEpoch = -1;
        int rebuilds;
        boolean presenceUsable;
        LongKeySet presenceKeys;
        DrawerCandidateIndex candidateIndex;
        int maxKeys = 100000;
        long maxSlotRefs = 1000000;

        Drawer add(int item, int meta, int count) {
            final Drawer d = new Drawer(item, meta, count);
            this.drawers.add(d);
            this.topologyDirty = true;
            return d;
        }

        int slotOf(Drawer d) {
            return this.drawers.indexOf(d);
        }

        private int[] slots() {
            final int[] slots = new int[this.drawers.size()];
            for (int i = 0; i < slots.length; i++) {
                slots[i] = i;
            }
            return slots;
        }

        private Drawer[] byId() {
            return this.drawers.toArray(new Drawer[0]);
        }

        private void ensureFresh() {
            if (this.presenceKeys == null || this.topologyDirty || this.builtEpoch != this.epoch) {
                this.rebuild();
            }
        }

        private void rebuild() {
            this.rebuilds++;
            final int[] slots = this.slots();
            final Drawer[] byId = this.byId();

            final LongKeySet keys = new LongKeySet(Math.max(16, slots.length));
            boolean pUsable = true;
            for (int slot : slots) {
                final Drawer d = byId[slot];
                if (!d.enabled) {
                    continue;
                }
                if (d.unknownImplementation) {
                    pUsable = false;
                    break;
                }
                if (d.isEmpty()) {
                    continue;
                }
                final long literal = key(d.item, d.meta);
                keys.add(literal);
                for (long k : equivalentsFor(literal)) {
                    keys.add(k);
                }
            }
            this.presenceKeys = keys;
            this.presenceUsable = pUsable;

            final BuildResult candidates = buildCandidateIndex(slots, byId, this.maxKeys, this.maxSlotRefs);
            this.candidateIndex = candidates.usable
                    && !candidates.keyCapExceeded && !candidates.slotRefCapExceeded
                    ? candidates.index : null;

            this.builtEpoch = this.epoch;
            this.topologyDirty = false;
        }

        boolean mightContain(int item, int meta) {
            this.ensureFresh();
            if (!this.presenceUsable) {
                return true;
            }
            return this.presenceKeys.contains(key(item, meta));
        }

        int[] candidatesFor(int item, int meta) {
            this.ensureFresh();
            if (this.candidateIndex == null) {
                return null;
            }
            return this.candidateIndex.candidatesFor(key(item, meta));
        }
    }


    static int[] simulateExtractItem(Network net, int reqItem, int reqMeta, boolean predicateNonNull,
                                     boolean narrowingEnabled, int[] stockSlots) {
        if (predicateNonNull) {
            return stockSlots;
        }
        if (!net.mightContain(reqItem, reqMeta)) {
            return new int[0];
        }
        if (!narrowingEnabled) {
            return stockSlots;
        }
        final int[] narrowed = net.candidatesFor(reqItem, reqMeta);
        return narrowed != null ? narrowed : stockSlots;
    }


    static void builderOrderAndDuplicateSemantics() {
        final DrawerCandidateIndex.Builder builder = new DrawerCandidateIndex.Builder(1000, 1000);
        check("literal plus equivalents append once per occurrence, not once per emission path",
                builder.addOccurrence(5, key(100, 0), new long[]{key(101, 0), key(101, 0)}),
                "expected the occurrence to be accepted");
        final DrawerCandidateIndex built = builder.build();
        final int[] literalCandidates = built.candidatesFor(key(100, 0));
        final int[] equivalentCandidates = built.candidatesFor(key(101, 0));
        check("the literal key gets exactly one slot reference for one occurrence",
                literalCandidates != null && literalCandidates.length == 1 && literalCandidates[0] == 5,
                Arrays.toString(literalCandidates));
        check("a key repeated within one occurrence's equivalents is still appended only once",
                equivalentCandidates != null && equivalentCandidates.length == 1 && equivalentCandidates[0] == 5,
                Arrays.toString(equivalentCandidates));

        final DrawerCandidateIndex.Builder ordered = new DrawerCandidateIndex.Builder(1000, 1000);
        ordered.addOccurrence(10, key(200, 0), new long[0]);
        ordered.addOccurrence(20, key(500, 0), new long[0]);
        ordered.addOccurrence(30, key(200, 0), new long[0]);
        final int[] multi = ordered.build().candidatesFor(key(200, 0));
        check("multiple literal candidates preserve drawerSlots relative order",
                Arrays.equals(multi, new int[]{10, 30}), Arrays.toString(multi));

        final DrawerCandidateIndex.Builder dup = new DrawerCandidateIndex.Builder(1000, 1000);
        dup.addOccurrence(7, key(300, 0), new long[0]);
        dup.addOccurrence(7, key(300, 0), new long[0]);
        final int[] dupResult = dup.build().candidatesFor(key(300, 0));
        check("duplicate occurrences already present in drawerSlots preserve multiplicity",
                Arrays.equals(dupResult, new int[]{7, 7}), Arrays.toString(dupResult));
    }


    static void literalAndOreEquivalentCandidates() {
        ore("ingotCopper", key(100, 0), new int[][]{{100, 0}, {101, 0}, {102, 3}, {103, WILDCARD}});

        final int[] slots = {0, 1, 2};
        final Drawer[] byId = {
                new Drawer(100, 0, 10),
                new Drawer(500, 0, 10),
                new Drawer(102, 3, 10)
        };
        final BuildResult result = buildCandidateIndex(slots, byId, 1000, 1000);
        check("a literal exact Item/meta request finds its candidate",
                contains(result.index.candidatesFor(key(100, 0)), 0), "expected slot 0");
        check("an ore-equivalent-only request finds its candidate without a literal drawer",
                contains(result.index.candidatesFor(key(102, 3)), 2), "expected slot 2");
        check("an unrelated item is not a candidate", result.index.candidatesFor(key(999, 0)) == null,
                "expected no entry");
    }


    static void conversionOffAndOnConservativeBehavior() {
        ore("ingotBrass", key(400, 0), new int[][]{{400, 0}, {450, 0}});
        final int[] slots = {0};

        final Drawer offDrawer = new Drawer(400, 0, 10);
        final BuildResult off = buildCandidateIndex(slots, new Drawer[]{offDrawer}, 1000, 1000);
        check("conversion-off conservative false positives remain allowed",
                contains(off.index.candidatesFor(key(450, 0)), 0),
                "expected the ore equivalent to remain a candidate even with conversion off");

        final Drawer onDrawer = new Drawer(400, 0, 10);
        onDrawer.dictConvertible = true;
        final BuildResult on = buildCandidateIndex(slots, new Drawer[]{onDrawer}, 1000, 1000);
        check("conversion-on required matches are included",
                contains(on.index.candidatesFor(key(450, 0)), 0),
                "expected the ore equivalent present with conversion on");
    }


    static void nbtNeverCausesAFalseNegative() {
        final int[] slots = {0};
        final Drawer[] byId = {new Drawer(600, 0, 10)};
        final BuildResult result = buildCandidateIndex(slots, byId, 1000, 1000);
        final int[] candidates = result.index.candidatesFor(key(600, 0));
        check("NBT is not part of the key, so a same item/meta request is a candidate regardless of NBT",
                contains(candidates, 0),
                "the candidate model must never omit a slot because of an NBT difference it does not track");
    }


    static void wildcardOreEntryIsNeverRequired() {
        ore("wild", key(700, 0), new int[][]{{700, 0}, {701, WILDCARD}});
        final int[] slots = {0};
        final Drawer[] byId = {new Drawer(700, 0, 10)};
        final BuildResult result = buildCandidateIndex(slots, byId, 1000, 1000);
        check("a wildcard-metadata ore entry contributes no candidate key",
                result.index.candidatesFor(key(701, 5)) == null, "expected no entry for the wildcard item");
    }


    static void sameItemDifferentMetaEdgeCase() {
        ore("dustSibling", key(800, 0), new int[][]{{800, 0}, {800, 7}, {801, 0}});
        final int[] slots = {0};
        final Drawer[] byId = {new Drawer(800, 0, 10)};
        final BuildResult result = buildCandidateIndex(slots, byId, 1000, 1000);
        check("the exact matcher rejects a same-item different-meta sibling",
                !matcherAccepts(800, 0, true, 800, 7),
                "the oracle must reject the sibling before consulting the ore list");
        check("the conservative candidate model keeps the sibling as an intentional false positive",
                contains(result.index.candidatesFor(key(800, 7)), 0),
                "a false positive here is required so the request still runs stock");
        check("a genuine ore equivalent with a different Item is still a candidate",
                contains(result.index.candidatesFor(key(801, 0)), 0), "expected 801:0 present");
    }


    static void builtInFractionalAndCompactingSupport() {
        ore("blockIron", key(900, 0), new int[][]{{900, 0}, {901, 0}});
        final int[] slots = {0, 1};
        final Drawer tier0 = new Drawer(900, 0, 10);
        tier0.fractional = true;
        final Drawer tier1 = new Drawer(901, 0, 10);
        tier1.fractional = true;
        final BuildResult result = buildCandidateIndex(slots, new Drawer[]{tier0, tier1}, 1000, 1000);
        check("a built-in fractional/compacting drawer indexes like any other supported drawer",
                result.usable, "expected the fractional network to remain usable");
        check("a compacting tier prototype is a candidate for its own key",
                contains(result.index.candidatesFor(key(901, 0)), 1), "expected the tier slot");
    }


    static void standardSupportedDrawerBehavior() {
        final int[] slots = {0};
        final Drawer[] byId = {new Drawer(1000, 0, 10)};
        final BuildResult result = buildCandidateIndex(slots, byId, 1000, 1000);
        check("a standard supported drawer builds a usable candidate index",
                result.usable && result.index != null, "expected a usable index");
    }


    static void disabledDrawerContributesNothing() {
        final int[] slots = {0};
        final Drawer disabled = new Drawer(1100, 0, 10);
        disabled.enabled = false;
        final BuildResult result = buildCandidateIndex(slots, new Drawer[]{disabled}, 1000, 1000);
        check("a disabled drawer never contributes a candidate slot",
                result.index == null || result.index.candidatesFor(key(1100, 0)) == null,
                "expected no candidates from a disabled drawer");
    }


    static void emptyDrawerContributesNothing() {
        final int[] slots = {0};
        final Drawer empty = new Drawer(1200, 0, 0);
        final BuildResult result = buildCandidateIndex(slots, new Drawer[]{empty}, 1000, 1000);
        check("an empty drawer's prototype never contributes a candidate slot",
                result.index == null || result.index.candidatesFor(key(1200, 0)) == null,
                "expected no candidates from an empty drawer");
    }


    static void unknownDrawerMakesTheModelUnusable() {
        final int[] slots = {0, 1};
        final Drawer known = new Drawer(1300, 0, 10);
        final Drawer unknown = new Drawer(1301, 0, 10);
        unknown.unknownImplementation = true;
        final BuildResult result = buildCandidateIndex(slots, new Drawer[]{known, unknown}, 1000, 1000);
        check("an unknown drawer implementation refuses the whole candidate model",
                !result.usable && result.index == null, "expected the network unusable for candidate narrowing");
    }


    static void oreExpansionFailureFailsOpen() {
        final int[] slots = {0};
        final Drawer throwing = new Drawer(1400, 0, 10);
        throwing.throwsOnExpansion = true;
        final BuildResult result = buildCandidateIndex(slots, new Drawer[]{throwing}, 1000, 1000);
        check("an ore expansion failure fails open rather than publishing a partial index",
                !result.usable && result.index == null, "expected the build to be refused, not partial");
    }


    static void keyPresentCandidateLookupReturnsExpectedArray() {
        ore("ingotTin", key(1500, 0), new int[][]{{1500, 0}, {1550, 0}});
        final Network net = new Network();
        net.add(1500, 0, 10);
        final int[] stock = {0};
        final int[] served = simulateExtractItem(net, 1550, 0, false, true, stock);
        check("a key-present candidate lookup returns the expected narrowed array",
                Arrays.equals(served, new int[]{0}), Arrays.toString(served));
    }


    static void provenAbsentTakesTheEmptyArrayFastPath() {
        final Network net = new Network();
        net.add(1600, 0, 10);
        final int[] stock = {0};
        final int[] served = simulateExtractItem(net, 9999, 0, false, true, stock);
        check("a proven-absent request still takes the existing empty-array fast path",
                served.length == 0, Arrays.toString(served));
    }


    static void predicateRequestsAlwaysStayStock() {
        final Network net = new Network();
        net.add(1700, 0, 10);
        final int[] stock = {0};
        final int[] served = simulateExtractItem(net, 1700, 0, true, true, stock);
        check("a predicate request always returns the full stock array",
                served == stock, "expected the exact stock array reference");
    }


    static void narrowingToggleOffReturnsStock() {
        final Network net = new Network();
        net.add(1800, 0, 10);
        final int[] stock = {0};
        final int[] served = simulateExtractItem(net, 1800, 0, false, false, stock);
        check("candidate narrowing toggled off returns the full stock array for a key-present request",
                served == stock, "expected the exact stock array reference");
    }


    static void candidateIndexUnavailableReturnsStock() {
        final Network net = new Network();
        final Drawer unknown = net.add(1900, 0, 10);
        unknown.unknownImplementation = true;
        final int[] stock = {0};
        final int[] served = simulateExtractItem(net, 1900, 0, false, true, stock);
        check("an unavailable candidate index returns the full stock array for a key-present request",
                served == stock, "expected the exact stock array reference");
    }


    static void capRefusalKeepsPresenceUsableAndFallsBackForKeyPresent() {
        final int[] slots = {0, 1, 2};
        final Drawer[] byId = {
                new Drawer(2000, 0, 10), new Drawer(2001, 0, 10), new Drawer(2002, 0, 10)
        };
        final BuildResult tinyKeyCap = buildCandidateIndex(slots, byId, 1, 1000000);
        check("a key-cap refusal is visible and publishes no candidate index",
                tinyKeyCap.usable && tinyKeyCap.keyCapExceeded && tinyKeyCap.index == null,
                "expected usable=true, keyCapExceeded=true, index=null");

        final BuildResult tinySlotCap = buildCandidateIndex(slots, byId, 1000, 1);
        check("a slot-reference-cap refusal is visible and publishes no candidate index",
                tinySlotCap.usable && tinySlotCap.slotRefCapExceeded && tinySlotCap.index == null,
                "expected usable=true, slotRefCapExceeded=true, index=null");

        final Network net = new Network();
        net.maxKeys = 1;
        net.add(2000, 0, 10);
        net.add(2001, 0, 10);
        check("presence stays usable after a candidate-only cap refusal",
                net.mightContain(2000, 0), "expected presence to still answer present");
        final int[] stock = {0, 1};
        final int[] served = simulateExtractItem(net, 2000, 0, false, true, stock);
        check("a key-present request falls back to stock when only the candidate cap was hit",
                served == stock, "expected the exact stock array reference");
    }


    static void invariantFallbackOnMissingEntry() {
        final int[] slots = {0};
        final Drawer[] byId = {new Drawer(2100, 0, 5)};
        final BuildResult result = buildCandidateIndex(slots, byId, 1000, 1000);
        final int[] present = result.index.candidatesFor(key(2100, 0));
        check("a genuinely indexed key returns its candidates", present != null && present.length == 1,
                Arrays.toString(present));
        final int[] missing = result.index.candidatesFor(key(4242, 0));
        check("a key with no entry returns null so the caller can fail open to stock, not an empty array",
                missing == null, "expected null, not an empty array");
    }


    static void invalidationRebuildsTheCandidateIndex() {
        final Network net = new Network();
        final Drawer d = net.add(2200, 0, 10);
        net.candidatesFor(2200, 0);
        int before = net.rebuilds;
        net.candidatesFor(2200, 0);
        check("no rebuild happens without an invalidation signal", net.rebuilds == before,
                "unexpected rebuild");

        d.count = 20;
        net.epoch++;
        net.candidatesFor(2200, 0);
        check("content/epoch invalidation rebuilds the candidate index", net.rebuilds == before + 1,
                "expected exactly one rebuild");

        before = net.rebuilds;
        final Drawer joined = net.add(2300, 0, 5);
        net.candidatesFor(2300, 0);
        check("topology invalidation rebuilds the candidate index", net.rebuilds == before + 1,
                "expected the join to trigger a rebuild");
        check("the newly joined drawer becomes a candidate",
                contains(net.candidatesFor(2300, 0), net.slotOf(joined)),
                "expected the joined drawer present");

        before = net.rebuilds;
        d.item = 2400;
        net.epoch++;
        final int[] afterMaterialChange = net.candidatesFor(2400, 0);
        check("a compacting material identity change rebuilds the candidate index",
                net.rebuilds == before + 1, "expected one rebuild");
        check("the changed material is a candidate after the rebuild",
                afterMaterialChange != null && contains(afterMaterialChange, net.slotOf(d)),
                "expected candidate present after the material change");

        before = net.rebuilds;
        d.dictConvertible = true;
        net.epoch++;
        net.candidatesFor(2400, 0);
        check("matcher/attribute invalidation rebuilds the candidate index",
                net.rebuilds == before + 1, "expected one rebuild on conversion toggle");
    }


    static boolean verify(int[] indexed, int[] recomputed) {
        return recomputed != null && Arrays.equals(recomputed, indexed);
    }

    static void verificationComparesIndexedAgainstRecomputedArrays() {
        ore("ingotSilver", key(2500, 0), new int[][]{{2500, 0}, {2550, 0}});
        final int[] slots = {0, 1};
        final Drawer[] byId = {new Drawer(2500, 0, 10), new Drawer(2550, 0, 10)};
        final BuildResult result = buildCandidateIndex(slots, byId, 1000, 1000);
        final int[] indexed = result.index.candidatesFor(key(2500, 0));
        final int[] recomputed = expectedCandidates(slots, byId, key(2500, 0));
        check("verification succeeds when the indexed and recomputed arrays exactly match",
                verify(indexed, recomputed), "expected verification success");

        final int[] tampered = indexed.length > 0 ? new int[]{9999} : new int[]{0};
        check("verification fails and would force stock when the arrays disagree",
                !verify(tampered, recomputed), "expected verification failure");

        check("a refused recomputation fails verification and would force stock",
                !verify(indexed, null), "expected verification failure on refusal");
    }

    static void verificationDefaultsDisabled() {
        check("candidate index verification ships disabled",
                !OptimizationConfig.instrumentCandidateIndexVerification, "expected false by default");
        check("candidate narrowing ships disabled",
                !OptimizationConfig.optimizeDrawerCandidateNarrowing, "expected false by default");
    }


    static void randomizedOracleSweep() {
        final Random rnd = new Random(20260919L);
        int totalChecked = 0;
        int orderMismatches = 0;
        int falseNegatives = 0;

        for (int trial = 0; trial < 200; trial++) {
            final int slotCount = 2 + rnd.nextInt(12);
            final int[] slots = new int[slotCount];
            final Drawer[] byId = new Drawer[slotCount];
            for (int i = 0; i < slotCount; i++) {
                slots[i] = i;
                final int item = 100 + rnd.nextInt(6);
                final int meta = rnd.nextInt(4);
                final int count = rnd.nextInt(4) == 0 ? 0 : 1 + rnd.nextInt(64);
                final Drawer d = new Drawer(item, meta, count);
                d.dictConvertible = rnd.nextBoolean();
                byId[i] = d;
            }

            final BuildResult result = buildCandidateIndex(slots, byId, 100000, 1000000);
            if (!result.usable || result.index == null) {
                continue;
            }

            for (int reqItem = 99; reqItem <= 106; reqItem++) {
                for (int reqMeta = 0; reqMeta <= 4; reqMeta++) {
                    final long requestKey = key(reqItem, reqMeta);
                    final int[] expected = expectedCandidates(slots, byId, requestKey);
                    int[] indexed = result.index.candidatesFor(requestKey);
                    if (indexed == null) {
                        indexed = new int[0];
                    }
                    totalChecked++;
                    if (!Arrays.equals(expected, indexed)) {
                        orderMismatches++;
                    }
                    for (int slot : slots) {
                        final Drawer d = byId[slot];
                        if (d.isEmpty()) {
                            continue;
                        }
                        if (matcherAccepts(d.item, d.meta, d.dictConvertible, reqItem, reqMeta)
                                && !contains(indexed, slot)) {
                            falseNegatives++;
                        }
                    }
                }
            }
        }

        check("candidate arrays match the independent order-preserving computation across "
                + totalChecked + " sweep requests", orderMismatches == 0, orderMismatches + " mismatched");
        check("every oracle-accepted stock match appears as a candidate across the sweep",
                falseNegatives == 0, falseNegatives + " missing candidates");
    }


    public static void main(String[] args) {
        builderOrderAndDuplicateSemantics();
        literalAndOreEquivalentCandidates();
        conversionOffAndOnConservativeBehavior();
        nbtNeverCausesAFalseNegative();
        wildcardOreEntryIsNeverRequired();
        sameItemDifferentMetaEdgeCase();
        builtInFractionalAndCompactingSupport();
        standardSupportedDrawerBehavior();
        disabledDrawerContributesNothing();
        emptyDrawerContributesNothing();
        unknownDrawerMakesTheModelUnusable();
        oreExpansionFailureFailsOpen();
        keyPresentCandidateLookupReturnsExpectedArray();
        provenAbsentTakesTheEmptyArrayFastPath();
        predicateRequestsAlwaysStayStock();
        narrowingToggleOffReturnsStock();
        candidateIndexUnavailableReturnsStock();
        capRefusalKeepsPresenceUsableAndFallsBackForKeyPresent();
        invariantFallbackOnMissingEntry();
        invalidationRebuildsTheCandidateIndex();
        verificationComparesIndexedAgainstRecomputedArrays();
        verificationDefaultsDisabled();
        randomizedOracleSweep();

        System.out.println(failures == 0 ? "\nCandidateIndexTest: ALL PASS"
                : "\nCandidateIndexTest: " + failures + " FAILURES");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
