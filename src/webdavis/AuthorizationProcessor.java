package webdavis;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.codec.binary.Base64;
import org.globus.myproxy.GetParams;
import org.globus.myproxy.MyProxy;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;

import au.edu.archer.desktopshibboleth.idp.IDP;
import au.org.mams.slcs.client.SLCSClient;
import au.org.mams.slcs.client.SLCSConfig;
import edu.sdsc.grid.io.RemoteAccount;
import edu.sdsc.grid.io.irods.IRODSAccount;
import edu.sdsc.grid.io.irods.IRODSFileSystem;
import edu.sdsc.grid.io.srb.SRBAccount;
import edu.sdsc.grid.io.srb.SRBFileSystem;

public class AuthorizationProcessor {
	private Map<String, DavisSession> connectionPool;
	private static AuthorizationProcessor self;
	protected long nonceSecret=this.hashCode() ^ System.currentTimeMillis();
	private DavisConfig davisConfig;
	
	private AuthorizationProcessor(){
		connectionPool=new HashMap<String, DavisSession>();
		davisConfig=DavisConfig.getInstance();
	}
	public static AuthorizationProcessor getInstance(){
		if (self==null){
			self=new AuthorizationProcessor();
		}
		return self;
	}
	public DavisSession getDavisSession(String authorization, boolean reset){
		String sessionID=SimpleMD5.MD5(authorization) + "*basic";
		Log.log(Log.DEBUG, "trying to get session for basic auth(https). sessionID:"+sessionID);
		DavisSession davisSession=connectionPool.get(sessionID);
		if (davisSession!=null&&reset) {
			Log.log(Log.INFORMATION, "reset session "+sessionID);
			davisSession.disconnect();
			connectionPool.remove(sessionID);
		}else if (davisSession!=null&&davisSession.isConnected()){
			Log.log(Log.DEBUG, "Got existing davisSession: "+davisSession);
			return davisSession;
		}
		return login(authorization, null, null, null);
		
	}
	public DavisSession getDavisSession(String sharedToken, String commonName, String shibSessionID, boolean reset){
		String sessionID = SimpleMD5.MD5(sharedToken+":"+shibSessionID) + "*shib";
		Log.log(Log.DEBUG, "trying to get session for shib auth(http). sessionID:"+sessionID);
		DavisSession davisSession=connectionPool.get(sessionID);
		if (davisSession!=null&&reset) {
			Log.log(Log.INFORMATION, "reset session "+sessionID);
			davisSession.disconnect();
			connectionPool.remove(sessionID);
		}else if (davisSession!=null&&davisSession.isConnected()){
			Log.log(Log.DEBUG, "Got existing davisSession: "+davisSession);
			return davisSession;
		}
		return login(null, sharedToken, commonName, shibSessionID);
	}
	
