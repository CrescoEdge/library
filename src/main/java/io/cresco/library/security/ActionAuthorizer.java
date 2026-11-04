package io.cresco.library.security;

import io.cresco.library.capability.CrescoAction;
import io.cresco.library.messaging.MsgEvent;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides whether the verified caller of a message may run its action. {@code PluginBuilder} asks it for every message
 * before the executor runs (and the controller before its regional and global executors run); a refusal is answered
 * {@code status_code=403}. See {@code controller/docs/caller-authorization.md} section 3.
 *
 * <p>Terms: the caller's tenant {@code C} (stamped), this agent's own tenant {@code D} (the destination), the superuser
 * tenants {@code S} ({@code broker_superuser_tenants}). Rules, deny by default:
 * <ul>
 *   <li>No verified caller (or a malformed stamp): refused, whatever the action.</li>
 *   <li>{@code anonymous}: refused here. Only when {@code broker_security_enabled=false} is set explicitly is every caller
 *       admitted, before any table is consulted ({@link #insecure}).</li>
 *   <li>An OPEN action (no required roles): allowed when {@code C == D}; or the caller holds {@code infra} or
 *       {@code admin} and {@code C} is in {@code S}; or the caller holds {@code infra} and the action admits
 *       infrastructure of every tenant ({@link #allowAnyTenantInfra}, fabric mechanics on the controller tiers only).</li>
 *   <li>A ROLE action (roles from {@code @CrescoAction(roles=...)}, {@link #require}, the privileged floor, or the default
 *       for undeclared actions): the caller must hold one of the roles, and hold it in scope -- {@code C == D}; or the role
 *       is {@code infra} or {@code admin} and {@code C} is in {@code S}; or the role is {@code infra} and the action
 *       admits infrastructure of every tenant.</li>
 * </ul>
 * So an admin of tenant T acts only on agents of T unless T is a superuser tenant; a {@code user} or {@code plugin}
 * never crosses a tenant.
 *
 * <p>The privileged floor ({@link #PRIVILEGED}): these actions of these bundles require {@code infra} or {@code admin}
 * whatever their annotations say; an annotation can only narrow it (to {@code infra}), never remove an action from it.
 * When the same action is declared more than once the requirements are intersected (the stricter wins; an empty
 * intersection leaves {@code infra} alone).
 */
public final class ActionAuthorizer {

    /** The roles every privileged action requires one of. */
    public static final Set<String> PRIVILEGED_ROLES =
            Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(Caller.ROLE_INFRA, Caller.ROLE_ADMIN)));

    /** Bundle symbolic name of the controller. */
    public static final String CONTROLLER_BUNDLE = "io.cresco.controller";

    /** Per bundle: the actions that require {@code infra} or {@code admin}, whatever the bundle's annotations say. */
    public static final Map<String, Set<String>> PRIVILEGED;

    static {
        Map<String, Set<String>> m = new HashMap<>();
        m.put(CONTROLLER_BUNDLE, set(
                // plugin lifecycle and code arriving over the network
                "pluginadd", "pluginupload", "pluginremove", "pluginrepopull",
                // controller and JVM
                "controllerupdate", "killjvm", "restartframework", "restartcontroller", "stopcontroller",
                // agent state
                "agent_disable", "agent_enable",
                // remote file access
                "getfiledata", "getfileinfo",
                // logging that exposes other callers' traffic (Trace through wsapi read everyone's messages)
                "setloglevel", "setlogdp"));
        m.put("io.cresco.executor", set("config_process", "run_process", "start_process", "end_process", "write_stdin"));
        m.put("io.cresco.filerepo", set("clearrepo", "putjar", "removefile", "putfiles"));
        m.put("io.cresco.repo", set("putjar"));
        PRIVILEGED = Collections.unmodifiableMap(m);
    }

    private static Set<String> set(String... s) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(s)));
    }

    /** The outcome for one message. */
    public static final class Decision {
        public final boolean allowed;
        public final String reason;
        public final String action;
        public final Set<String> required;
        public final Caller caller;

        Decision(boolean allowed, String reason, String action, Set<String> required, Caller caller) {
            this.allowed = allowed;
            this.reason = reason;
            this.action = action;
            this.required = required;
            this.caller = caller;
        }

        /** {@code unauthorized: <action> needs <roles>} (the 403 {@code status_desc}). */
        public String statusDesc() {
            return "unauthorized: " + (action == null || action.isEmpty() ? "(no action)" : action) + " needs "
                    + (required.isEmpty() ? "a caller of this agent's tenant" : String.join(" or ", required))
                    + (reason == null ? "" : " (" + reason + ")");
        }
    }

    private volatile String bundle;
    /** {@code TYPE|action} (action empty for a message without one) -> roles one of which the caller must hold. */
    private final Map<String, Set<String>> declared = new ConcurrentHashMap<>();
    /** {@code TYPE|action} keys that admit {@code infra} of every tenant. */
    private final Set<String> anyTenantInfra = ConcurrentHashMap.newKeySet();
    /** Roles for an action nobody declared: empty (open) for a plugin; {@code infra}/{@code admin} for the controller. */
    private volatile Set<String> defaultRoles = Collections.emptySet();

    public ActionAuthorizer(String bundleSymbolicName) {
        this.bundle = bundleSymbolicName;
    }

    /** The bundle whose privileged floor applies (set when the bundle context does not name it, e.g. in tests). */
    public void setBundle(String bundleSymbolicName) {
        this.bundle = bundleSymbolicName;
    }

    public String getBundle() { return bundle; }

    /** Read the {@code roles} of every {@code @CrescoAction} on {@code annotated} (type-level and per-method). */
    public void register(Class<?> annotated) {
        if (annotated == null) return;
        for (CrescoAction a : annotated.getAnnotationsByType(CrescoAction.class)) require(a.type(), a.name(), a.roles());
        for (Method m : annotated.getDeclaredMethods()) {
            for (CrescoAction a : m.getAnnotationsByType(CrescoAction.class)) require(a.type(), a.name(), a.roles());
        }
    }

    /**
     * Require one of {@code roles} for {@code type}/{@code action} ({@code action} null: a message of that type without
     * one); nothing when {@code roles} is empty. Intersects with an earlier requirement for the same key.
     */
    public void require(String type, String action, String... roles) {
        if (roles == null || roles.length == 0) return;
        declared.merge(key(type, action), roleSet(action, roles), ActionAuthorizer::intersect);
    }

    /**
     * Declare {@code type}/{@code action} OPEN (see the class rules), overriding the default for undeclared actions.
     * {@code action} {@code "*"} declares every action of {@code type} that is not declared itself; null, a message of
     * that type without an action. An open declaration never weakens roles declared for the same key.
     */
    public void open(String type, String action) {
        declared.merge(key(type, action), Collections.emptySet(), ActionAuthorizer::intersect);
    }

    /** The roles an action nobody declared requires (fail closed on the controller); empty means open. */
    public void setDefaultRoles(String... roles) {
        this.defaultRoles = roles == null || roles.length == 0 ? Collections.emptySet() : roleSet("(default)", roles);
    }

    /**
     * {@code type}/{@code action} admits {@code infra} of every tenant: the fabric mechanics a controller serves to the
     * agents of other tenants (registration, liveness, keep-alive). Never use it for anything that reads or changes a
     * tenant's data.
     */
    public void allowAnyTenantInfra(String type, String action) {
        anyTenantInfra.add(key(type, action));
    }

    private static Set<String> roleSet(String action, String... roles) {
        Set<String> want = new TreeSet<>();
        for (String r : roles) {
            if (!Caller.ALL_ROLES.contains(r)) throw new IllegalArgumentException("unknown role '" + r + "' on action " + action);
            want.add(r);
        }
        return Collections.unmodifiableSet(want);
    }

    /** The stricter of two requirements; an empty (open) one adds nothing. */
    private static Set<String> intersect(Set<String> a, Set<String> b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        Set<String> i = new TreeSet<>(a);
        i.retainAll(b);
        if (i.isEmpty()) i.add(Caller.ROLE_INFRA);
        return Collections.unmodifiableSet(i);
    }

    private static String key(String type, String action) {
        return (type == null ? "EXEC" : type.toUpperCase(Locale.ROOT)) + "|" + (action == null ? "" : action);
    }

    /** Is {@code action} declared here or in the bundle's privileged floor (a bounded set, safe as a metric tag)? */
    public boolean isKnown(String action) {
        if (action == null) return false;
        Set<String> floor = bundle == null ? null : PRIVILEGED.get(bundle);
        if (floor != null && floor.contains(action)) return true;
        for (String k : declared.keySet()) if (k.endsWith("|" + action)) return true;
        return false;
    }

    /** Is {@code type}/{@code action} itself declared here (roles or open), not merely covered by a default? */
    public boolean isDeclared(String type, String action) {
        return declared.containsKey(key(type, action));
    }

    /** The roles one of which a caller of {@code type}/{@code action} must hold; empty when the action is open. */
    public Set<String> requiredRoles(String type, String action) {
        Set<String> d = declared.get(key(type, action));
        if (d == null) d = declared.get(key(type, "*"));
        if (d == null) d = defaultRoles.isEmpty() ? null : defaultRoles;
        Set<String> floor = (bundle == null || action == null) ? null : PRIVILEGED.get(bundle);
        if (floor != null && floor.contains(action)) {
            return d == null || d.isEmpty() ? PRIVILEGED_ROLES : intersect(d, PRIVILEGED_ROLES);
        }
        return d == null ? Collections.emptySet() : d;
    }

    /** The decision in insecure development mode ({@code broker_security_enabled=false}): every caller may. */
    public static Decision insecure(MsgEvent msg) {
        String action = msg == null ? null : msg.getParam("action");
        return new Decision(true, "insecure development mode", action, Collections.emptySet(), Caller.anonymous());
    }

    /**
     * Decide for {@code msg}, whose verified caller is stamped on it.
     *
     * @param localTenant      this agent's own tenant, the destination (null when unknown: only a superuser tenant's
     *                         infra/admin, and infra where every tenant's is admitted, are in scope)
     * @param superuserTenants the superuser tenants
     */
    public Decision decide(MsgEvent msg, String localTenant, Set<String> superuserTenants) {
        String action = msg == null ? null : msg.getParam("action");
        String type = (msg == null || msg.getMsgType() == null) ? "EXEC" : msg.getMsgType().name();
        Set<String> need = requiredRoles(type, action);
        Caller c = Caller.fromMsgEvent(msg);
        if (c == null) {
            return new Decision(false, Caller.hasAny(msg) ? "malformed caller stamp" : "no verified caller", action, need, null);
        }
        // a secured node never admits an anonymous stamp (insecure development mode is decided before: insecure())
        if (c.isAnonymous()) return new Decision(false, "an anonymous caller on a secured node", action, need, c);
        boolean sameTenant = localTenant != null && localTenant.equals(c.getTenant());
        boolean superTenant = superuserTenants != null && superuserTenants.contains(c.getTenant());
        boolean infraAnyTenant = anyTenantInfra.contains(key(type, action)) || anyTenantInfra.contains(key(type, "*"));
        if (need.isEmpty()) {
            if (sameTenant) return new Decision(true, null, action, need, c);
            if (superTenant && (c.hasRole(Caller.ROLE_INFRA) || c.hasRole(Caller.ROLE_ADMIN))) return new Decision(true, null, action, need, c);
            if (infraAnyTenant && c.hasRole(Caller.ROLE_INFRA)) return new Decision(true, null, action, need, c);
            return new Decision(false, "caller " + c + " is of tenant " + c.getTenant() + ", this agent of " + localTenant,
                    action, need, c);
        }
        boolean heldOutOfScope = false;
        for (String r : need) {
            if (!c.hasRole(r)) continue;
            if (sameTenant) return new Decision(true, null, action, need, c);
            if (superTenant && PRIVILEGED_ROLES.contains(r)) return new Decision(true, null, action, need, c);
            if (infraAnyTenant && Caller.ROLE_INFRA.equals(r)) return new Decision(true, null, action, need, c);
            heldOutOfScope = true;
        }
        return new Decision(false, heldOutOfScope ? "caller " + c + " is of tenant " + c.getTenant() + ", this agent of " + localTenant
                : "caller " + c, action, need, c);
    }
}
