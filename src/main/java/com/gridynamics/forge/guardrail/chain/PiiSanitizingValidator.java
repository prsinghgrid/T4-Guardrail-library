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
        // A 16-digit card number in XXXX-XXXX-XXXX-XXXX format looks like an
        // international phone number to the loose PHONE_INTL pattern.  Suppress
        // phone detections when a full card match already covers the same digits.
        if (detectedTypes.contains("CREDIT_CARD")) {
            detectedTypes.remove("PHONE_IN");
            detectedTypes.remove("PHONE_INTL");
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

        // CREDIT_CARD must come before AADHAAR so that a 16-digit card number in
        // "XXXX XXXX XXXX XXXX" format is redacted before the Aadhaar pattern tries
        // to consume its first 12 digits as a false Aadhaar match.
        //
        // Uses a broad first-group of [1-9]\d{3} to cover all card networks:
        //   Visa (4xxx), Mastercard (51-55 and 2221-2720), Amex (34/37),
        //   Discover (6011/65xx), RuPay (6xxx, 81-82), Maestro (67xx),
        //   UnionPay (62xx), and any other 16-digit card format.
        // Spaces or hyphens between 4-digit groups are allowed (e.g. "6788 9980 9880 7979").
        m.put("CREDIT_CARD", pr(
            "\\b[1-9]\\d{3}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}\\b",
            0, "[CARD_REDACTED]"));

        // AADHAAR: 12 digits (4-4-4), optional space/hyphen separators.
        // Two guards prevent false positives inside 16-digit card numbers:
        //   Lookbehind (?<!\\d[\\s\\-]) — don't match if immediately preceded by "digit + separator",
        //   which means we are mid-sequence (e.g. matching digits 5-16 of a credit card).
        //   Lookahead (?![\\s\\-]?\\d{4}\\b) — don't match if followed by another 4-digit group,
        //   which means the 12 digits are actually the first 12 of a 16-digit card.
        m.put("AADHAAR", pr(
            "(?<!\\d[\\s\\-])\\b[2-9]\\d{3}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}(?![\\s\\-]?\\d{4}\\b)\\b",
            0, "[AADHAAR_REDACTED]"));

        m.put("PAN", pr(
            "\\b[A-Z]{5}\\d{4}[A-Z]\\b", 0, "[PAN_REDACTED]"));
        m.put("EMAIL", pr(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}",
            Pattern.CASE_INSENSITIVE, "[EMAIL_REDACTED]"));
        m.put("PHONE_SPOKEN", pr(
            TextNormalizationUtil.SPOKEN_DIGIT_RUN_PATTERN.pattern(),
            0, "[PHONE_REDACTED]"));

        // Indian mobile: 10 digits starting with 6-9, with optional +91 prefix.
        // Allows any spacing/hyphen between digit pairs (e.g. "91 52 34 25 65",
        // "98765 43210", "+91-98765-43210") — each digit may be separated by one
        // optional space or hyphen from its neighbour.
        m.put("PHONE_IN", pr(
            "(?:\\+?91[\\-\\s]?)?[6-9](?:[\\-\\s]?\\d){9}(?!\\d)",
            0, "[PHONE_REDACTED]"));
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
