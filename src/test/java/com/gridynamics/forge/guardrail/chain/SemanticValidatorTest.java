package com.gridynamics.forge.guardrail.chain;

import com.gridynamics.forge.guardrail.GuardrailContext;
import com.gridynamics.forge.guardrail.GuardrailEngine;
import com.gridynamics.forge.guardrail.GuardrailResult;
import com.gridynamics.forge.guardrail.config.GuardrailProperties;
import com.gridynamics.forge.guardrail.model.ValidationAction;
import com.gridynamics.forge.guardrail.model.ViolationSeverity;
import com.gridynamics.forge.guardrail.semantic.SemanticCategory;
import com.gridynamics.forge.guardrail.semantic.SemanticMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticValidatorTest {

    private GuardrailProperties props;
    private SemanticValidator validator;
    private GuardrailContext context;
    private SemanticValidatorTestSupport.StubSemanticStore semanticStore;

    @BeforeEach
    void setUp() {
        props = new GuardrailProperties();
        props.getSemantic().setTopK(10);
        props.getSemantic().getCategoryThresholds().setBias(0.65);
        props.getSemantic().getCategoryThresholds().setToxicity(0.78);

        semanticStore = new SemanticValidatorTestSupport.StubSemanticStore();
        validator = new SemanticValidator(
                props,
                new SemanticValidatorTestSupport.StubEmbeddingProvider(),
                semanticStore,
                null
        );
        props.getSemantic().setEnabled(true);
        context = GuardrailContext.of("JD_GEN", "T4");
    }

    @Test
    void blocksBiasMatchAboveCategoryThreshold() {
        semanticStore.candidates = List.of(
                new SemanticMatch(
                        "we prefer applicants who recently entered the workforce",
                        "age bias — workforce entry",
                        SemanticCategory.BIAS,
                        0.72
                )
        );

        var violations = validator.validate(
                "We prefer applicants who recently entered the workforce and adapt to startup energy",
                context);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).code()).isEqualTo("SEMANTIC_BIAS");
        assertThat(violations.get(0).severity()).isEqualTo(ViolationSeverity.HARD);
        assertThat(violations.get(0).detector()).isEqualTo("SEMANTIC");
        assertThat(violations.get(0).action()).isEqualTo(ValidationAction.BLOCK);
        assertThat(violations.get(0).message())
                .contains("0.720")
                .contains("0.650")
                .contains("workforce");
        assertThat(semanticStore.lastTopK).isEqualTo(10);
    }

    @Test
    void doesNotBlockWhenSimilarityBelowBiasThreshold() {
        semanticStore.candidates = List.of(
                new SemanticMatch("young energetic team", "age bias", SemanticCategory.BIAS, 0.60)
        );

        assertThat(validator.validate("some neutral hiring text", context)).isEmpty();
    }

    @Test
    void usesHigherThresholdForToxicity() {
        semanticStore.candidates = List.of(
                new SemanticMatch("mild insult", "toxic phrase", SemanticCategory.TOXICITY, 0.72)
        );

        assertThat(validator.validate("mild insult paraphrase", context)).isEmpty();
    }

    @Test
    void semanticViolationsPropagateToEngineResult() {
        semanticStore.candidates = List.of(
                new SemanticMatch("elite institutes only", "education bias", SemanticCategory.BIAS, 0.80)
        );

        var engine = new GuardrailEngine(props, List.of(validator), null);

        GuardrailResult result = engine.evaluate(
                "We prefer applicants who recently entered the workforce",
                context);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getViolations()).anyMatch(v -> "SEMANTIC_BIAS".equals(v.code()));
        assertThat(result.isBiasBlocked()).isTrue();
    }



}
