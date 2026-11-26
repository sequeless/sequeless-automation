package org.sequeless.automation.api;

/**
 * Thrown by {@link ObjectReadPort#find} when the principal is not allowed to read the object. The message
 * must not reveal whether the object exists.
 */
public final class ObjectReadDeniedException extends RuntimeException {

    /**
     * Creates the exception with a message.
     *
     * @param message the detail message
     */
    public ObjectReadDeniedException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and a cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public ObjectReadDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
