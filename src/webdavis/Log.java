package webdavis;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.text.DateFormat;
import java.text.MessageFormat;

import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Provides logging utility functionality.  A provider can extend this
 * class and is specified by the "smbdav.Log" system property, or via
 * a Jar service provider ("/META-INF/services/smbdav.Log").
 *
 * @author Eric Glass
 */
public abstract class Log {

    /**
     * Logging threshold indicating everything should be logged.
     */ 
    public static final int DEBUG = 0;

    /**
     * Logging threshold indicating useful information should be logged.
     */ 
    public static final int INFORMATION = 1;

    /**
     * Logging threshold indicating warnings should be logged.
     */ 
    public static final int WARNING = 2;

    /**
     * Logging threshold indicating errors should be logged.
     */ 
    public static final int ERROR = 3;

    /**
     * Logging threshold indicating critical errors should be logged.
     */ 
    public static final int CRITICAL = 4;

    /**
     * Logging threshold indicating nothing should be logged.
     */ 
    public static final int NOTHING = Integer.MAX_VALUE;

    private static final String RESOURCE = "/META-INF/services/" +
            Log.class.getName();

    private static Log instance;

    private static boolean logFailureDetected = false;

    private int logThreshold = CRITICAL;

    /**
     * Constructed provided for subclasses.
     */
    protected Log() { }

    /**
     * Returns the current logging threshold.
     *
     * @return an <code>int</code> containing the current threshold value.
     */
    public static int getThreshold() {
        return getInstance().getLogThreshold();
    }

    /**
     * Sets the current logging threshold.
     *
     * @param threshold The new logging threshold value.
     */
    public static void setThreshold(int threshold) {
        getInstance().setLogThreshold(threshold);
    }

    /**
     * Logs an object for the specified level.
     *
     *
     * @param level The logging level. 
     * @param arg The argument object.  <code>String</code>s will be logged
     * directly; <code>Throwable</code>s will log a stack trace.
     */ 
    public static void log(int level, Object arg) {
        getInstance().doLog(level, arg);
    }

    /**
     * Logs a message with an argument object for the specified level.
     *
     * @param level The logging level.
     * @param message The message.  This can contain format arguments,
     * i.e. "{0}".
     * @param arg The argument object.  A <code>String</code> will be
     * interpreted as a single format value.  A <code>Throwable</code>
     * will use the associated stack trace as the format value.
     * A <code>Object[]</code> will be interpreted as a set of format values
     * (i.e., "{0}", "{1}", etc.).
     */ 
    public static void log(int level, String message, Object arg) {
        getInstance().doLog(level, message, arg);
    }

    private static Log getInstance() {
        if (instance != null) return instance;
        synchronized (Log.class) {
            Log log = null;
            String instanceClass = null;
            try {
                instanceClass = System.getProperty(Log.class.getName());
                if (instanceClass == null) {
                    InputStream resource = Log.class.getResourceAsStream(
                            RESOURCE);
                    if (resource == null) {
                        resource = ClassLoader.getSystemResourceAsStream(
                                RESOURCE);
                    }
                    if (resource != null) {
                        Properties properties = new Properties();
                        properties.load(resource);
                        Enumeration propertyNames = properties.propertyNames();
                        if (propertyNames.hasMoreElements()) {
                            instanceClass = (String)
                                    propertyNames.nextElement();
                        }
                    }
                }
            } catch (Exception ex) { }
            if (instanceClass != null) {
                try {
                    log = (Log) Class.forName(instanceClass).newInstance();
                } catch (Exception ex) {
                    log = new DefaultLog();
                    log.doLog(NOTHING, DavisUtilities.getResource(Log.class,
                            "invalidLogClass", new Object[] { instanceClass },
                                    null));
                }
            }
            if (log == null) log = new DefaultLog();
            try {
                String logThreshold = System.getProperty(Log.class.getName() +
                        ".threshold");
                if (logThreshold != null) {
                    try {
                        logThreshold = logThreshold.toUpperCase();
                        Integer value = (Integer)
                                Log.class.getField(logThreshold).get(null);
                        log.setLogThreshold(value.intValue());
                    } catch (Exception badConstant) {
                        try {
                            log.setLogThreshold(Integer.parseInt(logThreshold));
                        } catch (Exception badNumber) {
                            log.setLogThreshold(DEBUG);
                            log.doLog(DEBUG, DavisUtilities.getResource(
                                    Log.class, "invalidThreshold",
                                            new Object[] { logThreshold },
                                                    null));
                        }
                    }
                }
            } catch (Exception ex) { }
            return (instance = log);
        }
    }

