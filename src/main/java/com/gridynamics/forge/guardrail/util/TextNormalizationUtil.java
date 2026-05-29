package com.gridynamics.forge.guardrail.util;

import java.text.Normalizer;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Normalizes obfuscated or adversarial text before guardrail validation.
 *
 * <p>Applied upstream of regex and fuzzy matchers to reduce bypass attempts
 * such as leetspeak ({@code k1ll}), symbol substitution ({@code h@ck}),
 * and excessive character repetition ({@code kiiiiiill}).
 *
 * <p>Leetspeak substitution is applied conservatively to avoid corrupting
 * technical tokens such as {@code base64} or credential strings containing digits.
 */
public final class TextNormalizationUtil {

    private static final String DIGIT_WORD_ALT =
            "zero|one|two|three|four|five|six|seven|eight|nine";

    /** Four or more consecutive English digit words (obfuscated phone numbers). */
    public static final Pattern SPOKEN_DIGIT_RUN_PATTERN = Pattern.compile(
            "(?i)(?:(?:" + DIGIT_WORD_ALT + ")\\s+){3,}(?:" + DIGIT_WORD_ALT + ")");

    private static final Map<Character, Character> LEET_MAP = Map.ofEntries(
            Map.entry('0', 'o'),
            Map.entry('1', 'i'),
            Map.entry('3', 'e'),
            Map.entry('@', 'a'),
            Map.entry('$', 's'),
            Map.entry('!', 'i')
    );

    private TextNormalizationUtil() {
    }

    /**
     * Converts common leetspeak substitutions to plain letters.
     *
     * <p>Digit substitutions are skipped inside numeric runs (e.g. {@code 64}
     * in {@code base64}) to prevent false rewrites of technical content.
     */
    public static String normalizeLeetspeak(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char lower = Character.toLowerCase(c);
            Character mapped = LEET_MAP.get(lower);
            if (mapped != null && shouldSubstitute(text, i, lower)) {
                sb.append(mapped);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Collapses irregular whitespace and strips zero-width characters.
     */
    public static String normalizeWhitespace(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        return text
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Decomposes Unicode compatibility characters and removes combining marks.
     */
    public static String normalizeUnicode(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        return Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }

    /**
     * Reduces stretched characters ({@code kiiiiiill} → {@code kiill}) while
     * preserving at least two repetitions for legitimate words like {@code book}.
     */
    public static String normalizeRepeatedCharacters(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        return text.replaceAll("(.)\\1{2,}", "$1$1");
    }

    /**
     * Runs structural normalization without leetspeak (safe for injection/secret patterns).
     * Also normalizes word-spelled punctuation and spelled-out digits to catch obfuscated PII.
     */
    public static String normalizeAll(String text) {
        if (text == null) {
            return "";
        }
        String result = normalizeUnicode(text);
        result = normalizeWhitespace(result);
        result = normalizeRepeatedCharacters(result);
        result = normalizeSpelledOutDigits(result);
        result = normalizeWordSpelledPunctuation(result);
        return result;
    }

    /**
     * Runs the full normalization pipeline including leetspeak decoding.
     */
    public static String normalizeAllWithLeetspeak(String text) {
        return normalizeLeetspeak(normalizeAll(text));
    }

    /**
     * Converts word-spelled punctuation to their symbol equivalents to detect
     * obfuscated contact info such as:
     * {@code "john dot doe at gmail dot com"} → {@code "john.doe@gmail.com"}
     * {@code "call me at nine dash eight"} → unchanged (not in email context)
     *
     * <p>Only rewrites "at" → "@" when surrounded by non-space tokens (email context).
     * Only rewrites "dot" → "." between word tokens (not at sentence boundaries).
     */
    public static String normalizeWordSpelledPunctuation(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        // "word dot word" → "word.word"  (catches obfuscated domains and emails)
        String result = text.replaceAll(
                "(?<=\\S)\\s+(?:dot|DOT)\\s+(?=\\S)", ".");
        // "word at word" → "word@word"  (catches obfuscated emails)
        result = result.replaceAll(
                "(?<=\\S)\\s+(?:at|AT)\\s+(?=\\S)", "@");
        // "word dash word" / "word hyphen word" → "word-word"
        result = result.replaceAll(
                "(?<=\\S)\\s+(?:dash|hyphen|DASH|HYPHEN)\\s+(?=\\S)", "-");
        return result;
    }

    public static String redactSpokenDigitRuns(String text, String replacement) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        return SPOKEN_DIGIT_RUN_PATTERN.matcher(text).replaceAll(replacement);
    }

    public static String normalizeSpelledOutDigits(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        Map<String, Character> digitWords = Map.ofEntries(
                Map.entry("zero",  '0'), Map.entry("one",   '1'),
                Map.entry("two",   '2'), Map.entry("three", '3'),
                Map.entry("four",  '4'), Map.entry("five",  '5'),
                Map.entry("six",   '6'), Map.entry("seven", '7'),
                Map.entry("eight", '8'), Map.entry("nine",  '9')
        );

        Matcher m = SPOKEN_DIGIT_RUN_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String run = m.group();
            StringBuilder digits = new StringBuilder();
            for (String token : run.trim().split("\\s+")) {
                Character d = digitWords.get(token.toLowerCase());
                if (d != null) digits.append(d);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(digits.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static boolean shouldSubstitute(String text, int index, char lower) {
        if (!LEET_MAP.containsKey(lower)) {
            return false;
        }
        if (!Character.isDigit(lower)) {
            return true;
        }
        if (!isInShortObfuscatedToken(text, index)) {
            return false;
        }
        boolean prevDigit = index > 0 && Character.isDigit(text.charAt(index - 1));
        boolean nextDigit = index < text.length() - 1 && Character.isDigit(text.charAt(index + 1));
        return !prevDigit || !nextDigit || isObfuscatedDigitSuffix(text, index);
    }

    private static boolean isInShortObfuscatedToken(String text, int index) {
        int start = index;
        int end = index;
        while (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) {
            start--;
        }
        while (end < text.length() - 1 && Character.isLetterOrDigit(text.charAt(end + 1))) {
            end++;
        }
        return (end - start + 1) <= 5;
    }

    private static boolean isObfuscatedDigitSuffix(String text, int index) {
        int start = index;
        while (start > 0 && Character.isDigit(text.charAt(start - 1))) {
            start--;
        }
        if (start == 0 || !Character.isLetter(text.charAt(start - 1))) {
            return false;
        }
        int end = index;
        while (end < text.length() - 1 && Character.isDigit(text.charAt(end + 1))) {
            end++;
        }
        return (end - start + 1) <= 2;
    }
}
