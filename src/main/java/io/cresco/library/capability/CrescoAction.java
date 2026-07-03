package io.cresco.library.capability;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Marks a handler method as a self-describing Cresco message action for the LLM-tool-calling capability
 * inventory. Placed on the per-action handler method (extract inline {@code switch} cases into methods so
 * each carries its own annotation). {@link CapabilityScanner} reflects these into ActionDescriptors, and
 * a drift test asserts every switch case has a matching {@code @CrescoAction} name.
 *
 * <p>The pairing {@code (type, name)} is the dispatch key: an EXEC message with param
 * {@code action=name} routes to this method. {@code summary} is the LLM tool description of WHAT it does;
 * {@code why} explains WHEN/WHY to use it. Together they become the tool "description".</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Repeatable(CrescoActions.class)
public @interface CrescoAction {

    /** The action string (switch case value), e.g. "listagents", "configsrctunnel". */
    String name();

    /** The MsgEvent.Type this action is dispatched under: EXEC (default), CONFIG, INFO, WATCHDOG, KPI. */
    String type() default "EXEC";

    /** WHAT the action does — one clear sentence. Becomes the first line of the tool description. */
    String summary();

    /** WHEN/WHY to use it (context, side effects, ordering). Appended to the tool description. */
    String why() default "";

    /** Input params (become the tool input_schema). */
    CrescoParam[] params() default {};

    /** Output params set on the reply. */
    CrescoReturn[] returns() default {};

    /**
     * Routing target override: global | regional | agent | plugin. Empty => inherit the bundle default
     * from {@link CrescoCapabilities#target()}.
     */
    String target() default "";

    /**
     * Identity params a caller must supply to route the call (override). Empty => inherit
     * {@link CrescoCapabilities#routingParams()}. E.g. plugin actions need {region, agent, pluginid}.
     */
    String[] routingParams() default {};
}
