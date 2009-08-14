package webdavis;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;

import edu.sdsc.grid.io.DirectoryMetaData;
import edu.sdsc.grid.io.FileMetaData;
import edu.sdsc.grid.io.GeneralFileSystem;
import edu.sdsc.grid.io.GeneralMetaData;
import edu.sdsc.grid.io.MetaDataCondition;
import edu.sdsc.grid.io.MetaDataRecordList;
import edu.sdsc.grid.io.MetaDataSelect;
import edu.sdsc.grid.io.MetaDataSet;
import edu.sdsc.grid.io.MetaDataTable;
import edu.sdsc.grid.io.RemoteFile;
import edu.sdsc.grid.io.RemoteFileOutputStream;
import edu.sdsc.grid.io.RemoteFileSystem;
import edu.sdsc.grid.io.ResourceMetaData;
import edu.sdsc.grid.io.UserMetaData;
import edu.sdsc.grid.io.irods.IRODSAccount;
import edu.sdsc.grid.io.irods.IRODSException;
import edu.sdsc.grid.io.irods.IRODSFile;
import edu.sdsc.grid.io.irods.IRODSFileInputStream;
import edu.sdsc.grid.io.irods.IRODSFileOutputStream;
import edu.sdsc.grid.io.irods.IRODSFileSystem;
import edu.sdsc.grid.io.irods.IRODSMetaDataRecordList;
import edu.sdsc.grid.io.irods.IRODSMetaDataSet;
import edu.sdsc.grid.io.srb.SRBFile;
import edu.sdsc.grid.io.srb.SRBFileInputStream;
import edu.sdsc.grid.io.srb.SRBFileOutputStream;
import edu.sdsc.grid.io.srb.SRBFileSystem;
import edu.sdsc.grid.io.srb.SRBMetaDataRecordList;
import edu.sdsc.grid.io.srb.SRBMetaDataSet;
import edu.sdsc.grid.io.MetaDataField;

import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.*;

/**
 * Default implementation of a handler for requests using the HTTP POST method.
 * 
 * @author Shunde Zhang
 * @author Eric Glass
 */
public class DefaultPostHandler extends AbstractHandler {

	final static char[] SEPARATORS = {'\\', '/'}; 

