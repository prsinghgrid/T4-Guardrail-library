package com.gridynamics.forge.guardrail.registry;

import com.gridynamics.forge.guardrail.config.GuardrailProperties;
import com.gridynamics.forge.guardrail.util.PatternMatchUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Central registry of compiled regex patterns shared across validators.
 *
 * <p>Patterns are immutable and thread-safe. Custom overrides from
 * {@link GuardrailProperties} are merged at construction time so validators
 * remain decoupled from pattern maintenance.
 */
public final class GuardrailPatternRegistry {

    /**
     * Derogatory adjectives used in gender-based group insults
     * ({@code women are <adj>}, {@code men are <adj>}, etc.).
     */
    private static final String GENDER_DEROGATORY_ADJECTIVES =
            "inferior|toxic|incompetent|unfit|worse|bad|assholes?|stupid|idiots?|morons?|"
                    + "worthless|useless|pathetic|disgusting|lazy|trash|garbage|scum|subhuman|"
                    + "terrible|awful|horrible|horrendous|the\\s+worst|good\\s+for\\s+nothing|"
                    + "dumb|brainless|inadequate|unqualified|incapable";

    private final Map<PatternCategory, List<CategorizedPattern>> patternsByCategory;

    public GuardrailPatternRegistry(GuardrailProperties props) {
        this.patternsByCategory = buildRegistry(props);
    }

    /**
     * Returns an unmodifiable view of patterns for the given category.
     */
    public List<CategorizedPattern> getPatterns(PatternCategory category) {
        return patternsByCategory.getOrDefault(category, List.of());
    }

    /**
     * Returns patterns filtered by subcategory (e.g. {@code gender} within {@code BIAS}).
     */
    public List<CategorizedPattern> getPatterns(PatternCategory category, String subcategory) {
        return getPatterns(category).stream()
                .filter(p -> p.subcategory().equalsIgnoreCase(subcategory))
                .toList();
    }

    private static Map<PatternCategory, List<CategorizedPattern>> buildRegistry(GuardrailProperties props) {
        Map<PatternCategory, List<CategorizedPattern>> registry = new EnumMap<>(PatternCategory.class);

        registry.put(PatternCategory.TOXICITY, buildToxicityPatterns(props));
        registry.put(PatternCategory.BIAS, buildBiasPatterns(props));
        registry.put(PatternCategory.INJECTION, buildInjectionPatterns());
        registry.put(PatternCategory.OUTPUT_LEAKAGE, buildOutputLeakagePatterns());
        registry.put(PatternCategory.SECRETS, buildSecretPatterns());
        registry.put(PatternCategory.PII, buildPiiPatterns());

        return Collections.unmodifiableMap(registry);
    }

