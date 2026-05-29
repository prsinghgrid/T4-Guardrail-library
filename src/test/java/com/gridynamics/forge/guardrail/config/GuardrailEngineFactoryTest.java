package com.gridynamics.forge.guardrail.config;

import com.gridynamics.forge.guardrail.GuardrailEngine;
import com.gridynamics.forge.guardrail.chain.GuardrailValidator;
import com.gridynamics.forge.guardrail.chain.InputLengthValidator;
import com.gridynamics.forge.guardrail.chain.OutputContentValidator;
import com.gridynamics.forge.guardrail.chain.SemanticValidator;
import com.gridynamics.forge.guardrail.chain.SemanticValidatorTestSupport;
import com.gridynamics.forge.guardrail.metrics.GuardrailMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GuardrailEngineFactoryTest {

    @Test
    void omitsSemanticFromPipelineWhenPropertyDisabled() {
        var props = new GuardrailProperties();
        props.getSemantic().setEnabled(false);

        SemanticValidator semantic = SemanticValidatorTestSupport.enabledStub(props);
        props.getSemantic().setEnabled(false);
        ObjectProvider<SemanticValidator> semanticProvider = new StubSemanticProvider(semantic);

        GuardrailEngine engine = GuardrailEngineFactory.build(
                props,
                List.<GuardrailValidator>of(new InputLengthValidator(props), semantic),
                semanticProvider,
                List.of(),
                new OutputContentValidator(props, new com.gridynamics.forge.guardrail.registry.GuardrailPatternRegistry(props)),
                GuardrailMetrics.NOOP);

        assertThat(engine.getInputValidatorNames())
                .containsExactly("INPUT_LENGTH")
                .doesNotContain("SEMANTIC");
    }

    private static final class StubSemanticProvider implements ObjectProvider<SemanticValidator> {
        private final SemanticValidator validator;

        private StubSemanticProvider(SemanticValidator validator) {
            this.validator = validator;
        }

        @Override
        public SemanticValidator getObject(Object... args) {
            return validator;
        }

        @Override
        public SemanticValidator getIfAvailable() {
            return validator;
        }

        @Override
        public SemanticValidator getIfUnique() {
            return validator;
        }

        @Override
        public SemanticValidator getObject() {
            return validator;
        }

        public Stream<SemanticValidator> stream() {
            return Stream.of(validator);
        }
    }
}
