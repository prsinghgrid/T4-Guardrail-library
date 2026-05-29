package com.gridynamics.forge.guardrail.chain;

import com.gridynamics.forge.guardrail.GuardrailContext;
import com.gridynamics.forge.guardrail.config.GuardrailProperties;
import com.gridynamics.forge.guardrail.model.ValidationAction;
import com.gridynamics.forge.guardrail.model.Violation;
import com.gridynamics.forge.guardrail.model.ViolationSeverity;
import com.gridynamics.forge.guardrail.registry.GuardrailPatternRegistry;
import com.gridynamics.forge.guardrail.registry.PatternCategory;
import com.gridynamics.forge.guardrail.util.PatternMatchUtil;
import com.gridynamics.forge.guardrail.util.TextNormalizationUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects common prompt injection and jailbreak attempts using shared
 * patterns from {@link GuardrailPatternRegistry}.
 *
 * <p>Input is normalized before matching to resist obfuscation bypasses.
 * HARD-block validator.
 */
public class PromptInjectionValidator implements GuardrailValidator {

    private final GuardrailProperties props;
    private final GuardrailPatternRegistry patternRegistry;

    public PromptInjectionValidator(GuardrailProperties props, GuardrailPatternRegistry patternRegistry) {
        this.props = props;
        this.patternRegistry = patternRegistry;
    }

    @Override
    public String name() {
        return "PROMPT_INJECTION";
    }

    @Override
    public int order() {
        return 110;
    }

    @Override
    public boolean isEnabled() {
        return props.getInjection().isEnabled();
    }

    @Override
    public List<Violation> validate(String text, GuardrailContext context) {
        String normalized = TextNormalizationUtil.normalizeAllWithLeetspeak(text);
        List<PatternMatchUtil.PatternHit> hits = PatternMatchUtil.matchCategorized(
                normalized,
                patternRegistry.getPatterns(PatternCategory.INJECTION)
        );

        if (hits.isEmpty()) {
            return List.of();
        }

        List<String> matchedText = new ArrayList<>();
        double maxConfidence = 0.0;
        String primaryCategory = "jailbreak";
        for (PatternMatchUtil.PatternHit hit : hits) {
            matchedText.addAll(hit.matchedText());
            maxConfidence = Math.max(maxConfidence, hit.definition().confidence());
            if ("jailbreak".equals(hit.definition().subcategory())
                    || "exfiltration".equals(hit.definition().subcategory())) {
                primaryCategory = hit.definition().subcategory();
            }
        }

        return List.of(Violation.of(
                "PROMPT_INJECTION_BLOCKED",
                "Prompt injection attempt detected",
                ViolationSeverity.HARD,
                name(),
                List.copyOf(matchedText),
                0.95,
                maxConfidence,
                primaryCategory,
                ValidationAction.BLOCK
        ));
    }
}
