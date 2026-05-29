package com.gridynamics.forge.guardrail.model;

/**
 * Severity levels for guardrail violations.
 *
 * <ul>
 *   <li>{@code HARD} — request is blocked; the prompt must not reach the LLM.</li>
 *   <li>{@code SOFT} — the prompt is sanitised/modified but still forwarded.</li>
 *   <li>{@code WARN} — informational; logged but no mutation.</li>
 * </ul>
 */
public enum ViolationSeverity {
    HARD,
    SOFT,
    WARN
}
