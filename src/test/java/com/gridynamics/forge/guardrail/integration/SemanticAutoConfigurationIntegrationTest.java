package com.gridynamics.forge.guardrail.integration;

import com.gridynamics.forge.guardrail.GuardrailEngine;
import com.gridynamics.forge.guardrail.chain.SemanticValidator;
import com.gridynamics.forge.guardrail.config.GuardrailAutoConfiguration;
import com.gridynamics.forge.guardrail.config.GuardrailEngineAutoConfiguration;
import com.gridynamics.forge.guardrail.config.SemanticAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documents when {@code SEMANTIC} appears in the pipeline. Your {@code application-guardrail-example.yml}
 * must be imported into the running Spring Boot app; unit tests use {@code hybridEngine()} without semantic.
 */
class SemanticAutoConfigurationIntegrationTest {

    @Test
    void defaultGuardrailAutoConfigDoesNotIncludeSemantic() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        GuardrailAutoConfiguration.class,
                        GuardrailEngineAutoConfiguration.class))
                .run(ctx -> assertThat(ctx.getBean(GuardrailEngine.class).getInputValidatorNames())
                        .doesNotContain("SEMANTIC"));
    }

    @Test
    void semanticAutoConfigPresentButDisabledOmitsSemanticValidator() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        GuardrailAutoConfiguration.class,
                        GuardrailEngineAutoConfiguration.class,
                        SemanticAutoConfiguration.class))
                .withPropertyValues("forge.guardrail.semantic.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(SemanticValidator.class);
                    assertThat(ctx.getBean(GuardrailEngine.class).getInputValidatorNames())
                            .doesNotContain("SEMANTIC");
                });
    }
}
