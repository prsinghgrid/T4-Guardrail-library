package com.gridynamics.forge.guardrail.semantic;

import com.gridynamics.forge.guardrail.embedding.EmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Populates the pgvector pattern store with embeddings generated from
 * {@code guardrail_seed_text}.
 */
public final class SemanticPatternSeeder {

    private static final Logger log = LoggerFactory.getLogger(SemanticPatternSeeder.class);

    private final JdbcTemplate jdbc;
    private final EmbeddingProvider embeddingProvider;
    private final PgVectorSemanticStore store;

    public SemanticPatternSeeder(JdbcTemplate jdbc,
                                  EmbeddingProvider embeddingProvider,
                                  PgVectorSemanticStore store) {
        this.jdbc = jdbc;
        this.embeddingProvider = embeddingProvider;
        this.store = store;
    }

    /**
     * Embed every row in {@code guardrail_seed_text} and insert missing patterns.
     */
    public void seed() {
        List<SeedRow> rows = jdbc.query(
                "SELECT category, description, text FROM guardrail_seed_text",
                (rs, n) -> new SeedRow(
                        rs.getString("category"),
                        rs.getString("description"),
                        rs.getString("text")
                )
        );

        log.info("[GUARDRAIL] Seeding semantic patterns...");
        int inserted = 0;
        int skipped = 0;
        for (SeedRow row : rows) {
            try {
                float[] embedding = embeddingProvider.embed(row.text());
                if (store.insertIfAbsent(row.category(), row.description(), row.text(), embedding)) {
                    inserted++;
                } else {
                    skipped++;
                }
            } catch (Exception ex) {
                log.warn("[GUARDRAIL] Failed to seed pattern '{}': {}", row.description(), ex.getMessage());
            }
        }
        log.info("[GUARDRAIL] Seeded {} semantic patterns into pgvector ({} already present)",
                inserted, skipped);
    }

    /** Returns the current count of rows in the pattern store. */
    public long countExisting() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + store.getTableName(), Long.class);
        return count != null ? count : 0L;
    }

    /** Returns the number of seed texts awaiting embedding generation. */
    public long countSeedText() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM guardrail_seed_text", Long.class);
        return count != null ? count : 0L;
    }

    private record SeedRow(String category, String description, String text) {}
}
