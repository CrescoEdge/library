package io.cresco.library.capability;

/** Serializable descriptor for one action input/output param (see {@link CapabilityScanner}). */
public class ParamDescriptor {
    public String name;
    public String type;        // json-schema type
    public boolean required;
    public String description;
    public boolean compressed;

    public ParamDescriptor() {}

    public ParamDescriptor(String name, String type, boolean required, String description, boolean compressed) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.description = description;
        this.compressed = compressed;
    }
}
