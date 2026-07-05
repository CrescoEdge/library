package io.cresco.library.osgi;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/** No-op activator pinned as the library bundle's Bundle-Activator so bnd does not auto-detect
 *  multiple BundleActivators from siddhi-core's shaded log4j2. */
public class NoOpActivator implements BundleActivator {
    @Override public void start(BundleContext context) { }
    @Override public void stop(BundleContext context) { }
}
