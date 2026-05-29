package com.gridynamics.forge.guardrail.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EmbeddingNormalizationUtilTest {

    @Test
    void normalizeProducesUnitLengthVector() {
        float[] raw = {3f, 4f};
        float[] normalized = EmbeddingNormalizationUtil.normalize(raw);

        double norm = 0;
        for (float v : normalized) {
            norm += v * v;
        }
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, within(1e-5));
        assertThat(normalized[0]).isCloseTo(0.6f, within(1e-5f));
        assertThat(normalized[1]).isCloseTo(0.8f, within(1e-5f));
    }

    @Test
    void normalizeIsIdempotentWithinTolerance() {
        float[] once = EmbeddingNormalizationUtil.normalize(new float[] {1f, 2f, 3f});
        float[] twice = EmbeddingNormalizationUtil.normalize(once);

        double norm = 0;
        for (float v : twice) {
            norm += v * v;
        }
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, within(1e-5));
    }
}
