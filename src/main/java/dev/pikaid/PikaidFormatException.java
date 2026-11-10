package dev.pikaid;

/**
 * Thrown when a value cannot be decoded as a valid pikaid.
 *
 * <p>This exception signals invalid input. Strict parsing never normalizes,
 * truncates, or coerces a value; callers must handle the failure explicitly.</p>
 */
public final class PikaidFormatException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a descriptive message.
     *
     * @param message description of the invalid input
     */
    public PikaidFormatException(String message) {
        super(message);
    }
}
