package dj2.ae2opt.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


public final class Diagnostics {

    public static final Logger LOG = MixinStatus.LOG;
    public static final Logger RUNTIME = MixinStatus.RUNTIME_LOG;


    public static final String STORE_ID = fingerprint();

    private static final int[] BUCKETS = {0, 1, 2, 4, 8, 16, 32, 64};

    private static final int KINDS = NetworkRequestContext.KINDS;
    private static final String[] KIND_NAMES = {
        "extract SIM", "extract MOD", "inject  SIM", "inject  MOD"
    };

    private static final long[] networkRequests = new long[KINDS];
    private static final long[] networkProbes = new long[KINDS];
    private static final long[] networkZeroProbes = new long[KINDS];
    private static final int[] networkMaxProbes = new int[KINDS];
    private static final long[][] networkProbeBuckets = new long[KINDS][BUCKETS.length];
    private static long networkNestedRequests;
    private static long probesOutsideNetworkRequest;
    private static long networkContextResets;

    private static boolean loggedNetworkFanOut;
    private static boolean loggedFanOutProbe;
    private static boolean loggedNegativeServed;

    public static long negativeConsidered;
    public static long negativeServed;
    public static long negativePredicateFallbacks;
    public static long negativeHolderMissing;
    public static long presenceEpochBumps;
    public static long presenceRebuildsContent;
    public static long presenceRebuildsTopology;
    public static long presenceRebuildsUnusable;
    public static long presenceKeysIndexed;
    public static long negativeUnindexableFallbacks;
    public static long negativeKeyPresentFallbacks;
    public static long presenceUnknownDrawerRefusals;
    public static long presenceFractionalIndexed;
    public static long presenceDictConvertibleIndexed;
    public static long presenceOreKeysAdded;
    public static long presenceRebuildsWithFractional;
    public static long oreExpansionFailures;


    public static final int CANDIDATE_SAMPLE_INTERVAL = 512;


    private static final java.util.concurrent.atomic.AtomicLong CANDIDATE_SEQUENCE =
            new java.util.concurrent.atomic.AtomicLong();

    public static long candidateEligible;
    public static long candidateSamples;
    public static long candidateSamplesRefused;
    public static long candidateSamplesErrored;
    public static long candidateZeroResults;
    public static long candidateLiteralSamples;
    public static long candidateOreOnlySamples;
    public static long candidateSlotsTotal;
    public static long candidateFullLengthTotal;
    private static final long[] CANDIDATE_BUCKETS = new long[CandidateSlotSampler.BUCKETS.length + 1];
    private static final long[] FULL_LENGTH_BUCKETS = new long[CandidateSlotSampler.BUCKETS.length + 1];
    private static final long[] RATIO_BUCKETS =
            new long[CandidateSlotSampler.RATIO_BUCKETS_PERCENT.length + 1];


    public static long phase2MatcherSamplesAttempted;
    public static long phase2CandidateModelRefused;
    public static long phase2CandidateModelErrored;
    public static long phase2MatcherSamplesCompleted;
    public static long phase2MatcherSamplesLeaked;
    public static long phase2MatcherSamplesNested;
    public static long phase2ZeroMatcherCallSamples;
    public static long phase2SamplesWithUnclassifiedCalls;
    public static long phase2MatcherCallsTotal;
    public static long phase2CandidateMatcherCallsTotal;
    public static long phase2NonCandidateMatcherCallsTotal;
    public static long phase2UnclassifiedMatcherCallsTotal;
    public static long phase2MatcherCallClassifyErrors;
    public static long phase2ModelContradictions;
    private static final long[] PHASE2_MATCHER_CALL_BUCKETS = new long[CandidateSlotSampler.BUCKETS.length + 1];
    private static final long[] PHASE2_CANDIDATE_CALL_BUCKETS =
            new long[CandidateSlotSampler.PHASE2_MATCHER_CANDIDATE_BUCKETS.length + 1];
    private static final long[] PHASE2_RETAINED_FRACTION_BUCKETS =
            new long[CandidateSlotSampler.RATIO_BUCKETS_PERCENT.length + 1];
    private static boolean loggedPhase2MatcherSample;


    public static long candidateNarrowingEligible;
    public static long candidateNarrowingServed;
    public static long candidateNarrowingStockFallback;
    public static long candidateNarrowingInvariantFallbacks;
    public static long candidateIndexBuilds;
    public static long candidateIndexBuildFailures;
    public static long candidateIndexKeyCapRefusals;
    public static long candidateIndexSlotRefCapRefusals;
    public static long candidateKeysBuiltTotal;
    public static long candidateSlotReferencesBuiltTotal;
    public static long candidateSlotsReturnedTotal;
    public static long candidateFullSlotsTotal;
    private static boolean loggedCandidateNarrowingServed;

    public static final int CANDIDATE_VERIFICATION_SAMPLE_INTERVAL = 512;
    private static final java.util.concurrent.atomic.AtomicLong CANDIDATE_VERIFICATION_SEQUENCE =
            new java.util.concurrent.atomic.AtomicLong();
    public static long candidateVerificationEligible;
    public static long candidateVerificationAttempted;
    public static long candidateVerificationVerified;
    public static long candidateVerificationRefused;
    public static long candidateVerificationErrored;
    public static long candidateVerificationMismatches;


    public static final int EPOCH_STANDARD = 0;
    public static final int EPOCH_COMPACTING = 1;
    public static final int EPOCH_ATTRIBUTES = 2;
    private static final long[] EPOCH_BUMPS = new long[3];
    private static boolean loggedOptimizedPath;
    private static boolean loggedTemplateHit;
    private static boolean loggedSkip;
    private static boolean loggedExtractionDiagnostics;
    private static boolean loggedPoweredExtractionContext;
    private static boolean loggedItemHandlerExtraction;

    private static long serverTicks;

