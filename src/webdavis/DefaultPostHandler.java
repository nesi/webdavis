package webdavis;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

//import junit.framework.TestCase;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.irods.jargon.core.exception.DataNotFoundException;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.pub.CollectionAO;
import org.irods.jargon.core.pub.DataObjectAO;
import org.irods.jargon.core.pub.domain.AvuData;
import org.irods.jargon.core.pub.domain.Collection;
import org.irods.jargon.core.pub.domain.DataObject;
import org.irods.jargon.core.pub.domain.UserFilePermission;
import org.irods.jargon.core.pub.io.IRODSFile;
import org.irods.jargon.core.pub.io.IRODSFileFactory;
import org.irods.jargon.core.pub.io.IRODSFileOutputStream;
import org.irods.jargon.core.query.AVUQueryElement;
import org.irods.jargon.core.query.AVUQueryOperatorEnum;
import org.irods.jargon.core.query.JargonQueryException;
import org.irods.jargon.core.query.MetaDataAndDomainData;
import org.irods.jargon.core.query.RodsGenQueryEnum;
import org.irods.jargon.core.query.AVUQueryElement.AVUQueryPart;
import org.irods.jargon.core.rule.IRODSRuleExecResult;
import org.irods.jargon.ticket.Ticket;
import org.irods.jargon.ticket.TicketAdminService;
import org.irods.jargon.ticket.TicketAdminServiceImpl;
import org.irods.jargon.ticket.packinstr.TicketCreateModeEnum;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Default implementation of a handler for requests using the HTTP POST method.
 * 
 * @author Shunde Zhang
 * @author Eric Glass
 */
public class DefaultPostHandler extends AbstractHandler {
	
	private static Random random = new Random();
	
