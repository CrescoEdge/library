package io.cresco.library.capability;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializes {@link ActionDescriptor}s into LLM-ready tool specs. The {@code name}/{@code description}/
 * {@code input_schema} fields are the standard Anthropic tool-calling shape (usable verbatim as a tool
 * definition); an extra {@code cresco_binding} block carries the routing info a tool-runner needs to turn
 * a tool call back into a MsgEvent (msg_type, target tier, action, routing/identity params, return params).
 */
public final class ToolSpecSerializer {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Gson GSON_PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private ToolSpecSerializer() {}

    /** One tool object (name/description/input_schema + cresco_binding) for an action descriptor. */
    public static Map<String, Object> toToolObject(ActionDescriptor d) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", d.toolName());

        StringBuilder desc = new StringBuilder();
        if (d.summary != null) { desc.append(d.summary); }
        if (d.why != null && !d.why.isEmpty()) { desc.append("\n\nWhen/why: ").append(d.why); }
        tool.put("description", desc.toString());

        // input_schema = routing params (required to address the call) + action params
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (String rp : d.routingParams) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("type", "string");
            p.put("description", "Routing identity: target " + rp + " for this call.");
            props.put(rp, p);
            required.add(rp);
        }
        if (d.params != null) {
            for (ParamDescriptor pd : d.params) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("type", pd.type == null ? "string" : pd.type);
                if (pd.description != null && !pd.description.isEmpty()) { p.put("description", pd.description); }
                props.put(pd.name, p);
                if (pd.required) { required.add(pd.name); }
            }
        }
        schema.put("properties", props);
        schema.put("required", required);
        tool.put("input_schema", schema);

        // cresco_binding: everything a runner needs to build + route the MsgEvent and read the reply
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("msg_type", d.msgType);
        binding.put("target", d.target);
        binding.put("action", d.action);
        binding.put("routing_params", d.routingParams);
        List<Map<String, Object>> rets = new ArrayList<>();
        if (d.returns != null) {
            for (ParamDescriptor rd : d.returns) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("name", rd.name);
                r.put("type", rd.type);
                r.put("description", rd.description);
                r.put("compressed", rd.compressed);
                rets.add(r);
            }
        }
        binding.put("returns", rets);
        tool.put("cresco_binding", binding);
        return tool;
    }

    /** A JSON array of tool objects for the given descriptors. */
    public static String toAnthropicTools(List<ActionDescriptor> descriptors, boolean pretty) {
        List<Map<String, Object>> tools = new ArrayList<>();
        if (descriptors != null) {
            for (ActionDescriptor d : descriptors) { tools.add(toToolObject(d)); }
        }
        return (pretty ? GSON_PRETTY : GSON).toJson(tools);
    }

    /** All tools flattened from a set of capability documents (e.g. a whole node or the whole mesh). */
    public static String toAnthropicTools(Iterable<CapabilityDocument> docs, boolean pretty) {
        List<ActionDescriptor> all = new ArrayList<>();
        if (docs != null) {
            for (CapabilityDocument doc : docs) {
                if (doc != null && doc.actions != null) { all.addAll(doc.actions); }
            }
        }
        return toAnthropicTools(all, pretty);
    }
}
