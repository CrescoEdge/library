package io.cresco.library.messaging;



import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.utilities.CLogger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Cresco remote procedure call helper.
 *
 * Event-driven: each outstanding call registers a {@link CompletableFuture} under its callId and
 * blocks on it with the caller's timeout. {@link #putReturnMessage} completes that future the instant
 * the reply lands, so the caller wakes immediately. The previous implementation polled the reply map on
 * a fixed 100 ms interval, which quantised EVERY round-trip up to the next 100 ms boundary -- a 10 ms
 * link and a 100 ms link both measured ~100 ms. Cresco harvests link RTT from these calls to drive the
 * cost model, so that quantisation flattened the very latency differential the router needs to route on.
 * Immediate wakeup makes the measured RTT reflect the real path latency.
 *
 * @author V.K. Cody Bumgardner
 * @author Caylin Hickey
 * @since 0.1.0
 */
public class RPC {
    /** Default RPC timeout (ms) for the no-timeout call(), preserving the old 300 x 100 ms budget. */
    private static final long DEFAULT_TIMEOUT_MS = 30000;
    /** Cresco logger */
    private CLogger logger;
    /** One outstanding call: the future its reply completes, and the callee it was addressed to. */
    private static final class Call {
        final CompletableFuture<MsgEvent> future = new CompletableFuture<>();
        final String region, agent, plugin;
        Call(MsgEvent request) {
            this.region = request.getDstRegion();
            this.agent = request.getDstAgent();
            this.plugin = request.getDstPlugin();
        }
    }

    /** Outstanding calls: callId -> the call its reply completes. */
    private final ConcurrentHashMap<String, Call> pending = new ConcurrentHashMap<>();

    private PluginBuilder plugin;
    /**
     * Constructor
     * @param plugin        PluginBuilder
     */
    public RPC(PluginBuilder plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger(RPC.class.getName(),CLogger.Level.Info);
    }

    /**
     * Issues a remote procedure call
     * @param msg           Message to send
     * @param timeout       How long to wait for the response (in milliseconds)
     * @return              The return message, null if no return is received
     */
    public MsgEvent call(MsgEvent msg, long timeout) {
        return call(msg, timeout, null);
    }

    /**
     * Issues a remote procedure call on behalf of an API user ({@link PluginBuilder#msgOutOnBehalfOf}).
     * @param msg           Message to send
     * @param caller        The API user, or null to send as this plugin
     * @return              The return message, null if no return is received
     */
    public MsgEvent call(MsgEvent msg, io.cresco.library.security.Caller caller) {
        return call(msg, DEFAULT_TIMEOUT_MS, caller);
    }

    /**
     * Issues a remote procedure call, on behalf of an API user when {@code caller} is not null.
     * @param msg           Message to send
     * @param timeout       How long to wait for the response (in milliseconds)
     * @param caller        The API user, or null to send as this plugin
     * @return              The return message, null if no return is received
     */
    public MsgEvent call(MsgEvent msg, long timeout, io.cresco.library.security.Caller caller) {
        String callId = java.util.UUID.randomUUID().toString();
        Call pendingCall = new Call(msg);
        CompletableFuture<MsgEvent> future = pendingCall.future;
        pending.put(callId, pendingCall);
        try {
            msg.setParam("callId-" + plugin.getRegion() + "-" + plugin.getAgent() + "-" + plugin.getPluginID(), callId);
            if (caller != null) {
                plugin.msgOutOnBehalfOf(msg, caller);
            } else {
                plugin.msgOut(msg);
            }
            // Block until the reply completes the future (immediate wakeup) or the timeout elapses.
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            return null; // no reply within the caller's budget
        } catch (Exception ex) {
            logger.error("call", ex);
            return null;
        } finally {
            pending.remove(callId); // never leak the registration, reply or not
        }
    }

    /**
     * Issues a remote procedure call
     * @param msg           Message to send
     * @return              The return message, null if no return is received
     */
    public MsgEvent call(MsgEvent msg) {
        return call(msg, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Places the return message for retrieval — completes the waiting call's future, waking it at once.
     * @param callId            ID of the remote-procedural call
     * @param returnMessage     The return message
     */
    public void putReturnMessage(String callId, MsgEvent returnMessage) {
        putReturnMessage(callId, returnMessage, false);
    }

    /**
     * As {@link #putReturnMessage(String, MsgEvent)}. {@code inProcess}: the reply was produced in this bundle by its own
     * executor (a call to itself), so it is not checked against the callee; any other reply must come from the callee the
     * call was addressed to ({@link #calleeRefusal}), or it is dropped and the call keeps waiting.
     */
    public void putReturnMessage(String callId, MsgEvent returnMessage, boolean inProcess) {
        Call call = pending.get(callId);
        if (call == null) {
            return; // late/duplicate reply after timeout+removal — safe to drop.
        }
        if (!inProcess) {
            String why = calleeRefusal(call.region, call.agent, call.plugin, returnMessage);
            if (why != null) {
                logger.warn("RPC reply DROPPED: " + why);
                return;
            }
        }
        call.future.complete(returnMessage);
    }

    /**
     * Why a reply may not answer a call addressed to {@code region/agent/plugin}, or null when it may. The reply's verified
     * caller ({@link io.cresco.library.security.Caller}) must be the callee: for a call to a controller (no plugin), any
     * controller ({@code infra}; the controller tiers may route a controller function to the regional or global
     * controller); for a call to a plugin, that plugin, or the controller of its agent (which answers when the plugin is
     * not there). {@code anonymous} (insecure development mode) passes.
     *
     * <p>A reply that carries NO verified caller is accepted as before while the fabric does not stamp callers yet (the
     * reply headers are written by the sender and change in transit -- {@code region_alias} normalization, controller
     * tiers -- so they bind nothing); once callers are stamped, every reply carries one and an unstamped reply is refused.
     */
    public static String calleeRefusal(String region, String agent, String plugin, MsgEvent reply) {
        io.cresco.library.security.Caller who = io.cresco.library.security.Caller.fromMsgEvent(reply);
        if (who == null) return io.cresco.library.security.Caller.hasAny(reply) ? "its caller stamp is malformed" : null;
        if (who.isAnonymous()) return null;
        if (plugin == null) {
            return who.hasRole(io.cresco.library.security.Caller.ROLE_INFRA) ? null
                    : "a call to the controller of " + region + "/" + agent + " was answered by " + who + ", not by a controller";
        }
        boolean sameNode = region != null && region.equals(who.getRegion()) && agent != null && agent.equals(who.getAgent());
        io.cresco.library.security.Caller.Kind k = who.getKind();
        if (sameNode && (k == io.cresco.library.security.Caller.Kind.CONTROLLER || k == io.cresco.library.security.Caller.Kind.AGENT)) return null;
        if (sameNode && k == io.cresco.library.security.Caller.Kind.PLUGIN && plugin.equals(who.getPluginId())) return null;
        return "a call to " + region + "/" + agent + "/" + plugin + " was answered by " + who;
    }

}