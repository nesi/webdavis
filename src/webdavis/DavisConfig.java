package webdavis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletConfig;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

public class DavisConfig {
//	private static DavisConfig self = null;
	/**
	 * The name of the servlet context attribute containing the charset used to
	 * interpret request URIs.
	 */
	public static final String REQUEST_URI_CHARSET = "request-uri.charset";	
	public static final String VERSION_FILE = "/WEB-INF/davis.version";
	
	private ServletConfig servletConfig;
	
	private String defaultDomain;
	private String realm;
	private String contextBase;
	private String contextBaseHeader;
	private boolean alwaysAuthenticate;
	private boolean acceptBasic;
//	private boolean enableBasic;
	private boolean closeOnAuthenticate;
	private String insecureConnection;
	private String serverName;
	private int serverPort;
	private String defaultResource;
	private String zoneName;
	private String defaultIdp;
	private String serverType;
	private String myproxyServer;
	private String proxyHost;
	private String proxyPort;
	private String proxyUsername;
	private String proxyPassword;
	private String anonymousUsername;
	private String anonymousPassword;
	private List<String> anonymousCollections;
	private String sharedTokenHeaderName;
	private String commonNameHeaderName;
	private String adminCertFile;
	private String adminKeyFile;
	private String organisationName;
	private String organisationLogo;
	private String organisationLogoGeometry;
	private String organisationSupport;
	private String helpURL;
	private String favicon;
	private String dojoroot;
	private String jargonDebug;
    private long maximumXmlRequest;
    private String displayMetadata;
    private String authClass;
    private String appVersion = "unknown";
    private String requiredDojoVersion = "unknown";
    private String logoutReturnURL;
    private String loginImage;
    private String loginHelp;
    private boolean disableReplicasButton;
    private int ghostBreadcrumb=0;
    private int ghostTrashBreadcrumb=0;
    private List<String> administrators = new ArrayList<String>();
    private String uiIncludeHead;
    private String uiIncludeBodyHeader;
    private String uiIncludeBodyFooter;
    private String sharingKey;
    private String sharingUser;
    private String sharingURLPrefix;
    private String sharingPassword;
    private String sharingHost;
    private int sharingPort;
    private String sharingZone;
    private String shibInitPath;
    private List<String> webdavUserAgents = new ArrayList<String>();
    private List<String> browserUserAgents = new ArrayList<String>();
    private String insecureLoginText;
    
    // General parameter substitutions for HTML file (substitutions not related to a file or request)
	private Hashtable<String, String> substitutions;

	public String getAuthClass() {
		return authClass;
	}

	private Properties configProperties = new Properties();
	private Hashtable<String, JSONObject> dynamicObjects;
	
	public DavisConfig() {
	}

//	public static DavisConfig getInstance() {
//		return getInstance(true);
//	}
	
//	public static DavisConfig getInstance(boolean create) {
//		if (self == null && create) {
//			self = new DavisConfig();
//		}
//		return self;
//	}

	public String getInitParameter(String key, boolean trim) {
		String s = getInitParameter(key, null);
		if (s != null && trim)
			s = s.trim();
		return s;
	}
//	public String getInitParameter(String key) {
//		
//		return getInitParameter(key, null);
//	}
	
	public String getInitParameter(String key, String defaultValue) {
		
//System.err.print("looking for key "+key);		
		String value = configProperties.getProperty(key);
//System.err.println(" - returning: "+value);
		if (value == null)
			return defaultValue;
		return value;
	}
	
	public Hashtable<String, JSONObject> getDynamicObjects() {
		
		return dynamicObjects;
	}
		
	public void findDynamicObjects() {

//		ArrayList declarations = new ArrayList();
		dynamicObjects = new Hashtable<String, JSONObject>();
		Enumeration<String> propertyNames = (Enumeration<String>)configProperties.propertyNames();
		while (propertyNames.hasMoreElements()) {
			String propertyName = propertyNames.nextElement();
			if (propertyName.startsWith("dynamicobject")) {  
				JSONObject declaration = (JSONObject)JSONValue.parse(configProperties.getProperty(propertyName));
				if (declaration == null || declaration.get("name") == null) {
					System.err.println("ERROR: Failed to parse declaration for "+propertyName+" - ignoring");
					continue;
				}
//System.err.println("adding rule for "+propertyName+" = "+configProperties.getProperty(propertyName)+" = "+rule);
				String name = (String)declaration.get("name");
				dynamicObjects.put(name, declaration);
			}
		}
//		return (JSONObject[]) declarations.toArray(new JSONObject[0]);
	}
	
