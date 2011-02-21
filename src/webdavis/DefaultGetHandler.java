package webdavis;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ProtocolException;
import java.net.SocketException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerException;

import javax.xml.transform.dom.DOMSource;

import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;

import edu.sdsc.grid.io.GeneralFile;
import edu.sdsc.grid.io.GeneralFileSystem;
import edu.sdsc.grid.io.GeneralMetaData;
import edu.sdsc.grid.io.MetaDataCondition;
import edu.sdsc.grid.io.MetaDataRecordList;
import edu.sdsc.grid.io.MetaDataSelect;
import edu.sdsc.grid.io.MetaDataSet;
import edu.sdsc.grid.io.Namespace;
import edu.sdsc.grid.io.RemoteFile;
import edu.sdsc.grid.io.RemoteFileInputStream;
import edu.sdsc.grid.io.RemoteRandomAccessFile;
import edu.sdsc.grid.io.ResourceMetaData;
import edu.sdsc.grid.io.irods.IRODSAccount;
import edu.sdsc.grid.io.irods.IRODSException;
import edu.sdsc.grid.io.irods.IRODSFile;
import edu.sdsc.grid.io.irods.IRODSFileInputStream;
import edu.sdsc.grid.io.irods.IRODSFileSystem;
import edu.sdsc.grid.io.irods.IRODSMetaDataSet;
import edu.sdsc.grid.io.irods.IRODSRandomAccessFile;
import edu.sdsc.grid.io.srb.SRBFile;
import edu.sdsc.grid.io.srb.SRBFileInputStream;
import edu.sdsc.grid.io.srb.SRBFileSystem;
import edu.sdsc.grid.io.srb.SRBRandomAccessFile;

/**
 * Default implementation of a handler for requests using the HTTP GET method.
 * <p>
 * In addition to providing standard GET functionality for resources, this
 * implementation provides directory listings for collections. An XSL stylesheet
 * can be specified to customize the appearance of the listing. The default
 * stylesheet location is provided in the Davenport servlet's deployment
 * descriptor as the "directory.xsl" initialization parameter, i.e.:
 * <p>
 * 
 * <pre>
 * &lt;init-param&gt;
 *     &lt;param-name&gt;directory.xsl&lt;/param-name&gt;
 *     &lt;param-value&gt;/mydir.xsl&lt;/param-value&gt;
 * &lt;/init-param&gt;
 * </pre>
 * <p>
 * The stylesheet location is resolved as follows:
 * <ul>
 * <li>
 * First, the system will look for the stylesheet as a servlet context resource
 * (via <code>ServletContext.getResourceAsStream()</code>).</li>
 * <li>
 * Next, the system will attempt to load the stylesheet as a classloader
 * resource (via <code>ClassLoader.getResourceAsStream()</code>), using the
 * Davenport classloader, the thread context classloader, and the system
 * classloader (in that order).</li>
 * <li>
 * Finally, the system will attempt to load the stylesheet directly. This will
 * only succeed if the location is specified as an absolute URL.</li>
 * </ul>
 * <p>
 * If not specified, this is set to "<code>/META-INF/directory.xsl</code>",
 * which will load a default stylesheet from the Davenport jarfile.
 * <p>
 * Users can also configure their own directory stylesheets. The configuration
 * page can be accessed by pointing your web browser at any Davenport collection
 * resource and passing "configure" as a URL parameter:
 * </p>
 * <p>
 * <code>http://server/davis/any/?configure</code>
 * </p>
 * <p>
 * The configuration page can be specified in the deployment descriptor via the
 * "directory.configuration" initialization parameter, i.e.:
 * <p>
 * 
 * <pre>
 * &lt;init-param&gt;
 *     &lt;param-name&gt;directory.configuration&lt;/param-name&gt;
 *     &lt;param-value&gt;/configuration.html&lt;/param-value&gt;
 * &lt;/init-param&gt;
 * </pre>
 * <p>
 * The configuration page's location is resolved in the same manner as the
 * default stylesheet described above.
 * <p>
 * If not specified, this is set to "<code>/META-INF/configuration.html</code>",
 * which will load and cache a default configuration page from the Davenport
 * jarfile.
 * <p>
 * Both the stylesheet and configuration page will attempt to load a resource
 * appropriate to the locale; the loading order is similar to that used by
 * resource bundles, i.e.:
 * <p>
 * directory_en_US.xsl <br>
 * directory_en.xsl <br>
 * directory.xsl
 * <p>
 * The client's locale will be tried first, followed by the server's locale.
 * 
 * @author Shunde Zhang
 * @author Eric Glass
 */
public class DefaultGetHandler extends AbstractHandler {

	private static final Timer TIMER = new Timer(true);
	private final long instanceID = new Date().getTime(); // ID for this servlet instance (its creation date) - for debugging

	private final Map<String, TemplateTracker> templateMap = new HashMap<String, TemplateTracker>();
	private final Map<Locale, Templates> defaultTemplates = new HashMap<Locale, Templates>();
	private final Map<Locale, byte[]> configurations = new HashMap<Locale, byte[]>();

	private String stylesheetLocation;
	private String configurationLocation;
	private String UIHTMLLocation;

	private PropertiesBuilder propertiesBuilder;
	private String defaultUIHTMLContent;
	private String uiLoadDate = "";
	private SimpleDateFormat dateFormat = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
	private boolean sortAscending = true;
	private String sortField = "name";

	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		propertiesBuilder = new DefaultPropertiesBuilder();
		propertiesBuilder.init(config);
		stylesheetLocation = config.getInitParameter("directory.xsl");
		if (stylesheetLocation == null) 
			stylesheetLocation = "/META-INF/directory.xsl";
		configurationLocation = config.getInitParameter("directory.configuration");
		if (configurationLocation == null) 
			configurationLocation = "/META-INF/configuration.html";
		UIHTMLLocation = config.getInitParameter("ui.html");
		if (UIHTMLLocation == null) 
			// UIHTMLLocation = "/META-INF/ui.html";
			UIHTMLLocation = "/WEB-INF/ui.html";
		