    /**
     * Returns the current logging threshold.
     *
     * @return an <code>int</code> containing the current threshold value.
     */
    protected int getLogThreshold() {
        return logThreshold;
    }

    private void setLogThreshold(int logThreshold) {
        this.logThreshold = logThreshold;
    }

    private void doLog(int level, Object arg) {
        if (level < getLogThreshold() || arg == null) return;
        if (!(arg instanceof Throwable)) {
            doLog(level, String.valueOf(arg), (Object[]) null);
            return;
        }
        Throwable throwable = (Throwable) arg;
        String message = throwable.getMessage();
        message = (message != null) ? message + ": {0}" : "{0}";
        StringWriter sw = new StringWriter();
        PrintWriter writer = new PrintWriter(sw);
        throwable.printStackTrace(writer);
        writer.flush();
        doLog(level, message, sw);
    }

    private void doLog(int level, String message, Object arg) {
        if (level < getLogThreshold()) return;
        if (arg instanceof Object[]) {
            doLog(level, message, (Object[]) arg);
            return;
        }
        if (!(arg instanceof Throwable)) {
            doLog(level, message, new Object[] { arg });
            return;
        }
        Throwable throwable = (Throwable) arg;
        if (message == null) {
            if (throwable == null) return;
            message = throwable.getMessage();
            message = (message != null) ? message + ": {0}" : "{0}";
        }
        if (throwable != null) {
            StringWriter sw = new StringWriter();
            PrintWriter writer = new PrintWriter(sw);
            throwable.printStackTrace(writer);
            writer.flush();
            doLog(level, message, new Object[] { sw });
        } else {
            doLog(level, message, (Object[]) null);
        }
    }

    private void doLog(int level, String message, Object[] args) {
        if (level < getLogThreshold() || message == null) return;
        if (args != null) {
            try {
                message = MessageFormat.format(message, args);
            } catch (Exception ex) { }
        }
        try {
            synchronized (this) {
                logMessage(level, message);
            }
        } catch (Throwable logFailure) {
            synchronized (Log.class) {
                if (logFailureDetected) {
                    throw new IllegalStateException(DavisUtilities.getResource(
                            Log.class, "unrecoverableLogFailure",
                                    new Object[] { logFailure }, null));
                }
                logFailureDetected = true;
                Log log = new DefaultLog();
                log.setLogThreshold(DEBUG);
                log.doLog(DEBUG, DavisUtilities.getResource(
                        Log.class, "logFailure", new Object[] { logFailure },
                                null));
                instance = log;
            }
        }
    }

    /**
     * Logs the specified message at the provided level.
     *
     * @param level The logging level.
     * @param message The message that is to be logged.
     */
    protected abstract void logMessage(int level, String message);

    private static class DefaultLog extends Log {

        protected void logMessage(int level, String message) {
            StringBuffer output = new StringBuffer();
            switch (level) {
            case DEBUG:
                output.append("DEBUG [");
                break;
            case INFORMATION:
                output.append("INFORMATION [");
                break;
            case WARNING:
                output.append("WARNING [");
                break;
            case ERROR:
                output.append("ERROR [");
                break;
            case CRITICAL:
                output.append("CRITICAL [");
                break;
            default:
                output.append("UNKNOWN [");
            }
            DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT,
                    DateFormat.SHORT);
            output.append(format.format(new Date())).append("]: ");
            output.append(message);
            System.out.println(output);
        }

    }

}
