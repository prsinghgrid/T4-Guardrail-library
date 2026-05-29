package com.gridynamics.forge.guardrail.config;

import com.gridynamics.forge.guardrail.GuardrailEngine;
import com.gridynamics.forge.guardrail.chain.GuardrailValidator;
import com.gridynamics.forge.guardrail.chain.OutputContentValidator;
import com.gridynamics.forge.guardrail.chain.SemanticValidator;
import com.gridynamics.forge.guardrail.metrics.GuardrailMetrics;
import com.gridynamics.forge.guardrail.spi.CustomGuardrailValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Creates {@link GuardrailEngine} after semantic beans when semantic mode is active,
 * so the pipeline includes {@code SEMANTIC} instead of logging a false "missing" warning.
 */
@AutoConfiguration
@AutoConfigureAfter({SemanticAutoConfiguration.class, GuardrailAutoConfiguration.class})
public class GuardrailEngineAutoConfiguration {

    /** Semantic on and {@link SemanticValidator} bean exists (ONNX + JDBC + pgvector). */
    @Bean
    @ConditionalOnMissingBean(GuardrailEngine.class)
    @ConditionalOnProperty(prefix = "forge.guardrail.semantic", name = "enabled", havingValue = "true")
    @ConditionalOnBean(SemanticValidator.class)
    public GuardrailEngine guardrailEngineWithSemantic(
            GuardrailProperties props,
            List<GuardrailValidator> builtInValidators,
            ObjectProvider<SemanticValidator> semanticValidator,
            List<CustomGuardrailValidator> customValidators,
            OutputContentValidator outputValidator,
            GuardrailMetrics metrics) {
        return GuardrailEngineFactory.build(
                props, builtInValidators, semanticValidator, customValidators, outputValidator, metrics);
    }

    /** Semantic off — regex-only pipeline. */
    @Bean
    @ConditionalOnMissingBean(GuardrailEngine.class)
    @ConditionalOnProperty(prefix = "forge.guardrail.semantic", name = "enabled", havingValue = "false", matchIfMissing = true)
    public GuardrailEngine guardrailEngineRegexOnly(
            GuardrailProperties props,
            List<GuardrailValidator> builtInValidators,
            ObjectProvider<SemanticValidator> semanticValidator,
            List<CustomGuardrailValidator> customValidators,
            OutputContentValidator outputValidator,
            GuardrailMetrics metrics) {
        return GuardrailEngineFactory.build(
                props, builtInValidators, semanticValidator, customValidators, outputValidator, metrics);
    }

    /** Semantic enabled but validator missing — allow degraded regex-only startup when fail-open. */
    @Bean
    @ConditionalOnMissingBean({GuardrailEngine.class, SemanticValidator.class})
    @ConditionalOnExpression("${forge.guardrail.semantic.enabled:false} == true && ${forge.guardrail.semantic.fail-open:true} == true")
    public GuardrailEngine guardrailEngineSemanticDegraded(
            GuardrailProperties props,
            List<GuardrailValidator> builtInValidators,
            ObjectProvider<SemanticValidator> semanticValidator,
            List<CustomGuardrailValidator> customValidators,
            OutputContentValidator outputValidator,
            GuardrailMetrics metrics) {
        return GuardrailEngineFactory.build(
                props, builtInValidators, semanticValidator, customValidators, outputValidator, metrics);
    }
}
