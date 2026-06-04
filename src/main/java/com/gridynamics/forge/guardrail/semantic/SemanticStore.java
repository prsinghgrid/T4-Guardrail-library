package com.gridynamics.forge.guardrail.semantic;

import java.util.List;

/**
 * Abstraction over the semantic pattern store.
 * The default implementation is {@link InMemorySemanticStore}; consumers may
 * supply their own bean to swap in a different backend.
 */
public interface SemanticStore {

    /**
     * Returns the {@code topK} nearest seed patterns for the given (pre-normalised)
     * query embedding, ordered by descending cosine similarity.
     * Callers apply per-category thresholds to the returned matches.
     */
    List<SemanticMatch> findNearest(float[] embedding, int topK);
}
