package com.gridynamics.forge.guardrail.spi;

import com.gridynamics.forge.guardrail.chain.GuardrailValidator;

/**
 * Extension point for team-specific guardrail validators.
 *
 * <p>Any Spring bean implementing this interface is auto-discovered
 * and added to the guardrail chain. Recommended order: 900+.
 *
 * <pre>{@code
 * @Component
 * public class MyCustomValidator implements CustomGuardrailValidator {
 *     @Override public String name()  { return "MY_CHECK"; }
 *     @Override public int order()    { return 950; }
 *     @Override
 *     public List<Violation> validate(String text, GuardrailContext ctx) {
 *         // your logic
 *         return List.of();
 *     }
 * }
 * }</pre>
 */
public interface CustomGuardrailValidator extends GuardrailValidator {
}
