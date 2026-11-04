package io.cresco.library.security;

import java.util.Locale;

/**
 * The deployment's security profile ({@code security_profile}), read by the controller and offered to plugins through
 * {@link io.cresco.library.agent.AgentService#getSecurityProfile()} so each enforces its side the same way.
 *
 * <ul>
 *   <li>{@link #PRODUCTION}: every node identity is pre-provisioned by the operator's PKI ({@code identity_keystore},
 *       {@code identity_keystore_password_file}, {@code trust_anchors}); no enrollment by a shared secret, no self-made
 *       certificate, no trust on first use, no upgrade window, no inline password; security, client authentication,
 *       tenant namespacing, message and plugin signing are mandatory. A node refuses to start, naming each problem, when
 *       anything required is missing or anything disallowed is set.</li>
 *   <li>{@link #STANDARD} (the default): secure by default, with the enrollment conveniences (discovery-secret joins,
 *       self-made and region-issued certificates, pins on first join, an explicit upgrade window).</li>
 *   <li>{@link #INSECURE}: explicit insecure debug mode only ({@code broker_security_enabled=false}); never production.</li>
 * </ul>
 */
public enum SecurityProfile {
    PRODUCTION, STANDARD, INSECURE;

    /** The configuration key. */
    public static final String KEY = "security_profile";

    /** {@code production} or {@code standard} (case-insensitive); anything else is null (a node refuses to start on it). */
    public static SecurityProfile parse(String v) {
        if (v == null || v.trim().isEmpty()) return STANDARD;
        switch (v.trim().toLowerCase(Locale.ROOT)) {
            case "production": return PRODUCTION;
            case "standard": return STANDARD;
            default: return null;
        }
    }

    /** The lower-case name as configured. */
    public String id() { return name().toLowerCase(Locale.ROOT); }
}
