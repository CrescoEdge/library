package io.cresco.library.capability;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Reflects {@link CrescoCapabilities}/{@link CrescoAction} annotations on an Executor implementation into
 * a {@link CapabilityDocument}. Pure reflection — no OSGi or messaging dependency — so it can run anywhere
 * (the OSGi service/package scan lives in {@link #scanOsgi}, added alongside the InventoryPrinter).
 */
public final class CapabilityScanner {

    private CapabilityScanner() {}

    /**
     * Scan an annotated Executor for its message actions.
     *
     * @param executor an Executor implementation annotated with {@link CrescoCapabilities} whose action
     *                 handler methods are annotated with {@link CrescoAction}.
     * @param bundle   the bundle/plugin id to stamp on the document (symbolic name or plugin id).
     * @param version  the bundle version.
     * @return a CapabilityDocument with one ActionDescriptor per annotated handler method.
     */
    /** Bundle symbolic name of the bundle hosting {@code clazz} (from the OSGi manifest), or its package. */
    public static String bundleName(Class<?> clazz) {
        try {
            Bundle b = FrameworkUtil.getBundle(clazz);
            if (b != null && b.getSymbolicName() != null) { return b.getSymbolicName(); }
        } catch (Throwable ignore) {}
        return (clazz != null) ? clazz.getPackageName() : "unknown";
    }

    /** Bundle version of the bundle hosting {@code clazz} (from the OSGi manifest), or "unknown". */
    public static String bundleVersion(Class<?> clazz) {
        try {
            Bundle b = FrameworkUtil.getBundle(clazz);
            if (b != null && b.getVersion() != null) { return b.getVersion().toString(); }
        } catch (Throwable ignore) {}
        return "unknown";
    }

    /** Scan an annotated Executor instance; bundle name + version are resolved from its OSGi manifest. */
    public static CapabilityDocument scanActions(Object executor) {
        if (executor == null) { return new CapabilityDocument("unknown", "unknown", "unknown", ""); }
        return scanActions(executor.getClass());
    }

    /** Scan an annotated Executor CLASS; bundle name + version are resolved from its OSGi manifest. */
    public static CapabilityDocument scanActions(Class<?> clazz) {
        return scanActions(clazz, bundleName(clazz), bundleVersion(clazz));
    }

    public static CapabilityDocument scanActions(Object executor, String bundle, String version) {
        if (executor == null) { return new CapabilityDocument("unknown", bundle, version, ""); }
        return scanActions(executor.getClass(), bundle, version);
    }

    /**
     * Scan an annotated Executor CLASS for its message actions (no instance needed — annotations are
     * static). Used to describe the controller tiers (Agent/Regional/Global executors) without
     * constructing them.
     */
    public static CapabilityDocument scanActions(Class<?> clazz, String bundle, String version) {
        if (clazz == null) { return new CapabilityDocument("unknown", bundle, version, ""); }
        CrescoCapabilities caps = clazz.getAnnotation(CrescoCapabilities.class);
        String namespace = (caps != null) ? caps.namespace() : clazz.getSimpleName();
        String summary = (caps != null) ? caps.summary() : "";
        String defTarget = (caps != null) ? caps.target() : "plugin";
        String[] defRouting = (caps != null) ? caps.routingParams() : new String[]{};

        CapabilityDocument doc = new CapabilityDocument(namespace, bundle, version, summary);
        // class-level repeated @CrescoAction (the low-refactor path) ...
        for (CrescoAction a : clazz.getAnnotationsByType(CrescoAction.class)) {
            doc.actions.add(toDescriptor(a, namespace, defTarget, defRouting, clazz.getName()));
        }
        // ... plus any per-method @CrescoAction
        for (Method m : clazz.getDeclaredMethods()) {
            for (CrescoAction a : m.getAnnotationsByType(CrescoAction.class)) {
                doc.actions.add(toDescriptor(a, namespace, defTarget, defRouting, clazz.getName()));
            }
        }
        return doc;
    }

    private static ActionDescriptor toDescriptor(CrescoAction a, String namespace, String defTarget,
                                                 String[] defRouting, String sourceClass) {
        ActionDescriptor d = new ActionDescriptor();
        d.namespace = namespace;
        d.action = a.name();
        d.msgType = a.type();
        d.summary = a.summary();
        d.why = a.why();
        d.target = a.target().isEmpty() ? defTarget : a.target();
        String[] routing = (a.routingParams().length > 0) ? a.routingParams() : defRouting;
        for (String r : routing) { d.routingParams.add(r); }
        for (CrescoParam p : a.params()) {
            d.params.add(new ParamDescriptor(p.name(), p.type(), p.required(), p.description(), p.compressed()));
        }
        for (CrescoReturn r : a.returns()) {
            d.returns.add(new ParamDescriptor(r.name(), r.type(), false, r.description(), r.compressed()));
        }
        d.sourceClass = sourceClass;
        return d;
    }

    /**
     * Scan the OSGi surface of every bundle in the framework: symbolic name/version/description, the
     * {@code Export-Package} header, and the interface names of each registered service. Answers "what
     * classes/services does this node expose via OSGi" for the capability inventory.
     */
    public static List<ServiceDescriptor> scanOsgi(BundleContext ctx) {
        List<ServiceDescriptor> out = new ArrayList<>();
        if (ctx == null) { return out; }
        try {
            for (Bundle b : ctx.getBundles()) {
                try {
                    ServiceDescriptor sd = new ServiceDescriptor();
                    sd.bundleSymbolicName = b.getSymbolicName();
                    sd.bundleVersion = (b.getVersion() != null) ? b.getVersion().toString() : "";
                    Object desc = b.getHeaders().get("Bundle-Description");
                    sd.bundleDescription = (desc != null) ? desc.toString() : "";
                    Object exp = b.getHeaders().get("Export-Package");
                    if (exp != null) {
                        // split top-level package clauses (ignore ;-attributes) on commas not inside quotes
                        for (String clause : exp.toString().split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
                            String pkg = clause.split(";")[0].trim();
                            if (!pkg.isEmpty()) { sd.exportedPackages.add(pkg); }
                        }
                    }
                    ServiceReference<?>[] refs = b.getRegisteredServices();
                    if (refs != null) {
                        for (ServiceReference<?> ref : refs) {
                            Object oc = ref.getProperty("objectClass");
                            if (oc instanceof String[]) {
                                for (String c : (String[]) oc) { sd.registeredServices.add(c); }
                            }
                        }
                    }
                    out.add(sd);
                } catch (Exception ignore) { /* skip a bundle we can't introspect */ }
            }
        } catch (Exception ignore) {}
        return out;
    }

    /** The set of {@link CrescoAction} names declared on an executor (class-level + method-level), for the drift test. */
    public static List<String> actionNames(Object executor) {
        List<String> names = new ArrayList<>();
        if (executor == null) { return names; }
        Class<?> clazz = executor.getClass();
        for (CrescoAction a : clazz.getAnnotationsByType(CrescoAction.class)) { names.add(a.name()); }
        for (Method m : clazz.getDeclaredMethods()) {
            for (CrescoAction a : m.getAnnotationsByType(CrescoAction.class)) { names.add(a.name()); }
        }
        return names;
    }
}
