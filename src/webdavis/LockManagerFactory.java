package webdavis;

import java.io.InputStream;

import java.util.Enumeration;
import java.util.Properties;

/**
 * This is the base class for lock management providers.  A subclass
 * of <code>LockManagerFactory</code> can be installed to vend
 * <code>LockManager</code> instances for Davenport.  The provider is
 * specified by the "smbdav.LockManagerFactory" system property, or
 * via a Jar service provider
 * ("/META-INF/services/smbdav.LockManagerFactory").
 *
 * @author Eric Glass 
 */
public abstract class LockManagerFactory {

    private static final String RESOURCE = "/META-INF/services/" +
            LockManagerFactory.class.getName();

    /**
     * Creates a new <code>LockManagerFactory</code> instance.
     *
     * @return A <code>LockManagerFactory</code> implementation.
     */ 
    public static LockManagerFactory newInstance() {
        String factoryClass = null;
        try {
            factoryClass = System.getProperty(
                    LockManagerFactory.class.getName());
        } catch (SecurityException ex) {
            Log.log(Log.DEBUG, "Unable to access System property \"{0}\".",
                    LockManagerFactory.class.getName());
        }
        if (factoryClass == null) {
            try {
                InputStream resource =
                        LockManagerFactory.class.getResourceAsStream(RESOURCE);
                if (resource == null) {
                    resource = ClassLoader.getSystemResourceAsStream(RESOURCE);
                }
                if (resource != null) {
                    Properties properties = new Properties();
                    properties.load(resource);
                    Enumeration propertyNames = properties.propertyNames();
                    if (propertyNames.hasMoreElements()) {
                        factoryClass = (String) propertyNames.nextElement();
                    }
                }
            } catch (Exception ex) {
                Log.log(Log.DEBUG, "Unable to load lock manager provider: {0}",
                        ex);
            }
        }
        if (factoryClass != null) {
            try {
                return (LockManagerFactory)
                        Class.forName(factoryClass).newInstance();
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException(ex.getMessage());
            }
        }
        return new DefaultLockManagerFactory();
    }

    /**
     * Configures the factory with a set of properties.  This
     * implementation does nothing; this should be overridden by
     * subclasses to configure implementation-specific features.
     *
     * @param properties The configuration properties.
     */
    public void setProperties(Properties properties) { }

    /**
     * Creates a new <code>LockManager</code> instance.
     *
     * @return A <code>LockManager</code> implementation.  The actual
     * object returned is implementation-specific.
     */
    public abstract LockManager newLockManager();

    private static class DefaultLockManagerFactory extends LockManagerFactory {

        private long defaultTimeout = DavisUtilities.INFINITE_TIMEOUT;

        private long maximumTimeout = DavisUtilities.INFINITE_TIMEOUT;

        public void setProperties(Properties properties) {
            String defaultTimeout = properties.getProperty("defaultTimeout");
            if (defaultTimeout != null) {
                this.defaultTimeout = Long.parseLong(defaultTimeout);
            }
            String maximumTimeout = properties.getProperty("maximumTimeout");
            if (maximumTimeout != null) {
                this.maximumTimeout = Long.parseLong(maximumTimeout);
            }
        }

        public LockManager newLockManager() {
            return new DefaultLockManager(defaultTimeout, maximumTimeout);
        }

    }

}
