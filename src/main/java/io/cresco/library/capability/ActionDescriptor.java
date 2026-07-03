package io.cresco.library.capability;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializable descriptor for one Cresco message action — the intermediate form between the
 * {@link CrescoAction} annotation and the emitted LLM tool spec. Carries both the LLM-facing fields
 * (summary/why/params) and the Cresco binding (msgType/target/routingParams/returns) a tool-runner needs
 * to actually invoke it.
 */
public class ActionDescriptor {
    public String namespace;              // bundle namespace (e.g. "global", "stunnel")
    public String action;                 // the switch case value
    public String msgType;                // EXEC | CONFIG | INFO | WATCHDOG | KPI
    public String summary;                // WHAT it does
    public String why;                    // WHEN/WHY to use it
    public String target;                 // global | regional | agent | plugin
    public List<String> routingParams = new ArrayList<>();
    public List<ParamDescriptor> params = new ArrayList<>();
    public List<ParamDescriptor> returns = new ArrayList<>();
    public String sourceClass;            // handler class (traceability)

    public ActionDescriptor() {}

    /** Tool name: cresco_&lt;namespace&gt;_&lt;action&gt;, sanitized to [a-zA-Z0-9_]. */
    public String toolName() {
        String raw = "cresco_" + (namespace == null ? "" : namespace) + "_" + (action == null ? "" : action);
        return raw.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
