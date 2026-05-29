package com.gridynamics.forge.guardrail.chain;

/**
 * A validator that also transforms/sanitises the prompt text.
 * The engine calls {@link #sanitise(String)} after {@link #validate}
 * to obtain the cleaned version.
 */
public interface SanitizingValidator extends GuardrailValidator {

    /** Return a sanitised copy of the input. Must be idempotent. */
    String sanitise(String text);
}
