package io.cresco.library.security;

import io.cresco.library.agent.AgentService;
import io.cresco.library.agent.AgentState;
import io.cresco.library.agent.ControllerState;
import io.cresco.library.data.DataPlaneService;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.messaging.RPC;
import io.cresco.library.plugin.Executor;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.utilities.CLogger;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** An RPC reply is bound to the callee it was addressed to; a stopping bundle never waits for the AgentService (OUT-111). */
class RpcCalleeAndStopTest {

    static final String T = "t1", SU = "cresco-system";

    static final class NoopExecutor implements Executor {
        public MsgEvent executeCONFIG(MsgEvent m) { return null; }
        public MsgEvent executeDISCOVER(MsgEvent m) { return null; }
        public MsgEvent executeERROR(MsgEvent m) { return null; }
        public MsgEvent executeINFO(MsgEvent m) { return null; }
        public MsgEvent executeEXEC(MsgEvent m) { return null; }
        public MsgEvent executeWATCHDOG(MsgEvent m) { return null; }
        public MsgEvent executeKPI(MsgEvent m) { return null; }
    }

    private static PluginBuilder builder(StubAgent agent) {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("pluginID", "p1");
        PluginBuilder pb = new PluginBuilder(agent, "io.cresco.test.Plugin", null, cfg);
        pb.setExecutor(new NoopExecutor());
        return pb;
    }

