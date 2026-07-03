package io.cresco.library.capability;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializable descriptor for the OSGi surface of a bundle — the classes/services it exposes. Populated
 * by {@link CapabilityScanner#scanOsgi}. Complements the message-action descriptors so the inventory
 * answers both "what messages can I send this node" and "what services/classes does it publish via OSGi".
 */
public class ServiceDescriptor {
    public String bundleSymbolicName;
    public String bundleVersion;
    public String bundleDescription;
    public List<String> exportedPackages = new ArrayList<>();     // Export-Package header
    public List<String> registeredServices = new ArrayList<>();   // service interface class names

    public ServiceDescriptor() {}
}
