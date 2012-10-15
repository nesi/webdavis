package webdavis;

import java.io.IOException;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.irods.jargon.core.pub.io.IRODSFile;


/**
 * Default implementation of a handler for requests using the WebDAV UNLOCK
 * method.
 *
 * @author Shunde Zhang
 * @author Eric Glass
 */
public class DefaultUnlockHandler extends AbstractHandler {

    /**
     * Services requests which use the WebDAV UNLOCK method.
     *
     * @param request The request being serviced.
     * @param response The servlet response.
     * @param auth The user's authentication information.
     * @throws ServletException If an application error occurs.
     * @throws IOException If an IO error occurs while handling the request.
     *
     */
    public void service(HttpServletRequest request,
            HttpServletResponse response, DavisSession davisSession)
                    throws ServletException, IOException {
        LockManager lockManager = getLockManager();
        if (lockManager == null) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    DavisUtilities.getResource(DefaultUnlockHandler.class,
                            "noLockManager", null, request.getLocale()));
            return;
        }
        IRODSFile file = getIRODSFile(request, davisSession);
        Log.log(Log.DEBUG, "UNLOCK Request for resource \"{0}\".", file);
        int result = checkLockOwnership(request, file);
        if (result != HttpServletResponse.SC_OK) {
            response.sendError(result);
            return;
        }
        result = checkConditionalRequest(request, davisSession, file);
        if (result != HttpServletResponse.SC_OK) {
            response.setStatus(result);
            response.flushBuffer();
            return;
        }
        String lockToken = request.getHeader("Lock-Token");
        if (lockToken == null || !((lockToken = lockToken.trim()).startsWith(
                "<") && lockToken.endsWith(">"))) {
            Log.log(Log.INFORMATION,
                    "Invalid lock token presented to UNLOCK: {0}", lockToken);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.flushBuffer();
            return;
        }
        lockToken = lockToken.substring(1, lockToken.length() - 1);
        try {
            lockManager.unlock(file, getPrincipal(request), lockToken);
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (LockException ex) {
            response.setStatus(ex.getStatus());
        }
        response.flushBuffer();
    }

}
