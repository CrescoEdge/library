package io.cresco.library.security;

import io.cresco.library.agent.AgentService;
import io.cresco.library.agent.AgentState;
import io.cresco.library.agent.ControllerState;
import io.cresco.library.capability.CrescoAction;
import io.cresco.library.data.DataPlaneService;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.plugin.Executor;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.security.ActionAuthorizer.Authority;
import io.cresco.library.security.ActionAuthorizer.Decision;
import io.cresco.library.security.ActionAuthorizer.Direction;
import io.cresco.library.security.ActionAuthorizer.Mode;
import io.cresco.library.security.ActionAuthorizer.Position;
import io.cresco.library.utilities.CLogger;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Authority follows the tree, as the library decides it: an action's direction, the position of the message's speaker
 * and the node's mode say whether a call that role and tenant allow is honoured, refused, or allowed and reported.
 */
class ActionDirectionTest {

    static final String T = "t1", SU = "cresco-system";

    static final Caller INFRA = Caller.controller(T, "r", "a2"), PLUGIN = Caller.plugin(T, "r", "a2", "p9"),
            ADMIN = Caller.api(T, "root", List.of("admin")), USER = Caller.api(T, "bob", List.of("user")),
            OTHER_ADMIN = Caller.api("t2", "root", List.of("admin"));

    /** One action of each direction that every one of the four callers may run by role, in the same tenant. */
    private static ActionAuthorizer table() {
        ActionAuthorizer a = new ActionAuthorizer("io.cresco.test");
        for (String action : new String[]{"manage", "report", "read"}) a.open("EXEC", action);
        a.direct("EXEC", "manage", Direction.DOWN);
        a.direct("EXEC", "report", Direction.UP);
        a.direct("EXEC", "read", Direction.ANY);
        return a;
    }

    private static MsgEvent msg(MsgEvent.Type type, String action, Caller c) {
        MsgEvent m = new MsgEvent(type, "r", "a2", null, "r", "a", null, false, false);
        if (action != null) m.setParam("action", action);
        if (c != null) c.stamp(m);
        return m;
    }

    private static MsgEvent exec(String action, Caller c) { return msg(MsgEvent.Type.EXEC, action, c); }

    /** What the rule says for a direction and a position: is the call honoured? */
    private static boolean honoured(Direction d, Position p) {
        switch (d) {
            case DOWN: return p == Position.SELF || p == Position.ABOVE;
            case UP: return p == Position.SELF || p == Position.BELOW;
            default: return true;
        }
    }

    @Test
    void everyPositionDirectionAndModeGivesAllowedRefusedOrWouldRefuseForEveryKindOfCaller() {
        ActionAuthorizer a = table();
        Map<Direction, String> actions = Map.of(Direction.DOWN, "manage", Direction.UP, "report", Direction.ANY, "read");
        int cells = 0;
        for (Caller c : List.of(INFRA, PLUGIN, ADMIN, USER)) {
            for (Direction d : Direction.values()) {
                for (Position p : Position.values()) {
                    for (Mode m : Mode.values()) {
                        Decision got = a.decide(exec(actions.get(d), c), T, Set.of(SU), p, m);
                        String cell = c + " " + d + " from " + p + " in " + m;
                        boolean ok = honoured(d, p);
                        boolean allowed = m != Mode.ENFORCE || ok;
                        assertEquals(allowed, got.allowed, cell + ": allowed");
                        assertEquals(m == Mode.LOG && !ok, got.wouldRefuse, cell + ": would refuse");
                        assertEquals(m == Mode.ENFORCE && !ok, got.directionRefused, cell + ": refused for its direction");
                        if (m == Mode.OFF) assertNull(got.direction, cell + ": the direction was looked at with the mode off");
                        else assertEquals(d, got.direction, cell);
                        if (m != Mode.OFF && d != Direction.ANY) assertEquals(p, got.position, cell);
                        else assertNull(got.position, cell + ": a position where none is needed");
                        cells++;
                    }
                }
            }
        }
        assertEquals(4 * 3 * 5 * 3, cells);
    }

