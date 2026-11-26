package org.sequeless.automation.api;

/**
 * Thrown by a {@link StoreCommandPort} method when the Store rejects a command, for a version conflict or
 * a denial. The claim fails, and the row is retried.
 */
public final class StoreCommandRejectedException extends RuntimeException {

    /**
     * Creates the exception with a message.
     *
     * @param message the detail message
     */
    public StoreCommandRejectedException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and a cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public StoreCommandRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
