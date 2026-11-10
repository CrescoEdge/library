package io.cresco.library.security;

import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.plugin.Executor;
import io.cresco.library.plugin.PluginBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A plugin answers an RPC request (its caller's call id, keyed by its source) and nothing else. It used to answer any
 * message that carried {@code is_rpc}: such an answer, with no call waiting for it, was run by its receiver as a request
 * and answered again, back and forth until the message's ttl ended.
 */
class OnlyRequestsAreAnsweredTest {

    /** Answers every EXEC with the message itself, as most executors do. */
    static final class Echo implements Executor {
        final AtomicInteger ran = new AtomicInteger();
        public MsgEvent executeCONFIG(MsgEvent m) { return null; }
        public MsgEvent executeDISCOVER(MsgEvent m) { return null; }
        public MsgEvent executeERROR(MsgEvent m) { return null; }
        public MsgEvent executeINFO(MsgEvent m) { return null; }
        public MsgEvent executeEXEC(MsgEvent m) { ran.incrementAndGet(); m.setParam("answer", "42"); return m; }
        public MsgEvent executeWATCHDOG(MsgEvent m) { return null; }
        public MsgEvent executeKPI(MsgEvent m) { return null; }
    }

    private static PluginBuilder plugin(RpcCalleeAndStopTest.StubAgent agent, Echo echo) {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("pluginID", "p1");
        PluginBuilder pb = new PluginBuilder(agent, "io.cresco.test.Plugin", null, cfg);
        pb.setExecutor(echo);
        pb.setIsActive(true);
        return pb;
    }

    /** A message of plugin r/b/p9 for plugin r/a/p1. */
    private static MsgEvent message() {
        MsgEvent m = new MsgEvent(MsgEvent.Type.EXEC, "r", "b", "p9", "r", "a", "p1", false, false);
        m.setParam("action", "getstatus");
        Caller.controller(RpcCalleeAndStopTest.T, "r", "b").stamp(m);
        return m;
    }

    private static void await(java.util.function.BooleanSupplier cond) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!cond.getAsBoolean() && System.currentTimeMillis() < end) Thread.sleep(10);
    }

    @Test
    void aRequestIsAnswered() throws Exception {
        RpcCalleeAndStopTest.StubAgent agent = new RpcCalleeAndStopTest.StubAgent();
        Echo echo = new Echo();
        PluginBuilder pb = plugin(agent, echo);
        MsgEvent m = message();
        m.setParam("is_rpc", "true");
        m.setParam("callId-r-b-p9", "c1");
        pb.msgIn(m);
        await(() -> !agent.out.isEmpty());
        assertEquals(1, agent.out.size(), "the request was not answered");
        MsgEvent reply = agent.out.get(0);
        assertEquals("b", reply.getDstAgent());
        assertEquals("p9", reply.getDstPlugin());
        assertEquals("42", reply.getParam("answer"));
    }

    @Test
    void aMessageThatOnlyCarriesIsRpcIsRunButNotAnswered() throws Exception {
        RpcCalleeAndStopTest.StubAgent agent = new RpcCalleeAndStopTest.StubAgent();
        Echo echo = new Echo();
        PluginBuilder pb = plugin(agent, echo);
        MsgEvent m = message();
        m.setParam("is_rpc", "true");                            // no call id: nobody waits for an answer
        pb.msgIn(m);
        await(() -> echo.ran.get() == 1);
        Thread.sleep(200);
        assertEquals(1, echo.ran.get());
        assertTrue(agent.out.isEmpty(), "a message with is_rpc but no call of its source was answered");
    }

    @Test
    void anAnswerNobodyWaitsForIsNotAnsweredAgain() throws Exception {
        RpcCalleeAndStopTest.StubAgent agent = new RpcCalleeAndStopTest.StubAgent();
        Echo echo = new Echo();
        PluginBuilder pb = plugin(agent, echo);
        // what r/b/p9 sends back for a call r/a/p1 never made (or no longer waits for): the call id is keyed by the
        // DESTINATION here, as setReturn left it
        MsgEvent m = message();
        m.setParam("is_rpc", "true");
        m.setParam("callId-r-x-p7", "c9");
        pb.msgIn(m);
        await(() -> echo.ran.get() == 1);
        Thread.sleep(200);
        assertTrue(agent.out.isEmpty(), "an answer nobody waits for was answered again (the bounce)");
    }
}
