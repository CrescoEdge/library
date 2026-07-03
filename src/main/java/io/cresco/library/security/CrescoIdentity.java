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
        // Escape the RFC 2253 special characters so identities containing them stay well-formed.
        sb.append(type).append('=').append(value.replaceAll("([,+\"\\\\<>;=])", "\\\\$1"));
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
