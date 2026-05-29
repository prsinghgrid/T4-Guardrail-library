package com.gridynamics.forge.guardrail.config;

import com.gridynamics.forge.guardrail.registry.GuardrailPatternRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuardrailStartupValidatorTest {

    @Test
    void rejectsInvalidThreshold() {
        var props = new GuardrailProperties();
        props.getToxicity().setHardBlockThreshold(1.5);
        var validator = new GuardrailStartupValidator(props, new GuardrailPatternRegistry(props), new MockEnvironment());

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("toxicity.hard-block-threshold");
    }

    @Test
    void productionModeConfigurationPasses() {
        var props = new GuardrailProperties();
        props.setProductionMode(true);

        var validator = new GuardrailStartupValidator(props, new GuardrailPatternRegistry(props), new MockEnvironment());
        validator.afterPropertiesSet();
        assertThat(props.isProductionMode()).isTrue();
    }

    @Test
    void validConfigurationPasses() {
        var props = new GuardrailProperties();
        var validator = new GuardrailStartupValidator(props, new GuardrailPatternRegistry(props), new MockEnvironment());
        validator.validate();
    }
}
