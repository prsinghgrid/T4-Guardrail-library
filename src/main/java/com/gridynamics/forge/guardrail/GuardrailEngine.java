package com.gridynamics.forge.guardrail;

import com.gridynamics.forge.guardrail.chain.GuardrailValidator;
import com.gridynamics.forge.guardrail.chain.OutputContentValidator;
import com.gridynamics.forge.guardrail.chain.SanitizingValidator;
import com.gridynamics.forge.guardrail.config.GuardrailProperties;
import com.gridynamics.forge.guardrail.exception.GuardrailViolationException;
import com.gridynamics.forge.guardrail.metrics.GuardrailMetrics;
import com.gridynamics.forge.guardrail.model.Violation;
import com.gridynamics.forge.guardrail.model.ViolationSeverity;
import com.gridynamics.forge.guardrail.report.ModerationReportFormatter;
import com.gridynamics.forge.guardrail.util.PromptInputResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Central guardrail engine that orchestrates the validation pipeline.
 *
 * <p>Production scan model: <strong>run every validator</strong>, collect every
 * violation into {@link GuardrailContext#scan()}, apply sanitizers, then make a
 * single block/allow decision with a complete report.
 *
 * <pre>
 *   Run ALL validators (no fail-fast)
 *   → Accumulate violations in GuardrailContext
 *   → Apply sanitization steps
 *   → Centralized BLOCK / ALLOW decision
 * </pre>
 */
public class GuardrailEngine {

    private static final Logger log = LoggerFactory.getLogger(GuardrailEngine.class);

    private final GuardrailProperties props;
    private final List<GuardrailValidator> inputValidators;
    private final OutputContentValidator outputValidator;
    private final GuardrailMetrics metrics;

    public GuardrailEngine(GuardrailProperties props,
                           List<GuardrailValidator> inputValidators,
                           OutputContentValidator outputValidator) {
        this(props, inputValidators, outputValidator, GuardrailMetrics.NOOP);
    }

    public GuardrailEngine(GuardrailProperties props,
                           List<GuardrailValidator> inputValidators,
                           OutputContentValidator outputValidator,
                           GuardrailMetrics metrics) {
        this.props = props;
        this.inputValidators = inputValidators.stream()
            .sorted(Comparator.comparingInt(GuardrailValidator::order))
            .toList();
        this.outputValidator = outputValidator;
        this.metrics = metrics != null ? metrics : GuardrailMetrics.NOOP;
        List<String> validatorNames = this.inputValidators.stream()
                .map(GuardrailValidator::name)
                .toList();
        List<String> activeValidatorNames = this.inputValidators.stream()
                .filter(GuardrailValidator::isEnabled)
                .map(GuardrailValidator::name)
                .toList();
        log.info("[GUARDRAIL] Pipeline validators ({} active / {} registered): {}",
                activeValidatorNames.size(), validatorNames.size(), activeValidatorNames);
        if (activeValidatorNames.size() != validatorNames.size()) {
            log.debug("[GUARDRAIL] Registered but inactive validators: {}",
                    validatorNames.stream().filter(n -> !activeValidatorNames.contains(n)).toList());
        }
        warnIfSemanticMisconfigured(activeValidatorNames);
    }

    private void warnIfSemanticMisconfigured(List<String> validatorNames) {
        var semantic = props.getSemantic();
        boolean semanticInPipeline = validatorNames.contains("SEMANTIC");

        if (semantic.isEnabled() && !semanticInPipeline) {
            log.error("[GUARDRAIL] semantic.enabled=true but SEMANTIC validator is missing — "
                    + "check ONNX paths, JDBC datasource, and pgvector seeding");
        }
    }

    /**
     * Runs the full pipeline and returns a complete {@link GuardrailResult}.
     * Hard policy blocks are represented as {@code allowed=false} with all violations attached.
     */
    public GuardrailResult evaluate(String rawPrompt, GuardrailContext context) {
        Instant start = Instant.now();
        PromptInputResolver.Resolved resolved = PromptInputResolver.resolve(rawPrompt, context);
        String prompt = resolved.prompt();
        GuardrailContext ctx = resolved.context();

        log.debug("[GUARDRAIL] Evaluating for feature={} team={}", ctx.featureType(), ctx.teamId());

        GuardrailResult.Builder builder = GuardrailResult.builder()
            .originalLength(prompt.length());

        if (prompt.isBlank()) {
            log.warn("[GUARDRAIL] Empty prompt for feature={}", ctx.featureType());
            ctx.scan().clear();
            return builder
                .sanitisedPrompt("")
                .allowed(false)
                .fallbackRequired(true)
                .addViolation(Violation.hard("EMPTY_PROMPT", "Prompt is empty or null", "ENGINE", List.of()))
                .processingTime(Duration.between(start, Instant.now()))
                .build();
        }

        ctx.scan().clear();
        String working = runValidatorChain(prompt, ctx, true);
        List<Violation> allViolations = ctx.scan().snapshot();
        Duration processingTime = Duration.between(start, Instant.now());

        if (shouldHardBlock(allViolations)) {
            log.error("[GUARDRAIL] HARD BLOCK after full scan — {} total violations, feature={} team={} detectors={}",
                    allViolations.size(), ctx.featureType(), ctx.teamId(), detectorSummary(allViolations));
            metrics.recordBlocked(ctx.featureType(), blockReason(allViolations));
            metrics.recordPipelineLatency(ctx.featureType(), processingTime);
            return applyViolationSummary(builder, working, allViolations, processingTime)
                .allowed(false)
                .fallbackRequired(true)
                .build();
        }

        metrics.recordAllowed(ctx.featureType());
        metrics.recordPipelineLatency(ctx.featureType(), processingTime);

        return applyViolationSummary(builder, working, allViolations, processingTime)
            .allowed(true)
            .fallbackRequired(false)
            .sanitisedLength(working.length())
            .estimatedTokens(estimateTokens(working))
            .build();
    }

    /**
     * Evaluates the prompt and throws {@link GuardrailViolationException} when a hard
     * policy block is detected after the full validator scan completes.
     */
    public GuardrailResult process(String rawPrompt, GuardrailContext context) {
        GuardrailResult result = evaluate(rawPrompt, context);
        if (!result.isAllowed()
                && result.hasHardViolations()
                && rawPrompt != null
                && !rawPrompt.isBlank()) {
            throw GuardrailViolationException.fromAggregatedReport(
                    result.getViolations(),
                    result.getProcessingTime()
            );
        }
        return result;
    }

    /**
     * Runs every enabled validator and collects all violations without short-circuiting.
     * Exposed for tests and diagnostics.
     */
    List<Violation> runFullValidatorScan(String text, GuardrailContext context, boolean recordMetrics) {
        context.scan().clear();
        runValidatorChain(text, context, recordMetrics);
        return context.scan().snapshot();
    }

    public GuardrailResult validateOutput(String llmOutput, GuardrailContext context) {
        if (outputValidator == null || !outputValidator.isEnabled()) {
            return GuardrailResult.builder()
                .sanitisedPrompt(llmOutput != null ? llmOutput : "")
                .allowed(true)
                .originalLength(llmOutput != null ? llmOutput.length() : 0)
                .sanitisedLength(llmOutput != null ? llmOutput.length() : 0)
                .build();
        }

        List<Violation> violations = outputValidator.validate(
            llmOutput != null ? llmOutput : "", context);
        context.recordViolations(violations);

        if (shouldHardBlock(violations)) {
            throw GuardrailViolationException.fromAggregatedReport(violations);
        }

        return GuardrailResult.builder()
            .sanitisedPrompt(llmOutput)
            .allowed(true)
            .fallbackRequired(false)
            .originalLength(llmOutput != null ? llmOutput.length() : 0)
            .sanitisedLength(llmOutput != null ? llmOutput.length() : 0)
            .violations(violations)
            .build();
    }

    /** Ordered validator names in the input pipeline (for tests and diagnostics). */
    public List<String> getInputValidatorNames() {
        return inputValidators.stream().map(GuardrailValidator::name).toList();
    }

    private String runValidatorChain(String text, GuardrailContext context, boolean recordMetrics) {
        String working = text;
        for (GuardrailValidator validator : inputValidators) {
            if (!validator.isEnabled()) {
                continue;
            }

            long validatorStart = System.nanoTime();
            List<Violation> violations = validator.validate(working, context);
            if (recordMetrics) {
                metrics.recordValidatorLatency(validator.name(),
                        Duration.ofNanos(System.nanoTime() - validatorStart));
            }

            context.recordViolations(violations);
            if (!violations.isEmpty()) {
                log.debug("[GUARDRAIL] {} reported {} violation(s)", validator.name(), violations.size());
            }

            if (validator instanceof SanitizingValidator sanitizer) {
                String sanitised = sanitizer.sanitise(working);
                if (!sanitised.equals(working)) {
                    log.info("[GUARDRAIL] Sanitised by {} for feature={}", validator.name(), context.featureType());
                    working = sanitised;
                }
            }
        }
        return working;
    }

    private static GuardrailResult.Builder applyViolationSummary(
            GuardrailResult.Builder builder,
            String working,
            List<Violation> violations,
            Duration processingTime) {

        ModerationReportFormatter.FormattedReport report =
                ModerationReportFormatter.format(violations, processingTime);

        return builder
            .sanitisedPrompt(working)
            .piiStripped(violations.stream().anyMatch(v -> "PII_STRIPPED".equals(v.code())))
            .truncated(violations.stream().anyMatch(v -> "PROMPT_TRUNCATED".equals(v.code())))
            .biasBlocked(hasBiasViolation(violations))
            .injectionBlocked(hasInjectionViolation(violations))
            .toxicityFlagged(hasToxicityViolation(violations))
            .violations(violations)
            .riskScore(violations.isEmpty() ? null : report.riskScore())
            .processingTime(processingTime);
    }

    private static boolean hasBiasViolation(List<Violation> violations) {
        return violations.stream().anyMatch(v ->
                v.severity() == ViolationSeverity.HARD
                        && ("BIAS".equals(v.detector()) || startsWithCode(v, "SEMANTIC_BIAS")));
    }

    private static boolean hasInjectionViolation(List<Violation> violations) {
        return violations.stream().anyMatch(v ->
                v.severity() == ViolationSeverity.HARD
                        && ("PROMPT_INJECTION".equals(v.detector())
                        || startsWithCode(v, "SEMANTIC_INJECTION")
                        || startsWithCode(v, "SEMANTIC_JAILBREAK")));
    }

    private static boolean hasToxicityViolation(List<Violation> violations) {
        return violations.stream().anyMatch(v ->
                v.code().startsWith("TOXICITY") || startsWithCode(v, "SEMANTIC_TOXICITY"));
    }

    private static boolean startsWithCode(Violation violation, String prefix) {
        return violation.code() != null && violation.code().startsWith(prefix);
    }

    private static String blockReason(List<Violation> violations) {
        return violations.stream()
                .filter(v -> v.severity() == ViolationSeverity.HARD)
                .map(Violation::code)
                .findFirst()
                .orElse("HARD_VIOLATION");
    }

    private static boolean shouldHardBlock(List<Violation> allViolations) {
        return allViolations.stream().anyMatch(v -> v.severity() == ViolationSeverity.HARD);
    }

    private static List<String> detectorSummary(List<Violation> violations) {
        return violations.stream().map(Violation::detector).distinct().toList();
    }

    private int estimateTokens(String text) {
        return text == null || text.isEmpty() ? 0 : (int) Math.ceil(text.length() / 4.0);
    }
}
