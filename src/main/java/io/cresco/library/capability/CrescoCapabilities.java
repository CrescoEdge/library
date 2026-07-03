package io.cresco.library.capability;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Class-level annotation on an {@link io.cresco.library.plugin.Executor} implementation that declares the
 * bundle-wide defaults for its {@link CrescoAction} methods in the capability inventory. Per-action
 * annotations can override {@code target}/{@code routingParams}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CrescoCapabilities {

    /** Namespace for tool names: cresco_&lt;namespace&gt;_&lt;action&gt;. E.g. "global", "agent", "stunnel". */
    String namespace();

    /** Human/LLM summary of this bundle/tier's role in the fabric. */
    String summary() default "";

    /** Default routing target for actions here: global | regional | agent | plugin. */
    String target();

    /** Default identity params a caller must supply to route actions here (e.g. {"region","agent","pluginid"}). */
    String[] routingParams() default {};
}
