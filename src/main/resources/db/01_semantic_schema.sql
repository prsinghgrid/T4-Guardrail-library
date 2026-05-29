-- ============================================================
-- Guardrail semantic pattern store (PostgreSQL + pgvector)
--
-- Prerequisites:
--   CREATE EXTENSION IF NOT EXISTS vector;
--
-- The embedding column stores 384-dimensional L2-normalised
-- float vectors produced by bge-small-en via ONNX Runtime.
-- ============================================================

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS guardrail_semantic_patterns (
    id          BIGSERIAL       PRIMARY KEY,
    category    VARCHAR(50)     NOT NULL,
    description VARCHAR(255)    NOT NULL,
    text        TEXT            NOT NULL,
    embedding   vector(384),
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Exact cosine search is used for small corpora (<1000 patterns).
-- Do NOT create IVFFlat here — it causes recall gaps on tiny datasets.
-- For large corpora, add IVFFlat manually after seeding with lists ~= sqrt(row_count).

CREATE UNIQUE INDEX IF NOT EXISTS idx_guardrail_semantic_patterns_text
    ON guardrail_semantic_patterns (text);

-- Category index for targeted queries
CREATE INDEX IF NOT EXISTS idx_guardrail_semantic_category
    ON guardrail_semantic_patterns (category);

-- Legacy deployments only: drop IVFFlat if an older schema created it
DROP INDEX IF EXISTS idx_guardrail_semantic_patterns_embedding;
