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
 * Validates LLM <em>output</em> before it is returned to the user.
 *
 * <p>Checks for leaked system prompts, unredacted PII, API keys, JWT tokens,
 * private keys, and cloud connection strings.
 * Not part of the input chain — invoke via {@code GuardrailEngine.validateOutput(...)}.
 */
public class OutputContentValidator implements GuardrailValidator {

    private final GuardrailProperties props;
    private final GuardrailPatternRegistry patternRegistry;

    public OutputContentValidator(GuardrailProperties props, GuardrailPatternRegistry patternRegistry) {
        this.props = props;
        this.patternRegistry = patternRegistry;
    }

    @Override
    public String name() {
        return "OUTPUT_CONTENT";
    }

    @Override
    public int order() {
        return 500;
    }

    @Override
    public boolean isEnabled() {
        return props.getOutput().isEnabled();
    }

    @Override
    public List<Violation> validate(String text, GuardrailContext context) {
        String normalized = TextNormalizationUtil.normalizeAll(text);
        List<Violation> violations = new ArrayList<>();

        if (props.getOutput().isCheckPiiLeakage()) {
            addPatternViolations(
                    violations,
                    normalized,
                    patternRegistry.getPatterns(PatternCategory.PII),
                    "OUTPUT_PII",
                    "LLM output contains unredacted PII",
                    ViolationSeverity.WARN,
                    ValidationAction.WARN
            );
        }

        if (props.getOutput().isCheckSystemLeak()) {
            List<PatternMatchUtil.PatternHit> leaks = PatternMatchUtil.matchCategorized(
                    normalized,
                    patternRegistry.getPatterns(PatternCategory.OUTPUT_LEAKAGE)
            );
            if (!leaks.isEmpty()) {
                List<String> matched = flattenMatches(leaks);
                violations.add(Violation.of(
                        "OUTPUT_SYSTEM_LEAK",
                        "LLM output may contain leaked system prompt",
                        ViolationSeverity.HARD,
                        name(),
                        matched,
                        0.95,
                        leaks.get(0).definition().confidence(),
                        "output_leakage",
                        ValidationAction.BLOCK
                ));
            }
        }

        if (props.getOutput().isCheckSecrets()) {
            List<PatternMatchUtil.PatternHit> secrets = PatternMatchUtil.matchCategorized(
                    normalized,
                    patternRegistry.getPatterns(PatternCategory.SECRETS)
            );
            for (PatternMatchUtil.PatternHit secret : secrets) {
                if (secret.definition().confidence() < props.getOutput().getSecretConfidenceThreshold()) {
                    continue;
                }
                violations.add(Violation.of(
                        "OUTPUT_SECRET_LEAK",
                        "LLM output contains sensitive credential: " + secret.definition().label(),
                        ViolationSeverity.HARD,
                        name(),
                        secret.matchedText(),
                        secret.definition().weight(),
                        secret.definition().confidence(),
                        secret.definition().subcategory(),
                        ValidationAction.BLOCK
                ));
            }
        }

        return List.copyOf(violations);
    }

    private void addPatternViolations(List<Violation> violations, String text,
                                      List<com.gridynamics.forge.guardrail.registry.CategorizedPattern> patterns,
                                      String codePrefix, String message,
                                      ViolationSeverity severity, ValidationAction action) {
        for (var pattern : patterns) {
            List<String> matches = PatternMatchUtil.findMatches(pattern.pattern(), text);
            if (!matches.isEmpty()) {
                violations.add(Violation.of(
                        codePrefix + "_" + pattern.label().toUpperCase(),
                        message + ": " + pattern.label(),
                        severity,
                        name(),
                        matches,
                        pattern.weight(),
                        pattern.confidence(),
                        pattern.subcategory(),
                        action
                ));
            }
        }
    }

    private static List<String> flattenMatches(List<PatternMatchUtil.PatternHit> hits) {
        List<String> matched = new ArrayList<>();
        for (PatternMatchUtil.PatternHit hit : hits) {
            matched.addAll(hit.matchedText());
        }
        return List.copyOf(matched);
    }
}
