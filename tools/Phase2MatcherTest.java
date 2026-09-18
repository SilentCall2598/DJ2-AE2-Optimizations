import dj2.ae2opt.core.CandidateSlotSampler;
import dj2.ae2opt.core.Diagnostics;
import dj2.ae2opt.core.Phase2MatcherContext;

import java.util.Arrays;


public final class Phase2MatcherTest {

    static int failures = 0;

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            System.out.println("pass  " + name);
        } else {
            System.out.println("FAIL  " + name + ": " + detail);
            failures++;
        }
    }

    static void bucketBoundaries() {
        int[] buckets = CandidateSlotSampler.PHASE2_MATCHER_CANDIDATE_BUCKETS;
        check("candidate-call buckets are 1, 2, 5, 17",
                Arrays.equals(buckets, new int[]{1, 2, 5, 17}), Arrays.toString(buckets));
        check("zero lands in bucket 0", CandidateSlotSampler.bucket(0, buckets) == 0, "expected 0");
        check("one lands in bucket 1", CandidateSlotSampler.bucket(1, buckets) == 1, "expected 1");
        check("four lands in bucket 2", CandidateSlotSampler.bucket(4, buckets) == 2, "expected 2");
        check("sixteen lands in bucket 3", CandidateSlotSampler.bucket(16, buckets) == 3, "expected 3");
        check("seventeen lands in the open bucket",
                CandidateSlotSampler.bucket(17, buckets) == buckets.length, "expected " + buckets.length);
        check("labels read as ranges",
                CandidateSlotSampler.bucketLabel(0, buckets).equals("0")
                        && CandidateSlotSampler.bucketLabel(1, buckets).equals("1")
                        && CandidateSlotSampler.bucketLabel(2, buckets).equals("2-4")
                        && CandidateSlotSampler.bucketLabel(3, buckets).equals("5-16")
                        && CandidateSlotSampler.bucketLabel(4, buckets).equals("17+"),
                CandidateSlotSampler.bucketLabel(2, buckets) + " / " + CandidateSlotSampler.bucketLabel(4, buckets));
        check("the default overload still uses the structural buckets",
                CandidateSlotSampler.bucketLabel(6).equals("257+"), CandidateSlotSampler.bucketLabel(6));
    }

    static void basicLifecycleWithAllThreeClassifications() {
        long attemptedBefore = Diagnostics.phase2MatcherSamplesAttempted;
        long completedBefore = Diagnostics.phase2MatcherSamplesCompleted;
        long callsBefore = Diagnostics.phase2MatcherCallsTotal;
        long candidatesBefore = Diagnostics.phase2CandidateMatcherCallsTotal;
        long nonCandidatesBefore = Diagnostics.phase2NonCandidateMatcherCallsTotal;
        long unclassifiedBefore = Diagnostics.phase2UnclassifiedMatcherCallsTotal;

        check("inactive before enter", !Phase2MatcherContext.active(), "expected inactive");

        Diagnostics.phase2MatcherSampleAttempted();
        Phase2MatcherContext.enter();
        check("active after enter", Phase2MatcherContext.active(), "expected active");

        Phase2MatcherContext.recordMatcherCall(Phase2MatcherContext.CANDIDATE);
        Phase2MatcherContext.recordMatcherCall(Phase2MatcherContext.NON_CANDIDATE);
        Phase2MatcherContext.recordMatcherCall(Phase2MatcherContext.CANDIDATE);
        Phase2MatcherContext.recordMatcherCall(Phase2MatcherContext.UNCLASSIFIED);

        Phase2MatcherContext.exit();
        check("inactive after exit", !Phase2MatcherContext.active(), "expected inactive");

        check("one sample attempted", Diagnostics.phase2MatcherSamplesAttempted == attemptedBefore + 1,
                "attempted=" + Diagnostics.phase2MatcherSamplesAttempted);
        check("one sample completed", Diagnostics.phase2MatcherSamplesCompleted == completedBefore + 1,
                "completed=" + Diagnostics.phase2MatcherSamplesCompleted);
        check("four matcher calls recorded", Diagnostics.phase2MatcherCallsTotal == callsBefore + 4,
                "total=" + Diagnostics.phase2MatcherCallsTotal);
        check("two candidate-member calls recorded",
                Diagnostics.phase2CandidateMatcherCallsTotal == candidatesBefore + 2,
                "candidates=" + Diagnostics.phase2CandidateMatcherCallsTotal);
        check("one classified non-candidate call recorded",
                Diagnostics.phase2NonCandidateMatcherCallsTotal == nonCandidatesBefore + 1,
                "non-candidates=" + Diagnostics.phase2NonCandidateMatcherCallsTotal);
        check("one unclassified call recorded",
                Diagnostics.phase2UnclassifiedMatcherCallsTotal == unclassifiedBefore + 1,
                "unclassified=" + Diagnostics.phase2UnclassifiedMatcherCallsTotal);
        check("candidate + non-candidate + unclassified reconciles with the total",
                Diagnostics.phase2MatcherCallsTotal
                        == Diagnostics.phase2CandidateMatcherCallsTotal
                                + Diagnostics.phase2NonCandidateMatcherCallsTotal
                                + Diagnostics.phase2UnclassifiedMatcherCallsTotal,
                "total=" + Diagnostics.phase2MatcherCallsTotal
                        + " candidate=" + Diagnostics.phase2CandidateMatcherCallsTotal
                        + " nonCandidate=" + Diagnostics.phase2NonCandidateMatcherCallsTotal
                        + " unclassified=" + Diagnostics.phase2UnclassifiedMatcherCallsTotal);
    }

    static void recordingOutsideContextIsIgnored() {
        long callsBefore = Diagnostics.phase2MatcherCallsTotal;
        Phase2MatcherContext.recordMatcherCall(Phase2MatcherContext.CANDIDATE);
        check("a call outside any entered sample is dropped, not attributed",
                Diagnostics.phase2MatcherCallsTotal == callsBefore,
                "total changed to " + Diagnostics.phase2MatcherCallsTotal);
    }

    static void zeroCallSampleIsCountedNotRatioed() {
        long zeroBefore = Diagnostics.phase2ZeroMatcherCallSamples;
        Phase2MatcherContext.enter();
        Phase2MatcherContext.exit();
        check("a sample with no matcher calls is counted as zero-call",
                Diagnostics.phase2ZeroMatcherCallSamples == zeroBefore + 1,
                "zero-call=" + Diagnostics.phase2ZeroMatcherCallSamples);
    }

    static void unclassifiedCallExcludesSampleFromRetainedFraction() {
        long unclassifiedSamplesBefore = Diagnostics.phase2SamplesWithUnclassifiedCalls;
        long candidatesBefore = Diagnostics.phase2CandidateMatcherCallsTotal;

        Phase2MatcherContext.enter();
        Phase2MatcherContext.recordMatcherCall(Phase2MatcherContext.CANDIDATE);
        Phase2MatcherContext.recordMatcherCall(Phase2MatcherContext.UNCLASSIFIED);
        Phase2MatcherContext.exit();

        check("a sample with an unclassified call is flagged, excluded from the retained fraction",
                Diagnostics.phase2SamplesWithUnclassifiedCalls == unclassifiedSamplesBefore + 1,
                "unclassified samples=" + Diagnostics.phase2SamplesWithUnclassifiedCalls);
        check("its classified calls still reach the aggregate totals",
                Diagnostics.phase2CandidateMatcherCallsTotal == candidatesBefore + 1,
                "candidates=" + Diagnostics.phase2CandidateMatcherCallsTotal);
    }

    static void cleanlyClassifiedSampleIsNotFlaggedUnclassified() {
        long unclassifiedSamplesBefore = Diagnostics.phase2SamplesWithUnclassifiedCalls;

        Phase2MatcherContext.enter();
        Phase2MatcherContext.recordMatcherCall(Phase2MatcherContext.CANDIDATE);
        Phase2MatcherContext.recordMatcherCall(Phase2MatcherContext.NON_CANDIDATE);
        Phase2MatcherContext.exit();

        check("a fully classified sample does not count toward the unclassified-sample total",
                Diagnostics.phase2SamplesWithUnclassifiedCalls == unclassifiedSamplesBefore,
                "unclassified samples=" + Diagnostics.phase2SamplesWithUnclassifiedCalls);
    }

    static void candidateModelInvalidSamplesAreVisibleAndDoNotEnter() {
        long attemptedBefore = Diagnostics.phase2MatcherSamplesAttempted;
        long refusedBefore = Diagnostics.phase2CandidateModelRefused;
        long erroredBefore = Diagnostics.phase2CandidateModelErrored;
        long enteredCompletedBefore = Diagnostics.phase2MatcherSamplesCompleted;

        CandidateSlotSampler.Sample refused = CandidateSlotSampler.sample(1L, null);
        Diagnostics.phase2MatcherSampleAttempted();
        if (refused.errored) {
            Diagnostics.phase2CandidateModelErrored();
        } else if (refused.refused) {
            Diagnostics.phase2CandidateModelRefused();
        } else {
            Phase2MatcherContext.enter();
        }

        CandidateSlotSampler.Sample errored = CandidateSlotSampler.sample(2L,
                new CandidateSlotSampler.SlotSource() {
                    public int count() { return 1; }
                    public int slotAt(int i) { return i; }
                    public boolean supported(int i) { return true; }
                    public boolean covers(int i, long k) { throw new IllegalStateException("boom"); }
                    public boolean literal(int i, long k) { return false; }
                });
        Diagnostics.phase2MatcherSampleAttempted();
        if (errored.errored) {
            Diagnostics.phase2CandidateModelErrored();
        } else if (errored.refused) {
            Diagnostics.phase2CandidateModelRefused();
        } else {
            Phase2MatcherContext.enter();
        }

        check("both invalid-model samples are counted as attempted",
                Diagnostics.phase2MatcherSamplesAttempted == attemptedBefore + 2,
                "attempted=" + Diagnostics.phase2MatcherSamplesAttempted);
        check("a refused candidate model is visibly counted",
                Diagnostics.phase2CandidateModelRefused == refusedBefore + 1,
                "refused=" + Diagnostics.phase2CandidateModelRefused);
        check("an errored candidate model is visibly counted",
                Diagnostics.phase2CandidateModelErrored == erroredBefore + 1,
                "errored=" + Diagnostics.phase2CandidateModelErrored);
        check("neither invalid-model sample activated the matcher-call context",
                !Phase2MatcherContext.active(), "expected inactive");
        check("neither invalid-model sample can complete a matcher-call comparison",
                Diagnostics.phase2MatcherSamplesCompleted == enteredCompletedBefore,
                "completed=" + Diagnostics.phase2MatcherSamplesCompleted);
    }

    static void nestedEntryIsSafeAndCounted() {
        long nestedBefore = Diagnostics.phase2MatcherSamplesNested;
        long completedBefore = Diagnostics.phase2MatcherSamplesCompleted;

        Phase2MatcherContext.enter();
        Phase2MatcherContext.recordMatcherCall(Phase2MatcherContext.CANDIDATE);
        Phase2MatcherContext.enter();
        Phase2MatcherContext.recordMatcherCall(Phase2MatcherContext.NON_CANDIDATE);
        Phase2MatcherContext.exit();
        Phase2MatcherContext.recordMatcherCall(Phase2MatcherContext.CANDIDATE);
        Phase2MatcherContext.exit();

        check("nesting is observed", Diagnostics.phase2MatcherSamplesNested == nestedBefore + 1,
                "nested=" + Diagnostics.phase2MatcherSamplesNested);
        check("both frames complete", Diagnostics.phase2MatcherSamplesCompleted == completedBefore + 2,
                "completed=" + Diagnostics.phase2MatcherSamplesCompleted);
        check("inactive once every frame has exited", !Phase2MatcherContext.active(), "expected inactive");
    }

    static void leakIsResetAtTickEnd() {
        Phase2MatcherContext.enter();
        Phase2MatcherContext.enter();
        check("two unresolved frames before reset", Phase2MatcherContext.active(), "expected active");
        int leaked = Phase2MatcherContext.resetAtServerTickEnd();
        check("both leaked frames are reported", leaked == 2, "leaked=" + leaked);
        check("inactive after the reset", !Phase2MatcherContext.active(), "expected inactive");
        check("a clean context reports no leak", Phase2MatcherContext.resetAtServerTickEnd() == 0,
                "expected 0");
    }

    static void extraExitIsSafe() {
        Phase2MatcherContext.exit();
        Phase2MatcherContext.exit();
        check("inactive after exits with nothing entered", !Phase2MatcherContext.active(), "expected inactive");
    }

    static void sequentialSamplesDoNotLeakIntoEachOther() {
        Phase2MatcherContext.enter();
        Phase2MatcherContext.recordMatcherCall(Phase2MatcherContext.CANDIDATE);
        Phase2MatcherContext.exit();

        long callsBeforeSecond = Diagnostics.phase2MatcherCallsTotal;
        Phase2MatcherContext.enter();
        check("a fresh frame starts with no calls from the previous, already-closed sample",
                Diagnostics.phase2MatcherCallsTotal == callsBeforeSecond, "expected unchanged before recording");
        Phase2MatcherContext.recordMatcherCall(Phase2MatcherContext.NON_CANDIDATE);
        Phase2MatcherContext.exit();
        check("only the second sample's own call was added",
                Diagnostics.phase2MatcherCallsTotal == callsBeforeSecond + 1,
                "total=" + Diagnostics.phase2MatcherCallsTotal);
    }

    static void modelContradictionCounterIsVisible() {
        long before = Diagnostics.phase2ModelContradictions;
        Diagnostics.phase2ModelContradiction();
        Diagnostics.phase2ModelContradiction();
        check("model contradictions accumulate and are never hidden",
                Diagnostics.phase2ModelContradictions == before + 2,
                "contradictions=" + Diagnostics.phase2ModelContradictions);
    }

    static void classifyErrorsAreCountedSeparatelyFromCalls() {
        long errorsBefore = Diagnostics.phase2MatcherCallClassifyErrors;
        long callsBefore = Diagnostics.phase2MatcherCallsTotal;
        long unclassifiedBefore = Diagnostics.phase2UnclassifiedMatcherCallsTotal;
        Phase2MatcherContext.enter();
        Diagnostics.phase2MatcherCallClassifyError();
        Phase2MatcherContext.recordMatcherCall(Phase2MatcherContext.UNCLASSIFIED);
        Phase2MatcherContext.exit();
        check("a classify error is counted", Diagnostics.phase2MatcherCallClassifyErrors == errorsBefore + 1,
                "errors=" + Diagnostics.phase2MatcherCallClassifyErrors);
        check("the invocation itself is still counted as an actual phase-2 call",
                Diagnostics.phase2MatcherCallsTotal == callsBefore + 1,
                "calls=" + Diagnostics.phase2MatcherCallsTotal);
        check("and as unclassified, not as a classified non-candidate",
                Diagnostics.phase2UnclassifiedMatcherCallsTotal == unclassifiedBefore + 1,
                "unclassified=" + Diagnostics.phase2UnclassifiedMatcherCallsTotal);
    }

    public static void main(String[] args) {
        bucketBoundaries();
        basicLifecycleWithAllThreeClassifications();
        recordingOutsideContextIsIgnored();
        zeroCallSampleIsCountedNotRatioed();
        unclassifiedCallExcludesSampleFromRetainedFraction();
        cleanlyClassifiedSampleIsNotFlaggedUnclassified();
        candidateModelInvalidSamplesAreVisibleAndDoNotEnter();
        nestedEntryIsSafeAndCounted();
        leakIsResetAtTickEnd();
        extraExitIsSafe();
        sequentialSamplesDoNotLeakIntoEachOther();
        modelContradictionCounterIsVisible();
        classifyErrorsAreCountedSeparatelyFromCalls();

        System.out.println(failures == 0 ? "\nPhase2MatcherTest: ALL PASS"
                : "\nPhase2MatcherTest: " + failures + " FAILURES");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
