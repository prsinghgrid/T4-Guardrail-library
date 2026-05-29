package com.gridynamics.forge.guardrail.registry;

import java.util.regex.Pattern;

/**
 * Immutable definition of a compiled guardrail pattern with scoring metadata.
 *
 * @param pattern    compiled regex (thread-safe, reusable)
 * @param label      human-readable identifier for matchedText reporting
 * @param category   top-level pattern category
 * @param subcategory finer-grained grouping (e.g. {@code gender}, {@code violence})
 * @param weight     contribution to aggregate score (0.0–1.0)
 * @param confidence baseline confidence for this pattern (0.0–1.0)
 */
public record CategorizedPattern(
        Pattern pattern,
        String label,
        PatternCategory category,
        String subcategory,
        double weight,
        double confidence
) {
    public CategorizedPattern {
        if (weight < 0.0 || weight > 1.0) {
            throw new IllegalArgumentException("weight must be between 0.0 and 1.0");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
    }
}
