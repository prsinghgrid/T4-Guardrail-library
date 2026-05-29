package com.gridynamics.forge.guardrail.chain;

import com.gridynamics.forge.guardrail.GuardrailContext;
import com.gridynamics.forge.guardrail.model.Violation;

import java.util.List;

/**
 * Contract for a single step in the guardrail validation chain.
 *
 * <p>Implementations must be thread-safe and stateless.
 *
 * @see SanitizingValidator
 */
public interface GuardrailValidator {

    /** Unique machine-readable name (e.g. {@code "BIAS"}, {@code "PII"}). */
    String name();

    /**
     * Execution order — lower values run first.
     * <ul>
     *   <li>0–99   : structural checks (length)</li>
     *   <li>100–199 : hard-block detectors (bias, injection)</li>
     *   <li>200–299 : sanitisers (PII)</li>
     *   <li>300–399 : soft warnings (toxicity)</li>
     *   <li>900+    : custom / team-specific</li>
     * </ul>
     */
    int order();

    /**
     * Validate the text and return any violations found.
     *
     * @param text    current prompt (may be modified by earlier validators)
     * @param context pipeline context
     * @return violations (empty = no issues)
     */
    List<Violation> validate(String text, GuardrailContext context);

    /** Whether this validator is enabled. Default {@code true}. */
    default boolean isEnabled() { return true; }
}
