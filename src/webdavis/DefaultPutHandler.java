package webdavis;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

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
        long length = -1;
        try {
        	length=Long.parseLong(request.getHeader("Content-Length"));
        }catch (Exception _e){}
        Log.log(Log.DEBUG, "request.getContentLength(): "+length);
        if (length==-1){
        	length=Long.parseLong(request.getHeader("x-expected-entity-length"));
            Log.log(Log.DEBUG, "request.getHeader(\"x-expected-entity-length\"): "+length);
        }
        if (length < 0) {
            response.sendError(HttpServletResponse.SC_LENGTH_REQUIRED);
            return;
        }
        if (length == 0){
        	Log.log(Log.INFORMATION, "content length = 0");
        	Log.log(Log.DEBUG, "request.getInputStream().available(): "+request.getInputStream().available());
//        	return;
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
        if (!parent.canWrite()){
    		response.sendError(HttpServletResponse.SC_FORBIDDEN, "Resource not accessible.");
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
        RemoteFileOutputStream outputStream = null;
    	Log.log(Log.DEBUG, "davisSession.getCurrentResource():"+davisSession.getCurrentResource());
    	try{
            if (file.getFileSystem() instanceof SRBFileSystem) {
            	((SRBFile)file).setResource(davisSession.getCurrentResource());
            	outputStream = new SRBFileOutputStream((SRBFile)file);
            }else if (file.getFileSystem() instanceof IRODSFileSystem) {
//            	if (davisSession.getCurrentResource()!=null) ((IRODSFile)file).setResource(davisSession.getCurrentResource());
            	Log.log(Log.DEBUG, "saving file into res:"+((IRODSFile)file).getResource());
            	outputStream = new IRODSFileOutputStream((IRODSFile)file);
            }
        	if (length>0) {
                int bufferSize = (int) (length / 100);
                //minimum buf size of 50KiloBytes
                if (bufferSize < 51200)
                    bufferSize = 51200;
                    //maximum buf size of 5MegaByte
                else if (bufferSize > 5242880)
                    bufferSize = 5242880;
                byte[] buf = new byte[bufferSize];
	            int count;
	            int interval=request.getSession().getMaxInactiveInterval();
	            long startTime=new Date().getTime();
	//            Log.log(Log.DEBUG, "PUT method: "+outputStream);
	            BufferedOutputStream output = new BufferedOutputStream(outputStream, 1024*256); //Buffersize of 256k seems to give max speed
	            long total = 0;
	            while ((count = input.read(buf)) != -1) {
                	//inactive interval - "idle" time < 1 min, increase inactive interval
                	if (request.getSession().getMaxInactiveInterval()-(new Date().getTime()-startTime)/1000<60){
                		//increase interval by 5 mins
                		request.getSession().setMaxInactiveInterval(request.getSession().getMaxInactiveInterval()+300);
                		Log.log(Log.DEBUG, "session time is extended to:"+request.getSession().getMaxInactiveInterval());
                	}
//	            	Log.log(Log.DEBUG, "PUT method writing "+count+" bytes.");
	                output.write(buf, 0, count);
	                total += count;
	            }
	            output.flush();
	            output.close();
	            Log.log(Log.DEBUG, "PUT method wrote "+total+" bytes.");
	            request.getSession().setMaxInactiveInterval(interval);
        	}
        	if (outputStream!=null) outputStream.close();
    	}catch (Exception e){
    		response.sendError(HttpServletResponse.SC_FORBIDDEN, "Resource not accessible.");
    		return;
    	}
        response.setStatus(HttpServletResponse.SC_CREATED);
        response.setHeader("Location", getRequestURL(request));
        response.setHeader("Allow", "OPTIONS, HEAD, GET, DELETE, PROPFIND, " +
                "PROPPATCH, COPY, MOVE, PUT");
        response.flushBuffer();
    }

}
