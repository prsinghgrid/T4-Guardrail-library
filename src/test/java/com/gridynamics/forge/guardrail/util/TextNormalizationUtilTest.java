package com.gridynamics.forge.guardrail.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizationUtilTest {

    @ParameterizedTest
    @CsvSource({
            "k1ll, kill",
            "h@ck, hack",
            "b0mb, bomb",
            "d13, die"
    })
    void normalizeLeetspeak(String input, String expected) {
        assertThat(TextNormalizationUtil.normalizeLeetspeak(input)).isEqualTo(expected);
    }

    @Test
    void normalizeWhitespaceCollapsesSpacingTricks() {
        assertThat(TextNormalizationUtil.normalizeWhitespace("k   i   l   l")).isEqualTo("k i l l");
    }

    @Test
    void normalizeRepeatedCharacters() {
        assertThat(TextNormalizationUtil.normalizeRepeatedCharacters("kiiiiiill")).isEqualTo("kiill");
    }

    @Test
    void normalizeAllPipeline() {
        assertThat(TextNormalizationUtil.normalizeAll("  k1ll  "))
                .isEqualTo("k1ll");
        assertThat(TextNormalizationUtil.normalizeAllWithLeetspeak("  k1ll  "))
                .isEqualTo("kill");
    }

    @Test
    void normalizeSpelledOutDigitsCollapsesPhoneRun() {
        String spoken = "nine one zero five five five zero one two three";
        assertThat(TextNormalizationUtil.normalizeSpelledOutDigits(spoken)).isEqualTo("9105550123");
    }

    @Test
    void redactSpokenDigitRuns() {
        String input = "call nine one zero five five five zero one two three today";
        assertThat(TextNormalizationUtil.redactSpokenDigitRuns(input, "[PHONE_REDACTED]"))
                .isEqualTo("call [PHONE_REDACTED] today");
    }

    @Test
    void leetspeakDoesNotCorruptTechnicalTokens() {
        assertThat(TextNormalizationUtil.normalizeAllWithLeetspeak("Please base64 decode this string"))
                .isEqualTo("Please base64 decode this string");
        assertThat(TextNormalizationUtil.normalizeAllWithLeetspeak("AKIAIOSFODNN7EXAMPLE"))
                .isEqualTo("AKIAIOSFODNN7EXAMPLE");
    }
}