    public static long pollsSkipped;
    public static long pollsRebuilt;
    public static long changesPosted;
    public static long directDiffPolls;
    public static long templateHits;
    public static long templateMisses;
    public static long templateCachePrunes;
    public static long templateEntriesPruned;
    public static long emptyPrototypesSkipped;
    public static long templateCachesDisabled;
    public static long templateCachesOversized;
    public static long templateCachesConfirmed;
    public static long fallbackConversions;
    public static long pollFailures;
    public static long itemHandlerExtractionsObserved;


    public static long extractionCalls;
    public static long extractionSimulateCalls;
    public static long extractionModulateCalls;
    public static long extractionRequestedItems;
    public static long extractionReturnedItems;
    public static long extractionZeroResults;
    public static long extractionPartialResults;
    public static long extractionFullResults;
    public static long extractionPlayerSourceCalls;
    public static long extractionMachineSourceCalls;
    public static long extractionUnattributedSourceCalls;
    public static long extractionCallsInsidePoweredExtraction;
    public static long extractionCallsOutsidePoweredExtraction;
    public static long poweredExtractionCalls;
    public static long poweredExtractionSimulateModeCalls;
    public static long poweredExtractionModulateModeCalls;
    public static long extractionPoweredContextPairs;
    public static long extractionHeuristicPairs;
    public static long extractionContextLeaksReset;
    public static long extractionRepeatedSimulations;
    public static long extractionSupersededSimulations;
    public static long extractionExpiredSimulations;
    public static long extractionModulatesWithoutMatchingSimulation;
    public static long extractionPairsMatchSimResult;
    public static long extractionPairsMatchSimRequest;
    public static long extractionPairsSameTypeDifferentAmount;

    public static long cellUpdatesWithEvent;
    public static long cellUpdatesWithoutEvent;
    public static long forceUpdatesRequestedDuringCellUpdate;
    public static long forceUpdatesRequestedElsewhere;
    public static long forceUpdatesExecuted;
    public static long forceUpdateNanos;

    private static final Map<String, long[]> PER_CHANNEL = new TreeMap<String, long[]>();

    private static int cellUpdateDepth;
    private static long forceUpdateStartedAt;

    private Diagnostics() {
    }

    public static void networkRequestCompleted(int kind, int probes, int zeroProbes,
                                               boolean nested) {
        if (kind < 0 || kind >= KINDS) {
            return;
        }
        if (!loggedNetworkFanOut) {
            loggedNetworkFanOut = true;
            MixinStatus.Feature.NETWORK_FAN_OUT_BRACKET.markRuntimeHit();
            RUNTIME.info("ACTIVE: network fan-out request bracket executed.");
        }
        networkRequests[kind]++;
        networkProbes[kind] += probes;
        networkZeroProbes[kind] += zeroProbes;
        if (probes > networkMaxProbes[kind]) {
            networkMaxProbes[kind] = probes;
        }
        networkProbeBuckets[kind][bucketFor(probes)]++;
        if (nested) {
            networkNestedRequests++;
        }
    }


    public static void fanOutProbeObserved() {
        if (!loggedFanOutProbe) {
            loggedFanOutProbe = true;
            MixinStatus.Feature.NETWORK_FAN_OUT_PROBE.markRuntimeHit();
            RUNTIME.info("ACTIVE: fan-out drawer-probe observer executed. Probes are being counted.");
        }
    }

    public static void negativeConsidered() {
        negativeConsidered++;
    }

    public static void negativeServed() {
        negativeServed++;
        if (!loggedNegativeServed) {
            loggedNegativeServed = true;
            MixinStatus.Feature.NEGATIVE_FAST_PATH.markRuntimeHit();
            MixinStatus.Feature.NEGATIVE_INDEX.markRuntimeHit();
            RUNTIME.info("ACTIVE: first Storage Drawers negative extraction answered from the "
                    + "presence index instead of a full drawer scan.");
        }
    }

    public static void negativePredicateFallback() {
        negativePredicateFallbacks++;
    }


    public static void conversionProbed() {
        MixinStatus.Feature.NEGATIVE_ATTRS.markRuntimeHit();
    }

    public static void negativeHolderMissing() {
        if (negativeHolderMissing++ == 0) {
            LOG.warn("A drawer controller is missing the presence index. The fast path is off for "
                    + "it and stock extraction runs; this means MixinTileEntityController did not "
                    + "apply to that class.");
        }
    }


    public static void presenceEpochBumped(int kind) {
        presenceEpochBumps++;
        if (kind >= 0 && kind < EPOCH_BUMPS.length) {
            EPOCH_BUMPS[kind]++;
        }
        switch (kind) {
            case EPOCH_STANDARD:
                MixinStatus.Feature.NEGATIVE_EPOCH_STANDARD.markRuntimeHit();
                break;
            case EPOCH_COMPACTING:
                MixinStatus.Feature.NEGATIVE_EPOCH_COMPACTING.markRuntimeHit();
                break;
            case EPOCH_ATTRIBUTES:
                MixinStatus.Feature.NEGATIVE_EPOCH_ATTRIBUTES.markRuntimeHit();
                break;
            default:
                break;
        }
    }


    public static void presenceUnknownDrawer() {
        presenceUnknownDrawerRefusals++;
    }

    public static void presenceIndexComposition(boolean usable, int fractional, int dictConvertible,
                                                int oreKeys) {
        if (!usable) {
            return;
        }
        presenceFractionalIndexed += fractional;
        presenceDictConvertibleIndexed += dictConvertible;
        presenceOreKeysAdded += oreKeys;
        if (fractional > 0) {
            presenceRebuildsWithFractional++;
        }
    }

    public static void oreExpansionFailed(Throwable t) {
        if (oreExpansionFailures++ == 0) {
            LOG.warn("Ore dictionary expansion failed; that controller falls back to stock.", t);
        }
    }


    public static boolean shouldSampleNegativeCandidate() {
        candidateEligible++;
        return (CANDIDATE_SEQUENCE.incrementAndGet() % CANDIDATE_SAMPLE_INTERVAL) == 0L;
    }

