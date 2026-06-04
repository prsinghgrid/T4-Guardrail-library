package com.gridynamics.forge.guardrail.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional fuzzy matching for spacing tricks, symbol insertion, and stretched characters.
 *
 * <p>Works alongside strict regex patterns — callers enable it via configuration.
 */
public final class FuzzyMatchUtil {

    private FuzzyMatchUtil() {
    }

    /**
     * Builds a regex that tolerates optional non-alphanumeric separators between letters
     * and limited character repetition (e.g. {@code h.a.c.k}, {@code k-i-l-l}).
     */
    public static Pattern buildFuzzyPattern(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return Pattern.compile("(?!)");
        }
        String[] tokens = phrase.trim().split("\\s+");
        StringBuilder regex = new StringBuilder();
        if (tokens.length == 1) {
            regex.append("\\b");
        }
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) {
                regex.append("[\\s\\W_]*+");
            }
            regex.append(buildTokenPattern(tokens[i]));
        }
        if (tokens.length == 1) {
            regex.append("\\b");
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    /**
     * Finds fuzzy matches of a phrase within normalized text.
     */
    public static List<String> findFuzzyMatches(String text, String phrase) {
        if (text == null || phrase == null || phrase.isBlank()) {
            return List.of();
        }
        String normalized = TextNormalizationUtil.normalizeAllWithLeetspeak(text);
        Pattern fuzzy = buildFuzzyPattern(phrase);
        Matcher matcher = fuzzy.matcher(normalized);
        Set<String> hits = new LinkedHashSet<>();
        while (matcher.find()) {
            hits.add(matcher.group());
        }
        return new ArrayList<>(hits);
    }

    private static String buildTokenPattern(String token) {
        StringBuilder tokenPattern = new StringBuilder();
        for (int i = 0; i < token.length(); i++) {
            if (i > 0) {
                tokenPattern.append("[\\s\\W_]*+");
            }
            char c = token.charAt(i);
            // Wrap Pattern.quote() in a non-capturing group so {1,4} quantifies
            // the character itself, not the closing \E of the quote block.
            tokenPattern.append("(?:").append(Pattern.quote(String.valueOf(c))).append("){1,4}");
        }
        return tokenPattern.toString();
    }
}