    @Test
    void aSpeakerTheNodeCannotPlaceIsRefusedInEnforceAndReportedInLog() {
        ActionAuthorizer a = table();
        for (String action : new String[]{"manage", "report"}) {
            assertFalse(a.decide(exec(action, INFRA), T, Set.of(SU), Position.UNKNOWN, Mode.ENFORCE).allowed, action + ": fail closed");
            assertFalse(a.decide(exec(action, INFRA), T, Set.of(SU), null, Mode.ENFORCE).allowed, action + ": no position at all");
            Decision log = a.decide(exec(action, INFRA), T, Set.of(SU), Position.UNKNOWN, Mode.LOG);
            assertTrue(log.allowed && log.wouldRefuse, action + ": log mode says what enforce would refuse");
            assertTrue(a.decide(exec(action, INFRA), T, Set.of(SU), Position.UNKNOWN, Mode.OFF).allowed);
        }
        assertFalse(a.decide(exec("manage", INFRA), T, Set.of(SU), Position.SELF, null).wouldRefuse);
        assertFalse(a.decide(exec("manage", INFRA), T, Set.of(SU), Position.ELSEWHERE, null).allowed, "no mode reads as enforce");
        assertTrue(a.decide(exec("read", INFRA), T, Set.of(SU), Position.UNKNOWN, Mode.ENFORCE).allowed, "an action honoured from anywhere");
    }

    @Test
    void theSignatureWithoutAPositionDecidesByRoleAndTenantAloneAsBefore() {
        ActionAuthorizer a = table();
        for (String action : new String[]{"manage", "report", "read"}) {
            Decision d = a.decide(exec(action, INFRA), T, Set.of(SU));
            assertTrue(d.allowed, action);
            assertFalse(d.wouldRefuse || d.directionRefused, action);
            assertNull(d.direction);
            assertNull(d.position);
        }
        ActionAuthorizer floor = new ActionAuthorizer("io.cresco.executor");
        assertTrue(floor.decide(exec("run_process", INFRA), T, Set.of(SU)).allowed, "the floor's roles only");
        assertFalse(floor.decide(exec("run_process", USER), T, Set.of(SU)).allowed);
    }

    @Test
    void roleAndTenantRefusalsAreUnchangedByDirection() {
        ActionAuthorizer a = new ActionAuthorizer("io.cresco.test");
        a.require("EXEC", "manage", "infra", "admin");
        a.direct("EXEC", "manage", Direction.DOWN);
        for (Mode m : Mode.values()) {
            for (Position p : Position.values()) {
                String cell = " from " + p + " in " + m;
                Decision plain = a.decide(exec("manage", USER), T, Set.of(SU));
                Decision user = a.decide(exec("manage", USER), T, Set.of(SU), p, m);
                assertFalse(user.allowed, "a user ran an admin action" + cell);
                assertFalse(user.directionRefused || user.wouldRefuse, "a role refusal was reported as a direction's" + cell);
                assertEquals(plain.statusDesc(), user.statusDesc(), "the 403 text of a role refusal changed" + cell);
                assertNull(user.position, "a position was asked for a call its role refuses" + cell);
                Decision foreign = a.decide(exec("manage", OTHER_ADMIN), T, Set.of(SU), p, m);
                assertFalse(foreign.allowed, "another tenant's admin" + cell);
                assertEquals(a.decide(exec("manage", OTHER_ADMIN), T, Set.of(SU)).statusDesc(), foreign.statusDesc());
                Decision nobody = a.decide(exec("manage", null), T, Set.of(SU), p, m);
                assertFalse(nobody.allowed);
                assertEquals("no verified caller", nobody.reason);
            }
        }
        // and the best position never lends a role
        assertFalse(a.decide(exec("manage", PLUGIN), T, Set.of(SU), Position.SELF, Mode.ENFORCE).allowed);
    }

