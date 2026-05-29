package com.gridynamics.forge.guardrail.exception;

import com.gridynamics.forge.guardrail.model.Violation;
import com.gridynamics.forge.guardrail.report.ModerationReportFormatter;

import java.time.Duration;
import java.util.List;

/**
 * Thrown when a guardrail detects a hard violation that must block processing.
 * Consumers should catch this and return an appropriate HTTP 422 / fallback response.
 *
 * <p>{@link #getViolations()} contains the full moderation report from every validator
 * that ran in the pipeline, not only the first hard match.
 */
public class GuardrailViolationException extends RuntimeException {

    private final String violationCode;
    private final List<String> details;
    private final List<Violation> violations;
    private final Duration processingTime;
    private final ModerationReportFormatter.FormattedReport formattedReport;

    public GuardrailViolationException(String message, String violationCode, List<String> details) {
        super(message);
        this.violationCode = violationCode;
        this.details = details != null ? List.copyOf(details) : List.of();
        this.violations = List.of();
        this.processingTime = null;
        this.formattedReport = null;
    }

    public GuardrailViolationException(String message, String violationCode,
                                        List<String> details, List<Violation> violations) {
        this(message, violationCode, details, violations, null);
    }

    public GuardrailViolationException(String message, String violationCode,
                                        List<String> details, List<Violation> violations,
                                        Duration processingTime) {
        super(message);
        this.violations = violations != null ? List.copyOf(violations) : List.of();
        this.processingTime = processingTime;
        this.formattedReport = violations.isEmpty()
                ? null
                : ModerationReportFormatter.format(violations, processingTime);
        this.violationCode = formattedReport != null ? formattedReport.violationCode() : violationCode;
        this.details = formattedReport != null
                ? formattedReport.details()
                : (details != null ? List.copyOf(details) : List.of());
    }

    /**
     * Builds an exception from the complete validator scan (production moderation report).
     */
    public static GuardrailViolationException fromAggregatedReport(List<Violation> allViolations) {
        return fromAggregatedReport(allViolations, null);
    }

    public static GuardrailViolationException fromAggregatedReport(
            List<Violation> allViolations, Duration processingTime) {

        List<Violation> report = allViolations != null ? List.copyOf(allViolations) : List.of();
        ModerationReportFormatter.FormattedReport formatted = ModerationReportFormatter.format(
                report, processingTime);

        return new GuardrailViolationException(
                formatted.message(),
                formatted.violationCode(),
                formatted.details(),
                report,
                processingTime
        );
    }

    public String getViolationCode()      { return violationCode; }
    public List<String> getDetails()      { return details; }
    public List<Violation> getViolations(){ return violations; }
    public Duration getProcessingTime()   { return processingTime; }
    public ModerationReportFormatter.FormattedReport getFormattedReport() { return formattedReport; }
}
