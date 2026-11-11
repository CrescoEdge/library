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
 *
 * <p><b>Authority follows the tree.</b> Role and tenant say WHO may call; an action's {@link Direction} says FROM WHERE.
 * A message has a speaker -- the node whose word its caller is: a controller's or plugin's own node, and for an API user
 * the node that vouched for it -- and the receiving node's controller says where that speaker stands ({@link Position}).
 * A {@link Direction#DOWN} action (management) is honoured only from the node itself or a controller above it; an
 * {@link Direction#UP} action (a child reporting to its parent) only from the node itself or a node below it; an
 * {@link Direction#ANY} action from anywhere. Every action of the privileged floor is {@code DOWN} unless it is declared
 * otherwise ({@link #direct}, {@code @CrescoAction(direction=...)}); every other action is {@code ANY} unless declared.
 * The role and tenant rules are checked first and are unchanged: direction only ever refuses more. {@link Mode} says what
 * a call from the wrong place meets: nothing ({@code OFF}), a count and a log line ({@code LOG}), or a refusal
 * ({@code ENFORCE}; a speaker the node cannot place, {@link Position#UNKNOWN}, is refused too).
 */
public final class ActionAuthorizer {

    /** From where an action is honoured. */
    public enum Direction {
        /** Management: only from the receiving node itself or a controller above it. */
        DOWN,
        /** A child reporting to its parent: only from the receiving node itself or a node below it. */
        UP,
        /** From anywhere (role and tenant only). */
        ANY;

        /** {@code down}, {@code up} or {@code any} (case-insensitive); null for an empty value (not declared). */
        public static Direction parse(String v) {
            if (v == null || v.trim().isEmpty()) return null;
            switch (v.trim().toLowerCase(Locale.ROOT)) {
                case "down": return DOWN;
                case "up": return UP;
                case "any": return ANY;
                default: throw new IllegalArgumentException("unknown direction '" + v + "' (down, up or any)");
            }
        }
    }

    /** Where the speaker of a message stands, as the receiving node sees it. */
    public enum Position {
        /** The receiving node itself. */
        SELF("the node itself"),
        /** The receiving node's parent, or the global above its region. */
        ABOVE("a controller above"),
        /** A node of the receiving controller's own region, or (at the global) of a region joined to it. */
        BELOW("a node below"),
        /** Any other node: a sibling, another region's node. */
        ELSEWHERE("a node elsewhere"),
        /** The receiving node cannot tell which node the caller's word is. */
        UNKNOWN("a node this node cannot place");

        private final String words;

        Position(String words) { this.words = words; }

        /** In words, for a refusal: {@code a node elsewhere}. */
        public String words() { return words; }
    }

    /** What a call from the wrong place meets ({@code authority_direction}). */
    public enum Mode {
        /** Direction is not looked at. */
        OFF,
        /** A call {@link #ENFORCE} would refuse is allowed, and reported ({@link Decision#wouldRefuse}). */
        LOG,
        /** A call from the wrong place is refused. */
        ENFORCE;

        /** {@code off}, {@code log} or {@code enforce} (case-insensitive); {@code LOG} when not set; null for anything else. */
        public static Mode parse(String v) {
            if (v == null || v.trim().isEmpty()) return LOG;
            switch (v.trim().toLowerCase(Locale.ROOT)) {
                case "off": return OFF;
                case "log": return LOG;
                case "enforce": return ENFORCE;
                default: return null;
            }
        }

        /** The lower-case name as configured. */
        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    /**
     * Who says where a message's speaker stands: the receiving node's controller
     * ({@link io.cresco.library.agent.AgentService#authority()}). It is asked for a position only for an action that has
     * a direction, after role and tenant allowed the call: an action honoured from anywhere costs nothing here.
     */
    public interface Authority {
        /** What a call from the wrong place meets on this node. Null is read as {@link Mode#ENFORCE}. */
        Mode mode();

        /**
         * Where the speaker of {@code msg} stands relative to this node; {@code caller} is the verified caller stamped on
         * it. Null is read as {@link Position#UNKNOWN}.
         */
        Position positionOf(MsgEvent msg, Caller caller);

        /** A call that {@link Mode#ENFORCE} would refuse was allowed ({@link Mode#LOG}): count it, log it. */
        default void wouldRefuse(MsgEvent msg, Decision decision) { }

        /** A controller that does not know directions (an earlier release): nothing is looked at. */
        Authority NONE = new Authority() {
            @Override public Mode mode() { return Mode.OFF; }
            @Override public Position positionOf(MsgEvent msg, Caller caller) { return Position.UNKNOWN; }
        };

        /** No controller could be asked: every action with a direction is refused (fail closed). */
        Authority CLOSED = new Authority() {
            @Override public Mode mode() { return Mode.ENFORCE; }
            @Override public Position positionOf(MsgEvent msg, Caller caller) { return Position.UNKNOWN; }
        };
    }

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
        /** The action's direction when it was looked at (a mode other than {@code OFF}, role and tenant allowed); else null. */
        public final Direction direction;
        /** Where the speaker stands, when the action has a direction other than {@code ANY}; else null. */
        public final Position position;
        /**
         * What gave the direction, a bounded name fit for a metric: the action (declared, or of the privileged floor), or
         * the message type for a declaration of every action of a type; null when {@link #position} is.
         */
        public final String directed;
        /** Allowed, and {@link Mode#ENFORCE} would refuse it: the speaker stands in the wrong place ({@link Mode#LOG}). */
        public final boolean wouldRefuse;
        /** Refused for where its speaker stands (role and tenant allowed it). */
        public final boolean directionRefused;

        Decision(boolean allowed, String reason, String action, Set<String> required, Caller caller) {
            this(allowed, reason, action, required, caller, null, null, null, false, false);
        }

        Decision(boolean allowed, String reason, String action, Set<String> required, Caller caller, Direction direction,
                 Position position, String directed, boolean wouldRefuse, boolean directionRefused) {
            this.allowed = allowed;
            this.reason = reason;
            this.action = action;
            this.required = required;
            this.caller = caller;
            this.direction = direction;
            this.position = position;
            this.directed = directed;
            this.wouldRefuse = wouldRefuse;
            this.directionRefused = directionRefused;
        }

        /**
         * The 403 {@code status_desc}: {@code unauthorized: <action> needs <roles>}; for a call refused for where its
         * speaker stands, {@code unauthorized: <action> is honoured only from the node itself or a controller above it}
         * ({@code or a node below it}, for an {@link Direction#UP} action).
         */
        public String statusDesc() {
            String a = action == null || action.isEmpty() ? "(no action)" : action;
            if (directionRefused) return "unauthorized: " + a + " " + honouredFrom(direction) + (reason == null ? "" : " (" + reason + ")");
            return "unauthorized: " + a + " needs "
                    + (required.isEmpty() ? "a caller of this agent's tenant" : String.join(" or ", required))
                    + (reason == null ? "" : " (" + reason + ")");
        }
    }

    /** {@code is honoured only from the node itself or a controller above it} ({@code or a node below it}, for UP). */
    public static String honouredFrom(Direction direction) {
        return "is honoured only from the node itself or " + (direction == Direction.UP ? "a node below it" : "a controller above it");
    }

    private volatile String bundle;
    /** {@code TYPE|action} (action empty for a message without one) -> roles one of which the caller must hold. */
    private final Map<String, Set<String>> declared = new ConcurrentHashMap<>();
    /** {@code TYPE|action} keys that admit {@code infra} of every tenant. */
    private final Set<String> anyTenantInfra = ConcurrentHashMap.newKeySet();
    /** Roles for an action nobody declared: empty (open) for a plugin; {@code infra}/{@code admin} for the controller. */
    private volatile Set<String> defaultRoles = Collections.emptySet();
    /** {@code TYPE|action} ({@code TYPE|*}: every action of the type not declared itself) -> the direction declared for it. */
    private final Map<String, Direction> directions = new ConcurrentHashMap<>();

    public ActionAuthorizer(String bundleSymbolicName) {
        this.bundle = bundleSymbolicName;
    }

    /** The bundle whose privileged floor applies (set when the bundle context does not name it, e.g. in tests). */
    public void setBundle(String bundleSymbolicName) {
        this.bundle = bundleSymbolicName;
    }

    public String getBundle() { return bundle; }

    /** Read the {@code roles} and {@code direction} of every {@code @CrescoAction} on {@code annotated} (type-level and per-method). */
    public void register(Class<?> annotated) {
        if (annotated == null) return;
        for (CrescoAction a : annotated.getAnnotationsByType(CrescoAction.class)) register(a);
        for (Method m : annotated.getDeclaredMethods()) {
            for (CrescoAction a : m.getAnnotationsByType(CrescoAction.class)) register(a);
        }
    }

    private void register(CrescoAction a) {
        require(a.type(), a.name(), a.roles());
        Direction d = Direction.parse(a.direction());
        if (d != null) direct(a.type(), a.name(), d);
    }

    /**
     * Declare from where {@code type}/{@code action} is honoured ({@code action} {@code "*"}: every action of
     * {@code type} that is not declared itself; null: a message of that type without an action). A declaration stands in
     * place of the default (the privileged floor's {@code DOWN}, {@code ANY} for everything else). Declared more than
     * once, the narrower stands ({@code ANY} adds nothing); {@code DOWN} and {@code UP} for the same action is a mistake
     * in the tables and throws.
     */
    public void direct(String type, String action, Direction direction) {
        if (direction == null) throw new IllegalArgumentException("no direction for action " + action);
        directions.merge(key(type, action), direction, (had, now) -> {
            if (had == now || now == Direction.ANY) return had;
            if (had == Direction.ANY) return now;
            throw new IllegalArgumentException("action " + action + " is declared both " + had + " and " + now);
        });
    }

    /**
     * From where {@code type}/{@code action} is honoured: as declared for it, else as declared for every action of its
     * type, else {@code DOWN} for an action of this bundle's privileged floor and {@code ANY} for any other.
     */
    public Direction directionOf(String type, String action) {
        Direction d = declaredDirection(type, action);
        if (d == null) d = directions.get(key(type, "*"));
        if (d != null) return d;
        return inFloor(action) ? Direction.DOWN : Direction.ANY;
    }

    /** The direction declared for {@code type}/{@code action} itself ({@link #direct}, an annotation), or null. */
    public Direction declaredDirection(String type, String action) {
        return directions.get(key(type, action));
    }

    /** Every declared direction by its key ({@code TYPE|action}; {@code TYPE|*} for a whole type), in key order. */
    public Map<String, Direction> directions() {
        return Collections.unmodifiableMap(new java.util.TreeMap<>(directions));
    }

    /** Every key ({@code TYPE|action}) this table declares roles for, or declares open, in key order. */
    public Set<String> declaredKeys() {
        return Collections.unmodifiableSet(new TreeSet<>(declared.keySet()));
    }

    private boolean inFloor(String action) {
        Set<String> floor = (bundle == null || action == null) ? null : PRIVILEGED.get(bundle);
        return floor != null && floor.contains(action);
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
        return requiredRoles(key(type, action), type, action);
    }

    /** {@link #requiredRoles(String, String)} with the action's key in hand (built once per message). */
    private Set<String> requiredRoles(String k, String type, String action) {
        Set<String> d = declared.get(k);
        if (d == null) d = declared.get(key(type, "*"));
        if (d == null) d = defaultRoles.isEmpty() ? null : defaultRoles;
        if (inFloor(action)) {
            return d == null || d.isEmpty() ? PRIVILEGED_ROLES : intersect(d, PRIVILEGED_ROLES);
        }
        return d == null ? Collections.emptySet() : d;
    }

    private boolean admitsAnyTenantInfra(String k, String type) {
        return anyTenantInfra.contains(k) || anyTenantInfra.contains(key(type, "*"));
    }

    /** The decision in insecure development mode ({@code broker_security_enabled=false}): every caller may. */
    public static Decision insecure(MsgEvent msg) {
        String action = msg == null ? null : msg.getParam("action");
        return new Decision(true, "insecure development mode", action, Collections.emptySet(), Caller.anonymous());
    }

    /**
     * Decide for {@code msg}, whose verified caller is stamped on it, by role and tenant alone (no direction is looked at).
     *
     * @param localTenant      this agent's own tenant, the destination (null when unknown: only a superuser tenant's
     *                         infra/admin, and infra where every tenant's is admitted, are in scope)
     * @param superuserTenants the superuser tenants
     */
    public Decision decide(MsgEvent msg, String localTenant, Set<String> superuserTenants) {
        return decide(msg, localTenant, superuserTenants, null, null, Mode.OFF);
    }

    /**
     * As {@link #decide(MsgEvent, String, Set)}, and then by direction: {@code position} is where the message's speaker
     * stands (null: {@link Position#UNKNOWN}), {@code mode} what a call from the wrong place meets (null:
     * {@link Mode#ENFORCE}). Role and tenant are checked first, as ever; a call they allow, whose action is {@code DOWN} or
     * {@code UP} and whose speaker stands elsewhere (or cannot be placed), is refused in {@code ENFORCE}, and allowed with
     * {@link Decision#wouldRefuse} set in {@code LOG}.
     */
    public Decision decide(MsgEvent msg, String localTenant, Set<String> superuserTenants, Position position, Mode mode) {
        return decide(msg, localTenant, superuserTenants, null, position == null ? Position.UNKNOWN : position,
                mode == null ? Mode.ENFORCE : mode);
    }

    /**
     * As {@link #decide(MsgEvent, String, Set, Position, Mode)}, with the position asked of {@code authority} only when it
     * is needed: the action has a direction, and role and tenant allowed the call.
     */
    public Decision decide(MsgEvent msg, String localTenant, Set<String> superuserTenants, Authority authority) {
        Authority a = authority == null ? Authority.CLOSED : authority;
        Mode mode;
        try {
            mode = a.mode();
        } catch (RuntimeException unknown) {
            mode = null;                        // the controller could not say: fail closed
        }
        return decide(msg, localTenant, superuserTenants, a, null, mode == null ? Mode.ENFORCE : mode);
    }

    private Decision decide(MsgEvent msg, String localTenant, Set<String> superuserTenants, Authority authority,
                            Position given, Mode mode) {
        String action = msg == null ? null : msg.getParam("action");
        String type = (msg == null || msg.getMsgType() == null) ? "EXEC" : msg.getMsgType().name();
        String k = key(type, action);
        Set<String> need = requiredRoles(k, type, action);
        Caller c = Caller.fromMsgEvent(msg);
        if (c == null) {
            return new Decision(false, Caller.hasAny(msg) ? "malformed caller stamp" : "no verified caller", action, need, null);
        }
        // a secured node never admits an anonymous stamp (insecure development mode is decided before: insecure())
        if (c.isAnonymous()) return new Decision(false, "an anonymous caller on a secured node", action, need, c);
        boolean sameTenant = localTenant != null && localTenant.equals(c.getTenant());
        boolean superTenant = superuserTenants != null && superuserTenants.contains(c.getTenant());
        boolean allowed = false, heldOutOfScope = false;
        if (need.isEmpty()) {
            allowed = sameTenant || (superTenant && (c.hasRole(Caller.ROLE_INFRA) || c.hasRole(Caller.ROLE_ADMIN)))
                    || (c.hasRole(Caller.ROLE_INFRA) && admitsAnyTenantInfra(k, type));
            heldOutOfScope = true;
        } else {
            for (String r : need) {
                if (!c.hasRole(r)) continue;
                if (sameTenant || (superTenant && PRIVILEGED_ROLES.contains(r))
                        || (Caller.ROLE_INFRA.equals(r) && admitsAnyTenantInfra(k, type))) {
                    allowed = true;
                    break;
                }
                heldOutOfScope = true;
            }
        }
        if (!allowed) {
            return new Decision(false, heldOutOfScope ? "caller " + c + " is of tenant " + c.getTenant() + ", this agent of " + localTenant
                    : "caller " + c, action, need, c);
        }
        // role and tenant allow it: from where is it honoured?
        if (mode == Mode.OFF) return new Decision(true, null, action, need, c);
        Direction direction = directions.isEmpty() ? null : directions.get(k);
        String directed = action;
        if (direction == null && !directions.isEmpty()) {
            direction = directions.get(key(type, "*"));
            directed = type;
        }
        if (direction == null) {
            direction = inFloor(action) ? Direction.DOWN : Direction.ANY;
            directed = action;
        }
        if (direction == Direction.ANY) return new Decision(true, null, action, need, c, direction, null, null, false, false);
        Position at = given;
        if (authority != null) {
            try {
                at = authority.positionOf(msg, c);
            } catch (RuntimeException unknown) {
                at = null;                      // the controller could not say: fail closed
            }
        }
        if (at == null) at = Position.UNKNOWN;
        if (at == Position.SELF || at == (direction == Direction.DOWN ? Position.ABOVE : Position.BELOW)) {
            return new Decision(true, null, action, need, c, direction, at, directed, false, false);
        }
        if (mode == Mode.LOG) return new Decision(true, null, action, need, c, direction, at, directed, true, false);
        return new Decision(false, "caller " + c + " speaks from " + at.words(), action, need, c, direction, at, directed, false, true);
    }
}
