package io.cresco.library.security;

import io.cresco.library.agent.AgentService;
import io.cresco.library.agent.AgentState;
import io.cresco.library.agent.ControllerState;
import io.cresco.library.capability.CrescoAction;
import io.cresco.library.data.DataPlaneService;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.plugin.Executor;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.utilities.CLogger;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The verified-caller contract (controller docs/caller-authorization.md section 3) as the library enforces it: roles, the
 * privileged floor, destination-tenant scope, deny by default with a 403.
 */
class CallerAuthorizationTest {

    static final String T = "t1", SU = "cresco-system";

    @CrescoAction(name = "dangerous", type = "CONFIG", summary = "x", roles = {"infra", "admin"})
    @CrescoAction(name = "forusers", summary = "x", roles = {"user", "admin"})
    @CrescoAction(name = "forplugins", summary = "x", roles = {"plugin"})
    @CrescoAction(name = "open", summary = "x")
    static class Annotated { }

    private static MsgEvent msg(MsgEvent.Type type, String action, Caller c) {
        MsgEvent m = new MsgEvent(type, "r", "a", null, "r", "a", null, false, false);
        if (action != null) m.setParam("action", action);
        if (c != null) c.stamp(m);
        return m;
    }

    private static ActionAuthorizer authz(String bundle) {
        ActionAuthorizer a = new ActionAuthorizer(bundle);
        a.register(Annotated.class);
        return a;
    }

    private static boolean ok(ActionAuthorizer a, MsgEvent.Type type, String action, Caller c) {
        return a.decide(msg(type, action, c), T, Set.of(SU)).allowed;
    }

    static final Caller INFRA = Caller.controller(T, "r", "rc"), ADMIN = Caller.api(T, "root", List.of("admin")),
            USER = Caller.api(T, "bob", List.of("user")), PLUGIN = Caller.plugin(T, "r", "a", "p1"),
            OTHER_INFRA = Caller.controller("t2", "r9", "rc"), OTHER_ADMIN = Caller.api("t2", "root", List.of("admin")),
            OTHER_USER = Caller.api("t2", "eve", List.of("user")), OTHER_PLUGIN = Caller.plugin("t2", "r9", "x", "p1"),
            SU_ADMIN = Caller.api(SU, "op", List.of("admin")), SU_USER = Caller.api(SU, "op", List.of("user")),
            SU_INFRA = Caller.controller(SU, "rg", "gc"), SU_PLUGIN = Caller.plugin(SU, "rg", "gc", "p");

    @Test
    void noVerifiedCallerIsRefusedEvenForAnOpenAction() {
        ActionAuthorizer.Decision d = authz(null).decide(msg(MsgEvent.Type.EXEC, "open", null), T, Set.of());
        assertFalse(d.allowed);
        assertTrue(d.statusDesc().startsWith("unauthorized: open needs"), d.statusDesc());
        assertTrue(d.statusDesc().contains("no verified caller"), d.statusDesc());
    }

    @Test
    void aForgedOrMalformedStampIsNoCaller() {
        MsgEvent m = msg(MsgEvent.Type.EXEC, "open", null);
        m.setParam(Caller.PARAM_CALLER, "controller:t1/r/rc");
        m.setParam(Caller.PARAM_TENANT, "t2");                       // inconsistent with the principal
        m.setParam(Caller.PARAM_ROLES, "infra");
        ActionAuthorizer.Decision d = authz(null).decide(m, T, Set.of());
        assertFalse(d.allowed);
        assertTrue(d.statusDesc().contains("malformed"), d.statusDesc());
        m.setParam(Caller.PARAM_TENANT, "t1");
        m.setParam(Caller.PARAM_ROLES, "root");                      // an unknown role
        assertFalse(authz(null).decide(m, T, Set.of()).allowed);
    }

