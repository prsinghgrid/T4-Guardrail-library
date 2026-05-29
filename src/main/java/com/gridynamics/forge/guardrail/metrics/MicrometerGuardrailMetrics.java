package com.gridynamics.forge.guardrail.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Micrometer-backed metrics for Spring Actuator dashboards.
 */
public final class MicrometerGuardrailMetrics implements GuardrailMetrics {

    private final MeterRegistry registry;
    private final Map<String, Timer> validatorTimers = new ConcurrentHashMap<>();
    private final Timer pipelineTimer;
    private final Counter blockedCounter;
    private final Counter allowedCounter;

    public MicrometerGuardrailMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.pipelineTimer = Timer.builder("guardrail.pipeline.latency").register(registry);
        this.blockedCounter = Counter.builder("guardrail.requests.blocked").register(registry);
        this.allowedCounter = Counter.builder("guardrail.requests.allowed").register(registry);
    }

    @Override
    public void recordValidatorLatency(String validatorName, Duration duration) {
        validatorTimers.computeIfAbsent(validatorName, name ->
                Timer.builder("guardrail.validator.latency").tag("validator", name).register(registry)
        ).record(duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordPipelineLatency(String featureType, Duration duration) {
        Timer.builder("guardrail.pipeline.latency")
                .tag("feature", safeTag(featureType))
                .register(registry)
                .record(duration.toNanos(), TimeUnit.NANOSECONDS);
        pipelineTimer.record(duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordBlocked(String featureType, String reason) {
        Counter.builder("guardrail.requests.blocked")
                .tag("feature", safeTag(featureType))
                .tag("reason", safeTag(reason))
                .register(registry)
                .increment();
        blockedCounter.increment();
    }

    @Override
    public void recordAllowed(String featureType) {
        Counter.builder("guardrail.requests.allowed")
                .tag("feature", safeTag(featureType))
                .register(registry)
                .increment();
        allowedCounter.increment();
    }

    private static String safeTag(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
