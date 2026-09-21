package dj2.ae2opt.core;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.List;


public final class StatusDashboard {

    private static final String OK = "[OK] ";
    private static final String OFF = "[OFF] ";
    private static final String WAIT = "[WAIT] ";
    private static final String ERR = "[ERR] ";

    private StatusDashboard() {
    }

    public static List<ITextComponent> compactLines() {
        final List<ITextComponent> lines = new ArrayList<ITextComponent>();

        lines.add(heading("DJ2 AE2 Optimizations v" + Constants.VERSION));

        lines.add(heading("Drawer optimizations:"));
        lines.add(featureLine("Conversion cache", MixinStatus.Feature.ITEM_REPOSITORY_CACHE));
        lines.add(aggregateLine("Negative fast path", MixinStatus.Feature.NEGATIVE_FAST_PATH,
                MixinStatus.Feature.NEGATIVE_INDEX, MixinStatus.Feature.NEGATIVE_ATTRS,
                MixinStatus.Feature.NEGATIVE_EPOCH_STANDARD, MixinStatus.Feature.NEGATIVE_EPOCH_COMPACTING,
                MixinStatus.Feature.NEGATIVE_EPOCH_ATTRIBUTES, MixinStatus.Feature.NEGATIVE_FRACTIONAL));
        lines.add(flagLine("Candidate narrowing", OptimizationConfig.optimizeDrawerCandidateNarrowing,
                MixinStatus.Feature.NEGATIVE_FAST_PATH));

        lines.add(heading("External integrations:"));
        lines.add(compatibilityLine("Ender Utilities " + OptimizationConfig.expectedEnderUtilitiesVersion,
                CompatibilityCheck.checkEnderUtilities()));
        lines.add(compatibilityLine("Actually Additions " + OptimizationConfig.expectedActuallyAdditionsVersion,
                CompatibilityCheck.checkActuallyAdditions()));
        lines.add(featureLine("External handler negative fast path",
                MixinStatus.Feature.EXTERNAL_HANDLER_NEGATIVE_EXTRACTION));

        lines.add(heading("Production ratios:"));
        lines.add(ratioLine("Conversion cache hit rate",
                Diagnostics.templateHits, Diagnostics.templateHits + Diagnostics.templateMisses));
        if (OptimizationConfig.optimizeDrawerInventoryDiff) {
            lines.add(ratioLine("Direct-diff poll usage", Diagnostics.directDiffPolls, Diagnostics.pollsRebuilt));
        }
        if (OptimizationConfig.optimizeDrawerNegativeExtraction) {
            lines.add(ratioLine("Drawer negative fast path served",
                    Diagnostics.negativeServed, Diagnostics.negativeConsidered));
        }
        if (OptimizationConfig.optimizeDrawerCandidateNarrowing) {
            lines.add(ratioLine("Candidate narrowing served",
                    Diagnostics.candidateNarrowingServed, Diagnostics.candidateNarrowingEligible));
        }
        if (OptimizationConfig.optimizeExternalItemHandlerNegativeExtraction) {
            lines.add(ratioLine("External handler negative fast path served",
                    Diagnostics.externalHandlerNegativeServed, Diagnostics.externalHandlerNegativeConsidered));
        }

        final List<ITextComponent> health = healthLines();
        if (!health.isEmpty()) {
            lines.add(heading("Health:"));
            lines.addAll(health);
        }

        lines.add(secondary("Diagnostics enabled: " + enabledDiagnosticsSummary()));
        lines.add(secondary("Use /dj2ae2opt status full for the complete report."));
        return lines;
    }

    private static List<ITextComponent> healthLines() {
        final List<ITextComponent> lines = new ArrayList<ITextComponent>();
        if (Diagnostics.pollFailures > 0) {
            lines.add(colored(ERR + Diagnostics.pollFailures
                    + " drawer network poll failure(s), fell back to stock", TextFormatting.RED));
        }
        if (Diagnostics.presenceRebuildsUnusable > 0) {
            lines.add(colored(ERR + Diagnostics.presenceRebuildsUnusable
                    + " drawer presence-index rebuild(s) came out unusable, falls open to stock",
                    TextFormatting.RED));
        }
        if (Diagnostics.candidateIndexBuildFailures > 0) {
            lines.add(colored(ERR + Diagnostics.candidateIndexBuildFailures
                    + " candidate-index build failure(s), falls open to stock", TextFormatting.RED));
        }
        if (Diagnostics.externalHandlerPresenceBuildFailures > 0) {
            lines.add(colored(ERR + Diagnostics.externalHandlerPresenceBuildFailures
                    + " external handler presence-index build failure(s), falls open to stock",
                    TextFormatting.RED));
        }
        if (Diagnostics.candidateVerificationMismatches > 0) {
            lines.add(colored(ERR + Diagnostics.candidateVerificationMismatches
                    + " candidate-index verification mismatch(es)", TextFormatting.RED));
        }
        for (MixinStatus.Feature feature : MixinStatus.Feature.values()) {
            if (!feature.isDiagnostic() && feature.isRequested() && !feature.isApplied()) {
                lines.add(colored(ERR + feature.label + " requested but not applied", TextFormatting.RED));
            }
        }
        return lines;
    }

