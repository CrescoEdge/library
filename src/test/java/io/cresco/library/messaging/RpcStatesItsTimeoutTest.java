package io.cresco.library.messaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * An RPC request says how long its caller waits ({@code rpc_timeout_ms}), so that a controller keeps it in a broker no
 * longer than that. Before, only the controller's own calls said so and every plugin's and gateway's request lived five
 * minutes in a queue nothing consumed.
 */
class RpcStatesItsTimeoutTest {

    private static MsgEvent request() {
        MsgEvent m = new MsgEvent(MsgEvent.Type.EXEC, "r", "a", "p", "r", "b", "q", false, false);
        m.setParam("action", "ping");
        return m;
    }

    @Test
    void aRequestStatesItsCallersTimeout() {
        MsgEvent m = request();
        RPC.stateTimeout(m, 5000);
        assertEquals("5000", m.getParam(RPC.TIMEOUT_PARAM));
    }

    @Test
    void aStatementTheRequestAlreadyCarriesIsKept() {
        // a gateway client's own statement, or a parameter an application signed: rewriting it would break that signature
        MsgEvent m = request();
        m.setParam(RPC.TIMEOUT_PARAM, "19000");
        RPC.stateTimeout(m, 20000);
        assertEquals("19000", m.getParam(RPC.TIMEOUT_PARAM));
    }

    @Test
    void noTimeoutNoStatement() {
        MsgEvent m = request();
        RPC.stateTimeout(m, 0);
        RPC.stateTimeout(m, -1);
        assertNull(m.getParam(RPC.TIMEOUT_PARAM));
        RPC.stateTimeout(null, 1000);   // nothing to do, nothing thrown
    }
}