    public static void candidateSampled(CandidateSlotSampler.Sample sample) {
        if (sample.errored) {
            candidateSamplesErrored++;
            return;
        }
        if (sample.refused) {
            candidateSamplesRefused++;
            return;
        }
        candidateSamples++;
        candidateSlotsTotal += sample.candidates;
        candidateFullLengthTotal += sample.fullLength;
        CANDIDATE_BUCKETS[CandidateSlotSampler.bucket(sample.candidates)]++;
        FULL_LENGTH_BUCKETS[CandidateSlotSampler.bucket(sample.fullLength)]++;
        final int ratio = CandidateSlotSampler.ratioBucket(sample.candidates, sample.fullLength);
        if (ratio >= 0) {
            RATIO_BUCKETS[ratio]++;
        }
        if (sample.candidates == 0) {


            candidateZeroResults++;
        } else if (sample.anyLiteral) {
            candidateLiteralSamples++;
        } else {
            candidateOreOnlySamples++;
        }
    }

    public static void phase2MatcherSampleAttempted() {
        phase2MatcherSamplesAttempted++;
    }

    public static void phase2CandidateModelRefused() {
        phase2CandidateModelRefused++;
    }

    public static void phase2CandidateModelErrored() {
        phase2CandidateModelErrored++;
    }

    public static void phase2ContextEntered() {
        if (!loggedPhase2MatcherSample) {
            loggedPhase2MatcherSample = true;
            MixinStatus.Feature.NEGATIVE_PHASE2_MATCHERS.markRuntimeHit();
            RUNTIME.info("ACTIVE: first phase-2 matcher-call sample entered for a key-present "
                    + "negative extraction fallback with a valid candidate model.");
        }
    }

    public static void phase2ContextNested() {
        phase2MatcherSamplesNested++;
    }

    public static void phase2MatcherCallClassifyError() {
        phase2MatcherCallClassifyErrors++;
    }

    public static void phase2ModelContradiction() {
        if (phase2ModelContradictions++ == 0) {
            LOG.warn("Phase-2 matcher model contradiction: a stock testPredicateExtract call matched "
                    + "a drawer the conservative candidate model did not include. This does not change "
                    + "gameplay; it means candidate-slot narrowing would need a wider model before it "
                    + "could be used for real extraction.");
        }
    }

    public static void phase2MatcherSampleCompleted(int matcherCalls, int candidateCalls,
                                                     int nonCandidateCalls, int unclassifiedCalls) {
        phase2MatcherSamplesCompleted++;
        MixinStatus.Feature.NEGATIVE_PHASE2_EXIT.markRuntimeHit();
        phase2MatcherCallsTotal += matcherCalls;
        phase2CandidateMatcherCallsTotal += candidateCalls;
        phase2NonCandidateMatcherCallsTotal += nonCandidateCalls;
        phase2UnclassifiedMatcherCallsTotal += unclassifiedCalls;
        PHASE2_MATCHER_CALL_BUCKETS[CandidateSlotSampler.bucket(matcherCalls)]++;
        PHASE2_CANDIDATE_CALL_BUCKETS[CandidateSlotSampler.bucket(candidateCalls,
                CandidateSlotSampler.PHASE2_MATCHER_CANDIDATE_BUCKETS)]++;
        if (matcherCalls == 0) {
            phase2ZeroMatcherCallSamples++;
            return;
        }
        if (unclassifiedCalls > 0) {
            phase2SamplesWithUnclassifiedCalls++;
            return;
        }
        final int ratio = CandidateSlotSampler.ratioBucket(candidateCalls, matcherCalls);
        if (ratio >= 0) {
            PHASE2_RETAINED_FRACTION_BUCKETS[ratio]++;
        }
    }

    public static void candidateIndexBuilt(int keys, long slotReferences) {
        candidateIndexBuilds++;
        candidateKeysBuiltTotal += keys;
        candidateSlotReferencesBuiltTotal += slotReferences;
    }

    public static void candidateIndexBuildFailed() {
        candidateIndexBuildFailures++;
    }

    public static void candidateIndexKeyCapRefused() {
        candidateIndexKeyCapRefusals++;
    }

    public static void candidateIndexSlotRefCapRefused() {
        candidateIndexSlotRefCapRefusals++;
    }

    public static void candidateNarrowingEligible() {
        candidateNarrowingEligible++;
    }

    public static void candidateNarrowingInvariantFallback() {
        candidateNarrowingInvariantFallbacks++;
    }

    public static void candidateNarrowingStockFallback() {
        candidateNarrowingStockFallback++;
    }

    public static void candidateNarrowingServed(int candidateSlots, int fullSlots) {
        candidateNarrowingServed++;
        candidateSlotsReturnedTotal += candidateSlots;
        candidateFullSlotsTotal += fullSlots;
        if (!loggedCandidateNarrowingServed) {
            loggedCandidateNarrowingServed = true;
            RUNTIME.info("ACTIVE: first key-present negative extraction served from the precomputed "
                    + "candidate-slot index instead of the full phase-2 array.");
        }
    }

    public static boolean shouldSampleCandidateVerification() {
        candidateVerificationEligible++;
        return (CANDIDATE_VERIFICATION_SEQUENCE.incrementAndGet()
                % CANDIDATE_VERIFICATION_SAMPLE_INTERVAL) == 0L;
    }

    public static void candidateVerificationVerified() {
        candidateVerificationAttempted++;
        candidateVerificationVerified++;
    }

    public static void candidateVerificationMismatch() {
        candidateVerificationAttempted++;
        if (candidateVerificationMismatches++ == 0) {
            LOG.warn("Candidate index verification mismatch: the precomputed candidate array "
                    + "disagreed with a fresh structural recomputation for a sampled request. "
                    + "Falling back to the full stock phase-2 array for that request; gameplay is "
                    + "unaffected. The index should be investigated before trusting candidate "
                    + "narrowing further.");
        }
    }

    public static void candidateVerificationRefused() {
        candidateVerificationAttempted++;
        candidateVerificationRefused++;
    }

