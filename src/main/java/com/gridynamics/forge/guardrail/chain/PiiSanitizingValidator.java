package com.gridynamics.forge.guardrail.chain;

import com.gridynamics.forge.guardrail.GuardrailContext;
import com.gridynamics.forge.guardrail.config.GuardrailProperties;
import com.gridynamics.forge.guardrail.model.ValidationAction;
import com.gridynamics.forge.guardrail.model.Violation;
import com.gridynamics.forge.guardrail.model.ViolationSeverity;
import com.gridynamics.forge.guardrail.util.TextNormalizationUtil;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Strips PII from prompts before they reach external LLM APIs.
 *
 * <p>Covers: email, phone (Indian + intl), Aadhaar, PAN, credit card,
 * IP address, passport, DOB, SSN, IFSC, and labelled names.
 */
public class PiiSanitizingValidator implements SanitizingValidator {

    /** Common TLD tokens used in word-spelled domain obfuscation. */
    private static final String OBFUSCATED_TLD =
            "com|org|net|edu|gov|io|co|uk|in|de|fr|au|ca|us|info|biz|me|app|dev|ai";

    /** Common stop-words that should not be treated as email local parts. */
    private static final String EMAIL_LOCAL_STOP_WORDS =
            "the|me|at|contact|meet|call|reach|email|mail|office|building|team|send|write";

    /**
     * Word-spelled email obfuscation variants:
     * <ul>
     *   <li>{@code sarah dot jones at outlook dot com}</li>
     *   <li>{@code sarah dot at outlook dot com} ({@code dot at} replaces {@code @})</li>
     *   <li>{@code prsingh at grid dot com}</li>
     * </ul>
     */
    private static final Pattern[] OBFUSCATED_EMAIL_PATTERNS = {
            Pattern.compile("(?i)\\b[\\w]+(?:\\s+(?:dot|DOT)\\s+(?!at\\b)[\\w]+)+\\s+(?:at|AT)\\s+"
                    + "[\\w]+(?:\\s+(?:dot|DOT)\\s+[\\w]+)+"),
            Pattern.compile("(?i)\\b[\\w]+\\s+(?:dot|DOT)\\s+(?:at|AT)\\s+[\\w]+(?:\\s+(?:dot|DOT)\\s+[\\w]+)+"),
            Pattern.compile("(?i)(?<!(?:dot|DOT)\\s)\\b(?!(?:" + EMAIL_LOCAL_STOP_WORDS + ")\\b)[\\w]+"
                    + "\\s+(?:at|AT)\\s+[\\w]+(?:\\s+(?:dot|DOT)\\s+[\\w]+)*\\s+(?:dot|DOT)\\s+"
                    + "(?:" + OBFUSCATED_TLD + ")\\b"),
    };

    private final GuardrailProperties props;
    private final Map<String, PatternReplacement> patterns;

    public PiiSanitizingValidator(GuardrailProperties props) {
        this.props = props;
        this.patterns = buildPatterns();
    }

    @Override public String name()  { return "PII"; }
    @Override public int order()    { return 200; }
    @Override public boolean isEnabled() { return props.getPii().isEnabled(); }

    @Override
    public List<Violation> validate(String text, GuardrailContext context) {
        Set<String> detectedTypes = new LinkedHashSet<>();
        String digitNormalized = TextNormalizationUtil.normalizeSpelledOutDigits(text);
        if (containsObfuscatedEmail(text)) {
            detectedTypes.add("EMAIL");
        }
        for (var entry : patterns.entrySet()) {
            String probe = isNumericPhoneKey(entry.getKey()) ? digitNormalized : text;
            if (entry.getValue().pattern.matcher(probe).find()) {
                detectedTypes.add(entry.getKey());
            }
        }
        if (detectedTypes.isEmpty()) return List.of();
        return List.of(Violation.of(
            "PII_STRIPPED",
            "PII detected and redacted: " + detectedTypes,
            ViolationSeverity.SOFT,
            name(),
            List.copyOf(detectedTypes),
            0.80,
            0.99,
            "PII",
            ValidationAction.SANITIZE
        ));
    }

