package com.gridynamics.forge.guardrail.util;

import com.gridynamics.forge.guardrail.registry.CategorizedPattern;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared regex and fuzzy matching helpers used by validators.
 */
public final class PatternMatchUtil {

    private PatternMatchUtil() {
    }

    /**
     * Compiles a case-insensitive pattern with word boundaries when the expression
     * does not already specify anchors or boundary markers.
     */
    public static Pattern wordBoundaryPattern(String regex) {
        String trimmed = regex.trim();
        boolean hasBoundary = trimmed.contains("\\b") || trimmed.startsWith("^") || trimmed.endsWith("$");
        String expression = hasBoundary ? trimmed : "\\b(?:%s)\\b".formatted(trimmed);
        return Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
    }

    /**
     * Finds all matches for a compiled pattern in normalized text.
     */
    public static List<String> findMatches(Pattern pattern, String normalizedText) {
        Matcher matcher = pattern.matcher(normalizedText);
        Set<String> hits = new LinkedHashSet<>();
        while (matcher.find()) {
            hits.add(matcher.group());
        }
        return new ArrayList<>(hits);
    }

    /**
     * Matches categorized patterns and returns all hits with metadata preserved.
     */
    public static List<PatternHit> matchCategorized(String text, Iterable<CategorizedPattern> patterns) {
        String normalized = TextNormalizationUtil.normalizeAll(text);
        List<PatternHit> hits = new ArrayList<>();
        for (CategorizedPattern categorized : patterns) {
            List<String> matches = findMatches(categorized.pattern(), normalized);
            if (!matches.isEmpty()) {
                hits.add(new PatternHit(categorized, matches));
            }
        }
        return hits;
    }

    /**
     * A single pattern match with its originating definition.
     */
    public record PatternHit(CategorizedPattern definition, List<String> matchedText) {
    }
}
