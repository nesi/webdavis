package webdavis;

import java.io.IOException;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.sdsc.grid.io.RemoteFile;
import edu.sdsc.grid.io.RemoteFileSystem;

/**
 * Default implementation of a handler for requests using the HTTP HEAD method.
 *
 * @author Eric Glass
 */
public class DefaultHeadHandler extends AbstractHandler {

    /**
     *
     * Services requests which use the HTTP HEAD method.
     * This implementation returns basic information regarding the specified
     * resource.
     * <br>
     * If the specified file does not exist, a 404 (Not Found) error is
     * sent to the client.
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
        if (!file.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String requestUrl = getRequestURL(request);
        if (file.getName().endsWith("/") && !requestUrl.endsWith("/")) {
            StringBuffer redirect = new StringBuffer(requestUrl).append("/");
            String query = request.getQueryString();
            if (query != null) redirect.append("?").append(query);
            response.sendRedirect(redirect.toString());
            return;
        }
        String etag = DavisUtilities.getETag(file);
        if (etag != null) response.setHeader("ETag", etag);
        long modified = file.lastModified();
        if (modified != 0) {
            response.setHeader("Last-Modified",
                    DavisUtilities.formatGetLastModified(modified));
        }
        int result = checkConditionalRequest(request, file);
        if (result != HttpServletResponse.SC_OK) {
            response.setStatus(result);
            response.flushBuffer();
            return;
        }
        String contentType = getServletConfig().getServletContext().getMimeType(
                file.getName());
        response.setContentType((contentType != null) ? contentType :
                "application/octet-stream");
        response.setContentLength(file.isFile() ? (int) file.length() : 0);
        response.flushBuffer();
    }

}
