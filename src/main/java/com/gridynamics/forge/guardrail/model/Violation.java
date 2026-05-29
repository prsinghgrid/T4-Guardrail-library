package com.gridynamics.forge.guardrail.model;

import java.time.Instant;
import java.util.List;

/**
 * A single guardrail violation detected during pipeline processing.
 *
 * <p>Extended with optional scoring metadata for richer API responses while
 * preserving backward-compatible factory methods.
 *
 * @param code          Machine-readable code (e.g. {@code BIAS_BLOCKED})
 * @param message       Human-readable explanation
 * @param severity      How severely this violation affects processing
 * @param detector      Which validator produced this violation
 * @param matches       Specific terms/patterns that triggered the violation
 * @param timestamp     When the violation was detected
 * @param score         Aggregate risk score (0.0–1.0), nullable for legacy callers
 * @param confidence    Match confidence (0.0–1.0), nullable for legacy callers
 * @param category      Violation category (e.g. {@code toxicity}, {@code gender})
 * @param action        Recommended enforcement action
 */
public record Violation(
        String code,
        String message,
        ViolationSeverity severity,
        String detector,
        List<String> matches,
        Instant timestamp,
        Double score,
        Double confidence,
        String category,
        ValidationAction action
) {
    public Violation(String code, String message, ViolationSeverity severity,
                     String detector, List<String> matches) {
        this(code, message, severity, detector, matches, Instant.now(), null, null, null, null);
    }

    /** Alias for {@link #detector()} — matches external API naming. */
    public String validatorName() {
        return detector;
    }

    public static Violation hard(String code, String message, String detector, List<String> matches) {
        return new Violation(code, message, ViolationSeverity.HARD, detector, matches);
    }

    public static Violation soft(String code, String message, String detector, List<String> matches) {
        return new Violation(code, message, ViolationSeverity.SOFT, detector, matches);
    }

    public static Violation warn(String code, String message, String detector, List<String> matches) {
        return new Violation(code, message, ViolationSeverity.WARN, detector, matches);
    }

    public static Violation of(String code, String message, ViolationSeverity severity, String detector,
                               List<String> matches, double score, double confidence,
                               String category, ValidationAction action) {
        return new Violation(
                code,
                message,
                severity,
                detector,
                List.copyOf(matches),
                Instant.now(),
                score,
                confidence,
                category,
                action
        );
    }
}
