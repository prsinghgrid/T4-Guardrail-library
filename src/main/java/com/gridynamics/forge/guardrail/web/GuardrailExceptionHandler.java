package com.gridynamics.forge.guardrail.web;

import com.gridynamics.forge.guardrail.exception.GuardrailViolationException;
import com.gridynamics.forge.guardrail.report.ModerationReportFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Global exception handler that catches {@link GuardrailViolationException}
 * and returns a structured 422 JSON response instead of a 500 error.
 *
 * <p>Auto-configured when Spring Web is on the classpath.
 * Teams can override by defining their own handler for
 * {@link GuardrailViolationException}.
 */
@RestControllerAdvice
public class GuardrailExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GuardrailExceptionHandler.class);

    @ExceptionHandler(GuardrailViolationException.class)
    public ResponseEntity<GuardrailErrorResponse> handleGuardrailViolation(
            GuardrailViolationException ex) {

        log.warn("[GUARDRAIL] Violation caught: code={} message={}",
            ex.getViolationCode(), ex.getMessage());

        ModerationReportFormatter.FormattedReport report = ex.getFormattedReport();
        if (report == null && !ex.getViolations().isEmpty()) {
            report = ModerationReportFormatter.format(
                    ex.getViolations(), ex.getProcessingTime());
        }

        GuardrailErrorResponse body;
        if (report != null) {
            body = new GuardrailErrorResponse(
                Instant.now(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "Guardrail Violation",
                report.allowed(),
                report.fallbackRequired(),
                report.violationCode(),
                report.message(),
                report.details(),
                report.violations(),
                report.riskScore(),
                report.processingTime()
            );
        } else {
            body = new GuardrailErrorResponse(
                Instant.now(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "Guardrail Violation",
                false,
                true,
                ex.getViolationCode(),
                ex.getMessage(),
                ex.getDetails(),
                null,
                null,
                ex.getProcessingTime()
            );
        }

        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(body);
    }
}
