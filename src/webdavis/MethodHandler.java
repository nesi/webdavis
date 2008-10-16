package webdavis;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.sdsc.grid.io.RemoteFileSystem;

/**
 * A class can implement the <code>MethodHandler</code> interface when it
 * wishes to service requests via a particular HTTP method.  The
 * Davenport servlet registers a method handler for a particular method,
 * and dispatches requests to that handler.
 * To install a handler,
 * <ul>
 * <li>Create a class implementing the <code>MethodHandler</code>
 * interface.  The implementing class must also provide a no-arg
 * constructor.</li>
 * <li>Add a servlet initialization parameter of the form
 * <i>handler.<method></i> to the Davenport deployment descriptor.  The
 * value should be the name of the class implementing
 * <code>MethodHandler</code>.</li>
 * </ul>
 * As an example, if you have a class <code>com.foo.MyGetHandler</code>
 * designed to service HTTP GET requests, you would add the following to the
 * Davenport deployment descriptor:
 * <p>
 * <pre>
 * &lt;init-param&gt;
 *     &lt;param-name&gt;handler.GET&lt;/param-name&gt;
 *     &lt;param-value&gt;com.foo.MyGetHandler&lt;/param-value&gt;
 * &lt;/init-param&gt;
 * </pre>
 *
 * @author Eric Glass
 */
public interface MethodHandler {

    /**
     * Interim status code (102) indicating that the server has accepted the
     * request but has not yet completed it.
     */ 
    public static final int SC_PROCESSING = 102;

    /**
     * Status code (207) providing status for multiple independent operations.
     */ 
    public static final int SC_MULTISTATUS = 207;

    /**
     * Status code (422) indicating that the server understands the content
     * type of the request entity, and the syntax of the request entity is
     * correct, but the contained instructions could not be processed.
     */ 
    public static final int SC_UNPROCESSABLE_ENTITY = 422;

    /**
     * Status code (423) indicating that the source or destination resource
     * of a method is locked.
     */ 
    public static final int SC_LOCKED = 423;

    /**
     * Status code (424) indicating that the method could not be performed
     * because an action upon which this action depends failed.
     */ 
    public static final int SC_FAILED_DEPENDENCY = 424;

    /**
     * Status code (507) indicating that the method could not be performed
     * on a resource because the server is unable to store the representation
     * needed to successfully complete the request.
     */ 
    public static final int SC_INSUFFICIENT_STORAGE = 507;

    /**
     * Called by the Davenport servlet to indicate that the handler is being
     * placed into service.  Semantics are identical to the <code>Servlet</code>
     * <code>init</code> method; the method is called exactly once after
     * instantiation.
     * 
     * @param config a <code>ServletConfig</code> object containing
     * the Davenport servlet's configuration and initialization parameters.
     * @throws ServletException If an error occurs during initialization.
     */
    public void init(ServletConfig config) throws ServletException;

    /**
     * Called by the Davenport servlet to indicate that the handler is being
     * taken out of service.  Semantics are identical to the
     * <code>Servlet</code> <code>destroy</code> method.  This method
     * gives the handler an opportunity to clean up any resources that
     * are being held.  After this method has been called, the
     * <code>service</code> method will not be invoked again.
     */
    public void destroy();

    /**
     * Called by the Davenport servlet to allow the handler to service a
     * request.  The Davenport servlet will select an appropriate handler
     * for a given HTTP method, and dispatch the request accordingly.
     * 
     * @param request The request that is being serviced.
     * @param response The servlet response object.
     * @param auth The authentication information provided by the user.
     * @throws IOException If an input or output exception occurs.
     * @throws ServletException If an exception occurs that interferes with
     * normal operation.
     */
    public void service(HttpServletRequest request,
            HttpServletResponse response, RemoteFileSystem rfs)
                    throws IOException, ServletException;

}
