package com.gridynamics.forge.guardrail.report;

import com.gridynamics.forge.guardrail.model.Violation;
import com.gridynamics.forge.guardrail.model.ViolationSeverity;
import com.gridynamics.forge.guardrail.web.ModerationViolationDto;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Normalizes raw validator output into a production-style moderation API response.
 */
public final class ModerationReportFormatter {

    private ModerationReportFormatter() {
    }

    public static FormattedReport format(List<Violation> rawViolations,
                                         Duration processingTime) {
        List<ModerationViolationDto> violations = normalizeViolations(rawViolations);
        long hardCount = violations.stream()
                .filter(v -> v.severity() == ViolationSeverity.HARD)
                .count();

        String violationCode = hardCount > 1
                ? "MULTIPLE_POLICY_VIOLATIONS"
                : violations.stream()
                .filter(v -> v.severity() == ViolationSeverity.HARD)
                .map(ModerationViolationDto::code)
                .findFirst()
                .orElse(violations.isEmpty() ? "GUARDRAIL_BLOCKED" : violations.get(0).code());

        String message = hardCount > 1
                ? "Prompt blocked due to multiple high-risk policy violations"
                : violations.isEmpty()
                    ? "Prompt blocked by guardrail policy"
                    : violations.get(0).message();

        int riskScore = computeRiskScorePercent(violations);

        List<String> details = violations.stream()
                .flatMap(v -> v.matches().stream())
                .distinct()
                .toList();

        return new FormattedReport(
                false,
                true,
                violationCode,
                message,
                violations,
                details,
                riskScore,
                processingTime
        );
    }

    private static List<ModerationViolationDto> normalizeViolations(List<Violation> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        List<ModerationViolationDto> normalized = new ArrayList<>();
        for (Violation violation : raw) {
            if ("OUTPUT_SECRET_LEAK".equals(violation.code())
                    || "OUTPUT_CONTENT".equals(violation.detector())) {
                continue;
            }
            normalized.add(toDto(violation));
        }
        return List.copyOf(normalized);
    }

    private static ModerationViolationDto toDto(Violation v) {
        double scorePercent = v.score() != null
                ? (v.score() <= 1.0 ? v.score() * 100.0 : v.score())
                : defaultScorePercent(v);
        double confidence = v.confidence() != null ? v.confidence() : defaultConfidence(v);

        return new ModerationViolationDto(
                mapCode(v.code()),
                v.message(),
                v.severity(),
                mapDetector(v.detector()),
                v.matches(),
                v.timestamp(),
                Math.round(scorePercent * 10.0) / 10.0,
                Math.round(confidence * 100.0) / 100.0,
                mapCategory(v),
                v.action()
        );
    }

    private static String mapCode(String code) {
        return code;
    }

    private static String mapDetector(String detector) {
        return detector;
    }

    private static String mapCategory(Violation v) {
        if (v.category() != null && !v.category().isBlank()) {
            return switch (v.category().toLowerCase()) {
                case "gender"      -> "GENDER_BIAS";
                case "nationality" -> "NATIONALITY_BIAS";
                case "religion"    -> "RELIGION_BIAS";
                case "caste"       -> "CASTE_BIAS";
                case "age"         -> "AGE_BIAS";
                case "disability"  -> "DISABILITY_BIAS";
                case "sexuality"   -> "SEXUALITY_BIAS";
                case "education"   -> "EDUCATION_BIAS";
                case "appearance", "marital_status", "custom" -> "DISCRIMINATION";
                case "jailbreak", "override", "exfiltration", "encoding" -> "JAILBREAK";
                case "cyber_abuse", "violence", "hate_speech", "illegal_activities", "self_harm" -> "TOXICITY";
                case "secrets", "aws", "auth", "openai", "crypto", "database" -> "SECRETS";
                case "prompt_injection", "toxicity" -> v.category().toUpperCase();
                default -> v.category().toUpperCase();
            };
        }
        return switch (v.detector()) {
            case "BIAS" -> "DISCRIMINATION";
            case "PROMPT_INJECTION" -> "JAILBREAK";
            case "PII" -> "PII";
            case "TOXICITY" -> "TOXICITY";
            case "SECRET_SCANNER" -> "SECRETS";
            case "SEMANTIC" -> mapSemanticCode(v.code());
            default -> "POLICY";
        };
    }

    private static String mapSemanticCode(String code) {
        if (code == null) {
            return "POLICY";
        }
        if (code.startsWith("SEMANTIC_BIAS")) {
            return "DISCRIMINATION";
        }
        if (code.startsWith("SEMANTIC_INJECTION") || code.startsWith("SEMANTIC_JAILBREAK")) {
            return "JAILBREAK";
        }
        if (code.startsWith("SEMANTIC_TOXICITY")) {
            return "TOXICITY";
        }
        return "POLICY";
    }

    private static double defaultScorePercent(Violation v) {
        return switch (v.severity()) {
            case HARD -> 90.0;
            case SOFT -> 80.0;
            case WARN -> 60.0;
        };
    }

    private static double defaultConfidence(Violation v) {
        return switch (v.severity()) {
            case HARD -> 0.95;
            case SOFT -> 0.90;
            case WARN -> 0.75;
        };
    }

    private static int computeRiskScorePercent(List<ModerationViolationDto> violations) {
        if (violations == null || violations.isEmpty()) {
            return 0;
        }
        return (int) Math.min(100, Math.round(violations.stream()
                .map(ModerationViolationDto::score)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0)));
    }

    public record FormattedReport(
            boolean allowed,
            boolean fallbackRequired,
            String violationCode,
            String message,
            List<ModerationViolationDto> violations,
            List<String> details,
            int riskScore,
            Duration processingTime
    ) {
    }
}
