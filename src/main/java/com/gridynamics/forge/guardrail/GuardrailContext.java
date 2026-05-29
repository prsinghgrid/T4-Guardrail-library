package com.gridynamics.forge.guardrail;

import com.gridynamics.forge.guardrail.model.Violation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Context passed through the guardrail pipeline for logging and routing.
 *
 * @param featureType    AI feature invoking the guardrail (e.g. "JD_GENERATION", "CHATBOT")
 * @param teamId         Which team is invoking (e.g. "T1"–"T6")
 * @param correlationId  Distributed trace / HTTP correlation ID
 * @param userRole       Role of the triggering user (e.g. "RECRUITER", "CANDIDATE", "SYSTEM")
 * @param requestId      Unique ID for this guardrail invocation (auto-generated)
 * @param timestamp      When the request was created
 * @param scan           Shared violation accumulator for the current pipeline run
 */
public record GuardrailContext(
    String featureType,
    String teamId,
    String correlationId,
    String userRole,
    String requestId,
    Instant timestamp,
    GuardrailViolationCollector scan
) {
    public GuardrailContext {
        if (featureType == null || featureType.isBlank())
            throw new IllegalArgumentException("featureType must not be blank");
        if (teamId == null || teamId.isBlank())
            throw new IllegalArgumentException("teamId must not be blank");
        if (requestId == null) requestId = UUID.randomUUID().toString();
        if (timestamp == null) timestamp = Instant.now();
        if (scan == null) scan = new GuardrailViolationCollector();
    }

    public static GuardrailContext of(String featureType, String teamId) {
        return new GuardrailContext(featureType, teamId, null, "SYSTEM", null, null, new GuardrailViolationCollector());
    }

    public static GuardrailContext of(String featureType, String teamId,
                                      String correlationId, String userRole) {
        return new GuardrailContext(featureType, teamId, correlationId, userRole, null, null, new GuardrailViolationCollector());
    }

    /** Records violations from a validator into the shared scan accumulator. */
    public void recordViolations(List<Violation> found) {
        scan.addAll(found);
    }
}
