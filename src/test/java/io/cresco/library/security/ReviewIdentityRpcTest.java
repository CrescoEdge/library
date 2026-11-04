package io.cresco.library.security;

import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.messaging.RPC;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent review of the 1.3 identity hardening (library c64486e), 2026-11-04. Each test asserts the SECURE outcome; a
 * failure is a finding.
 */
class ReviewIdentityRpcTest {

    /**
     * RPC callee binding accepts any reply whose stamp parses as {@code anonymous} (RPC.calleeRefusal: isAnonymous -> null),
     * whatever THIS node's mode, and Caller.parse returns anonymous() for principal "anonymous" with ANY tenant and roles.
     * Until phase 3 strips the reserved parameters nothing removes a sender-written stamp, and afterwards an anonymous stamp
     * kept across a bridge hop from an insecure-mode broker would still pass: a reply from anyone answers for any callee.
     */
    @Test
    void aReplyStampedAnonymousDoesNotAnswerForAnotherCallee() {
        MsgEvent reply = new MsgEvent(MsgEvent.Type.EXEC, "rgX", "evil", null, "rgA", "a1", "p1", false, false);
        reply.setParam(Caller.PARAM_CALLER, "anonymous");
        reply.setParam(Caller.PARAM_TENANT, "any-tenant");
        reply.setParam(Caller.PARAM_ROLES, "anything");
        String why = RPC.calleeRefusal("rgA", "a1", "plugin/7", reply);
        System.out.println("RPC: a reply forged as 'anonymous' from rgX/evil to a call for rgA/a1/plugin/7 -> " + (why == null ? "ACCEPTED" : why));
        assertNotNull(why, "a reply stamped 'anonymous' (by anyone) answers a call addressed to rgA/a1/plugin/7 on a node that is"
                + " not in insecure development mode");
    }
}