    public static void candidateVerificationErrored() {
        candidateVerificationAttempted++;
        candidateVerificationErrored++;
    }

    public static void negativeUnindexableFallback() {
        negativeUnindexableFallbacks++;
    }


    public static void negativeKeyPresentFallback() {
        negativeKeyPresentFallbacks++;
    }

    public static void presenceIndexRebuiltMarker() {
        MixinStatus.Feature.NEGATIVE_INDEX.markRuntimeHit();
    }

    public static void presenceIndexRebuilt(boolean topology, boolean usable, int keys) {
        if (topology) {
            presenceRebuildsTopology++;
        } else {
            presenceRebuildsContent++;
        }
        if (!usable) {
            presenceRebuildsUnusable++;
        }
        presenceKeysIndexed += keys;
    }

    public static void probeOutsideNetworkRequest() {
        probesOutsideNetworkRequest++;
    }

    public static void networkContextReset() {
        networkContextResets++;
    }

    private static int bucketFor(int probes) {
        for (int i = BUCKETS.length - 1; i >= 0; i--) {
            if (probes >= BUCKETS[i]) {
                return i;
            }
        }
        return 0;
    }


    public static void drawerOptimizedPathHit() {
        if (!loggedOptimizedPath) {
            loggedOptimizedPath = true;
            MixinStatus.Feature.ITEM_REPOSITORY_CACHE.markRuntimeHit();
            RUNTIME.info("ACTIVE: optimized Storage Drawers InventoryCache path executed. "
                    + "Counter store {}.", STORE_ID);
            RUNTIME.info("Conversion cache is now servicing AE2 storage bus polling.");
        }
    }

    public static void pollSkipped() {
        pollsSkipped++;
        if (!loggedSkip) {
            loggedSkip = true;
            MixinStatus.Feature.ITEM_LIST_VERSION.markRuntimeHit();
            RUNTIME.info("VERIFIED: first unchanged Storage Drawers poll skipped.");
        }
    }

    public static void pollRebuilt(int changes) {
        pollsRebuilt++;
        changesPosted += changes;
    }

    public static void directDiffPollUsed() {
        directDiffPolls++;
        if (directDiffPolls == 1) {
            RUNTIME.info("ACTIVE: first InventoryCache poll diffed via findPrecise lookups instead of "
                    + "the negate/merge/discard cycle.");
        }
    }

    public static void pollFailed(Throwable t) {
        if (pollFailures++ == 0) {
            LOG.warn("IItemRepository.getAllItems() failed; leaving this storage bus on AE2's own path", t);
        }
    }

    public static void itemHandlerExtractionObserved() {
        itemHandlerExtractionsObserved++;
        if (!loggedItemHandlerExtraction) {
            loggedItemHandlerExtraction = true;
            RUNTIME.info("ACTIVE: first generic ItemHandlerAdapter.extractItems call observed for "
                    + "the item-handler extraction diagnostic.");
        }
    }

    public static void templateHit() {
        templateHits++;
        if (!loggedTemplateHit) {
            loggedTemplateHit = true;
            RUNTIME.info("VERIFIED: first Storage Drawers prototype conversion-cache hit.");
        }
    }

    public static void templateMiss() {
        templateMisses++;
    }

    public static void templateCachePruned(int removed) {
        templateCachePrunes++;
        templateEntriesPruned += removed;
    }

    public static void emptyPrototypeSkipped() {
        if (emptyPrototypesSkipped++ == 0) {
            LOG.warn("A Storage Drawers repository reported an empty item prototype. AE2's own code "
                    + "would have thrown on this record; it is being skipped instead. Worth "
                    + "investigating if the count climbs.");
        }
    }

    public static void templateCacheDisabled(long hits, long lookups) {
        templateCachesDisabled++;
        LOG.info("Disabled the drawer conversion cache for one storage bus: {} hits in {} lookups "
                + "after warmup. Its repository is not returning stable prototype instances.",
                hits, lookups);
    }

    public static void templateCacheConfirmed(long hits, long lookups) {
        long confirmed = ++templateCachesConfirmed;
        if (confirmed <= 5) {
            RUNTIME.info("VERIFIED: prototype identities are stable on this repository ({} hits in {} "
                    + "lookups after warmup). No further stability sampling on this storage bus. "
                    + "Buses confirmed stable so far: {}.", hits, lookups, confirmed);
        }
    }


    public static void fallbackConversion() {
        fallbackConversions++;
    }

    public static void templateCacheOversized(int live, int limit) {
        templateCachesOversized++;
        LOG.warn("Disabled the drawer conversion cache for one storage bus: the repository exposes "
                + "{} live prototypes and maxLivePrototypesPerBus is {}. Raise it if this network "
                + "is legitimate.", live, limit);
    }


    public static void serverTick() {
        if (OptimizationConfig.instrumentExtractionTransactions) {
            int leakedDepth = ExtractionContext.resetAtServerTickEnd();
            if (leakedDepth > 0) {
                extractionContextLeaksReset += leakedDepth;
            }
        }
        serverTicks++;
        if (OptimizationConfig.instrumentNetworkFanOut) {
            NetworkRequestContext.resetForTickEnd();
        }
        if (OptimizationConfig.instrumentNegativePhase2Matchers) {
            int leaked = Phase2MatcherContext.resetAtServerTickEnd();
            if (leaked > 0) {
                phase2MatcherSamplesLeaked += leaked;
            }
        }
    }

    public static long serverTickCounter() {
        return serverTicks;
    }


    public static void poweredExtractionEntered(boolean simulateMode) {
        poweredExtractionCalls++;
        if (simulateMode) {
            poweredExtractionSimulateModeCalls++;
        } else {
            poweredExtractionModulateModeCalls++;
        }
        if (!loggedPoweredExtractionContext) {
            loggedPoweredExtractionContext = true;
            RUNTIME.info("ACTIVE: Platform.poweredExtraction transaction-scope diagnostics are observing calls.");
        }
    }


