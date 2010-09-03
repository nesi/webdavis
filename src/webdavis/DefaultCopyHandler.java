package webdavis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.sdsc.grid.io.FileFactory;
import edu.sdsc.grid.io.GeneralFile;
import edu.sdsc.grid.io.RemoteFile;
import edu.sdsc.grid.io.RemoteFileSystem;
import edu.sdsc.grid.io.irods.IRODSFile;
import edu.sdsc.grid.io.irods.IRODSFileSystem;
import edu.sdsc.grid.io.srb.SRBFile;
import edu.sdsc.grid.io.srb.SRBFileSystem;


/**
 * Default implementation of a handler for requests using the WebDAV
 * COPY method.
 *
 * @author Shunde Zhang
 * @author Eric Glass
 */
public class DefaultCopyHandler extends AbstractHandler {

	/**
     * Services requests which use the WebDAV COPY method.
     * This implementation copies the source file to the destination.
     * <br>
     * If the source file does not exist, a 404 (Not Found) error is sent
     * to the client.
     * <br>
     * If the destination is not specified, a 400 (Bad Request) error
     * is sent to the client.
     * <br>
     * If the source and destination specify the same resource, a 403
     * (Forbidden) error is sent to the client.
     * <br>
     * If the destination already exists, and the client has sent the
     * "Overwrite" request header with a value of "T", then the request
     * succeeds and the file is overwritten.  If the "Overwrite" header is
     * not provided, a 412 (Precondition Failed) error is sent to the client.
     *
     * @param request The request being serviced.
     * @param response The servlet response.
     * @param auth The user's authentication information.
     * @throws SerlvetException If an application error occurs.
     * @throws IOException If an IO error occurs while handling the request.
     */
    public void service(HttpServletRequest request, HttpServletResponse response, DavisSession davisSession) throws ServletException, IOException {
    	
    	response.setContentType("text/html; charset=\"utf-8\"");
        ArrayList<RemoteFile> fileList = new ArrayList<RemoteFile>();
    	boolean batch = getFileList(request, davisSession, fileList, getJSONContent(request));
    	String destinationField = request.getHeader("Destination");
    	if (destinationField.indexOf("://") < 0)	// If destination field is a relative path, prepend a protocol for getRemoteURL()
    		destinationField = "http://"+destinationField;
    	String destination = getRemoteURL(request, destinationField);
		if (destination == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        RemoteFile destinationFile = getRemoteFile(destination, davisSession);
        Iterator<RemoteFile> iterator = fileList.iterator();
        int result = 0;
        while (iterator.hasNext()) {
        	RemoteFile sourceFile = iterator.next();
			Log.log(Log.DEBUG, "copying: "+sourceFile+" to "+destinationFile);
            if (destinationFile.getAbsolutePath().equals(sourceFile.getAbsolutePath())) {
//            	if (batch)
//            		response.sendError(HttpServletResponse.SC_NO_CONTENT);
            	response.sendError(HttpServletResponse.SC_FORBIDDEN, DavisUtilities.getResource(DefaultCopyHandler.class, "sameResource", null, request.getLocale()));
            	return;
        	}
			result = copyFile(request, davisSession, sourceFile, destinationFile, batch);
			if (result != HttpServletResponse.SC_NO_CONTENT && result != HttpServletResponse.SC_CREATED) {
				if (batch) {
	    			String s = "Failed to copy '"+sourceFile.getAbsolutePath()+"'";
	    			Log.log(Log.WARNING, s);
	    			response.sendError(result, s); // Batch move failed
	    		} else
		    		response.sendError(result);
				return;
			}
        }
		response.setStatus(result);
		response.flushBuffer();
    }
    
    private int copyFile(HttpServletRequest request, DavisSession davisSession, RemoteFile file, RemoteFile destinationFile, boolean batch) throws IOException {

        if (!file.exists()) 
            return HttpServletResponse.SC_NOT_FOUND;
        
        if (batch) {
        	destinationFile.mkdirs(); // Make sure destination directory exists
            destinationFile = getRemoteFile(destinationFile.getAbsolutePath()+destinationFile.getPathSeparator()+file.getName(), davisSession);
        } else {
            int result = checkLockOwnership(request, destinationFile);
        	if (result != HttpServletResponse.SC_OK) 
        		return result;
        	result = checkConditionalRequest(request, destinationFile);
        	if (result != HttpServletResponse.SC_OK) 
        		return result;
        	LockManager lockManager = getLockManager();
        	if (lockManager != null) 
        		destinationFile = lockManager.getLockedResource(destinationFile, davisSession);
        }
        Log.log(Log.DEBUG, "file:"+file.getAbsolutePath()+" destinationFile:"+destinationFile.getAbsolutePath());

        boolean overwritten = false;
        if (destinationFile.exists()) {
        	if ("T".equalsIgnoreCase(request.getHeader("Overwrite"))) {
        		destinationFile.delete();
                overwritten = true;
            } else 
                return HttpServletResponse.SC_PRECONDITION_FAILED;
        }
        if (file.getFileSystem() instanceof SRBFileSystem) {
        	((SRBFile)file).setResource(davisSession.getDefaultResource());
        	((SRBFile)destinationFile).setResource(davisSession.getDefaultResource());
        } else 
        if (file.getFileSystem() instanceof IRODSFileSystem) {
           	((IRODSFile)file).setResource(davisSession.getDefaultResource());
        	((IRODSFile)destinationFile).setResource(davisSession.getDefaultResource());
        }
        
        /*if (!*/copyTo(file, destinationFile, davisSession)/*)*/; 
//        	// Jargon sometimes returns false when the rename seems to have worked, so check
//        	if (!destinationFile.exists()) 
//        		return HttpServletResponse.SC_FORBIDDEN;
      
        return overwritten ? HttpServletResponse.SC_NO_CONTENT : HttpServletResponse.SC_CREATED;
    }
   	
	private void copyTo(RemoteFile sourceFile, RemoteFile destinationFile, DavisSession davisSession) throws IOException {
		if (sourceFile.isFile()){
//	        if (destinationFile.getFileSystem() instanceof SRBFileSystem) 
//	        	((SRBFile)destinationFile).setResource(davisSession.getDefaultResource());
//	        else
//	        if (destinationFile.getFileSystem() instanceof IRODSFileSystem) 
//	        	((IRODSFile)destinationFile).setResource(davisSession.getDefaultResource());
			sourceFile.copyTo(destinationFile);
		} else if (sourceFile.isDirectory()) {
			  //recursive copy
			String fileList[] = sourceFile.list();
			
			destinationFile.mkdir();
			if (fileList != null) 
				for (int i=0; i < fileList.length; i++) 
					if (sourceFile.getFileSystem() instanceof SRBFileSystem)
						copyTo(new SRBFile((SRBFile)sourceFile,fileList[i]), new SRBFile((SRBFile)destinationFile, fileList[i]), davisSession);
					else 
					if (sourceFile.getFileSystem() instanceof IRODSFileSystem)
						copyTo(new IRODSFile((IRODSFile)sourceFile,fileList[i]), new IRODSFile((IRODSFile)destinationFile, fileList[i]), davisSession);
		}	
	}
}
