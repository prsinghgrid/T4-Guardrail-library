package com.gridynamics.forge.guardrail.chain;

import com.gridynamics.forge.guardrail.GuardrailContext;
import com.gridynamics.forge.guardrail.config.GuardrailProperties;
import com.gridynamics.forge.guardrail.model.ViolationSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InputLengthValidatorTest {

    private InputLengthValidator validator;
    private final GuardrailContext ctx = GuardrailContext.of("TEST", "T4");

    @BeforeEach
    void setUp() {
        var props = new GuardrailProperties();
        props.setMaxPromptLength(100);
        validator = new InputLengthValidator(props);
    }

    @Test
    void shortPromptPasses() {
        assertThat(validator.validate("Short prompt", ctx)).isEmpty();
    }

    @Test
    void longPromptGivesSoftViolation() {
        var result = validator.validate("x".repeat(150), ctx);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).severity()).isEqualTo(ViolationSeverity.SOFT);
    }

    @Test
    void sanitiseTruncates() {
        assertThat(validator.sanitise("x".repeat(150))).hasSize(100);
    }

    @Test
    void sanitisePreservesShort() {
        assertThat(validator.sanitise("Short")).isEqualTo("Short");
    }

    @Test
    void exactLengthDoesNotTrigger() {
        assertThat(validator.validate("y".repeat(100), ctx)).isEmpty();
    }
}
