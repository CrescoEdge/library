package io.cresco.library.capability;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Container for repeated {@link CrescoAction} annotations. Lets an Executor list all of its actions at the
 * CLASS level (co-located, compile-checked, no per-case method refactor) as well as per-method. The
 * {@link CapabilityScanner} reads both.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface CrescoActions {
    CrescoAction[] value();
}
