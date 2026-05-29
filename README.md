# Forge AI Guardrail

A production-ready Guardrail system for validating, filtering, and controlling AI-generated or user-provided content using both rule-based (regex) and semantic intelligence.

---

## Key Capabilities

### Rule-Based Validation (Regex & Heuristics)
- **PII Sanitization**: Automatically detects and safely masks sensitive data (Emails, Phone numbers, SSNs, Credit Cards, etc.) into tags like `[EMAIL_REDACTED]`. *(Regex exclusive)*
- **Bias & Toxicity**: Deterministic keyword and fuzzy-matching blocks for explicit hate speech, discriminatory language, and cyber abuse.
- **Prompt Injection**: Syntax checks for known jailbreak and LLM-bypass patterns.
- **Input Secrets**: Prevents LLM exposure to leaked API keys, tokens, or credentials.

### Semantic Engine (ONNX + pgvector)
- **Context-Aware Validation**: Utilizes `bge-small-en` embeddings to detect the *intent* of a prompt, catching paraphrased attacks that evade regex rules.
- **Trained Threat Categories**: Backed by a comprehensive, production-ready vector database trained on advanced threats:
  - **Bias**: Age, gender identity, religion, nationality, and caste exclusions.
  - **Toxicity**: Subtle bullying, threats, and illegal activity incitement.
  - **Jailbreaks & Prompt Injection**: Persona adoption (e.g., "DAN" mode), formatting tricks, and simulated shells.

---

## Validation Flow

1. **Input Resolving**: Normalizes obfuscated text (e.g., leetspeak, stretched characters).
2. **Rule-Based Fast Path**: Deterministic validators run first. PII is safely redacted, while explicit policy breaches trigger immediate `HARD` blocks.
3. **Semantic Deep Scan**: The prompt is embedded and mathematically compared (cosine similarity) against the threat vector database to catch zero-day or paraphrased attacks.
4. **Final Decision**: The `GuardrailEngine` returns a complete `GuardrailResult` with an aggregated Risk Score and detailed violation report.

---

## Project Structure

```text
src/main/java/com/gridynamics/forge/guardrail/
├── GuardrailEngine.java  # Core pipeline orchestrator
├── chain/                # Rule-based and semantic validators
├── config/               # Spring Boot auto-configuration
├── semantic/             # Semantic search & pgvector integration
├── report/               # Moderation reporting and scoring
└── util/                 # Text normalization and fuzzy matching
```

---

## Enterprise Ready
- **Fail-Fast / Fail-Open Configuration**: Robust resilience options for database and model dependencies.
- **Pluggable Architecture**: Extend with custom checks via the CustomGuardrailValidator SPI.
- **Observability**: Built-in Micrometer metrics tracking latency, block rates, and categories.
# T4-Guardrail-library