    @Override
    public String sanitise(String text) {
        String result = redactObfuscatedEmails(text);
        for (var entry : patterns.entrySet()) {
            if (isNumericPhoneKey(entry.getKey())) {
                continue;
            }
            PatternReplacement pr = entry.getValue();
            result = pr.pattern.matcher(result).replaceAll(pr.replacement);
        }
        result = TextNormalizationUtil.redactSpokenDigitRuns(result, "[PHONE_REDACTED]");
        String digitNormalized = TextNormalizationUtil.normalizeSpelledOutDigits(result);
        String withNumericPhones = digitNormalized;
        for (var entry : patterns.entrySet()) {
            if (!isNumericPhoneKey(entry.getKey())) {
                continue;
            }
            PatternReplacement pr = entry.getValue();
            withNumericPhones = pr.pattern.matcher(withNumericPhones).replaceAll(pr.replacement);
        }
        if (!withNumericPhones.equals(digitNormalized)) {
            result = withNumericPhones;
        }
        return sanitiseLabelledNames(result);
    }

    private static boolean isNumericPhoneKey(String key) {
        return "PHONE_IN".equals(key) || "PHONE_INTL".equals(key);
    }

    private static boolean containsObfuscatedEmail(String text) {
        if (text == null) {
            return false;
        }
        for (Pattern pattern : OBFUSCATED_EMAIL_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static String redactObfuscatedEmails(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String result = text;
        for (Pattern pattern : OBFUSCATED_EMAIL_PATTERNS) {
            result = pattern.matcher(result).replaceAll("[EMAIL_REDACTED]");
        }
        return result;
    }

    private String sanitiseLabelledNames(String text) {
        Pattern labelledName = Pattern.compile(
            "(?:name|employee|candidate|applicant|recruiter|manager|supervisor)"
                + "\\s*:\\s*[A-Z][a-z]+(?:\\s[A-Z][a-z]+)+",
            Pattern.CASE_INSENSITIVE
        );
        return labelledName.matcher(text).replaceAll(
            m -> m.group().replaceAll(":\\s*.*", ": [NAME_REDACTED]")
        );
    }

    private static Map<String, PatternReplacement> buildPatterns() {
        Map<String, PatternReplacement> m = new LinkedHashMap<>();

        // Process specific identifiers before broader phone patterns to avoid partial matches.
        m.put("AADHAAR", pr(
            "\\b[2-9]\\d{3}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}\\b", 0, "[AADHAAR_REDACTED]"));
        m.put("PAN", pr(
            "\\b[A-Z]{5}\\d{4}[A-Z]\\b", 0, "[PAN_REDACTED]"));
        m.put("CREDIT_CARD", pr(
            "\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12})\\b",
            0, "[CARD_REDACTED]"));
        m.put("EMAIL", pr(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}",
            Pattern.CASE_INSENSITIVE, "[EMAIL_REDACTED]"));
        m.put("PHONE_SPOKEN", pr(
            TextNormalizationUtil.SPOKEN_DIGIT_RUN_PATTERN.pattern(),
            0, "[PHONE_REDACTED]"));
        m.put("PHONE_IN", pr(
            "(?:\\+91[\\-\\s]?)?[6-9]\\d{9}", 0, "[PHONE_REDACTED]"));
        m.put("PHONE_INTL", pr(
            "\\+?\\d{1,3}[\\-\\s]?\\(?\\d{2,4}\\)?[\\-\\s]?\\d{3,4}[\\-\\s]?\\d{3,4}",
            0, "[PHONE_REDACTED]"));
        m.put("IP_ADDRESS", pr(
            "\\b(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b",
            0, "[IP_REDACTED]"));
        m.put("PASSPORT_IN", pr(
            "\\b[A-Z][1-9]\\d{6}\\b", 0, "[PASSPORT_REDACTED]"));
        m.put("DOB", pr(
            "\\b(?:0[1-9]|[12]\\d|3[01])[/\\-](?:0[1-9]|1[0-2])[/\\-](?:19|20)\\d{2}\\b",
            0, "[DOB_REDACTED]"));
        m.put("SSN", pr(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b", 0, "[SSN_REDACTED]"));
        m.put("IFSC", pr(
            "\\b[A-Z]{4}0[A-Z0-9]{6}\\b", 0, "[IFSC_REDACTED]"));

        return m;
    }

    private static PatternReplacement pr(String regex, int flags, String replacement) {
        return new PatternReplacement(Pattern.compile(regex, flags), replacement);
    }

    private record PatternReplacement(Pattern pattern, String replacement) {}
}
