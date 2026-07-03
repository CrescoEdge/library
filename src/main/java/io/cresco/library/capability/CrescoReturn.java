package io.cresco.library.capability;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Describes one output param an action sets on its reply MsgEvent, for the capability inventory.
 * Nested inside {@link CrescoAction#returns()}. Lets a tool-runner know which reply params carry the
 * result and how to read them (plain vs. compressed).
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface CrescoReturn {

    /** The reply param key the action sets (e.g. "agentslist", "status", "metrics"). */
    String name();

    /** JSON-Schema-ish type of the value ("string", "object", "integer", "binary", ...). */
    String type() default "string";

    /** Human/LLM-facing description of what this reply param contains. */
    String description() default "";

    /** True if the value is a gzip+base64 compressed param (read via getCompressedParam). */
    boolean compressed() default false;
}
