package com.gridynamics.forge.guardrail.chain;

import com.gridynamics.forge.guardrail.GuardrailContext;
import com.gridynamics.forge.guardrail.config.GuardrailProperties;
import com.gridynamics.forge.guardrail.model.Violation;

import java.util.List;

/**
 * Truncates prompts exceeding the configured max character length.
 */
public class InputLengthValidator implements SanitizingValidator {

    private final GuardrailProperties props;

    public InputLengthValidator(GuardrailProperties props) {
        this.props = props;
    }

    @Override public String name()  { return "INPUT_LENGTH"; }
    @Override public int order()    { return 10; }

    @Override
    public List<Violation> validate(String text, GuardrailContext context) {
        int max = props.getMaxPromptLength();
        if (text.length() > max) {
            return List.of(Violation.soft(
                "PROMPT_TRUNCATED",
                "Prompt truncated from " + text.length() + " to " + max + " chars",
                name(),
                List.of("length=" + text.length(), "max=" + max)
            ));
        }
        return List.of();
    }

    @Override
    public String sanitise(String text) {
        int max = props.getMaxPromptLength();
        return text.length() > max ? text.substring(0, max) : text;
    }
}