    @Test
    void thePrivilegedFloorIsDownForEveryBundleUnlessDeclaredOtherwise() {
        assertFalse(ActionAuthorizer.PRIVILEGED.isEmpty());
        for (Map.Entry<String, Set<String>> bundle : ActionAuthorizer.PRIVILEGED.entrySet()) {
            ActionAuthorizer a = new ActionAuthorizer(bundle.getKey());
            for (String action : bundle.getValue()) {
                for (String type : new String[]{"EXEC", "CONFIG"}) {
                    assertEquals(Direction.DOWN, a.directionOf(type, action), bundle.getKey() + " " + type + " " + action);
                    assertNull(a.declaredDirection(type, action), "a default is not a declaration");
                }
                MsgEvent m = exec(action, INFRA);
                assertFalse(a.decide(m, T, Set.of(SU), Position.ELSEWHERE, Mode.ENFORCE).allowed, bundle.getKey() + " " + action + " from a sibling");
                assertFalse(a.decide(m, T, Set.of(SU), Position.BELOW, Mode.ENFORCE).allowed, bundle.getKey() + " " + action + " from below");
                assertTrue(a.decide(m, T, Set.of(SU), Position.ABOVE, Mode.ENFORCE).allowed, bundle.getKey() + " " + action + " from above");
                assertTrue(a.decide(m, T, Set.of(SU), Position.SELF, Mode.ENFORCE).allowed, bundle.getKey() + " " + action + " from the node itself");
                assertEquals(action, a.decide(m, T, Set.of(SU), Position.ELSEWHERE, Mode.LOG).directed, "what the count is kept under");
            }
            assertEquals(Direction.ANY, a.directionOf("EXEC", "somethingelse"), "an action outside the floor");
        }
        // the same action name in a bundle whose floor does not list it
        assertEquals(Direction.ANY, new ActionAuthorizer("io.cresco.sysinfo").directionOf("EXEC", "run_process"));
        // declared otherwise: the declaration stands in place of the default
        ActionAuthorizer region = new ActionAuthorizer(ActionAuthorizer.CONTROLLER_BUNDLE);
        region.direct("CONFIG", "agent_enable", Direction.UP);
        assertEquals(Direction.UP, region.directionOf("CONFIG", "agent_enable"));
        assertEquals(Direction.DOWN, region.directionOf("CONFIG", "agent_disable"));
    }

    @Test
    void aDeclarationForAWholeTypeCoversItsUndeclaredActionsAndTheNarrowerOfTwoDeclarationsStands() {
        ActionAuthorizer a = new ActionAuthorizer(ActionAuthorizer.CONTROLLER_BUNDLE);
        a.open("WATCHDOG", "*");
        a.direct("WATCHDOG", "*", Direction.UP);
        a.open("WATCHDOG", "special");
        a.direct("WATCHDOG", "special", Direction.ANY);
        assertEquals(Direction.UP, a.directionOf("WATCHDOG", null));
        assertEquals(Direction.UP, a.directionOf("WATCHDOG", "anything"));
        assertEquals(Direction.UP, a.directionOf("WATCHDOG", "pluginadd"), "a declaration stands in place of the floor's default");
        assertEquals(Direction.ANY, a.directionOf("WATCHDOG", "special"));
        assertEquals(Direction.ANY, a.directionOf("EXEC", "anything"));
        Decision d = a.decide(msg(MsgEvent.Type.WATCHDOG, null, INFRA), T, Set.of(SU), Position.ELSEWHERE, Mode.LOG);
        assertTrue(d.wouldRefuse);
        assertEquals("WATCHDOG", d.directed, "a whole type's count is kept under the type, never under a sender's action");
        assertEquals("WATCHDOG", a.decide(msg(MsgEvent.Type.WATCHDOG, "x".repeat(40), INFRA), T, Set.of(SU), Position.ELSEWHERE, Mode.LOG).directed);

        a.direct("EXEC", "x", Direction.ANY);
        a.direct("EXEC", "x", Direction.DOWN);
        a.direct("EXEC", "x", Direction.ANY);
        assertEquals(Direction.DOWN, a.directionOf("EXEC", "x"), "ANY widened a declaration");
        assertThrows(IllegalArgumentException.class, () -> a.direct("EXEC", "x", Direction.UP), "DOWN and UP for one action");
        assertThrows(IllegalArgumentException.class, () -> a.direct("EXEC", "y", null));
        assertEquals(Direction.DOWN, a.directions().get("EXEC|x"));
        assertTrue(a.declaredKeys().contains("WATCHDOG|*"));
    }

