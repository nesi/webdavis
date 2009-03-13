package webdavis;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.sdsc.grid.io.RemoteFile;
import edu.sdsc.grid.io.RemoteFileOutputStream;
import edu.sdsc.grid.io.RemoteFileSystem;
import edu.sdsc.grid.io.irods.IRODSFile;
import edu.sdsc.grid.io.irods.IRODSFileOutputStream;
import edu.sdsc.grid.io.irods.IRODSFileSystem;
import edu.sdsc.grid.io.srb.SRBFile;
import edu.sdsc.grid.io.srb.SRBFileOutputStream;
import edu.sdsc.grid.io.srb.SRBFileSystem;

/**
 * Default implementation of a handler for requests using the HTTP PUT
 * method.
 *
 * @author Shunde Zhang
 * @author Eric Glass
 */
public class DefaultPutHandler extends AbstractHandler {

    /**
     * Services requests which use the HTTP PUT method.
     * This implementation uploads the content to the specified location.
     * <br>
     * If the content length is not specified, a 411 (Length Required) error
     * is sent to the client.
     * <br>
     * If the resource exists and is a collection, a 405 (Method Not Allowed)
     * error is sent to the client.
     * <br>
     * If the parent collection does not exist, a 409 (Conflict) error is
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
        int length = request.getContentLength();
        Log.log(Log.DEBUG, "request.getContentLength(): "+length);
        if (length==-1){
        	length=Integer.parseInt(request.getHeader("x-expected-entity-length"));
            Log.log(Log.DEBUG, "request.getHeader(\"x-expected-entity-length\"): "+length);
        }
        if (length < 0) {
            response.sendError(HttpServletResponse.SC_LENGTH_REQUIRED);
            return;
        }
        RemoteFile file = getRemoteFile(request, davisSession);
        boolean existsCurrently = file.exists();
        if (existsCurrently && !file.isFile()) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    DavisUtilities.getResource(DefaultPutHandler.class,
                            "collectionTarget", null, request.getLocale()));
            return;
        }
        RemoteFile parent = getRemoteParentFile(request, davisSession);
        	//createRemoteFile(file.getParent(), rfs);
        if (!(parent.exists() && parent.isDirectory())) {
            response.sendError(HttpServletResponse.SC_CONFLICT);
            return;
        }
        int result = checkLockOwnership(request, file);
        if (result != HttpServletResponse.SC_OK) {
            response.sendError(result);
            return;
        }
        result = checkConditionalRequest(request, file);
        if (result != HttpServletResponse.SC_OK) {
            response.setStatus(result);
            response.flushBuffer();
            return;
        }
        LockManager lockManager = getLockManager();
        if (lockManager != null) {
            file = lockManager.getLockedResource(file, davisSession);
        }
		if (davisSession.getCurrentResource()==null) davisSession.setCurrentResource(davisSession.getDefaultResource());
        InputStream input = request.getInputStream();
        RemoteFileOutputStream output = null;
    	Log.log(Log.DEBUG, "davisSession.getCurrentResource():"+davisSession.getCurrentResource());
        if (file.getFileSystem() instanceof SRBFileSystem) {
        	((SRBFile)file).setResource(davisSession.getCurrentResource());
        	output = new SRBFileOutputStream((SRBFile)file);
        }else if (file.getFileSystem() instanceof IRODSFileSystem) {
//        	if (davisSession.getCurrentResource()!=null) ((IRODSFile)file).setResource(davisSession.getCurrentResource());
        	Log.log(Log.DEBUG, "putting file into res:"+((IRODSFile)file).getResource());
        	output = new IRODSFileOutputStream((IRODSFile)file);
        }
        byte[] buf = new byte[8192];
        int count;
        Log.log(Log.DEBUG, "PUT method: "+output);
        while ((count = input.read(buf)) != -1) {
//        	Log.log(Log.DEBUG, "PUT method writing "+count+" bytes.");
            output.write(buf, 0, count);
        }
        output.flush();
        output.close();
        response.setStatus(HttpServletResponse.SC_CREATED);
        response.setHeader("Location", getRequestURL(request));
        response.setHeader("Allow", "OPTIONS, HEAD, GET, DELETE, PROPFIND, " +
                "PROPPATCH, COPY, MOVE, PUT");
        response.flushBuffer();
    }

}
