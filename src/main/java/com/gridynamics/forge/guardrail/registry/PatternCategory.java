package com.gridynamics.forge.guardrail.registry;

/**
 * High-level pattern groupings shared across validators.
 */
public enum PatternCategory {
    TOXICITY,
    INJECTION,
    PII,
    BIAS,
    OUTPUT_LEAKAGE,
    SECRETS
}