    @CrescoAction(name = "deploy", type = "CONFIG", summary = "x", roles = {"infra", "admin"}, direction = "down")
    @CrescoAction(name = "heartbeat", summary = "x", direction = "UP")
    @CrescoAction(name = "peek", summary = "x", direction = "any")
    @CrescoAction(name = "plain", summary = "x")
    @CrescoAction(name = "run_process", summary = "x")
    static class Annotated { }

    @CrescoAction(name = "typo", summary = "x", direction = "downward")
    static class Mistyped { }

    @Test
    void aPluginDeclaresItsOwnDirectionsOnItsActions() {
        ActionAuthorizer a = new ActionAuthorizer("io.cresco.executor");
        a.register(Annotated.class);
        assertEquals(Direction.DOWN, a.directionOf("CONFIG", "deploy"));
        assertEquals(Direction.UP, a.directionOf("EXEC", "heartbeat"));
        assertEquals(Direction.ANY, a.directionOf("EXEC", "peek"));
        assertEquals(Direction.ANY, a.directionOf("EXEC", "plain"), "no direction on the annotation: inherits ANY");
        assertNull(a.declaredDirection("EXEC", "plain"));
        assertEquals(Direction.DOWN, a.directionOf("EXEC", "run_process"), "no direction on the annotation: inherits the floor's DOWN");
        assertThrows(IllegalArgumentException.class, () -> new ActionAuthorizer("x").register(Mistyped.class),
                "an unknown direction must fail the plugin's start, never read as ANY");
        assertNull(Direction.parse(""));
        assertNull(Direction.parse(null));
    }

    @Test
    void theModeIsOffLogOrEnforceAndLogWhenNotSet() {
        assertEquals(Mode.LOG, Mode.parse(null));
        assertEquals(Mode.LOG, Mode.parse(" "));
        assertEquals(Mode.OFF, Mode.parse("off"));
        assertEquals(Mode.LOG, Mode.parse("Log"));
        assertEquals(Mode.ENFORCE, Mode.parse(" ENFORCE "));
        assertNull(Mode.parse("enforced"), "an unknown value is not a mode (the controller refuses to start on it)");
        assertEquals("enforce", Mode.ENFORCE.id());
    }

    @Test
    void theRefusalSaysFromWhereTheActionIsHonoured() {
        ActionAuthorizer a = table();
        Decision down = a.decide(exec("manage", ADMIN), T, Set.of(SU), Position.ELSEWHERE, Mode.ENFORCE);
        assertEquals("unauthorized: manage is honoured only from the node itself or a controller above it"
                + " (caller api:t1/root[admin] speaks from a node elsewhere)", down.statusDesc());
        Decision up = a.decide(exec("report", INFRA), T, Set.of(SU), Position.ABOVE, Mode.ENFORCE);
        assertEquals("unauthorized: report is honoured only from the node itself or a node below it"
                + " (caller controller:t1/r/a2[infra] speaks from a controller above)", up.statusDesc());
        Decision unknown = a.decide(exec("manage", ADMIN), T, Set.of(SU), Position.UNKNOWN, Mode.ENFORCE);
        assertTrue(unknown.statusDesc().endsWith("(caller api:t1/root[admin] speaks from a node this node cannot place)"), unknown.statusDesc());
        assertEquals("unauthorized: manage needs a caller of this agent's tenant (caller api:t2/root[admin] is of tenant t2, this agent of t1)",
                a.decide(exec("manage", OTHER_ADMIN), T, Set.of(SU), Position.SELF, Mode.ENFORCE).statusDesc(), "a tenant refusal keeps its text");
    }

    // ------------------------------------------------------------------ the position is asked only when it is needed

    /** An authority that answers one position and mode, and counts what it is asked and told. */
    static final class FixedAuthority implements Authority {
        volatile Mode mode;
        volatile Position position;
        final AtomicInteger asked = new AtomicInteger();
        final List<Decision> reported = new CopyOnWriteArrayList<>();
        FixedAuthority(Mode mode, Position position) { this.mode = mode; this.position = position; }
        @Override public Mode mode() { return mode; }
        @Override public Position positionOf(MsgEvent msg, Caller caller) { asked.incrementAndGet(); return position; }
        @Override public void wouldRefuse(MsgEvent msg, Decision decision) { reported.add(decision); }
    }

