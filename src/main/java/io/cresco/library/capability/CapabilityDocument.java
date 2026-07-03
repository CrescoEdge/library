package io.cresco.library.capability;

import java.util.ArrayList;
import java.util.List;

/**
 * The full self-description of one bundle/tier for the capability inventory: its message actions plus its
 * OSGi service/package surface. Serialized to JSON by the {@code getcapabilities} EXEC and by the Felix
 * InventoryPrinter, and aggregated fabric-wide by the controller's {@code getcapabilityinventory}.
 */
public class CapabilityDocument {
    public String namespace;                 // e.g. "global", "stunnel"
    public String bundle;                    // symbolic name / plugin id
    public String version;
    public String summary;                   // role of this bundle/tier
    public List<ActionDescriptor> actions = new ArrayList<>();
    public List<ServiceDescriptor> services = new ArrayList<>();   // OSGi surface (usually 1 self entry)

    public CapabilityDocument() {}

    public CapabilityDocument(String namespace, String bundle, String version, String summary) {
        this.namespace = namespace;
        this.bundle = bundle;
        this.version = version;
        this.summary = summary;
    }
}
