package webdavis;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import java.net.UnknownHostException;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.ietf.jgss.GSSCredential;

import au.edu.archer.desktopshibboleth.idp.IDP;
import au.org.mams.slcs.client.SLCSClient;
import au.org.mams.slcs.client.SLCSConfig;

import edu.sdsc.grid.io.Base64;
import edu.sdsc.grid.io.MetaDataCondition;
import edu.sdsc.grid.io.MetaDataRecordList;
import edu.sdsc.grid.io.MetaDataSelect;
import edu.sdsc.grid.io.MetaDataSet;
import edu.sdsc.grid.io.RemoteAccount;
import edu.sdsc.grid.io.irods.IRODSAccount;
import edu.sdsc.grid.io.irods.IRODSFileSystem;
import edu.sdsc.grid.io.srb.SRBAccount;
import edu.sdsc.grid.io.srb.SRBFileSystem;
import edu.sdsc.grid.io.srb.SRBMetaDataSet;

/**
 * This servlet provides a WebDAV gateway to SRB/iRods shared resources.
 * <p>
 * You can specify jCIFS configuration settings in the servlet's initialization
 * parameters which will be applied to the environment. Settings of particular
 * interest to the Davis servlet include:
 * <p>
 * <table border="1">
 * <tr>
 * <th>Parameter</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td><code>jcifs.smb.client.domain</code></td>
 * <td>Provides the default domain if not specified during HTTP Basic
 * authentication. If the user enters "username" rather than
 * "DOMAIN&#92;username", this specifies the default domain against which the
 * user should be authenticated.</td>
 * </tr>
 * <tr>
 * <td><code>jcifs.http.domainController</code></td>
 * <td>Provides the IP address or name of the server used to authenticate
 * clients. This is only used for browsing the root ("<code>smb://</code>")
 * and workgroups (if a server cannot be found). For servers, shares,
 * directories and files, the corresponding server is used. If not specified,
 * the system will attempt to locate a controller for the domain specified in
 * <code>jcifs.smb.client.domain</code> (if present). <b>If neither
 * <code>jcifs.http.domainController</code> nor
 * <code>jcifs.smb.client.domain</code> are specified, authentication will not
 * be required for browsing the SMB root (or workgroups for which a server
 * cannot be found).</b> This may pose a security risk.
 * <p>
 * It is not necessary for this to specify a real domain controller; any machine
 * offering SMB services can be used.</td>
 * </tr>
 * <tr>
 * <td><code>jcifs.netbios.wins</code></td>
 * <td>Specifies the IP address of a WINS server to be used in resolving server
 * and domain/workgroup names. This is needed to locate machines in other
 * subnets.</td>
 * </tr>
 * <tr>
 * <td><code>jcifs.http.enableBasic</code></td>
 * <td>Enables/disables HTTP Basic authentication support. This allows
 * non-NTLM-capable browsers to successfully authenticate. NTLM-capable clients
 * will authenticate using NTLM. This defaults to <code>true</code>. Setting
 * this to <code>false</code> will disable HTTP Basic authentication entirely,
 * allowing only NTLM-capable browsers to connect.</td>
 * </tr>
 * <tr>
 * <td><code>jcifs.http.insecureBasic</code></td>
 * <td>Enables/disables HTTP Basic authentication over an insecure channel.
 * Normally, HTTP Basic authentication will only be available over HTTPS.
 * Setting this to <code>true</code> will offer HTTP Basic authentication over
 * insecure HTTP, sending login information over the network unencrypted.
 * <b>This is a severe security risk, and is strongly advised against.</b> This
 * defaults to <code>false</code>.</td>
 * </tr>
 * <tr>
 * <td><code>jcifs.http.basicRealm</code></td>
 * <td>Specifies the HTTP Basic realm that will be presented during
 * authentication. Defaults to "Davenport".</td>
 * </tr>
 * </table>
 * <p>
 * Further details regarding configuration of the jCIFS environment can be found
 * in the jCIFS documentation (available from <a
 * href="http://jcifs.samba.org">http://jcifs.samba.org</a>).
 * </p>
 * <p>
 * Additionally, you can specify your own custom handlers for HTTP methods. By
 * implementing {@link smbdav.MethodHandler}, you can provide your own behavior
 * for GET, PUT, etc. requests. To enable your handler, add an initialization
 * parameter with the handler's classname. For example:
 * </p>
 * 
 * <pre>
 * &lt;init-param&gt;
 *     &lt;param-name&gt;handler.GET&lt;/param-name&gt;
 *     &lt;param-value&gt;com.foo.MyGetHandler&lt;/param-value&gt;
 * &lt;/init-param&gt;
 * </pre>
 * 
 * <p>
 * This will install a <code>com.foo.MyGetHandler</code> instance as the
 * handler for GET requests.
 * </p>
 * 
 * @author Eric Glass
 */
public class Davis extends HttpServlet {

	/**
	 * The name of the servlet context attribute containing the
	 * <code>SmbFileFilter</code> applied to resource requests.
	 */
	public static final String RESOURCE_FILTER = "davis.resourceFilter";

	/**
	 * The name of the servlet context attribute containing the
	 * <code>LockManager</code> which maintains WebDAV locks.
	 */
	public static final String LOCK_MANAGER = "davis.lockManager";