    private static List<CategorizedPattern> buildToxicityPatterns(GuardrailProperties props) {
        List<CategorizedPattern> patterns = new ArrayList<>();

        if (isCategoryEnabled(props.getToxicity().getCategories().getViolence())) {
            patterns.add(cp("kill|murder|slaughter|execute", "violence_terms", PatternCategory.TOXICITY, "violence", 0.85, 0.92));
            patterns.add(cp("hurt|harm|attack|abuse|assault", "harm_terms", PatternCategory.TOXICITY, "violence", 0.55, 0.80));
            patterns.add(cp("hurt\\s+them\\s+badly|people\\s+should\\s+die", "violence_phrases", PatternCategory.TOXICITY, "violence", 0.90, 0.95));
            // Intent-prefixed standalone violence verbs — catches "I want to destroy", "I will kill", "trying to harm", etc.
            patterns.add(cp("(?:want\\s+to|going\\s+to|will|trying\\s+to|plan\\s+to|wish\\s+to)\\s+(?:destroy|kill|murder|harm|attack|slaughter|annihilate|obliterate)",
                    "destroy_intent", PatternCategory.TOXICITY, "violence", 0.60, 0.82));
            // Negated-live death wish — catches "I don't want interns to live", "shouldn't be allowed to live"
            // Requires at least one word between negation and "to live" to avoid FP on "I don't want to live in X"
            patterns.add(cp("(?:don'?t|do\\s+not|doesn'?t|does\\s+not|shouldn'?t|should\\s+not|won'?t)\\s+(?:want\\s+)?(?:\\w+\\s+){1,4}to\\s+live",
                    "negated_live", PatternCategory.TOXICITY, "violence", 0.75, 0.88));
            // "want X dead", "want X to die", "want them all dead" — up to 4 intervening words
            patterns.add(cp("want\\s+(?:\\w+\\s+){0,4}(?:dead|to\\s+die|eliminated|wiped\\s+out)",
                    "want_dead", PatternCategory.TOXICITY, "violence", 0.80, 0.90));
            // "wish X were dead", "wish they would die", "hope they die"
            patterns.add(cp("(?:wish(?:es)?|hope(?:s)?)\\s+(?:\\w+\\s+){0,4}(?:were?\\s+dead|would\\s+die|to\\s+die|could\\s+die)",
                    "wish_dead", PatternCategory.TOXICITY, "violence", 0.80, 0.90));
        }

        if (isCategoryEnabled(props.getToxicity().getCategories().getHateSpeech())) {
            patterns.add(cp("racist|racial\\s+slur|terrorist|hate\\s+everyone", "hate_terms", PatternCategory.TOXICITY, "hate_speech", 0.80, 0.90));
            patterns.add(cp("destroy\\s+all\\s+systems", "destroy_systems", PatternCategory.TOXICITY, "hate_speech", 0.85, 0.92));
        }

        if (isCategoryEnabled(props.getToxicity().getCategories().getSelfHarm())) {
            patterns.add(cp("go\\s+die|you\\s+deserve\\s+to\\s+die|kill\\s+yourself|kys", "self_harm", PatternCategory.TOXICITY, "self_harm", 0.95, 0.97));
            patterns.add(cp("self[-\\s]?harm\\s+instructions", "self_harm_instructions", PatternCategory.TOXICITY, "self_harm", 0.90, 0.95));
            // Broader group/person death threats — "interns deserve to die", "[group] should die"
            patterns.add(cp("(?:\\w+\\s+){0,4}(?:deserve(?:s)?\\s+to\\s+die|should\\s+(?:all\\s+)?die|must\\s+die|need\\s+to\\s+die)",
                    "group_death_threat", PatternCategory.TOXICITY, "self_harm", 0.88, 0.93));
        }

        if (isCategoryEnabled(props.getToxicity().getCategories().getCyberAbuse())) {
            patterns.add(cp("teach\\s+me\\s+how\\s+to\\s+hack|how\\s+to\\s+hack", "hack_request", PatternCategory.TOXICITY, "cyber_abuse", 0.75, 0.88));
            patterns.add(cp("hack\\s+banking|bypass\\s+authentication|create\\s+malware", "cyber_abuse_phrases", PatternCategory.TOXICITY, "cyber_abuse", 0.88, 0.93));
            patterns.add(cp("ways\\s+to\\s+steal\\s+passwords|how\\s+to\\s+steal", "steal_request", PatternCategory.TOXICITY, "cyber_abuse", 0.80, 0.90));
            // Standalone dangerous intent verbs — lower weight, catches "I want to hack", "I want to crack", etc.
            patterns.add(cp("(?:want\\s+to|trying\\s+to|how\\s+(?:do\\s+I|can\\s+I)\\s+)\\s*(?:hack|crack|exploit|phish|ddos|bruteforce|brute.?force)", "hack_intent", PatternCategory.TOXICITY, "cyber_abuse", 0.60, 0.80));
        }

        if (isCategoryEnabled(props.getToxicity().getCategories().getIllegalActivities())) {
            patterns.add(cp("make\\s+a\\s+bomb|bomb\\s+threat", "bomb_terms", PatternCategory.TOXICITY, "illegal_activities", 0.95, 0.96));
            patterns.add(cp("child\\s+exploitation|sexually\\s+explicit", "explicit_illegal", PatternCategory.TOXICITY, "illegal_activities", 0.95, 0.97));
            // Intent-prefixed bomb verb — catches "I want to bomb", "I'm going to bomb"
            patterns.add(cp("(?:want\\s+to|going\\s+to|plan\\s+to|trying\\s+to)\\s+bomb",
                    "bomb_intent", PatternCategory.TOXICITY, "illegal_activities", 0.60, 0.82));
        }

        mergeCustomPatterns(patterns, props.getToxicity().getExtraPatterns(), PatternCategory.TOXICITY, "custom", 0.70, 0.85);
        return List.copyOf(patterns);
    }

