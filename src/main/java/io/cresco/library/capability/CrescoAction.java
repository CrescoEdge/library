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

    /**
     * The caller roles allowed to call this action: any of {@code infra}, {@code admin}, {@code plugin}, {@code user}
     * ({@link io.cresco.library.security.Caller}). {@code PluginBuilder.msgIn} checks it
     * ({@link io.cresco.library.security.ActionAuthorizer}) before the executor runs and answers a refused call with
     * {@code status_code=403}; with {@code broker_security_enabled=false} (insecure development mode) nothing is checked.
     *
     * <p>Empty (the default) means the action is open: to any caller of this agent's own tenant, and to {@code infra} or
     * {@code admin} of a superuser tenant. A non-empty list: the caller must hold one of the listed roles, in this agent's
     * tenant -- or, for {@code infra} and {@code admin} only, in a superuser tenant. The privileged floor
     * ({@link io.cresco.library.security.ActionAuthorizer#PRIVILEGED}) applies whatever this says. See
     * {@code controller/docs/caller-authorization.md} section 3.
     */
    String[] roles() default {};

    /**
     * From where this action is honoured ({@link io.cresco.library.security.ActionAuthorizer.Direction}): {@code "down"}
     * (management: only from the receiving node itself or a controller above it), {@code "up"} (a child reporting to its
     * parent: only from the node itself or a node below it) or {@code "any"} (from anywhere). Empty (the default) inherits:
     * {@code down} for an action of the privileged floor
     * ({@link io.cresco.library.security.ActionAuthorizer#PRIVILEGED}), {@code any} for every other. An action of the
     * privileged floor is {@code down} and stays so: declaring it {@code "any"} or {@code "up"} here fails the plugin's
     * start, as any unknown value does. Role and tenant are checked first and are unchanged by it; a controller of an
     * earlier release does not look at it. See {@code controller/docs/caller-authorization.md}, "Authority follows the tree".
     */
    String direction() default "";
}