	public JSONObject getDynamicObject(String name) {
		
		return dynamicObjects.get(name);
	}
	
	public String getInitParameters() {
		
		String s = "";
		Enumeration<Object> keys = configProperties.keys();
		while (keys.hasMoreElements()) {
			String key = (String)keys.nextElement();
			s += key+"="+configProperties.getProperty(key)+"\n";
		}
		return s;
	}
	
	public void readVersion(ServletConfig config) {

	    try {
//			BufferedReader reader = new BufferedReader(new InputStreamReader(config.getServletContext().getResourceAsStream(VERSION_FILE)));
			Properties properties = new Properties();
			InputStream stream = config.getServletContext().getResourceAsStream(VERSION_FILE);
			properties.load(stream);
//			appVersion = reader.readLine();
			appVersion = properties.getProperty("appVersion");
			requiredDojoVersion = properties.getProperty("requiredDojoVersion");
//			reader.close();
			stream.close();
		} catch (IOException e) {
        	System.err.println("WARNING: Failed to read application version file "+VERSION_FILE+": "+e);
		}
	}
	
	public void initConfig() {
		initConfig(null);
	}
	
	public void initConfig(ServletConfig config) {

		if (config == null)
			config = servletConfig;
		servletConfig = config;
		readVersion(config);
		String filesList = config.getInitParameter("config-files");
		if (filesList != null) {
			String[] configFileNames = filesList.split(" *, *");
			configProperties = new Properties();
			for (int i = 0; i < configFileNames.length; i++) {
				String fileName = configFileNames[i].trim();
				System.err.println("Loading config file: "+fileName);
				if (fileName.startsWith("/") || (fileName.length() > 2 && fileName.charAt(1)==':')){
					Properties properties = new Properties();
			        InputStream stream = null;
			        try {
			            stream = new FileInputStream(new File(fileName));
						properties.load(stream);
						stream.close();
						configProperties.putAll(properties);
			        } catch (Exception e) {
			        	System.err.println("WARNING: Can't load config file '"+fileName+"' - skipping");
			        	continue;
			        }   
				}else{
					fileName = "/WEB-INF/"+fileName;
					Properties properties = new Properties();
			        InputStream stream = null;
			        try {
			            stream = config.getServletContext().getResourceAsStream(fileName);
						properties.load(stream);
						stream.close();
						configProperties.putAll(properties);
			        } catch (Exception e) {
			        	System.err.println("WARNING: Can't load config file '"+fileName+"' - skipping");
			        	continue;
			        }   
				}
			}
		}
		findDynamicObjects();

		String requestUriCharset = getInitParameter(REQUEST_URI_CHARSET, "UTF-8").trim();
//		if (requestUriCharset == null)
//			requestUriCharset = "UTF-8";
		
		String logProviderName = Log.class.getName();
		String logProvider = getInitParameter(logProviderName, true);
		//System.out.println(logProviderName);
		//System.out.println(logProvider);
		if (logProvider != null) 
			try {
				System.setProperty(logProviderName, logProvider);
			} catch (Exception ignore) {}
		String logThreshold = getInitParameter(logProviderName+ ".threshold", true);
		if (logThreshold != null) 
			try {
				System.setProperty(logProviderName + ".threshold", logThreshold); 
				for (int i = 0; i < Log.LEVELNAMES.length; i++)
					if (Log.LEVELNAMES[i].toLowerCase().equals(logThreshold.toLowerCase()))
						Log.setThreshold(i);
			} catch (Exception ignore) {}
		
		// requestUriCharset = "ISO-8859-1";
		contextBase = getInitParameter("contextBase", true);
		contextBaseHeader = getInitParameter("contextBaseHeader", true);
		config.getServletContext().setAttribute(REQUEST_URI_CHARSET, requestUriCharset);
		acceptBasic = Boolean.valueOf(getInitParameter("acceptBasic", "false").trim()).booleanValue();
		closeOnAuthenticate = Boolean.valueOf(getInitParameter("closeOnAuthenticate", "false").trim()).booleanValue();
		alwaysAuthenticate = Boolean.valueOf(getInitParameter("alwaysAuthenticate", "true").trim()).booleanValue();
		insecureConnection = getInitParameter("insecureConnection", "block").trim();

		defaultIdp = getInitParameter("default-idp", true);
		serverType = getInitParameter("server-type", true);
		myproxyServer = getInitParameter("myproxy-server", true);
		defaultDomain = getInitParameter("default-domain", true);
		serverPort = 1247;
		try {
			serverPort = Integer.parseInt(getInitParameter("server-port", true));
		} catch (Exception _e) {}
		jargonDebug = getInitParameter("jargon.debug", "WARN").trim();
		serverName = getInitParameter("server-name", true);
		defaultResource = getInitParameter("default-resource", true);
		zoneName = getInitParameter("zone-name", true);
		proxyHost = getInitParameter("proxy-host", true);
		proxyPort = getInitParameter("proxy-port", true);
		proxyUsername = getInitParameter("proxy-username", true);
		proxyPassword = getInitParameter("proxy-password", /*false*/true);

		sharedTokenHeaderName = getInitParameter("shared-token-header-name", true);
		commonNameHeaderName = getInitParameter("cn-header-name", true);
		adminCertFile = getInitParameter("admin-cert-file", true);
		adminKeyFile = getInitParameter("admin-key-file", true);
		String anonymousCredentials = getInitParameter("anonymousCredentials", true);
		if (anonymousCredentials != null && anonymousCredentials.length() > 0 && anonymousCredentials.indexOf(":") > 0) {
			anonymousUsername = anonymousCredentials.split(":")[0];
			anonymousPassword = anonymousCredentials.split(":")[1];
		}
		String s = getInitParameter("anonymousCollections", true);
		if (s != null && s.length() > 0) 
			anonymousCollections = Arrays.asList(s.split(" *, *"));
		s = getInitParameter("webdavUserAgents", true);
		if (s != null && s.length() > 0) 
			webdavUserAgents = Arrays.asList(s.split(" *, *"));
		s = getInitParameter("browserUserAgents", true);
		if (s != null && s.length() > 0) 
			browserUserAgents = Arrays.asList(s.split(" *, *"));
		realm = getInitParameter("authentication-realm", "Davis").trim();
		organisationName = getInitParameter("organisation-name", "Davis").trim();
		organisationLogo = getInitParameter("organisation-logo", "").trim();
		organisationLogoGeometry = getInitParameter("organisation-logo-geometry", "").trim();
		organisationSupport = getInitParameter("organisation-support", "user support at your organisation").trim();
		logoutReturnURL = getInitParameter("logout-return-url", "").trim();
		loginImage = getInitParameter("login-image", "").trim();
	    loginHelp = getInitParameter("login-help", "/").trim();
		helpURL = getInitParameter("helpURL", "For help, please contact user support at your organisation").trim();
		favicon = getInitParameter("favicon", "").trim();		
		dojoroot = getInitParameter("dojoroot", "").trim();
        s = getInitParameter("maximumXmlRequest", true);
        maximumXmlRequest = (s != null) ? Long.parseLong(s) : 20000l;
		sharingKey = getInitParameter("sharing-key", "").trim();
		if (sharingKey.length() == 0)
			sharingKey = null;
		sharingUser = getInitParameter("sharing-user", "").trim();
		sharingZone = getInitParameter("sharing-zone", "").trim();
		if (sharingUser.length() == 0)
			sharingUser = null;
        sharingURLPrefix = getInitParameter("sharing-URL-prefix", "").trim();
        sharingHost = getInitParameter("sharing-host", "").trim();
        sharingPassword = getInitParameter("sharing-password", "").trim();
		s = getInitParameter("sharing-port", "0").trim();
		try {
			sharingPort = Integer.parseInt(s);
		} catch (Exception e) {}
        displayMetadata = getInitParameter("displayMetadata", "").trim();
        authClass = getInitParameter("authClass", true);
        disableReplicasButton = Boolean.valueOf(getInitParameter("disable-replicas-button", "false").trim()).booleanValue();
		s = getInitParameter("ghost-breadcrumb", "0").trim();
		try {
			ghostBreadcrumb = Integer.parseInt(s);
		} catch (Exception e) {}
		s = getInitParameter("ghost-trash-breadcrumb", "0").trim();
		try {
			ghostTrashBreadcrumb = Integer.parseInt(s);
		} catch (Exception e) {}
		String admins = getInitParameter("administrators", "").trim();
		if (admins != null) 
			administrators = new ArrayList<String>(Arrays.asList(admins.split(" *, *")));
		uiIncludeHead = getInitParameter("ui-include-head", "").trim();
		uiIncludeBodyHeader = getInitParameter("ui-include-body-header", "").trim();
		uiIncludeBodyFooter = getInitParameter("ui-include-body-footer", "").trim();
		shibInitPath = getInitParameter("shib-init-path", "/Shibboleth.sso/DS").trim();
		insecureLoginText = getInitParameter("insecure-login-text", "via HTTP").trim();
		
		Log.log(Log.DEBUG, "Logging initialized.");
		if (Log.getThreshold() < Log.INFORMATION) 
			Log.log(Log.DEBUG, "Configuration items:\n"+getInitParameters());
		
		jargonDebug = getJargonDebug();
		if (jargonDebug != null) {
			Level level = Level.toLevel(jargonDebug, Level.WARN);
			Logger.getRootLogger().setLevel(level);
			Log.log(Log.INFORMATION, "Jargon logging level set to "+level);
		}
		initSubstitutions();
	}

