package com.gridynamics.forge.guardrail.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Standard evaluate request body for Postman and HTTP clients.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GuardrailEvaluateRequest(
        @NotBlank(message = "prompt must not be blank")
        @Size(max = 32_000, message = "prompt exceeds maximum allowed length")
        String prompt,
        String featureType,
        String teamId
) {
    public GuardrailEvaluateRequest {
        if (featureType == null || featureType.isBlank()) {
            featureType = "DEFAULT";
        }
        if (teamId == null || teamId.isBlank()) {
            teamId = "T0";
        }
    }
}
