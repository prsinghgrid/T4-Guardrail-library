# Forge AI Guardrail

A production-ready AI guardrail library for validating, filtering, and controlling LLM prompts and responses using rule-based (regex + fuzzy) and semantic (ONNX + pgvector) intelligence.

---

## Key Capabilities

### Rule-Based Validation (Regex & Heuristics)
- **PII Sanitization** — detects and safely redacts sensitive data (email, phone, SSN, credit cards) into tags like `[EMAIL_REDACTED]`
- **Bias Detection** — blocks gender, age, nationality, caste, religion, disability, sexuality, and education-based discrimination
- **Toxicity & Hate Speech** — catches violence, self-harm, cyberbullying, and illegal activity prompts
- **Prompt Injection** — detects jailbreak patterns, DAN mode, system-prompt exfiltration, and override attempts
- **Secret Scanner** — prevents API keys, JWT tokens, RSA private keys, and connection strings from reaching the LLM

### Semantic Engine (ONNX + pgvector)
- **Context-Aware Validation** — uses `bge-small-en` embeddings to catch the *intent* of paraphrased or obfuscated attacks
- **Threat Categories** — bias, toxicity, jailbreak, and prompt injection backed by a curated vector database
- **Redis Cache** — embedding results are cached per-prompt to minimize model inference latency

---

## Validation Chain (execution order)

```
INPUT_LENGTH (10) → BIAS (100) → PROMPT_INJECTION (110) → SECRET_SCANNER (115)
  → PII Sanitize (200) → TOXICITY (300) → SEMANTIC (400, optional)
  → Decision: any HARD violation? → BLOCK or ALLOW
```

---

## Full Setup & Run Guide

Complete steps to run **Forge AI Guardrail** from scratch on a new machine.

---

### 1. Install Required Software

