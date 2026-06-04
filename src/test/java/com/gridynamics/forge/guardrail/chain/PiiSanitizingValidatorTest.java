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
    void stripsPhoneWithSpaceSeparators() {
        // "91 52 34 25 65" = 10-digit Indian mobile number written with 2-digit spacing
        var s = validator.sanitise("phone number is : 91 52 34 25 65");
        assertThat(s).contains("[PHONE_REDACTED]").doesNotContain("91 52 34 25 65");
    }

    @Test
    void detectsPhoneWithSpaceSeparators() {
        var violations = validator.validate("phone number is : 91 52 34 25 65", ctx);
        assertThat(violations).isNotEmpty();
        assertThat(violations.get(0).matches()).contains("PHONE_IN");
    }

    @Test
    void stripsCreditCardWithSpaces() {
        // 9152-prefix (RuPay / generic 9xxx) — 16-digit card in XXXX XXXX XXXX XXXX format
        var s = validator.sanitise("card number is 9152 4256 5464 5758");
        assertThat(s).contains("[CARD_REDACTED]").doesNotContain("9152");
    }

    @Test
    void detectsCreditCardWithSpacesNotAadhaar() {
        var violations = validator.validate("card number is 9152 4256 5464 5758", ctx);
        assertThat(violations).isNotEmpty();
        var types = violations.get(0).matches();
        assertThat(types).contains("CREDIT_CARD");
        assertThat(types).doesNotContain("AADHAAR");
    }

    @Test
    void stripsCreditCardWithHyphens() {
        var s = validator.sanitise("card: 4111-1111-1111-1111");
        assertThat(s).contains("[CARD_REDACTED]").doesNotContain("4111");
    }

    @Test
    void stripsMaestroRuPayCard() {
        // 6788-prefix covers Maestro / RuPay (67xx) cards — previously fell through to PHONE_INTL
        var s = validator.sanitise("my phone number is : 6788 9980 9880 7979");
        assertThat(s).isEqualTo("my phone number is : [CARD_REDACTED]");
    }

    @Test
    void detectsMaestroRuPayCardNotPhone() {
        var violations = validator.validate("my phone number is : 6788 9980 9880 7979", ctx);
        assertThat(violations).isNotEmpty();
        var types = violations.get(0).matches();
        assertThat(types).contains("CREDIT_CARD");
        assertThat(types).doesNotContain("PHONE_IN");
        assertThat(types).doesNotContain("PHONE_INTL");
        assertThat(types).doesNotContain("AADHAAR");
    }

    @Test
    void doesNotDetectAadhaarInsideCreditCard() {
        // The first 12 digits of "9152 4256 5464 5758" look like an Aadhaar —
        // the lookahead guard must prevent a false Aadhaar detection.
        var violations = validator.validate("9152 4256 5464 5758", ctx);
        assertThat(violations).isNotEmpty();
        assertThat(violations.get(0).matches()).doesNotContain("AADHAAR");
    }

    @Test
    void creditCardDetectionSuppressesPhoneCodetection() {
        // Any 16-digit number in XXXX XXXX XXXX XXXX format should report CREDIT_CARD only
        var violations = validator.validate("6011 1111 1111 1117", ctx);
        assertThat(violations).isNotEmpty();
        var types = violations.get(0).matches();
        assertThat(types).contains("CREDIT_CARD");
        assertThat(types).doesNotContain("PHONE_INTL");
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
