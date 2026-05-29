package com.gridynamics.forge.guardrail.chain;

import com.gridynamics.forge.guardrail.GuardrailContext;
import com.gridynamics.forge.guardrail.config.GuardrailProperties;
import com.gridynamics.forge.guardrail.model.ValidationAction;
import com.gridynamics.forge.guardrail.model.Violation;
import com.gridynamics.forge.guardrail.model.ViolationSeverity;
import com.gridynamics.forge.guardrail.registry.CategorizedPattern;
import com.gridynamics.forge.guardrail.registry.GuardrailPatternRegistry;
import com.gridynamics.forge.guardrail.registry.PatternCategory;
import com.gridynamics.forge.guardrail.util.FuzzyMatchUtil;
import com.gridynamics.forge.guardrail.util.PatternMatchUtil;
import com.gridynamics.forge.guardrail.util.TextNormalizationUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects toxic, abusive, violent, or unsafe language with severity scoring.
 *
 * <p>Scoring bands (configurable via {@code forge.guardrail.toxicity}):
 * <ul>
 *   <li>0.0 – warn-threshold → SAFE (no violation)</li>
 *   <li>warn-threshold – hard-block-threshold → WARN</li>
 *   <li>hard-block-threshold+ → HARD block (or WARN when legacy hard-block=false)</li>
 * </ul>
 *
 * <p>Legacy {@code hard-block=true} forces HARD block on any match.
 */
public class ToxicityValidator implements GuardrailValidator {

    private static final List<String> FUZZY_PHRASES = List.of(
            "kill", "destroy", "hack", "bomb", "die", "hurt them badly", "steal passwords"
    );

    private final GuardrailProperties props;
    private final GuardrailPatternRegistry patternRegistry;

    public ToxicityValidator(GuardrailProperties props, GuardrailPatternRegistry patternRegistry) {
        this.props = props;
        this.patternRegistry = patternRegistry;
    }

    @Override
    public String name() {
        return "TOXICITY";
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    public boolean isEnabled() {
        return props.getToxicity().isEnabled();
    }

    @Override
    public List<Violation> validate(String text, GuardrailContext context) {
        String normalized = TextNormalizationUtil.normalizeAllWithLeetspeak(text);
        List<CategorizedPattern> patterns = patternRegistry.getPatterns(PatternCategory.TOXICITY);

        List<String> matchedText = new ArrayList<>();
        double toxicityScore = 0.0;
        double maxConfidence = 0.0;
        String primaryCategory = "toxicity";

        for (CategorizedPattern pattern : patterns) {
            List<String> matches = PatternMatchUtil.findMatches(pattern.pattern(), normalized);
            if (matches.isEmpty()) {
                continue;
            }
            matchedText.addAll(matches);
            toxicityScore = Math.min(1.0, toxicityScore + pattern.weight());
            maxConfidence = Math.max(maxConfidence, pattern.confidence());
            primaryCategory = pattern.subcategory();
        }

        if (props.getFuzzy().isEnabled()) {
            ToxicityFuzzyResult fuzzyResult = applyFuzzyMatching(normalized, matchedText, toxicityScore, maxConfidence);
            matchedText = fuzzyResult.matchedText();
            toxicityScore = fuzzyResult.score();
            maxConfidence = fuzzyResult.confidence();
            if (!fuzzyResult.category().isEmpty()) {
                primaryCategory = fuzzyResult.category();
            }
        }

        if (matchedText.isEmpty()) {
            return List.of();
        }

        ToxicityDecision decision = resolveDecision(toxicityScore);

        if (decision.severity() == null) {
            return List.of();
        }

        return List.of(Violation.of(
                decision.code(),
                decision.message(),
                decision.severity(),
                name(),
                List.copyOf(matchedText),
                toxicityScore,
                maxConfidence,
                primaryCategory,
                decision.action()
        ));
    }

    private ToxicityFuzzyResult applyFuzzyMatching(String normalized, List<String> matchedText,
                                                    double score, double confidence) {
        List<String> allMatches = new ArrayList<>(matchedText);
        double updatedScore = score;
        double updatedConfidence = confidence;
        String category = "";

        for (String phrase : FUZZY_PHRASES) {
            List<String> fuzzyHits = FuzzyMatchUtil.findFuzzyMatches(normalized, phrase);
            if (fuzzyHits.isEmpty()) {
                continue;
            }
            allMatches.addAll(fuzzyHits);
            updatedScore = Math.min(1.0, updatedScore + 0.35);
            updatedConfidence = Math.max(updatedConfidence, 0.75);
            category = inferCategory(phrase);
        }

        return new ToxicityFuzzyResult(allMatches, updatedScore, updatedConfidence, category);
    }

    private ToxicityDecision resolveDecision(double toxicityScore) {
        GuardrailProperties.ToxicityProperties toxicity = props.getToxicity();

        if (toxicity.isHardBlock()) {
            return new ToxicityDecision(
                    ViolationSeverity.HARD,
                    "TOXICITY_BLOCKED",
                    "Toxic content detected",
                    ValidationAction.BLOCK
            );
        }

        if (toxicityScore >= toxicity.getHardBlockThreshold()) {
            return new ToxicityDecision(
                    ViolationSeverity.HARD,
                    "TOXICITY_BLOCKED",
                    "Toxic content detected (score=" + String.format("%.2f", toxicityScore) + ")",
                    ValidationAction.BLOCK
            );
        }

        if (toxicityScore >= toxicity.getWarnThreshold()) {
            return new ToxicityDecision(
                    ViolationSeverity.WARN,
                    "TOXICITY_WARNING",
                    "Potentially toxic content detected (score=" + String.format("%.2f", toxicityScore) + ")",
                    ValidationAction.WARN
            );
        }

        return new ToxicityDecision(null, null, null, ValidationAction.ALLOW);
    }

    private static String inferCategory(String phrase) {
        if (phrase.contains("hack") || phrase.contains("steal")) {
            return "cyber_abuse";
        }
        if (phrase.contains("bomb")) {
            return "illegal_activities";
        }
        if (phrase.contains("die") || phrase.contains("kill") || phrase.contains("destroy")) {
            return "violence";
        }
        return "violence";
    }

    private record ToxicityDecision(
            ViolationSeverity severity,
            String code,
            String message,
            ValidationAction action
    ) {
    }

    private record ToxicityFuzzyResult(
            List<String> matchedText,
            double score,
            double confidence,
            String category
    ) {
    }
}
