package webdavis;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
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
	
	static DavisConfig davisConfig = null;

	// private ErrorHandler[] errorHandlers;

	// private ResourceFilter filter;
	
	static Date profilingTimer = null;	// Used by DefaultGetHandler to measure time spent in parts of the code
	static long lastLogTime = 0;  // Used to log memory usage on a regular basis
	static final long MEMORYLOGPERIOD = 60*60*1000;  // How often log memory usage (in ms)
	static long headroom = Long.MAX_VALUE;
	
//	static final String[] WEBDAVMETHODS = {"propfind", "proppatch", "mkcol", "copy", "move", "lock"};
	static final String[] WEBDAVMETHODS = {"propfind", "proppatch", "lock"}; // Methods that indicate client must be webdav (methods not used by Davis web interface)
	static final String AUTHATTRIBUTENAME = "formauth";
	static final String ISBROWSERATTRIBUTENAME = "isbrowser";
	
	public static DavisConfig getConfig() {
		
		return davisConfig;
	}

	public void init() throws ServletException {
		ServletConfig config = getServletConfig();
		davisConfig = new DavisConfig();
		getConfig().initConfig(config);
		
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
		
		return "free memory: "+Runtime.getRuntime().freeMemory()+" bytes   headroom: "+headroom+" bytes   total memory: "
				+Runtime.getRuntime().totalMemory()+" bytes   max memory: "+Runtime.getRuntime().maxMemory()+" bytes";
	}
	
	private String requestToString(HttpServletRequest request, int debugLevel) {
		
		String s = "";
		
		if (debugLevel < Log.INFORMATION) {
			s += "    "+request.getMethod()+" request for "+request.getRequestURL()+"\n"+
				 "    uri: "+request.getRequestURI()+"\n"+
				 "    queryString: "+request.getQueryString()+"\n"+
			     "    pathInfo: "+request.getPathInfo()+"\n"+
				 "    headers:\n";
			Enumeration headerNames = request.getHeaderNames();
			while (headerNames.hasMoreElements()) {
				String headerName = (String)headerNames.nextElement();
				s += "        "+headerName+": ";
				if (headerName.equalsIgnoreCase("Authorization"))
					s += "censored\n";
				else {
					Enumeration headerValues = request.getHeaders(headerName);
					while (headerValues.hasMoreElements()) {
						s += headerValues.nextElement();
						if (headerValues.hasMoreElements())
							s += ", ";
					}
				}
				//if (headerNames.hasMoreElements())
					s += "\n";
			}
		
			s += "    isSecure: "+request.isSecure()+"\n";
			HttpSession httpSession = request.getSession(false);
			if (httpSession != null) {
				s += "    Active HTTP session: "+httpSession.getId()+"\n"; // This is the JSESSIONID cookie
				s += "    Davis session: "+httpSession.getAttribute(SESSION_ID);
			} else 
				s += "    HTTP session not yet established";
		}
		return s;
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

		HttpSession httpSession = request.getSession(false); // Get session but don't create a new one if not found
		String pathInfo = request.getPathInfo();
//		String uri=request.getRequestURI();
//		String queryString = request.getQueryString();
		
		if (request.getParameter("loginform") != null) {
//			System.err.println("********** got loginform");
			String referrer = request.getHeader("referrer");
			if (referrer == null)
				referrer = request.getHeader("referer");
			if (referrer == null) {
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Your browser doesn't provide a referrer header field. \nPlease contact "+getConfig().getOrganisationSupport());
				response.flushBuffer();
				return;
			}
			String authorization = "Basic "+new String(Base64.encodeBase64((request.getParameter("username").trim()+":"+request.getParameter("password")).getBytes()));
			
			request.getSession().setAttribute(AUTHATTRIBUTENAME, authorization); // Save auth info in httpsession - to be retrieved below
			if (referrer.toLowerCase().endsWith("?noanon") || referrer.toLowerCase().endsWith("&noanon")) { // trim trailing noanon query if present
				int i = referrer.length()-".noanon".length();
				referrer = referrer.substring(0, i);
			}
			response.addHeader("Location", referrer);
			response.sendError(HttpServletResponse.SC_SEE_OTHER);
			response.flushBuffer();
			return;
		}
			
		if (pathInfo == null || "".equals(pathInfo))
			pathInfo = "/";

		profilingTimer = new Date();		
		Log.log(Log.DEBUG, "Timer started: "+(new Date().getTime()-profilingTimer.getTime()));

		// Log request + header
		Log.log(Log.INFORMATION, "==========> RECEIVED REQUEST:\n"+requestToString(request, Log.getThreshold()));
		
		DavisConfig config=davisConfig;
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
		boolean reset=false;
		AuthorizationProcessor authorizationProcessor = AuthorizationProcessor.getInstance();

		String authorization = null;
		// Look for a form-based auth atttribute if https and is a browser (attribute is stored in httpsession from form-based login page)
		if (request.isSecure() && isBrowser(request)) { 
			String auth = null;
			if ((auth = (String)request.getSession().getAttribute(AUTHATTRIBUTENAME)) != null) 
				authorization = auth;
		}
		
		if (authorization == null) {
			// Check for basic auth header
			authorization = request.getHeader("Authorization"); 
			if (authorization != null) 
				request.getSession().setAttribute(AUTHATTRIBUTENAME, authorization); // Save auth info in http session in case it's not present in header in later requests (FireFox)
			else
				authorization = (String)request.getSession().getAttribute(AUTHATTRIBUTENAME); // If no auth header, get it from session if saved there from earlier request
		}
		
		// Reset request?
		if (request.getQueryString() != null && request.getQueryString().indexOf("reset") > -1)
			reset=true;
					
		String errorMsg = null;
		// If no auth info in header and http connection - should be shib
		if (authorization == null && !request.isSecure() && config.getInsecureConnection().equalsIgnoreCase("shib")){
			//before login, check if there is shib session
			Log.log(Log.DEBUG, "Trying shib login");
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
					errorMsg = "Shared token '"+sharedToken+"' is not found in HTTP header.";
				} else if (commonName == null || commonName.length() == 0) {
					errorMsg = "Common name '"+commonName+"' is not found in HTTP header.";
				} else if (shibCookieNum > 0) 
					davisSession = authorizationProcessor.getDavisSession(sharedToken, commonName, shibSessionID, reset);
			}
			if (davisSession == null && errorMsg == null)
				errorMsg = "Shibboleth login failed.";
		}else
		// If auth info in header (basic/form auth) but not shib (http or https)
		if (authorization != null) {
			Log.log(Log.DEBUG, "Not shib login but auth info present. Looking for existing session...");
			davisSession = authorizationProcessor.getDavisSession(authorization, reset);
		}
		
		// Anonymous access
		if (davisSession == null && isAnonymousPath(pathInfo) && (request.getQueryString() == null || request.getQueryString().indexOf("noanon") < 0)){
			Log.log(Log.DEBUG, "Path is anonymous, allowing access by anonymous user");
			String authString = "Basic "+Base64.encodeBase64String((config.getAnonymousUsername()+":"+config.getAnonymousPassword()).getBytes());
			davisSession = authorizationProcessor.getDavisSession(authString, reset);
			errorMsg = null;
		}
		// Check that the client's uihandle is known to us . If not, send an error so that UI can reload window.
		String uiHandle = request.getParameter("uihandle");
		if (uiHandle != null && !uiHandle.equals("null")) 
			if (davisSession == null || davisSession.getClientInstance(uiHandle) == null) {	
				String s = "Cache for client with uiHandle="+uiHandle+" not found (server may have been restarted).";
				if (davisSession != null)
					s += " Cache keys:"+davisSession.getClientInstances().keySet();
				Log.log(Log.WARNING,  s);
			//	response.sendError(HttpServletResponse.SC_GONE, "Your client appears to be out of sync with the server (server may have been restarted)");
				response.sendError(HttpServletResponse.SC_GONE, "Access denied - you are not currently logged in");
				response.flushBuffer();
				return;
			}

		// Still no session established, check for error, else tell client with auth to try next
		if (davisSession == null){
			if (errorMsg != null){
				Log.log(Log.DEBUG, "No session found, returning FORBIDDEN with message: "+errorMsg);
				response.sendError(HttpServletResponse.SC_FORBIDDEN, errorMsg);
				response.flushBuffer();
				return;
			}else{
				Log.log(Log.DEBUG, "No session found, calling fail handler.");
				fail(request, response);
				return;
			}
		}
		
		Log.log(Log.DEBUG, "HTTPSession: "+httpSession);
		if (httpSession == null || reset) {
			httpSession = request.getSession();
			Log.log(Log.DEBUG, "Setting Davis session ID: "+davisSession.getSessionID());
			httpSession.setAttribute(SESSION_ID, davisSession.getSessionID());
			davisSession.increaseSharedNumber();
		}
		Log.log(Log.INFORMATION, "Final davisSession: " + davisSession);
		long currentTime = new Date().getTime();
		Log.log(Log.DEBUG, "Time after establishing session: "+(currentTime-profilingTimer.getTime()));

		if (Runtime.getRuntime().freeMemory() < headroom)
			headroom = Runtime.getRuntime().freeMemory();
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
				Log.log(Log.WARNING, "**** UNHANDLED ERROR for:\n"+requestToString(request, Log.DEBUG)+"\n\n    Exception was: "+DavisUtilities.getStackTrace(throwable));
				String firstStackElement = "";
				StackTraceElement[] elements = throwable.getStackTrace();
				if (elements.length > 0)
					firstStackElement = "at "+elements[0].getClassName()+"."+elements[0].getMethodName()+"("+elements[0].getFileName()+":"+elements[0].getLineNumber()+")";
