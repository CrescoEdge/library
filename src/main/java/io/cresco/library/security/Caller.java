package io.cresco.library.security;

import io.cresco.library.messaging.MsgEvent;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The verified caller of a message: who sent it, in which tenant, with which roles.
 *
 * <p>Infrastructure sets it on the receiving side; a sender can never set it (see
 * {@code controller/docs/caller-authorization.md}). The three reserved parameters below are removed from every message
 * as it arrives off the wire and from every message a plugin hands to its controller, and are then written by the
 * stamping side: the broker (from the producer connection's TLS identity), the controller (for in-JVM delivery), or the
 * controller on behalf of an API user ({@code PluginBuilder.msgOutOnBehalfOf}, from allowed bundles only).
 *
 * <p>Principal forms:
 * <ul>
 *   <li>{@code controller:<tenant>/<region>/<agent>} -- a Cresco controller (agent, region or global mode), role {@code infra};</li>
 *   <li>{@code agent:<tenant>/<region>/<agent>} -- reserved, parsed but not issued;</li>
 *   <li>{@code plugin:<tenant>/<region>/<agent>/<pluginid>} -- a plugin, role {@code plugin};</li>
 *   <li>{@code api:<tenant>/<user>} -- an API user authenticated by wsapi, roles {@code admin} and/or {@code user};</li>
 *   <li>{@code anonymous} -- insecure development mode only, every role.</li>
 * </ul>
 *
 * <p>Immutable and thread-safe.
 */
public final class Caller {

    /** Reserved parameter: the caller principal. */
    public static final String PARAM_CALLER = "cresco_caller";
    /** Reserved parameter: the caller's tenant. */
    public static final String PARAM_TENANT = "cresco_caller_tenant";
    /** Reserved parameter: the caller's roles, a comma list. */
    public static final String PARAM_ROLES = "cresco_caller_roles";

    /**
     * Reserved parameter: the former region a node answers at ({@code region_alias}, GaiaKeep gfs#26), as its region's
     * registry records it from the node's own verified registration. Set by infrastructure (the broker the node is attached
     * to, or the node's own controller in-JVM), never by a sender: see {@link #regionAliasOf}.
     */
    public static final String PARAM_ALIAS = "cresco_caller_alias";

    /** Every reserved parameter name. A sender's value for any of them is never read. */
    public static final Set<String> RESERVED_PARAMS =
            Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(PARAM_CALLER, PARAM_TENANT, PARAM_ROLES, PARAM_ALIAS)));

    /** A Cresco controller (agent, region or global). */
    public static final String ROLE_INFRA = "infra";
    /** An administrator, authenticated by wsapi. Counts across tenants only in a superuser tenant. */
    public static final String ROLE_ADMIN = "admin";
    /** A plugin running inside an agent. */
    public static final String ROLE_PLUGIN = "plugin";
    /** An ordinary API user, authenticated by wsapi. */
    public static final String ROLE_USER = "user";

    /** Every role, in a stable order. */
    public static final Set<String> ALL_ROLES =
            Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(ROLE_INFRA, ROLE_ADMIN, ROLE_PLUGIN, ROLE_USER)));

    /** The principal of the insecure-mode caller. */
    public static final String ANONYMOUS = "anonymous";

    /** The kind of principal, from its prefix. */
    public enum Kind { CONTROLLER, AGENT, PLUGIN, API, ANONYMOUS }

    private final String principal;
    private final String tenant;
    private final Set<String> roles;
    private final Kind kind;
    /** The principal's parts after its prefix (tenant first), as parsed; null for {@code anonymous}. Never handed out. */
    private final String[] parts;

    private Caller(String principal, String tenant, Set<String> roles, Kind kind, String[] parts) {
        this.principal = principal;
        this.tenant = tenant;
        this.roles = roles;
        this.kind = kind;
        this.parts = parts;
    }

    /**
     * A caller from its parts. Throws {@link IllegalArgumentException} unless the principal is well formed, its tenant
     * matches {@code tenant}, and every role is known.
     */
    public static Caller of(String principal, String tenant, Collection<String> roles) {
        Caller c = parse(principal, tenant, roles == null ? null : String.join(",", roles));
        if (c == null) {
            throw new IllegalArgumentException("not a valid caller: principal=" + principal + " tenant=" + tenant + " roles=" + roles);
        }
        return c;
    }

    /** A Cresco controller: {@code controller:<tenant>/<region>/<agent>} with {@code infra}. */
    public static Caller controller(String tenant, String region, String agent) {
        return of("controller:" + tenant + "/" + region + "/" + agent, tenant, Collections.singleton(ROLE_INFRA));
    }

    /** A plugin: {@code plugin:<tenant>/<region>/<agent>/<pluginid>} with {@code plugin}. */
    public static Caller plugin(String tenant, String region, String agent, String pluginId) {
        return of("plugin:" + tenant + "/" + region + "/" + agent + "/" + pluginId, tenant, Collections.singleton(ROLE_PLUGIN));
    }

    /**
     * An API user authenticated by wsapi: {@code api:<tenant>/<user>} with roles drawn from {@code admin} and
     * {@code user} only. Throws {@link IllegalArgumentException} for any other role, an empty role set, or a bad name.
     */
    public static Caller api(String tenant, String user, Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("an API caller needs a role (admin or user)");
        }
        for (String r : roles) {
            if (!ROLE_ADMIN.equals(r) && !ROLE_USER.equals(r)) {
                throw new IllegalArgumentException("an API caller can hold only admin or user, not " + r);
            }
        }
        return of("api:" + tenant + "/" + user, tenant, roles);
    }

    /** The insecure development-mode caller: {@code anonymous}, no tenant, every role. */
    public static Caller anonymous() {
        return new Caller(ANONYMOUS, "", ALL_ROLES, Kind.ANONYMOUS, null);
    }

    /**
     * Parse a caller from the three reserved values. Returns null unless the values are well formed and consistent:
     * the principal names the given tenant, every name is a valid Cresco name, and every role is known. An
     * {@code anonymous} caller is parsed with every role whatever the roles value says.
     */
    public static Caller parse(String principal, String tenant, String rolesCsv) {
        if (principal == null || tenant == null || rolesCsv == null) return null;
        if (ANONYMOUS.equals(principal)) return anonymous();
        int colon = principal.indexOf(':');
        if (colon <= 0) return null;
        String prefix = principal.substring(0, colon);
        String[] parts = principal.substring(colon + 1).split("/", -1);
        Kind kind;
        switch (prefix) {
            case "controller": kind = Kind.CONTROLLER; if (parts.length != 3) return null; break;
            case "agent":      kind = Kind.AGENT;      if (parts.length != 3) return null; break;
            case "plugin":     kind = Kind.PLUGIN;     if (parts.length != 4) return null; break;
            case "api":        kind = Kind.API;        if (parts.length != 2) return null; break;
            default: return null;
        }
        if (!CrescoIdentity.isValidTenant(parts[0]) || !parts[0].equals(tenant)) return null;
        for (int i = 1; i < parts.length; i++) {
            if (!isValidPart(kind, i, parts[i])) return null;
        }
        Set<String> roles = new TreeSet<>();
        for (String r : rolesCsv.split(",", -1)) {
            String t = r.trim();
            if (t.isEmpty()) continue;
            if (!ALL_ROLES.contains(t)) return null;
            roles.add(t);
        }
        if (roles.isEmpty()) return null;
        return new Caller(principal, tenant, Collections.unmodifiableSet(roles), kind, parts);
    }

    /** Plugin ids and API user names are wider than node names; neither may carry the separators. */
    private static boolean isValidPart(Kind kind, int index, String s) {
        if (s == null || s.isEmpty() || s.length() > 128) return false;
        boolean wide = (kind == Kind.PLUGIN && index == 3) || (kind == Kind.API && index == 1);
        if (!wide) return CrescoIdentity.isValidName(s);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '@';
            if (!ok) return false;
        }
        return true;
    }

    /** The caller stamped on {@code msg}, or null if it carries none (or a malformed one). */
    public static Caller fromMsgEvent(MsgEvent msg) {
        if (msg == null || msg.getParams() == null) return null;
        return parse(msg.getParam(PARAM_CALLER), msg.getParam(PARAM_TENANT), msg.getParam(PARAM_ROLES));
    }

    /**
     * The former region the verified node of {@code msg}'s caller also answers at ({@link #PARAM_ALIAS}), or null: none,
     * no node caller (controller, agent or plugin), or a value that is not a plain region name other than the node's own.
     * Like the stamp itself it is infrastructure's: a sender's value is stripped on arrival.
     */
    public static String regionAliasOf(MsgEvent msg) {
        if (msg == null || msg.getParams() == null) return null;
        String alias = msg.getParam(PARAM_ALIAS);
        Caller who = fromMsgEvent(msg);
        if (alias == null || who == null) return null;
        if (who.kind != Kind.CONTROLLER && who.kind != Kind.AGENT && who.kind != Kind.PLUGIN) return null;
        return validAlias(alias, who.getRegion());
    }

    /** {@code alias} when it is a plain region name (no {@code _}, {@code .}, {@code /}) other than {@code region}, else null. */
    public static String validAlias(String alias, String region) {
        if (alias == null) return null;
        alias = alias.trim();
        if (alias.isEmpty() || alias.length() > 64 || alias.equals(region)) return null;
        for (int i = 0; i < alias.length(); i++) {
            char c = alias.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-')) return null;
        }
        return alias;
    }

    /** Write the former region {@code alias} of this (node) caller onto {@code msg}; null or invalid removes it. */
    public void stampRegionAlias(MsgEvent msg, String alias) {
        String a = (kind == Kind.CONTROLLER || kind == Kind.AGENT || kind == Kind.PLUGIN) ? validAlias(alias, getRegion()) : null;
        if (a == null) msg.removeParam(PARAM_ALIAS);
        else msg.setParam(PARAM_ALIAS, a);
    }

    /** Remove the reserved parameters from {@code msg}. */
    public static void strip(MsgEvent msg) {
        if (msg == null || msg.getParams() == null) return;
        for (String p : RESERVED_PARAMS) msg.removeParam(p);
    }

    /** True if {@code msg} carries any of the reserved parameters. */
    public static boolean hasAny(MsgEvent msg) {
        if (msg == null || msg.getParams() == null) return false;
        for (String p : RESERVED_PARAMS) if (msg.getParams().containsKey(p)) return true;
        return false;
    }

    /** Write this caller onto {@code msg}, overwriting any reserved value already there (and removing any alias). */
    public void stamp(MsgEvent msg) {
        msg.removeParam(PARAM_ALIAS);
        msg.setParam(PARAM_CALLER, principal);
        msg.setParam(PARAM_TENANT, tenant);
        msg.setParam(PARAM_ROLES, getRolesCsv());
    }

    public String getPrincipal() { return principal; }
    public String getTenant() { return tenant; }
    public Set<String> getRoles() { return roles; }
    public Kind getKind() { return kind; }
    public boolean hasRole(String role) { return roles.contains(role); }
    public boolean isAnonymous() { return kind == Kind.ANONYMOUS; }
    public String getRolesCsv() { return String.join(",", roles); }

    /** {@code <region>} for a controller, agent or plugin caller; null otherwise. */
    public String getRegion() { return part(1); }
    /** {@code <agent>} for a controller, agent or plugin caller; null otherwise. */
    public String getAgent() { return part(2); }
    /** {@code <pluginid>} for a plugin caller; null otherwise. */
    public String getPluginId() { return kind == Kind.PLUGIN ? part(3) : null; }
    /** {@code <user>} for an API caller; null otherwise. */
    public String getUser() { return kind == Kind.API ? parts[1] : null; }

    /** Read on every message that has a direction (where its caller's node stands): the parts are kept, never split again. */
    private String part(int i) {
        if (kind == Kind.ANONYMOUS || kind == Kind.API) return null;
        return i < parts.length ? parts[i] : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Caller)) return false;
        Caller c = (Caller) o;
        return principal.equals(c.principal) && tenant.equals(c.tenant) && roles.equals(c.roles);
    }

    @Override
    public int hashCode() { return Objects.hash(principal, tenant, roles); }

    @Override
    public String toString() { return principal + "[" + getRolesCsv() + "]"; }
}
