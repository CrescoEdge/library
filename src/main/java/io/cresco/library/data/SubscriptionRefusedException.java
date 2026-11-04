package io.cresco.library.data;

/**
 * The broker refused a dataplane subscription by policy (tenant isolation, or a recipient-bound stream asked for by
 * another identity). It is not a transient failure: retrying the same subscription is refused again. A transient failure
 * (no session, broker unreachable) is not this exception; those methods return null instead.
 *
 * <p>It extends {@link SecurityException}, so callers that already catch that keep working; new callers should catch
 * this type, which {@link DataPlaneService} declares on every subscribing method.
 */
public class SubscriptionRefusedException extends SecurityException {

    private static final long serialVersionUID = 1L;

    public SubscriptionRefusedException(String reason) {
        super(reason);
    }
}
