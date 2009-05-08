package webdavis;

import java.io.IOException;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.sdsc.grid.io.RemoteFile;
import edu.sdsc.grid.io.RemoteFileSystem;
import edu.sdsc.grid.io.irods.IRODSFile;
import edu.sdsc.grid.io.irods.IRODSFileSystem;
import edu.sdsc.grid.io.srb.SRBFile;
import edu.sdsc.grid.io.srb.SRBFileSystem;

/**
 * Default implementation of a handler for requests using the HTTP DELETE
 * method.
 *
 * @author Shunde Zhang
 * @author Eric Glass
 */
public class DefaultDeleteHandler extends AbstractHandler {

    /**
     * Services requests which use the HTTP DELETE method.
     * This implementation deletes the specified file
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
        int result = checkLockOwnership(request, file);
        if (result != HttpServletResponse.SC_OK) {
            response.sendError(result);
            return;
        }
        result = checkConditionalRequest(request, file);
        if (result != HttpServletResponse.SC_OK) {
            response.sendError(result);
            return;
        }
        LockManager lockManager = getLockManager();
        if (lockManager != null) {
            file = lockManager.getLockedResource(file, davisSession);
        }
        del(file);
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        response.flushBuffer();
    }
    
    public void del(RemoteFile file){
    	del(file, false);
    }
    	
    public boolean del(RemoteFile file, boolean abortOnFail) {
    	if (file.isDirectory()){
    		Log.log(Log.DEBUG, "(del)entering dir "+file.getAbsolutePath());
    		String[] fileList=file.list();
    		Log.log(Log.DEBUG, "(del)entering dir has children number: "+fileList.length);
    		if (fileList.length>0){
        		for (int i=0;i<fileList.length;i++){
        			Log.log(Log.DEBUG, "(del)entering child "+fileList[i]);
    				if (file.getFileSystem() instanceof SRBFileSystem){
    					del(new SRBFile( (SRBFile)file,fileList[i]));
    				}else if (file.getFileSystem() instanceof IRODSFileSystem){
    					del(new IRODSFile( (IRODSFile)file,fileList[i]));
    				}
        		}
    		}
    		if (!file.delete() && abortOnFail)
    			return false;
    	}else{
    		Log.log(Log.DEBUG, "deleting file "+file.getAbsolutePath());
			if (file.getFileSystem() instanceof SRBFileSystem){
				((SRBFile)file).delete(true);
			}else if (file.getFileSystem() instanceof IRODSFileSystem){
				((IRODSFile)file).delete();
			}
    	}
    	return true;
    }
}