    @Test
    void thePositionIsAskedOnlyForADirectedActionThatRoleAndTenantAllow() {
        ActionAuthorizer a = table();
        a.require("EXEC", "adminonly", "admin");
        a.direct("EXEC", "adminonly", Direction.DOWN);
        FixedAuthority au = new FixedAuthority(Mode.ENFORCE, Position.ELSEWHERE);
        assertTrue(a.decide(exec("read", INFRA), T, Set.of(SU), au).allowed);
        assertEquals(0, au.asked.get(), "asked for an action honoured from anywhere");
        assertFalse(a.decide(exec("adminonly", USER), T, Set.of(SU), au).allowed);
        assertFalse(a.decide(exec("manage", OTHER_ADMIN), T, Set.of(SU), au).allowed);
        assertFalse(a.decide(exec("manage", null), T, Set.of(SU), au).allowed);
        assertEquals(0, au.asked.get(), "asked for a call that role or tenant refuses");
        au.mode = Mode.OFF;
        assertTrue(a.decide(exec("manage", INFRA), T, Set.of(SU), au).allowed);
        assertEquals(0, au.asked.get(), "asked with the mode off");
        au.mode = Mode.ENFORCE;
        assertFalse(a.decide(exec("manage", INFRA), T, Set.of(SU), au).allowed);
        assertEquals(1, au.asked.get(), "once per decision");
        au.position = Position.ABOVE;
        assertTrue(a.decide(exec("manage", INFRA), T, Set.of(SU), au).allowed);
    }

    @Test
    void anAuthorityThatCannotAnswerFailsClosed() {
        ActionAuthorizer a = table();
        Authority throwing = new Authority() {
            @Override public Mode mode() { return Mode.ENFORCE; }
            @Override public Position positionOf(MsgEvent msg, Caller caller) { throw new IllegalStateException("not ready"); }
        };
        assertFalse(a.decide(exec("manage", INFRA), T, Set.of(SU), throwing).allowed);
        Authority noMode = new Authority() {
            @Override public Mode mode() { throw new IllegalStateException("not ready"); }
            @Override public Position positionOf(MsgEvent msg, Caller caller) { return null; }
        };
        Decision d = a.decide(exec("manage", INFRA), T, Set.of(SU), noMode);
        assertFalse(d.allowed, "no mode and no position");
        assertEquals(Position.UNKNOWN, d.position);
        assertFalse(a.decide(exec("manage", INFRA), T, Set.of(SU), (Authority) null).allowed, "nobody to ask");
        assertTrue(a.decide(exec("read", INFRA), T, Set.of(SU), (Authority) null).allowed);
        assertTrue(a.decide(exec("manage", INFRA), T, Set.of(SU), Authority.NONE).allowed, "a controller of an earlier release");
        assertEquals(Mode.OFF, Authority.NONE.mode());
        assertEquals(Position.UNKNOWN, Authority.NONE.positionOf(null, null));
    }

    // ------------------------------------------------------------------ PluginBuilder

    /** A controller as a plugin sees it; {@code authority} null: a controller of an earlier release (the interface's default). */
    static class StubAgent implements AgentService {
        final List<MsgEvent> out = new CopyOnWriteArrayList<>();
        @Override public AgentState getAgentState() {
            return new AgentState((ControllerState) Proxy.newProxyInstance(ControllerState.class.getClassLoader(),
                    new Class<?>[]{ControllerState.class}, (p, m, a) -> {
                        switch (m.getName()) {
                            case "getRegion": return "r";
                            case "getAgent": return "a";
                            case "isActive": return true;
                            default: return null;
                        }
                    }));
        }
        @Override public DataPlaneService getDataPlaneService() { return null; }
        @Override public CLogger getCLogger(PluginBuilder pb, String b, String i, CLogger.Level l) { return CallerAuthorizationTest.StubAgent.logger(); }
        @Override public CLogger getCLogger(PluginBuilder pb, String b, String i) { return CallerAuthorizationTest.StubAgent.logger(); }
        @Override public void msgOut(String id, MsgEvent msg) { out.add(msg); }
        @Override public void setLogLevel(String logId, CLogger.Level level) { }
        @Override public String getAgentDataDirectory() { return System.getProperty("java.io.tmpdir"); }
        @Override public String getTenant() { return T; }
        @Override public Set<String> getSuperuserTenants() { return Set.of(SU); }
    }

