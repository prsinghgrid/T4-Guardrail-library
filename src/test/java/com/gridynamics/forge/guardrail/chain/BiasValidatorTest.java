package com.gridynamics.forge.guardrail.chain;

import com.gridynamics.forge.guardrail.GuardrailContext;
import com.gridynamics.forge.guardrail.GuardrailTestFixtures;
import com.gridynamics.forge.guardrail.config.GuardrailProperties;
import com.gridynamics.forge.guardrail.model.Violation;
import com.gridynamics.forge.guardrail.model.ViolationSeverity;
import com.gridynamics.forge.guardrail.report.ModerationReportFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BiasValidatorTest {

    private BiasValidator validator;
    private final GuardrailContext ctx = GuardrailContext.of("TEST", "T4");

    @BeforeEach
    void setUp() {
        var props = new GuardrailProperties();
        validator = new BiasValidator(props, GuardrailTestFixtures.patternRegistry(props));
    }

    @Test
    void cleanPrompt() {
        assertThat(validator.validate("Senior Java developer needed", ctx)).isEmpty();
    }

    @Test
    void detectsOnlyBoysStandalone() {
        var result = validator.validate("we require only boys", ctx);
        assertThat(result).isNotEmpty();
        assertThat(result.stream().map(Violation::code))
                .anyMatch(code -> code.startsWith("BIAS_GENDER"));
        assertThat(result.stream().map(Violation::severity))
                .containsOnly(ViolationSeverity.HARD);
    }

    @Test
    void detectsOnlyGirlsStandalone() {
        var result = validator.validate("we only want girls", ctx);
        assertThat(result).isNotEmpty();
        assertThat(result.stream().map(Violation::code))
                .anyMatch(code -> code.startsWith("BIAS_GENDER"));
    }

    @Test
    void detectsVerbOnlyGender() {
        var result = validator.validate("we need only women for this role", ctx);
        assertThat(result).isNotEmpty();
        assertThat(result.stream().map(Violation::code))
                .anyMatch(code -> code.startsWith("BIAS_GENDER"));
    }

    @Test
    void detectsAgeBiasWorkforceStartupParaphrase() {
        String prompt = "We prefer applicants who recently entered the workforce "
                + "and can easily adapt to modern startup energy.";
        var result = validator.validate(prompt, ctx);
        assertThat(result).isNotEmpty();
        assertThat(result.stream().map(Violation::code))
                .anyMatch(code -> code.startsWith("BIAS_AGE"));
    }

    @Test
    void detectsGenderBias() {
        var result = validator.validate("Create JD for male only candidates", ctx);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).severity()).isEqualTo(ViolationSeverity.HARD);
        assertThat(result.get(0).code()).isEqualTo("BIAS_GENDER");
        assertThat(result.get(0).matches()).contains("male only");
    }

    @Test
    void detectsReversedWordOrderGenderBias() {
        // "Only female candidates" — reversed word order compared to "female only"
        var result = validator.validate("Only female candidates under 30 should apply.", ctx);
        assertThat(result).isNotEmpty();
        assertThat(result.stream().map(Violation::code)).contains("BIAS_GENDER");
    }

    @Test
    void detectsAgeBiasNumeric() {
        var result = validator.validate("Candidates under 30 preferred.", ctx);
        assertThat(result).isNotEmpty();
        assertThat(result.stream().map(Violation::code)).contains("BIAS_AGE");
    }

    @Test
    void detectsEducationBias() {
        var result = validator.validate("Only candidates from IIT should apply.", ctx);
        assertThat(result).isNotEmpty();
        assertThat(result.stream().map(Violation::code)).contains("BIAS_EDUCATION");
    }

    @Test
    void detectsEducationElitismParaphraseWithoutOnly() {
        String prompt = "We only consider candidates from top-tier universities with highly reputed "
                + "academic institutions. State school graduates are not a fit.";
        var result = validator.validate(prompt, ctx);
        assertThat(result).isNotEmpty();
        assertThat(result.stream().map(Violation::code)).contains("BIAS_EDUCATION");
    }

    /**
     * Core bug-report scenario: "Only female candidates under 30 from IIT should apply."
     * must produce BIAS_GENDER + BIAS_AGE + BIAS_EDUCATION violations and be blocked.
     */
    @Test
    void detectsGenderAgEducationBias_bugReportScenario() {
        var result = validator.validate("Only female candidates under 30 from IIT should apply.", ctx);

        List<String> codes = result.stream().map(Violation::code).toList();
        assertThat(codes).contains("BIAS_GENDER");
        assertThat(codes).contains("BIAS_AGE");
        assertThat(codes).contains("BIAS_EDUCATION");
        assertThat(result).allMatch(v -> v.severity() == ViolationSeverity.HARD);
    }

    @Test
    void detectsCasteBias() {
        var result = validator.validate("Only upper caste candidates should apply", ctx);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("BIAS_CASTE");
        assertThat(result.get(0).matches()).contains("upper caste");
    }

    @Test
    void detectsGenderDerogatoryLanguage() {
        var result = validator.validate(
                "Women are toxic engineers and only men should be hired", ctx);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("BIAS_GENDER");
        assertThat(result.get(0).matches())
                .contains("Women are toxic", "only men should be hired");
    }

    @Test
    void detectsGenderAssholeInsult() {
        var result = validator.validate("Women are asshole engineers", ctx);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).matches()).anyMatch(m -> m.toLowerCase().contains("women are asshole"));
    }

    @Test
    void detectsStretchedGenderInsult() {
        var result = validator.validate("Women are asssshole engineers", ctx);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).matches()).anyMatch(m -> m.toLowerCase().contains("women are asshole"));
    }

    @Test
    void detectsAlternateGenderInsults() {
        assertThat(validator.validate("Men are morons in this team", ctx)).hasSize(1);
        assertThat(validator.validate("Girls are worthless candidates", ctx)).hasSize(1);
    }

    @Test
    void detectsMultipleTermsAcrossSubcategories() {
        // male only (gender) + upper caste (caste) + attractive candidates (appearance)
        // Each subcategory produces its own Violation, so we expect >= 2 violations
        var result = validator.validate("male only, upper caste, attractive candidates", ctx);
        assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        List<String> codes = result.stream().map(Violation::code).toList();
        assertThat(codes).contains("BIAS_GENDER", "BIAS_CASTE");
    }

    @Test
    void caseInsensitive() {
        assertThat(validator.validate("FEMALE ONLY applicants", ctx)).hasSize(1);
    }

    @Test
    void supportsExtraTerms() {
        var props = new GuardrailProperties();
        props.getBias().setExtraTerms(List.of("custom banned phrase"));
        var custom = new BiasValidator(props, GuardrailTestFixtures.patternRegistry(props));
        assertThat(custom.validate("Contains custom banned phrase here", ctx)).hasSize(1);
    }

    @Test
    void supportsExtraPatterns() {
        var props = new GuardrailProperties();
        props.getBias().setExtraPatterns(List.of("no\\s+remote\\s+workers"));
        var custom = new BiasValidator(props, GuardrailTestFixtures.patternRegistry(props));
        assertThat(custom.validate("We need no remote workers", ctx)).hasSize(1);
    }

    @Test
    void noFalsePositiveOnSkill() {
        assertThat(validator.validate("Must have Java skill and teamwork", ctx)).isEmpty();
    }

    @Test
    void disabledReportsNotEnabled() {
        var props = new GuardrailProperties();
        props.getBias().setEnabled(false);
        assertThat(new BiasValidator(props, GuardrailTestFixtures.patternRegistry(props)).isEnabled()).isFalse();
    }

    // ── LGBTQ / Sexuality bias ────────────────────────────────────────────────

    @Test
    void detectsLgbtqOnlyRequirement() {
        var result = validator.validate("we need only LGBTQ candidates", ctx);
        assertThat(result).isNotEmpty();
        assertThat(result.stream().map(Violation::code))
                .anyMatch(code -> code.startsWith("BIAS_SEXUALITY"));
        assertThat(result.stream().map(Violation::severity))
                .containsOnly(ViolationSeverity.HARD);
    }

    @Test
    void detectsLgbtqOnlyWithVerb() {
        var result = validator.validate("we only want LGBTQ applicants for this role", ctx);
        assertThat(result).isNotEmpty();
        assertThat(result.stream().map(Violation::code))
                .anyMatch(code -> code.startsWith("BIAS_SEXUALITY"));
    }

    @Test
    void detectsIndividualIdentityExclusion() {
        var result = validator.validate("no transgender employees allowed on this project", ctx);
        assertThat(result).isNotEmpty();
        assertThat(result.stream().map(Violation::code))
                .anyMatch(code -> code.startsWith("BIAS_SEXUALITY"));
    }

    @Test
    void detectsNoGayPeopleExclusion() {
        var result = validator.validate("no gay people should be considered for the role", ctx);
        assertThat(result).isNotEmpty();
        assertThat(result.stream().map(Violation::code))
                .anyMatch(code -> code.startsWith("BIAS_SEXUALITY"));
    }

    @Test
    void cleanLgbtqInclusionStatement() {
        // "LGBTQ-friendly" workplace statement must NOT trigger a bias violation
        assertThat(validator.validate("we are an LGBTQ-friendly workplace", ctx)).isEmpty();
        assertThat(validator.validate("we celebrate LGBTQ pride at our company", ctx)).isEmpty();
    }

    // ── Nationality bias ──────────────────────────────────────────────────────

    @Test
    void detectsNationalityOnlyRussian() {
        var result = validator.validate("we need only Russian people for this role", ctx);
        assertThat(result).isNotEmpty();
        assertThat(result.stream().map(Violation::code))
                .anyMatch(code -> code.startsWith("BIAS_NATIONALITY"));
        assertThat(result.stream().map(Violation::severity))
                .containsOnly(ViolationSeverity.HARD);
    }

    @Test
    void detectsNationalityOnlyPhrasing() {
        var result = validator.validate("only Chinese candidates are welcome to apply", ctx);
        assertThat(result).isNotEmpty();
        assertThat(result.stream().map(Violation::code))
                .anyMatch(code -> code.startsWith("BIAS_NATIONALITY"));
    }

    @Test
    void detectsNamedNationalitySuffix() {
        var result = validator.validate("Indians only need apply for this position", ctx);
        assertThat(result).isNotEmpty();
        assertThat(result.stream().map(Violation::code))
                .anyMatch(code -> code.startsWith("BIAS_NATIONALITY"));
    }

    // ── Category mapping assertions (via ModerationReportFormatter) ───────────

    @Test
    void nationalityViolationMapsToNationalityBiasCategory() {
        var violations = validator.validate("we need only Russian people", ctx);
        assertThat(violations).isNotEmpty();

        var report = ModerationReportFormatter.format(violations, Duration.ofMillis(10));
        assertThat(report.violations())
                .filteredOn(dto -> dto.code().startsWith("BIAS_NATIONALITY"))
                .isNotEmpty()
                .allMatch(dto -> "NATIONALITY_BIAS".equals(dto.category()));
    }

    @Test
    void genderViolationMapsToGenderBiasCategory() {
        var violations = validator.validate("male only candidates required", ctx);
        assertThat(violations).isNotEmpty();

        var report = ModerationReportFormatter.format(violations, Duration.ofMillis(10));
        assertThat(report.violations())
                .filteredOn(dto -> dto.code().startsWith("BIAS_GENDER"))
                .isNotEmpty()
                .allMatch(dto -> "GENDER_BIAS".equals(dto.category()));
    }

    @Test
    void sexualityViolationMapsToSexualityBiasCategory() {
        var violations = validator.validate("no lgbtq candidates please", ctx);
        assertThat(violations).isNotEmpty();

        var report = ModerationReportFormatter.format(violations, Duration.ofMillis(10));
        assertThat(report.violations())
                .filteredOn(dto -> dto.code().startsWith("BIAS_SEXUALITY"))
                .isNotEmpty()
                .allMatch(dto -> "SEXUALITY_BIAS".equals(dto.category()));
    }
}
