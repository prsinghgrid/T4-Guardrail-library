package com.gridynamics.forge.guardrail.embedding;

/**
 * Contract for generating dense text embeddings locally.
 *
 * <p>Implementations must be thread-safe (sessions are shared across requests)
 * and must produce L2-normalized vectors of the same dimension every call.
 */
public interface EmbeddingProvider {

    /**
     * Embed the supplied text and return an L2-normalised float vector.
     *
     * @param text input string (already PII-sanitised by the time this is called)
     * @return L2-normalised embedding, never {@code null}
     */
    float[] embed(String text);

    /**
     * Dimensionality of the produced vectors (e.g. 384 for bge-small-en).
     */
    int dimensions();

    /**
     * Human-readable provider name used in logs and metrics.
     */
    default String name() {
        return getClass().getSimpleName();
    }

    /**
     * Returns {@code false} if the provider could not initialise its model.
     * The {@link com.gridynamics.forge.guardrail.chain.SemanticValidator} respects
     * this flag and skips semantic checks when {@code false}.
     */
    default boolean isAvailable() {
        return true;
    }
}
