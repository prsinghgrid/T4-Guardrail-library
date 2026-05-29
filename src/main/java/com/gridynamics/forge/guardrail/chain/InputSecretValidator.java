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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects credentials and secrets embedded in user <em>input</em> prompts.
 *
 * <p>Separate from {@link OutputContentValidator} which scans LLM responses only.
 * Emits a single consolidated {@code SECRET_DETECTED} violation per scan.
 */
public class InputSecretValidator implements GuardrailValidator {

    private final GuardrailProperties props;
    private final GuardrailPatternRegistry patternRegistry;

    public InputSecretValidator(GuardrailProperties props, GuardrailPatternRegistry patternRegistry) {
        this.props = props;
        this.patternRegistry = patternRegistry;
    }

    @Override
    public String name() {
        return "SECRET_SCANNER";
    }

    @Override
    public int order() {
        return 115;
    }

    @Override
    public boolean isEnabled() {
        return props.getInputSecrets().isEnabled();
    }

    @Override
    public List<Violation> validate(String text, GuardrailContext context) {
        if (!props.getInputSecrets().isEnabled()) {
            return List.of();
        }

        String normalized = TextNormalizationUtil.normalizeAll(text);
        List<PatternMatchUtil.PatternHit> hits = PatternMatchUtil.matchCategorized(
                normalized,
                patternRegistry.getPatterns(PatternCategory.SECRETS)
        );

        if (hits.isEmpty()) {
            return List.of();
        }

        Set<String> secretLabels = new LinkedHashSet<>();
        double maxScore = 0.0;
        double maxConfidence = 0.0;

        for (PatternMatchUtil.PatternHit hit : hits) {
            if (hit.definition().confidence() < props.getInputSecrets().getConfidenceThreshold()) {
                continue;
            }
            secretLabels.add(toDisplayLabel(hit.definition().label()));
            maxScore = Math.max(maxScore, hit.definition().weight());
            maxConfidence = Math.max(maxConfidence, hit.definition().confidence());
        }

        if (secretLabels.isEmpty()) {
            return List.of();
        }

        return List.of(Violation.of(
                "SECRET_DETECTED",
                "Prompt contains embedded credentials or secrets",
                ViolationSeverity.HARD,
                name(),
                new ArrayList<>(secretLabels),
                maxScore,
                maxConfidence,
                "SECRETS",
                ValidationAction.BLOCK
        ));
    }

    private static String toDisplayLabel(String label) {
        return switch (label) {
            case "aws_access_key" -> "AWS_ACCESS_KEY";
            case "jwt_token" -> "JWT_TOKEN";
            case "openai_api_key" -> "OPENAI_API_KEY";
            case "bearer_token" -> "BEARER_TOKEN";
            case "private_key" -> "PRIVATE_KEY";
            case "connection_string" -> "CONNECTION_STRING";
            default -> label.toUpperCase();
        };
    }
}