    private static void await(java.util.function.BooleanSupplier cond) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!cond.getAsBoolean() && System.currentTimeMillis() < end) Thread.sleep(10);
    }

    /** An AgentService that records what the plugin sends and answers the tenant questions. */
    static final class StubAgent implements AgentService {
        final List<MsgEvent> out = new CopyOnWriteArrayList<>();
        @Override public AgentState getAgentState() {
            return new AgentState((ControllerState) Proxy.newProxyInstance(ControllerState.class.getClassLoader(),
                    new Class<?>[]{ControllerState.class}, (p, m, a) -> {
                        switch (m.getName()) {
                            case "getRegion": return "r";
                            case "getAgent": return "a";
                            case "isActive": return true;
                            case "isRegionalController": case "isGlobalController": return false;
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
        @Override public String getTenant() { return T; }
        @Override public Set<String> getSuperuserTenants() { return Set.of(SU); }
        static CLogger logger() {
            return (CLogger) Proxy.newProxyInstance(CLogger.class.getClassLoader(), new Class<?>[]{CLogger.class}, (p, m, a) -> null);
        }
    }

    // ------------------------------------------------------------------ RPC callee binding

    @Test
    void anRpcReplyMustComeFromTheCalleeAsked() {
        MsgEvent reply = new MsgEvent(MsgEvent.Type.EXEC, "r", "a", "p1", "r", "x", null, false, false);
        Caller.plugin(T, "r", "a", "p1").stamp(reply);
        assertNull(RPC.calleeRefusal("r", "a", "p1", reply));
        Caller.controller(T, "r", "a").stamp(reply);
        assertNull(RPC.calleeRefusal("r", "a", "p1", reply), "the callee's own controller answers for a missing plugin");
        Caller.plugin(T, "r", "a", "p2").stamp(reply);
        assertNotNull(RPC.calleeRefusal("r", "a", "p1", reply), "another plugin answered");
        Caller.plugin(T, "r", "b", "p1").stamp(reply);
        assertNotNull(RPC.calleeRefusal("r", "a", "p1", reply), "the same plugin id on another agent answered");
        Caller.api(T, "eve", List.of("admin")).stamp(reply);
        assertNotNull(RPC.calleeRefusal("r", "a", null, reply), "an API user answered a call to a controller");
        Caller.plugin(T, "r", "a", "p1").stamp(reply);
        assertNotNull(RPC.calleeRefusal("r", "a", null, reply), "a plugin answered a call to a controller");
        Caller.controller(T, "r", "rc").stamp(reply);
        assertNull(RPC.calleeRefusal("r", "a", null, reply), "a controller (the tiers may route it) answered a controller call");
        reply.setParam(Caller.PARAM_CALLER, "controller:t1/r/a");
        reply.setParam(Caller.PARAM_TENANT, "t9");
        assertNotNull(RPC.calleeRefusal("r", "a", null, reply), "a reply with a malformed stamp");
        Caller.strip(reply);
        assertNotNull(RPC.calleeRefusal("r", "a", null, reply), "the controller stamps every delivery: an unstamped reply is refused");
        Caller.controller(T, "r9", "x").stamp(reply);
        reply.setParam("route_unavailable", "REMOTE_REGION");
        assertNull(RPC.calleeRefusal("r", "a", "p1", reply), "a router's error reply comes from whichever controller could not route");
        Caller.plugin(T, "r9", "x", "p").stamp(reply);
        assertNotNull(RPC.calleeRefusal("r", "a", "p1", reply), "only a controller generates a router error reply");
        reply.removeParam("route_unavailable");
        Caller.anonymous().stamp(reply);
        assertNull(RPC.calleeRefusal("r", "a", null, reply));
    }

    @Test
    void aForgedReplyDoesNotCompleteTheCallButTheCalleesDoes() throws Exception {
        StubAgent agent = new StubAgent();
        PluginBuilder pb = builder(agent);
        MsgEvent req = new MsgEvent(MsgEvent.Type.EXEC, "r", "a", "p1", "r", "b", "p9", false, false);
        Thread caller = new Thread(() -> {
            MsgEvent got = pb.sendRPC(req, 4000);
            req.setParam("result", got == null ? "timeout" : got.getParam("who"));
        });
        caller.start();
        await(() -> !agent.out.isEmpty());
        MsgEvent sent = agent.out.get(0);
        String callIdKey = "callId-r-a-p1";
        MsgEvent forged = new MsgEvent(MsgEvent.Type.EXEC, "r", "b", "p9", "r", "a", "p1", false, false);
        forged.setParam(callIdKey, sent.getParam(callIdKey));
        forged.setParam("ttl", "1");
        forged.setParam("who", "forger");
        Caller.plugin(T, "r", "b", "p8").stamp(forged);
        pb.msgIn(forged);
        MsgEvent real = new MsgEvent(MsgEvent.Type.EXEC, "r", "b", "p9", "r", "a", "p1", false, false);
        real.setParam(callIdKey, sent.getParam(callIdKey));
        real.setParam("ttl", "1");
        real.setParam("who", "callee");
        Caller.plugin(T, "r", "b", "p9").stamp(real);
        pb.msgIn(real);
        caller.join(6000);
        assertEquals("callee", req.getParam("result"));
        pb.close();
    }

    // ------------------------------------------------------------------ OUT-111

    private static Object proxy(Class<?> type, java.lang.reflect.InvocationHandler h) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, h);
    }

    @Test
    void aStoppingBundleNeverWaitsForTheAgentService() throws Exception {
        PluginBuilder pb = new PluginBuilder(new StubAgent(), "io.cresco.test.Plugin", null, new HashMap<>(Map.of("pluginID", "p1")));
        org.osgi.framework.Bundle stopping = (org.osgi.framework.Bundle) proxy(org.osgi.framework.Bundle.class,
                (p, m, a) -> m.getName().equals("getState") ? org.osgi.framework.Bundle.STOPPING : null);
        org.osgi.framework.BundleContext ctx = (org.osgi.framework.BundleContext) proxy(org.osgi.framework.BundleContext.class,
                (p, m, a) -> m.getName().equals("getBundle") ? stopping : null);
        org.osgi.util.tracker.ServiceTracker<Object, Object> never = new org.osgi.util.tracker.ServiceTracker<Object, Object>(
                ctx, "io.cresco.library.agent.AgentService", null) {
            @Override public Object getService() { return null; }
            @Override public Object waitForService(long ms) throws InterruptedException { Thread.sleep(ms); return null; }
        };
        set(pb, "bundleContext", ctx);
        set(pb, "agentServiceTracker", never);
        long t0 = System.currentTimeMillis();
        assertNull(pb.getAgentService());
        assertTrue(System.currentTimeMillis() - t0 < 1000, "getAgentService waited while its bundle was stopping");
    }

    private static void set(Object o, String field, Object v) throws Exception {
        java.lang.reflect.Field f = PluginBuilder.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(o, v);
    }
}
