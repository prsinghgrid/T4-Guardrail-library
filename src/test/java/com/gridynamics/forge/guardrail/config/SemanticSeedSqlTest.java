package com.gridynamics.forge.guardrail.config;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the structure and coverage of the in-memory semantic seed file
 * {@code classpath:semantic/semantic_seeds.csv}.
 */
class SemanticSeedSqlTest {

    private record SeedRow(String category, String description, String text) {}

    private static List<SeedRow> loadRows() throws IOException {
        List<SeedRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(
                        SemanticSeedSqlTest.class.getClassLoader()
                                .getResourceAsStream("semantic/semantic_seeds.csv")),
                StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\|", 3);
                if (parts.length == 3) {
                    rows.add(new SeedRow(parts[0].trim(), parts[1].trim(), parts[2].trim()));
                }
            }
        }
        return rows;
    }

    @Test
    void seedFileExists() throws IOException {
        List<SeedRow> rows = loadRows();
        assertThat(rows).isNotEmpty();
    }

    @Test
    void allCategoriesPresent() throws IOException {
        List<SeedRow> rows = loadRows();
        List<String> categories = rows.stream().map(SeedRow::category).distinct().toList();
        assertThat(categories).contains("BIAS", "TOXICITY", "PROMPT_INJECTION", "JAILBREAK");
    }

    @Test
    void biasCategoryHasSufficientCoverage() throws IOException {
        long count = loadRows().stream().filter(r -> "BIAS".equals(r.category())).count();
        assertThat(count).as("BIAS seed count").isGreaterThanOrEqualTo(40);
    }

    @Test
    void promptInjectionCategoryHasSufficientCoverage() throws IOException {
        long count = loadRows().stream().filter(r -> "PROMPT_INJECTION".equals(r.category())).count();
        assertThat(count).as("PROMPT_INJECTION seed count").isGreaterThanOrEqualTo(15);
    }

    @Test
    void jailbreakCategoryHasSufficientCoverage() throws IOException {
        long count = loadRows().stream().filter(r -> "JAILBREAK".equals(r.category())).count();
        assertThat(count).as("JAILBREAK seed count").isGreaterThanOrEqualTo(10);
    }

    @Test
    void toxicityCategoryHasSufficientCoverage() throws IOException {
        long count = loadRows().stream().filter(r -> "TOXICITY".equals(r.category())).count();
        assertThat(count).as("TOXICITY seed count").isGreaterThanOrEqualTo(10);
    }

    @Test
    void noRowHasMissingFields() throws IOException {
        List<SeedRow> rows = loadRows();
        for (SeedRow row : rows) {
            assertThat(row.category()).as("category").isNotBlank();
            assertThat(row.description()).as("description for: " + row.text()).isNotBlank();
            assertThat(row.text()).as("text for: " + row.description()).isNotBlank();
        }
    }

    @Test
    void totalSeedCountMatchesExpected() throws IOException {
        List<SeedRow> rows = loadRows();
        assertThat(rows).as("total seed count").hasSizeGreaterThanOrEqualTo(119);
    }
}
