package com.gridynamics.forge.guardrail.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard evaluate request body for Postman and HTTP clients.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GuardrailEvaluateRequest(
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
