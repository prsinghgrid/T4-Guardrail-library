package com.gridynamics.forge.guardrail.chain;

import com.gridynamics.forge.guardrail.GuardrailContext;
import com.gridynamics.forge.guardrail.config.GuardrailProperties;
import com.gridynamics.forge.guardrail.model.ValidationAction;
import com.gridynamics.forge.guardrail.model.Violation;
import com.gridynamics.forge.guardrail.model.ViolationSeverity;
import com.gridynamics.forge.guardrail.registry.CategorizedPattern;
import com.gridynamics.forge.guardrail.registry.GuardrailPatternRegistry;
import com.gridynamics.forge.guardrail.registry.PatternCategory;
import com.gridynamics.forge.guardrail.util.PatternMatchUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects credentials and secrets embedded in user <em>input</em> prompts.
 *
 * <p>Separate from {@link OutputContentValidator} which scans LLM responses only.
 * Emits a single consolidated {@code SECRET_DETECTED} violation per scan.
 *
 * <p>Secret patterns rely on exact character runs (e.g. {@code -----BEGIN},
 * {@code AKIA[0-9A-Z]{16}}). Text is intentionally matched against the <em>raw</em>
 * input rather than normalised text to avoid {@code normalizeRepeatedCharacters}
 * collapsing credential sentinels like {@code -----} to {@code --}.
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

        // Match directly against the raw text. Secret patterns depend on exact
        // repeated-character runs (e.g. "-----BEGIN", "AKIA0000…") that
        // normalizeRepeatedCharacters would collapse and break.
        List<CategorizedPattern> secretPatterns = patternRegistry.getPatterns(PatternCategory.SECRETS);
        Set<String> secretLabels = new LinkedHashSet<>();
        double maxScore = 0.0;
        double maxConfidence = 0.0;

        for (CategorizedPattern pattern : secretPatterns) {
            List<String> matches = PatternMatchUtil.findMatches(pattern.pattern(), text);
            if (matches.isEmpty()) {
                continue;
            }
            if (pattern.confidence() < props.getInputSecrets().getConfidenceThreshold()) {
                continue;
            }
            secretLabels.add(toDisplayLabel(pattern.label()));
            maxScore = Math.max(maxScore, pattern.weight());
            maxConfidence = Math.max(maxConfidence, pattern.confidence());
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
