package com.gridynamics.forge.guardrail.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Structured error response returned when a guardrail violation is caught.
 *
 * <p>Includes the full aggregated moderation report from all validators that executed,
 * formatted for production API consumers (Postman, BFFs, etc.).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GuardrailErrorResponse(
    Instant timestamp,
    int status,
    String error,
    Boolean allowed,
    Boolean fallbackRequired,
    String violationCode,
    String message,
    List<String> details,
    List<ModerationViolationDto> violations,
    Integer riskScore,
    Duration processingTime
) {
    /** Backward-compatible constructor without aggregated report fields. */
    public GuardrailErrorResponse(Instant timestamp, int status, String error,
                                   String violationCode, String message, List<String> details) {
        this(timestamp, status, error, false, true, violationCode, message, details, null, null, null);
    }
}