#### Java 17+
```bash
java -version
# Required: Java 17 or Java 21
```
Download from [https://adoptium.net](https://adoptium.net) if not installed.

#### Apache Maven
```bash
mvn -version
```
Download from [https://maven.apache.org/download.cgi](https://maven.apache.org/download.cgi) if not installed.

#### Docker Desktop
```bash
docker --version
docker compose version
```
Download from [https://www.docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop).  
**Ensure Docker Desktop is running before proceeding.**

---

### 2. Clone the Project

```bash
git clone <your-repository-url>
cd forge-ai
```

---

### 3. Create the `.env` File

The `docker-compose.yml` reads database credentials from a `.env` file in the project root.  
Create it with the following content:

```env
POSTGRES_DB=guardrail_db
POSTGRES_USER=guardrail
POSTGRES_PASSWORD=guardrail_secret
```

> **Note:** These values must match the `spring.datasource` settings in your `application.yml`.

---

### 4. Review `docker-compose.yml` Ports

The provided compose file already maps PostgreSQL to **port 5433** (not 5432) to avoid conflicts with a locally installed PostgreSQL:

```yaml
ports:
  - "5433:5432"   # host:container — change host port if 5433 is also taken
```

Redis remains on its default port:

```yaml
ports:
  - "6379:6379"
```

If you need to change either port, update both `docker-compose.yml` and `application.yml` (`spring.datasource.url`) consistently.

---

### 5. Start Infrastructure

```bash
docker compose up -d
```

Verify containers are healthy:

```bash
docker compose ps
```

Expected output:

```
NAME                  STATUS
guardrail-pgvector    Up (healthy)
guardrail-redis       Up (healthy)
```

> On first start, Docker automatically runs `01_semantic_schema.sql` and `02_semantic_seed.sql` to initialise the database schema and seed data.

---

### 6. Download the Local Embedding Model

The semantic layer requires the `bge-small-en-v1.5` ONNX model and tokenizer.

#### Create the model directory

```bash
mkdir -p ~/models/bge-small-en
```

#### Install the HuggingFace CLI

```bash
pip3 install huggingface-hub
```

#### Download the tokenizer

```bash
huggingface-cli download BAAI/bge-small-en-v1.5 \
  tokenizer.json \
  --local-dir ~/models/bge-small-en
```

#### Download the ONNX model

```bash
huggingface-cli download BAAI/bge-small-en-v1.5 \
  onnx/model.onnx \
  --local-dir ~/models/bge-small-en
```

#### Verify downloads

```bash
find ~/models/bge-small-en -type f
```

Expected:

```
~/models/bge-small-en/tokenizer.json
~/models/bge-small-en/onnx/model.onnx
```

> **Tip:** If the `huggingface-cli` command is not on PATH, locate it with `pip3 show huggingface-hub` or use the full path (e.g. `~/.local/bin/huggingface-cli`).

---

### 7. Configure `application.yml`

Copy the provided example and fill in your paths:

```bash
cp src/main/resources/application-guardrail-example.yml src/main/resources/application.yml
```

Edit `application.yml` with your local values:

```yaml
forge:
  guardrail:
    semantic:
      enabled: true
      fail-open: true          # set false in production to hard-fail if ONNX or DB is unavailable

      onnx:
        model-path: /Users/<your-user>/models/bge-small-en/onnx/model.onnx
        tokenizer-path: /Users/<your-user>/models/bge-small-en/tokenizer.json
        max-tokens: 128
        intra-op-threads: 1

      pgvector:
        table-name: guardrail_semantic_patterns

      redis:
        enabled: true
        ttl-seconds: 3600
        key-prefix: "guardrail:emb:"

spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/guardrail_db   # port must match docker-compose
    username: guardrail
    password: guardrail_secret
    driver-class-name: org.postgresql.Driver

  data:
    redis:
      host: localhost
      port: 6379
```

> Replace `/Users/<your-user>/` with your actual home directory path.

---

### 8. Build the Library

From the project root:

```bash
mvn clean install -DskipTests
```

This compiles the code, packages the JAR, and installs it into your local Maven repository (`~/.m2`).

To run tests as well:

```bash
mvn clean install
```

---

### 9. Verify Database Contents

Connect to the running PostgreSQL container:

```bash
docker exec -it guardrail-pgvector psql -U guardrail -d guardrail_db
```

Check that seed data was loaded:

```sql
SELECT COUNT(*) FROM guardrail_seed_text;
```

Check the semantic pattern store (populated on first application startup):

```sql
SELECT COUNT(*) FROM guardrail_semantic_patterns;
```

Expected:
- `guardrail_seed_text` — **> 0** rows (loaded by Docker init scripts)
- `guardrail_semantic_patterns` — **> 0** rows after the application has started at least once

Exit the psql session:

```sql
\q
```

---

### 10. Start the Application

```bash
mvn spring-boot:run
```

Or run the packaged JAR directly:

```bash
java -jar target/forge-ai-guardrail-2.0.1.jar
```

---

### 11. Verify Startup Logs

Watch for these lines in the console output:

```
[GUARDRAIL] OnnxEmbeddingProvider ready
[GUARDRAIL] Startup validation passed
[GUARDRAIL] Seeded X semantic patterns into guardrail_semantic_patterns
```

If any of these are missing, check the [Common Issues](#13-common-issues) section below.

---

### 12. Test the API

#### Evaluate a prompt (bias detection)

```bash
curl -s -X POST http://localhost:8080/api/guardrail/evaluate \
  -H "Content-Type: application/json" \
  -d '{"prompt": "We prefer applicants who recently entered the workforce and can adapt to startup culture."}' \
  | jq .
```

Expected response — semantic age bias detected:

```json
{
  "allowed": false,
  "violations": [
    {
      "code": "SEMANTIC_BIAS",
      "category": "DISCRIMINATION",
      "severity": "HARD"
    }
  ]
}
```

#### Evaluate a clean prompt

```bash
curl -s -X POST http://localhost:8080/api/guardrail/evaluate \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Senior Java developer needed for a backend role."}' \
  | jq .
```

Expected response:

```json
{
  "allowed": true,
  "violations": []
}
```

#### Process a prompt (PII sanitization applied)

```bash
curl -s -X POST http://localhost:8080/api/guardrail/process \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Contact me at john@example.com or 9876543210."}' \
  | jq .
```

Expected: PII fields are redacted in the returned `sanitisedPrompt`.

---

### 13. Common Issues

#### PostgreSQL port already in use

**Error:**
```
bind: address already in use :::5432
```

**Fix:** The `docker-compose.yml` already uses port `5433` on the host. If `5433` is also taken, change the host-side port mapping:

```yaml
ports:
  - "5434:5432"
```

Then update `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5434/guardrail_db
```

---

#### Docker daemon not running

**Error:**
```
Cannot connect to the Docker daemon at unix:///var/run/docker.sock
```

**Fix:** Open Docker Desktop and wait for it to show "Running" status before retrying.

---

#### `.env` file missing

**Error:**
```
variable is not set. Defaulting to a blank string.
```

**Fix:** Create `.env` in the project root (see [Step 3](#3-create-the-env-file)).

---

#### ONNX model not found at startup

**Error:**
```
OnnxEmbeddingProvider failed to load model
```

**Fix:** Verify the file paths in `application.yml` and that the download completed:

```bash
ls ~/models/bge-small-en/onnx/model.onnx
ls ~/models/bge-small-en/tokenizer.json
```

---

#### `guardrail_semantic_patterns` table is empty

**Cause:** The seeder runs on startup and requires the ONNX model and PostgreSQL to both be available.

**Check:**
1. Confirm ONNX model loaded successfully (look for `OnnxEmbeddingProvider ready` in logs)
2. Confirm `guardrail_seed_text` has rows (see [Step 9](#9-verify-database-contents))
3. Confirm `SemanticPatternSeeder` ran — look for `Seeded` in startup logs
4. If seeding failed silently, restart the application after fixing the underlying dependency

---

#### `huggingface-cli` not found on PATH

**Fix:**

```bash
pip3 install --upgrade huggingface-hub
# then try:
python3 -m huggingface_hub.commands.huggingface_cli download BAAI/bge-small-en-v1.5 \
  onnx/model.onnx \
  --local-dir ~/models/bge-small-en
```

---

### 14. Useful Docker Commands

| Action | Command |
|---|---|
| Start containers | `docker compose up -d` |
| Stop containers (keep data) | `docker compose down` |
| Stop + delete all data | `docker compose down -v` |
| View live logs | `docker compose logs -f` |
| Restart a single service | `docker compose restart pgvector` |
| Open psql session | `docker exec -it guardrail-pgvector psql -U guardrail -d guardrail_db` |

---

## Project Structure

```
forge-ai/
├── docker-compose.yml                     # Local dev: pgvector + Redis
├── .env                                   # Docker env vars (not committed)
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/gridynamics/forge/guardrail/
    │   │   ├── GuardrailEngine.java        # Pipeline orchestrator
    │   │   ├── chain/                      # Validator implementations
    │   │   ├── config/                     # Spring Boot auto-configuration
    │   │   ├── registry/                   # Pattern registry
    │   │   ├── semantic/                   # ONNX + pgvector integration
    │   │   ├── report/                     # Moderation report formatting
    │   │   ├── metrics/                    # Micrometer metrics
    │   │   ├── spi/                        # Custom validator extension point
    │   │   └── util/                       # Text normalization, fuzzy match
    │   └── resources/
    │       ├── db/
    │       │   ├── 01_semantic_schema.sql  # Table definitions
    │       │   └── 02_semantic_seed.sql    # Seed threat patterns
    │       └── application-guardrail-example.yml
    └── test/
        └── java/com/gridynamics/forge/guardrail/
```

---

## Architecture Overview

```
HTTP Request
    │
    ▼
GuardrailController
    │
    ▼
GuardrailEngine.evaluate() / process()
    │
    ├─► INPUT_LENGTH validator    (order 10)
    ├─► BIAS validator            (order 100)  ← regex + fuzzy + leetspeak decode
    ├─► PROMPT_INJECTION validator(order 110)
    ├─► SECRET_SCANNER validator  (order 115)  ← raw text matching (no normalization)
    ├─► PII sanitizer             (order 200)  ← redacts, does not block
    ├─► TOXICITY validator        (order 300)
    └─► SEMANTIC validator        (order 400, optional)
            │
            ├─► OnnxEmbeddingProvider  (bge-small-en ONNX)
            ├─► RedisEmbeddingCache    (TTL-based cache)
            └─► PgVectorSemanticStore  (cosine similarity search)
    │
    ▼
any HARD violation?
    ├── YES → GuardrailViolationException (HTTP 422)
    │         └─► ModerationReportFormatter → violation report
    └── NO  → GuardrailResult (allowed=true, sanitisedPrompt)
```

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.3 |
| Embeddings | ONNX Runtime + bge-small-en-v1.5 |
| Vector DB | PostgreSQL 16 + pgvector extension |
| Cache | Redis 7 |
| Build | Apache Maven |
| Metrics | Micrometer (optional) |
