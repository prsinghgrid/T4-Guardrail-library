package com.gridynamics.forge.guardrail.metrics;

import java.time.Duration;

/**
 * Observability hooks for guardrail pipeline stages.
 *
 * <p>No-op by default. When Micrometer is on the classpath and a {@code MeterRegistry}
 * bean exists, {@link MicrometerGuardrailMetrics} records timers and counters.
 */
public interface GuardrailMetrics {

    GuardrailMetrics NOOP = new GuardrailMetrics() { };

    default void recordValidatorLatency(String validatorName, Duration duration) { }

    default void recordPipelineLatency(String featureType, Duration duration) { }

    default void recordBlocked(String featureType, String reason) { }

    default void recordAllowed(String featureType) { }

    /**
     * Increments a per-violation-code counter tagged by feature type.
     * Enables dashboards to track e.g. BIAS_GENDER vs PROMPT_INJECTION_BLOCKED over time.
     */
    default void recordViolation(String violationCode, String featureType) { }

}
