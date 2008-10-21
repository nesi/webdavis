package webdavis;

import java.io.IOException;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.sdsc.grid.io.RemoteFile;
import edu.sdsc.grid.io.RemoteFileSystem;

/**
 * Default implementation of a handler for requests using the WebDAV
 * MKCOL method.
 *
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
        RemoteFile file = getRemoteFile(request, davisSession);
        if (file.exists()) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
//        int result = checkLockOwnership(request, file);
//        if (result != HttpServletResponse.SC_OK) {
//            response.sendError(result);
//            return;
//        }
        int result = checkConditionalRequest(request, file);
        if (result != HttpServletResponse.SC_OK) {
            response.sendError(result);
            return;
        }
//        try {
            file.mkdir();
            response.setStatus(HttpServletResponse.SC_CREATED);
//        } catch (SmbAuthException ex) {
//            throw ex;
//        } catch (IOException ex) {
//            response.sendError(HttpServletResponse.SC_CONFLICT,
//                    ex.getMessage());
//        }
        response.flushBuffer();
    }

}
