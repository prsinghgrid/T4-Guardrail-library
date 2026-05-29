package com.gridynamics.forge.guardrail.chain;

import com.gridynamics.forge.guardrail.config.GuardrailProperties;
import com.gridynamics.forge.guardrail.embedding.EmbeddingProvider;
import com.gridynamics.forge.guardrail.semantic.PgVectorSemanticStore;
import com.gridynamics.forge.guardrail.semantic.SemanticMatch;

import java.util.List;

/** Shared stubs for semantic validator tests. */
public final class SemanticValidatorTestSupport {

    private SemanticValidatorTestSupport() {}

    public static SemanticValidator enabledStub(GuardrailProperties props) {
        props.getSemantic().setEnabled(true);
        return new SemanticValidator(props, new StubEmbeddingProvider(), new StubSemanticStore(), null);
    }

    static final class StubEmbeddingProvider implements EmbeddingProvider {
        @Override
        public float[] embed(String text) {
            return new float[] {0.1f, 0.2f, 0.3f};
        }

        @Override
        public int dimensions() {
            return 3;
        }
    }

    static class StubSemanticStore extends PgVectorSemanticStore {
        List<SemanticMatch> candidates = List.of();
        int lastTopK;

        StubSemanticStore() {
            super(null, "test");
        }

        @Override
        public List<SemanticMatch> findNearest(float[] embedding, int topK) {
            lastTopK = topK;
            return candidates;
        }
    }
}