	/**
	 * The name of the servlet context attribute containing the charset used to
	 * interpret request URIs.
	 */
	public static final String REQUEST_URI_CHARSET = "request-uri.charset";

	/**
	 * The name of the request attribute containing the context base for URL
	 * rewriting.
	 */
	public static final String CONTEXT_BASE = "davis.contextBase";

	/**
	 * The name of the request attribute containing the authenticated principal.
	 */
	// public static final String PRINCIPAL = "davenport.principal";
	public static final String PRINCIPAL = "davis.principal";
	public static final String CREDENTIALS = "davis.credentials";
	private static final int DEFAULT_SRB_PORT = 5544;

	private final Map handlers = new HashMap();

	// private ErrorHandler[] errorHandlers;

	// private ResourceFilter filter;

	private String defaultDomain;

	private String realm;

	private String contextBase;

	private String contextBaseHeader;

	private boolean alwaysAuthenticate;

	private boolean acceptBasic;

	private boolean enableBasic;

	private boolean closeOnAuthenticate;

	private boolean insecureBasic;

	protected long nonceSecret=this.hashCode() ^ System.currentTimeMillis();

	
	public void init() throws ServletException {
		ServletConfig config = getServletConfig();
		String logProviderName = Log.class.getName();
		String logProvider = config.getInitParameter(logProviderName);
		System.out.println(logProviderName);
		System.out.println(logProvider);
		if (logProvider != null) {
			try {
				System.setProperty(logProviderName, logProvider);
			} catch (Exception ignore) {
			}
		}
		String logThreshold = config.getInitParameter(logProviderName
				+ ".threshold");
		if (logThreshold != null) {
			try {
				System
						.setProperty(logProviderName + ".threshold",
								logThreshold);
			} catch (Exception ignore) {
			}
		}
		Log.log(Log.DEBUG, "Logging initialized.");
		if (Log.getThreshold() < Log.INFORMATION) {
			Properties props = new Properties();
			Enumeration params = config.getInitParameterNames();
			while (params.hasMoreElements()) {
				String paramName = (String) params.nextElement();
				props
						.setProperty(paramName, config
								.getInitParameter(paramName));
			}
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			props.list(new PrintStream(stream));
			Log.log(Log.DEBUG, "Davis init parameters: {0}", stream);
		}
		String jargonDebug= config.getInitParameter("jargon.debug");
		if (jargonDebug!=null)
			System.setProperty("jargon.debug", jargonDebug);
		else
			System.setProperty("jargon.debug", "0");

		String requestUriCharset = config.getInitParameter(REQUEST_URI_CHARSET);
		if (requestUriCharset == null)
			requestUriCharset = "ISO-8859-1";
		contextBase = config.getInitParameter("contextBase");
		contextBaseHeader = config.getInitParameter("contextBaseHeader");
		config.getServletContext().setAttribute(REQUEST_URI_CHARSET,
				requestUriCharset);
		String defaultServer = defaultDomain;
		String acceptBasic = config.getInitParameter("acceptBasic");
		this.acceptBasic = Boolean.valueOf(acceptBasic).booleanValue();
		String enableBasic = "true";
		this.enableBasic = (enableBasic == null)
				|| Boolean.valueOf(enableBasic).booleanValue();
		String closeOnAuthenticate = config
				.getInitParameter("closeOnAuthenticate");
		this.closeOnAuthenticate = Boolean.valueOf(closeOnAuthenticate)
				.booleanValue();
		realm = "Davis";
		String alwaysAuthenticate = config
				.getInitParameter("alwaysAuthenticate");
		this.alwaysAuthenticate = (alwaysAuthenticate == null)
				|| Boolean.valueOf(alwaysAuthenticate).booleanValue();
		initLockManager(config);
		// initFilter(config);
		initHandlers(config);
		// initErrorHandlers(config);
	}

	public void destroy() {
		Iterator iterator = handlers.entrySet().iterator();
		while (iterator.hasNext()) {
			((MethodHandler) ((Map.Entry) iterator.next()).getValue())
					.destroy();
			iterator.remove();
		}
		// if (errorHandlers != null) {
		// for (int i = errorHandlers.length - 1; i >= 0; i--) {
		// errorHandlers[i].destroy();
		// }
		// errorHandlers = null;
		// }
		// if (filter != null) {
		// filter.destroy();
		// filter = null;
		// }
		ServletContext context = getServletContext();
		context.removeAttribute(LOCK_MANAGER);
		// context.removeAttribute(RESOURCE_FILTER);
		context.removeAttribute(REQUEST_URI_CHARSET);
		Map contMap = (Map) context.getAttribute(Davis.CREDENTIALS);
		if (contMap!=null){
			iterator=contMap.keySet().iterator();
			while (iterator.hasNext()) {
				((DavisSession)iterator.next()).disconnect();
			}
		}
		Log.log(Log.DEBUG, "Davis finished destroy.");
	}