	/**
	 * Services requests which use the HTTP POST method. This may, at some
	 * point, implement some sort of useful behavior. Right now it doesn't do
	 * anything.
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
	public void service(HttpServletRequest request,
			HttpServletResponse response, DavisSession davisSession)
			throws ServletException, IOException {

		String method = request.getParameter("method");
		if (method == null)
			return;
		String url = getRemoteURL(request, getRequestURL(request),
				getRequestURICharset());
		Log.log(Log.DEBUG, "url:" + url + " method:" + method);
		RemoteFile file = getRemoteFile(request, davisSession);
		Log.log(Log.DEBUG, "GET Request for resource \"{0}\".", file);
		if (!file.exists()) {
			Log.log(Log.WARNING, "File does not exist.");
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		String requestUrl = getRequestURL(request);
		Log.log(Log.DEBUG, "Request URL: {0}", requestUrl);
		StringBuffer str = new StringBuffer();
		if (method.equalsIgnoreCase("permission")) {
			String username = request.getParameter("username");
			boolean recursive = false;
			InputStream input = request.getInputStream();
			byte[] buf = new byte[request.getContentLength()];
			int count=input.read(buf);
			Log.log(Log.DEBUG, "read:"+count);
			Log.log(Log.DEBUG, "received data: " + new String(buf));
			JSONArray jsonArray=(JSONArray)JSONValue.parse(new String(buf));
			if (jsonArray != null) {	
				JSONObject jsonObject = (JSONObject)jsonArray.get(0);
				JSONArray filesArray = (JSONArray)jsonObject.get("files");
				GeneralFileSystem fileSystem = file.getFileSystem();
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
				for (int j = 0; j < filesArray.size(); j++) {
					String fileName = (String)filesArray.get(j);
					RemoteFile selectedFile = getRemoteFile(file.getAbsolutePath()+file.getPathSeparator()+fileName, davisSession);
					if (j == filesArray.size()-1)		// Use the last file in the list for returning metadata below
						file = selectedFile;
				
					if (username != null) {
						if (/*file.getFileSystem()*/fileSystem instanceof SRBFileSystem) {
							Log.log(Log.DEBUG, "change permission for "+username+"."+domain+" to "+permission+" (recursive="+recursive+")");
							((SRBFile) selectedFile).changePermissions(permission, username, domain, recursive);
						} else if (/*file.getFileSystem()*/fileSystem instanceof IRODSFileSystem) {
							Log.log(Log.DEBUG, "change permission for "+username+" to "+permission+" (recursive="+recursive+")");
							((IRODSFile) selectedFile).changePermissions(permission, username, recursive);
						}
					}
					if (sticky!=null) {
						Log.log(Log.DEBUG, "set "+selectedFile.getAbsolutePath()+" -- sticky:"+sticky);
						boolean flag=false;
						try {
							flag = Boolean.parseBoolean(sticky);
						} catch (Exception _e) {
						}
						this.setSticky(selectedFile, flag, recursive);
					}
				}
			}

			// Fetch permissions for item
			MetaDataRecordList[] permissions = null;
			str.append("{\n");
			if (file.getFileSystem() instanceof SRBFileSystem) {
				permissions = ((SRBFile) file).getPermissions(true);
				if (file.isDirectory()){
					str.append("sticky:'").append(this.isPermInherited(file)).append("',\n");
				}
				str.append("items:[");
				if (permissions != null) {
					for (int i = 0; i < permissions.length; i++) {
						// for (MetaDataField f:p.getFields()){
						// Log.log(Log.DEBUG, f.getName()+" "+p.getValue(f));
						// }
						if (i > 0)
							str.append(",\n");
						else
							str.append("\n");
						// "user name"
						str.append("{username:'").append(
								permissions[i].getValue(SRBMetaDataSet.USER_NAME))
								.append("', ");
						// "user domain"
						str
								.append("domain:'")
								.append(
										permissions[i]
												.getValue(SRBMetaDataSet.USER_DOMAIN))
								.append("', ");
	                    if(file.isDirectory())
	                    {
	    					// "directory access constraint"
	    					str
	    							.append("permission:'")
	    							.append(
	    									permissions[i]
	    											.getValue(SRBMetaDataSet.DIRECTORY_ACCESS_CONSTRAINT))
	    							.append("'}");
	                    }
	                    else
	                    {
	    					// "file access constraint"
	    					str
	    							.append("permission:'")
	    							.append(
	    									permissions[i]
	    											.getValue(SRBMetaDataSet.ACCESS_CONSTRAINT))
	    							.append("'}");
	                    }
					}
				}
			} else if (file.getFileSystem() instanceof IRODSFileSystem) {
				if (file.isDirectory()){
					permissions = ((IRODSFile) file).query(new String[]{DirectoryMetaData.DIRECTORY_INHERITANCE});
					boolean stickyBit=false;
					if (permissions != null && permissions.length>0){
						String stickBitStr=(String)permissions[0].getValue(DirectoryMetaData.DIRECTORY_INHERITANCE);
						Log.log(Log.DEBUG, "stickBitStr: "+stickBitStr);
						stickyBit=stickBitStr!=null&&stickBitStr.equals("1");
					}
					str.append("sticky:'").append(stickyBit).append("',\n");
					permissions = ((IRODSFile) file).query(new String[]{IRODSMetaDataSet.DIRECTORY_USER_NAME, IRODSMetaDataSet.DIRECTORY_USER_ZONE,
							IRODSMetaDataSet.DIRECTORY_ACCESS_CONSTRAINT});
				}else
					permissions = ((IRODSFile) file).query(new String[]{UserMetaData.USER_NAME,
							GeneralMetaData.ACCESS_CONSTRAINT});
				Log.log(Log.DEBUG, "irods permissions: "+permissions);
				str.append("items:[");
				if (permissions != null) {
					for (int i = 0; i < permissions.length; i++) {
//						for (MetaDataField f:permissions[i].getFields()){
//						Log.log(Log.DEBUG, f.getName()+" "+permissions[i].getValue(f));
//						}
						if (i > 0)
							str.append(",\n");
						else
							str.append("\n");
						// "user domain"
	                    if(file.isDirectory())
	                    {
							// "user name"
							str.append("{username:'").append(
									permissions[i].getValue(IRODSMetaDataSet.DIRECTORY_USER_NAME));
							if (!((IRODSFileSystem)file.getFileSystem()).getZone().equals(permissions[i].getValue(IRODSMetaDataSet.DIRECTORY_USER_ZONE)))	
								str.append("#")
								   .append(permissions[i].getValue(IRODSMetaDataSet.DIRECTORY_USER_ZONE));
							str.append("', ");
	    					// "directory access constraint"
	    					str
	    							.append("permission:'")
	    							.append(
	    									permissions[i]
	    											.getValue(IRODSMetaDataSet.DIRECTORY_ACCESS_CONSTRAINT))
	    							.append("'}");
	                    }
	                    else
	                    {
							// "user name"
							str.append("{username:'").append(
									permissions[i].getValue(UserMetaData.USER_NAME))
									.append("', ");
	    					// "file access constraint"
	    					str
	    							.append("permission:'")
	    							.append(
	    									permissions[i]
	    											.getValue(GeneralMetaData.ACCESS_CONSTRAINT))
	    							.append("'}");
	                    }
					}
				}
			}
			str.append("\n");
			str.append("]}");

		} else if (method.equalsIgnoreCase("metadata")) {
			if (request.getContentLength() > 0) {	// write metadata if given in request
		        InputStream input = request.getInputStream();
		        byte[] buf = new byte[request.getContentLength()];
		        int count=input.read(buf);
		        Log.log(Log.DEBUG, "read:"+count);
		        Log.log(Log.DEBUG, "received metadata: " + new String(buf));

				// for testing
//				String line = null;
//				StringBuffer buffer = new StringBuffer();
//				while ((line = reader.readLine()) != null) {
//					buffer.append(line);
//				}
//				Log.log(Log.DEBUG, "received metadata: " + buffer);

				JSONArray jsonArray=(JSONArray)JSONValue.parse(new String(buf));
				if (jsonArray != null) {	
					JSONObject jsonObject = (JSONObject)jsonArray.get(0);
					JSONArray filesArray = (JSONArray)jsonObject.get("files");
					JSONArray metadataArray = (JSONArray)jsonObject.get("metadata");
					GeneralFileSystem fileSystem = file.getFileSystem();
					
					for (int j = 0; j < filesArray.size(); j++) {
						String fileName = (String)filesArray.get(j);
						RemoteFile selectedFile = getRemoteFile(file.getAbsolutePath()+file.getPathSeparator()+fileName, davisSession);
						Log.log(Log.DEBUG, "changing metadata for: "+selectedFile);
						if (j == filesArray.size()-1)		// Use the last file in the list for returning metadata below
							file = selectedFile;

						MetaDataTable metaDataTable = null;
	
						if (/*file.getFileSystem()*/fileSystem instanceof SRBFileSystem) {
							String[][] definableMetaDataValues = new String[metadataArray.size()][2];
	
	    					for (int i = 0; i < metadataArray.size(); i++) {
	    						definableMetaDataValues[i][0] = (String) ((JSONObject) metadataArray.get(i)).get("name");
	    						definableMetaDataValues[i][1] = (String) ((JSONObject) metadataArray.get(i)).get("value");
	    					}
	
	    					int[] operators = new int[definableMetaDataValues.length];
							MetaDataRecordList rl;
							MetaDataField mdf=null;
							if (!selectedFile.isDirectory()){
								mdf=SRBMetaDataSet.getField(SRBMetaDataSet.DEFINABLE_METADATA_FOR_FILES);
							}else{
								mdf=SRBMetaDataSet.getField(SRBMetaDataSet.DEFINABLE_METADATA_FOR_DIRECTORIES);
							}
							if (mdf!=null){
								rl = new SRBMetaDataRecordList(mdf,(MetaDataTable) null);
								selectedFile.modifyMetaData(rl);
								metaDataTable = new MetaDataTable(operators, definableMetaDataValues);
								rl = new SRBMetaDataRecordList(mdf,metaDataTable);
								selectedFile.modifyMetaData(rl);
							}
	
						}else if (/*file.getFileSystem()*/fileSystem instanceof IRODSFileSystem) {
							//delete all metadata, uses wildcards
	//						try{
							((IRODSFile)selectedFile).deleteMetaData(new String[]{"%","%","%"});
	//						}catch (Exception _e){}
							
							String[][] definableMetaDataValues = new String[metadataArray.size()][3];
	
	    					for (int i = 0; i < metadataArray.size(); i++) {
	    						definableMetaDataValues[i][0] = (String) ((JSONObject) metadataArray.get(i)).get("name");
	    						definableMetaDataValues[i][1] = (String) ((JSONObject) metadataArray.get(i)).get("value");
	    						definableMetaDataValues[i][2] = (String) ((JSONObject) metadataArray.get(i)).get("unit");
	    					}
	    					for (String[] metadata:definableMetaDataValues)
	    						((IRODSFile)selectedFile).modifyMetaData(metadata);
						}
					}
				}
			}

			// Get and return metadata 
			MetaDataCondition[] conditions;
			MetaDataTable metaDataTable = null;
			MetaDataSelect[] selects=null;
			MetaDataRecordList[] rl = null;
			str.append("{\nitems:[");
			boolean b = false;
			if (file.getFileSystem() instanceof SRBFileSystem) {
				// conditions = new MetaDataCondition[0];
				// conditions[0] = MetaDataSet.newCondition(
				// SRBMetaDataSet.DEFINABLE_METADATA_FOR_FILES, metaDataTable );

				if (!file.isDirectory()){
					selects = new MetaDataSelect[1];
					// "definable metadata for files"
					selects[0] = MetaDataSet.newSelection(SRBMetaDataSet.DEFINABLE_METADATA_FOR_FILES);
				}else{
					selects = new MetaDataSelect[1];
					// "definable metadata for files"
					selects[0] = MetaDataSet.newSelection(SRBMetaDataSet.DEFINABLE_METADATA_FOR_DIRECTORIES);
				}
				if (selects!=null){
					rl = file.query(selects);
				}
				if (rl != null) { // Nothing in the database matched the query
					for (int i = 0; i < rl.length; i++) {
//						if (i > 0)
//							str.append(",\n");
//						else
//							str.append("\n");

						int metaDataIndex;
						if (file.isDirectory())
							metaDataIndex = rl[i].getFieldIndex(SRBMetaDataSet.DEFINABLE_METADATA_FOR_DIRECTORIES);
						else
							metaDataIndex = rl[i].getFieldIndex(SRBMetaDataSet.DEFINABLE_METADATA_FOR_FILES);
						if (metaDataIndex > -1) {
							MetaDataTable t = rl[i].getTableValue(metaDataIndex);
							for (int j = 0; j < t.getRowCount(); j++) {
								if (b)
									str.append(",\n");
								else
									str.append("\n");
								str.append("{name:'");
								str.append(t.getStringValue(j, 0)).append("', ");
								str.append("value:'")
										.append(t.getStringValue(j, 1)).append("'}");
								b = true;
							}
						}

						// "definable metadata for files"
						// String[]
						// lines=rl[i].getValue(SRBMetaDataSet.DEFINABLE_METADATA_FOR_FILES).toString().split("\n");
						// boolean b=false;
						// for (int j=0;j<lines.length;j++){
						// if (b)
						// str.append(",\n");
						// else
						// str.append("\n");
						// if (lines[j].length()>0){
						// str.append("{name:'");
						// str.append(lines[j].replaceAll(" = ",
						// "', value:'").trim());
						// str.append("'}");
						// b=true;
						// }
						// }
						// str.append("{name:'").append(rl[i].).append("', ");
						// str.append("value:'").append(permissions[i].getValue("file access constraint")).append("'}");
						// for (int j=0;j<rl[i].getFieldCount();j++){
						// System.out.println("field name: "+rl[i].getFieldName(j));
						// System.out.println("value: "+rl[i].getValue(j));
						// }
					}
				}

			}else if (file.getFileSystem() instanceof IRODSFileSystem) {
				selects=new MetaDataSelect[3];
				if (file.isDirectory()){
				    selects[0] = MetaDataSet.newSelection( IRODSMetaDataSet.META_COLL_ATTR_NAME );
					selects[1] = MetaDataSet.newSelection( IRODSMetaDataSet.META_COLL_ATTR_VALUE );
					selects[2] = MetaDataSet.newSelection( IRODSMetaDataSet.META_COLL_ATTR_UNITS );    
				}else{
				    selects[0] = MetaDataSet.newSelection( IRODSMetaDataSet.META_DATA_ATTR_NAME );
					selects[1] = MetaDataSet.newSelection( IRODSMetaDataSet.META_DATA_ATTR_VALUE );
					selects[2] = MetaDataSet.newSelection( IRODSMetaDataSet.META_DATA_ATTR_UNITS );    
				}
				rl = file.query( selects );
//				selects = new MetaDataSelect[1];
//				// "definable metadata for files"
//				selects[0] = MetaDataSet
//						.newSelection(IRODSMetaDataSet.GROUP_DEFINABLE_METADATA);
//				rl = file.query( selects );
				if (rl != null) { // Nothing in the database matched the query
					for (int i = 0; i < rl.length; i++) {
//						if (i > 0)
//							str.append(",\n");
//						else
//							str.append("\n");
						if (i>0) str.append(",\n");
						if (file.isDirectory()){
							str.append("{name:'");
							str.append(rl[i].getValue(IRODSMetaDataSet.META_COLL_ATTR_NAME)).append("', ");
							str.append("value:'")
								.append(rl[i].getValue(IRODSMetaDataSet.META_COLL_ATTR_VALUE)).append("', ");
							str.append("unit:'")
									.append(rl[i].getValue(IRODSMetaDataSet.META_COLL_ATTR_UNITS)).append("'}");
						}else{
							str.append("{name:'");
							str.append(rl[i].getValue(IRODSMetaDataSet.META_DATA_ATTR_NAME)).append("', ");
							str.append("value:'")
								.append(rl[i].getValue(IRODSMetaDataSet.META_DATA_ATTR_VALUE)).append("', ");
							str.append("unit:'")
									.append(rl[i].getValue(IRODSMetaDataSet.META_DATA_ATTR_UNITS)).append("'}");
						}
						b = true;
					}
				}
			}
			str.append("\n");
			str.append("]}");
		} else if (method.equalsIgnoreCase("upload")) {

			
//	IRODSFile f=new IRODSFile((IRODSFileSystem)file.getFileSystem(), ((IRODSFileSystem)file.getFileSystem()).getHomeDirectory()+"/temp2.txt");
//System.err.println("f="+f);
//System.err.println("exists="+f.exists());
//	f.setResource("arcs-df.ivec.org");
//	f.replicate("arcs-df.intersect.org.au");
////	f.replicate("data-dev.ersa.edu.au");
////	f.setResource("second.ngdata-dev.hpcu.uq.edu.au");
////	f.replicate("second.ngdata-dev.hpcu.uq.edu.au");
////	f.deleteReplica("second.ngdata-dev.hpcu.uq.edu.au");
//System.err.println("replicate finished");
			
			response.setContentType("text/html");
			if (!ServletFileUpload.isMultipartContent(request)) 
	            str.append("<html><body><textarea>{\"status\":\"failed\", \"message\":\"Invalid request (not multipart)\"}</textarea></body></html>");
	        else {
                long contentLength = request.getContentLength();
                if (contentLength==-1)
                	contentLength=Long.parseLong(request.getHeader("x-expected-entity-length"));
		        
		        Tracker tracker = createTracker(contentLength);
		      	
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
		                	for (int i = 0; i < SEPARATORS.length; i++) {  // Make sure we remove any absolute portion of path for all platforms
		                		int j = fileName.lastIndexOf(SEPARATORS[i]); 
		                		if (j >= 0)
		                			fileName = fileName.substring(j+1);
		                	}
	                        file = getRemoteFile(file.getAbsolutePath()+file.getPathSeparator()+fileName, davisSession);
	                        boolean existsCurrently = file.exists();
	                        if (existsCurrently && !file.isFile()) {
	                        	Log.log(Log.WARNING, file.getAbsolutePath()+" already exists on server");
	            	            str.append("<html><body><textarea>{\"status\":\"failed\", \"message\":\"File already exists\"}</textarea></body></html>");
	                        } else {	                        
		                		if (davisSession.getCurrentResource() == null) 
		                			davisSession.setCurrentResource(davisSession.getDefaultResource());
		                        RemoteFileOutputStream stream = null;
		                    	Log.log(Log.DEBUG, "saving file "+file.getAbsolutePath()+" into res:"+davisSession.getCurrentResource());
		                        if (file.getFileSystem() instanceof SRBFileSystem) {
		                        	((SRBFile)file).setResource(davisSession.getCurrentResource());
		                        	stream = new SRBFileOutputStream((SRBFile)file);
		                        }else if (file.getFileSystem() instanceof IRODSFileSystem) {
		                        	stream = new IRODSFileOutputStream((IRODSFile)file);
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
			                        str.append("<html><body><textarea>{\"status\":\"success\",\"message\":\""+tracker.getBytesReceived()+"\"}</textarea></body></html>");
			                        result = true;
			                    } 
	                        }
		                }
		            }
		            if (!result) 
		            	str.append("<html><body><textarea>{\"status\":\"failed\", \"message\":\"No file to upload\"}</textarea></body></html>");
		        } catch (EOFException e) {
                    str.append("<html><body><textarea>{\"status\":\"failed\", \"message\":\"Unexpected end of file\"}</textarea></body></html>");
		        } catch (IOException e) {
                    str.append("<html><body><textarea>{\"status\":\"failed\", \"message\":\""+e.getMessage()+"\"}</textarea></body></html>");
		        } catch (FileUploadException e) {
	                str.append("<html><body><textarea>{\"status\":\"failed\", \"message\":\""+e.getMessage()+"\"}</textarea></body></html>");
		        }
	        }
		} else if (method.equalsIgnoreCase("domains")) {
			str.append("{\nitems:[\n");
			String[] domains=FSUtilities.getDomains((SRBFileSystem)davisSession.getRemoteFileSystem());
			for (int i = 0; i < domains.length; i++) {
				if (i>0) str.append(",\n");
				str.append("{name:'").append(domains[i]).append("'}");
			}
			str.append("\n");
			str.append("]}");
		} else if (method.equalsIgnoreCase("resources")) {
			str.append("{\nitems:[\n");
			String[] resources = null;
			if (davisSession.getRemoteFileSystem() instanceof SRBFileSystem)
				resources = FSUtilities.getSRBResources((SRBFileSystem)file.getFileSystem());
			else
				resources = FSUtilities.getIRODSResources((IRODSFileSystem)file.getFileSystem());
			for (int i = 0; i < resources.length; i++) { 
				if (i > 0) str.append(",\n");
				str.append("{name:'").append(resources[i]).append("'}");
			}
			str.append("\n");
			str.append("]}");
		} else if (method.equalsIgnoreCase("replicas")) {
			String deleteResource = request.getParameter("delete");
			String replicateResource = request.getParameter("replicate");
	    	ArrayList<RemoteFile> fileList = new ArrayList<RemoteFile>();
	    	/*boolean batch = */getFileList(request, davisSession, fileList); 
	        Iterator<RemoteFile> iterator = fileList.iterator();
			str.append("{\nitems:[\n");
	        while (iterator.hasNext()) {
	        	file = iterator.next();
	        	if (!file.isDirectory()) {	// Can't get replica info for a directory
	        		if (deleteResource != null) {
	        			Log.log(Log.DEBUG, "Deleting replica at "+deleteResource);
                        if (file.getFileSystem() instanceof SRBFileSystem) {
    	        			Log.log(Log.DEBUG, "Not currently supported by Jargon");
                        }else if (file.getFileSystem() instanceof IRODSFileSystem) {
                        	((IRODSFile)file).deleteReplica(deleteResource);	//Currently ALWAYS returns false!
    	        			Log.log(Log.DEBUG, "Not currently working in Jargon");
                        }
	        		}
	        		if (replicateResource != null) {
	        			Log.log(Log.DEBUG, "Replicating to "+replicateResource);
                        if (file.getFileSystem() instanceof SRBFileSystem) {
 //   	        			Log.log(Log.DEBUG, "Not currently supported by Jargon");
                     ((SRBFile)file).replicate(replicateResource);	
                        } else if (file.getFileSystem() instanceof IRODSFileSystem) {
                        	try {
                        		((IRODSFile)file).replicate(replicateResource);
                        	} catch (IRODSException e) {
                    			Log.log(Log.DEBUG, "Replicate failed: "+e.getMessage());
                    			response.sendError(HttpServletResponse.SC_FORBIDDEN);
                    			return;
                        	}
                        	//   	        			Log.log(Log.DEBUG, "Not currently working in Jargon");
                        }
	        		}
	        		
	        		// Now get replica info
		    		MetaDataRecordList[] rl = null;
		    		String[] selectFieldNames = new String[] {FileMetaData.FILE_REPLICA_NUM, ResourceMetaData.RESOURCE_NAME};
		    		MetaDataSelect selects[] = MetaDataSet.newSelection(selectFieldNames);
		    		rl = file.query(selects);
		    		if (rl != null)
			    		for (int i = 0; i < rl.length; i++) {
							if (i > 0) str.append(",\n");
							str.append("{resource:'").append(rl[i].getValue(ResourceMetaData.RESOURCE_NAME)).append("', ");
							str.append("number:'").append(rl[i].getValue(FileMetaData.FILE_REPLICA_NUM)).append("'}");
			    		}
	        	}
	        }
			str.append("\n");
			str.append("]}");
		} else if (method.equalsIgnoreCase("userlist")) {
			str.append("{\nitems:[\n");
			if (davisSession.getRemoteFileSystem() instanceof SRBFileSystem){
				String[] users=FSUtilities.getUsernamesByDomainName((SRBFileSystem)davisSession.getRemoteFileSystem(),request.getParameter("domain"));
				for (int i = 0; i < users.length; i++) {
					if (i>0) str.append(",\n");
					str.append("{name:'").append(users[i]).append("'}");
				}
			}else if (davisSession.getRemoteFileSystem() instanceof IRODSFileSystem){
				String[] users=FSUtilities.getUsernames((IRODSFileSystem)davisSession.getRemoteFileSystem());
				for (int i = 0; i < users.length; i++) {
					if (i>0) str.append(",\n");
					str.append("{name:'").append(users[i]).append("'}");
				}
			}
			str.append("\n");
			str.append("]}");
		}
		ServletOutputStream op = null;
		try {
			 op = response.getOutputStream();
		} catch (EOFException e) {
			Log.log(Log.WARNING, "EOFException when preparing to send servlet response - client probably disconnected");
			return;
		}
		byte[] buf = str.toString().getBytes();
		Log.log(Log.DEBUG, "output(" + buf.length + "):\n" + str);
		op.write(buf);
		op.flush();
		op.close();
	}
	
	private boolean isPermInherited(RemoteFile file) throws IOException 
    {

            String[] selectFieldNames = {
                    SRBMetaDataSet.DIRECTORY_LINK_NUMBER,
            };

            MetaDataSelect inheritanceQuery[] = MetaDataSet.newSelection( selectFieldNames );
            MetaDataRecordList[] list = file.query(inheritanceQuery);

            if(list != null)
            {
                MetaDataRecordList r = list[0];
                String result  = r.getValue(r.getFieldIndex(SRBMetaDataSet.DIRECTORY_LINK_NUMBER)).toString();
                return result.equals("1");
            }

            return false;
    }
	private void setSticky(RemoteFile file, boolean flag, boolean recursive) throws IOException{
		if (file.getFileSystem() instanceof SRBFileSystem) {
			MetaDataField mdf=SRBMetaDataSet.getField(SRBMetaDataSet.DIRECTORY_LINK_NUMBER);
			MetaDataRecordList rl = new SRBMetaDataRecordList(mdf,String.valueOf(flag));
			file.modifyMetaData(rl);
		} else if (file.getFileSystem() instanceof IRODSFileSystem) {
			((IRODSFile)file).changePermissions(flag?"inherit":"noinherit", "", recursive);
		}	
	}
	
	
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
