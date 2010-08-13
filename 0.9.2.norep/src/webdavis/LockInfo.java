package webdavis;

import org.w3c.dom.DocumentFragment;

/**
 * Represents information associated with a lock.
 *
 * @author Eric Glass
 */
public class LockInfo {

    private boolean exclusive;

    private DocumentFragment owner;

    private int depth = DavisUtilities.INFINITE_DEPTH;

    private long timeout = DavisUtilities.UNSPECIFIED_TIMEOUT;

    /**
     * Indicates whether the lock is exclusive.
     *
     * @return A <code>boolean</code> indicating whether this is an
     * exclusive (<code>true</code>) or shared (<code>false</code>) lock.
     */
    public boolean isExclusive() {
        return exclusive;
    }

    /**
     * Sets the lock to be exclusive or shared.
     *
     * @param exclusive Indicates whether the lock should be exclusive
     * (<code>true</code>) or shared (<code>false</code>).
     */ 
    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    /**
     * Returns the lock owner information.
     *
     * @return A <code>DocumentFragment</code> containing the XML fragment
     * representing the lock owner information.
     */
    public DocumentFragment getOwner() {
        return owner;
    }

    /**
     * Sets the lock owner information.
     *
     * @param owner The XML fragment representing the lock owner information.
     */
    public void setOwner(DocumentFragment owner) {
        this.owner = owner;
    }

    /**
     * Retrieves the lock depth.
     *
     * @return An <code>int</code> indicating the lock depth.  Should be
     * either <code>SmbDAVUtilities.RESOURCE_ONLY_DEPTH</code> or
     * <code>SmbDAVUtilities.INFINITE_DEPTH</code>.
     */ 
    public int getDepth() {
        return depth;
    }

    /**
     * Sets the lock depth.
     *
     * @param depth The lock depth.  One of
     * <code>SmbDAVUtilities.RESOURCE_ONLY_DEPTH</code> or
     * <code>SmbDAVUtilities.INFINITE_DEPTH</code>.
     */ 
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Returns the lock timeout value in milliseconds.
     *
     * @return A <code>long<code> containing the lock timeout value.
     * If no timeout has been specified, this will be
     * <code>SmbDAVUtilities.UNSPECIFIED_TIMEOUT</code>.  If an infinite
     * timeout has been specified, this will be
     * <code>SmbDAVUtilities.INFINITE_TIMEOUT</code>.
     */ 
    public long getTimeout() {
        return timeout;
    }

    /**
     * Sets the lock timeout value in milliseconds.
     *
     * @param timeout The timeout value.  This should be a value in
     * milliseconds, or one of
     * <code>SmbDAVUtilities.UNSPECIFIED_TIMEOUT</code> (if no timeout
     * is specified) or
     * <code>SmbDAVUtilities.INFINITE_TIMEOUT</code> (if an infinite
     * timeout is specified).
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public String toString() {
        StringBuffer buffer = new StringBuffer("scope: ");
        buffer.append(isExclusive() ? "exclusive; " : "shared; ");
        buffer.append("depth: ");
        buffer.append((getDepth() == DavisUtilities.INFINITE_DEPTH) ?
                "infinity; " : "0; ");
        buffer.append("timeout: ");
        buffer.append(DavisUtilities.formatTimeout(getTimeout()));
        return buffer.toString();
    }

}
