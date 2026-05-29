package com.gridynamics.forge.guardrail.util;

import com.gridynamics.forge.guardrail.GuardrailContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptInputResolverTest {

    private static final GuardrailContext CTX = GuardrailContext.of("TEST", "T1");

    @Test
    void returnsPlainTextUnchanged() {
        assertThat(PromptInputResolver.resolve("hire only young engineers", CTX).prompt())
                .isEqualTo("hire only young engineers");
    }

    @Test
    void extractsPromptFromJsonEnvelope() {
        String json = """
                {"prompt":"We only consider candidates from top-tier universities.","featureType":"HIRING","teamId":"t1"}
                """;
        var resolved = PromptInputResolver.resolve(json, CTX);
        assertThat(resolved.prompt()).isEqualTo("We only consider candidates from top-tier universities.");
        assertThat(resolved.context().featureType()).isEqualTo("HIRING");
        assertThat(resolved.context().teamId()).isEqualTo("t1");
    }

    @Test
    void extractsPromptWithWhitespaceAroundJson() {
        String wrapped = "  {\"prompt\":\"state school graduates are not a fit\"}  ";
        assertThat(PromptInputResolver.resolve(wrapped, CTX).prompt())
                .isEqualTo("state school graduates are not a fit");
    }
}