	/**
	 * Services requests which use the HTTP POST method. 
	 * 
	 * @param request
	 *            The request being serviced.
	 * @param response
	 *            The servlet response.
	 * @param auth
	 *            The user's authentication information.
	 * @throws SerlvetException
	 *             If an application error occurs.
	 * @throws IOException
	 *             If an IO error occurs while handling the request.
	 */
	public void service(HttpServletRequest request, HttpServletResponse response, DavisSession davisSession)
			throws ServletException, IOException {

		String method = request.getParameter("method");
		if (method == null)
			return;
		String url = getRemoteURL(request, getRequestURL(request), getRequestURICharset());
		Log.log(Log.DEBUG, "url:" + url + " method:" + method);

		IRODSFile file = null;
		try {
			file = getIRODSFile(request, davisSession);
		} catch (NullPointerException e) {
			Log.log(Log.CRITICAL, "Caught a NullPointerException in DefaultGethandler.service. request=" + request.getRequestURI() + " session=" + davisSession
							+ "\nAborting request. Exception is:"+DavisUtilities.getStackTrace(e));
			internalError(response, "Jargon error in DefaultGethandler.service");
			return;
		}
		Log.log(Log.DEBUG, "POST Request for resource \"{0}\".", file.getAbsolutePath());

		if (file.getName().equals("noaccess")) { 
			Log.log(Log.WARNING, "File " + file.getAbsolutePath() + " does not exist or unknown server error - Jargon says 'noaccess'");
			try {
				response.sendError(HttpServletResponse.SC_NOT_FOUND, "File " + file.getAbsolutePath() + " provides no access.");
				response.flushBuffer();
			} catch (IOException e) {
				if (e.getMessage().equals("Closed"))
					Log.log(Log.WARNING, file.getAbsolutePath() + ": connection to server may have been lost.");
				throw (e);
			}
			return;
		}
		if (!file.exists()) {
			String message = "";
				message = FSUtilities.testConnection(davisSession);
			if (message != null) {
				lostConnection(response, message);
				return;
			}
			Log.log(Log.WARNING, "File " + file.getAbsolutePath() + " does not exist or unknown server error.");					
			response.sendError(HttpServletResponse.SC_NOT_FOUND, "File " + file.getAbsolutePath() + " does not exist.");
			response.flushBuffer();
			return;
		}
		String requestUrl = getRequestURL(request);
		Log.log(Log.DEBUG, "Request URL: {0}", requestUrl);
		StringBuffer json = new StringBuffer();
		
		String requestUIHandle = null;
		if (request.getParameter("uihandle") != null) {
			requestUIHandle = request.getParameter("uihandle");
			if (requestUIHandle.equals("null"))
				requestUIHandle = null;
		}
		
        response.setContentType("text/json; charset=\"utf-8\"");
        IRODSFileFactory fileFactory=davisSession.getFileFactory();
        DataObjectAO dataObjectAO=davisSession.getDataObjectAO();
        CollectionAO collectionAO=davisSession.getCollectionAO();
		
		if (method.equalsIgnoreCase("permission")) {
			String username = request.getParameter("username");
			boolean recursive = false;
			JSONArray jsonArray = getJSONContent(request);						
			
			// Write permissions for given items
			if (jsonArray != null) {	
		    	ArrayList<IRODSFile> fileList = new ArrayList<IRODSFile>();		    						
	    		getFileList(request, davisSession, fileList, jsonArray);

				String domain = null;
				String permission = null;
				if (username != null) {		// Set permissions
					domain = request.getParameter("domain");
					permission = request.getParameter("permission");
				}
				try {
					recursive = Boolean.parseBoolean(request.getParameter("recursive"));
				} catch (Exception _e) {}
				Log.log(Log.DEBUG, "recursive="+recursive);
				String sticky = request.getParameter("sticky");
				DataObject dataObject;
				for (int j = 0; j < fileList.size(); j++) {
					IRODSFile selectedFile = fileList.get(j);
					if (j == fileList.size()-1)		// Use the last file in the list for returning metadata below
						file = selectedFile;
					try {
						if (username != null) {
							Log.log(Log.DEBUG, "change permission for "+username+" to "+permission+" (recursive="+recursive+")");
						/*		if (recursive) 
									iRODSSetPermission((IRODSFile)selectedFile, permission, username);
								else*/
							if (permission.equalsIgnoreCase("r")) {
								if (file.isDirectory()) {
									collectionAO.setAccessPermissionRead(davisSession.getIRODSAccount().getZone(), file.getAbsolutePath(), username, recursive);
								}else{
									dataObjectAO.setAccessPermissionRead(davisSession.getIRODSAccount().getZone(), file.getAbsolutePath(), username);
								}									
							} else if (permission.equalsIgnoreCase("w")) {
								if (file.isDirectory()) {
									collectionAO.setAccessPermissionWrite(davisSession.getIRODSAccount().getZone(), file.getAbsolutePath(), username, recursive);
								}else{
									dataObjectAO.setAccessPermissionWrite(davisSession.getIRODSAccount().getZone(), file.getAbsolutePath(), username);
								}									
							} else if (permission.equalsIgnoreCase("all")) {
								if (file.isDirectory()) {
									collectionAO.setAccessPermissionOwn(davisSession.getIRODSAccount().getZone(), file.getAbsolutePath(), username, recursive);
								}else{
									dataObjectAO.setAccessPermissionOwn(davisSession.getIRODSAccount().getZone(), file.getAbsolutePath(), username);
								}									
							} else if (permission.equalsIgnoreCase("n")) {
								if (file.isDirectory()) {
									collectionAO.removeAccessPermissionForUser(davisSession.getIRODSAccount().getZone(), file.getAbsolutePath(), username, recursive);
								}else{
									dataObjectAO.removeAccessPermissionsForUser(davisSession.getIRODSAccount().getZone(), file.getAbsolutePath(), username);
								}									
							}
//							((IRODSFile)selectedFile).changePermissions(permission, username, selectedFile.isDirectory() && recursive);
						}
						if (sticky!=null) {
							Log.log(Log.DEBUG, "set "+selectedFile.getAbsolutePath()+" -- sticky:"+sticky);
							boolean flag=false;
							try {
								flag = Boolean.parseBoolean(sticky);
							} catch (Exception _e) {
							}
							if (flag){
								//set inherit
								collectionAO.setAccessPermissionInherit(davisSession.getIRODSAccount().getZone(), selectedFile.getAbsolutePath(), recursive);
							} else {
								// unset inherit
								collectionAO.setAccessPermissionToNotInherit(davisSession.getIRODSAccount().getZone(), selectedFile.getAbsolutePath(), recursive);
							}
						}
//					} catch (IOException e){
//			        	Log.log(Log.DEBUG, "Set permissions failed: "+e);
//			        	String s = e.getMessage();
//			        	if (s.endsWith("-818000") || s.endsWith("-816000")) 
//			        		s = "you don't have permission"; // irods error -818000 or -816000
//			        	if (s.endsWith("-827000")) 
//			        		s = "unkown user"; 
//	        			response.sendError(HttpServletResponse.SC_FORBIDDEN, s);
//	        			return;
					} catch (JargonException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						throw new IOException(e.getMessage());
					}
				}
			}

			// Fetch permissions for item
			List<UserFilePermission> permissions;
			String owner = "unknown";
			DataObject dataObject = null;
			Collection collection = null;
			try {
				if (file.isDirectory()) {
					permissions = collectionAO.listPermissionsForCollection(file.getAbsolutePath());
					collection = collectionAO.findByAbsolutePath(file.getAbsolutePath());
					owner = collection.getCollectionOwnerName();
				}else{
					permissions = dataObjectAO.listPermissionsForDataObject(file.getAbsolutePath());
					dataObject=dataObjectAO.findByAbsolutePath(file.getAbsolutePath());
					owner = dataObject.getDataOwnerName();
				}
			} catch (JargonException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				throw new IOException(e.getMessage());
			}
			json.append("{\n");
			if (file.isDirectory()){
				try {
					boolean stickyBit = collectionAO.isCollectionSetForPermissionInheritance(file.getAbsolutePath());
					json.append(escapeJSONArg("sticky")+":"+escapeJSONArg(""+stickyBit)+",\n");
				} catch (JargonException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					throw new IOException(e.getMessage());
				}
			}
			json.append(escapeJSONArg("owner")+":"+escapeJSONArg(owner)+",\n");
//				Log.log(Log.DEBUG, "irods permissions: "+permissions);
			json.append(escapeJSONArg("items")+":[");
			if (permissions != null) {
				for (int i = 0; i < permissions.size(); i++) {
					if (i > 0)
						json.append(",\n");
					else
						json.append("\n");
					// "user domain"
					Log.log(Log.DEBUG, "permission:"+permissions.get(i).getFilePermissionEnum().toString());
                    if (file.isDirectory()) {	// "user name"
						json.append("{"+escapeJSONArg("username")+":");
						String s = ""+permissions.get(i).getUserName();
						if (!davisSession.getIRODSAccount().getZone().equals(permissions.get(i).getUserZone()))	
							s += "#"+permissions.get(i).getUserZone();
						json.append(escapeJSONArg(s)+",");
    					// "directory access constraint"
    					json.append(escapeJSONArg("permission")+":"+escapeJSONArg(DavisUtilities.iPermissionToPermission(permissions.get(i).getFilePermissionEnum().toString()))+"}");
                    } else {	// "user name"
						json.append("{"+escapeJSONArg("username")+":"+escapeJSONArg(permissions.get(i).getUserName())+",");
    					// "file access constraint"
    					json.append(escapeJSONArg("permission")+":"+escapeJSONArg(DavisUtilities.iPermissionToPermission(permissions.get(i).getFilePermissionEnum().toString()))+"}");
                    }
				}
			}
			json.append("\n]}");
			
		} else if (method.equalsIgnoreCase("metadata")) {
			if (request.getContentLength() > 0) {	// write metadata if given in request
				JSONArray jsonArray = getJSONContent(request);			
				if (jsonArray != null) {	

			    	ArrayList<IRODSFile> fileList = new ArrayList<IRODSFile>();
			    	getFileList(request, davisSession, fileList, jsonArray);
				
					JSONObject jsonObject = (JSONObject)jsonArray.get(0);
					JSONArray metadataArray = (JSONArray)jsonObject.get("metadata");
					
					for (int j = 0; j < fileList.size(); j++) {
						IRODSFile selectedFile = fileList.get(j);
						Log.log(Log.DEBUG, "changing metadata for: "+selectedFile);
						if (j == fileList.size()-1)		// Use the last file in the list for returning metadata below
							file = selectedFile;

						
						try {
							List<MetaDataAndDomainData> metadatas=dataObjectAO.findMetadataValuesForDataObject(selectedFile);
							//delete all metadata, uses wildcards
							for (MetaDataAndDomainData metadata:metadatas){
								dataObjectAO.deleteAVUMetadata(file.getAbsolutePath(), new AvuData(metadata.getAvuAttribute(),metadata.getAvuValue(),metadata.getAvuUnit()));
							}
	
	    					for (int i = 0; i < metadataArray.size(); i++) {
	    						dataObjectAO.addAVUMetadata(file.getAbsolutePath(), new AvuData( (String) ((JSONObject) metadataArray.get(i)).get("name"), (String) ((JSONObject) metadataArray.get(i)).get("value"), (String) ((JSONObject) metadataArray.get(i)).get("unit")));
	    					}
//						} catch (IOException e){
//				        	Log.log(Log.DEBUG, "Set metadata failed: "+e);
//				        	String s = e.getMessage();
//				        	if (s.endsWith("-818000")) 
//				        		s = "you don't have permission"; // irods error -818000 
//		        			response.sendError(HttpServletResponse.SC_FORBIDDEN, s);
//		        			return;
						} catch (JargonException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							throw new IOException(e.getMessage());
						}
					}
				}
			}

			// Get and return metadata 
			List<MetaDataAndDomainData> metadatas = null;
			try {
				metadatas = dataObjectAO.findMetadataValuesForDataObject(file);
			} catch (JargonException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				throw new IOException(e.getMessage());
			}
			json.append("{\n"+escapeJSONArg("items")+":[");
			boolean b = false;
			if (metadatas != null) { // Nothing in the database matched the query
				for (int i = 0; i < metadatas.size(); i++) {
					if (i>0) json.append(",\n");
					json.append("{"+escapeJSONArg("name")+":");
					json.append(escapeJSONArg(metadatas.get(i).getAvuAttribute())+",");
					json.append(escapeJSONArg("value")+":"+escapeJSONArg(metadatas.get(i).getAvuValue())+",");
					json.append(escapeJSONArg("unit")+":"+escapeJSONArg(metadatas.get(i).getAvuUnit())+"}");
					b = true;
				}
			}
			json.append("\n]}");
			
		} else if (method.equalsIgnoreCase("upload")) {
			response.setContentType("text/html");
			if (!ServletFileUpload.isMultipartContent(request)) // Returns json wrapped in an HTML textarea 
	            json.append(wrapJSONInHTML(escapeJSONArg("status")+":"+escapeJSONArg("failed")+","+escapeJSONArg("message")+":"+escapeJSONArg("Invalid request (not multipart)")));
	        else {
                long contentLength = request.getContentLength();
                if (contentLength == -1)
                	try {
                		contentLength = Long.parseLong(request.getHeader("x-expected-entity-length"));
                	} catch (NumberFormatException e){}
	            if (contentLength < 0) 
	            	json.append(wrapJSONInHTML(escapeJSONArg("status")+":"+escapeJSONArg("failed")+","+escapeJSONArg("message")+":"+escapeJSONArg("Your browser can't upload files larger than 2Gb")));
	            else {
	                Tracker tracker = createTracker(contentLength);
	                ClientInstance client = davisSession.getClientInstance(requestUIHandle);
	                if (client != null)
	                	client.setTracker(tracker);
			      	
			        String encoding = request.getCharacterEncoding();
			        if (encoding == null) 
			            encoding = "UTF-8";
			        
			        ServletFileUpload uploadProcessor = new ServletFileUpload();
			        uploadProcessor.setHeaderEncoding(encoding);
	//	      		upload.setSizeMax(getSizeLimit(request));	// Set maximum file size allowed for transfer
	
			        try {
			        	boolean result = false;
			            FileItemIterator iter = uploadProcessor.getItemIterator(request);
			            if (iter.hasNext()) {
			                FileItemStream fileItemStream = iter.next();
			                if (!fileItemStream.isFormField()) {
			                	InputStream inputStream = fileItemStream.openStream();
			                	String fileName = fileItemStream.getName();
			                	char c = '/';
			                	if (fileName.startsWith(":\\", 1))	// Win32 upload
			                		c = '\\';
			                	int j = fileName.lastIndexOf(c); 
			                	if (j >= 0)
			                		fileName = fileName.substring(j+1);
		                        file = getIRODSFile(file.getAbsolutePath()+IRODSFile.PATH_SEPARATOR+fileName, davisSession);
		                        boolean existsCurrently = file.exists();
		                        if (existsCurrently /*&& !file.isFile()*/) {
		                        	Log.log(Log.WARNING, file.getAbsolutePath()+" already exists on server");
		            	            json.append(wrapJSONInHTML(escapeJSONArg("status")+":"+escapeJSONArg("failed")+","+escapeJSONArg("message")+":"+escapeJSONArg("File already exists")));
		                        } else {	                        
			                		if (davisSession.getCurrentResource() == null) 
			                			davisSession.setCurrentResource(davisSession.getDefaultResource());
			                        IRODSFileOutputStream stream = null;
			                    	Log.log(Log.DEBUG, "saving file "+file.getAbsolutePath()+" into res:"+davisSession.getCurrentResource());
			                        try {
										stream = fileFactory.instanceIRODSFileOutputStream(file);
									} catch (JargonException e1) {
										// TODO Auto-generated catch block
										e1.printStackTrace();
										throw new IOException(e1.getMessage());
									}
			                        BufferedOutputStream outputStream = new BufferedOutputStream(stream, 1024*256);  //Buffersize of 256k seems to give max speed
			                        try {
			                        	copy(tracker, inputStream, outputStream);
			                        } catch (IOException e) {
			                        	try {
					                        outputStream.flush();
					                        outputStream.close();
			                        	} catch (IOException ee) {}
			                        	throw e;
			                        }
			                        outputStream.flush();
			                        outputStream.close();
				                    if (tracker.getBytesReceived() >= 0) {
				                    	tracker.setComplete();
				                        json.append(wrapJSONInHTML(escapeJSONArg("status")+":"+escapeJSONArg("success")+","+escapeJSONArg("message")+":"+escapeJSONArg(""+tracker.getBytesReceived())));
				                        result = true;
				                    } 
				                    client = davisSession.getClientInstance(requestUIHandle);
				                    if (client != null)
				                    	client.setTracker(null);
		                        }
			                }
			            }
			            if (!result) 
			            	json.append(wrapJSONInHTML(escapeJSONArg("status")+":"+escapeJSONArg("failed")+","+escapeJSONArg("message")+":"+escapeJSONArg("No file to upload")));
			        } catch (EOFException e) {
	                    json.append(wrapJSONInHTML(escapeJSONArg("status")+":"+escapeJSONArg("failed")+","+escapeJSONArg("message")+":"+escapeJSONArg("Unexpected end of file")));
			        } catch (IOException e) {
			        	Log.log(Log.DEBUG, "Upload failed: "+e);
			        	String s = e.getMessage();
			        	if (s.equals("IRODS error occured msg")) //sic
			        		s = "you don't have permission to upload here"; // Assume it's irods error -818000
	                    json.append(wrapJSONInHTML(escapeJSONArg("status")+":"+escapeJSONArg("failed")+","+escapeJSONArg("message")+":"+escapeJSONArg(s)));
			        } catch (FileUploadException e) {
			        	Log.log(Log.DEBUG, "Upload failed: "+e);
		                json.append(wrapJSONInHTML(escapeJSONArg("status")+":"+escapeJSONArg("failed")+","+escapeJSONArg("message")+":"+escapeJSONArg(e.getMessage())));
			        }
	            }
	        }
			
		} else if (method.equalsIgnoreCase("uploadstatus")) {	
			Tracker tracker = null;
            ClientInstance client = davisSession.getClientInstance(requestUIHandle);
            if (client == null) {
    			Log.log(Log.DEBUG, "Transfer for "+requestUIHandle+" does not exist.");
    			response.sendError(HttpServletResponse.SC_NOT_FOUND);
    			return;
            }
            tracker = client.getTracker();
			if (tracker != null) {
				long transferred = -1;
				long total = -1;
				try {
					transferred = tracker.getBytesReceived();
					total = tracker.getSize();
				} catch (Exception e) {}
				if (transferred > -1 && total > -1)
					json.append("{"+escapeJSONArg("transferred")+":"+transferred+','+escapeJSONArg("total")+":"+total+"}");
			}
			json.append("\n");
			
		} else if (method.equalsIgnoreCase("dynamicobjects")) {
			json.append("{\n"+escapeJSONArg("items")+":[\n");
			Enumeration<JSONObject> dynamicObjects = Davis.getConfig().getDynamicObjects().elements();
			int i = 0;
			while (dynamicObjects.hasMoreElements()) {
				JSONObject dynamicObject = dynamicObjects.nextElement();
				if (i++ > 0) json.append(",\n");
				json.append(dynamicObject.toString()); // No escaping required - the whole dynamic object declaration is sent verbatim
			}
			json.append("\n]}");
			
		} else if (method.equalsIgnoreCase("resources")) {
			json.append("{\n"+escapeJSONArg("items")+":[\n");
			String[] resources = null;
			resources = FSUtilities.getIRODSResources(davisSession);
			for (int i = 0; i < resources.length; i++) { 
				if (i > 0) json.append(",\n");
				json.append("{"+escapeJSONArg("name")+":"+escapeJSONArg(resources[i])+"}");
			}
			json.append("\n]}");
			
		} else if (method.equalsIgnoreCase("collectionmetadata")) {
			json.append("{"+escapeJSONArg("items")+":[\n");
			HashMap<String, FileMetadata> files = null;
			files = FSUtilities.getIRODSCollectionMetadata(davisSession, file);
			if (file != null) {
				FileMetadata[] filesMetadata = files.values().toArray(new FileMetadata[0]);
				for (int i = 0; i < filesMetadata.length; i++) { 
					if (i > 0) json.append("  ,\n");
					json.append("  {"+escapeJSONArg("file")+":"+escapeJSONArg(filesMetadata[i].getName())+","+escapeJSONArg("metadata")+": [\n");
					HashMap<String, ArrayList<String>> metadata = filesMetadata[i].getMetadata();
					String[] names = metadata.keySet().toArray(new String[0]);
					for (int j = 0; j < names.length; j++) {
						if (j > 0) json.append(",\n");
						String name = names[j];
						ArrayList<String> values = metadata.get(name);
						for (int k = 0; k < values.size(); k++) {
							if (k > 0) json.append(",\n");
							json.append("    {"+escapeJSONArg("name")+":"+escapeJSONArg(name)+","+escapeJSONArg("value")+":"+escapeJSONArg(values.get(k))+"}");
						}
					}
					json.append("]}\n");
				}
			}
			json.append("]}");
			
		} else if (method.equalsIgnoreCase("execbutton")) {
			JSONArray jsonArray = getJSONContent(request);
	    	ArrayList<IRODSFile> fileList = new ArrayList<IRODSFile>();
//	    	boolean batch = true;
//	    	try {
//	    		batch = batch = getFileList(request, davisSession, fileList, jsonArray);
//	    	} catch (ServletException e) {
//	    		if (!checkClientInSync(response, e))
//	    			return;
//	    	}
//System.err.println("file list="+fileList);
			String buttonName = request.getParameter("button");

	    	JSONObject uiJSON = null;
	    	if (jsonArray != null) 	
	    		uiJSON = (JSONObject)jsonArray.get(0);
	    	if (uiJSON == null) {
	    		Log.log(Log.ERROR, "Internal error servicing dynamic button "+buttonName+" - can't find button name");
    			response.sendError(HttpServletResponse.SC_FORBIDDEN);
    			return;
	    	}
			Log.log(Log.DEBUG, "Executing rule '"+buttonName+"'");
			JSONObject button = Davis.getConfig().getDynamicObject(buttonName);
//System.err.println("button="+button);
			String ruleText = (String)button.get("rule");
			StringBuffer commandLine = new StringBuffer(ruleText);
			commandLine.append("\n");
			JSONArray args = (JSONArray)uiJSON.get("args");
//System.err.println("args="+args);
			JSONObject arg;
			for (int i = 0; i < args.size(); i++) {
				if (i > 0) commandLine.append("%");
				arg = (JSONObject)args.get(i);
				commandLine.append("*").append(arg.get("name")).append("=").append(arg.get("value"));
			}
			if (button.get("location").equals("selection") && fileList.size() > 0){
				if (args.size() > 0) commandLine.append("%");
				commandLine.append("*filelist=");
				for (int i = 0; i < fileList.size(); i++) {
					if (i > 0) commandLine.append(",");
					commandLine.append(fileList.get(i).getAbsolutePath()); //+"/"+fileList.get(i).getName());
				}
			}
			commandLine.append("\n*OUT");
			Log.log(Log.DEBUG, "commandLine="+commandLine);

			//execute rule
//			String rule="passwordRule||msiExecCmd(changePassword,\"*username *password\",null,null,null,*OUT)|nop\n*username="+username+"%*password="+password+"\n*OUT";
//		    System.out.println(rule);
			String notice = null;
			java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(commandLine.toString().getBytes());
		    try {
				IRODSRuleExecResult outputParameters = davisSession.getRuleProcessingAO().executeRule( commandLine.toString() );
				Object out = outputParameters.getOutputParameterResults().get("*OUT");
				if (out instanceof String){
					notice = (String)out;
				} else if (out instanceof String[]) {
					notice = "Done.";
					String tmp;
					for (String s:(String[])out) {
						if (s!=null) {
							tmp = new String(Base64.decodeBase64(s.getBytes()), "ISO-8859-1").trim().replace("\n", "");
							Log.log(Log.DEBUG, "out="+tmp);
							notice = tmp;
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				notice = "Sorry, there is an error with rule execution!";
			}
			
			if (notice != null && notice.length() > 0)
				json.append("{"+escapeJSONArg("notice")+":"+escapeJSONArg(notice)+"}");
			json.append("\n");
			
		} else if (method.equalsIgnoreCase("replicas")) {
			String deleteResource = request.getParameter("delete");
			String replicateResource = request.getParameter("replicate");
	    	ArrayList<IRODSFile> fileList = new ArrayList<IRODSFile>();
//	    	try {
	    		getFileList(request, davisSession, fileList, getJSONContent(request));
//	    	} catch (ServletException e) {
//	    		if (!checkClientInSync(response, e))
//	    			return;
//	    	}

	        Iterator<IRODSFile> iterator = fileList.iterator();
			json.append("{\n"+escapeJSONArg("items")+":[\n");
	        while (iterator.hasNext()) {
	        	file = iterator.next();
	        	if (!file.isDirectory()) {	// Can't get replica info for a directory
	        		if (deleteResource != null) {
	        			Log.log(Log.DEBUG, "Deleting replica at "+deleteResource);
//                    	((IRODSFile)file).deleteReplica(deleteResource);	//Currently ALWAYS returns false!
	        			Log.log(Log.DEBUG, "Not currently working in Jargon");
	        		}
	        		if (replicateResource != null) {
	        			Log.log(Log.DEBUG, "Replicating to "+replicateResource);
                    	try {
                    		dataObjectAO.replicateIrodsDataObject(file.getAbsolutePath(), replicateResource);
                    	} catch (JargonException e) {
                			String s = e.getMessage();
                			if (s.contains("IRODS error occured -303"))
                				s = "Unknown host";
                			if (s.contains("IRODS error occured -305"))
                				s = "Can't connect to host";
                			if (s.contains("IRODS error occured -347"))
                				s = "Can't connect to host";
                			Log.log(Log.DEBUG, "Replication failed: "+e.getMessage());
                			response.sendError(HttpServletResponse.SC_FORBIDDEN, "replication failed: "+s);
                			return;
                    	}
                        	//   	        			Log.log(Log.DEBUG, "Not currently working in Jargon");
	        		}
	        		
	        		// Now get replica info
	        		StringBuilder query = new StringBuilder();
	        		query.append(RodsGenQueryEnum.COL_COLL_NAME.getName() + " like '"+file.getParent()+"' and ");
	        		query.append(RodsGenQueryEnum.COL_DATA_NAME.getName() + " like '"+file.getName()+"'");

	        		List<DataObject> dataObjects = null;
					try {
						dataObjects = dataObjectAO.findWhere(query.toString());
					} catch (JargonException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						throw new IOException(e.getMessage());
					}
		    		if (dataObjects != null)
			    		for (int i = 0; i < dataObjects.size(); i++) {
							if (i > 0) json.append(",\n");
							json.append("{"+escapeJSONArg("resource")+":"+escapeJSONArg(dataObjects.get(i).getResourceName())+",");
							json.append(escapeJSONArg("number")+":"+escapeJSONArg(String.valueOf(dataObjects.get(i).getDataReplicationNumber()))+"}");
			    		}
	        	}
	        }
			json.append("\n]}");
			
//		} else if (method.equalsIgnoreCase("shares")) {
//			json.append("{\n"+escapeJSONArg("items")+":[\n");
//			MetaDataRecordList[] fileDetails = getShares(davisSession);
//			if (fileDetails == null) 
//    			fileDetails = new MetaDataRecordList[0];	
// 			int i = 0;
//    		for (MetaDataRecordList p:fileDetails) {
//    			String dirName = (String)p.getValue(IRODSMetaDataSet.DIRECTORY_NAME);
//    			String fileName = (String)p.getValue(IRODSMetaDataSet.FILE_NAME);
//    			String sharingURL = (String)p.getValue(IRODSMetaDataSet.META_DATA_ATTR_VALUE);
//				if (i++ > 0) json.append(",\n");
//				json.append("{"+escapeJSONArg("file")+":\""+FSUtilities.escape(fileName)+"\",");
//				json.append(escapeJSONArg("dir")+":\""+FSUtilities.escape(dirName)+"\",");
//				json.append(escapeJSONArg("url")+":"+escapeJSONArg(sharingURL)+"}");
//        	}
////				}
//    		json.append("\n]}");
//			
//			
		} else if (method.equalsIgnoreCase("alltags")) {
			json.append("{\n"+escapeJSONArg("items")+":[\n");
			String[] tags = getTags(null, davisSession, DavisConfig.TAGMETAKEY);
 			int i = 0;
    		for (String s:tags) {
				if (i++ > 0) json.append(",\n");
				json.append("{"+escapeJSONArg("data")+":\""+FSUtilities.escape(s)+"\"}");
        	}
//				}
			json.append("\n]}");
			
		} else if (method.equalsIgnoreCase("puttags")) {
	    	boolean batch = false;
			if (request.getContentLength() > 0) {	// write tag metadata if present in request			
				JSONArray jsonArray = getJSONContent(request);			
				if (jsonArray != null) { 					
			    	ArrayList<IRODSFile> fileList = new ArrayList<IRODSFile>();
			    	getFileList(request, davisSession, fileList, jsonArray);				
			    	batch = (fileList.size() > 1);
					JSONObject jsonObject = (JSONObject)jsonArray.get(0);
					JSONArray tagsArray = (JSONArray)jsonObject.get("tags");
					
					for (int j = 0; j < fileList.size(); j++) {						
						IRODSFile selectedFile = fileList.get(j);
				//		if (j == fileList.size()-1)		
							file = selectedFile;	// Use first/last file in the list for returning metadata below
						if (tagsArray == null)
							break;
						Log.log(Log.DEBUG, "Changing tag metadata for: "+selectedFile+" to: "+tagsArray);
						
						try {
							List<MetaDataAndDomainData> metadatas=dataObjectAO.findMetadataValuesForDataObject(selectedFile);
							//delete all tag metadata, uses wildcards
							for (MetaDataAndDomainData metadata:metadatas){
								if (metadata.getAvuAttribute().equalsIgnoreCase(DavisConfig.TAGMETAKEY))
									dataObjectAO.deleteAVUMetadata(file.getAbsolutePath(), new AvuData(metadata.getAvuAttribute(),metadata.getAvuValue(),metadata.getAvuUnit()));
							}
	
							for (int i = 0; i < tagsArray.size(); i++) {
	    						dataObjectAO.addAVUMetadata(file.getAbsolutePath(), new AvuData(DavisConfig.TAGMETAKEY, (String)tagsArray.get(i), ""));
							}
//						} catch (IOException e){
//				        	Log.log(Log.DEBUG, "Set tag metadata failed: "+e);
//				        	String s = e.getMessage();
//				        	if (s.endsWith("-818000")) 
//				        		s = "you don't have permission"; // irods error -818000 
//		        			response.sendError(HttpServletResponse.SC_FORBIDDEN, s);
//		        			return;
						} catch (DataNotFoundException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							throw new IOException(e.getMessage());
						} catch (JargonException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							throw new IOException(e.getMessage());
						}
					}
				}
			}
				
			// Get and return tags for given item
	    	String[] tags = {};
	    	if (!batch)
	    		tags = getTags(file, davisSession, DavisConfig.TAGMETAKEY);
			json.append("{\n"+escapeJSONArg("items")+":[");
			for (int i = 0; i < tags.length; i++) {
				if (i>0) json.append(",\n");
				json.append("{"+escapeJSONArg("data")+":"+escapeJSONArg(tags[i])+"}");
			}
			json.append("\n]}");			
			
//		} else if (method.equalsIgnoreCase("unshareall")) {
//			JSONObject jsonObject = null;
//			JSONArray jsonArray = getJSONContent(request);
//			if (jsonArray != null) {	
//				jsonObject = (JSONObject)jsonArray.get(0);
//				JSONArray fileNamesArray = (JSONArray)jsonObject.get("files");
//				String sharingKey = Davis.getConfig().getSharingKey();
//				if (fileNamesArray != null && sharingKey != null)
//					for (int i = 0; i < fileNamesArray.size(); i++) {
//						String name = (String)fileNamesArray.get(i);
//						name = name.replaceAll("\\+", "%2B");
//						name = URLDecoder.decode(name, "UTF-8");
//						if (name.trim().length() == 0)
//							continue;	// If for any reason name is "", we MUST skip it because that's equivalent to home!   	 
//						file = getIRODSFile(name, davisSession);
//						String s = share(davisSession, file, false);
//						if (s != null) {
//							response.sendError(HttpServletResponse.SC_FORBIDDEN, s);
//							return;
//						}
//					}			
//			} else
//				throw new ServletException("Internal error reading file list: error parsing JSON");			
			
		} else if (method.equalsIgnoreCase("share")) { // share or unshare
			String action = request.getParameter("action");
			String sharingKey = Davis.getConfig().getSharingKey();
	    	ArrayList<IRODSFile> fileList = new ArrayList<IRODSFile>();
//	    	try {
	    	getFileList(request, davisSession, fileList, getJSONContent(request));
//	    	} catch (ServletException e) {
//	    		if (!checkClientInSync(response, e))
//	    			return;
//	    	}
	    	TicketAdminService ticketSvc = davisSession.getTicketAdminService();

	        Iterator<IRODSFile> iterator = fileList.iterator();
	        String s="";
	        while (sharingKey != null && iterator.hasNext()) {
	        	file = iterator.next();
        		try {
		        	if (action.equals("share")) {
							String ticketId = ticketSvc.createTicket(TicketCreateModeEnum.READ,
									file, null);
	
		        	}else{
		        		List<Ticket> tickets=null;
		        		if (file.isDirectory())
		        			tickets=ticketSvc.listAllTicketsForGivenCollection(file.getAbsolutePath(),0);
		        		else
		        			tickets=ticketSvc.listAllTicketsForGivenDataObject(file.getAbsolutePath(),0);
		        		for (Ticket ticket:tickets) {
		        			ticketSvc.deleteTicket(ticket.getTicketId());
		        		}
		        	}
				} catch (JargonException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					s+=e.getMessage();
				}
	        }
        	if (s != null&&s.length()>0) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN, s);
				return;
	    	}
//			
//		} else if (method.equalsIgnoreCase("search")) {
//			IRODSFileSystem searchFileSystem = null;
//			try {
//				searchFileSystem = FSUtilities.createIRODSFileSystem((IRODSAccount)davisSession.getIRODSFileSystem().getAccount(), DavisConfig.JARGONIRODS_SEARCH_SOCKET_TIMEOUT);
//			} catch (java.lang.SecurityException e) { // GSI credentials expired
//				Log.log(Log.WARNING, "Search failed due to "+e+"   This is likely to be because the user's GSI credentials expired. User will be asked to reauthenticate.");
//				response.sendError(HttpServletResponse.SC_GONE, "Access denied - you are not currently logged in");
//				response.flushBuffer();
//				return;
//			}
//			String s = request.getParameter("from");
//			boolean fromRoot = (s == null || s.equals("root"));
//			s = request.getParameter("show");
//			boolean showRead = (s == null || s.equals("read"));
//			s = request.getParameter("fileMatch");
//			boolean fileExact = (s == null || s.equals("exact"));
//			s = request.getParameter("pathMatch");
//			boolean pathExact = (s == null || s.equals("exact"));
//			String fileKeyword = request.getParameter("file");
//			String pathKeyword = request.getParameter("path");
//			boolean fileKeywordPresent = (fileKeyword.length() > 0);
//			boolean pathKeywordPresent = (pathKeyword.length() > 0);
//			String metadataNameKeyword = request.getParameter("metadataName");
//			String metadataValueKeyword = request.getParameter("metadataValue");
//			s = request.getParameter("metadataNameMatch");
//			boolean metadataNameExact = (s == null || s.equals("exact"));
//			s = request.getParameter("metadataValueMatch");
//			boolean metadataValueExact = (s == null || s.equals("exact"));
//
//			String[] tags = {};
//			String tagString = request.getParameter("tags");
//			if (tagString != null && tagString.length() > 0)
//				tags = tagString.split(", *");
//			
//			String keyword = fileKeyword;
//			if (!fileExact)
//				keyword = "%"+fileKeyword+"%";
//			if (!pathExact)
//				pathKeyword = "%"+pathKeyword+"%";
//			
//			ArrayList<MetaDataCondition> conditionsFile = new ArrayList<MetaDataCondition>();
//			if (fileKeywordPresent)
//				conditionsFile.add(MetaDataSet.newCondition(IRODSMetaDataSet.FILE_NAME, MetaDataCondition.LIKE, keyword));
//			if (pathKeywordPresent)
//				conditionsFile.add(MetaDataSet.newCondition(IRODSMetaDataSet.DIRECTORY_NAME, MetaDataCondition.LIKE, pathKeyword));
//
//			keyword = "%/"+fileKeyword;
//			if (!fileExact)
//				keyword = "%"+fileKeyword+"%";
//
//			ArrayList<MetaDataCondition> conditionsDir = new ArrayList<MetaDataCondition>();
//			if (fileKeywordPresent)
//				conditionsDir.add(MetaDataSet.newCondition(IRODSMetaDataSet.DIRECTORY_NAME, MetaDataCondition.LIKE, keyword));
//			if (pathKeywordPresent)
//				conditionsDir.add(MetaDataSet.newCondition(IRODSMetaDataSet.PARENT_DIRECTORY_NAME, MetaDataCondition.LIKE, pathKeyword));
//
//			if (!fromRoot) {
//				String currentDir = file.getAbsolutePath();
//				conditionsFile.add(MetaDataSet.newCondition(IRODSMetaDataSet.DIRECTORY_NAME, MetaDataCondition.LIKE, currentDir+"%"));
//				conditionsDir.add(MetaDataSet.newCondition(IRODSMetaDataSet.PARENT_DIRECTORY_NAME, MetaDataCondition.LIKE, currentDir+"%"));
//			}
//			
//			if (!showRead) {
//				conditionsFile.add(MetaDataSet.newCondition(IRODSMetaDataSet.OWNER, MetaDataCondition.EQUAL, davisSession.getAccount()));
//				conditionsDir.add(MetaDataSet.newCondition(IRODSMetaDataSet.DIRECTORY_OWNER, MetaDataCondition.EQUAL, davisSession.getAccount()));					
//			}
//
//			if (metadataNameKeyword != null && metadataNameKeyword.length() > 0) {
//				if (!metadataNameExact)
//					metadataNameKeyword = "%"+metadataNameKeyword+"%";
//				conditionsFile.add(MetaDataSet.newCondition(IRODSMetaDataSet.META_DATA_ATTR_NAME, MetaDataCondition.LIKE, metadataNameKeyword));
//				conditionsDir.add(MetaDataSet.newCondition(IRODSMetaDataSet.META_COLL_ATTR_NAME, MetaDataCondition.LIKE, metadataNameKeyword));
//			}
//			if (metadataValueKeyword != null && metadataValueKeyword.length() > 0) {
//				if (!metadataValueExact)
//					metadataValueKeyword = "%"+metadataValueKeyword+"%";
//				conditionsFile.add(MetaDataSet.newCondition(IRODSMetaDataSet.META_DATA_ATTR_VALUE, MetaDataCondition.LIKE, metadataValueKeyword));
//				conditionsDir.add(MetaDataSet.newCondition(IRODSMetaDataSet.META_COLL_ATTR_VALUE, MetaDataCondition.LIKE, metadataValueKeyword));
//			}
//			if (tags != null && tags.length > 0) {
//				conditionsFile.add(MetaDataSet.newCondition(IRODSMetaDataSet.META_DATA_ATTR_NAME, MetaDataCondition.EQUAL, DavisConfig.TAGMETAKEY));
////###TBD switch the two blocks below to enable multiple tag searching when jargon is fixed
////				conditionsFile.add(MetaDataSet.newCondition(IRODSMetaDataSet.META_DATA_ATTR_VALUE, MetaDataCondition.IN, tags));
//				conditionsFile.add(MetaDataSet.newCondition(IRODSMetaDataSet.META_DATA_ATTR_VALUE, MetaDataCondition.EQUAL, tags[0]));
//
//				conditionsDir.add(MetaDataSet.newCondition(IRODSMetaDataSet.META_COLL_ATTR_NAME, MetaDataCondition.EQUAL, DavisConfig.TAGMETAKEY));
//
////				conditionsDir.add(MetaDataSet.newCondition(IRODSMetaDataSet.META_COLL_ATTR_VALUE, MetaDataCondition.IN, tags));
//				conditionsDir.add(MetaDataSet.newCondition(IRODSMetaDataSet.META_COLL_ATTR_VALUE, MetaDataCondition.EQUAL, tags[0]));
//			}
////System.err.println("**************conditionsFile="+conditionsFile);
////System.err.println("**************conditionsDir="+conditionsDir);
//			MetaDataSelect selectsFile[] = MetaDataSet.newSelection(new String[]{
//					IRODSMetaDataSet.FILE_NAME,
//					IRODSMetaDataSet.DIRECTORY_NAME,
//					IRODSMetaDataSet.CREATION_DATE,
//					IRODSMetaDataSet.MODIFICATION_DATE,
//					IRODSMetaDataSet.SIZE,
//					IRODSMetaDataSet.RESOURCE_NAME,
//					IRODSMetaDataSet.FILE_REPLICA_STATUS,
//				});
//			MetaDataSelect selectsDir[] = MetaDataSet.newSelection(new String[]{
//					IRODSMetaDataSet.DIRECTORY_NAME,
//					IRODSMetaDataSet.DIRECTORY_TYPE,
//					IRODSMetaDataSet.DIRECTORY_CREATE_DATE,
//					IRODSMetaDataSet.DIRECTORY_MODIFY_DATE,
//				});
//			try {
//				Log.log(Log.DEBUG, "Search: querying files with "+conditionsFile);
//				MetaDataRecordList[] fileDetails = searchFileSystem.query(conditionsFile.toArray(new MetaDataCondition[0]), selectsFile, DavisConfig.SEARCH_MAX_QUERY_RESULTS);
//				Log.log(Log.DEBUG, "Search: querying directories with "+conditionsDir);
//	    		MetaDataRecordList[] dirDetails = searchFileSystem.query(conditionsDir.toArray(new MetaDataCondition[0]), selectsDir, DavisConfig.SEARCH_MAX_QUERY_RESULTS, Namespace.DIRECTORY);
//				Log.log(Log.DEBUG, "Search: querying complete");
//				int totalResults = 0;
//				int nResults = 0;
//				if (fileDetails != null && fileDetails.length > 0) {
//					nResults += fileDetails.length;
//					MetaDataRecordList[] l = MetaDataRecordList.getAllResults(fileDetails);
//					if (l != null && l.length > 0) 
//						totalResults += l.length;
//				}
//				if (dirDetails != null && dirDetails.length > 0) {
//					nResults += dirDetails.length;
//					MetaDataRecordList[] l = MetaDataRecordList.getAllResults(dirDetails);
//					if (l != null && l.length > 0)
//						totalResults += l.length;
//				}
//
//				boolean truncated = (nResults != totalResults);
//				HashMap<String, FileMetadata> metadata = new HashMap<String, FileMetadata>();
//				
//				selectsFile = 
//					MetaDataSet.newSelection(new String[] {
//							IRODSMetaDataSet.META_DATA_ATTR_NAME,
//							IRODSMetaDataSet.META_DATA_ATTR_VALUE,
//							IRODSMetaDataSet.FILE_NAME,
//							IRODSMetaDataSet.DIRECTORY_NAME
//					});
//				conditionsFile = new ArrayList<MetaDataCondition>();
//				conditionsDir = new ArrayList<MetaDataCondition>();
//				if (fileKeywordPresent) {
//					conditionsFile.add(MetaDataSet.newCondition(IRODSMetaDataSet.FILE_NAME, MetaDataCondition.LIKE, keyword));
//					conditionsDir.add(MetaDataSet.newCondition(IRODSMetaDataSet.DIRECTORY_NAME, MetaDataCondition.LIKE, keyword));
//				}
//				if (pathKeywordPresent) {
//					conditionsFile.add(MetaDataSet.newCondition(IRODSMetaDataSet.DIRECTORY_NAME, MetaDataCondition.LIKE, pathKeyword));
//					conditionsDir.add(MetaDataSet.newCondition(IRODSMetaDataSet.PARENT_DIRECTORY_NAME, MetaDataCondition.LIKE, pathKeyword));
//				}	
//
//				if (metadataNameKeyword != null && metadataNameKeyword.length() > 0) {
//					if (!metadataNameExact)
//						metadataNameKeyword = "%"+metadataNameKeyword+"%";
//					conditionsFile.add(MetaDataSet.newCondition(IRODSMetaDataSet.META_DATA_ATTR_NAME, MetaDataCondition.LIKE, metadataNameKeyword));
//					conditionsDir.add(MetaDataSet.newCondition(IRODSMetaDataSet.META_COLL_ATTR_NAME, MetaDataCondition.LIKE, metadataNameKeyword));
//				}
//				if (metadataValueKeyword != null && metadataValueKeyword.length() > 0) {
//					if (!metadataValueExact)
//						metadataValueKeyword = "%"+metadataValueKeyword+"%";
//					conditionsFile.add(MetaDataSet.newCondition(IRODSMetaDataSet.META_DATA_ATTR_VALUE, MetaDataCondition.LIKE, metadataValueKeyword));
//					conditionsDir.add(MetaDataSet.newCondition(IRODSMetaDataSet.META_COLL_ATTR_VALUE, MetaDataCondition.LIKE, metadataValueKeyword));
//				}
//
//				selectsDir =
//					MetaDataSet.newSelection(new String[] {
//						IRODSMetaDataSet.META_COLL_ATTR_NAME,
//						IRODSMetaDataSet.META_COLL_ATTR_VALUE,
//						IRODSMetaDataSet.DIRECTORY_NAME,
//						IRODSMetaDataSet.PARENT_DIRECTORY_NAME
//					});
//				Log.log(Log.DEBUG, "Search: querying metadata");
//				MetaDataRecordList[] fileMetaDetails = searchFileSystem.query(conditionsFile.toArray(new MetaDataCondition[0]), selectsFile, DavisConfig.SEARCH_MAX_QUERY_RESULTS*10);
//				MetaDataRecordList[] dirMetaDetails = searchFileSystem.query(conditionsDir.toArray(new MetaDataCondition[0]), selectsDir, DavisConfig.SEARCH_MAX_QUERY_RESULTS*10, Namespace.DIRECTORY);
//				Log.log(Log.DEBUG, "Search: querying metadata complete");
//				if (fileMetaDetails == null) 
//					fileMetaDetails = new MetaDataRecordList[0];
//				if (dirMetaDetails == null) 
//					dirMetaDetails = new MetaDataRecordList[0];
//				
//				for (MetaDataRecordList p:fileMetaDetails) {
//					String path = (String)p.getValue(IRODSMetaDataSet.DIRECTORY_NAME)+"/"+(String)p.getValue(IRODSMetaDataSet.FILE_NAME);
//					FileMetadata mdata = metadata.get(path);
//					if (mdata == null) {
//						mdata = new FileMetadata((IRODSFileSystem)file.getFileSystem(), (String)p.getValue(IRODSMetaDataSet.DIRECTORY_NAME), (String)p.getValue(IRODSMetaDataSet.FILE_NAME));
//						metadata.put(path, mdata);
//					}
//					mdata.addItem((String)p.getValue(IRODSMetaDataSet.META_DATA_ATTR_NAME), (String)p.getValue(IRODSMetaDataSet.META_DATA_ATTR_VALUE));
//				}
//				for (MetaDataRecordList p:dirMetaDetails) {
//					String path = (String)p.getValue(IRODSMetaDataSet.DIRECTORY_NAME);
//					FileMetadata mdata = metadata.get(path);
//					if (mdata == null) {
//						mdata = new FileMetadata((IRODSFileSystem)file.getFileSystem(), (String)p.getValue(IRODSMetaDataSet.PARENT_DIRECTORY_NAME), (String)p.getValue(IRODSMetaDataSet.DIRECTORY_NAME));
//						metadata.put(path, mdata);
//					}
//					mdata.addItem((String)p.getValue(IRODSMetaDataSet.META_COLL_ATTR_NAME), (String)p.getValue(IRODSMetaDataSet.META_COLL_ATTR_VALUE));
//				}
//
////FSUtilities.dumpQueryResult(fileDetails, ">file>");
////FSUtilities.dumpQueryResult(dirDetails, ">dir>");
////FSUtilities.dumpQueryResult(fileMetaDetails, ">filemeta>");
////FSUtilities.dumpQueryResult(dirMetaDetails, ">dirmeta>");
//
//				CachedFile[] fileList = FSUtilities.buildCache(fileDetails, dirDetails, (IRODSFileSystem)file.getFileSystem(), metadata, /*sort*/false, true, true);
//				json = new StringBuffer(FSUtilities.generateJSONFileListing(fileList, /*file*/null, /*comparator*/null, /*requestUIHandle*/null, /*start*/0, /*count*/Integer.MAX_VALUE, /*directoriesOnly*/false, false, truncated, totalResults));
//			} catch (SocketTimeoutException e) {
//				s = "Search query took too long - aborted.";
//				response.sendError(HttpServletResponse.SC_GATEWAY_TIMEOUT, s);
//				try {
//					searchFileSystem.close();
//				} catch (IOException ee) {}
//				return;
//			}
//			try {
//				searchFileSystem.close();
//			} catch (IOException ee) {}
//			
		} else if (method.equalsIgnoreCase("userlist")) {
			json.append("{\n"+escapeJSONArg("items")+":[\n");
			String[] users=FSUtilities.getUsernames(davisSession);
			for (int i = 0; i < users.length; i++) {
				if (i>0) json.append(",\n");
				json.append("{"+escapeJSONArg("name")+":"+escapeJSONArg(users[i])+"}");
			}
			json.append("\n]}");
		} else if (method.equalsIgnoreCase("debug")) { 
			try {
//				TestingPropertiesHelper testingPropertiesHelper = new TestingPropertiesHelper();
//				Properties testingProperties = testingPropertiesHelper.getTestProperties();
//				AssertionHelper assertionHelper = new AssertionHelper();
//				String IRODS_TEST_SUBDIR_PATH = "DeleteTest";
//				// test tuning variables
//				String testFileNamePrefix = "del1200filethenquery";
//				String testFileExtension = ".txt";
//				String deleteCollectionSubdir = IRODS_TEST_SUBDIR_PATH + "/del1200noforcethenquerydir";
//				int numberOfTestFiles = 1600;
//
//				System.err.println("properties="+testingProperties.toString());
//				// create collection to zap, this is all just setup
//				String deleteCollectionAbsPath = testingPropertiesHelper.buildIRODSCollectionAbsolutePathFromTestProperties(testingProperties, deleteCollectionSubdir);
//				IrodsInvocationContext invocationContext = testingPropertiesHelper.buildIRODSInvocationContextFromTestProperties(testingProperties);
//				IcommandInvoker invoker = new IcommandInvoker(invocationContext);
//				ImkdirCommand imkdrCommand = new ImkdirCommand();
//				imkdrCommand.setCollectionName(deleteCollectionAbsPath);
//				System.err.println("user home="+System.getProperty("user.home"));
//				System.err.println("deleteCollectionAbsPath="+deleteCollectionAbsPath);
//				invoker.invokeCommandAndGetResultAsString(imkdrCommand);
//
//				IputCommand iputCommand = new IputCommand();
//				String genFileName = "";
//				String fullPathToTestFile = "";
//
//				// generate a number of files in the subdir
//				for (int i = 0; i < numberOfTestFiles; i++) {
//					genFileName = testFileNamePrefix + String.valueOf(i) + testFileExtension;
//					fullPathToTestFile = FileGenerator.generateFileOfFixedLengthGivenName(testingProperties.getProperty(GENERATED_FILE_DIRECTORY_KEY) + "/", genFileName, 1);
//
//					iputCommand.setLocalFileName(fullPathToTestFile);
//					iputCommand.setIrodsFileName(deleteCollectionAbsPath);
//					iputCommand.setForceOverride(true);
//					invoker.invokeCommandAndGetResultAsString(iputCommand);
//				}
//
//				// now try and delete the collecton **** code to replicate Rowan's issue *****
//				IRODSAccount account = testingPropertiesHelper.buildIRODSAccountFromTestProperties(testingProperties);
//				IRODSFileSystem irodsFileSystem = new IRODSFileSystem(account);
//				IRODSFile irodsFile = new IRODSFile(irodsFileSystem, testingPropertiesHelper.buildIRODSCollectionAbsolutePathFromTestProperties(testingProperties, deleteCollectionSubdir));
//
//				System.err.println("** Parent file before="+irodsFile.getParentFile());					
//				boolean deleteResult = irodsFile.delete(false);
//				System.err.println("** Parent file after="+irodsFile.getParentFile());					
//				System.err.println("** Parent file exists="+irodsFile.getParentFile().exists());					
//
//
//				//irodsFile.close();
//				// now do a query
//
//
//				String[] fields = { IRODSMetaDataSet.FILE_NAME,	IRODSMetaDataSet.DIRECTORY_NAME };
//
//
//				MetaDataSelect[] select = IRODSMetaDataSet.newSelection(fields);
//				MetaDataCondition[] condition = new MetaDataCondition[1];
//				condition[0] = IRODSMetaDataSet.newCondition(IRODSMetaDataSet.DIRECTORY_NAME,
//						MetaDataCondition.EQUAL, irodsFile.getAbsolutePath());
//				MetaDataRecordList[] fileList = irodsFileSystem.query(condition, select, 100, Namespace.FILE, false);
//
//
//				irodsFileSystem.close();
//				TestCase.assertTrue("delete was unsuccessful", deleteResult);
//				assertionHelper.assertIrodsFileOrCollectionDoesNotExist(deleteCollectionAbsPath);
				
//				IRODSCommandsDeleteTest tester = new IRODSCommandsDeleteTest();
//				tester.setUpBeforeClass();
//				tester.testDelete1200ByDeletingCollectionNoForceThenIssueGenQuery();
			} catch (Exception e) {
				System.err.println("************ Exception in debug method handler: "+e);
				e.printStackTrace();
			}
			
		} else if (method.equalsIgnoreCase("logout")) { 
			HttpSession session = request.getSession(true);
			request.getSession().removeAttribute(Davis.FORMAUTHATTRIBUTENAME); // Discard auth attribute (if there is one)
			session.invalidate();
			AuthorizationProcessor.getInstance().destroy(davisSession.getSessionID());
			
			davisSession.getClientInstances().clear(); // destroy cache for the session (all browser windows) 
			
			Log.log(Log.INFORMATION, "logout from: "+request.getRemoteAddr());
			if (request.isSecure()) 
				json.append("{"+escapeJSONArg("redirect")+":"+escapeJSONArg(request.getRequestURI())+"}");	// Return to login page for original url
			else {
				String returnURL = Davis.getConfig().getLogoutReturnURL();
				if (returnURL == null || returnURL.length() == 0)
					returnURL = "";
				else
					returnURL = "?return="+returnURL;
				json.append("{"+escapeJSONArg("redirect")+":"+escapeJSONArg("https://"+request.getServerName()+"/Shibboleth.sso/Logout"+returnURL)+"}");
			}
		} else { 
			throw new ServletException("Internal error: unnknown method");			
		}
		
		ServletOutputStream op = null;
		try {
			 op = response.getOutputStream();
		} catch (EOFException e) {
			Log.log(Log.WARNING, "EOFException when preparing to send servlet response - client probably disconnected");
			return;
		}
		byte[] buf = json.toString().getBytes();
		Log.log(Log.DEBUG, "output(" + buf.length + "):\n" + json.toString());
		op.write(buf);
		op.flush();
		op.close();
	}
	
//	private MetaDataRecordList[] getShares(DavisSession davisSession) throws IOException{
//		
//		return getShares(davisSession, null, null, null);
//	}
//
//	private MetaDataRecordList[] getShares(DavisSession davisSession, String directory, String fileName, String key) throws IOException{
//		
//		String sharingKey = key;
//		if (sharingKey == null)
//			sharingKey = Davis.getConfig().getSharingKey();
//		if (sharingKey == null)
//			return null;		
//
//		MetaDataSelect selectsFile[] = 
//		MetaDataSet.newSelection(new String[] {
////				IRODSMetaDataSet.FILE_ACCESS_NAME,
//				//IRODSMetaDataSet.FILE_ACCESS_USER_ID,
//				IRODSMetaDataSet.OWNER,							
//				IRODSMetaDataSet.FILE_NAME,
//				IRODSMetaDataSet.META_DATA_ATTR_VALUE,
//				IRODSMetaDataSet.DIRECTORY_NAME
//		});
//		MetaDataCondition conditionsFile[];
//		if (directory == null)
//			conditionsFile = new MetaDataCondition[] {
//					MetaDataSet.newCondition(IRODSMetaDataSet.META_DATA_ATTR_NAME, MetaDataCondition.EQUAL, sharingKey),
//					MetaDataSet.newCondition(IRODSMetaDataSet.OWNER, MetaDataCondition.EQUAL, davisSession.getAccount()), 
//			};
//		else
//			conditionsFile = new MetaDataCondition[] {
//				MetaDataSet.newCondition(IRODSMetaDataSet.META_DATA_ATTR_NAME, MetaDataCondition.EQUAL, sharingKey),
//				MetaDataSet.newCondition(IRODSMetaDataSet.OWNER, MetaDataCondition.EQUAL, davisSession.getAccount()), 
//				MetaDataSet.newCondition(IRODSMetaDataSet.FILE_NAME, MetaDataCondition.EQUAL, fileName), 
//				MetaDataSet.newCondition(IRODSMetaDataSet.DIRECTORY_NAME, MetaDataCondition.EQUAL, directory), 
//			};
//		return ((IRODSFileSystem)davisSession.getIRODSFileSystem()).query(conditionsFile, selectsFile, DavisConfig.JARGON_MAX_QUERY_NUM);
//	}
	
	private String[] getTags(IRODSFile file, DavisSession davisSession, String key) throws IOException{
		
		HashSet<String> tags = new HashSet<String>();
        DataObjectAO dataObjectAO=davisSession.getDataObjectAO();
        CollectionAO collectionAO=davisSession.getCollectionAO();
		
		List<MetaDataAndDomainData> metadatas;
		try {
			if (file==null) {
				ArrayList<AVUQueryElement> avus = new ArrayList<AVUQueryElement>();
				avus.add(AVUQueryElement.instanceForValueQuery(AVUQueryPart.ATTRIBUTE,
						AVUQueryOperatorEnum.EQUAL, key));
				metadatas = dataObjectAO.findMetadataValuesByMetadataQuery(avus);
				for (MetaDataAndDomainData metadata:metadatas){
					tags.add(metadata.getAvuValue());
				}
				metadatas = collectionAO.findMetadataValuesByMetadataQuery(avus);
				for (MetaDataAndDomainData metadata:metadatas){
					tags.add(metadata.getAvuValue());
				}
			}else{
				if (file.isDirectory()){
					metadatas = collectionAO.findMetadataValuesForCollection(file.getAbsolutePath());
					for (MetaDataAndDomainData metadata:metadatas){
						if (metadata.getAvuAttribute().equalsIgnoreCase(key)) tags.add(metadata.getAvuValue());
					}
				}else{
					metadatas = dataObjectAO.findMetadataValuesForDataObject(file);
					for (MetaDataAndDomainData metadata:metadatas){
						if (metadata.getAvuAttribute().equalsIgnoreCase(key)) tags.add(metadata.getAvuValue());
					}
				}
			}


			return tags.toArray(new String[0]);
		} catch (JargonException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new IOException(e.getMessage());
		} catch (JargonQueryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new IOException(e.getMessage());
		}
		
	}
	
//	private String share(DavisSession davisSession, IRODSFile file, boolean share) {
//		
//    	if (!file.isDirectory()) {	// Can't share a directory
//			String sharingKey = Davis.getConfig().getSharingKey();
//    		String username = Davis.getConfig().getSharingUser();
//			if (username != null) {
//				GeneralFileSystem fileSystem = file.getFileSystem();				
//				String permission = "r";
//				if (!share)
//					permission = "n";
//				
//				try {
//					// Add/remove read permission for share user
//					Log.log(Log.DEBUG, "change permission for "+username+" to "+permission+" for sharing");
//					((IRODSFile)file).changePermissions(permission, username, false);
//				} catch (IOException e){
//					String s = e.toString();
//					if (e.getCause() != null)
//						s += " : "+e.getCause().getMessage();
//		        	Log.log(Log.DEBUG, "Set permissions failed for sharing: "+s);
//		        	if (e.getMessage().endsWith("-818000")) 
//		        		s = "you don't have permission"; // irods error -818000 
//		        	if (e.getMessage().endsWith("IRODS error occured msg")) {
//		        		s = "can't unshare "+file.getName();  
//		        		Log.log(Log.ERROR, s+" due to iRODS error");
//		        	}
//		        	if (e.getMessage().endsWith("-827000")) {
//		        		s = "Internal error: sharing user account doesn't exist";
//		        		Log.log(Log.ERROR, s);
//		        	}
//        			return s;
//				}
//				
//				try {
//					// Add/remove share metadata
//					if (fileSystem instanceof SRBFileSystem) {
//						Log.log(Log.ERROR,"Sharing not implemented for SRB");
//					}else 
//					if (fileSystem instanceof IRODSFileSystem) {
//						if (share) {
//							MetaDataRecordList[] fileDetails = getShares(davisSession, file.getParent(), file.getName(), null);
//							if (!(fileDetails == null || fileDetails.length == 0)) {
//								Log.log(Log.DEBUG, file.getPath()+" is already shared - ignoring");
//								return null;
//							}
//						}
//						Log.log(Log.DEBUG, "removing metadata field '"+sharingKey+"' for "+username+" to end sharing");
//						((IRODSFile)file).deleteMetaData(new String[]{sharingKey,"%","%"});
//						if (share) {
//							String randomString = Long.toHexString(random.nextLong());
//							String shareURL = Davis.getConfig().getSharingURLPrefix()+'/'+randomString+'/'+DavisUtilities.encodeFileName(file.getName());
//							String[] metadata = new String[] {sharingKey, shareURL, ""};		    	
//							Log.log(Log.DEBUG, "adding share URL '"+shareURL+"' to metadata field '"+sharingKey+"' for "+username+" to enable sharing");
//							((IRODSFile)file).modifyMetaData(metadata);
//						}
//					}
//				} catch (IOException e){
//		        	Log.log(Log.DEBUG, "Set metadata failed for sharing: "+e);
//		        	String s = e.getMessage();
//		        	if (s.endsWith("-818000")) 
//		        		s = "you don't have permission"; // irods error -818000 
//        			return s;
//				}
//			}
//    	}
//    	return null;
//	}
	

    private boolean error = false;

	
    private static final Map trackers = new HashMap();	// Set of trackers currently transferring
    
    private static void copy(Tracker tracker, InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[4096];
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            tracker.incrementBytesReceived(n);
//   Log.log(Log.DEBUG, "sent "+n);
//            try { 			// Simulate slow connection.
//                 Thread.sleep(3);
//            } catch (InterruptedException ex) {
//                throw new RuntimeException(ex);
//            }
        }
    }
    