		// Load UI html into a string so that subsequent requests can use that
		// directly
		defaultUIHTMLContent = loadUI(UIHTMLLocation);
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
	}
	
	private String loadUI(String fileName) {

//		String result = "";
//		try {
//			InputStream stream = DavisUtilities.getResourceAsStream(fileName);
//			if (stream == null)
//				throw new IOException("can't open file");
//			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
//			char[] buffer = new char[1024];
//			int numRead = 0;
//			while ((numRead = reader.read(buffer)) != -1) {
//				String readData = String.valueOf(buffer, 0, numRead);
//				result += (readData);
//			}
//			reader.close();
//		} catch (IOException e) {
//			Log.log(Log.CRITICAL, "Failed to read UI html file: " + e);
//		}
		uiLoadDate = dateFormat.format(new Date());
		return /*result*/DavisUtilities.loadResource(fileName);
	}

	public void destroy() {
		propertiesBuilder.destroy();
		propertiesBuilder = null;
		stylesheetLocation = null;
		UIHTMLLocation = null;
		synchronized (defaultTemplates) {
			defaultTemplates.clear();
		}
		synchronized (configurations) {
			configurations.clear();
		}
		super.destroy();
	}

	Comparator<Object> comparator = new Comparator<Object>() {
		public int compare(Object file1, Object file2) {

			if (sortField.equals("name")) { // File name column
				if (((GeneralFile) file1).isDirectory()	&& !((GeneralFile) file2).isDirectory()) // Keep directories separate from files
					return -1 * (sortAscending ? 1 : -1);
				if (!((GeneralFile) file1).isDirectory() && ((GeneralFile) file2).isDirectory())
					return (sortAscending ? 1 : -1);
				return (((GeneralFile) file1).getName().toLowerCase().compareTo(((GeneralFile) file2).getName().toLowerCase()))	* (sortAscending ? 1 : -1);
			} else if (sortField.equals("size")) {
				if (((GeneralFile) file1).isDirectory()	&& !((GeneralFile) file2).isDirectory()) // Keep directories separate from files
					return -1 * (sortAscending ? 1 : -1);
				if (!((GeneralFile) file1).isDirectory() && ((GeneralFile) file2).isDirectory())
					return (sortAscending ? 1 : -1);
				return (new Long(((GeneralFile) file1).length()).compareTo(new Long(((GeneralFile) file2).length()))) * (sortAscending ? 1 : -1);
			} else if (sortField.equals("date")) {
				return (new Long(((GeneralFile) file1).lastModified()).compareTo(new Long(((GeneralFile) file2).lastModified()))) * (sortAscending ? 1 : -1);
			}

			return 0; // ###TBD comparator for metadata
		}
	};

	/**
	 * Services requests which use the HTTP GET method. This implementation
	 * retrieves the content for non-collection resources, using the content
	 * type information mapped in {@link smbdav.DavisUtilities}. For collection
	 * resources, the collection listing is retrieved as from a PROPFIND request
	 * with a depth of 1 (the collection and its immediate contents). The
	 * directory listing stylesheet is applied to the resultant XML document. <br>
	 * If the specified file does not exist, a 404 (Not Found) error is sent to
	 * the client.
	 * 
	 * @param request
	 *            The request being serviced.
	 * @param response
	 *            The servlet response.
	 * @param auth
	 *            The user's authentication information.
	 * @throws ServletException
	 *             If an application error occurs.
	 * @throws IOException
	 *             If an IO error occurs while handling the request.
	 * 
	 */
	public void service(HttpServletRequest request, HttpServletResponse response, DavisSession davisSession) throws ServletException, IOException {
//		if (Log.getThreshold() <= Log.DEBUG) { // Skip the request.toString below if not debugging cos jetty 6 throws an exception sometimes
//			String r = "";
//			try {
//				r = request.toString();
//			} catch (Exception e) {
//				r = "jetty error: "+e.getMessage();
//			}
//			Log.log(Log.DEBUG, ("=============== id="+instanceID+" request="+r));
//		}
		String url = getRemoteURL(request, getRequestURL(request), getRequestURICharset());
		// Check for non remote-server requests (eg dojo files) and return them if so
		if (url.startsWith("/dojoroot") || url.startsWith("/applets.jar")) {
			Log.log(Log.DEBUG, "Returning contents of " + url);
			writeFile(url, request, response);
			return;
		}
		RemoteFile file = null;
		try {
			file = getRemoteFile(request, davisSession);
		} catch (NullPointerException e) {
			Log.log(Log.CRITICAL, "Caught a NullPointerException in DefaultGethandler.service. request=" + request.getRequestURI() + " session=" + davisSession
							+ "\nAborting request. Exception is:"+DavisUtilities.getStackTrace(e));
			internalError(response, "Jargon error in DefaultGethandler.service");
			return;
		}
		Log.log(Log.DEBUG, "GET Request for resource \"{0}\".", file.getAbsolutePath());

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
		
		if (!file.exists()) { // File doesn't exist
//			try {
//				boolean connected = true;
				String message = null;
//				if (davisSession.getRemoteFileSystem() instanceof IRODSFileSystem) {
//					try {
//						((IRODSFileSystem)davisSession.getRemoteFileSystem()).miscServerInfo();
//					} catch (ProtocolException e) {
//						connected = false;
//						message = e.getMessage();
//					} catch (SocketException e) {
//						connected = false;
//						message = e.getMessage();
//					} catch (Exception e) {
//						Log.log(Log.WARNING, "Jargon exception when testing for connection (get): "+e);					
//					}
					message = FSUtilities.testConnection(davisSession);
//				}
//				try {  //### Not needed anymore because of above test?
//					file.getPermissions(); // Test server connection
//				} catch (SocketException e) {
//					connected = false;
//					message = e.getMessage();
//				} catch (IRODSException e) {
//					message = e.getMessage();
//           			if (message.contains("IRODS error occured -816000")) // Invalid Argument seems to indicate dropped connection too
//           				connected = false;
//				}
				if (message != null) {
					lostConnection(response, message);
					return;
				}
				Log.log(Log.WARNING, "File " + file.getAbsolutePath() + " does not exist or unknown server error.");					
				response.sendError(HttpServletResponse.SC_NOT_FOUND, "File " + file.getAbsolutePath() + " does not exist.");
				response.flushBuffer();
//			} catch (IOException e) {  //### Not needed anymore because of above test?
//				if (e.getMessage() != null && e.getMessage().equals("Closed"))
//					Log.log(Log.WARNING, file.getAbsolutePath() + ": connection to server may have been lost.");
//				throw (e);
//			}
			return;
		}
		
		// if (!file.canRead()){
		// response.sendError(HttpServletResponse.SC_FORBIDDEN,
		// "Resource not accessible.");
		// return;
		// }
		String requestUrl = getRequestURL(request);
		StringBuffer json = new StringBuffer();
		Log.log(Log.DEBUG, "Request URL: {0}", requestUrl);
		if (file.getName().endsWith("/") && !requestUrl.endsWith("/")) { // Redirect
			StringBuffer redirect = new StringBuffer(requestUrl).append("/");
			String query = request.getQueryString();
			if (query != null)
				redirect.append("?").append(query);
			Log.log(Log.INFORMATION, "Redirecting to \"{0}\".", redirect);
			response.sendRedirect(redirect.toString());
			return;
		}
		if (!file.isFile()) { // Directory
			Log.log(Log.DEBUG, "Time before creating dynamic html: " + (new Date().getTime() - Davis.profilingTimer.getTime()));
			String method = request.getParameter("method");
			if (method != null && method.equals("dojoquery")) { // Dojo QueryReadStore request (ie ajax directory listing)
				if (davisSession.getRemoteFileSystem() instanceof SRBFileSystem) {
					Log.log(Log.ERROR, "dojoquery method not implemented for SRB");
					return;
				}
				boolean noCache = false;
				if (request.getParameter("nocache") != null)
					noCache = true;				
				boolean directoriesOnly = false;
				if (request.getParameter("directoriesonly") != null)
					directoriesOnly = true;
				String requestUIHandle = null;
				if (request.getParameter("uihandle") != null) {
					requestUIHandle = request.getParameter("uihandle");//+(directoriesOnly?"dir":""); // Use separate cache for directoriesOnly query
					if (requestUIHandle.equals("null"))
						requestUIHandle = null;
				}
				if (requestUIHandle == null)
					requestUIHandle = ""+new Date().getTime();//+(directoriesOnly?"dir":""); // Use separate cache for directoriesOnly query
				
				// Request is like ?method=dojoquery&name=*&start=0&count=30&sort=name
				String sort = request.getParameter("sort");
				if (sort != null) {
					sortField = sort;
					sortAscending = true;
					if (sort.startsWith("-")) {
						sortAscending = false;
						sortField = sort.substring(1);
					}
				}
				String s = request.getParameter("start");
				int start = 0;
				if (s != null)
					start = Integer.parseInt(s);
				s = request.getParameter("count");
				int count = 0;
				if (s != null)
					count = Integer.parseInt(s);

				CachedFile[] fileList = null;
				ClientInstance client = davisSession.getClientInstance(requestUIHandle);
				if (client != null)
					fileList = client.getFileListCache();
				if (noCache || fileList == null) {
					Log.log(Log.DEBUG, "Fetching directory contents from irods");
					fileList = FSUtilities.getIRODSCollectionDetails(file, false, !directoriesOnly, !directoriesOnly);
					client = new ClientInstance();
					client.setFileListCache(fileList);
					davisSession.getClientInstances().put(requestUIHandle, client);
				} else
					Log.log(Log.DEBUG, "Fetching directory contents from cache");

				boolean emptyDir = (fileList.length == 0);
				if (!emptyDir)
					Arrays.sort((Object[]) fileList, comparator);

				json.append("{\n"+escapeJSONArg("numRows")+":"+escapeJSONArg(""+(fileList.length+1+(emptyDir ? 1 : 0)))+",");
				json.append(escapeJSONArg("uiHandle")+":"+escapeJSONArg(requestUIHandle)+",");
				json.append(escapeJSONArg("readOnly")+":"+escapeJSONArg(""+!file.canWrite())+",");
				json.append(escapeJSONArg("items")+":[\n");
				for (int i = start; i < start + count; i++) {
					if (i >= fileList.length + 1)
						break;
					if (i > start)
						json.append(",\n");
					if (i == 0) {json.append("{\"name\":{\"name\":\"... Parent Directory\",\"type\":\"top\"},"
										+ "\"date\":{\"value\":\"0\",\"type\":\"top\"},"
										+ "\"size\":{\"size\":\"0\",\"type\":\"top\"},"
										+ "\"sharing\":{\"value\":\"\",\"type\":\"top\"},"
										+ "\"metadata\":{\"value\":\"\",\"type\":\"top\"}}");
						continue;
					}
					String type = fileList[i-1].isDirectory() ? "d" : "f";
					HashMap<String, ArrayList<String>> metadata = fileList[i-1].getMetadata();
					String sharingValue = "";
					String sharingKey = Davis.getConfig().getSharingKey();
					if (metadata != null && sharingKey != null) {
						ArrayList<String> values = metadata.get(sharingKey);
						if (values != null)
							sharingValue = values.get(0);
					}
					json.append("{\"name\":{\"name\":"+"\""+FSUtilities.escape(fileList[i-1].getName())+"\""+",\"type\":"+escapeJSONArg(type)+"}"
							+",\"date\":{\"value\":"+escapeJSONArg(dateFormat.format(fileList[i-1].lastModified()))+",\"type\":"+escapeJSONArg(type)+"},"
							+"\"size\":{\"size\":"+escapeJSONArg(""+fileList[i-1].length())+",\"type\":"+escapeJSONArg(type)+"},"
							+"\"sharing\":{\"value\":"+escapeJSONArg(sharingValue)+",\"type\":"+escapeJSONArg(type)+"},"
							+"\"metadata\":{\"values\":[");

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
								json.append("    {" + escapeJSONArg("name") + ":" + escapeJSONArg(name) + "," + escapeJSONArg("value") + ":"
										+ escapeJSONArg(values.get(k)) + "}");
							}
						}
					}
					json.append("]");
					if (metadata != null)
						json.append("\n    ");
					json.append(",\"type\":" + escapeJSONArg(type) + "}}");
				}
				if (emptyDir) {
					boolean filtered = false;
					json.append(",\n{\"name\":{\"name\":\""	+ (filtered ? "(No matches)" : "("+(directoriesOnly?"No directories found":"Directory is empty")+")")
									+ "\",\"type\":\"bottom\"}," + "\"date\":{\"value\":\"0\",\"type\":\"bottom\"},"
									+ "\"size\":{\"size\":\"0\",\"type\":\"bottom\"},"
									+ "\"sharing\":{\"value\":\"\",\"type\":\"bottom\"},"
									+ "\"metadata\":{\"value\":\"\",\"type\":\"bottom\"}}");
				}
				json.append("\n]}");
				ServletOutputStream op = null;
				try {
					op = response.getOutputStream();
				} catch (EOFException e) {
					Log.log(Log.WARNING, "EOFException when preparing to send servlet response - client probably disconnected");
					return;
				}
				byte[] buf = json.toString().getBytes();
				Log.log(Log.DEBUG, "output(" + buf.length + "):\n" + json);
				response.setContentType("text/json; charset=\"utf-8\"");
				addNoCacheDirectives(response);
				op.write(buf);
				op.flush();
				op.close();

				Log.log(Log.DEBUG, "Time after creating dynamic json: " + (new Date().getTime() - Davis.profilingTimer.getTime()));
				return;
			}
			String format = request.getParameter("format");
			if (format != null && format.equals("json")) { // List directory contents as JSON
				RemoteFile[] fileList = FSUtilities.getIRODSCollectionDetails(file);
				json.append("{\n" + escapeJSONArg("items") + ":[\n");
				for (int i = 0; i < fileList.length; i++) {
					if (i > 0)
						json.append(",\n");
					String type = fileList[i].isDirectory() ? "d" : "f";
					json.append("{"	+ escapeJSONArg("name")	+ ":" + escapeJSONArg(fileList[i].getName()) + ","
							+ escapeJSONArg("type")	+ ":" + escapeJSONArg(type)	+ "," + escapeJSONArg("size") + ":"
							+ escapeJSONArg("" + fileList[i].length()) + "," + escapeJSONArg("date") + ":"
							+ escapeJSONArg(dateFormat.format(fileList[i].lastModified())) + "}");
				}
				json.append("\n]}");
				ServletOutputStream op = null;
				try {
					op = response.getOutputStream();
				} catch (EOFException e) {
					Log.log(Log.WARNING, "EOFException when preparing to send servlet response - client probably disconnected");
					return;
				}
				byte[] buf = json.toString().getBytes();
				Log.log(Log.DEBUG, "output(" + buf.length + "):\n" + json);
				response.setContentType("text/json; charset=\"utf-8\"");
				op.write(buf);
				op.flush();
				op.close();

				Log.log(Log.DEBUG, "Time after creating dynamic json: " + (new Date().getTime() - Davis.profilingTimer.getTime()));
				return;
			}
			if (request.getParameter("uiold") == null) { // Use new UI
				if (request.getParameter("reload-config") != null && Davis.getConfig().getAdministrators().contains(davisSession.getAccount())) {
					// If ?reload-config is added to url, then entire configuration will be reloaded.
					Log.log(Log.INFORMATION, "Reloading configuration");
					Davis.getConfig().refresh();
					Log.log(Log.INFORMATION, "Reloading ui from "+UIHTMLLocation);
					defaultUIHTMLContent = loadUI(UIHTMLLocation);
					addNoCacheDirectives(response);
					ServletOutputStream op = null;
					try {
						op = response.getOutputStream();
					} catch (EOFException e) {
						Log.log(Log.WARNING, "EOFException when preparing to send servlet response - client probably disconnected");
						return;
					}
					response.setContentType("text/html; charset=\"utf-8\"");
					op.println("<html><body><br><br><br><br><h2 style=\"text-align: center;\">Davis configuration was reloaded.</h2></body></html>");
					op.flush();
					op.close();
					return;
				}
//				if (request.getParameter("reload-ui") != null) {
//					// If ?reload-ui is added to url, then ui.html will be reloaded.
//					Log.log(Log.INFORMATION, "Reloading ui from "+UIHTMLLocation);
//					defaultUIHTMLContent = loadUI(UIHTMLLocation);
//				}
				String uiHTMLContent = defaultUIHTMLContent;
				if (request.getParameter("uidev") != null) {
					// Simple hack to allow loading of a development ui every time a request is received.
					// To use this, add '?uidev' to url and place a link in davis/webapps/root/WEB-INF called uidev.html pointing
					// to a ui.htlm being worked on. The big advantage of this is that if the only file being modified is ui.html
					// then Davis doesn't need to be rebuilt or reinstalled or the server restarted.
					String s = "/WEB-INF/uidev.html";
					Log.log(Log.DEBUG, "loading ui from " + s);
					uiHTMLContent = loadUI(s);
				}
				String dojoroot = Davis.getConfig().getDojoroot();
				if (dojoroot.indexOf("/") < 0)
					dojoroot = request.getContextPath() + "/" + dojoroot;
				Log.log(Log.DEBUG, "dojoroot:" + dojoroot);

				DavisConfig config = Davis.getConfig();

				// Define request specific substitutions for UI HTML file
				Hashtable<String, String> substitutions = new Hashtable<String, String>();
				substitutions.put("dojoroot", dojoroot);
				substitutions.put("servertype", getServerType());
				substitutions.put("href", requestUrl);
				substitutions.put("url", file.getAbsolutePath().replace("\\", "\\\\").replace("\"", "\\\"")); // Escape " chars - ui uses this string inside double quotes
				substitutions.put("unc", file.toString());
				substitutions.put("parent", request.getContextPath()+file.getParent());
				substitutions.put("home", davisSession.getHomeDirectory());
				substitutions.put("trash", davisSession.getTrashDirectory());
				substitutions.put("account", davisSession.getAccount());
				String auth = davisSession.getAuthenticationScheme(false).toLowerCase();
				if (auth == null || !auth.equals("shib"))
					auth = davisSession.getAuthenticationScheme(true).toLowerCase();
				substitutions.put("authscheme", /*davisSession.getAuthenticationScheme()*/auth);
				substitutions.put("uiloaddate", uiLoadDate);
				String version = "";
				if (file.getFileSystem() instanceof IRODSFileSystem) 
					version = ((IRODSFileSystem)file.getFileSystem()).getVersion();
				else
					version = ((SRBFileSystem)file.getFileSystem()).getVersion();
				substitutions.put("jargonversion", version);
				substitutions.put("disablereplicasbutton", ""+config.getDisableReplicasButton());
				substitutions.put("sharinguser", ""+config.getSharingUser());
				substitutions.put("sharingkey", ""+config.getSharingKey());
				substitutions.put("ghostbreadcrumb", ""+config.getGhostBreadcrumb());
				substitutions.put("ghosttrashbreadcrumb", ""+config.getGhostTrashBreadcrumb());
//				substitutions.put("includehead", ""+config.getUIIncludeHead());
//				substitutions.put("includebodyheader", ""+config.getUIIncludeBodyHeader());
//				substitutions.put("includebodyfooter", ""+config.getUIIncludeBodyFooter());
				substitutions.put("shibinitpath", ""+config.getShibInitPath());
				substitutions.put("isadmin", ""+Davis.getConfig().getAdministrators().contains(davisSession.getAccount()));
				String uiContent = new String(uiHTMLContent);
				uiContent = DavisUtilities.preprocess(uiContent, Davis.getConfig().getIncludeSubstitutions());	// Make 'include snippets' substitutions
				uiContent = DavisUtilities.preprocess(uiContent, Davis.getConfig().getGeneralSubstitutions());	// Make general substitutions
				uiContent = DavisUtilities.preprocess(uiContent, substitutions);				// Make request specific substitutions
				response.setContentType("text/html; charset=\"utf-8\"");
				OutputStreamWriter out = new OutputStreamWriter(response.getOutputStream());
				out.write(uiContent, 0, uiContent.length());
				out.flush();
				response.flushBuffer();
				return;
			}

			// Use old UI
			if ("configure".equals(request.getQueryString())) {
				Log.log(Log.INFORMATION, "Configuration request received.");
				showConfiguration(request, response);
				return;
			}
			String view = request.getParameter("view");
			if (view == null) {
				Cookie[] cookies = request.getCookies();
				if (cookies != null) {
					for (int i = cookies.length - 1; i >= 0; i--) {
						if (cookies[i].getName().equals("view")) {
							view = cookies[i].getValue();
							break;
						}
					}
				}
			} else {
				view = view.trim();
				Cookie cookie = new Cookie("view", view);
				cookie.setPath("/");
				if (view.equals("")) {
					view = null;
					HttpSession session = request.getSession(false);
					if (session != null)
						clearTemplates(session);
					cookie.setMaxAge(0);
				} else {
					cookie.setMaxAge(Integer.MAX_VALUE);
				}
				response.addCookie(cookie);
			}
			Locale locale = request.getLocale();
			Templates templates = getDefaultTemplates(locale);
			if (view != null) {
				Log.log(Log.DEBUG, "Custom view installed: {0}", view);
				templates = null;
				try {
					HttpSession session = request.getSession(false);
					if (session != null) {
						templates = getTemplates(session);
					}
					if (templates == null) {
						Source source = getStylesheet(view, false, locale);
						templates = TransformerFactory.newInstance().newTemplates(source);
						if (session == null)
							session = request.getSession(true);
						setTemplates(session, templates);
					}
				} catch (Exception ex) {
					Log.log(Log.WARNING, "Unable to install stylesheet: {0}", ex);
					HttpSession session = request.getSession(false);
					if (session != null)
						clearTemplates(session);
					showConfiguration(request, response);
					return;
				}
			}
			PropertiesDirector director = new PropertiesDirector(getPropertiesBuilder());
			Document properties = null;
			properties = director.getAllProperties(file, requestUrl, 1);

			try {
				Transformer transformer = templates.newTransformer();
				String dojoroot = Davis.getConfig().getDojoroot();
				if (dojoroot.indexOf("/") < 0) {
					// System.out.println(request.getPathInfo());
					// System.out.println(request.getRequestURI());
					// System.out.println(request.getContextPath());
					dojoroot = request.getContextPath() + "/" + dojoroot;
				}
				Log.log(Log.DEBUG, "dojoroot:" + dojoroot);
				transformer.setParameter("dojoroot", dojoroot);
				transformer.setParameter("servertype", getServerType());
				transformer.setParameter("href", requestUrl);
				transformer.setParameter("url", file.getAbsolutePath());
				transformer.setParameter("unc", file.toString());
				transformer.setParameter("parent", request.getContextPath()	+ file.getParent());
				transformer.setParameter("home", davisSession.getHomeDirectory());
				transformer.setParameter("trash", davisSession.getTrashDirectory());
				// String type;
				// switch (file.getType()) {
				// case SmbFile.TYPE_WORKGROUP:
				// type = "TYPE_WORKGROUP";
				// break;
				// case SmbFile.TYPE_SERVER:
				// type = "TYPE_SERVER";
				// break;
				// case SmbFile.TYPE_SHARE:
				// type = "TYPE_SHARE";
				// break;
				// case SmbFile.TYPE_FILESYSTEM:
				// type = "TYPE_FILESYSTEM";
				// break;
				// case SmbFile.TYPE_PRINTER:
				// type = "TYPE_PRINTER";
				// break;
				// case SmbFile.TYPE_NAMED_PIPE:
				// type = "TYPE_NAMED_PIPE";
				// break;
				// case SmbFile.TYPE_COMM:
				// type = "TYPE_COMM";
				// break;
				// default:
				// type = "TYPE_UNKNOWN";
				// }
				// transformer.setParameter("type", type);
				transformer.setOutputProperty("encoding", "UTF-8");
				ByteArrayOutputStream collector = new ByteArrayOutputStream();
				transformer.transform(new DOMSource(properties), new StreamResult(collector));
				response.setContentType("text/html; charset=\"utf-8\"");
				collector.writeTo(response.getOutputStream());
				response.flushBuffer();

				Log.log(Log.DEBUG, "Time after creating dynamic html: " + (new Date().getTime() - Davis.profilingTimer.getTime()));

			} catch (TransformerException ex) {
				throw new IOException(ex.getMessage());
			}
			return;
		}

		// Request for file
		// For files with multiple replicas, a clean replica will be returned. If only a dirty copy is found, then that will be used.
		if (file.getFileSystem() instanceof IRODSFileSystem) {	
			// Find first clean replica of file for download
			MetaDataCondition conditions[] = {
				MetaDataSet.newCondition(GeneralMetaData.DIRECTORY_NAME, MetaDataCondition.EQUAL, file.getParent()),
				MetaDataSet.newCondition(GeneralMetaData.FILE_NAME, MetaDataCondition.EQUAL, file.getName()),
				MetaDataSet.newCondition(IRODSMetaDataSet.FILE_REPLICA_STATUS, MetaDataCondition.EQUAL, "1"),
			};
			MetaDataSelect selects[] = MetaDataSet.newSelection(new String[]{
					IRODSMetaDataSet.RESOURCE_STATUS,
					IRODSMetaDataSet.RESOURCE_NAME
				});
			MetaDataRecordList[] fileDetails = (davisSession.getRemoteFileSystem()).query(conditions, selects);

    		if (fileDetails == null || fileDetails.length < 1) {
    			Log.log(Log.WARNING, "No clean replicas found for "+file.getAbsolutePath());
    		
    			// No clean replicas, try *any* replicas
    			conditions = new MetaDataCondition[] {
    					MetaDataSet.newCondition(GeneralMetaData.DIRECTORY_NAME, MetaDataCondition.EQUAL, file.getParent()),
    					MetaDataSet.newCondition(GeneralMetaData.FILE_NAME, MetaDataCondition.EQUAL, file.getName())
    				};
    			fileDetails = (davisSession.getRemoteFileSystem()).query(conditions, selects);
        		
    			if (fileDetails == null || fileDetails.length < 1) {
	    			String s= "Internal get request error - no replicas found: "+file.getAbsolutePath();
	    			Log.log(Log.ERROR, s+": "+file.getAbsolutePath());
	    			response.sendError(HttpServletResponse.SC_NOT_FOUND, s);
	    			response.flushBuffer();
	    			return;
    			}
    			Log.log(Log.WARNING, "Using dirty replica for "+file.getAbsolutePath());
    		}
    		
    		boolean found = false;
    		for (int i = 0; i < fileDetails.length; i++) {
        		MetaDataRecordList p = fileDetails[i]; 
        		String status = (String)p.getValue(IRODSMetaDataSet.RESOURCE_STATUS);
        		if (status == null)
        			Log.log(Log.WARNING, "status of resource "+p.getValue(IRODSMetaDataSet.RESOURCE_NAME)+" was null");
//  System.err.println("********** resource is "+p.getValue(IRODSMetaDataSet.RESOURCE_NAME)+"  status is "+p.getValue(IRODSMetaDataSet.RESOURCE_STATUS));
        		if (status == null || status.length() == 0 || status.toLowerCase().contains("up")) { // Empty status string = up
            		Log.log(Log.DEBUG, "setting resouce for get of "+file.getName()+" to "+p.getValue(IRODSMetaDataSet.RESOURCE_NAME));
            		try {
            			((IRODSFile)file).setResource((String)p.getValue(IRODSMetaDataSet.RESOURCE_NAME));
            		} catch (Exception e) {
            			Log.log(Log.ERROR, "failed to set resource for get of "+file.getName()+" to "+(String)p.getValue(IRODSMetaDataSet.RESOURCE_NAME)+": "+e);
            		}
            		found = true;
            		break;
        		}
    		}
    		if (!found) {
    			String s= "No replicas are available because a resource is down: "+file.getAbsolutePath();
    			Log.log(Log.ERROR, s);
    			response.sendError(HttpServletResponse.SC_NOT_FOUND, s);
    			response.flushBuffer();
    			return;
    		}
		}

		String etag = DavisUtilities.getETag(file);
		if (etag != null)
			response.setHeader("ETag", etag);
		long modified = file.lastModified();
		if (modified != 0) {
			response.setHeader("Last-Modified", DavisUtilities.formatGetLastModified(modified));
		}
		int result = checkConditionalRequest(request, file);
		if (result != HttpServletResponse.SC_OK) {
			response.sendError(result, "Request Error.");
			response.flushBuffer();
			return;
		}
		String contentType = getServletConfig().getServletContext().getMimeType(file.getName());
		response.setHeader("Content-Length", String.valueOf(file.length()));
		response.setContentType((contentType != null) ? contentType	: "application/octet-stream");
		response.setContentLength((int) file.length());
		// Don't send cache control stuff for IE. It has problems when 'getting'. 
		// See http://www.experts-exchange.com/Web_Development/Web_Languages-Standards/ASP/Q_22780724.html
		if (request.getHeader("User-Agent") != null && !request.getHeader("User-Agent").contains("MSIE ")) 
			addNoCacheDirectives(response);
		// RemoteFileInputStream input = null;
		String startingPoint = request.getHeader("Content-Range");
		long offset = 0;
		if (startingPoint == null)
			startingPoint = request.getHeader("Range");
		startingPoint = null;  // Disable ranges for now because Davis can't handle multiple ranges. FF uses them for (at least) PDF downloads and the download fails
		if (startingPoint != null) {
//			if (startingPoint.contains(",")) {
//				try {
//	//				Log.log(Log.DEBUG, "offset:" + offsetString);
//	//				offset = Long.parseLong(offsetString);
////					response.setHeader("Accept-Ranges", "none");
//					offset = 0;
//					response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
//					response.setHeader("Content-Range", offset + "-" + file.length() + "/" + file.length());
//					response.setContentLength((int) (file.length() - offset));
//				} catch (Exception _e) {
//				}
//			} else
				try {
					String offsetString = startingPoint.substring(startingPoint.indexOf("bytes") + 6, startingPoint.indexOf("-"));
					Log.log(Log.DEBUG, "offset:" + offsetString);
					offset = Long.parseLong(offsetString);
					response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
					response.setHeader("Content-Range", startingPoint.substring(0,6) + offset + "-" + file.length() + "/" + file.length());
					response.setContentLength((int) (file.length() - offset));
				} catch (Exception _e) {
				}
		} else {
			response.setHeader("Accept-Ranges", /*"bytes"*/"none");
		}
		long bufferSize = file.length() / 100;
		// minimum buf size of 50KiloBytes
		if (bufferSize < 51200)
			bufferSize = 51200;
		// maximum buf size of 5MegaByte
		else if (bufferSize > 5242880)
			bufferSize = 5242880;
		byte[] buf = new byte[(int)bufferSize];
		int count = 0;
		ServletOutputStream output = response.getOutputStream();
		int interval = request.getSession().getMaxInactiveInterval();
		long startTime = new Date().getTime();
		Log.log(Log.DEBUG, "Downloading file from " + offset + " max inactive interval:" + interval + " starting at:" + new Date(startTime));
		if (offset > 0) {
			RemoteRandomAccessFile input = null;
			try {
				if (file.getFileSystem() instanceof SRBFileSystem) {
					// input = new SRBFileInputStream((SRBFile)file);
					input = new SRBRandomAccessFile((SRBFile) file, "r");
				} else if (file.getFileSystem() instanceof IRODSFileSystem) {
					Log.log(Log.DEBUG, "file can read?:" + file.canRead());
					input = new IRODSRandomAccessFile((IRODSFile) file, "r");
				}
				input.seek(offset);
				// try{
				while ((count = input.read(buf)) > 0) {
					// Log.log(Log.DEBUG, "read "+count);
					if (request.getSession().getMaxInactiveInterval() - (new Date().getTime() - startTime) / 1000 < 60) {
						// increase interval by 5 mins
						request.getSession().setMaxInactiveInterval(request.getSession().getMaxInactiveInterval() + 300);
						Log.log(Log.DEBUG, "session time is extended to:" + request.getSession().getMaxInactiveInterval());
					}
					output.write(buf, 0, count);
				}
				output.flush();
			} catch (Exception e) {
				Log.log(Log.WARNING, "remote peer is closed: " + e.getMessage());
				if (checkGetError(response, e.getMessage()))
					return;
				Log.log(Log.WARNING, "Exception was "+e);
			}
			if (input != null)
				input.close();
		} else {
			RemoteFileInputStream input = null;
			try {
				if (file.getFileSystem() instanceof SRBFileSystem)
					input = new SRBFileInputStream((SRBFile) file);
				else if (file.getFileSystem() instanceof IRODSFileSystem) {
					Log.log(Log.DEBUG, "file can read?:" + file.canRead());
					input = new IRODSFileInputStream((IRODSFile) file);
				}
				while (true) {
					try {
						count = input.read(buf);
					} catch (IOException e) {
						if (e.getMessage().endsWith("-19000")) { // Quick way to detect IRODS permission failed
							response.sendError(HttpServletResponse.SC_FORBIDDEN, "You do not have permission to access this resource.");
							response.flushBuffer();
							return;
						}
						throw (e);
					}
					if (count <= 0)
						break;
					// inactive interval - "idle" time < 1 min, increase
					// inactive interval
					if (request.getSession().getMaxInactiveInterval() - (new Date().getTime() - startTime) / 1000 < 60) {
						// increase interval by 5 mins
						request.getSession().setMaxInactiveInterval(request.getSession().getMaxInactiveInterval() + 300);
						Log.log(Log.DEBUG, "session time is extended to:" + request.getSession().getMaxInactiveInterval());
					}
					//Log.log(Log.DEBUG, "read "+count);
					output.write(buf, 0, count);
				}
				output.flush();
			} catch (Exception e) {
				Log.log(Log.WARNING, "remote peer is closed: " + e.getMessage());
				if (checkGetError(response, e.getMessage()))
					return;
				Log.log(Log.WARNING, "Exception was "+e);
			}
			if (input != null)
				input.close();
		}
		request.getSession().setMaxInactiveInterval(interval);
		output.close();
	}
	
	private boolean checkGetError(HttpServletResponse response, String message) throws IOException {
		
		if ((message != null) && message.contains("IRODS error occured -105000")) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, "Item is unavailable because its resource is unavailable.  Please contact "+Davis.getConfig().getOrganisationSupport());
			response.flushBuffer();
			return true;
		}
		return false;
	}

	private void writeFile(String url, HttpServletRequest request, HttpServletResponse response) throws IOException {
		// URL u=request.getSession().getServletContext().getResource(url);
		// u.get?
		InputStream in = request.getSession().getServletContext().getResourceAsStream(url);
		// response.setContentType ("application/pdf");
		ServletOutputStream op = response.getOutputStream();
		int length;
		byte[] buf = new byte[2048];
		while ((in != null) && ((length = in.read(buf)) != -1)) {
			// the data has already been read into buf
			// System.out.println("Bytes read in: " + Integer.toString(length));
			op.write(buf, 0, length);
		}
		op.flush();
		if (in == null)
			Log.log(Log.ERROR, this.getClass().getName()+" failed to return "+url+". It wasn't found on the local file system.");
		else
			in.close();
		op.close();
	}

	/**
	 * Returns the <code>PropertiesBuilder</code> that will be used to build the
	 * PROPFIND result XML document for directory listings.
	 * 
	 * @return The <code>PropertiesBuilder</code> used to build the XML
	 *         document.
	 */
	protected PropertiesBuilder getPropertiesBuilder() {
		return propertiesBuilder;
	}

	private void showConfiguration(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html");
		OutputStream output = response.getOutputStream();
		output.write(getConfiguration(request.getLocale()));
		response.flushBuffer();
	}

	private byte[] getConfiguration(Locale locale) throws ServletException,	IOException {
		synchronized (configurations) {
			byte[] configuration = configurations.get(locale);
			if (configuration != null)
				return configuration;
			InputStream stream = DavisUtilities.getResourceAsStream(configurationLocation, locale);
			if (stream == null) {
				throw new ServletException(DavisUtilities.getResource(DefaultGetHandler.class, "configurationPageError", null, null));
			}
			ByteArrayOutputStream collector = new ByteArrayOutputStream();
			byte[] buffer = new byte[2048];
			int count;
			while ((count = stream.read(buffer, 0, 2048)) != -1) {
				collector.write(buffer, 0, count);
			}
			configuration = collector.toByteArray();
			configurations.put(locale, configuration);
			return configuration;
		}
	}

	private Templates getTemplates(HttpSession session) {
		String id = session.getId();
		TemplateTracker tracker;
		synchronized (templateMap) {
			tracker = templateMap.get(id);
		}
		if (tracker == null)
			return null;
		Log.log(Log.DEBUG, "Retrieved precompiled stylesheet.");
		return tracker.getTemplates();
	}

	private void clearTemplates(HttpSession session) {
		String id = session.getId();
		TemplateTracker tracker;
		synchronized (templateMap) {
			tracker = templateMap.remove(id);
		}
		if (tracker != null) {
			Log.log(Log.DEBUG, "Removing precompiled stylesheet.");
			tracker.cancel();
		}
	}

	private void setTemplates(HttpSession session, Templates templates) {
		String id = session.getId();
		long cacheTime = (long) session.getMaxInactiveInterval() * 1000;
		Log.log(Log.DEBUG, "Storing precompiled stylesheet.");
		synchronized (templateMap) {
			templateMap.put(id, new TemplateTracker(id, templates, cacheTime));
		}
	}

	private Templates getDefaultTemplates(Locale locale) throws ServletException {
		synchronized (defaultTemplates) {
			Templates templates = defaultTemplates.get(locale);
			if (templates != null)
				return templates;
			try {
				Source source = getStylesheet(stylesheetLocation, true, locale);
				templates = TransformerFactory.newInstance().newTemplates(source);
				defaultTemplates.put(locale, templates);
				return templates;
			} catch (Exception ex) {
				throw new ServletException(DavisUtilities.getResource(DefaultGetHandler.class, "stylesheetError", null, null));
			}
		}
	}