    static final class DirectedAgent extends StubAgent {
        final FixedAuthority authority;
        DirectedAgent(FixedAuthority authority) { this.authority = authority; }
        @Override public Authority authority() { return authority; }
    }

    @CrescoAction(name = "run_process", type = "CONFIG", summary = "x", roles = {"infra", "admin"})
    @CrescoAction(name = "status", type = "CONFIG", summary = "x")
    static final class ProcessExecutor implements Executor {
        final AtomicInteger ran = new AtomicInteger();
        private MsgEvent run(MsgEvent m) { ran.incrementAndGet(); m.setParam("ran", "yes"); return m; }
        public MsgEvent executeCONFIG(MsgEvent m) { return run(m); }
        public MsgEvent executeDISCOVER(MsgEvent m) { return run(m); }
        public MsgEvent executeERROR(MsgEvent m) { return run(m); }
        public MsgEvent executeINFO(MsgEvent m) { return run(m); }
        public MsgEvent executeEXEC(MsgEvent m) { return run(m); }
        public MsgEvent executeWATCHDOG(MsgEvent m) { return run(m); }
        public MsgEvent executeKPI(MsgEvent m) { return run(m); }
    }

    /** The executor plugin, as far as its bundle name and its privileged floor go. */
    private static PluginBuilder executorPlugin(AgentService agent, ProcessExecutor ex) {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("pluginID", "p1");
        PluginBuilder pb = new PluginBuilder(agent, "io.cresco.test.Plugin", null, cfg);
        pb.getAuthorizer().setBundle("io.cresco.executor");
        pb.setExecutor(ex);
        return pb;
    }

    private static MsgEvent call(String action, Caller c) {
        MsgEvent m = new MsgEvent(MsgEvent.Type.CONFIG, "r", "x", "q", "r", "a", "p1", false, false);
        m.setParam("action", action);
        m.setParam("is_rpc", "true");
        m.setParam("callId-r-x-q", "c-1");
        if (c != null) c.stamp(m);
        return m;
    }