	private void initSubstitutions() {
		
		substitutions = new Hashtable<String, String>();
		substitutions.put("appversion", getAppVersion());
		substitutions.put("authenticationrealm", getRealm());
		substitutions.put("organisationname", getOrganisationName());
		substitutions.put("organisationlogo", getOrganisationLogo());
		substitutions.put("favicon", getFavicon());
		substitutions.put("displayMetadata", getDisplayMetadata());
		String s = getAnonymousUsername();
		if (s == null)
			s = "";
		int i = s.lastIndexOf('\\');
		if (i > -1)
			s = s.substring(i+1);
		substitutions.put("anonymoususername", s);
		String[] geom = null;
		String geomString = getOrganisationLogoGeometry();
		String w = "";
		String h = "";
		if (geomString != null) {
			try {
				geom = geomString.split("x");
				w = geom[0];
				h = geom[1];
			} catch (Exception e) {}
		}
		substitutions.put("organisationlogowidth", w);
		substitutions.put("organisationlogoheight", h);
		substitutions.put("organisationsupport", getOrganisationSupport());
		substitutions.put("helpurl", getHelpURL());
		substitutions.put("requireddojoversion", getRequiredDojoVersion());
		substitutions.put("loginimage", getLoginImage());
		substitutions.put("loginhelp", getLoginHelp());
	}