    private static List<CategorizedPattern> buildBiasPatterns(GuardrailProperties props) {
        List<CategorizedPattern> patterns = new ArrayList<>();
        GuardrailProperties.BiasProperties bias = props.getBias();

        if (isCategoryEnabled(bias.getCategories().getGender())) {
            patterns.add(cp("male\\s+only|female\\s+only|men\\s+preferred|women\\s+preferred", "gender_exclusion", PatternCategory.BIAS, "gender", 0.95, 0.96));
            patterns.add(cp("no\\s+transgenders?|cisgender\\s+only|no\\s+non-binary|no\\s+genderqueer", "gender_identity_exclusion", PatternCategory.BIAS, "gender", 0.95, 0.96));
            patterns.add(cp("he/him\\s+required|she/her\\s+required|no\\s+women|no\\s+men", "gender_requirement", PatternCategory.BIAS, "gender", 0.95, 0.96));
            patterns.add(cp("gender\\s+preference|boys\\s+only|girls\\s+only", "gender_preference", PatternCategory.BIAS, "gender", 0.90, 0.94));
            // Catch reversed word-order: "only female candidates", "only male applicants"
            patterns.add(cp("only\\s+(?:female|male|women|men|girl|boy)\\s+(?:candidates?|applicants?|employees?|professionals?|engineers?|workers?|persons?|people)",
                    "gender_exclusion_reversed", PatternCategory.BIAS, "gender", 0.95, 0.96));
            // Catch "female candidates only" / "male applicants only"
            patterns.add(cp("(?:female|male|women|men|girl|boy)\\s+(?:candidates?|applicants?|employees?|professionals?|engineers?|workers?)\\s+only",
                    "gender_exclusion_suffix", PatternCategory.BIAS, "gender", 0.95, 0.96));
            // Catch hiring-intent with gender: "only want male candidates", "only need female engineers"
            // Allows up to 4 intervening words between "only" and the gender word
            patterns.add(cp("(?:only\\s+(?:want|need|hire|consider|accept|seek|prefer|looking\\s+for|require)\\s+(?:\\w+\\s+){0,4}(?:male|female|men|women|boys?|girls?)\\s+(?:candidates?|applicants?|employees?|professionals?|engineers?|workers?))"
                            + "|(?:(?:want|need|hire|prefer|seek)\\s+(?:only\\s+)?(?:male|female|men|women)\\s+(?:candidates?|applicants?|employees?))",
                    "gender_hiring_intent", PatternCategory.BIAS, "gender", 0.92, 0.94));
            patterns.add(cp(
                    "(?:women|men|girls|boys|females?|males?)\\s+are\\s+(?:all\\s+)?(?:"
                            + GENDER_DEROGATORY_ADJECTIVES + ")"
                            + "|men\\s+are\\s+(?:superior|better)"
                            + "|only\\s+(?:men|women)\\s+should\\s+be\\s+hired",
                    "gender_inferiority", PatternCategory.BIAS, "gender", 0.90, 0.95));
        }


        if (isCategoryEnabled(bias.getCategories().getReligion())) {
            patterns.add(cp("muslim\\s+only|hindu\\s+only|christian\\s+only|sikh\\s+only|jain\\s+only", "religion_exclusion", PatternCategory.BIAS, "religion", 0.95, 0.96));
            patterns.add(cp("no\\s+muslims|no\\s+hindus|no\\s+christians", "religion_ban", PatternCategory.BIAS, "religion", 0.95, 0.96));
        }

        if (isCategoryEnabled(bias.getCategories().getCaste())) {
            patterns.add(cp("brahmin\\s+only|upper\\s+caste|lower\\s+caste", "caste_exclusion", PatternCategory.BIAS, "caste", 0.95, 0.97));
            patterns.add(cp("caste\\s+preferred|scheduled\\s+caste\\s+not|obc\\s+only", "caste_preference", PatternCategory.BIAS, "caste", 0.95, 0.96));
        }

        if (isCategoryEnabled(bias.getCategories().getNationality())) {
            patterns.add(cp("indians\\s+only|no\\s+foreigners|whites\\s+only|no\\s+immigrants", "nationality_exclusion", PatternCategory.BIAS, "nationality", 0.95, 0.96));
            patterns.add(cp("native\\s+speakers\\s+only", "language_nationality", PatternCategory.BIAS, "nationality", 0.75, 0.85));
        }

        if (isCategoryEnabled(bias.getCategories().getDisability())) {
            patterns.add(cp("no\\s+disabled|physically\\s+fit\\s+only|no\\s+handicapped", "disability_exclusion", PatternCategory.BIAS, "disability", 0.95, 0.97));
            patterns.add(cp("able[-\\s]?bodied\\s+only|no\\s+wheelchair", "disability_requirement", PatternCategory.BIAS, "disability", 0.95, 0.96));
        }

        if (isCategoryEnabled(bias.getCategories().getSexuality())) {
            patterns.add(cp("heterosexual\\s+only|straight\\s+only|no\\s+lgbtq", "sexuality_exclusion", PatternCategory.BIAS, "sexuality", 0.95, 0.97));
        }

        if (isCategoryEnabled(bias.getCategories().getAge())) {
            patterns.add(cp("young\\s+candidates\\s+only|no\\s+candidates\\s+above|below\\s+25\\s+only", "age_exclusion", PatternCategory.BIAS, "age", 0.90, 0.94));
            patterns.add(cp("maximum\\s+age|minimum\\s+age|too\\s+old|too\\s+young|age\\s+limit", "age_limit", PatternCategory.BIAS, "age", 0.80, 0.88));
            // Numeric age thresholds: "under 30", "above 25", "below 35", "candidates under 30"
            patterns.add(cp("(?:candidates?|applicants?|persons?|people|employees?)?\\s*(?:under|below|above|over|older\\s+than|younger\\s+than|aged?\\s*(?:under|below|above|over)?)\\s*\\d+\\s*(?:years?(?:\\s+of\\s+age)?|yrs?)?",
                    "age_numeric", PatternCategory.BIAS, "age", 0.90, 0.93));
            // Explicit age bracket requirements
            patterns.add(cp("age\\s*(?:requirement|restriction|criteria|criteria|bracket|range|cap|cutoff|cut.?off|bar)",
                    "age_bracket", PatternCategory.BIAS, "age", 0.85, 0.90));
            // Age-coded language commonly used in hiring: "young and energetic", "fresh graduates"
            patterns.add(cp("young\\s+(?:and\\s+)?(?:energetic|dynamic|vibrant|talent|professional|candidates?)",
                    "age_coded_young", PatternCategory.BIAS, "age", 0.85, 0.90));
            patterns.add(cp("fresh\\s+(?:graduates?|out\\s+of\\s+college|pass\\s+outs?)\\s*(?:only|preferred|required|wanted)?",
                    "age_coded_fresh", PatternCategory.BIAS, "age", 0.85, 0.90));
            patterns.add(cp("fresh[\\s-]?minded",
                    "age_coded_fresh_minded", PatternCategory.BIAS, "age", 0.82, 0.88));
            patterns.add(cp("recently\\s+graduated|new\\s+graduates?\\s+(?:only|preferred)",
                    "age_coded_recent_grad", PatternCategory.BIAS, "age", 0.82, 0.88));
            patterns.add(cp("recently\\s+entered\\s+the\\s+workforce",
                    "age_workforce_entry", PatternCategory.BIAS, "age", 0.90, 0.94));
            patterns.add(cp("prefer\\s+applicants?\\s+who\\s+recently\\s+entered",
                    "age_prefer_new_workforce", PatternCategory.BIAS, "age", 0.88, 0.92));
            patterns.add(cp("(?:modern\\s+)?startup\\s+energy",
                    "age_startup_energy", PatternCategory.BIAS, "age", 0.82, 0.88));
            patterns.add(cp("early[\\s-]?career\\s+(?:talent|applicants?|professionals?).{0,40}startup",
                    "age_early_career_startup", PatternCategory.BIAS, "age", 0.85, 0.90));
            patterns.add(cp("adapt\\s+to\\s+(?:modern\\s+)?startup",
                    "age_startup_adapt", PatternCategory.BIAS, "age", 0.80, 0.86));
        }


        patterns.add(cp("height\\s+requirement|weight\\s+requirement|attractive\\s+candidates|good\\s+looking\\s+only", "appearance", PatternCategory.BIAS, "appearance", 0.85, 0.90));
        patterns.add(cp("unmarried\\s+only|no\\s+pregnant|single\\s+only|married\\s+preferred|no\\s+children", "marital_status", PatternCategory.BIAS, "marital_status", 0.85, 0.90));

        if (isCategoryEnabled(bias.getCategories().getEducation())) {
            // Named institution restrictions: "from IIT", "IIT graduates only", "from IIM only"
            patterns.add(cp("(?:only\\s+)?(?:from\\s+)?(?:IIT|IIM|NIT|BITS|IISc|AIIMS|IITs|IIMs|NITs)\\s*(?:graduates?|alumni|candidates?|applicants?|only|students?)?",
                    "education_institution", PatternCategory.BIAS, "education", 0.90, 0.94));
            // Generic "top college only", "premier institute only", "Tier 1 college only"
            patterns.add(cp("(?:top|premier|elite|tier[\\s-]?1)\\s+(?:colleges?|universities|institutes?|schools?|institutions?)\\s+only",
                    "education_tier", PatternCategory.BIAS, "education", 0.88, 0.92));
            patterns.add(cp("(?:from|at)\\s+(?:elite|premier|top|tier[\\s-]?1)\\s+(?:institutes?|colleges?|universities?|schools?)",
                    "education_tier_from", PatternCategory.BIAS, "education", 0.86, 0.90));
            patterns.add(cp("(?:elite|premier|top|tier[\\s-]?1)\\s+(?:institutes?|colleges?|universities?|schools?)",
                    "education_tier_reference", PatternCategory.BIAS, "education", 0.84, 0.88));
            patterns.add(cp("top[\\s-]tier\\s+universit",
                    "education_top_tier_universities", PatternCategory.BIAS, "education", 0.88, 0.92));
            patterns.add(cp("only\\s+consider\\s+candidates?\\s+from\\s+top[\\s-]tier",
                    "education_consider_top_tier", PatternCategory.BIAS, "education", 0.90, 0.94));
            patterns.add(cp("highly\\s+reputed\\s+academic\\s+institutions?",
                    "education_reputed_institutions", PatternCategory.BIAS, "education", 0.86, 0.90));
            patterns.add(cp("state\\s+school\\s+graduates?\\s+(?:are\\s+)?not\\s+(?:a\\s+)?fit",
                    "education_state_school_exclusion", PatternCategory.BIAS, "education", 0.88, 0.92));
            // "graduates from XYZ only", "alumni of XYZ only"
            patterns.add(cp("(?:graduates?|alumni)\\s+(?:from|of)\\s+(?:IIT|IIM|NIT|BITS|IISc|AIIMS|Harvard|MIT|Oxford|Cambridge|Stanford)",
                    "education_alumni", PatternCategory.BIAS, "education", 0.90, 0.94));
            // Degree-based exclusion: "MBA required", "only MBA candidates"
            patterns.add(cp("only\\s+(?:MBA|B\\.?Tech|M\\.?Tech|PhD|IIT|engineering)\\s+(?:graduates?|candidates?|applicants?|holders?)",
                    "education_degree", PatternCategory.BIAS, "education", 0.88, 0.92));
        }

        mergeCustomPatterns(patterns, bias.getExtraPatterns(), PatternCategory.BIAS, "custom", 0.90, 0.92);
        mergeLegacyTerms(patterns, bias.getExtraTerms());
        return List.copyOf(patterns);
    }

