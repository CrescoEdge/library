package io.cresco.library.security;

import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.messaging.RPC;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent adversarial review of Phase 3 (library c5e902a), 2026-11-04. Each test asserts the outcome the contract
 * (controller docs/caller-authorization.md) promises; a failure is a finding. Review-only: never pushed.
 */
class ReviewPhase3RpcTest {

    private static MsgEvent reply(String fromRegion, String fromAgent, String fromPlugin, Caller stamp) {
        MsgEvent r = new MsgEvent(MsgEvent.Type.EXEC, fromRegion, fromAgent, fromPlugin, "rgC", "caller", "p-caller", false, false);
        r.setParam("is_rpc", "true");
        stamp.stamp(r);
        return r;
    }

    /**
     * Compatibility (GaiaKeep gfs#26, controller RegionAlias): a node moved to another region keeps answering at its former
     * address (region_alias). A caller that addressed it there records the FORMER region as the callee; the reply is stamped
     * with the node's CURRENT region (its verified identity), so calleeRefusal drops it and every such call times out on a
     * secured fabric. Phase 1 accepted unstamped replies, so this only breaks now that every reply is stamped.
     */
    @Test
    void aCallAddressedByRegionAliasIsAnsweredByTheMovedNode() {
        // gfs storage core names its peers by Cresco address: it calls rgOld/a1/gfs; a1 now lives in rgNew (region_alias=rgOld)
        MsgEvent r = reply("rgOld", "a1", "plugin-gfs", Caller.plugin("t1", "rgNew", "a1", "plugin-gfs"));
        // fix (hardening, F8): the alias comes from a trusted source -- the broker a1 is attached to stamps the former region
        // its region's registry recorded from a1's own verified registration (Caller.PARAM_ALIAS); a sender's value is stripped
        Caller.plugin("t1", "rgNew", "a1", "plugin-gfs").stampRegionAlias(r, "rgOld");
        String why = RPC.calleeRefusal("rgOld", "a1", "plugin-gfs", r);
        assertNull(why, "the moved node's own reply to a call made at its region_alias is dropped (" + why
                + "): region_alias (gfs#26) no longer works with broker_security_enabled=true");
    }

    /**
     * The route-error exemption: any controller ({@code infra}, of any tenant) may answer any call as long as the reply carries
     * {@code route_unavailable} or {@code route_refused}. The reply is then delivered to the caller WHOLE: it can carry any
     * result parameters, so a relay (every regional/global controller on the route, and every source-route waypoint) that sees
     * the call id forges the callee's answer. The exemption should only ever fail the call (or strip everything but the
     * error), never deliver a payload the callee did not write.
     */
    @Test
    void aRouteErrorFromAnotherTenantsControllerCannotCarryAnAnswer() {
        MsgEvent r = reply("rgEvil", "relay", null, Caller.controller("t2", "rgEvil", "relay"));
        r.setParam("route_unavailable", "x");
        r.setParam("status_code", "10");
        r.setParam("is_registered", "true");
        r.setParam("pluginid", "plugin-forged");
        String why = RPC.calleeRefusal("rgA", "a1", "plugin-7", r);
        assertNotNull(why, "a reply from controller:t2/rgEvil/relay (not the callee rgA/a1/plugin-7, another tenant) carrying a"
                + " route_unavailable marker plus forged result params (status_code=10, pluginid) is accepted as the callee's answer");
    }

    // ---- added with the fixes (hardening, F8/F9) ----

    /** Without the infrastructure-stamped alias the moved node is not the callee of a call made at its former address. */
    @Test
    void withoutATrustedAliasAReplyFromAnotherRegionIsRefused() {
        MsgEvent r = reply("rgOld", "a1", "plugin-gfs", Caller.plugin("t1", "rgNew", "a1", "plugin-gfs"));
        assertNotNull(RPC.calleeRefusal("rgOld", "a1", "plugin-gfs", r));
        // an alias for ANOTHER former region, or for another agent name, binds nothing
        Caller.plugin("t1", "rgNew", "a1", "plugin-gfs").stampRegionAlias(r, "rgOther");
        assertNotNull(RPC.calleeRefusal("rgOld", "a1", "plugin-gfs", r));
        Caller.plugin("t1", "rgNew", "a1", "plugin-gfs").stampRegionAlias(r, "rgOld");
        assertNotNull(RPC.calleeRefusal("rgOld", "a2", "plugin-gfs", r), "the alias is a region, never another agent");
        // re-stamping the caller drops a stale alias
        Caller.plugin("t1", "rgNew", "a1", "plugin-gfs").stamp(r);
        assertNull(r.getParam(Caller.PARAM_ALIAS));
        assertNotNull(RPC.calleeRefusal("rgOld", "a1", "plugin-gfs", r));
    }

    /** An API or anonymous stamp never carries an alias; an invalid alias is ignored. */
    @Test
    void anAliasBindsOnlyANodeStamp() {
        MsgEvent r = reply("rgOld", "a1", "plugin-gfs", Caller.api("t1", "u", java.util.List.of("user")));
        r.setParam(Caller.PARAM_ALIAS, "rgOld");
        assertNull(Caller.regionAliasOf(r));
        MsgEvent n = reply("rgOld", "a1", "plugin-gfs", Caller.plugin("t1", "rgNew", "a1", "plugin-gfs"));
        n.setParam(Caller.PARAM_ALIAS, "rg_Old");
        assertNull(Caller.regionAliasOf(n));
        n.setParam(Caller.PARAM_ALIAS, "rgNew");
        assertNull(Caller.regionAliasOf(n), "the node's own region is no alias");
    }

    /** A route error from a controller that is not the callee FAILS the call: marker and error only, nothing else. */
    @Test
    void aRouteErrorOnlyFailsTheCall() {
        MsgEvent r = reply("rgEvil", "relay", null, Caller.controller("t2", "rgEvil", "relay"));
        r.setParam("route_unavailable", "x");
        r.setParam("error", "no route");
        r.setParam("status_code", "10");
        r.setParam("is_registered", "true");
        r.setParam("pluginid", "plugin-forged");
        MsgEvent f = RPC.routeFailure(r);
        assertNotNull(f);
        assertEquals("x", f.getParam("route_unavailable"));
        assertEquals("no route", f.getParam("error"));
        assertEquals("503", f.getParam("status_code"));
        assertNull(f.getParam("is_registered"));
        assertNull(f.getParam("pluginid"));
        assertEquals(Caller.controller("t2", "rgEvil", "relay"), Caller.fromMsgEvent(f));
        // not a route error, or not from a controller: no failure is made of it
        MsgEvent plain = reply("rgEvil", "relay", null, Caller.controller("t2", "rgEvil", "relay"));
        assertNull(RPC.routeFailure(plain));
        MsgEvent fromPlugin = reply("rgEvil", "relay", "p", Caller.plugin("t2", "rgEvil", "relay", "p"));
        fromPlugin.setParam("route_refused", "tier");
        assertNull(RPC.routeFailure(fromPlugin));
    }
}
