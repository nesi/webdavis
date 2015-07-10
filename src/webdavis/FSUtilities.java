package webdavis;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.Vector;

import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.connection.auth.AuthResponse;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.pub.DataObjectAO;
import org.irods.jargon.core.pub.IRODSFileSystem;
import org.irods.jargon.core.pub.ResourceAO;
import org.irods.jargon.core.pub.UserAO;
import org.irods.jargon.core.pub.domain.Resource;
import org.irods.jargon.core.pub.domain.User;
import org.irods.jargon.core.pub.io.IRODSFile;
import org.irods.jargon.core.query.MetaDataAndDomainData;
import org.irods.jargon.ticket.TicketAdminService;

/**
 * Utilities for SRB/iRODS
 * 
 * @author Shunde Zhang
 *
 */
public class FSUtilities {
	
	public static SimpleDateFormat dateFormat = new SimpleDateFormat("E dd MMM yyyy HH:mm:ss z");

	private static final boolean[] ESCAPED;

    static {
        ESCAPED = new boolean[128];
        for (int i = 0; i < 128; i++) {
            ESCAPED[i] = true;
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            ESCAPED[i] = false;
        }
        for (int i = 'a'; i <= 'z'; i++) {
            ESCAPED[i] = false;
        }
        for (int i = '0'; i <= '9'; i++) {
            ESCAPED[i] = false;
        }
        ESCAPED['+'] = false;
        ESCAPED['-'] = false;
        ESCAPED['='] = false;
        ESCAPED['.'] = false;
        ESCAPED['_'] = false;
        ESCAPED['*'] = false;
        ESCAPED['('] = false;
        ESCAPED[')'] = false;
        ESCAPED[','] = false;
        ESCAPED['@'] = false;
        ESCAPED['\''] = false;
        ESCAPED['$'] = false;
        ESCAPED[':'] = false;
        ESCAPED['&'] = false;
        ESCAPED['!'] = false;
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public static String escape(String name) throws IOException {
        boolean dir = name.endsWith("/");
        if (dir) name = name.substring(0, name.length() - 1);
        StringBuffer buffer = new StringBuffer();
        char[] chars = name.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] > 0x7f || ESCAPED[chars[i]]) {
                byte[] bytes = new String(chars, i, 1).getBytes("UTF-8");
                for (int j = 0; j < bytes.length; j++) {
                    buffer.append("%");
                    buffer.append(Integer.toHexString((bytes[j] >> 4) & 0x0f));
                    buffer.append(Integer.toHexString(bytes[j] & 0x0f));
                }
            } else {
                buffer.append(chars[i]);
            }
        }
        if (dir) buffer.append("/");
        return buffer.toString();
    }

	public static String[] getIRODSResources(DavisSession davisSession) throws IOException {
		return getIRODSResources(davisSession,davisSession.getIRODSAccount().getZone());
	}
	public static String[] getIRODSResources(DavisSession davisSession, String currentZone) throws IOException {
		try {
			List<String> resourceNameList = davisSession.getResourceAO().listResourceNames();
			return resourceNameList.toArray(new String[resourceNameList.size()]);
		} catch (JargonException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new IOException(e.getMessage());
		}
	 }	 

	public static HashMap<String, FileMetadata> getIRODSCollectionMetadata(DavisSession davisSession, IRODSFile collection){
		
		return getIRODSCollectionMetadata(davisSession, collection, null);
	}
		
	public static HashMap<String, FileMetadata> getIRODSCollectionMetadata(DavisSession davisSession, IRODSFile collection, String attrName){

		HashMap<String, FileMetadata> results = new HashMap<String, FileMetadata>();
		List<MetaDataAndDomainData> metadatas;
		String path;
		try {
	        DataObjectAO dataObjectAO=davisSession.getDataObjectAO();
			metadatas = dataObjectAO.findMetadataValuesForDataObject(collection);
			for (MetaDataAndDomainData metadata:metadatas){
				if (attrName!=null&&!metadata.getAvuAttribute().equalsIgnoreCase(attrName)) continue;
				path=collection.getAbsolutePath();
    			FileMetadata mdata = results.get(path);
    			if (mdata == null) {
	    			mdata = new FileMetadata(dataObjectAO.findByAbsolutePath(path));
	    			results.put(path, mdata);
    			}
    			mdata.addItem(metadata.getAvuAttribute(), metadata.getAvuValue());
			}
    		Log.log(Log.DEBUG, "IRODSCollectionMetadata for file '"+collection.getAbsolutePath()+"' for user '"+davisSession.getIRODSAccount().getUserName()+"': \n"+results);
    		return results;
		}catch (JargonException e){
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
		
	}
	
	public static CachedFile[] getIRODSCollectionDetails(DavisSession davisSession, IRODSFile file){
		
		return getIRODSCollectionDetails(davisSession, file, true, true, false);
	}

	public static CachedFile[] getIRODSCollectionDetails(DavisSession davisSession, IRODSFile collection, boolean sort, boolean getFiles, boolean getMetadata){
		
		// For files with multiple replicas, a clean replica will be returned in the result. If only a dirty copy is found, then that will be used.
		HashMap<String, FileMetadata> metadata = null;
		if (getMetadata)
			metadata = getIRODSCollectionMetadata(davisSession, collection);
		Log.log(Log.DEBUG, "getIRODSCollectionDetails '"+collection.getAbsolutePath()+"' for "+davisSession.getIRODSAccount().getUserName());
		File[] files=collection.listFiles();
		CachedFile[] cacheFiles=new CachedFile[files.length];
		Comparator<Object> comparator = null;
		if (sort)
			comparator= new Comparator<Object>() {
			public int compare(Object file1, Object file2) {
				return (((File)file1).getName().toLowerCase().compareTo(((File)file2).getName().toLowerCase()));
				}     			
			};
		File file;
		if (sort)
			Arrays.sort((Object[])files, comparator);
		TicketAdminService ticketAdminService = null;
		try {
			ticketAdminService=davisSession.getTicketAdminService();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		for (int i=0;i<files.length;i++) {
			file=files[i];
			cacheFiles[i]=new CachedFile(file);
			if (getMetadata && metadata != null) {
				if (metadata.containsKey(file.getAbsolutePath())) 
					cacheFiles[i].setMetadata(metadata.get(file.getAbsolutePath()).getMetadata());
				try {
					if (file.isDirectory())
						cacheFiles[i].setTickets(ticketAdminService.listAllTicketsForGivenCollection(file.getAbsolutePath(), 0));
					else
						cacheFiles[i].setTickets(ticketAdminService.listAllTicketsForGivenDataObject(file.getAbsolutePath(), 0));
				} catch (org.irods.jargon.core.exception.FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (JargonException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		}
		return cacheFiles;
	}


	/**
	 * Test an iRODS session connection
	 * 
	 * @param davisSession
	 * @return String Null if connection is ok, an exception message if not.
	 */
	public static String testConnection(final DavisSession davisSession) {
		
		
				String message = null;
				try {
					davisSession.currentConnection().getIRODSServerProperties();
				} catch (ProtocolException e) {
					message = e.getMessage();
					if (message == null)
						message = "ProtocolException";
				} catch (SocketException e) {
					message = e.getMessage();
					if (message == null)
						message = "SocketException";
				} catch (Exception e) {
					message = e.getMessage();
					if (message == null)
						message = "Exception";
					Log.log(Log.WARNING, "Jargon exception when testing for connection: "+e+DavisUtilities.getStackTrace(e));					
				}
				return message;
	}
	
	public static String escapeJSONArg(String s) {
		return "\""+escapeJSON(s)+"\"";
	}

	public static String escapeJSON(String s) {
    	return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
	}
	
	public static String generateJSONFileListing(CachedFile[] fileList, IRODSFile collection, Comparator<Object> comparator, String requestUIHandle, int start, int count, boolean directoriesOnly, boolean directoryListing, boolean truncated, int totalResults) throws IOException {
		
		StringBuffer json = new StringBuffer();
		boolean emptyDir = (fileList.length == 0);
		if (!emptyDir && comparator != null)
			Arrays.sort((Object[]) fileList, comparator);

//		if (!directoryListing && start == 0)
//			start = -1;
		json.append("{\n"+escapeJSONArg("numRows")+":"+escapeJSONArg(""+(fileList.length+(directoryListing ? 1:0)+(emptyDir ? 1:0)))+",");
		if (truncated)
			json.append(escapeJSONArg("truncated")+":"+escapeJSONArg("true")+",");
		if (totalResults > -1)
			json.append(escapeJSONArg("totalResults")+":"+escapeJSONArg(""+totalResults)+",");
		if (requestUIHandle == null)
			requestUIHandle = "null";
		json.append(escapeJSONArg("uiHandle")+":"+escapeJSONArg(requestUIHandle)+",");
		if (collection != null)
			json.append(escapeJSONArg("readOnly")+":"+escapeJSONArg(""+!collection.canWrite())+",");
		json.append(escapeJSONArg("items")+":[\n");
		if (directoryListing && start == 0) {
			json.append("{\"name\":{\"name\":\"... Parent Directory\",\"type\":\"top\",\"parent\":\"..\"},"
						+ "\"date\":{\"value\":\"0\",\"type\":\"top\"},"
						+ "\"size\":{\"size\":\"0\",\"type\":\"top\"},"
						+ "\"sharing\":{\"value\":\"\",\"type\":\"top\"},"
						+ "\"metadata\":{\"value\":\"\",\"type\":\"top\"}}");
			count--;
		}
		if (directoryListing && start > 0)
			start--;
		for (int i = start; i < start + count; i++) {
			if (i >= fileList.length)
				break;
			if ((directoryListing && start == 0) || i > start)
				json.append(",\n");
			String type = fileList[i].isDirectory() ? "d" : "f";
			json.append("{\"name\":{\"name\":"+"\""+FSUtilities.escape(fileList[i].getName())+"\""+",\"type\":"+escapeJSONArg(type)+",\"parent\":"+"\""+escape/*JSONArg*/(fileList[i].getParent())+"\""+"}"
					+",\"date\":{\"value\":"+escapeJSONArg(dateFormat.format(fileList[i].lastModified()))+",\"type\":"+escapeJSONArg(type)+"},"
					+"\"size\":{\"size\":"+escapeJSONArg(""+fileList[i].length())+",\"type\":"+escapeJSONArg(type)+"},"
					+"\"sharing\":{\"value\":"+escapeJSONArg(/*sharingValue*/fileList[i].getSharingValue())+",\"type\":"+escapeJSONArg(type)+"},"
					+"\"metadata\":{\"values\":[");

			HashMap<String, ArrayList<String>> metadata = fileList[i].getMetadata();
			if (metadata != null) {
				json.append("\n");
				String[] names = metadata.keySet().toArray(new String[0]);
				for (int j = 0; j < names.length; j++) {
					if (j > 0)
						json.append(",\n");
					String name = names[j];
					ArrayList<String> values = metadata.get(name);
					for (int k = 0; k < values.size(); k++) {
						if (k > 0)
							json.append(",\n");
						json.append("    {"+escapeJSONArg("name")+":"+escapeJSONArg(name)+","+escapeJSONArg("value")+":"
								+escapeJSONArg(values.get(k))+"}");
					}
				}
			}
			json.append("]");
			if (metadata != null)
				json.append("\n    ");
			json.append(",\"type\":" + escapeJSONArg(type) + "}}");
		}
		if (emptyDir) {
			if (directoryListing)
				json.append(",\n");
			json.append("{\"name\":{\"name\":\""	+ (!directoryListing ? "(No matches)" : "("+(directoriesOnly?"No directories found":"Directory is empty")+")")
							+ "\",\"type\":\"bottom\",\"parent\":\"\"}," + "\"date\":{\"value\":\"0\",\"type\":\"bottom\"},"
							+ "\"size\":{\"size\":\"0\",\"type\":\"bottom\"},"
							+ "\"sharing\":{\"value\":\"\",\"type\":\"bottom\"},"
							+ "\"metadata\":{\"value\":\"\",\"type\":\"bottom\"}}");
		}
		json.append("\n]}");
//System.err.println("!!!!!!!!!returning json:"+json);
		return json.toString();
	}
	
	final static Integer lock = new Integer(0);
	
	public static void establishIRODSFileSystemConnection(IRODSAccount account) throws IOException {
		IRODSFileSystem irodsFileSystem;
		try {
			irodsFileSystem = IRODSFileSystem.instance();
			synchronized (lock) {
	            // Jargon Classic API 3.0 changed it to be final.
				// IRODSConstants.CONNECTION_TIMEOUT_VALUE = timeout;
				AuthResponse authResponse=irodsFileSystem.getIRODSAccessObjectFactory().authenticateIRODSAccount(account);
				Log.log(Log.DEBUG, "irods auth response:"+authResponse.getStartupResponse());
				Log.log(Log.DEBUG, "irods auth successful? "+authResponse.isSuccessful());
			}
		} catch (JargonException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new IOException(e.getMessage());
		}

	}


	public static String[] getUsernames(DavisSession davisSession) throws IOException{
		UserAO userAO;
		try {
			userAO = davisSession.getUserAO();
			List<User> users=userAO.findAll();
			String[] usernames=new String[users.size()];
			for (int i=0;i<users.size();i++) {
				usernames[i]=users.get(i).getName();
				if (!users.get(i).getZone().equals(davisSession.getIRODSAccount().getZone())) 
					usernames[i] += "#"+users.get(i).getZone();
			}
			return usernames;
		} catch (JargonException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new IOException(e.getMessage());
		}
	}
}