    private static Tracker createTracker(long size) {
    	Tracker tracker = new Tracker(size);
    	trackers.put(tracker.getId(), tracker);
    	return tracker;
    }
    
    private static Tracker getTracker(long id, boolean verifyComplete) {
        synchronized(trackers) {
            Tracker tracker = (Tracker) trackers.get(id);
            if (verifyComplete) {
                if (tracker == null) {
                    throw new IllegalArgumentException("No file found with id: \"" + id + "\".");
                }
                if (!tracker.isComplete()) {
                    throw new IllegalArgumentException("Cannot store file with id: \"" + id 
                            + "\" because it has not yet completed uploading.");
                }
            }
            return tracker;
        }
    }
    
    public static class Tracker {
        
        public static final int OK = 0;
        public static final int ERROR_FILE_NO_FILE = 1;
        public static final int ERROR_FILE_INVALID = 2;
        public static final int ERROR_FILE_OVERSIZE = 3;
        private static long autoId = -1;
        
        private long bytesReceived;
        private long size;
        private boolean complete;
        private long id;
        private FileItem fileItem;
        private int error = OK;
        
        Tracker(long size) {
        	super();
        	this.id = getAutoId();
        	this.size = size;
        }
        
        synchronized private long getAutoId() {
        	
        	return ++autoId;
        }
        
        public long getBytesReceived() {
            return bytesReceived;
        }
        
        public int getComplete() {
            return (int) (bytesReceived * 100 / size);
        }
        
        public int getError() {
            return error;
        }
        
        public long getId() {
            return id;
        }
        
        public String getName() {
            return fileItem.getName();
        }
        
        public InputStream getInputStream() 
        throws IOException {
            return fileItem.getInputStream();
        }
        
        public long getSize() {
            return size;
        }
        
        public boolean isComplete() {
            return complete;
        }
        
        public void incrementBytesReceived(long increment) {
            this.bytesReceived += increment;
        }
        
        public void setBytesReceived(long bytesReceived) {
            this.bytesReceived = bytesReceived;
        }
        
        private void setFileItem(FileItem fileItem) {
            this.fileItem = fileItem;
        }
        
        public void setError(int error) {
            this.error = error; 
        }
        
        public void setComplete() {
            complete = true;
        }
    }
}