    public static void extractionDiagnosticsHit() {
        if (!loggedExtractionDiagnostics) {
            loggedExtractionDiagnostics = true;
            RUNTIME.info("ACTIVE: ItemRepositoryAdapter extraction diagnostics are observing real transactions.");
        }
    }

    public static void extractionCall(boolean simulate, long requested, long returned,
                                      boolean playerSource, boolean machineSource,
                                      boolean insidePoweredExtraction) {
        extractionCalls++;
        if (simulate) {
            extractionSimulateCalls++;
        } else {
            extractionModulateCalls++;
        }
        extractionRequestedItems += requested;
        extractionReturnedItems += returned;

        if (returned <= 0L) {
            extractionZeroResults++;
        } else if (returned >= requested && requested > 0L) {
            extractionFullResults++;
        } else {
            extractionPartialResults++;
        }

        if (playerSource) {
            extractionPlayerSourceCalls++;
        } else if (machineSource) {
            extractionMachineSourceCalls++;
        } else {
            extractionUnattributedSourceCalls++;
        }

        if (insidePoweredExtraction) {
            extractionCallsInsidePoweredExtraction++;
        } else {
            extractionCallsOutsidePoweredExtraction++;
        }
    }

    public static void extractionRepeatedSimulation() {
        extractionRepeatedSimulations++;
    }

    public static void extractionSimulationSuperseded() {
        extractionSupersededSimulations++;
    }

    public static void extractionSimulationExpired() {
        extractionExpiredSimulations++;
    }

    public static void extractionModulatePair(int pair) {
        if (pair == ExtractionPairTracker.MOD_NO_MATCH) {
            extractionModulatesWithoutMatchingSimulation++;
            return;
        }

        if (ExtractionPairTracker.isPoweredPair(pair)) {
            extractionPoweredContextPairs++;
        } else {
            extractionHeuristicPairs++;
        }

        switch (ExtractionPairTracker.amountRelation(pair)) {
            case ExtractionPairTracker.MOD_MATCHES_SIM_RESULT:
                extractionPairsMatchSimResult++;
                break;
            case ExtractionPairTracker.MOD_MATCHES_SIM_REQUEST:
                extractionPairsMatchSimRequest++;
                break;
            case ExtractionPairTracker.MOD_MATCHES_TYPE_DIFFERENT_AMOUNT:
                extractionPairsSameTypeDifferentAmount++;
                break;
            default:
                extractionModulatesWithoutMatchingSimulation++;
                break;
        }
    }

    public static void cellUpdateEnter(boolean hasEvent) {
        cellUpdateDepth++;
        if (hasEvent) {
            cellUpdatesWithEvent++;
        } else {
            cellUpdatesWithoutEvent++;
        }
    }

    public static void cellUpdateExit() {
        if (cellUpdateDepth > 0) {
            cellUpdateDepth--;
        }
    }

    public static void forceUpdateRequested(String channel) {
        if (cellUpdateDepth > 0) {
            forceUpdatesRequestedDuringCellUpdate++;
            channel(channel)[0]++;
        } else {
            forceUpdatesRequestedElsewhere++;
            channel(channel)[1]++;
        }
    }

    public static void forceUpdateEnter() {
        forceUpdateStartedAt = System.nanoTime();
    }

    public static void forceUpdateExit(String channel) {
        long elapsed = System.nanoTime() - forceUpdateStartedAt;
        forceUpdatesExecuted++;
        forceUpdateNanos += elapsed;
        long[] counters = channel(channel);
        counters[2]++;
        counters[3] += elapsed;
    }

    private static long[] channel(String name) {
        long[] counters = PER_CHANNEL.get(name);
        if (counters == null) {
            counters = new long[4];
            PER_CHANNEL.put(name, counters);
        }
        return counters;
    }

