package webdavis;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

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

	boolean inTrash;

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
    public void service(HttpServletRequest request, HttpServletResponse response, DavisSession davisSession)
    	throws ServletException, IOException {
    	   	
        response.setContentType("text/html; charset=\"utf-8\"");
    	ArrayList<RemoteFile> fileList = new ArrayList<RemoteFile>();
    	boolean batch = getFileList(request, davisSession, fileList, getJSONContent(request));
    	Log.log(Log.DEBUG, "deleting "+(batch?"batch files":"file")+": "+fileList);
    	
//    	boolean batch = false;  	
//    	RemoteFile uriFile = getRemoteFile(request, davisSession);
//        if (request.getContentLength() <= 0) {
//        	fileList.add(uriFile);
//        } else {
//        	batch = true;
//	        InputStream input = request.getInputStream();
//	        byte[] buf = new byte[request.getContentLength()];
//	        int count=input.read(buf);
//	        Log.log(Log.DEBUG, "read:"+count);
//	        Log.log(Log.DEBUG, "received file list: " + new String(buf));
//
//			JSONArray jsonArray=(JSONArray)JSONValue.parse(new String(buf));
//			if (jsonArray != null) {	
//				JSONObject jsonObject = (JSONObject)jsonArray.get(0);
//				JSONArray fileNamesArray = (JSONArray)jsonObject.get("files");
//				for (int i = 0; i < fileNamesArray.size(); i++) {
//					String name = (String)fileNamesArray.get(i);
//					if (name.trim().length() == 0)
//						continue;	// If for any reason name is "", we MUST skip it because that's equivalent to home!   	 
//					fileList.add(getRemoteFile(uriFile.getAbsolutePath()+uriFile.getPathSeparator()+name, davisSession));
//				}
//			} else
//				throw new ServletException("Internal error deleting file: error parsing JSON");
//		}
		
        Iterator<RemoteFile> iterator = fileList.iterator();
        while (iterator.hasNext()) {
        	RemoteFile condemnedFile = iterator.next();
			Log.log(Log.DEBUG, "deleting: "+condemnedFile);
	    	int result = deleteFile(request, davisSession, condemnedFile, batch);
			if (result != HttpServletResponse.SC_NO_CONTENT) {
    			String s = "Failed to delete '"+condemnedFile.getAbsolutePath()+"'";
				if (batch) {
	    			Log.log(Log.WARNING, s+" in batch mode");
	    			response.sendError(HttpServletResponse.SC_FORBIDDEN, s); // Batch delete failed
	    		} else {
	    			Log.log(Log.WARNING, s);
		    		response.sendError(result, s);
	    		}
				return;
			}
        }
		response.setStatus(HttpServletResponse.SC_NO_CONTENT);
		response.flushBuffer();
   }		 
    	
    private int deleteFile(HttpServletRequest request, DavisSession davisSession, RemoteFile file, boolean batch) throws IOException {
    	
 //   	RemoteFile file = getRemoteFile(request, davisSession);
        if (!file.exists()) {
            /*response.sendError(*/ return HttpServletResponse.SC_NOT_FOUND/*)*/;
            //return;
        }
        if (!batch) {
        	int result = checkLockOwnership(request, file);
        	if (result != HttpServletResponse.SC_OK) 
            /*response.sendError(*/ return result/*)*/;
            //return;
        	result = checkConditionalRequest(request, file);
        	if (result != HttpServletResponse.SC_OK) 
            /*response.sendError(*/ return result/*)*/;
            //return;
        	LockManager lockManager = getLockManager();
        	if (lockManager != null) 
        		file = lockManager.getLockedResource(file, davisSession);
        }
        if (file.getFileSystem() instanceof IRODSFileSystem)
        	inTrash=file.getAbsolutePath().startsWith("/"+davisSession.getZone()+"/trash");
        boolean success = del(file, davisSession);
        if (batch) 
        	return success ? HttpServletResponse.SC_NO_CONTENT : HttpServletResponse.SC_NOT_MODIFIED; // If delete failed, let caller know with SC_NOT_MODIFIED
        return HttpServletResponse.SC_NO_CONTENT;
       // response.flushBuffer();
    }
    
//    private boolean error = false;
    
    public boolean del(RemoteFile file, DavisSession davisSession) {
    	boolean result=true;
		if (file.getFileSystem() instanceof SRBFileSystem){
	    	if (file.isDirectory()){
	    		Log.log(Log.DEBUG, "(del)entering dir "+file.getAbsolutePath());
	    		String[] fileList=file.list();
	    		Log.log(Log.DEBUG, "(del)entering dir has children number: "+fileList.length);
	    		if (fileList.length>0){
	        		for (int i=0;i<fileList.length;i++){
	        			Log.log(Log.DEBUG, "(del)entering child "+fileList[i]);
	        			result = result & del(new SRBFile( (SRBFile)file,fileList[i]),davisSession);
	        		}
	    		}
	    	}
			Log.log(Log.DEBUG, "deleting "+file.getAbsolutePath());
			result = result & ((SRBFile)file).delete(true); 
		}else if (file.getFileSystem() instanceof IRODSFileSystem){
			//iRODS does support recursive deletion now
			if (inTrash/*false*/) { // But not in trash for now
		    	if (file.isDirectory()){
		    		Log.log(Log.DEBUG, "(del)entering dir "+file.getAbsolutePath());
		    		String[] fileList=file.list();
		    		Log.log(Log.DEBUG, "(del)entering dir has children number: "+fileList.length);
		    		if (fileList.length>0){
		        		for (int i=0;i<fileList.length;i++){
		        			Log.log(Log.DEBUG, "(del)entering child "+fileList[i]);
		        			result = result & del(new IRODSFile( (IRODSFile)file,fileList[i]),davisSession);
		        		}
		    		}
		    	}
				Log.log(Log.DEBUG, "deleting "+file.getAbsolutePath()+" forcefully");
				result = result & ((IRODSFile)file).delete(true); 
			} else {
				Log.log(Log.DEBUG, "deleting - force:"+inTrash);
				try {
					result = result & ((IRODSFile)file).delete(inTrash);
				} catch (NullPointerException e) { // Catch jargon bugs
					Log.log(Log.WARNING,"Jargon threw a NullPointerException during delete: "+e);
					result = false;
				}
			}
		}
		if (!result) Log.log(Log.WARNING,"Failed to delete file: "+file);
    	return result;    	
    }
}