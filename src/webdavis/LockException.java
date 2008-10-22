package webdavis;

/**
 * Represents a locking condition error.
 *
 * @author Eric Glass
 */
public class LockException extends Exception {

    private final int status;

    /**
     * Creates a <code>LockException</code> which causes the specified
     * HTTP status.
     *
     * @param status The status resulting from this lock exception.
     */
    public LockException(int status) {
        this.status = status;
    }

    /**
     * Returns the HTTP status code.
     *
     * @return An <code>int</code> containing the HTTP status code
     * this lock exception causes.
     */
    public int getStatus() {
        return status;
    }

}