    @Test
    void privilegedActionsAdmitOnlyInScopeInfraAndAdmin() {
        ActionAuthorizer a = authz(null);
        assertTrue(ok(a, MsgEvent.Type.CONFIG, "dangerous", INFRA));
        assertTrue(ok(a, MsgEvent.Type.CONFIG, "dangerous", ADMIN));
        assertFalse(ok(a, MsgEvent.Type.CONFIG, "dangerous", USER));
        assertFalse(ok(a, MsgEvent.Type.CONFIG, "dangerous", PLUGIN));
        assertFalse(ok(a, MsgEvent.Type.CONFIG, "dangerous", OTHER_ADMIN), "another tenant's admin acts only on its own tenant's agents");
        assertFalse(ok(a, MsgEvent.Type.CONFIG, "dangerous", OTHER_INFRA), "another tenant's controller");
        assertTrue(ok(a, MsgEvent.Type.CONFIG, "dangerous", SU_ADMIN), "a superuser-tenant admin");
        assertTrue(ok(a, MsgEvent.Type.CONFIG, "dangerous", SU_INFRA), "a superuser-tenant controller");
        assertFalse(ok(a, MsgEvent.Type.CONFIG, "dangerous", SU_USER), "a superuser-tenant user is still only a user");
        assertTrue(ok(a, MsgEvent.Type.CONFIG, "dangerous", Caller.anonymous()), "insecure development mode");
    }

    @Test
    void openActionsAdmitTheAgentsTenantAndSuperuserInfraOrAdmin() {
        ActionAuthorizer a = authz(null);
        for (Caller c : List.of(INFRA, ADMIN, USER, PLUGIN, SU_ADMIN, SU_INFRA)) assertTrue(ok(a, MsgEvent.Type.EXEC, "open", c), c.toString());
        for (Caller c : List.of(OTHER_INFRA, OTHER_ADMIN, OTHER_USER, OTHER_PLUGIN, SU_USER, SU_PLUGIN)) {
            assertFalse(ok(a, MsgEvent.Type.EXEC, "open", c), c.toString());
        }
        assertTrue(ok(a, MsgEvent.Type.KPI, null, PLUGIN), "a message without an action is open to the tenant");
        assertFalse(a.decide(msg(MsgEvent.Type.EXEC, "open", INFRA), null, Set.of(SU)).allowed,
                "an unknown destination tenant admits only what needs no tenant");
        assertTrue(a.decide(msg(MsgEvent.Type.EXEC, "open", SU_INFRA), null, Set.of(SU)).allowed);
    }

    @Test
    void declaredRolesAreExactlyTheRolesAllowed() {
        ActionAuthorizer a = authz(null);
        assertTrue(ok(a, MsgEvent.Type.EXEC, "forusers", USER));
        assertTrue(ok(a, MsgEvent.Type.EXEC, "forusers", ADMIN));
        assertFalse(ok(a, MsgEvent.Type.EXEC, "forusers", INFRA), "infra holds none of the roles named");
        assertFalse(ok(a, MsgEvent.Type.EXEC, "forusers", PLUGIN));
        assertFalse(ok(a, MsgEvent.Type.EXEC, "forusers", OTHER_USER));
        assertTrue(ok(a, MsgEvent.Type.EXEC, "forplugins", PLUGIN));
        assertFalse(ok(a, MsgEvent.Type.EXEC, "forplugins", OTHER_PLUGIN), "a plugin never crosses a tenant");
        assertFalse(ok(a, MsgEvent.Type.EXEC, "forplugins", SU_PLUGIN), "not even from a superuser tenant");
        assertTrue(ok(a, MsgEvent.Type.CONFIG, "forplugins", USER), "the key is (type, action): CONFIG forplugins is undeclared, so open");
    }

