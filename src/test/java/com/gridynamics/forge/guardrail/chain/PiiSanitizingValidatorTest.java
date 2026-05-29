package com.gridynamics.forge.guardrail.chain;

import com.gridynamics.forge.guardrail.GuardrailContext;
import com.gridynamics.forge.guardrail.config.GuardrailProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiSanitizingValidatorTest {

    private PiiSanitizingValidator validator;
    private final GuardrailContext ctx = GuardrailContext.of("TEST", "T4");

    @BeforeEach
    void setUp() {
        validator = new PiiSanitizingValidator(new GuardrailProperties());
    }

    @Test
    void cleanText() {
        assertThat(validator.validate("Generate JD for backend engineer", ctx)).isEmpty();
    }

    @Test
    void stripsEmail() {
        var s = validator.sanitise("Contact john.doe@gridynamics.com for details");
        assertThat(s).contains("[EMAIL_REDACTED]").doesNotContain("john.doe@gridynamics.com");
    }

    @Test
    void detectsObfuscatedEmail() {
        String prompt = "Contact me at sarah dot jones at outlook dot com";
        var violations = validator.validate(prompt, ctx);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).matches()).contains("EMAIL");
    }

    @Test
    void stripsObfuscatedEmail() {
        String prompt = "Contact me at sarah dot jones at outlook dot com";
        var s = validator.sanitise(prompt);
        assertThat(s)
                .isEqualTo("Contact me at [EMAIL_REDACTED]")
                .doesNotContain("sarah")
                .doesNotContain("outlook");
    }

    @Test
    void stripsObfuscatedEmailAndSpokenPhoneTogether() {
        String prompt = "Contact me at sarah dot jones at outlook dot com. "
                + "My number is four one five five five five zero one two three.";
        var violations = validator.validate(prompt, ctx);
        assertThat(violations.get(0).matches())
                .contains("EMAIL")
                .contains("PHONE_SPOKEN");

        var s = validator.sanitise(prompt);
        assertThat(s)
                .isEqualTo("Contact me at [EMAIL_REDACTED]. My number is [PHONE_REDACTED].")
                .doesNotContain("sarah")
                .doesNotContain("four one five");
    }

    @Test
    void stripsDotAtObfuscatedEmail() {
        String prompt = "Contact me at sarah dot at outlook dot com";
        var s = validator.sanitise(prompt);
        assertThat(s)
                .isEqualTo("Contact me at [EMAIL_REDACTED]")
                .doesNotContain("sarah")
                .doesNotContain("outlook");
    }

    @Test
    void stripsSimpleObfuscatedEmail() {
        String prompt = "Contact me at sarah at outlook dot com";
        var s = validator.sanitise(prompt);
        assertThat(s)
                .isEqualTo("Contact me at [EMAIL_REDACTED]")
                .doesNotContain("outlook");
    }

    @Test
    void doesNotPartiallyRedactMultiSegmentLocalEmail() {
        String prompt = "Contact me at sarah dot jones at outlook dot com";
        var s = validator.sanitise(prompt);
        assertThat(s)
                .isEqualTo("Contact me at [EMAIL_REDACTED]")
                .doesNotContain("sarah")
                .doesNotContain("jones");
    }

    @Test
    void stripsMultipleObfuscatedEmailVariantsInOnePrompt() {
        String prompt = "Contact me at sarah dot at outlook dot com. "
                + "Contact me at sarah dot jones at outlook dot com.";
        var s = validator.sanitise(prompt);
        assertThat(s)
                .isEqualTo("Contact me at [EMAIL_REDACTED]. Contact me at [EMAIL_REDACTED].")
                .doesNotContain("sarah")
                .doesNotContain("outlook");
    }

    @Test
    void stripsUsernameAtDomainObfuscatedEmail() {
        String prompt = "Contact me prsingh at grid dot com. "
                + "Contact me at sarah dot jones at outlook dot com.";
        var s = validator.sanitise(prompt);
        assertThat(s)
                .isEqualTo("Contact me [EMAIL_REDACTED]. Contact me at [EMAIL_REDACTED].")
                .doesNotContain("prsingh")
                .doesNotContain("grid")
                .doesNotContain("sarah");
    }

    @Test
    void doesNotFalsePositiveOnInnocentProseWithAtAndDot() {
        assertThat(validator.validate("Meet me at the office dot com convention hall", ctx)).isEmpty();
    }

    @Test
    void stripsPhone() {
        var s = validator.sanitise("Call +91 9876543210");
        assertThat(s).contains("[PHONE_REDACTED]").doesNotContain("9876543210");
    }

    @Test
    void stripsSpokenPhoneNumber() {
        String prompt = "Contact me at john.smith@company.com or call nine one zero "
                + "five five five zero one two three.";
        var violations = validator.validate(prompt, ctx);
        assertThat(violations).isNotEmpty();
        assertThat(violations.get(0).matches())
                .contains("EMAIL")
                .contains("PHONE_SPOKEN");

        var s = validator.sanitise(prompt);
        assertThat(s)
                .contains("[EMAIL_REDACTED]")
                .contains("[PHONE_REDACTED]")
                .doesNotContain("john.smith@company.com")
                .doesNotContain("nine one zero");
    }

    @Test
    void stripsAadhaar() {
        assertThat(validator.sanitise("Aadhaar: 2345 6789 0123")).contains("[AADHAAR_REDACTED]");
    }

    @Test
    void stripsPan() {
        var s = validator.sanitise("PAN: ABCDE1234F");
        assertThat(s).contains("[PAN_REDACTED]").doesNotContain("ABCDE1234F");
    }

    @Test
    void stripsCreditCard() {
        assertThat(validator.sanitise("Card: 4111111111111111")).contains("[CARD_REDACTED]");
    }

    @Test
    void stripsSSN() {
        assertThat(validator.sanitise("SSN: 123-45-6789")).contains("[SSN_REDACTED]");
    }

    @Test
    void stripsLabelledNames() {
        var s = validator.sanitise("Employee: Rahul Sharma is applying");
        assertThat(s).contains("[NAME_REDACTED]").doesNotContain("Rahul Sharma");
    }

    @Test
    void idempotent() {
        var input = "Contact john@test.com now";
        assertThat(validator.sanitise(input)).isEqualTo(validator.sanitise(validator.sanitise(input)));
    }
}
