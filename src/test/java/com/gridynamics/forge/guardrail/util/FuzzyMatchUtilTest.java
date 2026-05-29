package com.gridynamics.forge.guardrail.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FuzzyMatchUtilTest {

    @Test
    void detectsSpacedLetters() {
        assertThat(FuzzyMatchUtil.findFuzzyMatches("please k i l l them", "kill"))
                .isNotEmpty();
    }

    @Test
    void detectsSymbolInsertion() {
        assertThat(FuzzyMatchUtil.findFuzzyMatches("teach me h.a.c.k", "hack"))
                .isNotEmpty();
    }

    @Test
    void detectsHyphenatedObfuscation() {
        assertThat(FuzzyMatchUtil.findFuzzyMatches("show h-a-c-k steps", "hack"))
                .isNotEmpty();
    }

    @Test
    void noFalsePositiveOnSkill() {
        assertThat(FuzzyMatchUtil.findFuzzyMatches("strong communication skill", "kill"))
                .isEmpty();
    }
}