    @Test
    void thePrivilegedFloorCannotBeRemovedByAnAnnotation() {
        // a filerepo build that annotates putjar as open (or forgets it) still requires infra or admin
        @CrescoAction(name = "putjar", summary = "x", roles = {"user", "plugin"}) class Lax { }
        ActionAuthorizer a = new ActionAuthorizer("io.cresco.filerepo");
        a.register(Lax.class);
        assertFalse(ok(a, MsgEvent.Type.EXEC, "putjar", USER));
        assertFalse(ok(a, MsgEvent.Type.EXEC, "putjar", PLUGIN));
        assertTrue(ok(a, MsgEvent.Type.EXEC, "putjar", INFRA), "an empty intersection leaves infra");
        assertFalse(ok(a, MsgEvent.Type.EXEC, "putjar", ADMIN));
        ActionAuthorizer c = new ActionAuthorizer(ActionAuthorizer.CONTROLLER_BUNDLE);
        for (String action : ActionAuthorizer.PRIVILEGED.get(ActionAuthorizer.CONTROLLER_BUNDLE)) {
            for (MsgEvent.Type t : new MsgEvent.Type[]{MsgEvent.Type.CONFIG, MsgEvent.Type.EXEC}) {
                assertFalse(ok(c, t, action, PLUGIN), action + " from a plugin");
                assertFalse(ok(c, t, action, USER), action + " from a user");
                assertFalse(c.decide(msg(t, action, null), T, Set.of()).allowed, action + " without a caller");
                assertTrue(ok(c, t, action, INFRA), action + " from infra");
                assertTrue(ok(c, t, action, ADMIN), action + " from admin");
                assertFalse(ok(c, t, action, OTHER_ADMIN), action + " from another tenant's admin");
            }
        }
        for (String a2 : List.of("setloglevel", "setlogdp", "stopcontroller")) {
            assertTrue(ActionAuthorizer.PRIVILEGED.get(ActionAuthorizer.CONTROLLER_BUNDLE).contains(a2), a2);
        }
    }

    @Test
    void anUndeclaredActionTakesTheDefaultAndCrossTenantInfraIsPerAction() {
        ActionAuthorizer a = new ActionAuthorizer(null);
        assertTrue(ok(a, MsgEvent.Type.EXEC, "whatever", USER), "a plugin's undeclared action is open to its tenant");
        a.setDefaultRoles("infra", "admin");
        assertFalse(ok(a, MsgEvent.Type.EXEC, "whatever", USER), "the controller fails closed on what it did not declare");
        assertTrue(ok(a, MsgEvent.Type.EXEC, "whatever", ADMIN));
        a.require("EXEC", "ping", "infra", "admin", "plugin", "user");
        assertFalse(ok(a, MsgEvent.Type.EXEC, "ping", OTHER_INFRA));
        a.allowAnyTenantInfra("EXEC", "ping");
        assertTrue(ok(a, MsgEvent.Type.EXEC, "ping", OTHER_INFRA), "fabric mechanics admit every tenant's controllers");
        assertFalse(ok(a, MsgEvent.Type.EXEC, "ping", OTHER_ADMIN), "but not another tenant's admin");
        assertFalse(ok(a, MsgEvent.Type.EXEC, "ping", OTHER_PLUGIN));
        a.require("WATCHDOG", null, "infra");
        assertTrue(ok(a, MsgEvent.Type.WATCHDOG, null, INFRA));
        assertFalse(ok(a, MsgEvent.Type.WATCHDOG, null, PLUGIN), "a type-level requirement for messages without an action");
    }

