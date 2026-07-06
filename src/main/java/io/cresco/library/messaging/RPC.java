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
    /** Outstanding calls: callId -> future that its reply will complete. */
    private final ConcurrentHashMap<String, CompletableFuture<MsgEvent>> pending = new ConcurrentHashMap<>();

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
        String callId = java.util.UUID.randomUUID().toString();
        CompletableFuture<MsgEvent> future = new CompletableFuture<>();
        pending.put(callId, future);
        try {
            msg.setParam("callId-" + plugin.getRegion() + "-" + plugin.getAgent() + "-" + plugin.getPluginID(), callId);
            plugin.msgOut(msg);
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
        CompletableFuture<MsgEvent> future = pending.get(callId);
        if (future != null) {
            future.complete(returnMessage);
        }
        // else: late/duplicate reply after timeout+removal — safe to drop.
    }

}