	/**
	 * Authenticates the user against a domain before forwarding the request to
	 * the appropriate handler.
	 * 
	 * @param request
	 *            The request being handled.
	 * @param response
	 *            The response supplied by the servlet.
	 * @throws IOException
	 *             If an IO error occurs while handling the request.
	 * @throws ServletException
	 *             If an application error occurs.
	 */
	protected void service(HttpServletRequest request,
			HttpServletResponse response) throws IOException, ServletException {
		Log.log(Log.INFORMATION, "Received {0} request for \"{1}\".",
				new Object[] { request.getMethod(), request.getRequestURL() });
		if (Log.getThreshold() < Log.INFORMATION) {
			StringBuffer headers = new StringBuffer();
			Enumeration headerNames = request.getHeaderNames();
			while (headerNames.hasMoreElements()) {
				String headerName = (String) headerNames.nextElement();
				headers.append("    ").append(headerName).append(": ");
				Enumeration headerValues = request.getHeaders(headerName);
				while (headerValues.hasMoreElements()) {
					headers.append(headerValues.nextElement());
					if (headerValues.hasMoreElements())
						headers.append(", ");
				}
				if (headerNames.hasMoreElements())
					headers.append("\n");
			}
			Log.log(Log.DEBUG, "Headers:\n{0}", headers);
			HttpSession session = request.getSession(false);
			if (session != null) {
				Log.log(Log.DEBUG, "Active session: {0}", session.getId());
			} else {
				Log.log(Log.DEBUG, "Session not yet established.");
			}
		}
		String contextBase = this.contextBase;
		if (contextBaseHeader != null) {
			String dynamicBase = request.getHeader(contextBaseHeader);
			if (dynamicBase != null)
				contextBase = dynamicBase;
		}
		if (contextBase != null) {
			if (!contextBase.endsWith("/"))
				contextBase += "/";
			request.setAttribute(CONTEXT_BASE, contextBase);
			Log.log(Log.DEBUG, "Using context base: " + contextBase);
		}
		// boolean usingBasic = (acceptBasic || enableBasic) &&
		// (insecureBasic || request.isSecure());
		// if (Log.getThreshold() < Log.INFORMATION) {
		// Log.log(Log.DEBUG, enableBasic ? "Basic auth offered to client." :
		// "Basic auth NOT offered to client.");
		// if (acceptBasic) {
		// Log.log(Log.DEBUG,
		// "Basic auth accepted if supplied by client.");
		// }
		// Log.log(Log.DEBUG, insecureBasic ?
		// "Basic auth enabled over insecure requests." :
		// "Basic auth disabled over insecure requests.");
		// Log.log(Log.DEBUG, request.isSecure() ?
		// "This request is secure." : "This request is NOT secure.");
		// Log.log(Log.DEBUG, usingBasic ?
		// "Basic auth ENABLED for this request." :
		// "Basic auth DISABLED for this request.");
		// }
		String pathInfo = request.getPathInfo();
		if (pathInfo == null || "".equals(pathInfo))
			pathInfo = "/";
		// String target = "smb:/" + pathInfo;
		// UniAddress server = null;
		// int port = DEFAULT_SMB_PORT;
		// try {
		// server = getServer(target);
		// port = getPort(target);
		// Log.log(Log.DEBUG, "Target is \"{0}:{1}\".",
		// new Object[] { server, new Integer(port) });
		// } catch (UnknownHostException ex) {
		// Log.log(Log.DEBUG, "Unknown server: {0}", ex);
		// response.sendError(HttpServletResponse.SC_NOT_FOUND,
		// DavisUtilities.getResource(Davis.class,
		// "unknownServer", new Object[] { target },
		// request.getLocale()));
		// return;
		// }
		String defaultDomain;
		String serverName;
		int serverPort;
		String defaultResource;
		String zoneName;
		String serverType=getServletConfig().getInitParameter("server-type");

		defaultDomain = getServletConfig().getInitParameter("default-domain");
		serverPort = 5544;
		serverName = getServletConfig().getInitParameter("server-name");
		defaultResource = getServletConfig().getInitParameter(
				"default-resource");
		zoneName = getServletConfig().getInitParameter(
				"zone-name");
		String proxyHost = getServletConfig().getInitParameter("proxy-host");
		String proxyPort = getServletConfig().getInitParameter("proxy-port");
		String proxyUsername = getServletConfig().getInitParameter(
				"proxy-username");
		String proxyPassword = getServletConfig().getInitParameter(
				"proxy-password");
		String idpName = null;
		String user = null;
		String basicUsername = null;
		String password = null;
		String domain = defaultDomain;
		DavisSession davisSession = null;
		String sessionID = null;
		String authorization = request.getHeader("Authorization");
		// System.out.println("Authorization: " + authorization);
		if (authorization != null
				&& authorization.regionMatches(true, 0, "Basic ", 0, 6)) {

			String authInfo = new String(Base64.fromString(authorization
					.substring(6)), "ISO-8859-1");
			// System.out.println("authInfo:"+authInfo);
			int index = authInfo.indexOf(':');
			user = (index != -1) ? authInfo.substring(0, index) : authInfo;
			password = (index != -1) ? authInfo.substring(index + 1) : "";
			basicUsername = user;
			sessionID = basicUsername + "%" + request.getServerName() + "#" + request.getRemoteAddr();
			Log.log(Log.DEBUG, "sessionID:"+sessionID);

			if ((index = user.indexOf('\\')) != -1
					|| (index = user.indexOf('/')) != -1) {
				idpName = user.substring(0, index);
				user = user.substring(index + 1);
			}
			boolean hasResource = false;
			if ((index = user.indexOf('#')) != -1) {
				defaultResource = user.substring(index + 1);
				user = user.substring(0, index);
				hasResource = true;
			}
			if ((index = user.indexOf('@')) != -1) {
				serverName = user.substring(index + 1);
				user = user.substring(0, index);
				domain = serverName;
//				if (!hasResource)
//					defaultResource = "";
			}
			if ((index = user.indexOf('.')) != -1) {
				domain = user.substring(index + 1);
				user = user.substring(0, index);
			}

			// System.out.println("login: "+idpName+" "+user+" "+domain+"
			// "+serverName+" "+defaultResource);
		}else if (authorization != null
				&& authorization.regionMatches(true, 0, "Digest ", 0, 6)) {
//            QuotedStringTokenizer tokenizer = new QuotedStringTokenizer(credentials,
//                    "=, ",
//                    true,
//                    false);
//            boolean stale=false;
//            Digest digest=new Digest(request.getMethod());
//String last=null;
//String name=null;
//
//loop:
//while (tokenizer.hasMoreTokens())
//{
//String tok = tokenizer.nextToken();
//char c=(tok.length()==1)?tok.charAt(0):'\0';
//
//switch (c)
//{
//case '=':
//name=last;
//last=tok;
//break;
//case ',':
//name=null;
//case ' ':
//break;
//
//default:
//last=tok;
//if (name!=null)
//{
//if ("username".equalsIgnoreCase(name))
//digest.username=tok;
//else if ("realm".equalsIgnoreCase(name))
//digest.realm=tok;
//else if ("nonce".equalsIgnoreCase(name))
//digest.nonce=tok;
//else if ("nc".equalsIgnoreCase(name))
//digest.nc=tok;
//else if ("cnonce".equalsIgnoreCase(name))
//digest.cnonce=tok;
//else if ("qop".equalsIgnoreCase(name))
//digest.qop=tok;
//else if ("uri".equalsIgnoreCase(name))
//digest.uri=tok;
//else if ("response".equalsIgnoreCase(name))
//digest.response=tok;
//break;
//}
//}
//}
//
//int n=checkNonce(digest.nonce,request);
//if (n>0)
//user = realm.authenticate(digest.username,digest,request);
//else if (n==0)
//stale = true;
//if (user==null)
//    Log.warn("AUTH FAILURE: user "+StringUtil.printable(digest.username));
//else   
//{
//    request.setAuthType(Constraint.__DIGEST_AUTH);
//    request.setUserPrincipal(user);
//}
			
			
			
		} else {
			fail(serverName, request, response);
			return;
		}

		HttpSession session = request.getSession(false);
		if (session != null) {
			// System.out.println("Obtained handle to session
			// "+session.getId());
			Map credentials = (Map) session.getAttribute(CREDENTIALS);
			if (credentials != null) {
				// System.out.println("Dumping credential cache:"+credentials);
				davisSession = (DavisSession) credentials.get(sessionID);
				// if (authentication != null) {
				// System.out.println(
				// "Found cached credentials for "+serverName);
				// } else {
				// System.out.println(
				// "No cached credentials found for "+serverName);
				// }
				Log.log(Log.DEBUG, "Got davisSession from session. sessionId="+sessionID);
			}
		}
		if (davisSession == null) {
			Map credentials = (Map) request.getSession().getServletContext()
					.getAttribute(CREDENTIALS);
			if (credentials != null) {
				// System.out.println("Dumping credential cache:"+credentials);
				davisSession = (DavisSession) credentials.get(sessionID);
				Log.log(Log.DEBUG, "Got davisSession from servletContext. sessionId="+sessionID);
			}
		}
		if (davisSession!=null){
			if (davisSession.getRemoteFileSystem()==null){
				Log.log(Log.DEBUG,"no file system in davisSesion");
				davisSession=null;
			}else if (davisSession.getRemoteFileSystem()!=null&&!davisSession.getRemoteFileSystem().isConnected()){
				Log.log(Log.DEBUG,"file system in davisSesion expired, need to reconnect.");
				davisSession=null;
			}
		}

		String homeDir = null;
		String redirectURL;

		if (davisSession == null) {
			RemoteAccount account = null;
			if (idpName != null) {
				// auth with idp
				IDP idp = null;
				SLCSClient client;
				try {
					if (proxyHost != null && proxyHost.toString().length() > 0) {
						SLCSConfig config = SLCSConfig.getInstance();
						config.setProxyHost(proxyHost.toString());
						if (proxyPort != null
								&& proxyPort.toString().length() > 0)
							config.setProxyPort(Integer.parseInt(proxyPort
									.toString()));
						if (proxyUsername != null
								&& proxyUsername.toString().length() > 0)
							config.setProxyUsername(proxyUsername.toString());
						if (proxyPassword != null
								&& proxyPassword.toString().length() > 0)
							config.setProxyPassword(proxyPassword.toString());
					}
					client = new SLCSClient();
					List<IDP> idps = client.getAvailableIDPs();
					for (IDP idptmp : idps) {
						// System.out.println("idp: "+idptmp.getName()+"
						// "+idptmp.getProviderId());
						if (idptmp.getName().equalsIgnoreCase(idpName)) {
							idp = idptmp;
							break;
						}
					}
					if (idp == null) {
						for (IDP idptmp : idps) {
							// System.out.println("idp: "+idptmp.getName()+"
							// "+idptmp.getProviderId());
							if (idptmp.getName().startsWith(idpName)) {
								idp = idptmp;
								break;
							}
						}
					}
					if (idp == null) {
						for (IDP idptmp : idps) {
							// System.out.println("idp: "+idptmp.getName()+"
							// "+idptmp.getProviderId());
							if (idptmp.getName().indexOf(idpName) > -1) {
								idp = idptmp;
								break;
							}
						}

					}
					if (idp == null) {
						fail(serverName, request, response);
						return;
					}
					Log.log(Log.DEBUG,"logging in with idp: "+idp.getName());
					GSSCredential gssCredential = client.slcsLogin(idp, user,
							password);
					if (serverType.equalsIgnoreCase("irods")){
						//(java.lang.String host, int port, java.lang.String userName, java.lang.String password, java.lang.String homeDirectory, java.lang.String zone, java.lang.String defaultStorageResource) 
						davisSession = new DavisSession();
						account = new IRODSAccount(serverName,serverPort,"","","",zoneName,"");
//						((IRODSAccount)account).setPort(serverPort);
//						((IRODSAccount)account).setHost(serverName);
//						((IRODSAccount)account).setAuthenticationScheme( IRODSAccount.GSI_PASSWORD );
						((IRODSAccount)account).setGSSCredential(gssCredential);
						davisSession.setZone(zoneName);
						davisSession.setServerName(serverName);
						davisSession.setServerPort(serverPort);
//						davisSession.setAccount(account.getUserName());
						davisSession.setDn(gssCredential.getName().toString());
					}else{
						account = new SRBAccount(serverName, serverPort,
								gssCredential);
						davisSession = new DavisSession();
						davisSession.setServerName(serverName);
						davisSession.setServerPort(serverPort);
						davisSession.setDomain(((SRBAccount)account).getDomainName());
						davisSession.setZone(zoneName);
						davisSession.setAccount(account.getUserName());
						davisSession.setDn(gssCredential.getName().toString());
					}
				} catch (GeneralSecurityException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			} else {
				Log.log(Log.DEBUG,"login with username/password");
				if (serverType.equalsIgnoreCase("irods")){
					account = new IRODSAccount(serverName, serverPort, user, password, "/" + zoneName + "/home/" + user, zoneName, defaultResource);
					davisSession = new DavisSession();
					davisSession.setServerName(serverName);
					davisSession.setServerPort(serverPort);
					davisSession.setAccount(user);
					davisSession.setZone(zoneName);
				}else{
					account = new SRBAccount(serverName, serverPort, user,
							password, "/" + zoneName + "/home/" + user + "."
									+ domain, domain, defaultResource);
					davisSession = new DavisSession();
					davisSession.setServerName(serverName);
					davisSession.setDomain(domain);
					davisSession.setServerPort(serverPort);
					davisSession.setAccount(user);
					davisSession.setZone(zoneName);
				}

			}
			if (davisSession==null){
				fail(serverName, request, response);
				return;
			}
			davisSession.setSessionID(sessionID);
			try {
				String[] resList = null;
				if (serverType.equalsIgnoreCase("irods")){
					IRODSFileSystem irodsFileSystem = new IRODSFileSystem((IRODSAccount)account);
					Log.log(Log.DEBUG, "irods fs:"+irodsFileSystem);
					homeDir = irodsFileSystem.getHomeDirectory();
					if (davisSession.getAccount()==null||davisSession.getAccount().equals("")){
						user = FSUtilities.getiRODSUsernameByDN(irodsFileSystem, davisSession.getDn());
						if (user==null){
							fail(serverName, request, response);
							return;
						}
						davisSession.setAccount(user);
						homeDir = "/" + zoneName + "/home/" + davisSession.getAccount();
					}
					davisSession.setRemoteFileSystem(irodsFileSystem);
					resList = FSUtilities.getIRODSResources(irodsFileSystem,davisSession.getZone());
					if (homeDir == null)
						homeDir = "/" + zoneName + "/home/" + user;
				}else{
					SRBFileSystem srbFileSystem = new SRBFileSystem((SRBAccount)account);
					// if (srbFileSystem.isConnected()){
					davisSession.setRemoteFileSystem(srbFileSystem);
					homeDir = srbFileSystem.getHomeDirectory();
					resList = FSUtilities.getSRBResources(srbFileSystem,davisSession.getZone());
					if (homeDir == null)
						homeDir = "/" + zoneName + "/home/" + user + "."
								+ domain;
				}
				Log.log(Log.DEBUG, "zone:"+davisSession.getZone());
				if (resList!=null) {
					for (String res : resList) {
						Log.log(Log.DEBUG, "res:"+res);
						if (res.equals(defaultResource)) {
							davisSession.setDefaultResource(res);
						}
					}
					if ((davisSession.getDefaultResource() == null || davisSession.getDefaultResource().length() == 0)
							&& resList.length > 0) {
						for (String res : resList) {
							if (res.startsWith(defaultResource)) {
								davisSession.setDefaultResource(res);
							}
						}
					}
					if ((davisSession.getDefaultResource() == null || davisSession.getDefaultResource().length() == 0)
							&& resList.length > 0) {
						davisSession.setDefaultResource(resList[0]);
					}
				}

//				}
				Log.log(Log.DEBUG, "homedir:" + homeDir);
				davisSession.setHomeDirectory(homeDir);

				// srbFileSystem.set
				// change resource
				// srbFileSystem.close();
				// srbFileSystem=new SRBFileSystem(account);

				Log.log(Log.DEBUG, "Authentication succeeded. home=" + homeDir
						+ " defaultRes=" + davisSession.getDefaultResource()
						+ " zone=" + davisSession.getZone());
				// System.out.println(req.getPathInfo()+"
				// "+req.getServletPath());
				// System.out.println(req.getContextPath()+"
				// "+req.getRequestURL());
				// System.out.println(req.getRequestURL().substring(0,req.getRequestURL().length()-req.getPathInfo().length()+1)+homeDir);
//				redirectURL = "/srbdav" + homeDir; // req.getRequestURL().substring(0,req.getRequestURL().length()-req.getPathInfo().length()+1)+homeDir;
			} catch (Exception e) {
				e.printStackTrace();
				Log.log(Log.DEBUG, "Authentication failed.");
				fail(serverName, request, response);
				return;
			}
			session = request.getSession();
			if (session != null) {
				Log.log(Log.DEBUG,"Obtained handle to session "
						+ session.getId());
				Map credentials = (Map) session.getAttribute(CREDENTIALS);
				if (credentials == null) {
					Log
							.log(Log.DEBUG,
									"Created new credential cache for current session.");
					credentials = new Hashtable();
					session.setAttribute(CREDENTIALS, credentials);
				}
				credentials.put(sessionID, davisSession);
				Log.log(Log.DEBUG, "Put davisSession to session. sessionID="+sessionID);
				// System.out.println("Cached Credentials for "+serverName+":
				// "+authentication);

			}
			Map credentials = (Map) request.getSession().getServletContext()
					.getAttribute(CREDENTIALS);
			if (credentials == null) {
				Log.log(Log.DEBUG,
						"Created new credential cache for servlet context.");
				credentials = new Hashtable();
				request.getSession().getServletContext().setAttribute(
						CREDENTIALS, credentials);
			}
			credentials.put(sessionID, davisSession);
			Log.log(Log.DEBUG, "Put davisSession to ServletContext. sessionID="+sessionID);

			// System.out.println("redirecting to "+redirectURL);
			// if (!redirectURL.endsWith(getRelativePath(req, authentication)))
			// redirect(redirectURL,req,resp);
			// return;
			// RequestDispatcher requestDispatcher =
			// getServletContext().getRequestDispatcher(redirectURL);
			// requestDispatcher.include(req, resp);
			// return;
			// }else{

		}
		if (davisSession == null) {
			// System.out.println( "No credentials obtained (required).");
			fail(null, request, response);
			return;
		}
		// System.out.println("Using credentials: " + authentication);

		if (davisSession != null) {
			request.setAttribute(PRINCIPAL, davisSession);
		// // fStore.setFileSystem(authentication);
		}
		// if (authentication == null) authentication = anonymousCredentials;
		Log.log(Log.DEBUG, "Final davisSession: " + davisSession);
		// if (authentication != null) {
		// request.setAttribute(PRINCIPAL, authentication);
		// }
		MethodHandler handler = getHandler(request.getMethod());
		if (handler != null) {
			try {
				Log.log(Log.DEBUG, "Handler is {0}", handler.getClass());
				handler.service(request, response, davisSession);
			} catch (Throwable throwable) {
				// Log.log(Log.INFORMATION,
				// "Error handler chain invoked for: {0}", throwable);
				// for (int i = 0; i < errorHandlers.length; i++) {
				// try {
				// Log.log(Log.DEBUG, "Error handler is {0}",
				// errorHandlers[i].getClass());
				// errorHandlers[i].handle(throwable, request, response);
				// Log.log(Log.DEBUG, "Error handler consumed throwable.");
				// return;
				// } catch (Throwable t) {
				// throwable = t;
				// if (throwable instanceof ErrorHandlerException) {
				// throwable = ((ErrorHandlerException)
				// throwable).getThrowable();
				// Log.log(Log.DEBUG,
				// "Error chain circumvented with: {0}",
				// throwable);
				// break;
				// }
				// Log.log(Log.DEBUG, "Handler output: {0}", throwable);
				// }
				// }
				Log.log(Log.INFORMATION, "Unhandled error: {0}", throwable);
				if (throwable instanceof ServletException) {
					throw (ServletException) throwable;
				} else if (throwable instanceof IOException) {
					throw (IOException) throwable;
				} else if (throwable instanceof RuntimeException) {
					throw (RuntimeException) throwable;
				} else if (throwable instanceof Error) {
					throw (Error) throwable;
				} else {
					throw new ServletException(throwable);
				}
			}
		} else {
			Log.log(Log.INFORMATION, "Unrecognized method: "
					+ request.getMethod());
			response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		}
	}

	/**
	 * Returns the <code>MethodHandler</code> for the specified method.
	 * 
	 * @param method
	 *            The HTTP method (GET, POST, PUT, etc.) being handled.
	 * @return A <code>MethodHandler</code> capable of servicing the request
	 *         using the given method.
	 */
	protected MethodHandler getHandler(String method) {
		if (method == null)
			return null;
		return (MethodHandler) handlers.get(method.toUpperCase());
	}

    private void initLockManager(ServletConfig config) throws ServletException {

        String factoryClass = LockManagerFactory.class.getName();
        String lockProvider = config.getInitParameter(factoryClass);
        if (lockProvider != null) {
            try {
                System.setProperty(factoryClass, lockProvider);
            } catch (Exception ignore) { }
        }

        try {
            LockManager lockManager = null;
            LockManagerFactory factory = LockManagerFactory.newInstance();
            if (factory != null) {
                Properties properties = new Properties();
                String prefix = factoryClass + ".";
                int prefixLength = prefix.length();
                Enumeration parameters = config.getInitParameterNames();
                while (parameters.hasMoreElements()) {
                    String param = (String) parameters.nextElement();
                    if (!param.startsWith(prefix)) continue;
                    String property = param.substring(prefixLength);
                    properties.setProperty(property,
                            config.getInitParameter(param));
                }

                if (Log.getThreshold() < Log.INFORMATION) {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    properties.list(new PrintStream(stream));
                    Object[] args = new Object[] { factory.getClass(), stream };
                    Log.log(Log.DEBUG,
                            "Initializing lock manager factory {0}):\n{1}",
                                    args);
                }

                factory.setProperties(properties);
                lockManager = factory.newLockManager();
            }

            if (lockManager != null) {
                config.getServletContext().setAttribute(LOCK_MANAGER,
                        lockManager);
                Log.log(Log.DEBUG, "Installed lock manager: {0}",
                        lockManager.getClass().getName());
            } else {
                Log.log(Log.INFORMATION,
                        "No lock manager available, locking support disabled.");
            }

        } catch (Exception ex) {
            Log.log(Log.CRITICAL, "Unable to obtain lock manager: {0}", ex);
            if (ex instanceof ServletException) throw (ServletException) ex;
            if (ex instanceof RuntimeException) throw (RuntimeException) ex;
            throw new ServletException(ex);
        }
    }


	private void initHandlers(ServletConfig config) throws ServletException {
		handlers.clear();
		handlers.put("OPTIONS", new DefaultOptionsHandler());
		handlers.put("HEAD", new DefaultHeadHandler());
		handlers.put("GET", new DefaultGetHandler());
		handlers.put("POST", new DefaultPostHandler());
		handlers.put("DELETE", new DefaultDeleteHandler());
		handlers.put("PROPFIND", new DefaultPropfindHandler());
		handlers.put("PROPPATCH", new DefaultProppatchHandler());
		handlers.put("COPY", new DefaultCopyHandler());
		handlers.put("MOVE", new DefaultMoveHandler());
		handlers.put("PUT", new DefaultPutHandler());
		handlers.put("MKCOL", new DefaultMkcolHandler());
		if (config.getServletContext().getAttribute(LOCK_MANAGER) != null) {
			handlers.put("LOCK", new DefaultLockHandler());
			handlers.put("UNLOCK", new DefaultUnlockHandler());
		}
		Enumeration parameters = config.getInitParameterNames();
		while (parameters.hasMoreElements()) {
			String name = (String) parameters.nextElement();
			if (!name.startsWith("handler."))
				continue;
			String method = name.substring(8);
			try {
				handlers.put(method.toUpperCase(), Class.forName(
						config.getInitParameter(name)).newInstance());
				Log.log(Log.DEBUG, "Created handler for {0}: {1}",
						new Object[] { method,
								handlers.get(method.toUpperCase()) });
			} catch (Exception ex) {
				String message = DavisUtilities.getResource(Davis.class,
						"cantCreateHandler", new Object[] { method, ex }, null);
				Log.log(Log.CRITICAL, message + "\n{0}", ex);
				throw new UnavailableException(message);
			}
		}
		Iterator iterator = handlers.values().iterator();
		while (iterator.hasNext()) {
			((MethodHandler) iterator.next()).init(config);
		}
	}

	// private void initErrorHandlers(ServletConfig config)
	// throws ServletException {
	// List errorHandlers = new ArrayList();
	// String errorHandlerClasses = config.getInitParameter("errorHandlers");
	// if (errorHandlerClasses == null) {
	// errorHandlerClasses =
	// "smbdav.DefaultAuthErrorHandler smbdav.DefaultIOErrorHandler";
	// }
	// StringTokenizer tokenizer = new StringTokenizer(errorHandlerClasses);
	// while (tokenizer.hasMoreTokens()) {
	// String errorHandler = tokenizer.nextToken();
	// try {
	// errorHandlers.add(Class.forName(errorHandler).newInstance());
	// Log.log(Log.DEBUG, "Created error handler: " + errorHandler);
	// } catch (Exception ex) {
	// String message = DavisUtilities.getResource(Davis.class,
	// "cantCreateErrorHandler",
	// new Object[] { errorHandler, ex }, null);
	// Log.log(Log.CRITICAL, message + "\n{0}", ex);
	// throw new UnavailableException(message);
	// }
	// }
	// Iterator iterator = errorHandlers.iterator();
	// while (iterator.hasNext()) {
	// ((ErrorHandler) iterator.next()).init(config);
	// }
	// this.errorHandlers = (ErrorHandler[])
	// errorHandlers.toArray(new ErrorHandler[0]);
	// }

	// private void initFilter(ServletConfig config) throws ServletException {
	// String fileFilters = config.getInitParameter("fileFilters");
	// if (fileFilters == null) return;
	// List filters = new ArrayList();
	// StringTokenizer tokenizer = new StringTokenizer(fileFilters);
	// while (tokenizer.hasMoreTokens()) {
	// String filter = tokenizer.nextToken();
	// try {
	// SmbFileFilter fileFilter = (SmbFileFilter) Class.forName(
	// config.getInitParameter(filter)).newInstance();
	// Log.log(Log.DEBUG, "Created filter {0}: {1}",
	// new Object[] { filter, fileFilter.getClass() });
	// if (fileFilter instanceof DavenportFileFilter) {
	// Properties properties = new Properties();
	// Enumeration parameters = config.getInitParameterNames();
	// String prefix = filter + ".";
	// int prefixLength = prefix.length();
	// while (parameters.hasMoreElements()) {
	// String parameter = (String) parameters.nextElement();
	// if (parameter.startsWith(prefix)) {
	// properties.setProperty(
	// parameter.substring(prefixLength),
	// config.getInitParameter(parameter));
	// }
	// }
	// if (Log.getThreshold() < Log.INFORMATION) {
	// ByteArrayOutputStream stream =
	// new ByteArrayOutputStream();
	// properties.list(new PrintStream(stream));
	// Object[] args = new Object[] { filter,
	// fileFilter.getClass(), stream };
	// Log.log(Log.DEBUG,
	// "Initializing filter \"{0}\" ({1}):\n{2}",
	// args);
	// }
	// ((DavenportFileFilter) fileFilter).init(properties);
	// }
	// filters.add(fileFilter);
	// } catch (Exception ex) {
	// String message = DavisUtilities.getResource(Davis.class,
	// "cantCreateFilter", new Object[] { filter, ex }, null);
	// Log.log(Log.CRITICAL, message + "\n{0}", ex);
	// throw new UnavailableException(message);
	// }
	// }
	// if (!filters.isEmpty()) {
	// this.filter = new ResourceFilter((SmbFileFilter[])
	// filters.toArray(new SmbFileFilter[0]));
	// config.getServletContext().setAttribute(RESOURCE_FILTER,
	// this.filter);
	// Log.log(Log.DEBUG, "Filter installed.");
	// }
	// }

	private void fail(String server, HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		if (server != null) {
			HttpSession session = request.getSession(false);
			if (session != null) {
				Map credentials = (Map) session.getAttribute(CREDENTIALS);
				if (credentials != null) {
					credentials.remove(server);
				}
				Log.log(Log.DEBUG, "Removed credentials for \"{0}\".", server);
			}
		}
		try {
			response.reset();
		} catch (IllegalStateException ex) {
			Log.log(Log.DEBUG, "Unable to reset response (already committed).");
		}
		// if (enableNtlm) {
		// Log.log(Log.DEBUG, "Requesting NTLM Authentication.");
		// response.setHeader("WWW-Authenticate", "NTLM");
		// }
		// boolean usingBasic = (acceptBasic || enableBasic) &&
		// (insecureBasic || request.isSecure());
		// if (usingBasic && enableBasic) {
		Log.log(Log.DEBUG, "Requesting Basic Authentication.");
		response.addHeader("WWW-Authenticate", "Basic realm=\"" + realm + "\"");
//		response.addHeader("WWW-Authenticate", "Digest realm=\"" + realm + "\", algorithm=MD5, domain=\""+ "eResearchSA" +"\", nonce=\""+newNonce(request)+"\",qop=\"auth\"");
		// }
		// if (closeOnAuthenticate) {
		// Log.log(Log.DEBUG, "Closing HTTP connection.");
		// response.setHeader("Connection", "close");
		// }
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.flushBuffer();
	}

    public String newNonce(HttpServletRequest request)
    {
         long ts=request.getSession().getCreationTime();
        long sk=nonceSecret;
         
        byte[] nounce = new byte[24];
         for (int i=0;i<8;i++)
         {
             nounce[i]=(byte)(ts&0xff);
             ts=ts>>8;
             nounce[8+i]=(byte)(sk&0xff);
            sk=sk>>8;
         }
        
         byte[] hash=null;
        try
         {
            MessageDigest md = MessageDigest.getInstance("MD5");
             md.reset();
             md.update(nounce,0,16);
             hash = md.digest();
        }
       catch(Exception e)
         {
            Log.log(Log.WARNING,e);
         }
        
         for (int i=0;i<hash.length;i++)
         {
            nounce[8+i]=hash[i];
             if (i==23)
                break;
       }
        
        return Base64.toString(nounce);
    }

}
