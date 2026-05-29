package com.gridynamics.forge.guardrail.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticSeedSqlTest {

    private static final List<String> CATEGORY_ORDER = List.of(
            "BIAS", "PROMPT_INJECTION", "JAILBREAK", "TOXICITY"
    );

    @Test
    void seedFileGroupsCategoriesInOrderWithSingleInsertPerCategory() throws IOException {
        String sql = Files.readString(
                Path.of("src/main/resources/db/02_semantic_seed.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).contains("-- ── BIAS ──");
        assertThat(sql).contains("-- ── PROMPT_INJECTION ──");
        assertThat(sql).contains("-- ── JAILBREAK ──");
        assertThat(sql).contains("-- ── TOXICITY ──");

        int biasInserts = countInsertsForCategory(sql, "BIAS");
        int injectionInserts = countInsertsForCategory(sql, "PROMPT_INJECTION");
        int jailbreakInserts = countInsertsForCategory(sql, "JAILBREAK");
        int toxicityInserts = countInsertsForCategory(sql, "TOXICITY");

        assertThat(biasInserts).isEqualTo(1);
        assertThat(injectionInserts).isEqualTo(1);
        assertThat(jailbreakInserts).isEqualTo(1);
        assertThat(toxicityInserts).isEqualTo(1);

        assertThat(countCategoryRows(sql, "BIAS")).isGreaterThanOrEqualTo(40);
        assertThat(countCategoryRows(sql, "PROMPT_INJECTION")).isGreaterThanOrEqualTo(15);
        assertThat(countCategoryRows(sql, "JAILBREAK")).isGreaterThanOrEqualTo(10);
        assertThat(countCategoryRows(sql, "TOXICITY")).isGreaterThanOrEqualTo(10);

        assertThat(categoryBlockOrder(sql)).containsExactlyElementsOf(CATEGORY_ORDER);
    }

    private static int countInsertsForCategory(String sql, String category) {
        Pattern block = Pattern.compile(
                "-- ── " + Pattern.quote(category) + " ──[\\s\\S]*?INSERT INTO guardrail_seed_text",
                Pattern.MULTILINE);
        return (int) block.matcher(sql).results().count();
    }

    private static long countCategoryRows(String sql, String category) {
        Pattern row = Pattern.compile("\\('" + category + "',");
        return row.matcher(sql).results().count();
    }

    private static List<String> categoryBlockOrder(String sql) {
        Pattern header = Pattern.compile("-- ── (BIAS|PROMPT_INJECTION|JAILBREAK|TOXICITY) ──");
        List<String> order = new ArrayList<>();
        header.matcher(sql).results().forEach(m -> order.add(m.group(1)));
        return order;
    }
}
