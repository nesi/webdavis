package webdavis;

import java.io.IOException;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.irods.jargon.core.pub.io.IRODSFile;

/**
 * Default implementation of a handler for requests using the WebDAV
 * MKCOL method.
 *
 * @author Shunde Zhang
 * @author Eric Glass
 */
public class DefaultMkcolHandler extends AbstractHandler {

    /**
     * Services requests which use the WebDAV MKCOL method.
     * This implementation creates a directory at the specified location.
     * <br>
     * If the specified directory already exists, a 405 (Method Not Allowed)
     * error is sent to the client.
     * <br>
     * If the directory could not be created (the parent is not a share or
     * directory, or does not exist) a 409 (Conflict) error is sent to the
     * client.
     * If directory creation fails, a 403 (Forbidden) error is sent to the
     * client.
     *
     * @param request The request being serviced.
     * @param response The servlet response.
     * @param auth The user's authentication information.
     * @throws ServletException If an application error occurs.
     * @throws IOException If an IO error occurs while handling the request.
     */
    public void service(HttpServletRequest request,
            HttpServletResponse response, DavisSession davisSession)
                throws ServletException, IOException {
    	IRODSFile file = getIRODSFile(request, davisSession);
        response.setContentType("text/html; charset=\"utf-8\"");
        if (file.exists()) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        int result = checkLockOwnership(request, file);
        if (result != HttpServletResponse.SC_OK) {
            response.sendError(result);
            return;
        }
        result = checkConditionalRequest(request, davisSession, file);
        if (result != HttpServletResponse.SC_OK) {
            response.sendError(result);
            return;
        }
//        try {
            if (file.mkdir())
                response.setStatus(HttpServletResponse.SC_CREATED);
            else
                response.setStatus(HttpServletResponse.SC_CONFLICT);
//        } catch (SmbAuthException ex) {
//            throw ex;
//        } catch (IOException ex) {
//            response.sendError(HttpServletResponse.SC_CONFLICT,
//                    ex.getMessage());
//        }
        response.flushBuffer();
    }

}
