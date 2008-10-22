package webdavis;

import java.io.IOException;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.sdsc.grid.io.RemoteFile;
import edu.sdsc.grid.io.RemoteFileSystem;

/**
 * Default implementation of a handler for requests using the WebDAV
 * MOVE method.
 *
 * @author Eric Glass
 */
public class DefaultMoveHandler extends AbstractHandler {

    /**
     * Services requests which use the WebDAV MOVE method.
     * This implementation moves the source file to the destination location.
     * <br>
     * If the source file does not exist, a 404 (Not Found) error is sent
     * to the client.
     * <br>
     * If the destination is not specified, a 400 (Bad Request) error is
     * sent to the client.
     * <br>
     * If the destination already exists, and the client has sent the
     * "Overwrite" request header with a value of "T", then the request
     * succeeds and the file is overwritten.  If the "Overwrite" header is
     * not provided, a 412 (Precondition Failed) error is sent to the client.
     * <br>
     * If the destination was created, but the source could not be removed,
     * a 403 (Forbidden) error is sent to the client.
     *
     * @param request The request being serviced.
     * @param response The servlet response.
     * @param auth The user's authentication information.
     * @throws SerlvetException If an application error occurs.
     * @throws IOException If an IO error occurs while handling the request.
     */
    public void service(HttpServletRequest request,
            HttpServletResponse response, DavisSession davisSession)
                    throws ServletException, IOException {
        RemoteFile file = getRemoteFile(request, davisSession);
        if (!file.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String destination = getRemoteURL(request,
                request.getHeader("Destination"));
        if (destination == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        RemoteFile destinationFile = getRemoteFile(destination, davisSession);
        if (destinationFile.equals(file)) return;
        int result = checkLockOwnership(request, file);
        if (result != HttpServletResponse.SC_OK) {
            response.sendError(result);
            return;
        }
        result = checkLockOwnership(request, destinationFile);
        if (result != HttpServletResponse.SC_OK) {
            response.sendError(result);
            return;
        }
        result = checkConditionalRequest(request, file);
        if (result != HttpServletResponse.SC_OK) {
            response.sendError(result);
            return;
        }
        result = checkConditionalRequest(request, destinationFile);
        if (result != HttpServletResponse.SC_OK) {
            response.sendError(result);
            return;
        }
        LockManager lockManager = getLockManager();
        if (lockManager != null) {
            file = lockManager.getLockedResource(file, davisSession);
            destinationFile = lockManager.getLockedResource(destinationFile,
            		davisSession);
        }
        boolean overwritten = false;
        if (destinationFile.exists()) {
            if ("T".equalsIgnoreCase(request.getHeader("Overwrite"))) {
                destinationFile.delete();
                overwritten = true;
            } else {
                response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                return;
            }
        }
        file.copyTo(destinationFile,overwritten);
        try {
            file.delete();
            response.setStatus(overwritten ? HttpServletResponse.SC_NO_CONTENT :
                    HttpServletResponse.SC_CREATED);
            response.flushBuffer();
//        } catch (SmbAuthException ex) {
//            throw ex;
        } catch (IOException ex) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    DavisUtilities.getResource(DefaultMoveHandler.class,
                            "cantDeleteSource", null, request.getLocale()));
        }
    }

}
