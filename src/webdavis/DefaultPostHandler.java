package webdavis;

import java.io.IOException;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.sdsc.grid.io.RemoteFileSystem;


/**
 * Default implementation of a handler for requests using the HTTP POST
 * method.
 *
 * @author Eric Glass
 */
public class DefaultPostHandler extends AbstractHandler {

    /**
     * Services requests which use the HTTP POST method.
     * This may, at some point, implement some sort of useful behavior.
     * Right now it doesn't do anything.
     *
     * @param request The request being serviced.
     * @param response The servlet response.
     * @param auth The user's authentication information.
     * @throws SerlvetException If an application error occurs.
     * @throws IOException If an IO error occurs while handling the request.
     */
    public void service(HttpServletRequest request,
            HttpServletResponse response, DavisSession davisSession)
                    throws ServletException, IOException { }

}