    @Test
    void anUnknownRoleInAnAnnotationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ActionAuthorizer(null).require("EXEC", "x", "root"));
    }

    // ------------------------------------------------------------------ PluginBuilder enforcement

    /** An AgentService that records what the plugin sends and answers the tenant questions. */
    static final class StubAgent implements AgentService {
        final List<MsgEvent> out = new CopyOnWriteArrayList<>();
        volatile String tenant = T;
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
        @Override public CLogger getCLogger(PluginBuilder pb, String b, String i, CLogger.Level l) { return logger(); }
        @Override public CLogger getCLogger(PluginBuilder pb, String b, String i) { return logger(); }
        @Override public void msgOut(String id, MsgEvent msg) { out.add(msg); }
        @Override public void setLogLevel(String logId, CLogger.Level level) { }
        @Override public String getAgentDataDirectory() { return System.getProperty("java.io.tmpdir"); }
        @Override public String getTenant() { return tenant; }
        @Override public Set<String> getSuperuserTenants() { return Set.of(SU); }
        static CLogger logger() {
            return (CLogger) Proxy.newProxyInstance(CLogger.class.getClassLoader(), new Class<?>[]{CLogger.class}, (p, m, a) -> null);
        }
    }

    @CrescoAction(name = "wipe", type = "CONFIG", summary = "x", roles = {"infra", "admin"})
    @CrescoAction(name = "hello", type = "CONFIG", summary = "x")
    static final class CountingExecutor implements Executor {
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

    private static PluginBuilder builder(StubAgent agent, CountingExecutor ex) {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("pluginID", "p1");
        PluginBuilder pb = new PluginBuilder(agent, "io.cresco.test.Plugin", null, cfg);
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
    void aRefusedCallNeverReachesTheExecutorAndIsAnswered403() throws Exception {
        StubAgent agent = new StubAgent();
        CountingExecutor ex = new CountingExecutor();
        PluginBuilder pb = builder(agent, ex);
        for (Caller c : Arrays.asList(PLUGIN, USER, null, OTHER_ADMIN)) {
            agent.out.clear();
            pb.msgIn(call("wipe", c));
            await(() -> !agent.out.isEmpty());
            assertEquals(0, ex.ran.get(), "the executor ran a privileged action for " + c);
            assertEquals(1, agent.out.size(), "no 403 answer for " + c);
            MsgEvent answer = agent.out.get(0);
            assertEquals("403", answer.getParam("status_code"));
            assertTrue(answer.getParam("status_desc").startsWith("unauthorized: wipe needs admin or infra"), answer.getParam("status_desc"));
            assertEquals("x", answer.getDstAgent(), "the 403 goes back to the caller");
        }
        assertEquals(4, pb.deniedCount());
        agent.out.clear();
        pb.msgIn(call("wipe", ADMIN));
        await(() -> ex.ran.get() == 1);
        assertEquals(1, ex.ran.get(), "admin was refused");
        pb.msgIn(call("hello", USER));
        await(() -> ex.ran.get() == 2);
        assertEquals(2, ex.ran.get(), "an open action was refused to a same-tenant user");
        agent.tenant = "t2";
        pb.msgIn(call("hello", USER));
        await(() -> pb.deniedCount() == 5);
        assertEquals(5, pb.deniedCount(), "the destination's own tenant decides");
        assertEquals(2, ex.ran.get());
        pb.close();
    }

    @Test
    void aRefusedMessageThatIsNotACallIsDroppedSilently() throws Exception {
        StubAgent agent = new StubAgent();
        CountingExecutor ex = new CountingExecutor();
        PluginBuilder pb = builder(agent, ex);
        MsgEvent m = call("hello", null);
        m.removeParam("is_rpc");
        pb.msgIn(m);
        await(() -> pb.deniedCount() == 1);
        Thread.sleep(100);
        assertEquals(0, ex.ran.get());
        assertTrue(agent.out.isEmpty(), "a refused one-way message was answered");
        pb.close();
    }

    @Test
    void aMalformedTtlOnAReplyShapedMessageDoesNotThrow() {
        StubAgent agent = new StubAgent();
        PluginBuilder pb = builder(agent, new CountingExecutor());
        MsgEvent m = new MsgEvent(MsgEvent.Type.EXEC, "r", "b", "p9", "r", "a", "p1", false, false);
        m.setParam("callId-r-a-p1", "nope");
        m.setParam("ttl", "x");
        assertDoesNotThrow(() -> pb.msgIn(m));
        pb.close();
    }
}