//				if (throwable.getCause() != null && throwable.getCause().getMessage().contains("Broken pipe"))
//				if (throwable.getMessage().contains("Broken pipe") || (throwable.getCause() != null && throwable.getCause().getMessage().contains("Broken pipe")))
				if ((throwable.getMessage() != null && throwable.getMessage().contains("Broken pipe")) || 
					(throwable.getCause() != null && throwable.getCause().getMessage() != null && throwable.getCause().getMessage().contains("Broken pipe")))
					throwable = new Throwable("Your client appears to have disconnected. Please try again, or contact "+config.getOrganisationSupport()+".\n\n    Error was: "+throwable, throwable.getCause());
				else
					throwable = new Throwable("Internal Davis error. Please contact "+config.getOrganisationSupport()+"\n\n    Error was: "+throwable+"\n        "+firstStackElement+"\n        ------------------------\n", throwable.getCause());
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
		Log.log(Log.DEBUG, "Time at end of service: "+(new Date().getTime()-profilingTimer.getTime()));
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
		
		// If we've previously determined browser/webdav client, get result from session
		if ((String)request.getSession().getAttribute(ISBROWSERATTRIBUTENAME) != null)
			return ((String)request.getSession().getAttribute(ISBROWSERATTRIBUTENAME)).toLowerCase().equals("true");
		
		boolean browser = true;		
		String method = request.getMethod();
		String accept = request.getHeader("accept");
		String agent = request.getHeader("user-agent");
		ListIterator<String> webdavAgents = Davis.getConfig().getWebdavUserAgents().listIterator();
		ListIterator<String> browserAgents = Davis.getConfig().getBrowserUserAgents().listIterator();
		while ((agent != null) && webdavAgents.hasNext()) 
			if (agent.toLowerCase().startsWith(webdavAgents.next().toLowerCase())) {
				browser = false;
				break;
			}
		if (browser) { // If agent prefix not in webdavagent items, infer client type
			if (Arrays.asList(WEBDAVMETHODS).contains(method.toLowerCase()))
				browser = false;
			else
			if (accept == null)
				browser = false;
		}

		while ((agent != null) && browserAgents.hasNext()) 
			if (agent.toLowerCase().startsWith(browserAgents.next().toLowerCase())) {
				browser = true;
				break;
			}
		
		Log.log(Log.DEBUG, "isBrowser(): "+browser+" (method="+method+" accept="+accept+")");
		
		// Save decision in session so it's only made once
		request.getSession().setAttribute(ISBROWSERATTRIBUTENAME, ""+browser);
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
				form = DavisUtilities.preprocess(form, getConfig().getGeneralSubstitutions());	// Make general substitutions
				Hashtable<String, String> substitutions = new Hashtable<String, String>();
				String queryString = request.getQueryString();
				if (queryString == null)
					queryString = "";
				else
					queryString = "?"+queryString;
				if (queryString.toLowerCase().endsWith("?noanon") || queryString.toLowerCase().endsWith("&noanon")) { // trim trailing noanon query if present
					int i = queryString.length()-".noanon".length();
					queryString = queryString.substring(0, i);
				}				
				substitutions.put("insecureurl", "<a href=\""+request.getRequestURL().toString().replaceFirst("^https", "http")+queryString+"\">");
				substitutions.put("insecurelogintext", getConfig().getInsecureLoginText());
				if (request.getSession().getAttribute(AUTHATTRIBUTENAME) != null) {	// Form has been submitted and auth failed
					Log.log(Log.DEBUG, "Returning form-based login page with error message to client.");
					substitutions.put("failedmessage", "<span style=\"color:red\">Authentication Failed</span><br><br><small>Please ensure your username and password are correct,<br>and that cookies are enabled in your browser.<br><br></small>");
					request.getSession().removeAttribute(AUTHATTRIBUTENAME);
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
		response.addHeader("WWW-Authenticate", "Basic realm=\"" + getConfig().getRealm() + "\"");
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
    	if (getConfig().getAnonymousCollections()==null||getConfig().getAnonymousCollections().size()==0) return false;
    	for (String s:getConfig().getAnonymousCollections()){
    		if (path.startsWith(s)) return true;
    	}
    	return false;
    }
}
