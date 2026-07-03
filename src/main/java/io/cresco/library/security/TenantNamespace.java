package io.cresco.library.security;

/**
 * Tenant destination namespacing — the syntactic half of Cresco's "glocal" tenant isolation (the
 * cryptographic half is {@link CrescoIdentity}, the tenant bound into the cert DN). Every Cresco-managed
 * destination (MsgEvent inbox queues AND dataplane topics) is qualified with the owning tenant as a
 * leading name segment, so any broker — including a bridge — can enforce isolation with a purely local
 * string check against the connection's cert-bound tenant. See {@code docs/tenant-isolation-design.md}.
 *
 * <p>Qualified form: <pre>T.&lt;tenant&gt;.&lt;raw-destination&gt;</pre> e.g. {@code T.tenantA.region-a_agent1}
 * or {@code T.tenantA.region.event}. The literal first segment {@code "T"} is the marker; a raw Cresco
 * destination ({@code region_agent} with underscores, or {@code agent.event}/{@code region.event}/
 * {@code global.event}) never begins with {@code "T."}, so qualified and raw names are unambiguous and
 * qualification is idempotent.
 *
 * <p>All logic is pure and dependency-free so it is usable from the controller, the dataplane, and any
 * plugin via the shared library. Everything here is a no-op unless the caller has {@code tenant_namespacing}
 * enabled; when disabled the fabric uses raw names exactly as before.
 */
public final class TenantNamespace {

    private TenantNamespace() { }

    /** Literal marker segment that begins every tenant-qualified destination. */
    public static final String MARKER = "T";

    /** Wildcard tenant segment for SUPERUSER/infra consumers that must span all tenants ({@code T.*.<raw>}). */
    public static final String WILDCARD = "*";

    /**
     * MsgEvent param that carries the origin tenant with the message, so every relaying producer qualifies
     * the (always-raw) forward destination with the message's tenant rather than the relay's local tenant.
     */
    public static final String TENANT_PARAM = "cresco_tenant";

    /** True if {@code dest} is already tenant-qualified (begins with the {@code "T."} marker). */
    public static boolean isNamespaced(String dest) {
        return dest != null && dest.startsWith(MARKER + ".");
    }

    /** The prefix {@code "T.<tenant>."} that all of a tenant's qualified destinations share. */
    public static String prefix(String tenant) {
        return MARKER + "." + tenant + ".";
    }

    /**
     * Qualify a raw destination with {@code tenant}. Idempotent (returns as-is if already qualified) and a
     * no-op when {@code tenant} is null/empty, so callers can invoke it unconditionally behind the flag.
     */
    public static String qualify(String tenant, String rawDest) {
        if (rawDest == null || tenant == null || tenant.isEmpty()) return rawDest;
        if (isNamespaced(rawDest)) return rawDest;
        return prefix(tenant) + rawDest;
    }

    /** Wildcard-tenant form {@code T.*.<raw>} for an infra/superuser consumer that must receive every tenant. */
    public static String wildcard(String rawDest) {
        if (rawDest == null) return null;
        if (isNamespaced(rawDest)) return rawDest;
        return MARKER + "." + WILDCARD + "." + rawDest;
    }

    /** The tenant of a qualified destination, or null if not qualified. {@code T.tenantA.foo} -> {@code tenantA}. */
    public static String tenantOf(String dest) {
        if (!isNamespaced(dest)) return null;
        int start = MARKER.length() + 1;                 // past "T."
        int end = dest.indexOf('.', start);
        if (end < 0) return null;
        return dest.substring(start, end);
    }
}