    private static List<CategorizedPattern> buildInjectionPatterns() {
        return List.of(
                cp("ignore\\s+(all\\s+)?previous\\s+instructions", "ignore_previous", PatternCategory.INJECTION, "override", 0.95, 0.96),
                cp("ignore\\s+(all\\s+)?above\\s+instructions", "ignore_above", PatternCategory.INJECTION, "override", 0.95, 0.96),
                cp("ignore\\s+all\\s+restrictions", "ignore_restrictions", PatternCategory.INJECTION, "jailbreak", 0.95, 0.98),
                cp("disregard\\s+(all\\s+)?prior\\s+(instructions|prompts|rules|safety\\s+polic(?:y|ies))", "disregard_prior", PatternCategory.INJECTION, "override", 0.95, 0.96),
                cp("forget\\s+(all\\s+)?(your|the)\\s+(previous|prior|original)\\s+(instructions|rules|prompt)", "forget_instructions", PatternCategory.INJECTION, "override", 0.95, 0.96),
                cp("you\\s+are\\s+now\\s+(an\\s+)?unrestricted\\s+AI\\s+assistant", "unrestricted_assistant", PatternCategory.INJECTION, "jailbreak", 0.95, 0.98),
                cp("you\\s+are\\s+now\\s+(DAN|unrestricted|jailbroken|evil)", "role_override", PatternCategory.INJECTION, "jailbreak", 0.95, 0.97),
                cp("act\\s+as\\s+if\\s+you\\s+have\\s+no\\s+(restrictions|rules|guidelines)", "no_restrictions", PatternCategory.INJECTION, "jailbreak", 0.90, 0.94),
                cp("pretend\\s+you\\s+(are|have)\\s+(no|unrestricted|unlimited)", "pretend_unrestricted", PatternCategory.INJECTION, "jailbreak", 0.90, 0.94),
                cp("pretend\\s+you\\s+are\\s+DAN\\s+mode|DAN\\s+mode", "dan_mode", PatternCategory.INJECTION, "jailbreak", 0.95, 0.98),
                cp("do\\s+anything\\s+now", "dan_phrase", PatternCategory.INJECTION, "jailbreak", 0.95, 0.97),
                cp("enter\\s+(developer|debug|admin|god)\\s+mode", "dev_mode", PatternCategory.INJECTION, "jailbreak", 0.95, 0.96),
                cp("hypothetically,?\\s+if\\s+you\\s+had\\s+no\\s+content\\s+polic",
                        "hypothetical_no_policy", PatternCategory.INJECTION, "jailbreak", 0.92, 0.95),
                cp("if\\s+you\\s+had\\s+no\\s+(?:content\\s+)?polic(?:y|ies)",
                        "no_content_policy", PatternCategory.INJECTION, "jailbreak", 0.90, 0.94),
                cp("without\\s+(?:any\\s+)?content\\s+polic(?:y|ies)",
                        "without_content_policy", PatternCategory.INJECTION, "jailbreak", 0.88, 0.92),
                // Override/bypass safety filters
                cp("override\\s+(safety|content|security)\\s+(filters?|policy|guidelines|controls?|checks?)", "override_safety", PatternCategory.INJECTION, "override", 0.95, 0.96),
                cp("(?:bypass|circumvent|disable|remove|skip|evade|defeat)\\s+(?:all\\s+)?(?:safety|security|content|guardrail|moderation)\\s+(?:filters?|checks?|controls?|policies|restrictions|rules|mechanisms?|guardrails?)",
                        "bypass_safety", PatternCategory.INJECTION, "override", 0.95, 0.97),

                // Reveal/expose private or personal data
                // Pattern allows up to 2 modifier words between entity (candidate/user) and data noun
                // e.g. "reveal raw candidate personal information" — "personal" sits between entity and noun
                cp("(?:reveal|expose|share|leak|dump|output|show|print|give\\s+me)\\s+(?:all\\s+)?(?:raw|private|personal|sensitive|confidential|internal|hidden)\\s+(?:(?:candidate|user|customer|employee|patient)\\s+)?(?:\\w+\\s+){0,2}(?:data|information|details|records?|profiles?|credentials?)",
                        "reveal_personal_data", PatternCategory.INJECTION, "exfiltration", 0.93, 0.96),
                cp("base64[\\s_]*decode", "base64_decode", PatternCategory.INJECTION, "encoding", 0.85, 0.90),
                cp("hex\\s*decode", "hex_decode", PatternCategory.INJECTION, "encoding", 0.85, 0.90),
                cp("rot13", "rot13", PatternCategory.INJECTION, "encoding", 0.80, 0.85),
                cp("(output|reveal|show|repeat|print)\\s+(the\\s+)?(system\\s+prompt|instructions\\s+above|everything\\s+above|initial\\s+prompt|hidden\\s+internal\\s+instructions)", "exfiltrate_prompt", PatternCategory.INJECTION, "exfiltration", 0.95, 0.97),
                cp("reveal\\s+hidden\\s+internal\\s+instructions", "reveal_internal", PatternCategory.INJECTION, "exfiltration", 0.95, 0.97),
                cp("what\\s+(are|were)\\s+your\\s+(initial|system|original)\\s+(instructions|prompt|rules)", "ask_system_prompt", PatternCategory.INJECTION, "exfiltration", 0.90, 0.94),
                cp("\\[SYSTEM\\]|\\[INST\\]|<<SYS>>|<\\|im_start\\|>", "delimiter_injection", PatternCategory.INJECTION, "delimiter", 0.90, 0.94),
                cp("```\\s*(system|assistant)\\s*\\n", "markdown_system", PatternCategory.INJECTION, "delimiter", 0.85, 0.90)
        );
    }