	public void refresh() {
		
		initConfig();
	}
	
	public String getDefaultDomain() {
		return defaultDomain;
	}

	public void setDefaultDomain(String defaultDomain) {
		this.defaultDomain = defaultDomain;
	}

	public String getRealm() {
		return realm;
	}

	public void setRealm(String realm) {
		this.realm = realm;
	}

	public String getContextBase() {
		return contextBase;
	}

	public void setContextBase(String contextBase) {
		this.contextBase = contextBase;
	}

	public String getContextBaseHeader() {
		return contextBaseHeader;
	}

	public void setContextBaseHeader(String contextBaseHeader) {
		this.contextBaseHeader = contextBaseHeader;
	}

	public boolean isAlwaysAuthenticate() {
		return alwaysAuthenticate;
	}

	public void setAlwaysAuthenticate(boolean alwaysAuthenticate) {
		this.alwaysAuthenticate = alwaysAuthenticate;
	}

	public boolean isAcceptBasic() {
		return acceptBasic;
	}

	public void setAcceptBasic(boolean acceptBasic) {
		this.acceptBasic = acceptBasic;
	}

//	public boolean isEnableBasic() {
//		return enableBasic;
//	}
//
//	public void setEnableBasic(boolean enableBasic) {
//		this.enableBasic = enableBasic;
//	}

	public boolean isCloseOnAuthenticate() {
		return closeOnAuthenticate;
	}

	public void setCloseOnAuthenticate(boolean closeOnAuthenticate) {
		this.closeOnAuthenticate = closeOnAuthenticate;
	}

	public String getInsecureConnection() {
		return insecureConnection;
	}

	public void setInsecureConnection(String insecureConnection) {
		this.insecureConnection = insecureConnection;
	}

	public String getServerName() {
		return serverName;
	}

	public void setServerName(String serverName) {
		this.serverName = serverName;
	}

	public int getServerPort() {
		return serverPort;
	}

	public void setServerPort(int serverPort) {
		this.serverPort = serverPort;
	}

	public String getDefaultResource() {
		return defaultResource;
	}

	public void setDefaultResource(String defaultResource) {
		this.defaultResource = defaultResource;
	}

	public String getZoneName() {
		return zoneName;
	}

	public void setZoneName(String zoneName) {
		this.zoneName = zoneName;
	}

	public String getDefaultIdp() {
		return defaultIdp;
	}

	public void setDefaultIdp(String defaultIdp) {
		this.defaultIdp = defaultIdp;
	}

	public String getServerType() {
		return serverType;
	}

	public void setServerType(String serverType) {
		this.serverType = serverType;
	}

	public String getMyproxyServer() {
		return myproxyServer;
	}

	public void setMyproxyServer(String myproxyServer) {
		this.myproxyServer = myproxyServer;
	}

	public String getProxyHost() {
		return proxyHost;
	}

