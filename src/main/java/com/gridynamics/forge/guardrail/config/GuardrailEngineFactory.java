package com.gridynamics.forge.guardrail.config;

import com.gridynamics.forge.guardrail.GuardrailEngine;
import com.gridynamics.forge.guardrail.chain.GuardrailValidator;
import com.gridynamics.forge.guardrail.chain.OutputContentValidator;
import com.gridynamics.forge.guardrail.chain.SemanticValidator;
import com.gridynamics.forge.guardrail.metrics.GuardrailMetrics;
import com.gridynamics.forge.guardrail.spi.CustomGuardrailValidator;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class GuardrailEngineFactory {

    private GuardrailEngineFactory() {}

    static GuardrailEngine build(
            GuardrailProperties props,
            List<GuardrailValidator> builtInValidators,
            ObjectProvider<SemanticValidator> semanticValidator,
            List<CustomGuardrailValidator> customValidators,
            OutputContentValidator outputValidator,
            GuardrailMetrics metrics) {

        List<GuardrailValidator> all = new ArrayList<>();
        Set<String> registered = new HashSet<>();
        boolean semanticEnabled = props.getSemantic().isEnabled();
        for (GuardrailValidator validator : builtInValidators) {
            if ("OUTPUT_CONTENT".equals(validator.name())) {
                continue;
            }
            if ("SEMANTIC".equals(validator.name()) && !semanticEnabled) {
                continue;
            }
            if (registered.add(validator.name())) {
                all.add(validator);
            }
        }
        if (semanticEnabled) {
            semanticValidator.ifAvailable(validator -> {
                if (registered.add(validator.name())) {
                    all.add(validator);
                }
            });
        }
        for (CustomGuardrailValidator customValidator : customValidators) {
            if (registered.add(customValidator.name())) {
                all.add(customValidator);
            }
        }
        return new GuardrailEngine(props, all, outputValidator, metrics);
    }
}
