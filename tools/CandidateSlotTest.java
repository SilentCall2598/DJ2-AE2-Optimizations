import dj2.ae2opt.core.CandidateSlotSampler;
import dj2.ae2opt.core.Diagnostics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class CandidateSlotTest {

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
        return ((long) item << 32) | (meta & 0xFFFFFFFFL);
    }


    static final Map<String, int[][]> ORE = new LinkedHashMap<String, int[][]>();
    static final Map<Long, String> ORE_OF = new LinkedHashMap<Long, String>();


    static void ore(String name, int[][] entries) {
        ORE.put(name, entries);
        for (int[] e : entries) {
            if (e[1] != WILDCARD) {
                ORE_OF.put(key(e[0], e[1]), name);
            }
        }
    }

    static final class Slot {
        final int id;
        final int item, meta;
        final boolean supported;

        Slot(int id, int item, int meta, boolean supported) {
            this.id = id; this.item = item; this.meta = meta; this.supported = supported;
        }

        long protoKey() { return key(item, meta); }


        boolean covers(long requestKey) {
            if (protoKey() == requestKey) {
                return true;
            }
            String name = ORE_OF.get(protoKey());
            if (name == null) {
                return false;
            }
            for (int[] e : ORE.get(name)) {
                if (e[1] == WILDCARD) {
                    continue;
                }
                if (key(e[0], e[1]) == requestKey) {
                    return true;
                }
            }
            return false;
        }
    }

    static CandidateSlotSampler.Sample run(final List<Slot> slots, long requestKey) {
        return CandidateSlotSampler.sample(requestKey, new CandidateSlotSampler.SlotSource() {
            public int count() { return slots.size(); }
            public int slotAt(int i) { return slots.get(i).id; }
            public boolean supported(int i) { return slots.get(i).supported; }
            public boolean covers(int i, long key) { return slots.get(i).covers(key); }
            public boolean literal(int i, long key) { return slots.get(i).protoKey() == key; }
        });
    }


    static void globalSamplingGate() {
        final int interval = Diagnostics.CANDIDATE_SAMPLE_INTERVAL;
        int taken = 0;
        for (int i = 0; i < interval - 1; i++) {
            if (Diagnostics.shouldSampleNegativeCandidate()) {
                taken++;
            }
        }
        check("511 eligible requests produce no sample", taken == 0, "took " + taken);
        check("the 512th is the first sample", Diagnostics.shouldSampleNegativeCandidate(),
                "expected a sample on the interval boundary");
        for (int i = 0; i < interval - 1; i++) {
            Diagnostics.shouldSampleNegativeCandidate();
        }
        check("the 1024th is the second sample", Diagnostics.shouldSampleNegativeCandidate(),
                "expected the second sample");
        check("eligible requests are all counted for the sanity check",
                Diagnostics.candidateEligible == 2L * interval,
                "counted " + Diagnostics.candidateEligible);


        int before = 0;
        for (int i = 0; i < interval - 1; i++) {
            if (Diagnostics.shouldSampleNegativeCandidate()) {
                before++;
            }
        }
        check("sampling does not restart per controller", before == 0,
                "a global sequence should not fire again until the next boundary");
    }

    static void ratioBuckets() {
        check("a tiny retained fraction lands in the lowest bucket",
                CandidateSlotSampler.ratioBucket(1, 1000) == 0, "1/1000");
        check("a boundary belongs to the bucket it names",
                CandidateSlotSampler.ratioBucket(1, 100) == 0
                        && CandidateSlotSampler.ratioBucket(5, 100) == 1
                        && CandidateSlotSampler.ratioBucket(6, 100) == 2,
                "1%, 5% and 6% should be <=1%, 1-5% and 5-10%");
        check("everything above the last bound is the open bucket",
                CandidateSlotSampler.ratioBucket(80, 100) == CandidateSlotSampler.RATIO_BUCKETS_PERCENT.length,
                "80% should be >75%");
        check("zero candidates has no ratio bucket",
                CandidateSlotSampler.ratioBucket(0, 100) < 0,
                "a disagreement is not a 0% narrowing");
        check("ratio labels read as ranges",
                CandidateSlotSampler.ratioBucketLabel(0).equals("<=1%")
                        && CandidateSlotSampler.ratioBucketLabel(2).equals("5-10%")
                        && CandidateSlotSampler.ratioBucketLabel(6).equals(">75%"),
                CandidateSlotSampler.ratioBucketLabel(2));
    }


    static void diagnosticFailureIsContained() {
        CandidateSlotSampler.Sample sample = CandidateSlotSampler.sample(key(1, 0),
                new CandidateSlotSampler.SlotSource() {
                    public int count() { return 3; }
                    public int slotAt(int i) { return i; }
                    public boolean supported(int i) { return true; }
                    public boolean covers(int i, long k) {
                        throw new IllegalStateException("ore expansion failed");
                    }
                    public boolean literal(int i, long k) { return false; }
                });
        check("a throwing re-walk is contained, not propagated",
                sample.errored && !sample.refused, "expected errored");
        check("an errored sample measures nothing",
                sample.candidates == 0 && sample.recorded == 0 && !sample.anyLiteral,
                "expected no measurement");
    }

    public static void main(String[] args) {
        ore("ingotCopper", new int[][]{{100, 0}, {101, 0}, {103, WILDCARD}});
        ore("dustSibling", new int[][]{{300, 0}, {300, 7}});

        List<Slot> net = new ArrayList<Slot>(Arrays.asList(
                new Slot(10, 100, 0, true),
                new Slot(11, 500, 0, true),
                new Slot(12, 100, 0, true),
                new Slot(13, 101, 0, true),
                new Slot(14, 700, 2, true)));

        CandidateSlotSampler.Sample literal = run(net, key(100, 0));
        check("full array length is the whole controller", literal.fullLength == 5,
                "got " + literal.fullLength);
        check("literal and duplicate and ore-equivalent all counted", literal.candidates == 3,
                "got " + literal.candidates);
        check("duplicates are counted separately, not collapsed",
                literal.recorded >= 2 && literal.slots[0] == 10 && literal.slots[1] == 12,
                Arrays.toString(Arrays.copyOf(literal.slots, literal.recorded)));
        check("slot order matches the original array",
                literal.slots[0] == 10 && literal.slots[1] == 12 && literal.slots[2] == 13,
                Arrays.toString(Arrays.copyOf(literal.slots, literal.recorded)));
        check("a literal prototype is reported", literal.anyLiteral, "expected a literal candidate");


        CandidateSlotSampler.Sample oreOnly = run(net, key(101, 0));
        check("an ore-only request still finds its candidates", oreOnly.candidates == 3,
                "got " + oreOnly.candidates);
        check("an ore-only request reports a literal because a drawer stores that item exactly",
                oreOnly.anyLiteral, "slot 13 stores 101:0");

        List<Slot> noLiteral = new ArrayList<Slot>(Arrays.asList(
                new Slot(20, 100, 0, true), new Slot(21, 900, 0, true)));
        CandidateSlotSampler.Sample pureOre = run(noLiteral, key(101, 0));
        check("a request served only through ore expansion is classified as such",
                pureOre.candidates == 1 && !pureOre.anyLiteral, "candidates " + pureOre.candidates);


        List<Slot> comp = new ArrayList<Slot>(Arrays.asList(
                new Slot(30, 200, 0, true), new Slot(31, 201, 0, true)));
        check("a compacting tier prototype is found by its own key",
                run(comp, key(201, 0)).candidates == 1, "expected the tier slot");


        List<Slot> sibling = new ArrayList<Slot>(Arrays.asList(new Slot(40, 300, 0, true)));
        check("a same-Item different-meta sibling is a conservative candidate",
                run(sibling, key(300, 7)).candidates == 1,
                "expected the sibling counted, since the index would also hand it to stock");


        List<Slot> wild = new ArrayList<Slot>(Arrays.asList(new Slot(50, 100, 0, true)));
        check("a wildcard ore entry is not a candidate", run(wild, key(103, 0)).candidates == 0,
                "expected no candidate for a wildcard-only entry");


        List<Slot> mixed = new ArrayList<Slot>(Arrays.asList(
                new Slot(60, 100, 0, true), new Slot(61, 100, 0, false), new Slot(62, 100, 0, true)));
        CandidateSlotSampler.Sample refused = run(mixed, key(100, 0));
        check("an unsupported drawer refuses the sample", refused.refused, "expected refusal");
        check("a refused sample reports no candidates rather than an undercount",
                refused.candidates == 0 && refused.recorded == 0,
                "candidates " + refused.candidates);


        CandidateSlotSampler.Sample none = run(noLiteral, key(4242, 0));
        check("zero candidates is representable and not an error", !none.refused && none.candidates == 0,
                "expected a clean zero");


        check("bucket labels are ranges",
                CandidateSlotSampler.bucketLabel(0).equals("0")
                        && CandidateSlotSampler.bucketLabel(1).equals("1")
                        && CandidateSlotSampler.bucketLabel(2).equals("2-4")
                        && CandidateSlotSampler.bucketLabel(6).equals("257+"),
                CandidateSlotSampler.bucketLabel(2) + " / " + CandidateSlotSampler.bucketLabel(6));
        check("bucket boundaries land where the labels say",
                CandidateSlotSampler.bucket(0) == 0 && CandidateSlotSampler.bucket(1) == 1
                        && CandidateSlotSampler.bucket(4) == 2 && CandidateSlotSampler.bucket(5) == 3
                        && CandidateSlotSampler.bucket(257) == 6,
                "boundary mismatch");

        globalSamplingGate();
        ratioBuckets();
        diagnosticFailureIsContained();

        System.out.println(failures == 0 ? "\nCandidateSlotTest: ALL PASS"
                : "\nCandidateSlotTest: " + failures + " FAILURES");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
