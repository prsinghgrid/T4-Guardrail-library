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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects prompts containing prohibited bias terms using regex patterns
 * grouped by protected category (gender, religion, caste, etc.).
 *
 * <p>HARD-block — any match prevents the prompt from reaching the LLM.
 * Teams can extend via {@code forge.guardrail.bias.extra-patterns}
 * or legacy {@code extra-terms}.
 */
public class BiasValidator implements GuardrailValidator {

    private final GuardrailProperties props;
    private final GuardrailPatternRegistry patternRegistry;

    public BiasValidator(GuardrailProperties props, GuardrailPatternRegistry patternRegistry) {
        this.props = props;
        this.patternRegistry = patternRegistry;
    }

    @Override
    public String name() {
        return "BIAS";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public boolean isEnabled() {
        return props.getBias().isEnabled();
    }

    @Override
    public List<Violation> validate(String text, GuardrailContext context) {
        List<CategorizedPattern> biasPatterns = patternRegistry.getPatterns(PatternCategory.BIAS);

        // First pass: standard normalization (preserves digits — needed for age patterns like "under 30").
        String stdNormalized = TextNormalizationUtil.normalizeAll(text);
        List<PatternMatchUtil.PatternHit> hits = new ArrayList<>(
                PatternMatchUtil.matchCategorizedOnText(stdNormalized, biasPatterns));

        // Second pass: leetspeak-decoded normalization (catches obfuscated terms like "0nly b0ys").
        // We run only this pass and merge any NEW label hits not already captured above.
        String leetNormalized = TextNormalizationUtil.normalizeAllWithLeetspeak(text);
        if (!leetNormalized.equals(stdNormalized)) {
            Set<String> seenLabels = new LinkedHashSet<>();
            for (PatternMatchUtil.PatternHit h : hits) {
                seenLabels.add(h.definition().label());
            }
            for (PatternMatchUtil.PatternHit h : PatternMatchUtil.matchCategorizedOnText(leetNormalized, biasPatterns)) {
                if (seenLabels.add(h.definition().label())) {
                    hits.add(h);
                }
            }
        }

        if (props.getFuzzy().isEnabled()) {
            hits = mergeFuzzyHits(hits, stdNormalized);
        }

        if (hits.isEmpty()) {
            return List.of();
        }

        // Group hits by subcategory and emit one Violation per subcategory for
        // granular violation codes: BIAS_GENDER, BIAS_AGE, BIAS_EDUCATION, etc.
        java.util.Map<String, List<PatternMatchUtil.PatternHit>> bySubcategory = new java.util.LinkedHashMap<>();
        for (PatternMatchUtil.PatternHit hit : hits) {
            String sub = hit.definition().subcategory();
            bySubcategory.computeIfAbsent(sub, k -> new ArrayList<>()).add(hit);
        }

        List<Violation> violations = new ArrayList<>();
        for (java.util.Map.Entry<String, List<PatternMatchUtil.PatternHit>> entry : bySubcategory.entrySet()) {
            String subcategory = entry.getKey();
            List<PatternMatchUtil.PatternHit> subHits = entry.getValue();

            List<String> matchedText = new ArrayList<>();
            double maxScore = 0.0;
            double maxConfidence = 0.0;
            for (PatternMatchUtil.PatternHit hit : subHits) {
                matchedText.addAll(hit.matchedText());
                maxScore = Math.max(maxScore, hit.definition().weight());
                maxConfidence = Math.max(maxConfidence, hit.definition().confidence());
            }

            // Code format: BIAS_GENDER, BIAS_AGE, BIAS_EDUCATION, BIAS_CASTE, etc.
            String code = "BIAS_" + subcategory.toUpperCase().replace(' ', '_');
            violations.add(Violation.of(
                    code,
                    "Prompt contains prohibited " + subcategory + " bias terms: " + matchedText,
                    ViolationSeverity.HARD,
                    name(),
                    List.copyOf(matchedText),
                    maxScore,
                    maxConfidence,
                    subcategory,
                    ValidationAction.BLOCK
            ));
        }

        return Collections.unmodifiableList(violations);
    }

    private List<PatternMatchUtil.PatternHit> mergeFuzzyHits(
            List<PatternMatchUtil.PatternHit> regexHits, String text) {

        Set<String> alreadyMatched = new LinkedHashSet<>();
        for (PatternMatchUtil.PatternHit hit : regexHits) {
            alreadyMatched.addAll(hit.matchedText());
        }

        List<PatternMatchUtil.PatternHit> merged = new ArrayList<>(regexHits);
        for (String legacyTerm : props.getBias().getExtraTerms()) {
            if (legacyTerm == null || legacyTerm.isBlank()) {
                continue;
            }
            List<String> fuzzyMatches = FuzzyMatchUtil.findFuzzyMatches(text, legacyTerm);
            fuzzyMatches.removeIf(alreadyMatched::contains);
            if (fuzzyMatches.isEmpty()) {
                continue;
            }
            alreadyMatched.addAll(fuzzyMatches);
            merged.add(new PatternMatchUtil.PatternHit(
                    new com.gridynamics.forge.guardrail.registry.CategorizedPattern(
                            FuzzyMatchUtil.buildFuzzyPattern(legacyTerm),
                            legacyTerm.trim(),
                            PatternCategory.BIAS,
                            "custom",
                            0.90,
                            0.92
                    ),
                    fuzzyMatches
            ));
        }
        return merged;
    }
}
