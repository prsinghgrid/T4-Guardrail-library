package com.gridynamics.forge.guardrail;

import com.gridynamics.forge.guardrail.chain.*;
import com.gridynamics.forge.guardrail.config.GuardrailProperties;
import com.gridynamics.forge.guardrail.metrics.GuardrailMetrics;
import com.gridynamics.forge.guardrail.registry.GuardrailPatternRegistry;

import java.util.List;

/**
 * Shared test fixtures for guardrail unit tests.
 */
public final class GuardrailTestFixtures {

    /** Regex + secret scanners in default hybrid test engine (semantic is separate). */
    /** Matches validator {@link com.gridynamics.forge.guardrail.chain.GuardrailValidator#order()}. */
    public static final List<String> HYBRID_VALIDATOR_NAMES = List.of(
            "INPUT_LENGTH",
            "BIAS",
            "PROMPT_INJECTION",
            "SECRET_SCANNER",
            "PII",
            "TOXICITY"
    );

    private GuardrailTestFixtures() {
    }

    public static GuardrailPatternRegistry patternRegistry(GuardrailProperties props) {
        return new GuardrailPatternRegistry(props);
    }

    public static GuardrailPatternRegistry defaultPatternRegistry() {
        return patternRegistry(new GuardrailProperties());
    }

    public static GuardrailEngine hybridEngine(GuardrailProperties props) {
        return hybridEngine(props, GuardrailMetrics.NOOP);
    }

    public static GuardrailEngine hybridEngine(GuardrailProperties props, GuardrailMetrics metrics) {
        var registry = patternRegistry(props);

        return new GuardrailEngine(
                props,
                List.of(
                        new InputLengthValidator(props),
                        new BiasValidator(props, registry),
                        new InputSecretValidator(props, registry),
                        new PromptInjectionValidator(props, registry),
                        new PiiSanitizingValidator(props),
                        new ToxicityValidator(props, registry)
                ),
                new OutputContentValidator(props, registry),
                metrics
        );
    }
}
