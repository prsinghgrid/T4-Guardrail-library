package com.gridynamics.forge.guardrail.semantic;

/**
 * Semantic violation categories. Maps to the {@code category} column in
 * {@code classpath:semantic/semantic_seeds.csv} and held in {@link InMemorySemanticStore}.
 */
public enum SemanticCategory {

    /** Age, gender, caste, religion, race, disability, nationality, education bias. */
    BIAS,

    /** Hate speech, violence, self-harm, cyber abuse, illegal incitement. */
    TOXICITY,

    /** Direct or indirect instructions to override the system prompt. */
    PROMPT_INJECTION,

    /** Roleplay tricks, "DAN" mode, "pretend you have no restrictions", etc. */
    JAILBREAK
}
