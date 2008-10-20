package webdavis;

import java.io.IOException;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.sdsc.grid.io.RemoteFile;
import edu.sdsc.grid.io.RemoteFileSystem;

/**
 * Default implementation of a handler for requests using the HTTP OPTIONS
 * method.
 *
 * @author Shunde Zhang
 */
public class DefaultOptionsHandler extends AbstractHandler {

    /**
     * Services requests which use the HTTP OPTIONS method.
     * This implementation provides the list of supported methods for
     * the target resource.
     *
     * @param request The request being serviced.
     * @param response The servlet response.
     * @param auth The user's authentication information.
     * @throws ServletException If an application error occurs.
     * @throws IOException If an IO error occurs while handling the request.
     */
    public void service(HttpServletRequest request,
            HttpServletResponse response, RemoteFileSystem rfs)
                    throws IOException, ServletException {
        boolean lockSupport = false; //no lock at the moment  //(getLockManager() != null);
        response.setHeader("DAV", lockSupport ? "1,2" : "1");
        response.setHeader("MS-Author-Via", "DAV");
        RemoteFile file = getRemoteFile(request, rfs);
        StringBuffer allow = new StringBuffer();
        if (file.exists()) {
            allow.append("OPTIONS, HEAD, GET, DELETE, PROPFIND");
            allow.append(", PROPPATCH, COPY, MOVE, POST");
            if (file.isDirectory()) allow.append(", PUT");
        } else {
            allow.append("OPTIONS, MKCOL, PUT, POST");
        }
        if (lockSupport) allow.append(", LOCK, UNLOCK");
        Log.log(Log.DEBUG, "Allow methods for {0}: {1}",new Object[]{file.getAbsolutePath(),allow.toString()});
        response.setHeader("Allow", allow.toString());
    }

}
