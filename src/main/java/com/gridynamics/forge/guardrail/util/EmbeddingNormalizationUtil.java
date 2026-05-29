package com.gridynamics.forge.guardrail.util;

/**
 * L2-normalises embedding vectors so cosine distance ({@code <=>}) equals
 * one minus cosine similarity for pgvector queries.
 */
public final class EmbeddingNormalizationUtil {

    private static final double MIN_NORM = 1e-10;

    private EmbeddingNormalizationUtil() {}

    /**
     * Returns an L2-normalised copy of {@code vector}. Already-normalised vectors
     * are re-normalised (idempotent within floating-point tolerance).
     */
    public static float[] normalize(float[] vector) {
        if (vector == null || vector.length == 0) {
            return vector;
        }
        double norm = 0.0;
        for (float v : vector) {
            norm += (double) v * v;
        }
        norm = Math.sqrt(norm);
        if (norm < MIN_NORM) {
            return vector.clone();
        }
        float[] result = new float[vector.length];
        float normF = (float) norm;
        for (int i = 0; i < vector.length; i++) {
            result[i] = vector[i] / normF;
        }
        return result;
    }
}