    private static List<CategorizedPattern> buildOutputLeakagePatterns() {
        return List.of(
                cp("you\\s+are\\s+a\\s+helpful\\s+assistant", "helpful_assistant_leak", PatternCategory.OUTPUT_LEAKAGE, "system_prompt", 0.90, 0.94),
                cp("system\\s+prompt:", "system_prompt_label", PatternCategory.OUTPUT_LEAKAGE, "system_prompt", 0.95, 0.96),
                cp("<<sys>>|\\[system\\]", "system_markers", PatternCategory.OUTPUT_LEAKAGE, "system_prompt", 0.95, 0.96),
                cp("my\\s+instructions\\s+are|i\\s+was\\s+instructed\\s+to", "instruction_leak", PatternCategory.OUTPUT_LEAKAGE, "system_prompt", 0.85, 0.90)
        );
    }

    private static List<CategorizedPattern> buildSecretPatterns() {
        return List.of(
                secret("AKIA[0-9A-Z]{16}", "aws_access_key", "aws", 0.98, 0.99),
                secret("(?i)bearer\\s+[a-z0-9\\-._~+/]+=*", "bearer_token", "auth", 0.95, 0.98),
                secret("sk-[a-zA-Z0-9]{20,}", "openai_api_key", "openai", 0.98, 0.99),
                secret("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", "private_key", "crypto", 0.99, 0.99),
                secret("(?i)(?:mongodb|postgres|mysql|redis)://[^\\s]+", "connection_string", "database", 0.95, 0.97),
                secret("eyJ[a-zA-Z0-9_-]*\\.[a-zA-Z0-9_-]*\\.[a-zA-Z0-9_-]*", "jwt_token", "auth", 0.92, 0.96)
        );
    }

