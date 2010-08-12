package webdavis;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.apache.commons.codec.binary.Base64;

/**
 * This servlet provides a WebDAV gateway to SRB/iRods shared resources.
 * 
 * @author Shunde Zhang
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
	 * The name of the request attribute containing the context base for URL
	 * rewriting.
	 */
	public static final String CONTEXT_BASE = "davis.contextBase";

	/**
	 * The name of the request attribute containing the authenticated principal.
	 */
	public static final String SESSION_ID = "davis.sessionID";

	private final Map<String, MethodHandler> handlers = new HashMap<String, MethodHandler>();

	// private ErrorHandler[] errorHandlers;

	// private ResourceFilter filter;
	
	static Date profilingTimer = null;	// Used by DefaultGetHandler to measure time spent in parts of the code
	static long lastLogTime = 0;  // Used to log memory usage on a regular basis
	static final long MEMORYLOGPERIOD = 60*60*1000;  // How often log memory usage (in ms)
	
	static final String[] WEBDAVMETHODS = {"propfind", "proppatch", "mkcol", "copy", "move", "lock"};
	static final String FORMAUTHATTRIBUTENAME = "formauth";
	

	public void init() throws ServletException {
		ServletConfig config = getServletConfig();
		DavisConfig.getInstance().initConfig(config);
		
//		String logProviderName = Log.class.getName();
//		String logProvider = config.getInitParameter(logProviderName);
//		System.out.println(logProviderName);
//		System.out.println(logProvider);
//		if (logProvider != null) {
//			try {
//				System.setProperty(logProviderName, logProvider);
//			} catch (Exception ignore) {
//			}
//		}
//		String logThreshold = config.getInitParameter(logProviderName
//				+ ".threshold");
//		if (logThreshold != null) {
//			try {
//				System
//						.setProperty(logProviderName + ".threshold",
//								logThreshold);
//			} catch (Exception ignore) {
//			}
//		}
		
		Log.log(Log.DEBUG, "Logging initialized.");
		if (Log.getThreshold() < Log.INFORMATION) 
			Log.log(Log.DEBUG, "Configuration items:\n"+DavisConfig.getInstance().getInitParameters());
		
		String jargonDebug= DavisConfig.getInstance().getJargonDebug();
		if (jargonDebug!=null) {
			Level level = Level.toLevel(jargonDebug, Level.WARN);
			Logger.getRootLogger().setLevel(level);
			Log.log(Log.INFORMATION, "Jargon logging level set to "+level);
		}
		DavisUtilities.init(config);
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
//		context.removeAttribute(REQUEST_URI_CHARSET);
//		Map contMap = (Map) context.getAttribute(Davis.CREDENTIALS);
//		if (contMap!=null){
//			iterator=contMap.keySet().iterator();
//			while (iterator.hasNext()) {
//				((DavisSession)iterator.next()).disconnect();
//			}
//		}
		AuthorizationProcessor.getInstance().destroyConnectionPool();
		Log.log(Log.DEBUG, "Davis finished destroy.");
	}

	protected String getMemoryUsage() {
		
		return "free memory: "+Runtime.getRuntime().freeMemory()+" bytes   total memory: "
				+Runtime.getRuntime().totalMemory()+" bytes   max memory: "+Runtime.getRuntime().maxMemory()+" bytes";
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
	protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		
		String pathInfo = request.getPathInfo();
		String uri=request.getRequestURI();
		String queryString = request.getQueryString();
		
		if (request.getParameter("loginform") != null) {
//			System.err.println("********** got loginform");
			String referrer = request.getHeader("referrer");
			if (referrer == null)
				referrer = request.getHeader("referer");
			if (referrer == null) {
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Your browser doesn't provide a referrer header field. \nPlease contact "+DavisConfig.getInstance().getOrganisationSupport());
				response.flushBuffer();
				return;
			}
			String authorization = "Basic "+new String(Base64.encodeBase64((request.getParameter("username").trim()+":"+request.getParameter("password")).getBytes()));
			
			request.getSession().setAttribute(FORMAUTHATTRIBUTENAME, authorization); // Save auth info in httpsession - to be retrieved below
			
			response.addHeader("Location", referrer);
			response.sendError(HttpServletResponse.SC_SEE_OTHER);
			response.flushBuffer();
			return;
		}
			
		if (pathInfo == null || "".equals(pathInfo))
			pathInfo = "/";

		profilingTimer = new Date();		
		Log.log(Log.DEBUG, "#### Timer started: "+(new Date().getTime()-profilingTimer.getTime()));

		// Log request + header
		Log.log(Log.INFORMATION, "================ Received {0} request for \"{1}\".", new Object[] {request.getMethod(), request.getRequestURL()});
		Log.log(Log.INFORMATION, "uri:"+uri);
		Log.log(Log.INFORMATION, "queryString:"+queryString);
		if (Log.getThreshold() < Log.INFORMATION) {
			Log.log(Log.DEBUG, "pathInfo:\n{0}", pathInfo);
			StringBuffer headers = new StringBuffer();
			Enumeration headerNames = request.getHeaderNames();
			while (headerNames.hasMoreElements()) {
				String headerName = (String) headerNames.nextElement();
				headers.append("    ").append(headerName).append(": ");
				if (!headerName.equalsIgnoreCase("Authorization")){
					Enumeration headerValues = request.getHeaders(headerName);
					while (headerValues.hasMoreElements()) {
						headers.append(headerValues.nextElement());
						if (headerValues.hasMoreElements())
							headers.append(", ");
					}
				}else 
					headers.append("censored");
				if (headerNames.hasMoreElements())
					headers.append("\n");
			}
			Log.log(Log.DEBUG, "Headers:\n{0}", headers);
			Log.log(Log.DEBUG, "isSecure:{0}", request.isSecure());
			HttpSession httpSession = request.getSession(false);
			if (httpSession != null) {
				Log.log(Log.DEBUG, "Active HTTP session: {0}", httpSession.getId()); // This is the JSESSIONID cookie
			} else {
				Log.log(Log.DEBUG, "HTTP session not yet established.");
			}
		}
		
		DavisConfig config=DavisConfig.getInstance();
		String contextBase = config.getContextBase();
		if (config.getContextBaseHeader() != null) {
			String dynamicBase = request.getHeader(config.getContextBaseHeader());
			if (dynamicBase != null)
				contextBase = dynamicBase;
		}
		if (contextBase != null) {
			if (!contextBase.endsWith("/"))
				contextBase += "/";
			request.setAttribute(CONTEXT_BASE, contextBase);
			Log.log(Log.DEBUG, "Using context base: " + contextBase);
		}
		
		// Check if non-secure (http) connection is allowed
		if (config.getInsecureConnection().equalsIgnoreCase("block") && !request.isSecure()){
			Log.log(Log.DEBUG, "Davis is configured to not allow insecure connections.");
			try {
				response.reset();
			} catch (IllegalStateException ex) {
				Log.log(Log.DEBUG, "Unable to reset response (already committed).");
			}
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.flushBuffer();
			return;
		}
		
		DavisSession davisSession = null;
		// Reset connection was requested?
		boolean reset=false;
		AuthorizationProcessor authorizationProcessor = AuthorizationProcessor.getInstance();
		String authorization = null;
		if (!isBrowser(request))	// Only allow Basic auth for webdav 
			authorization = request.getHeader("Authorization"); 
//System.err.println("###############authorization header found");
		if (request.getQueryString() != null && request.getQueryString().indexOf("reset") > -1)
			reset=true;
		
		if (authorization == null && isBrowser(request)) { 
			String auth = null;
			if ((auth = (String)request.getSession().getAttribute(FORMAUTHATTRIBUTENAME)) != null) { // Check if there's an auth attribute stored in httpsession (from form-based login page)
//System.err.println("%%%%%%%%%%%%%%%% Found auth attribute");
				authorization = auth;
			}
		}
					
		String errorMsg = null;
		// If no auth info in header and http connection - should be shib
		if (authorization == null && !request.isSecure() && config.getInsecureConnection().equalsIgnoreCase("shib")){
			//before login, check if there is shib session
			int shibCookieNum = 0;
			Cookie[] cookies = request.getCookies();
			if (cookies != null){
				for (Cookie cookie:cookies)
					if (cookie.getName().startsWith("_shibstate") || cookie.getName().startsWith("_shibsession") || cookie.getName().startsWith("_saml_idp")) 
						shibCookieNum++;
				String sharedToken = request.getHeader(config.getSharedTokenHeaderName());
				String commonName = request.getHeader(config.getCommonNameHeaderName());
				String shibSessionID = request.getHeader("Shib-Session-ID");
				if (sharedToken == null || sharedToken.length() == 0) {
					errorMsg = "Shared token is not found in HTTP header.";
				} else if (commonName == null || commonName.length() == 0) {
					errorMsg = "Common name is not found in HTTP header.";
				} else if (shibCookieNum > 0) 
					davisSession = authorizationProcessor.getDavisSession(sharedToken, commonName, shibSessionID, reset);
			}
			if (davisSession == null && errorMsg == null)
				errorMsg = "Shibboleth login failed.";
		}else
		// If auth info in header but not shib (http or https)
		if (authorization != null){
			davisSession = authorizationProcessor.getDavisSession(authorization, reset);
		} 
		
		// Anonymous access
		if (davisSession == null && isAnonymousPath(pathInfo)){
			String authString = "Basic "+Base64.encodeBase64String((config.getAnonymousUsername()+":"+config.getAnonymousPassword()).getBytes());
			davisSession = authorizationProcessor.getDavisSession(authString, reset);
			errorMsg = null;
		}
				
		// Still no session established, check for error, else tell client with auth to try next
		if (davisSession == null){
			if (errorMsg != null){
				response.sendError(HttpServletResponse.SC_FORBIDDEN, errorMsg);
				response.flushBuffer();
				return;
			}else{
				fail(request, response);
				return;
			}
		}
		
		HttpSession httpSession = request.getSession(false);
		if (httpSession == null || reset) {
			httpSession = request.getSession();
			httpSession.setAttribute(SESSION_ID, davisSession.getSessionID());
			davisSession.increaseSharedNumber();
		}
		Log.log(Log.INFORMATION, "Final davisSession: " + davisSession);
		long currentTime = new Date().getTime();
		Log.log(Log.DEBUG, "#### Time after establishing session: "+(currentTime-profilingTimer.getTime()));
		if (currentTime - lastLogTime >= MEMORYLOGPERIOD) {
			lastLogTime = currentTime;
			Log.log(Log./*INFORMATION*/WARNING, getMemoryUsage());
		}
		MethodHandler handler = getHandler(request.getMethod());
		if (handler != null) {
			try {
				Log.log(Log.DEBUG, "Handler is {0}", handler.getClass());
				handler.service(request, response, davisSession);
			} catch (Throwable throwable) {
				Log.log(Log.WARNING, "Unhandled error: {0}", throwable);
				throwable = new Throwable("Internal Davis error. Please contact "+config.getOrganisationSupport()+".\n\nError was: "+throwable, throwable.getCause());
				if (throwable.getCause() != null && throwable.getCause().getMessage().contains("Broken pipe"))
					throwable = new Throwable("Client appears to have disconnected. Please try again, or contact "+config.getOrganisationSupport()+".\n\nError was: "+throwable, throwable.getCause());
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
			Log.log(Log.INFORMATION, "Unrecognized method: " + request.getMethod());
			response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		}
		Log.log(Log.DEBUG, "#### Time at end of service: "+(new Date().getTime()-profilingTimer.getTime()));
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
 //@TBD move this to davis config file?
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
                    properties.setProperty(property, config.getInitParameter(param));
                }

                if (Log.getThreshold() < Log.INFORMATION) {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    properties.list(new PrintStream(stream));
                    Object[] args = new Object[] { factory.getClass(), stream };
                    Log.log(Log.DEBUG, "Initializing lock manager factory {0}):\n{1}", args);
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
//@TBD move these to davis config file?
		Enumeration parameters = config.getInitParameterNames();
		while (parameters.hasMoreElements()) {
			String name = (String) parameters.nextElement();
			if (!name.startsWith("handler."))
				continue;
			String method = name.substring(8);
			try {
				handlers.put(method.toUpperCase(), (MethodHandler) Class.forName(
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
	
	private boolean isBrowser(HttpServletRequest request) {
		
		boolean browser = true;		
		String method = request.getMethod();
		String accept = request.getHeader("accept");
		Log.log(Log.DEBUG, "in isBrowser(): method="+method+" accept="+accept);
		if (Arrays.asList(WEBDAVMETHODS).contains(method))
			browser = false;
		else
		if (accept == null)
			browser = false;
		return browser;
	}

	private void fail(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//		if (server != null) {
//			HttpSession session = request.getSession(false);
//			if (session != null) {
//				Map credentials = (Map) session.getAttribute(CREDENTIALS);
//				if (credentials != null) {
//					credentials.remove(server);
//				}
//				Log.log(Log.DEBUG, "Removed credentials for \"{0}\".", server);
//			}
//		}
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
		
		if (request.isSecure()) {
//System.err.println("##################### https - returning form");
			boolean browser = isBrowser(request);
			if (browser) {
				Log.log(Log.DEBUG, "Client is using "+(browser ? "a browser" : "webdav"));
				String form = DavisUtilities.loadResource("/WEB-INF/login.html");
				form = DavisUtilities.preprocess(form, DavisUtilities.substitutions);	// Make general substitutions
				Hashtable<String, String> substitutions = new Hashtable<String, String>();
				String queryString = request.getQueryString();
				if (queryString == null)
					queryString = "";
				else
					queryString = "?"+queryString;
				substitutions.put("insecureurl", "<a href=\""+request.getRequestURL().toString().replaceFirst("^https", "http")+queryString+"\">");
				if (request.getSession().getAttribute(FORMAUTHATTRIBUTENAME) != null) {	// Form has been submitted and auth failed
					Log.log(Log.DEBUG, "Returning form-based login page with error message to client.");
					substitutions.put("failedmessage", "<span style=\"color:red\">Authentication Failed</span><br><br><small>Please ensure your username and password are correct,<br>and that cookies are enabled in your browser.<br><br></small>");
					request.getSession().removeAttribute(FORMAUTHATTRIBUTENAME);
				} else {
					if ((request.getHeader("referrer") != null) || (request.getHeader("referer") != null)) // Cookies might be blocked for this site
						substitutions.put("failedmessage", "<small>Please ensure cookies are enabled for this site.</small><br><br>");
					else
						substitutions.put("failedmessage", "");
					Log.log(Log.DEBUG, "Returning form-based login page to client.");
				}
				form = DavisUtilities.preprocess(form, substitutions);

				response.setContentType("text/html; charset=\"utf-8\"");
				OutputStreamWriter out = new OutputStreamWriter(response.getOutputStream());
				out.write(form, 0, form.length());
				out.flush();
				response.flushBuffer();
				return;
			}
		}

		Log.log(Log.DEBUG, "Requesting Basic Authentication.");
		response.addHeader("WWW-Authenticate", "Basic realm=\"" + DavisConfig.getInstance().getRealm() + "\"");
//		response.addHeader("WWW-Authenticate", "Digest realm=\"" + realm + "\", algorithm=MD5, domain=\""+ "eResearchSA" +"\", nonce=\""+newNonce(request)+"\",qop=\"auth\"");
		// }
		// if (closeOnAuthenticate) {
		// Log.log(Log.DEBUG, "Closing HTTP connection.");
		// response.setHeader("Connection", "close");
		// }
		response.sendError(HttpServletResponse.SC_UNAUTHORIZED,"Login failed.");
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.flushBuffer();
	}
	

    private boolean isAnonymousPath(String path){
    	if (DavisConfig.getInstance().getAnonymousCollections()==null||DavisConfig.getInstance().getAnonymousCollections().size()==0) return false;
    	for (String s:DavisConfig.getInstance().getAnonymousCollections()){
    		if (path.startsWith(s)) return true;
    	}
    	return false;
    }
}
