package dj2.ae2opt.core;


public final class CandidateSlotSampler {


    public static final int[] BUCKETS = {1, 2, 5, 17, 65, 257};


    public static final int[] RATIO_BUCKETS_PERCENT = {1, 5, 10, 25, 50, 75};


    public static final int[] PHASE2_MATCHER_CANDIDATE_BUCKETS = {1, 2, 5, 17};


    private static final int RECORDED_SLOTS = 8;


    public interface SlotSource {

        int count();


        int slotAt(int i);


        boolean supported(int i);


        boolean covers(int i, long requestKey);


        boolean literal(int i, long requestKey);
    }

    public static final class Sample {

        public boolean refused;

        public boolean errored;
        public int fullLength;
        public int candidates;
        public boolean anyLiteral;
        public final int[] slots = new int[RECORDED_SLOTS];
        public int recorded;
    }

    private CandidateSlotSampler() {
    }


    public static Sample sample(long requestKey, SlotSource source) {
        final Sample out = new Sample();
        if (source == null) {
            out.refused = true;
            return out;
        }
        try {
            return walk(requestKey, source, out);
        } catch (RuntimeException e) {
            out.errored = true;
            out.refused = false;
            out.candidates = 0;
            out.recorded = 0;
            out.anyLiteral = false;
            return out;
        }
    }

    private static Sample walk(long requestKey, SlotSource source, Sample out) {
        out.fullLength = source.count();
        for (int i = 0; i < out.fullLength; i++) {
            if (!source.supported(i)) {
                out.refused = true;
                out.candidates = 0;
                out.recorded = 0;
                out.anyLiteral = false;
                return out;
            }
            if (!source.covers(i, requestKey)) {
                continue;
            }
            out.candidates++;
            if (source.literal(i, requestKey)) {
                out.anyLiteral = true;
            }
            if (out.recorded < RECORDED_SLOTS) {
                out.slots[out.recorded++] = source.slotAt(i);
            }
        }
        return out;
    }


    public static int ratioBucket(int candidates, int fullLength) {
        if (candidates <= 0 || fullLength <= 0) {
            return -1;
        }
        final double percent = 100.0D * candidates / fullLength;
        for (int i = 0; i < RATIO_BUCKETS_PERCENT.length; i++) {
            if (percent <= RATIO_BUCKETS_PERCENT[i]) {
                return i;
            }
        }
        return RATIO_BUCKETS_PERCENT.length;
    }

    public static String ratioBucketLabel(int index) {
        if (index < 0) {
            return "n/a";
        }
        if (index == RATIO_BUCKETS_PERCENT.length) {
            return ">" + RATIO_BUCKETS_PERCENT[RATIO_BUCKETS_PERCENT.length - 1] + "%";
        }
        if (index == 0) {
            return "<=" + RATIO_BUCKETS_PERCENT[0] + "%";
        }
        return RATIO_BUCKETS_PERCENT[index - 1] + "-" + RATIO_BUCKETS_PERCENT[index] + "%";
    }


    public static int bucket(int value) {
        return bucket(value, BUCKETS);
    }

    public static String bucketLabel(int index) {
        return bucketLabel(index, BUCKETS);
    }

    public static int bucket(int value, int[] buckets) {
        if (value <= 0) {
            return 0;
        }
        for (int i = buckets.length - 1; i >= 0; i--) {
            if (value >= buckets[i]) {
                return i + 1;
            }
        }
        return 0;
    }

    public static String bucketLabel(int index, int[] buckets) {
        if (index == 0) {
            return "0";
        }
        final int low = buckets[index - 1];
        if (index == buckets.length) {
            return low + "+";
        }
        final int high = buckets[index] - 1;
        return low == high ? String.valueOf(low) : low + "-" + high;
    }
}
