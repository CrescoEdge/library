package io.cresco.library.security;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.security.cert.X509Certificate;
import java.util.Objects;

/**
 * The Cresco fabric identity carried by (or asserted for) a node: {@code tenant / region / agent}
 * plus an optional stable node UID. This is the identity that agents use to identify themselves and
 * to be identified by other agents.
 *
 * <p>The canonical on-the-wire form is the subject DN of a node's leaf X.509 certificate:
 * <pre>CN=&lt;agent&gt;, OU=&lt;region&gt;, O=&lt;tenant&gt;, UID=&lt;uid&gt;</pre>
 * Binding identity <em>into</em> the certificate (rather than a sidecar filename) is what lets any
 * holder of the issuing region-CA cert cryptographically verify the claim — see
 * {@code docs/distributed-identity-trust-design.md}. This class is pure JDK (no provider dependency)
 * so it is usable from every plugin via the shared library.
 */
public final class CrescoIdentity {

    // LDAP attribute type for userid (RFC 2253 renders this OID unless mapped to the "UID" keyword).
    private static final String UID_OID = "0.9.2342.19200300.100.1.1";

    private final String tenant;
    private final String region;
    private final String agent;
    private final String uid;

    private CrescoIdentity(String tenant, String region, String agent, String uid) {
        this.tenant = tenant;
        this.region = region;
        this.agent = agent;
        this.uid = uid;
    }

    public static CrescoIdentity of(String tenant, String region, String agent, String uid) {
        return new CrescoIdentity(tenant, region, agent, uid);
    }

    /** Parse identity from an X.509 certificate's subject DN. Returns {@code null} on any failure. */
    public static CrescoIdentity fromCertificate(X509Certificate cert) {
        if (cert == null) {
            return null;
        }
        try {
            return fromDN(cert.getSubjectX500Principal().getName());
        } catch (Exception ex) {
            return null;
        }
    }

    /** Parse identity from an RFC 2253 distinguished name. Returns {@code null} if it can't be parsed. */
    public static CrescoIdentity fromDN(String dn) {
        if (dn == null || dn.isEmpty()) {
            return null;
        }
        try {
            String tenant = null, region = null, agent = null, uid = null;
            LdapName ln = new LdapName(dn);
            for (Rdn rdn : ln.getRdns()) {
                String type = rdn.getType();
                String value = String.valueOf(rdn.getValue());
                if ("CN".equalsIgnoreCase(type)) {
                    agent = value;
                } else if ("OU".equalsIgnoreCase(type)) {
                    region = value;
                } else if ("O".equalsIgnoreCase(type)) {
                    tenant = value;
                } else if ("UID".equalsIgnoreCase(type) || UID_OID.equals(type)) {
                    uid = value;
                }
            }
            if (agent == null && region == null && tenant == null) {
                return null; // not a Cresco identity DN
            }
            return new CrescoIdentity(tenant, region, agent, uid);
        } catch (Exception ex) {
            return null;
        }
    }

    /** Build the canonical subject DN string for this identity (omits null components). */
    public String toX500Name() {
        StringBuilder sb = new StringBuilder();
        if (agent != null)  append(sb, "CN", agent);
        if (region != null) append(sb, "OU", region);
        if (tenant != null) append(sb, "O", tenant);
        if (uid != null)    append(sb, "UID", uid);
        return sb.toString();
    }

    private static void append(StringBuilder sb, String type, String value) {
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(type).append('=').append(escape(value));
    }

    /**
     * RFC 4514 attribute-value escaping: the specials {@code , + " \ < > ; =}, a leading {@code #} or space, a trailing
     * space, and NUL. Without the leading/trailing rules a value like {@code " rc"} or {@code "#0c027263"} is read back by
     * a DN parser as {@code "rc"} (trimmed, or decoded from hex DER) -- somebody else's name.
     */
    static String escape(String value) {
        StringBuilder b = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ',' || c == '+' || c == '"' || c == '\\' || c == '<' || c == '>' || c == ';' || c == '='
                    || (i == 0 && (c == '#' || c == ' ')) || (i == value.length() - 1 && c == ' ')) {
                b.append('\\').append(c);
            } else if (c == 0) {
                b.append("\\00");
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static final java.util.regex.Pattern NAME = java.util.regex.Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9.-]{0,62}[A-Za-z0-9])?");

    /**
     * The Cresco identity name grammar (tenant, region, agent, uid): 1-64 of {@code A-Z a-z 0-9 . -}, starting and ending
     * with a letter or digit. No whitespace, {@code #}, DN specials, control characters, or {@code _} (the separator in
     * an agent path {@code <region>_<agent>}). The controller issues, pins and compares only such names.
     */
    public static boolean isValidName(String s) {
        return s != null && NAME.matcher(s).matches();
    }

    private static final java.util.regex.Pattern TENANT = java.util.regex.Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,62}[A-Za-z0-9])?");

    /**
     * The tenant name grammar: {@link #isValidName} without {@code .}. Tenant-namespaced destinations are
     * {@code T.<tenant>.<name>} ({@link TenantNamespace}), so a tenant {@code t1} would otherwise be a string prefix of
     * tenant {@code t1.x}'s namespace.
     */
    public static boolean isValidTenant(String s) {
        return s != null && TENANT.matcher(s).matches();
    }

    /**
     * The identity a certificate names, read strictly for security decisions: single-valued CN, OU and O exactly once
     * each (UID at most once, nothing else), every value a valid name ({@link #isValidName}; the tenant a valid tenant name,
     * {@link #isValidTenant}). Anything else is null.
     */
    public static CrescoIdentity fromCertificateStrict(X509Certificate cert) {
        if (cert == null) return null;
        try {
            LdapName ln = new LdapName(cert.getSubjectX500Principal().getName());
            String tenant = null, region = null, agent = null, uid = null;
            for (Rdn rdn : ln.getRdns()) {
                if (rdn.size() != 1 || !(rdn.getValue() instanceof String)) return null;
                String type = rdn.getType(), value = (String) rdn.getValue();
                if (!isValidName(value)) return null;
                if ("CN".equalsIgnoreCase(type)) { if (agent != null) return null; agent = value; }
                else if ("OU".equalsIgnoreCase(type)) { if (region != null) return null; region = value; }
                else if ("O".equalsIgnoreCase(type)) { if (tenant != null || !isValidTenant(value)) return null; tenant = value; }
                else if ("UID".equalsIgnoreCase(type) || UID_OID.equals(type)) { if (uid != null) return null; uid = value; }
                else return null;
            }
            if (agent == null || region == null || tenant == null) return null;
            return new CrescoIdentity(tenant, region, agent, uid);
        } catch (Exception ex) {
            return null;
        }
    }

    public String getTenant() { return tenant; }
    public String getRegion() { return region; }
    public String getAgent()  { return agent; }
    public String getUid()    { return uid; }

    /** The {@code <region>_<agent>} path used as the broker queue name and the legacy trust alias. */
    public String getAgentPath() {
        return region + "_" + agent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CrescoIdentity)) return false;
        CrescoIdentity that = (CrescoIdentity) o;
        return Objects.equals(tenant, that.tenant)
                && Objects.equals(region, that.region)
                && Objects.equals(agent, that.agent)
                && Objects.equals(uid, that.uid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenant, region, agent, uid);
    }

    @Override
    public String toString() {
        return "CrescoIdentity{tenant=" + tenant + ", region=" + region
                + ", agent=" + agent + ", uid=" + uid + "}";
    }
}