	private DavisSession login(String authorization, String sharedToken, String commonName, String shibSessionID){
		
		String idpName = null;
		String user = null;
		char[] password = null;
		String domain = davisConfig.getDefaultDomain();
		String defaultResource=davisConfig.getDefaultResource();
		String serverName=davisConfig.getServerName();
		GSSCredential gssCredential=null;
		String sessionID=null;
		if (sharedToken!=null&&commonName!=null&&sharedToken.length()>0&&commonName.length()>0){
			sessionID = SimpleMD5.MD5(sharedToken+":"+shibSessionID) + "*shib";
			ShibUtil shibUtil=new ShibUtil();
			Map result;
			if (sharedToken!=null&&commonName!=null&&(result=shibUtil.passInShibSession(sharedToken,commonName))!=null){  //found shib session, get username/password
				user=(String) result.get("username");
				password=(char[]) result.get("password");
				Log.log(Log.DEBUG,"shibUtil got user "+user+" and generated a new password.");
			}
		}else if (authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
			sessionID=SimpleMD5.MD5(authorization) + "*basic";
			String authInfo=null;
			try {
				authInfo = new String(Base64.decodeBase64(authorization
						.substring(6).getBytes()), "ISO-8859-1");
			} catch (UnsupportedEncodingException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				return null;
			}
			// System.out.println("authInfo:"+authInfo);
			int index = authInfo.indexOf(':');
			user = (index != -1) ? authInfo.substring(0, index) : authInfo;
			password = (index != -1) ? authInfo.substring(index + 1).toCharArray() : "".toCharArray();

			if ((index = user.indexOf('\\')) != -1
					|| (index = user.indexOf('/')) != -1) {
				if (index==0){
					idpName=davisConfig.getDefaultIdp();
				}else
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
//					davisConfig.getDefaultResource() = "";
			}
			if ((index = user.indexOf('%')) != -1) {
				domain = user.substring(index + 1);
				user = user.substring(0, index);
			}

			if (idpName != null) {
				// auth with idp
				try {
					if (idpName.equalsIgnoreCase("myproxy")){
						Log.log(Log.DEBUG,"logging in with myproxy: "+davisConfig.getMyproxyServer());
						if (davisConfig.getMyproxyServer()==null||davisConfig.getMyproxyServer().equals("")){
							return null;
						}
						MyProxy mp = new MyProxy(davisConfig.getMyproxyServer(), 7512);
						GetParams getRequest = new GetParams();
						getRequest.setCredentialName(null);
						getRequest.setLifetime(3600);
						getRequest.setPassphrase(new String(password));
						getRequest.setUserName(user);
						gssCredential = mp.get(null,getRequest);
						
					}else{
						IDP idp = null;
						SLCSClient client;
						if (davisConfig.getProxyHost() != null && davisConfig.getProxyHost().toString().length() > 0) {
							SLCSConfig config = SLCSConfig.getInstance();
							config.setProxyHost(davisConfig.getProxyHost().toString());
							if (davisConfig.getProxyPort() != null
									&& davisConfig.getProxyPort().toString().length() > 0)
								config.setProxyPort(Integer.parseInt(davisConfig.getProxyPort()
										.toString()));
							if (davisConfig.getProxyUsername() != null
									&& davisConfig.getProxyUsername().toString().length() > 0)
								config.setProxyUsername(davisConfig.getProxyUsername().toString());
							if (davisConfig.getProxyPassword() != null
									&& davisConfig.getProxyPassword().toString().length() > 0)
								config.setProxyPassword(davisConfig.getProxyPassword().toString());
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
							return null;
						}
						Log.log(Log.DEBUG,"logging in with idp: "+idp.getName());  //+" "+user+" "+password
						gssCredential = client.slcsLogin(idp, user,	password);
						Log.log(Log.DEBUG,"Got porxy from idp: "+gssCredential.getName().toString());
					}
				} catch (GeneralSecurityException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if (gssCredential == null) {
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
		RemoteAccount account=null;
		DavisSession davisSession=null;
		if (gssCredential!=null){
			if (davisConfig.getServerType().equalsIgnoreCase("irods")){
				davisSession = new DavisSession();
				account = new IRODSAccount(davisConfig.getServerName(),davisConfig.getServerPort(),gssCredential,"",defaultResource);
//				account = new IRODSAccount(davisConfig.getServerName(),davisConfig.getServerPort(),"","","",davisConfig.getZoneName(),davisConfig.getDefaultResource());
//				((IRODSAccount)account).setGSSCredential(gssCredential);
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
			}else{
				account = new SRBAccount(davisConfig.getServerName(), davisConfig.getServerPort(),
						gssCredential);
				davisSession = new DavisSession();
				davisSession.setServerName(davisConfig.getServerName());
				davisSession.setServerPort(davisConfig.getServerPort());
				davisSession.setDomain(((SRBAccount)account).getDomainName());
				davisSession.setZone(davisConfig.getZoneName());
				davisSession.setAccount(account.getUserName());
				try {
					davisSession.setDn(gssCredential.getName().toString());
				} catch (GSSException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}else if (user!=null&&password!=null){
			Log.log(Log.DEBUG,"login with username/password");
			if (davisConfig.getServerType().equalsIgnoreCase("irods")){
				account = new IRODSAccount(davisConfig.getServerName(), davisConfig.getServerPort(), user, new String(password), "/" + davisConfig.getZoneName() + "/home/" + user, davisConfig.getZoneName(), defaultResource);
				davisSession = new DavisSession();
				davisSession.setServerName(davisConfig.getServerName());
				davisSession.setServerPort(davisConfig.getServerPort());
				davisSession.setAccount(user);
				davisSession.setZone(davisConfig.getZoneName());
				davisSession.setDefaultResource(defaultResource);
			}else{
				account = new SRBAccount(davisConfig.getServerName(), davisConfig.getServerPort(), user,
						new String(password), "/" + davisConfig.getZoneName() + "/home/" + user + "."
								+ domain, domain, defaultResource);
				davisSession = new DavisSession();
				davisSession.setServerName(davisConfig.getServerName());
				davisSession.setDomain(domain);
				davisSession.setServerPort(davisConfig.getServerPort());
				davisSession.setAccount(user);
				davisSession.setZone(davisConfig.getZoneName());
				davisSession.setDefaultResource(defaultResource);
			}
		}
		
		String homeDir;
		try {
			String[] resList = null;
			if (davisConfig.getServerType().equalsIgnoreCase("irods")){
				IRODSFileSystem irodsFileSystem = new IRODSFileSystem((IRODSAccount)account);
				Log.log(Log.DEBUG, "irods fs:"+irodsFileSystem);
				homeDir = irodsFileSystem.getHomeDirectory();
				if (davisSession.getAccount()==null||davisSession.getAccount().equals("")){
					user = irodsFileSystem.getUserName(); //FSUtilities.getiRODSUsernameByDN(irodsFileSystem, davisSession.getDn());
					if (user==null||user.equals("")){
						return null;
					}
					Log.log(Log.DEBUG, "Found user '"+user+"' for GSI");
					davisSession.setAccount(user);
					homeDir = "/" + davisConfig.getZoneName() + "/home/" + davisSession.getAccount();
				}
				davisSession.setRemoteFileSystem(irodsFileSystem);
				resList = null; //FSUtilities.getIRODSResources(irodsFileSystem,davisSession.getZone());
				if (homeDir == null)
					homeDir = "/" + davisConfig.getZoneName() + "/home/" + user;
			}else{
				SRBFileSystem srbFileSystem = new SRBFileSystem((SRBAccount)account);
				// if (srbFileSystem.isConnected()){
				davisSession.setRemoteFileSystem(srbFileSystem);
				homeDir = srbFileSystem.getHomeDirectory();
				resList = FSUtilities.getSRBResources(srbFileSystem,davisSession.getZone());
				if (homeDir == null)
					homeDir = "/" + davisConfig.getZoneName() + "/home/" + user + "." + domain;
			}
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
			session.disconnect();
		}
		connectionPool=null;
		
	}
	public void destroy(String sessionID) {
		DavisSession session=connectionPool.get(sessionID);
		if (session!=null){
			session.descreaseSharedNumber();
			if (!session.isShared()){
				Log.log(Log.DEBUG,"destroying:"+session);
				session.disconnect();
				connectionPool.remove(sessionID);
				Log.log(Log.INFORMATION,"num of connections in pool:"+connectionPool.size());
			}else{
				Log.log(Log.DEBUG,"connection is being used:"+session);
			}
		}
		
	}
	public DavisSession getDavisSessionByID(String sessionID) {
		DavisSession session=connectionPool.get(sessionID);
		if (!session.isConnected()){
//			session.disconnect();
//			connectionPool.remove(sessionID);
			return null;
		}
		return session;
	}
}
