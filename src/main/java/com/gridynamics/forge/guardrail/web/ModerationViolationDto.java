package com.gridynamics.forge.guardrail.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gridynamics.forge.guardrail.model.ValidationAction;
import com.gridynamics.forge.guardrail.model.ViolationSeverity;

import java.time.Instant;
import java.util.List;

/**
 * API-friendly violation view with 0–100 risk scores for Postman/clients.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModerationViolationDto(
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
}
