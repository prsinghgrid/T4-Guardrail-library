package com.gridynamics.forge.guardrail.semantic;

/**
 * A single pgvector similarity hit returned by {@link PgVectorSemanticStore}.
 *
 * @param patternText  The stored unsafe pattern text (from the DB)
 * @param description  Short human-readable label (e.g. "age bias — younger candidate preference")
 * @param category     Semantic category of the matched pattern
 * @param similarity   Cosine similarity in [0.0, 1.0]; higher = more similar
 */
public record SemanticMatch(
        String patternText,
        String description,
        SemanticCategory category,
        double similarity
) {}
