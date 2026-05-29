package com.gridynamics.forge.guardrail.integration;

import com.gridynamics.forge.guardrail.GuardrailEngine;
import com.gridynamics.forge.guardrail.chain.*;
import com.gridynamics.forge.guardrail.config.GuardrailAutoConfiguration;
import com.gridynamics.forge.guardrail.config.GuardrailEngineAutoConfiguration;
import com.gridynamics.forge.guardrail.config.GuardrailProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class GuardrailAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
                GuardrailAutoConfiguration.class,
                GuardrailEngineAutoConfiguration.class));

    @Test
    void allBeansCreatedByDefault() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(GuardrailEngine.class);
            assertThat(ctx).hasSingleBean(BiasValidator.class);
            assertThat(ctx).hasSingleBean(PiiSanitizingValidator.class);
            assertThat(ctx).hasSingleBean(PromptInjectionValidator.class);
            assertThat(ctx).hasSingleBean(ToxicityValidator.class);
            assertThat(ctx).hasSingleBean(InputLengthValidator.class);
            assertThat(ctx).hasSingleBean(InputSecretValidator.class);
            assertThat(ctx).hasSingleBean(OutputContentValidator.class);

            assertThat(ctx.getBean(GuardrailEngine.class).getInputValidatorNames())
                    .containsExactly(
                            "INPUT_LENGTH", "BIAS", "PROMPT_INJECTION",
                            "SECRET_SCANNER", "PII", "TOXICITY");
        });
    }

    @Test
    void biasCanBeDisabled() {
        runner.withPropertyValues("forge.guardrail.bias.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(BiasValidator.class));
    }

    @Test
    void piiCanBeDisabled() {
        runner.withPropertyValues("forge.guardrail.pii.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(PiiSanitizingValidator.class));
    }

    @Test
    void injectionCanBeDisabled() {
        runner.withPropertyValues("forge.guardrail.injection.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(PromptInjectionValidator.class));
    }

    @Test
    void customPropertiesBind() {
        runner.withPropertyValues(
            "forge.guardrail.max-prompt-length=5000",
            "forge.guardrail.fallback-confidence-threshold=0.8"
        ).run(ctx -> {
            var props = ctx.getBean(GuardrailProperties.class);
            assertThat(props.getMaxPromptLength()).isEqualTo(5000);
            assertThat(props.getFallbackConfidenceThreshold()).isEqualTo(0.8);
        });
    }
}