    private static void await(java.util.function.BooleanSupplier cond) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!cond.getAsBoolean() && System.currentTimeMillis() < end) Thread.sleep(10);
    }

    @Test
    void aPluginsPrivilegedActionFromASiblingIsRefused403InEnforceAndNeverRuns() throws Exception {
        DirectedAgent agent = new DirectedAgent(new FixedAuthority(Mode.ENFORCE, Position.ELSEWHERE));
        ProcessExecutor ex = new ProcessExecutor();
        PluginBuilder pb = executorPlugin(agent, ex);
        pb.msgIn(call("run_process", Caller.controller(T, "r", "sibling")));
        await(() -> !agent.out.isEmpty());
        assertEquals(0, ex.ran.get(), "a sibling agent's controller ran a process here");
        assertEquals(1, agent.out.size(), "no 403 answer");
        assertEquals("403", agent.out.get(0).getParam("status_code"));
        assertEquals("unauthorized: run_process is honoured only from the node itself or a controller above it"
                + " (caller controller:t1/r/sibling[infra] speaks from a node elsewhere)", agent.out.get(0).getParam("status_desc"));
        assertEquals(1, pb.deniedCount());
        assertTrue(agent.authority.reported.isEmpty(), "a refusal is not a would-refuse");
        // the same call from the region controller above, and from the node itself, runs
        for (Position from : new Position[]{Position.ABOVE, Position.SELF}) {
            int before = ex.ran.get();
            agent.authority.position = from;
            pb.msgIn(call("run_process", Caller.controller(T, "r", "rc")));
            await(() -> ex.ran.get() == before + 1);
            assertEquals(before + 1, ex.ran.get(), "refused from " + from);
        }
        // an action outside the floor is honoured from a sibling
        agent.authority.position = Position.ELSEWHERE;
        int before = ex.ran.get();
        pb.msgIn(call("status", Caller.controller(T, "r", "sibling")));
        await(() -> ex.ran.get() == before + 1);
        assertEquals(before + 1, ex.ran.get());
        assertEquals(1, pb.deniedCount());
        pb.close();
    }

    @Test
    void inLogModeTheSameCallRunsAndTheControllerIsToldOnce() throws Exception {
        DirectedAgent agent = new DirectedAgent(new FixedAuthority(Mode.LOG, Position.ELSEWHERE));
        ProcessExecutor ex = new ProcessExecutor();
        PluginBuilder pb = executorPlugin(agent, ex);
        pb.msgIn(call("run_process", Caller.controller(T, "r", "sibling")));
        await(() -> ex.ran.get() == 1);
        assertEquals(1, ex.ran.get(), "log mode refused a call");
        assertEquals(0, pb.deniedCount());
        await(() -> agent.authority.reported.size() == 1);
        assertEquals(1, agent.authority.reported.size(), "the controller was not told (or told twice)");
        Decision told = agent.authority.reported.get(0);
        assertEquals("run_process", told.directed);
        assertEquals(Position.ELSEWHERE, told.position);
        assertEquals(Direction.DOWN, told.direction);
        // a call from the right place is not reported
        agent.authority.position = Position.ABOVE;
        pb.msgIn(call("run_process", Caller.controller(T, "r", "rc")));
        await(() -> ex.ran.get() == 2);
        assertEquals(2, ex.ran.get());
        assertEquals(1, agent.authority.reported.size());
        pb.close();
    }

    @Test
    void underAControllerOfAnEarlierReleaseNothingChanges() throws Exception {
        StubAgent old = new StubAgent();                         // does not implement authority(): the interface's default
        assertSame(Authority.NONE, old.authority());
        ProcessExecutor ex = new ProcessExecutor();
        PluginBuilder pb = executorPlugin(old, ex);
        pb.msgIn(call("run_process", Caller.controller(T, "r", "sibling")));
        await(() -> ex.ran.get() == 1);
        assertEquals(1, ex.ran.get(), "a plugin built against this library refused a call an earlier controller admits");
        Decision d = pb.authorize(call("run_process", Caller.controller(T, "r", "sibling")));
        assertTrue(d.allowed);
        assertFalse(d.wouldRefuse);
        // role and tenant as ever
        assertFalse(pb.authorize(call("run_process", USER)).allowed);
        pb.close();
    }

    @Test
    void aControllerThatCannotBeAskedFailsClosedForADirectedAction() {
        StubAgent broken = new StubAgent() {
            @Override public Authority authority() { throw new IllegalStateException("stopping"); }
        };
        PluginBuilder pb = executorPlugin(broken, new ProcessExecutor());
        assertFalse(pb.authorize(call("run_process", Caller.controller(T, "r", "rc"))).allowed);
        assertTrue(pb.authorize(call("status", Caller.controller(T, "r", "rc"))).allowed, "an action honoured from anywhere");
        pb.close();
    }

    @Test
    void aCallersNodeIsReadWithoutParsingItsNameAgain() {
        Caller c = Caller.plugin(T, "rg1", "node7", "p@1.0");
        assertEquals("rg1", c.getRegion());
        assertSame(c.getRegion(), c.getRegion(), "the region is split out of the principal on every read");
        assertEquals("node7", c.getAgent());
        assertSame(c.getAgent(), c.getAgent());
        assertEquals("p@1.0", c.getPluginId());
        assertEquals("root", ADMIN.getUser());
        assertNull(ADMIN.getRegion());
        assertNull(ADMIN.getAgent());
        assertNull(Caller.anonymous().getRegion());
        assertNull(Caller.anonymous().getUser());
        List<Caller> all = new ArrayList<>(List.of(INFRA, PLUGIN, ADMIN, USER));
        for (Caller x : all) assertEquals(x, Caller.parse(x.getPrincipal(), x.getTenant(), x.getRolesCsv()));
    }
}
