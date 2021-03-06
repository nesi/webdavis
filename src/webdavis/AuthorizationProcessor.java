package webdavis;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Base64;
import org.globus.myproxy.GetParams;
import org.globus.myproxy.MyProxy;
import org.globus.myproxy.MyProxyException;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.connection.GSIIRODSAccount;
import org.irods.jargon.core.connection.AuthScheme;

public class AuthorizationProcessor {

	private Map<String, DavisSession> connectionPool;
	private static AuthorizationProcessor self;
	protected long nonceSecret=this.hashCode() ^ System.currentTimeMillis();
	private DavisConfig davisConfig;
	
	protected AuthorizationProcessor(){
		connectionPool=new HashMap<String, DavisSession>();
		davisConfig=Davis.getConfig();
	}
	public static AuthorizationProcessor getInstance(){
		if (self==null){
			if (Davis.getConfig().getAuthClass()!=null){
				try {
					self=(AuthorizationProcessor) Class.forName(Davis.getConfig().getAuthClass()).newInstance();
					Log.log(Log.DEBUG, Davis.getConfig().getAuthClass()+" init'ed.");
				} catch (InstantiationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if (self==null) self=new AuthorizationProcessor();
		}
		return self;
	}
	
	// For basic auth
	public DavisSession getDavisSession(String authorization, boolean reset) {
		String sessionID=getUsername(authorization)+"|"+SimpleMD5.MD5(authorization) + "*basic|";
		Log.log(Log.DEBUG, "trying to get session for basic auth(https). sessionID:"+sessionID);
		DavisSession davisSession=connectionPool.get(sessionID); // Look for existing Davis session
		if (davisSession != null && reset) { // Session exists and reset requested
			Log.log(Log.INFORMATION, "reset session "+sessionID);
			try {
				davisSession.disconnect();
			} catch (RuntimeException e) {
				Log.log(Log.WARNING, "Jargon failed to close session: "+davisSession+" - "+e.getMessage());
			}
			connectionPool.remove(sessionID);
		}else 
		if (davisSession !=null && davisSession.isConnected()) { // Session exists and is alive
			Log.log(Log.DEBUG, "Got existing davisSession: "+davisSession);
			return davisSession;
		}
		// No session found, or reset requested - create new session.
		return login(authorization, null, null, null);	
	}
	
	// For shib
	public DavisSession getDavisSession(String sharedToken, String commonName, String shibSessionID, boolean reset){
		String sessionID = "|"+SimpleMD5.MD5(sharedToken+":"+shibSessionID) + "*shib|";
		Log.log(Log.DEBUG, "trying to get session for shib auth(http). sessionID:"+sessionID);
		DavisSession davisSession=connectionPool.get(sessionID); // Look for existing Davis session
		if (davisSession !=null && reset) { // Session exists and reset requested
			Log.log(Log.INFORMATION, "reset session "+sessionID);
			try {
				davisSession.disconnect();
			} catch (RuntimeException e) {
				Log.log(Log.WARNING, "Jargon failed to close session: "+davisSession+" - "+e.getMessage());
			}
			connectionPool.remove(sessionID);
		}else 
		if (davisSession != null && davisSession.isConnected()){ // Session exists and is alive
			Log.log(Log.DEBUG, "Got existing davisSession: "+davisSession);
			return davisSession;
		}
		// No session found, or reset requested
		return login(null, sharedToken, commonName, shibSessionID);
	}
	
	protected String getUsername(String authorization){
		if (authorization.startsWith("Basic ")){
			String authInfo;
			try {
				authInfo = new String(Base64.decodeBase64(authorization
						.substring(6).getBytes()), "ISO-8859-1");
				// System.out.println("authInfo:"+authInfo);
				int index = authInfo.indexOf(':');
				return (index != -1) ? authInfo.substring(0, index) : authInfo;
			} catch (UnsupportedEncodingException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				return null;
			}
		}else{
			return null;
		}
	}
	private void clearOldSessionForTheSameUser(String user){
		String sessionID=null;
		for (String key:connectionPool.keySet()){
			if (key.startsWith(user+"|")){
				sessionID=key;
				break;
			}
		}
		if (sessionID!=null){
			try {
				connectionPool.get(sessionID).disconnect();
			} catch (RuntimeException e) {
				Log.log(Log.WARNING, "Jargon failed to close session: "+e.getMessage());
			}
			connectionPool.remove(sessionID);
			Log.log(Log.INFORMATION,"removed old session from pool:"+sessionID);
		}
	}

    protected GSSCredential myproxyLogin(String user, char[] password, String host)
    {
        Log.log(Log.DEBUG,"logging in with myproxy: "+ host);
        if (host == null || host.equals(""))
            return null;
        
        try{
            MyProxy mp = new MyProxy(host, 7512);
            GetParams getRequest = new GetParams();
            getRequest.setCredentialName(null);
//            getRequest.setLifetime(3600);
            getRequest.setLifetime(DavisConfig.GSSCREDENTIALLIFETIME);
            getRequest.setPassphrase(new String(password));
            getRequest.setUserName(user);
            GSSCredential gssCredential = mp.get(null,getRequest);
            if (gssCredential == null) {
            	Log.log(Log.DEBUG,"can't get gssCredential from myproxy: "+ host);
                return null;
            }
            try {
				Log.log(Log.DEBUG,"gssCredential: "+ gssCredential.getName().toString());
			} catch (GSSException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            return gssCredential;
        }
        catch(MyProxyException e){
        	Log.log(Log.ERROR, "Caught MyProxy exception: "+e);
            return null;
        }
        catch(Exception e){
        	Log.log(Log.ERROR, "Caught exception during myproxy login: "+e);
        	return null;
        }
    }
	
	private DavisSession login(String authorization, String sharedToken, String commonName, String shibSessionID){
		
		String idpName = null;
		String user = null;
		String authUser = null;
		char[] password = null;
		String domain = davisConfig.getDefaultDomain();
		String defaultResource=davisConfig.getDefaultResource();
		String serverName=davisConfig.getServerName();
		GSSCredential gssCredential=null;
		String sessionID=null;
		
		if (sharedToken !=null && commonName !=null && sharedToken.length() > 0 && commonName.length() > 0){ // Shib session?
			sessionID = "|"+SimpleMD5.MD5(sharedToken+":"+shibSessionID) + "*shib|";
			ShibUtil shibUtil=new ShibUtil();
			Map result;
			if (sharedToken !=null && commonName !=null && (result=shibUtil.passInShibSession(sharedToken,commonName)) != null){  //found shib session, get username/password
				user=(String) result.get("username");
				password=(char[]) result.get("password");
				Log.log(Log.DEBUG,"shibUtil got user "+user+" and generated a new password.");
			}
		}else 
		if (authorization.regionMatches(true, 0, "Basic ", 0, 6)) { // Basic
			String authInfo=null;
			try {
				authInfo = new String(Base64.decodeBase64(authorization.substring(6).getBytes()), "ISO-8859-1");
			} catch (UnsupportedEncodingException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				return null;
			}
			// authInfo is user:password
//System.err.println("authInfo:"+authInfo);
			int index = authInfo.indexOf(':');
			user = (index != -1) ? authInfo.substring(0, index) : authInfo;
			password = (index != -1) ? authInfo.substring(index + 1).toCharArray() : "".toCharArray();
			// sessionID is user|MD5(authorization)*basic|
			sessionID=user+"|"+SimpleMD5.MD5(authorization) + "*basic|";
			authUser=user;

			idpName=davisConfig.getDefaultIdp();
			if ((index = user.indexOf('\\')) != -1 || (index = user.indexOf('/')) != -1) { // If user is something \ user
				if (index==0){
					// do nothing
				}else
					idpName = user.substring(0, index);
				user = user.substring(index + 1);
			}
//			boolean hasResource = false;
			if ((index = user.indexOf('#')) != -1) { // If user is user#resource
				defaultResource = user.substring(index + 1);
				user = user.substring(0, index);
//				hasResource = true;
			}
			if ((index = user.indexOf('@')) != -1) { // If user is user@server
				serverName = user.substring(index + 1);
				user = user.substring(0, index);
				domain = serverName;
//				if (!hasResource)
//					davisConfig.getDefaultResource() = "";
			}
			if ((index = user.indexOf('%')) != -1) { // If user is user%domain
				domain = user.substring(index + 1);
				user = user.substring(0, index);
			}

			Log.log(Log.DEBUG,"Auth Scheme:"+idpName+" (user="+user+")");
			if (idpName != null) {
				// auth with idp
				try {
					// myproxy \ user
					if (idpName.equalsIgnoreCase("myproxy")){
					    gssCredential = myproxyLogin(user, password, davisConfig.getMyproxyServer());
                        if(gssCredential == null)
                            return null;
					}
					// irods|srb \ user
                    else if (idpName.equalsIgnoreCase("irods")){
						// using irods users to login
						gssCredential = null;
					}
                    else if (idpName.equalsIgnoreCase("pam")){
						// using irods pam to login
						gssCredential = null;
					}
                    else if (isExtendedAuthScheme(idpName)){
						// use extended auth scheme, need to return gssCredential
						gssCredential = this.processExtendedAuthScheme(idpName, user, password, domain, serverName, defaultResource);
                        if(gssCredential == null)
                            return null;
					}else{ 
						return null;
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return null;
				}
			}
		}else if (authorization.regionMatches(true, 0, "Digest ", 0, 6)) {
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
		}
		IRODSAccount account=null;
		DavisSession davisSession=null;
		
		String homeDir;
		try {
			if (gssCredential!=null){
				Log.log(Log.DEBUG,"login with gssCredential");
				davisSession = new DavisSession();
				account = GSIIRODSAccount.instance(davisConfig.getServerName(),davisConfig.getServerPort(),gssCredential,defaultResource);
//					account = new IRODSAccount(davisConfig.getServerName(),davisConfig.getServerPort(),"","","",davisConfig.getZoneName(),davisConfig.getDefaultResource());
//					((IRODSAccount)account).setGSSCredential(gssCredential);
				davisSession.setZone(davisConfig.getZoneName());
				davisSession.setServerName(davisConfig.getServerName());
				davisSession.setServerPort(davisConfig.getServerPort());
				davisSession.setDefaultResource(defaultResource);
				try {
					davisSession.setDn(gssCredential.getName().toString());
				} catch (GSSException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				Log.log(Log.DEBUG, "Davis session created.");
			}else if (user!=null&&password!=null){
				Log.log(Log.DEBUG,"login with username/password");
				account = new IRODSAccount(davisConfig.getServerName(), davisConfig.getServerPort(), user, new String(password), "/" + davisConfig.getZoneName() + "/home/" + user, davisConfig.getZoneName(), defaultResource);
				davisSession = new DavisSession();
				davisSession.setServerName(davisConfig.getServerName());
				davisSession.setServerPort(davisConfig.getServerPort());
				davisSession.setAccount(user);
				davisSession.setZone(davisConfig.getZoneName());
				davisSession.setDefaultResource(defaultResource);
				if (idpName != null && idpName.equalsIgnoreCase("pam")) account.setAuthenticationScheme(AuthScheme.PAM);
			}
			String[] resList = null;
			Log.log(Log.DEBUG, "Creating IRODSFileSystem");
			FSUtilities.establishIRODSFileSystemConnection(account);
			homeDir = account.getHomeDirectory();
			if (davisSession.getAccount() == null || davisSession.getAccount().equals("")){
				user = account.getUserName(); //FSUtilities.getiRODSUsernameByDN(irodsFileSystem, davisSession.getDn());
				if (user == null || user.equals(""))
					return null;
				Log.log(Log.DEBUG, "Found iRODS user '"+user+"' for GSI");
				davisSession.setAccount(user);
				homeDir = "/" + davisConfig.getZoneName() + "/home/" + davisSession.getAccount();
			}
			davisSession.setIRODSAccount(account);

			resList = null; //FSUtilities.getIRODSResources(irodsFileSystem,davisSession.getZone());
			if (homeDir == null)
				homeDir = "/" + davisConfig.getZoneName() + "/home/" + user;
			Log.log(Log.DEBUG, "zone:"+davisSession.getZone());
			if (resList!=null) {
				for (String res : resList) {
					Log.log(Log.DEBUG, "res:"+res);
					if (res.equals(davisConfig.getDefaultResource())) {
						davisSession.setDefaultResource(res);
					}
				}
				if ((davisSession.getDefaultResource() == null || davisSession.getDefaultResource().length() == 0)
						&& resList.length > 0) {
					for (String res : resList) {
						if (res.startsWith(davisConfig.getDefaultResource())) {
							davisSession.setDefaultResource(res);
						}
					}
				}
				if ((davisSession.getDefaultResource() == null || davisSession.getDefaultResource().length() == 0)
						&& resList.length > 0) {
					davisSession.setDefaultResource(resList[0]);
				}
			}

//			}
			Log.log(Log.DEBUG, "homedir:" + homeDir);
			davisSession.setHomeDirectory(homeDir);
			Log.log(Log.DEBUG, "trashdir:" + davisSession.getTrashDirectory());
			Log.log(Log.DEBUG, "Authentication succeeded. home=" + homeDir
					+ " defaultRes=" + davisSession.getDefaultResource()
					+ " zone=" + davisSession.getZone());
		} catch (Exception e) {
			e.printStackTrace();
			Log.log(Log.WARNING, "Authentication failed.");
			return null;
		}
		if (davisSession!=null){
			davisSession.setSessionID(sessionID);
			if (authUser!=null) {
				clearOldSessionForTheSameUser(authUser);
			}
			connectionPool.put(sessionID, davisSession);
		}
		return davisSession;
	}

//	private DavisSession getDavisSession(String sessionID, HttpServletRequest request){
//		DavisSession davisSession=null;
//		HttpSession session = request.getSession(false);
//		if (session != null) {
//			// System.out.println("Obtained handle to session
//			// "+session.getId());
//			Map credentials = (Map) session.getAttribute(CREDENTIALS);
//			if (credentials != null) {
//				// System.out.println("Dumping credential cache:"+credentials);
//				davisSession = (DavisSession) credentials.get(sessionID);
//				// if (authentication != null) {
//				// System.out.println(
//				// "Found cached credentials for "+davisConfig.getServerName());
//				// } else {
//				// System.out.println(
//				// "No cached credentials found for "+davisConfig.getServerName());
//				// }
//				Log.log(Log.DEBUG, "Got davisSession from session. sessionId="+sessionID);
//			}
//		}
//		if (davisSession == null) {
//			Map credentials = (Map) request.getSession().getServletContext()
//					.getAttribute(CREDENTIALS);
//			if (credentials != null) {
//				// System.out.println("Dumping credential cache:"+credentials);
//				davisSession = (DavisSession) credentials.get(sessionID);
//				Log.log(Log.DEBUG, "Got davisSession from servletContext. sessionId="+sessionID);
//			}
//		}
//		if (davisSession!=null){
//			if (davisSession.getRemoteFileSystem()==null){
//				Log.log(Log.DEBUG,"no file system in davisSesion");
//				davisSession=null;
//			}else if (davisSession.getRemoteFileSystem()!=null&&!davisSession.getRemoteFileSystem().isConnected()){
//				Log.log(Log.DEBUG,"file system in davisSesion expired, need to reconnect.");
//				davisSession=null;
//			}
//		}
//		return davisSession;
//	}
    private String newNonce(HttpServletRequest request)
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
        
        return new String(Base64.encodeBase64(nounce));
    }
	public void destroyConnectionPool() {
		for (DavisSession session:connectionPool.values()){
			try {
				session.disconnect();
			} catch (RuntimeException e) {
				Log.log(Log.WARNING, "Jargon failed to close session: "+session+" - "+e.getMessage());
			}
		}
		connectionPool=null;
		
	}
	public void destroy(String sessionID) {
		DavisSession session=connectionPool.get(sessionID);
		if (session!=null){
			session.descreaseSharedNumber();
			if (!session.isShared()){
				Log.log(Log.DEBUG,"destroying:"+session);
				try {
					session.disconnect();
				} catch (RuntimeException e) {
					Log.log(Log.WARNING, "Jargon failed to close session: "+session+" - "+e.getMessage());
				}
				connectionPool.remove(sessionID);
				Log.log(Log.INFORMATION,"num of connections in pool:"+connectionPool.size());
			}else{
				Log.log(Log.DEBUG,"connection is being used:"+session);
			}
		}
		
	}
	public DavisSession getDavisSessionByID(String sessionID) {
		Log.log(Log.DEBUG, "Getting Davis seesion by ID: "+sessionID);
		DavisSession session=connectionPool.get(sessionID);
		if (!session.isConnected()){
//			session.disconnect();
//			connectionPool.remove(sessionID);
			return null;
		}
		return session;
	}
	protected boolean isExtendedAuthScheme(String schemeName){
		// need to add config/code/script for customized auth scheme, e.g. salt
		return false;
	}
	protected GSSCredential processExtendedAuthScheme(String schemeName,
			String user, char[] password, String domain, String serverName,
			String defaultResource){
		return null;
	}
}