    public static List<String> summaryLines() {
        long polls = pollsSkipped + pollsRebuilt;
        long lookups = templateHits + templateMisses;
        List<String> lines = new ArrayList<String>();
        lines.add(String.format("drawer polls      : %d total, %d rebuilt, %d skipped (%s), %d change entries posted",
                polls, pollsRebuilt, pollsSkipped, percent(pollsSkipped, polls), changesPosted));
        if (OptimizationConfig.optimizeDrawerInventoryDiff) {
            lines.add(String.format("direct diff       : %d of %d rebuilt polls used findPrecise diffing (%s); "
                    + "the rest fell back to the negate/merge cycle pending confirmed identity stability",
                    directDiffPolls, pollsRebuilt, percent(directDiffPolls, pollsRebuilt)));
        }
        lines.add(String.format("conversion cache  : %d hits, %d misses (%s hit rate)",
                templateHits, templateMisses, percent(templateHits, lookups)));
        lines.add(String.format("cache stability   : %d buses confirmed stable, %d disabled for unstable "
                + "identities, %d disabled for size",
                templateCachesConfirmed, templateCachesDisabled, templateCachesOversized));
        lines.add(String.format("cache upkeep      : %d prunes dropping %d stale entries",
                templateCachePrunes, templateEntriesPruned));
        lines.add(String.format("cache policy      : stability sampling %s, max %d live prototypes / bus",
                OptimizationConfig.autoDisableTemplateCacheOnLowHitRate ? "ON" : "BYPASSED",
                OptimizationConfig.maxLivePrototypesPerBus));
        if (OptimizationConfig.instrumentExtractionTransactions) {
            long paired = extractionPairsMatchSimResult
                    + extractionPairsMatchSimRequest
                    + extractionPairsSameTypeDifferentAmount;
            lines.add(String.format("extraction calls  : %d total, %d SIMULATE (%s), %d MODULATE (%s)",
                    extractionCalls, extractionSimulateCalls, percent(extractionSimulateCalls, extractionCalls),
                    extractionModulateCalls, percent(extractionModulateCalls, extractionCalls)));
            lines.add(String.format("extraction result : %d full, %d partial, %d zero; %,d requested / %,d returned items",
                    extractionFullResults, extractionPartialResults, extractionZeroResults,
                    extractionRequestedItems, extractionReturnedItems));
            lines.add(String.format("powered extract   : %d calls, %d outer SIMULATE, %d outer MODULATE",
                    poweredExtractionCalls, poweredExtractionSimulateModeCalls,
                    poweredExtractionModulateModeCalls));
            lines.add(String.format("adapter origin    : %d inside poweredExtraction (%s), %d outside",
                    extractionCallsInsidePoweredExtraction,
                    percent(extractionCallsInsidePoweredExtraction, extractionCalls),
                    extractionCallsOutsidePoweredExtraction));
            lines.add(String.format("SIM->MOD pairs    : %d paired (%s of MODULATE), %d no matching SIM",
                    paired, percent(paired, extractionModulateCalls),
                    extractionModulatesWithoutMatchingSimulation));
            lines.add(String.format("  pair confidence : %d same poweredExtraction invocation, %d outside-context heuristic",
                    extractionPoweredContextPairs, extractionHeuristicPairs));
            lines.add(String.format("  amount relation : %d match SIM result, %d match SIM request, %d same type / adjusted amount",
                    extractionPairsMatchSimResult, extractionPairsMatchSimRequest,
                    extractionPairsSameTypeDifferentAmount));
            lines.add(String.format("SIM observations  : %d repeated same request, %d superseded, %d expired next tick",
                    extractionRepeatedSimulations, extractionSupersededSimulations,
                    extractionExpiredSimulations));
            lines.add(String.format("action sources    : %d player, %d machine, %d unattributed",
                    extractionPlayerSourceCalls, extractionMachineSourceCalls,
                    extractionUnattributedSourceCalls));
            if (extractionContextLeaksReset > 0) {
                lines.add("context resets    : " + extractionContextLeaksReset
                        + " leaked poweredExtraction scope(s) cleared at tick end");
            }
        }
        if (fallbackConversions > 0) {
            lines.add("uncached conversions: " + fallbackConversions);
        }
        if (pollFailures > 0) {
            lines.add("poll failures     : " + pollFailures);
        }
        if (emptyPrototypesSkipped > 0) {
            lines.add("empty prototypes  : " + emptyPrototypesSkipped);
        }
        if (OptimizationConfig.instrumentNetworkMonitor) {
            lines.add(String.format("cellUpdate        : %d from an event, %d direct",
                    cellUpdatesWithEvent, cellUpdatesWithoutEvent));
            lines.add(String.format("forceUpdate asked : %d inside cellUpdate, %d elsewhere (nesting transitions)",
                    forceUpdatesRequestedDuringCellUpdate, forceUpdatesRequestedElsewhere));
            lines.add(String.format("forceUpdate ran   : %d times, %d ms total, %d us mean",
                    forceUpdatesExecuted, forceUpdateNanos / 1_000_000L,
                    forceUpdatesExecuted == 0 ? 0 : forceUpdateNanos / forceUpdatesExecuted / 1000L));
            for (Map.Entry<String, long[]> entry : PER_CHANNEL.entrySet()) {
                long[] c = entry.getValue();
                lines.add(String.format("  %s : asked %d+%d, ran %d, %d ms",
                        entry.getKey(), c[0], c[1], c[2], c[3] / 1_000_000L));
            }
        }
        if (OptimizationConfig.instrumentNetworkFanOut) {
            lines.addAll(fanOutLines());
        }
        if (OptimizationConfig.optimizeDrawerNegativeExtraction) {
            lines.add(String.format("negative fast path: %d eligible, %d served (%s)",
                    negativeConsidered, negativeServed, percent(negativeServed, negativeConsidered)));
            lines.add(String.format("  stock fallbacks : %d key present, %d network unindexable, "
                    + "%d predicate, %d no index",
                    negativeKeyPresentFallbacks, negativeUnindexableFallbacks,
                    negativePredicateFallbacks, negativeHolderMissing));
            lines.add(String.format("presence index    : %d rebuilds (%d content, %d topology, "
                    + "%d unusable), %d keys indexed",
                    presenceRebuildsContent + presenceRebuildsTopology, presenceRebuildsContent,
                    presenceRebuildsTopology, presenceRebuildsUnusable, presenceKeysIndexed));
            lines.add(String.format("index composition : %d fractional drawers, %d dict-convertible, "
                    + "%d ore keys added, %d rebuilds included a compacting drawer",
                    presenceFractionalIndexed, presenceDictConvertibleIndexed, presenceOreKeysAdded,
                    presenceRebuildsWithFractional));
            lines.add(String.format("unindexable cause : %d unknown drawer implementations, "
                    + "%d ore expansion failures",
                    presenceUnknownDrawerRefusals, oreExpansionFailures));
            if (OptimizationConfig.optimizeDrawerCandidateNarrowing) {
                lines.addAll(candidateNarrowingLines());
            }
            if (OptimizationConfig.instrumentCandidateIndexVerification) {
                lines.addAll(candidateVerificationLines());
            }
            if (OptimizationConfig.instrumentNegativeCandidateSlots) {
                lines.addAll(candidateLines());
            }
            if (OptimizationConfig.instrumentNegativePhase2Matchers) {
                lines.addAll(phase2MatcherLines());
                if (OptimizationConfig.optimizeDrawerCandidateNarrowing) {
                    lines.add("  note            : candidate narrowing is ON, so this measures the "
                            + "narrowed runtime path, not the original v0.4.5 stock baseline");
                }
            }
            lines.add(String.format("epoch bumps       : %d total (%d standard, %d compacting, "
                    + "%d matcher)",
                    presenceEpochBumps, EPOCH_BUMPS[EPOCH_STANDARD],
                    EPOCH_BUMPS[EPOCH_COMPACTING], EPOCH_BUMPS[EPOCH_ATTRIBUTES]));
        }
        if (OptimizationConfig.instrumentItemHandlerExtraction) {
            lines.addAll(itemHandlerExtractionLines());
        }
        lines.add("counter store     : " + STORE_ID);
        return lines;
    }

