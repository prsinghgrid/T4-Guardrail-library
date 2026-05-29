package com.gridynamics.forge.guardrail.config;

import com.gridynamics.forge.guardrail.GuardrailEngine;
import com.gridynamics.forge.guardrail.chain.BiasValidator;
import com.gridynamics.forge.guardrail.chain.GuardrailValidator;
import com.gridynamics.forge.guardrail.chain.InputLengthValidator;
import com.gridynamics.forge.guardrail.chain.InputSecretValidator;
import com.gridynamics.forge.guardrail.chain.OutputContentValidator;
import com.gridynamics.forge.guardrail.chain.PiiSanitizingValidator;
import com.gridynamics.forge.guardrail.chain.PromptInjectionValidator;
import com.gridynamics.forge.guardrail.chain.ToxicityValidator;
import com.gridynamics.forge.guardrail.metrics.GuardrailMetrics;
import com.gridynamics.forge.guardrail.metrics.MicrometerGuardrailMetrics;
import com.gridynamics.forge.guardrail.registry.GuardrailPatternRegistry;
import com.gridynamics.forge.guardrail.spi.CustomGuardrailValidator;
import com.gridynamics.forge.guardrail.web.GuardrailController;
import com.gridynamics.forge.guardrail.web.GuardrailExceptionHandler;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** Spring Boot auto-configuration for guardrail validators and web support. */
@AutoConfiguration
@EnableConfigurationProperties(GuardrailProperties.class)
public class GuardrailAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GuardrailStartupValidator guardrailStartupValidator(GuardrailProperties props,
                                                                 GuardrailPatternRegistry patternRegistry,
                                                                 Environment environment) {
        return new GuardrailStartupValidator(props, patternRegistry, environment);
    }

    @Bean
    @ConditionalOnMissingBean
    public GuardrailPatternRegistry guardrailPatternRegistry(GuardrailProperties props) {
        return new GuardrailPatternRegistry(props);
    }

    @Bean
    @ConditionalOnMissingBean(GuardrailMetrics.class)
    @ConditionalOnClass(MeterRegistry.class)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(MeterRegistry.class)
    public GuardrailMetrics micrometerGuardrailMetrics(MeterRegistry registry) {
        return new MicrometerGuardrailMetrics(registry);
    }

    @Bean
    @ConditionalOnMissingBean(GuardrailMetrics.class)
    public GuardrailMetrics guardrailMetrics() {
        return GuardrailMetrics.NOOP;
    }

    @Bean
    @ConditionalOnMissingBean
    public InputLengthValidator inputLengthValidator(GuardrailProperties props) {
        return new InputLengthValidator(props);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "forge.guardrail.bias", name = "enabled", matchIfMissing = true)
    public BiasValidator biasValidator(GuardrailProperties props, GuardrailPatternRegistry patternRegistry) {
        return new BiasValidator(props, patternRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "forge.guardrail.pii", name = "enabled", matchIfMissing = true)
    public PiiSanitizingValidator piiSanitizingValidator(GuardrailProperties props) {
        return new PiiSanitizingValidator(props);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "forge.guardrail.injection", name = "enabled", matchIfMissing = true)
    public PromptInjectionValidator promptInjectionValidator(GuardrailProperties props,
                                                               GuardrailPatternRegistry patternRegistry) {
        return new PromptInjectionValidator(props, patternRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "forge.guardrail.toxicity", name = "enabled", matchIfMissing = true)
    public ToxicityValidator toxicityValidator(GuardrailProperties props,
                                                GuardrailPatternRegistry patternRegistry) {
        return new ToxicityValidator(props, patternRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "forge.guardrail.input-secrets", name = "enabled", matchIfMissing = true)
    public InputSecretValidator inputSecretValidator(GuardrailProperties props,
                                                      GuardrailPatternRegistry patternRegistry) {
        return new InputSecretValidator(props, patternRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutputContentValidator outputContentValidator(GuardrailProperties props,
                                                          GuardrailPatternRegistry patternRegistry) {
        return new OutputContentValidator(props, patternRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication
    public GuardrailExceptionHandler guardrailExceptionHandler() {
        return new GuardrailExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean(GuardrailController.class)
    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "forge.guardrail.api", name = "enabled", matchIfMissing = true)
    public GuardrailController guardrailController(GuardrailEngine guardrailEngine) {
        return new GuardrailController(guardrailEngine);
    }
}