	public void setProxyHost(String proxyHost) {
		this.proxyHost = proxyHost;
	}

	public String getProxyPort() {
		return proxyPort;
	}

	public void setProxyPort(String proxyPort) {
		this.proxyPort = proxyPort;
	}

	public String getProxyUsername() {
		return proxyUsername;
	}

	public void setProxyUsername(String proxyUsername) {
		this.proxyUsername = proxyUsername;
	}

	public String getProxyPassword() {
		return proxyPassword;
	}

	public void setProxyPassword(String proxyPassword) {
		this.proxyPassword = proxyPassword;
	}

	public String getAnonymousUsername() {
		return anonymousUsername;
	}

	public void setAnonymousUsername(String anonymousUsername) {
		this.anonymousUsername = anonymousUsername;
	}

	public String getAnonymousPassword() {
		return anonymousPassword;
	}

	public void setAnonymousPassword(String anonymousPassword) {
		this.anonymousPassword = anonymousPassword;
	}

	public List<String> getAnonymousCollections() {
		return anonymousCollections;
	}

	public void setAnonymousCollections(List<String> anonymousCollections) {
		this.anonymousCollections = anonymousCollections;
	}

	public String getSharedTokenHeaderName() {
		return sharedTokenHeaderName;
	}

	public void setSharedTokenHeaderName(String sharedTokenHeaderName) {
		this.sharedTokenHeaderName = sharedTokenHeaderName;
	}

	public String getCommonNameHeaderName() {
		return commonNameHeaderName;
	}

	public void setCommonNameHeaderName(String commonNameHeaderName) {
		this.commonNameHeaderName = commonNameHeaderName;
	}

	public String getAdminCertFile() {
		return adminCertFile;
	}

	public void setAdminCertFile(String adminCertFile) {
		this.adminCertFile = adminCertFile;
	}

	public String getAdminKeyFile() {
		return adminKeyFile;
	}

	public void setAdminKeyFile(String adminKeyFile) {
		this.adminKeyFile = adminKeyFile;
	}

	public String getOrganisationName() {
		return organisationName;
	}
	
	public String getOrganisationLogo() {
		return organisationLogo;
	}
	
	public String getOrganisationSupport() {
		return organisationSupport;
	}
	
	public String getLogoutReturnURL() {
		return logoutReturnURL;
	}
	
	public String getLoginImage() {
		return loginImage;
	}
	
	public String getLoginHelp() {
		return loginHelp;
	}
	
	public String getHelpURL() {
		return helpURL;
	}
	
	public String getFavicon() {
		return favicon;
	}
	
	public String getOrganisationLogoGeometry() {
		return organisationLogoGeometry;
	}
	
	public String getDojoroot() {
		return dojoroot;
	}
	
	public String getJargonDebug() {
		return jargonDebug;
	}
	
	public long getMaximumXmlRequest() {
		return maximumXmlRequest;
	}
	
	public String getDisplayMetadata() {
		return displayMetadata;
	}
	
	public String getAppVersion() {
		return appVersion;
	}
	
	public String getRequiredDojoVersion() {
		return requiredDojoVersion;
	}
	
	public boolean getDisableReplicasButton() {
		return disableReplicasButton;
	}
	
	public int getGhostBreadcrumb() {
		return ghostBreadcrumb;
	}
	
	public int getGhostTrashBreadcrumb() {
		return ghostTrashBreadcrumb;
	}
	
	public List<String> getAdministrators() {
		return administrators;
	}
	
	public List<String> getWebdavUserAgents() {
		return webdavUserAgents;
	}
	
	public List<String> getBrowserUserAgents() {
		return browserUserAgents;
	}
	
	public String getUIIncludeHead() {
		return uiIncludeHead;
	}
	
	public String getUIIncludeBodyHeader() {
		return uiIncludeBodyHeader;
	}
	
	public String getUIIncludeBodyFooter() {
		return uiIncludeBodyFooter;
	}

	public String getSharingUser() {
		return sharingUser;
	}
	
	public String getSharingKey() {
		return sharingKey;
	}
	
	public String getSharingURLPrefix() {
		return sharingURLPrefix;
	}
	
	public String getSharingPassword() {
		return sharingPassword;
	}
	
	public String getSharingHost() {
		return sharingHost;
	}
	
	public int getSharingPort() {
		return sharingPort;
	}
	
	public String getSharingZone() {
		return sharingZone;
	}
	
	public String getShibInitPath() {
		return shibInitPath;
	}
	
	public Hashtable<String, String> getSubstitutions() {
		return substitutions;
	}
	
	public String getInsecureLoginText() {
		return insecureLoginText;
	}
}
