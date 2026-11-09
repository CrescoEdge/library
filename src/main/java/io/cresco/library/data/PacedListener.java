package io.cresco.library.data;

import jakarta.jms.MessageListener;

/**
 * A dataplane listener that may block in {@link #onMessage} to slow its stream: backpressure. wsapi's WebSocket
 * consumers are paced listeners; while one waits for its client to read, what it has not taken stays at the broker, whose
 * producer flow control then slows the stream's writers instead of discarding anything.
 *
 * <p>The dataplane gives each paced listener a JMS session of its own (every other listener of a node shares one), so a
 * wait holds back only this listener's messages, in order. A paced listener must not wait forever: when it gives up on a
 * consumer that reads nothing (wsapi closes it), it returns and is removed.
 */
public interface PacedListener extends MessageListener {
}
