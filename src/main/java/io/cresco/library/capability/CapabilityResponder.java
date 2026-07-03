package io.cresco.library.capability;

import com.google.gson.Gson;
import io.cresco.library.messaging.MsgEvent;

/**
 * One-liner for a bundle to answer the standard {@code getcapabilities} EXEC action: it reflects the
 * executor's {@link CrescoAction} annotations into a {@link CapabilityDocument} and returns it as JSON on
 * the reply. Bundle symbolic name + version are read from the OSGi manifest (never hardcoded). Keeps every
 * bundle's handler uniform:
 * <pre>case "getcapabilities": return CapabilityResponder.respond(incoming, this);</pre>
 */
public final class CapabilityResponder {

    private static final Gson GSON = new Gson();

    private CapabilityResponder() {}

    public static MsgEvent respond(MsgEvent incoming, Object executor) {
        try {
            CapabilityDocument doc = CapabilityScanner.scanActions(executor);
            incoming.setParam("capabilities", GSON.toJson(doc));
            incoming.setParam("status", "10");
        } catch (Exception ex) {
            incoming.setParam("status", "9");
            incoming.setParam("status_desc", "capability scan failed: " + ex.getMessage());
        }
        return incoming;
    }

    /** The capability document JSON for an executor (used by the controller aggregator). */
    public static String documentJson(Object executor) {
        return GSON.toJson(CapabilityScanner.scanActions(executor));
    }
}
