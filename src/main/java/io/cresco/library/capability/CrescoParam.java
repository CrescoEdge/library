package io.cresco.library.capability;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Describes one input parameter of a Cresco message action, for the LLM-tool-calling capability
 * inventory. Nested inside {@link CrescoAction#params()}. The {@code type} is a JSON-Schema type
 * ("string", "integer", "number", "boolean", "object", "array") so the descriptor can be emitted
 * directly as a tool input_schema property.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface CrescoParam {

    /** The MsgEvent param key a caller sets (e.g. "action_region", "action_stunnel_id"). */
    String name();

    /** JSON-Schema type for the tool input_schema. */
    String type() default "string";

    /** Whether the action requires this param. */
    boolean required() default false;

    /** Human/LLM-facing description: what this param is and how it affects the action. */
    String description() default "";

    /** True if the value rides as a gzip+base64 compressed param (setCompressedParam/getCompressedParam). */
    boolean compressed() default false;
}