    private static List<String> itemHandlerExtractionLines() {
        List<String> lines = new ArrayList<String>();
        lines.add("-- ItemHandlerAdapter.extractItems (generic IItemHandler storage buses) --");
        List<ItemHandlerExtractionStats.HandlerStats> ranked = ItemHandlerExtractionStats.snapshot();
        Collections.sort(ranked, new Comparator<ItemHandlerExtractionStats.HandlerStats>() {
            @Override
            public int compare(ItemHandlerExtractionStats.HandlerStats a, ItemHandlerExtractionStats.HandlerStats b) {
                return Long.compare(b.slotsExamined, a.slotsExamined);
            }
        });
        long totalRequests = 0;
        long totalSlots = 0;
        long totalSuccessful = 0;
        for (ItemHandlerExtractionStats.HandlerStats stats : ranked) {
            totalRequests += stats.requests;
            totalSlots += stats.slotsExamined;
            totalSuccessful += stats.successfulRequests;
        }
        lines.add(String.format("observed calls    : %d total, %d successful (%s)",
                totalRequests, totalSuccessful, percent(totalSuccessful, totalRequests)));
        lines.add(String.format("tracked classes   : %d of max %d%s",
                ItemHandlerExtractionStats.trackedClassCount(), OptimizationConfig.maxItemHandlerClassesTracked,
                ItemHandlerExtractionStats.isOverflowing()
                        ? String.format(" (cap reached; %d request(s) / %d slot(s) uncounted beyond the cap)",
                                ItemHandlerExtractionStats.overflowRequests(),
                                ItemHandlerExtractionStats.overflowSlotsExamined())
                        : ""));
        int rank = 0;
        for (ItemHandlerExtractionStats.HandlerStats stats : ranked) {
            if (++rank > 10) {
                lines.add(String.format("  ... %d more tracked handler class(es), see full snapshot if needed",
                        ranked.size() - 10));
                break;
            }
            double avgSlots = stats.requests == 0 ? 0.0 : (double) stats.slotsExamined / stats.requests;
            lines.add(String.format("  %d. %s%s", rank, stats.handlerClassName,
                    stats.ownerClassName == null ? "" : " (owner: " + stats.ownerClassName + ")"));
            lines.add(String.format("     %d requests, %d slots examined (%.2f avg/request), "
                    + "%d successful / %d unsuccessful (%s)",
                    stats.requests, stats.slotsExamined, avgSlots,
                    stats.successfulRequests, stats.unsuccessfulRequests,
                    percent(stats.successfulRequests, stats.requests)));
            if (stats.requestedAmount > 0) {
                lines.add(String.format("     requested %,d / extracted %,d items",
                        stats.requestedAmount, stats.extractedAmount));
            }
        }
        lines.add(String.format("total slot scans  : %d across all tracked handler classes", totalSlots));
        return lines;
    }

    private static List<String> fanOutLines() {
        List<String> lines = new ArrayList<String>();
        lines.add("-- network fan-out (drawer probes per AE2 network request) --");
        for (int kind = 0; kind < KINDS; kind++) {
            long requests = networkRequests[kind];
            long probes = networkProbes[kind];
            lines.add(String.format("%s       : %d requests, %d probes (%s per request, max %d), "
                    + "%d returned nothing (%s)",
                    KIND_NAMES[kind], requests, probes,
                    requests == 0 ? "n/a" : String.format("%.2f", (double) probes / requests),
                    networkMaxProbes[kind], networkZeroProbes[kind],
                    percent(networkZeroProbes[kind], probes)));
            if (requests > 0) {
                lines.add(String.format("%s spread: %s", KIND_NAMES[kind], histogram(kind)));
            }
        }
        long totalRequests = 0L;
        long totalProbes = 0L;
        for (int kind = 0; kind < KINDS; kind++) {
            totalRequests += networkRequests[kind];
            totalProbes += networkProbes[kind];
        }
        if (totalRequests > 0L && totalProbes == 0L) {
            lines.add("INCOMPLETE        : network requests were bracketed but no drawer probe was "
                    + "recorded. The probe");
            lines.add("                    observer is not installed - these counts are not a "
                    + "measurement of fan-out.");
        }
        lines.add(String.format("unattributed      : %d probes outside any network request",
                probesOutsideNetworkRequest));
        lines.add(String.format("context           : %d nested requests, %d tick-end resets",
                networkNestedRequests, networkContextResets));
        return lines;
    }


