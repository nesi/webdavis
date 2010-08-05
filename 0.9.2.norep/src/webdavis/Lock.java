package webdavis;

import java.security.Principal;

/**
 * Represents an active lock.
 *
 * @author Eric Glass
 */
public abstract class Lock extends LockInfo {

    /**
     * Returns the token associated with this lock.
     * 
     * @return A <code>String</code> containing the associated lock
     * token URI (typically an "opaquelocktoken" URI).
     */
    public abstract String getToken();

    /**
     * Returns the principal owning this lock.
     *
     * @return A <code>Principal</code> representing the lock owner.
     */ 
    public abstract DavisSession getDavisSession();

    public String toString() {
        StringBuffer buffer = new StringBuffer("token: ");
        buffer.append(getToken()).append("; ");
        buffer.append("DavisSession: ");
        buffer.append(getDavisSession()).append("; ");
        buffer.append(super.toString());
        return buffer.toString();
    }

}
