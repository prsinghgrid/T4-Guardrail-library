package com.gridynamics.forge.guardrail.semantic;

/**
 * Semantic violation categories stored in pgvector alongside each unsafe pattern.
 *
 * <p>Maps to the {@code category} column of {@code guardrail_semantic_patterns}.
 */
public enum SemanticCategory {

    /** Age, gender, caste, religion, disability, nationality, education bias. */
    BIAS,

    /** Hate speech, violence, self-harm, cyber abuse, illegal incitement. */
    TOXICITY,

    /** Direct or indirect instructions to override the system prompt. */
    PROMPT_INJECTION,

    /** Roleplay tricks, "DAN" mode, "pretend you have no restrictions", etc. */
    JAILBREAK
}