    private static String histogram(int kind) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < BUCKETS.length; i++) {
            if (networkProbeBuckets[kind][i] == 0) {
                continue;
            }
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(bucketLabel(i)).append(": ").append(networkProbeBuckets[kind][i]);
        }
        return out.length() == 0 ? "none" : out.toString();
    }

    private static String bucketLabel(int index) {
        if (index == BUCKETS.length - 1) {
            return BUCKETS[index] + "+";
        }
        int low = BUCKETS[index];
        int high = BUCKETS[index + 1] - 1;
        return low == high ? String.valueOf(low) : low + "-" + high;
    }

    private static List<String> candidateNarrowingLines() {
        final List<String> lines = new ArrayList<String>();
        lines.add("-- candidate narrowing production path --");
        lines.add(String.format("candidate index    : %d builds, %d keys, %d slot refs",
                candidateIndexBuilds, candidateKeysBuiltTotal, candidateSlotReferencesBuiltTotal));
        lines.add(String.format("candidate limits   : %d key-cap, %d slot-ref-cap, %d build errors",
                candidateIndexKeyCapRefusals, candidateIndexSlotRefCapRefusals, candidateIndexBuildFailures));
        lines.add(String.format("narrowing requests : %d eligible key-present, %d narrowed (%s), "
                + "%d stock fallback",
                candidateNarrowingEligible, candidateNarrowingServed,
                percent(candidateNarrowingServed, candidateNarrowingEligible),
                candidateNarrowingStockFallback));
        lines.add(String.format("narrowing slots    : %d candidate slots returned of %d original full "
                + "slots (%s retained) - structural reduction, not measured time saved",
                candidateSlotsReturnedTotal, candidateFullSlotsTotal,
                percent(candidateSlotsReturnedTotal, candidateFullSlotsTotal)));
        lines.add("invariant fallback : " + candidateNarrowingInvariantFallbacks);
        return lines;
    }

    private static List<String> candidateVerificationLines() {
        final List<String> lines = new ArrayList<String>();
        lines.add("-- candidate index verification --");
        lines.add(String.format("samples            : %d eligible (1 in %d), %d attempted, %d verified",
                candidateVerificationEligible, CANDIDATE_VERIFICATION_SAMPLE_INTERVAL,
                candidateVerificationAttempted, candidateVerificationVerified));
        lines.add(String.format("model invalid      : %d refused, %d errored",
                candidateVerificationRefused, candidateVerificationErrored));
        lines.add("index mismatch     : " + candidateVerificationMismatches);
        lines.add("stock fallback     : " + (candidateVerificationAttempted - candidateVerificationVerified)
                + " sampled request(s) forced to stock");
        return lines;
    }

    private static List<String> candidateLines() {
        final List<String> lines = new ArrayList<String>();
        lines.add("-- structural full-array bound (A), not actual matcher calls or time saved --");
        lines.add(String.format("candidate sampling: %d eligible, %d sampled (1 in %d), %d refused, "
                + "%d errored, %d key-present with zero candidates",
                candidateEligible, candidateSamples, CANDIDATE_SAMPLE_INTERVAL,
                candidateSamplesRefused, candidateSamplesErrored, candidateZeroResults));
        lines.add(String.format("  narrowing bound : %d candidate slots of %d full-array slots (%s)"
                + " - phase-2 array only, not slots scanned or time saved",
                candidateSlotsTotal, candidateFullLengthTotal,
                percent(candidateSlotsTotal, candidateFullLengthTotal)));
        lines.add(String.format("  candidate origin: %d had a literal prototype, %d were ore-equivalent only",
                candidateLiteralSamples, candidateOreOnlySamples));
        lines.add("  retained frac   : " + ratioHistogram());
        lines.add("  candidates      : " + histogram(CANDIDATE_BUCKETS));
        lines.add("  full array      : " + histogram(FULL_LENGTH_BUCKETS));
        return lines;
    }

    private static String ratioHistogram() {
        return ratioHistogram(RATIO_BUCKETS);
    }

    private static String ratioHistogram(long[] buckets) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < buckets.length; i++) {
            if (buckets[i] == 0) {
                continue;
            }
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(CandidateSlotSampler.ratioBucketLabel(i)).append(": ").append(buckets[i]);
        }
        return out.length() == 0 ? "none" : out.toString();
    }

    private static String histogram(long[] buckets) {
        return histogram(buckets, CandidateSlotSampler.BUCKETS);
    }

    private static String histogram(long[] buckets, int[] bucketDefs) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < buckets.length; i++) {
            if (buckets[i] == 0) {
                continue;
            }
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(CandidateSlotSampler.bucketLabel(i, bucketDefs)).append(": ").append(buckets[i]);
        }
        return out.length() == 0 ? "none" : out.toString();
    }

    private static List<String> phase2MatcherLines() {
        final List<String> lines = new ArrayList<String>();
        lines.add("-- phase-2 actual matcher-call bound (B), separate from the structural bound (A) "
                + "above and from time saved (C, unmeasured) --");
        lines.add(String.format("phase-2 samples   : %d attempted, %d completed, %d model invalid "
                + "(%d refused, %d errored), %d zero-call, %d leaked, %d nested",
                phase2MatcherSamplesAttempted, phase2MatcherSamplesCompleted,
                phase2CandidateModelRefused + phase2CandidateModelErrored,
                phase2CandidateModelRefused, phase2CandidateModelErrored,
                phase2ZeroMatcherCallSamples, phase2MatcherSamplesLeaked, phase2MatcherSamplesNested));
        lines.add(String.format("  matcher calls   : %d actual phase-2 calls, %d candidate-member (%s), "
                + "%d classified non-candidate, %d unclassified",
                phase2MatcherCallsTotal, phase2CandidateMatcherCallsTotal,
                percent(phase2CandidateMatcherCallsTotal, phase2MatcherCallsTotal),
                phase2NonCandidateMatcherCallsTotal, phase2UnclassifiedMatcherCallsTotal));
        lines.add(String.format("  classify errors : %d (%d sample(s) excluded from the retained-fraction "
                + "histogram below because of them)",
                phase2MatcherCallClassifyErrors, phase2SamplesWithUnclassifiedCalls));
        lines.add("  model contradict: " + phase2ModelContradictions
                + " (a stock match on a slot the candidate model excluded)");
        lines.add("  retained frac   : " + ratioHistogram(PHASE2_RETAINED_FRACTION_BUCKETS));
        lines.add("  matcher calls   : " + histogram(PHASE2_MATCHER_CALL_BUCKETS));
        lines.add("  candidate calls : " + histogram(PHASE2_CANDIDATE_CALL_BUCKETS,
                CandidateSlotSampler.PHASE2_MATCHER_CANDIDATE_BUCKETS));
        return lines;
    }

    private static String fingerprint() {
        ClassLoader loader = Diagnostics.class.getClassLoader();
        return Integer.toHexString(System.identityHashCode(Diagnostics.class))
                + "/" + (loader == null ? "boot"
                        : loader.getClass().getSimpleName() + "@"
                        + Integer.toHexString(System.identityHashCode(loader)));
    }

    public static void dump(String reason) {
        MixinStatus.log(LOG, "Diagnostics (" + reason + ")", summaryLines());
    }

    private static String percent(long part, long total) {
        return total == 0 ? "n/a" : String.format("%.2f%%", 100.0D * part / total);
    }
}