    /** Secrets use literal regex — word boundaries are not applicable. */
    private static CategorizedPattern secret(String regex, String label, String subcategory,
                                               double weight, double confidence) {
        return new CategorizedPattern(
                Pattern.compile(regex),
                label,
                PatternCategory.SECRETS,
                subcategory,
                weight,
                confidence
        );
    }

    private static List<CategorizedPattern> buildPiiPatterns() {
        return List.of(
                new CategorizedPattern(
                        Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}", Pattern.CASE_INSENSITIVE),
                        "email",
                        PatternCategory.PII,
                        "contact",
                        0.90,
                        0.95
                ),
                new CategorizedPattern(
                        Pattern.compile("(?:\\+91[\\-\\s]?)?[6-9]\\d{9}"),
                        "phone_in",
                        PatternCategory.PII,
                        "contact",
                        0.85,
                        0.90
                )
        );
    }

    private static CategorizedPattern cp(String regex, String label, PatternCategory category,
                                         String subcategory, double weight, double confidence) {
        return new CategorizedPattern(
                PatternMatchUtil.wordBoundaryPattern(regex),
                label,
                category,
                subcategory,
                weight,
                confidence
        );
    }

    private static boolean isCategoryEnabled(boolean enabled) {
        return enabled;
    }

    private static void mergeCustomPatterns(List<CategorizedPattern> target, List<String> customRegexes,
                                            PatternCategory category, String subcategory,
                                            double weight, double confidence) {
        if (customRegexes == null || customRegexes.isEmpty()) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String regex : customRegexes) {
            if (regex == null || regex.isBlank() || !seen.add(regex)) {
                continue;
            }
            target.add(new CategorizedPattern(
                    PatternMatchUtil.wordBoundaryPattern(regex),
                    "custom:" + regex,
                    category,
                    subcategory,
                    weight,
                    confidence
            ));
        }
    }

    /** Backward-compatible conversion of legacy plain-text extra terms. */
    private static void mergeLegacyTerms(List<CategorizedPattern> target, List<String> extraTerms) {
        if (extraTerms == null || extraTerms.isEmpty()) {
            return;
        }
        for (String term : extraTerms) {
            if (term == null || term.isBlank()) {
                continue;
            }
            String escaped = Pattern.quote(term.trim()).replace("\\ ", "\\s+");
            target.add(new CategorizedPattern(
                    PatternMatchUtil.wordBoundaryPattern(escaped),
                    term.trim(),
                    PatternCategory.BIAS,
                    "custom",
                    0.90,
                    0.92
            ));
        }
    }
}