//	private InputStream getResourceAsStream(String location, Locale locale) {
//		int index = location.indexOf('.');
//		String prefix = (index != -1) ? location.substring(0, index) : location;
//		String suffix = (index != -1) ? location.substring(index) : "";
//		String language = locale.getLanguage();
//		String country = locale.getCountry();
//		String variant = locale.getVariant();
//		InputStream stream = null;
//		if (!variant.equals("")) {
//			stream = getResourceAsStream(prefix + '_' + language + '_' + country + '_' + variant + suffix);
//			if (stream != null)
//				return stream;
//		}
//		if (!country.equals("")) {
//			stream = getResourceAsStream(prefix + '_' + language + '_' + country + suffix);
//			if (stream != null)
//				return stream;
//		}
//		stream = getResourceAsStream(prefix + '_' + language + suffix);
//		if (stream != null)
//			return stream;
//		Locale secondary = Locale.getDefault();
//		if (!locale.equals(secondary)) {
//			language = secondary.getLanguage();
//			country = secondary.getCountry();
//			variant = secondary.getVariant();
//			if (!variant.equals("")) {
//				stream = getResourceAsStream(prefix + '_' + language + '_' + country + '_' + variant + suffix);
//				if (stream != null)
//					return stream;
//			}
//			if (!country.equals("")) {
//				stream = getResourceAsStream(prefix + '_' + language + '_' + country + suffix);
//				if (stream != null)
//					return stream;
//			}
//			stream = getResourceAsStream(prefix + '_' + language + suffix);
//			if (stream != null)
//				return stream;
//		}
//		return getResourceAsStream(location);
//	}
//
//	private InputStream getResourceAsStream(String location) {
//		InputStream stream = null;
//		try {
//			stream = getServletConfig().getServletContext().getResourceAsStream(location);
//			if (stream != null)
//				return stream;
//		} catch (Exception ex) {}
//		try {
//			stream = getClass().getResourceAsStream(location);
//			if (stream != null)
//				return stream;
//		} catch (Exception ex) {}
//		try {
//			ClassLoader loader = Thread.currentThread().getContextClassLoader();
//			if (loader != null)
//				stream = loader.getResourceAsStream(location);
//			if (stream != null)
//				return stream;
//		} catch (Exception ex) {}
//		try {
//			ClassLoader loader = ClassLoader.getSystemClassLoader();
//			if (loader != null)
//				stream = loader.getResourceAsStream(location);
//			if (stream != null)
//				return stream;
//		} catch (Exception ex) {}
//		return null;
//	}

	private Source getStylesheet(String location, boolean allowExternal, Locale locale) throws Exception {
		InputStream stream = DavisUtilities.getResourceAsStream(location, locale); 
		if (stream != null) {
			Log.log(Log.DEBUG, "Obtained stylesheet for \"{0}\".", location);
			return new StreamSource(stream);
		}
		if (!allowExternal) {
			throw new IllegalArgumentException(DavisUtilities.getResource(DefaultGetHandler.class, "stylesheetNotFound", new Object[] { location }, null));
		}
		Log.log(Log.DEBUG, "Using external stylesheet at \"{0}\".", location);
		return new StreamSource(location);
	}

	private class TemplateTracker extends TimerTask {

		private final Templates templates;
		private final String id;

		public TemplateTracker(String id, Templates templates, long cacheTime) {
			this.templates = templates;
			this.id = id;
			TIMER.schedule(this, cacheTime);
		}

		public void run() {
			Log.log(Log.DEBUG, "Removing cached stylesheet for session {0}", id);
			synchronized (templateMap) {
				templateMap.remove(id);
			}
		}

		public Templates getTemplates() {
			return templates;
		}
	}
}