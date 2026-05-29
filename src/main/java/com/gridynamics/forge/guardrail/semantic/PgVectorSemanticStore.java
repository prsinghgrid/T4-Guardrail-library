package com.gridynamics.forge.guardrail.semantic;

import com.gridynamics.forge.guardrail.util.EmbeddingNormalizationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * pgvector-backed store for unsafe semantic patterns.
 */
public class PgVectorSemanticStore {

    private static final Logger log = LoggerFactory.getLogger(PgVectorSemanticStore.class);

    private final JdbcTemplate jdbc;
    private final String tableName;

    public PgVectorSemanticStore(JdbcTemplate jdbc, String tableName) {
        this.jdbc = jdbc;
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }

    /** Returns the {@code topK} nearest patterns; caller applies per-category thresholds. */
    public List<SemanticMatch> findNearest(float[] embedding, int topK) {
        String vector = toVectorLiteral(EmbeddingNormalizationUtil.normalize(embedding));

        String sql = """
                SELECT category, text, description,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM %s
                WHERE embedding IS NOT NULL
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """.formatted(tableName);

        return jdbc.execute((Connection conn) -> {
            try (Statement st = conn.createStatement()) {
                st.execute("SET LOCAL ivfflat.probes = 100");
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, vector);
                ps.setString(2, vector);
                ps.setInt(3, topK);
                return readMatches(ps.executeQuery());
            }
        });
    }

    /** @return {@code true} when a new row was inserted */
    public boolean insertIfAbsent(String category, String description, String text, float[] embedding) {
        String vector = toVectorLiteral(EmbeddingNormalizationUtil.normalize(embedding));
        String sql = """
                INSERT INTO %s (category, description, text, embedding)
                SELECT ?, ?, ?, ?::vector
                WHERE NOT EXISTS (SELECT 1 FROM %s WHERE text = ?)
                """.formatted(tableName, tableName);
        return jdbc.update(sql, category, description, text, vector, text) > 0;
    }

    private static List<SemanticMatch> readMatches(ResultSet rs) throws SQLException {
        List<SemanticMatch> matches = new ArrayList<>();
        while (rs.next()) {
            matches.add(new SemanticMatch(
                    rs.getString("text"),
                    rs.getString("description"),
                    parseCategory(rs.getString("category")),
                    rs.getDouble("similarity")
            ));
        }
        return matches;
    }

    private static String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }

    private static SemanticCategory parseCategory(String raw) {
        try {
            return SemanticCategory.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("[GUARDRAIL] Unknown semantic category '{}', defaulting to TOXICITY", raw);
            return SemanticCategory.TOXICITY;
        }
    }
}