    private static String enabledDiagnosticsSummary() {
        final List<String> enabled = new ArrayList<String>();
        if (OptimizationConfig.instrumentExtractionTransactions) {
            enabled.add("extraction");
        }
        if (OptimizationConfig.instrumentNetworkMonitor) {
            enabled.add("network monitor");
        }
        if (OptimizationConfig.instrumentNetworkFanOut) {
            enabled.add("fan-out");
        }
        if (OptimizationConfig.instrumentNegativePhase2Matchers) {
            enabled.add("phase-2 matchers");
        }
        if (OptimizationConfig.instrumentItemHandlerExtraction) {
            enabled.add("item-handler extraction");
        }
        if (OptimizationConfig.instrumentNegativeCandidateSlots) {
            enabled.add("candidate slots");
        }
        if (OptimizationConfig.instrumentCandidateIndexVerification) {
            enabled.add("candidate index verification");
        }
        if (enabled.isEmpty()) {
            return "none";
        }
        final StringBuilder joined = new StringBuilder();
        for (int i = 0; i < enabled.size(); i++) {
            if (i > 0) {
                joined.append(", ");
            }
            joined.append(enabled.get(i));
        }
        return joined.toString();
    }

    private static ITextComponent featureLine(String label, MixinStatus.Feature feature) {
        if (!feature.isRequested()) {
            return colored(OFF + label, TextFormatting.GRAY);
        }
        if (feature.isApplied()) {
            return colored(OK + label, TextFormatting.GREEN);
        }
        return colored(ERR + label + " (requested but not applied)", TextFormatting.RED);
    }

    private static ITextComponent aggregateLine(String label, MixinStatus.Feature primary,
                                                 MixinStatus.Feature... supporting) {
        if (!primary.isRequested()) {
            return colored(OFF + label, TextFormatting.GRAY);
        }
        boolean anyMissing = !primary.isApplied();
        for (MixinStatus.Feature feature : supporting) {
            if (feature.isRequested() && !feature.isApplied()) {
                anyMissing = true;
            }
        }
        if (anyMissing) {
            return colored(ERR + label + " (requested but not fully applied)", TextFormatting.RED);
        }
        return colored(OK + label, TextFormatting.GREEN);
    }

    private static ITextComponent flagLine(String label, boolean enabled, MixinStatus.Feature backing) {
        if (!enabled) {
            return colored(OFF + label, TextFormatting.GRAY);
        }
        if (backing.isRequested() && !backing.isApplied()) {
            return colored(ERR + label + " (backing mixin not applied)", TextFormatting.RED);
        }
        return colored(OK + label, TextFormatting.GREEN);
    }

    private static ITextComponent compatibilityLine(String label, CompatibilityCheck.Support support) {
        switch (support) {
            case SUPPORTED:
                return colored(OK + label, TextFormatting.GREEN);
            case UNSUPPORTED:
                return colored(WAIT + label + " (version mismatch, falls open to stock)", TextFormatting.YELLOW);
            default:
                return colored(OFF + label + " (not installed)", TextFormatting.GRAY);
        }
    }

    private static ITextComponent ratioLine(String label, long served, long eligible) {
        final String percent = eligible == 0 ? "n/a" : String.format("%.1f%%", 100.0 * served / eligible);
        return colored(label + ": " + percent + " (" + served + "/" + eligible + ")", TextFormatting.GREEN);
    }

    private static ITextComponent heading(String text) {
        return colored(text, TextFormatting.AQUA);
    }

    private static ITextComponent secondary(String text) {
        return colored(text, TextFormatting.GRAY);
    }

    private static ITextComponent colored(String text, TextFormatting color) {
        final TextComponentString component = new TextComponentString(text);
        component.getStyle().setColor(color);
        return component;
    }
}
