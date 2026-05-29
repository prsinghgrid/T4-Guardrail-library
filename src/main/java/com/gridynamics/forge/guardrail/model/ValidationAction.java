package com.gridynamics.forge.guardrail.model;

/**
 * Recommended enforcement action derived from a validator's scoring logic.
 */
public enum ValidationAction {
    ALLOW,
    WARN,
    BLOCK,
    SANITIZE
}
