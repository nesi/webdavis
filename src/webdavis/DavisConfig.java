package webdavis;

import java.io.BufferedReader;
import java.io.File;
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

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

public class DavisConfig {
	private static DavisConfig self;
	/**
	 * The name of the servlet context attribute containing the charset used to
	 * interpret request URIs.
	 */
	public static final String REQUEST_URI_CHARSET = "request-uri.charset";
	private String defaultDomain;

	private String realm;

	private String contextBase;

	private String contextBaseHeader;

	private boolean alwaysAuthenticate;

	private boolean acceptBasic;

	private boolean enableBasic;

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
	private String favicon;
	
	private String dojoroot;
	private String jargonDebug;
    private long maximumXmlRequest;
    
    private String displayMetadata;
    private String authClass;

	public String getAuthClass() {
		return authClass;
	}

	private Properties configProperties = new Properties();
	private Hashtable<String, JSONObject> dynamicObjects;
	

	private DavisConfig() {
	}

	public static DavisConfig getInstance() {
		if (self == null) {
			self = new DavisConfig();
		}
		return self;
	}

	public String getInitParameter(String key) {
		
		return getInitParameter(key, null);
	}
	
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
	
	public void initConfig(ServletConfig config) {

		String filesList = config.getInitParameter("config-files");
		if (filesList != null) {
			String[] configFileNames = filesList.split(" *, *");
			for (int i = 0; i < configFileNames.length; i++) {
				if (configFileNames[i].trim().startsWith("/")||(configFileNames[i].trim().length()>2&&configFileNames[i].trim().charAt(1)==':')){
					String fileName = configFileNames[i].trim();
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
					String fileName = "/WEB-INF/"+configFileNames[i].trim();
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

		String requestUriCharset = getInitParameter(REQUEST_URI_CHARSET, "UTF-8");
//		if (requestUriCharset == null)
//			requestUriCharset = "UTF-8";
		
		String logProviderName = Log.class.getName();
		String logProvider = getInitParameter(logProviderName);
		//System.out.println(logProviderName);
		//System.out.println(logProvider);
		if (logProvider != null) 
			try {
				System.setProperty(logProviderName, logProvider);
			} catch (Exception ignore) {}
		String logThreshold = getInitParameter(logProviderName+ ".threshold");
		if (logThreshold != null) 
			try {
				System.setProperty(logProviderName + ".threshold", logThreshold);
			} catch (Exception ignore) {}
		
		// requestUriCharset = "ISO-8859-1";
		contextBase = getInitParameter("contextBase");
		contextBaseHeader = getInitParameter("contextBaseHeader");
		config.getServletContext().setAttribute(REQUEST_URI_CHARSET, requestUriCharset);
		String acceptBasic = getInitParameter("acceptBasic");
		this.acceptBasic = Boolean.valueOf(acceptBasic).booleanValue();
		String enableBasic = "true";
		this.enableBasic = (enableBasic == null) || Boolean.valueOf(enableBasic).booleanValue();
		String closeOnAuthenticate = getInitParameter("closeOnAuthenticate");
		this.closeOnAuthenticate = Boolean.valueOf(closeOnAuthenticate).booleanValue();
		String alwaysAuthenticate = getInitParameter("alwaysAuthenticate");
		this.alwaysAuthenticate = (alwaysAuthenticate == null) || Boolean.valueOf(alwaysAuthenticate).booleanValue();
		insecureConnection = getInitParameter("insecureConnection", "block");

		defaultIdp = getInitParameter("default-idp");
		serverType = getInitParameter("server-type");
		myproxyServer = getInitParameter("myproxy-server");
		defaultDomain = getInitParameter("default-domain");
		serverPort = 1247;
		try {
			serverPort = Integer.parseInt(getInitParameter("server-port"));
		} catch (Exception _e) {}
		jargonDebug = getInitParameter("jargon.debug", "0");
		serverName = getInitParameter("server-name");
		defaultResource = getInitParameter("default-resource");
		zoneName = getInitParameter("zone-name");
		proxyHost = getInitParameter("proxy-host");
		proxyPort = getInitParameter("proxy-port");
		proxyUsername = getInitParameter("proxy-username");
		proxyPassword = getInitParameter("proxy-password");

		sharedTokenHeaderName = getInitParameter("shared-token-header-name");
		commonNameHeaderName = getInitParameter("cn-header-name");
		adminCertFile = getInitParameter("admin-cert-file");
		adminKeyFile = getInitParameter("admin-key-file");
		String anonymousCredentials = getInitParameter("anonymousCredentials");
		if (anonymousCredentials != null && anonymousCredentials.length() > 0 && anonymousCredentials.indexOf(":") > 0) {
			anonymousUsername = anonymousCredentials.split(":")[0];
			anonymousPassword = anonymousCredentials.split(":")[1];
		}
		String anonymousCollectionString = getInitParameter("anonymousCollections");
		if (anonymousCollectionString != null && anonymousCollectionString.length() > 0) 
			anonymousCollections = Arrays.asList(anonymousCollectionString.split(","));
		realm = getInitParameter("authentication-realm", "Davis");
		organisationName = getInitParameter("organisation-name", "Davis");
		organisationLogo = getInitParameter("organisation-logo", "");
		organisationLogoGeometry = getInitParameter("organisation-logo-geometry", "");
		organisationSupport = getInitParameter("organisation-support", "user support at your organisation");
		favicon = getInitParameter("favicon", "");		
		dojoroot = getInitParameter("dojoroot", "");
        String s = getInitParameter("maximumXmlRequest");
        maximumXmlRequest = (s != null) ? Long.parseLong(s) : 20000l;
        
        displayMetadata = getInitParameter("displayMetadata", "");
        authClass = getInitParameter("authClass");
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

	public boolean isEnableBasic() {
		return enableBasic;
	}

	public void setEnableBasic(boolean enableBasic) {
		this.enableBasic = enableBasic;
	}

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
}
