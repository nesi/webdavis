package webdavis;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.util.Enumeration;
import java.util.Random;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.cert.CertificateException;
import javax.security.cert.X509Certificate;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.auth.CredentialsProvider;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.SecureProtocolSocketFactory;
import org.apache.http.HttpRequest;
import org.apache.http.HttpStatus;
import org.globus.gsi.GlobusCredential;
import org.globus.gsi.GlobusCredentialException;
import org.globus.gsi.gssapi.GlobusGSSCredentialImpl;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;

import edu.sdsc.grid.io.GeneralMetaData;
import edu.sdsc.grid.io.MetaDataCondition;
import edu.sdsc.grid.io.MetaDataRecordList;
import edu.sdsc.grid.io.MetaDataSelect;
import edu.sdsc.grid.io.MetaDataSet;
import edu.sdsc.grid.io.irods.IRODSAccount;
import edu.sdsc.grid.io.irods.IRODSAdmin;
import edu.sdsc.grid.io.irods.IRODSFileSystem;
import edu.sdsc.grid.io.irods.IRODSMetaDataSet;
import edu.sdsc.grid.io.irods.User;

import au.edu.archer.desktopshibboleth.utils.authen.CredentialHandler;
import au.edu.archer.desktopshibboleth.utils.ssl.InteractiveHttpsClient;
import au.org.mams.slcs.client.SLCSConfig;

public class ShibUtil {
	private SLCSConfig config;
	private String slcsLoginURL;
	private String username;
	private char[] password;
	
	public ShibUtil(){
//		config = SLCSConfig.getInstance();
//		this.slcsLoginURL = config.getSLCSServer();
//    	Log.log(Log.DEBUG, "slcsLoginURL:"+slcsLoginURL);
	}
	public GSSCredential getSLCSCertificate(HttpServletRequest request){
		
		try {
		
	    // Create a trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[] {
				new X509TrustManager() {
				public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return null;
				}
				public void checkClientTrusted(
				java.security.cert.X509Certificate[] certs, String authType) {
				}
				public void checkServerTrusted(
				java.security.cert.X509Certificate[] certs, String authType) {
				}
				}
				};
	    
	    // Install the all-trusting trust manager
	    final SSLContext sslContext = SSLContext.getInstance( "SSL" );
	    sslContext.init( null, trustAllCerts, new java.security.SecureRandom() );
	    // Create an ssl socket factory with our all-trusting manager
	    final SSLSocketFactory sslSocketFactorysslSocketFactory = sslContext.getSocketFactory();
	    final SecureProtocolSocketFactory simpleSPSFactory=new SecureProtocolSocketFactory(){

		    /**
		     * @see SecureProtocolSocketFactory#createSocket(java.lang.String,int,java.net.InetAddress,int)
		     */
		    public Socket createSocket(
		        String host,
		        int port,
		        InetAddress clientHost,
		        int clientPort)
		        throws IOException, UnknownHostException
		   {
		       return sslSocketFactorysslSocketFactory.createSocket(
		            host,
		            port,
		            clientHost,
		            clientPort
		        );
		    }

		    /**
		     * @see SecureProtocolSocketFactory#createSocket(java.lang.String,int)
		     */
		    public Socket createSocket(String host, int port)
		        throws IOException, UnknownHostException
		    {
		        return sslSocketFactorysslSocketFactory.createSocket(
		            host,
		            port
		        );
		    }

		    /**
		     * @see SecureProtocolSocketFactory#createSocket(java.net.Socket,java.lang.String,int,boolean)
		     */
		    public Socket createSocket(
		        Socket socket,
		        String host,
		        int port,
		        boolean autoClose)
		        throws IOException, UnknownHostException
		    {
		        return sslSocketFactorysslSocketFactory.createSocket(
		            socket,
		            host,
		            port,
		            autoClose
		        );
		    }


		    public Socket createSocket(
		            final String host,
		            final int port,
		            final InetAddress localAddress,
		            final int localPort,
		            final HttpConnectionParams params
		        ) throws IOException, UnknownHostException, ConnectTimeoutException {
		            if (params == null) {
		                throw new IllegalArgumentException("Parameters may not be null");
		            }
		            int timeout = params.getConnectionTimeout();
		            if (timeout == 0) {
		                return sslSocketFactorysslSocketFactory.createSocket(host, port, localAddress, localPort);
		            } else {
		                Socket socket = sslSocketFactorysslSocketFactory.createSocket();
		                SocketAddress localaddr = new InetSocketAddress(localAddress, localPort);
		                SocketAddress remoteaddr = new InetSocketAddress(host, port);
		                socket.bind(localaddr);
		                socket.connect(remoteaddr, timeout);
		                return socket;
		            }
		        }



	    };
	    
	    Protocol.registerProtocol("https", new Protocol("https", simpleSPSFactory, 443));

		   HttpClient client;

			client = new HttpClient();
			client.getState().clearCredentials();
//			client.getParams().setParameter(CredentialsProvider.PROVIDER, CredentialHandler.getInstance().getCredentialProvider());
			client.getParams().setCookiePolicy(org.apache.commons.httpclient.cookie.CookiePolicy.RFC_2109);

		    // Create a method instance.
		    GetMethod method = new GetMethod(this.slcsLoginURL);
		    
		    // Provide custom retry handler is necessary
		    Enumeration headerNames=request.getHeaderNames();
		    String headerName;
		    while (headerNames.hasMoreElements()){
		    	headerName=(String) headerNames.nextElement();
		    	if (!headerName.startsWith("Shib-")&&!headerName.startsWith("REMOTE_USER")) method.addRequestHeader(headerName,request.getHeader(headerName));
		    }

		    try {
		      // Execute the method.
		      int statusCode = client.executeMethod(method);

		      if (statusCode != HttpStatus.SC_OK) {
		        System.err.println("Method failed: " + method.getStatusLine());
		      }

		      // Read the response body.
		      byte[] responseBody = method.getResponseBody();

		      // Deal with the response.
		      // Use caution: ensure correct character encoding and is not binary data
		      System.out.println(new String(responseBody));

		    } catch (HttpException e) {
		      System.err.println("Fatal protocol violation: " + e.getMessage());
		      e.printStackTrace();
		    } catch (IOException e) {
		      System.err.println("Fatal transport error: " + e.getMessage());
		      e.printStackTrace();
		    } finally {
		      // Release the connection.
		      method.releaseConnection();
		    }  
		} catch ( final Exception e ) {
		    e.printStackTrace();
		}
		return null;
	}
    public String loginSLCS(String cookies) throws HttpException, IOException{
    	HttpClient client=new HttpClient();
    	GetMethod get=new GetMethod(this.slcsLoginURL);
    	client.getParams().setCookiePolicy(org.apache.commons.httpclient.cookie.CookiePolicy.RFC_2109);
    	StringBuffer buffer=new StringBuffer();
    	get.setRequestHeader("Cookie", cookies);
    	for (Header h:get.getRequestHeaders()) Log.log(Log.DEBUG, h);
    	client.executeMethod(get);
    	Log.log(Log.DEBUG, "return code of SLCS login:"+get.getStatusCode());
    	for (Header h:get.getResponseHeaders()) Log.log(Log.DEBUG, h);
//    	if (client.getState()==)
    	String in = get.getResponseBodyAsString();
        // Process the data from the input stream.
        get.releaseConnection();
        return in;

    }
    public boolean passInShibSession(String sharedToken, String commonName){
    	if (sharedToken==null) return false;
		GlobusCredential adminCred;
		try {
			adminCred = new GlobusCredential(Davis.adminCertFile, Davis.adminKeyFile);
	        GSSCredential gssCredential = new GlobusGSSCredentialImpl(adminCred, GSSCredential.INITIATE_AND_ACCEPT);
	        if (Davis.serverType.equalsIgnoreCase("irods")){
		        IRODSAccount adminAccount=new IRODSAccount(Davis.serverName,Davis.serverPort,gssCredential);
		        IRODSFileSystem irodsFileSystem = new IRODSFileSystem(adminAccount);
		        String[] selectFieldNames = {
						IRODSMetaDataSet.USER_NAME,
					};
				MetaDataCondition conditions[] = {
									MetaDataSet.newCondition(
											IRODSMetaDataSet.USER_INFO,	MetaDataCondition.LIKE, "%<ST>"+sharedToken+"</ST>%")
								};
				MetaDataSelect selects[] =
						MetaDataSet.newSelection( selectFieldNames );
				MetaDataRecordList[] userDetails = irodsFileSystem.query(conditions,selects,1);
				IRODSAdmin admin = new IRODSAdmin(irodsFileSystem);
				if (userDetails!=null) {
					username=(String) userDetails[0].getValue(IRODSMetaDataSet.USER_NAME);
					password=getRandomPassword(12);
//					admin.USER.modifyPassword(username, new String(password));
					changePasswordRule(irodsFileSystem, username, new String(password));
				}else {
					String[] names=commonName.split(" ");
					username=names[0].toLowerCase()+"."+names[names.length-1].toLowerCase();
					for (int i=0;i<20;i++){
						if (i>0) username+=i;
						conditions[0] = MetaDataSet.newCondition(
										IRODSMetaDataSet.USER_NAME,	MetaDataCondition.LIKE, username);
						userDetails = irodsFileSystem.query(conditions,selects,1);
						if (userDetails==null||userDetails.length==0){
							admin.USER.addUser(username, "rodsuser");
							admin.USER.modifyInfo(username, sharedToken);
							password=getRandomPassword(12);
//							admin.USER.modifyPassword(username, new String(password));
							changePasswordRule(irodsFileSystem, username, new String(password));
							break;
						}
					}
				}
				irodsFileSystem.close();
		       	return true;
	        }
		} catch (GlobusCredentialException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GSSException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NullPointerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
    }
	public void changePasswordRule(IRODSFileSystem fs, String username,String password){
		String rule="passwordRule||msiExecCmd(changePassword,\"*username *password\",null,null,null,*OUT)|nop\n*username="+username+"%*password="+password+"\n*OUT";
	    System.out.println(rule);
		java.io.ByteArrayInputStream inputStream =
	        new java.io.ByteArrayInputStream(rule.getBytes());
	    try {
			java.util.HashMap outputParameters = 
			    fs.executeRule( inputStream );
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
    //Cookie: SESS3d4e795375e8d8d39b2952e0a7e7882d=1v7bcfkuigqdes7u2d2qbc4ch0; _saml_idp=dXJuOm1hY2U6ZmVkZXJhdGlvbi5vcmcuYXU6dGVzdGZlZDppZHAuZXJlc2VhcmNoc2EuZWR1LmF1; _shibstate_015ed05fb42d0d8b4678e4f9baca4ee92a5ccb50=http%3A%2F%2Farcs-df.eresearchsa.edu.au%2FARCS; _shibsession_015ed05fb42d0d8b4678e4f9baca4ee92a5ccb50=_0ac596db0cc1f42eea2e18a91c5c77ed; JSESSIONID=sqm29pnf9tz0


    //_saml_idp=dXJuOm1hY2U6ZmVkZXJhdGlvbi5vcmcuYXU6dGVzdGZlZDppZHAuZXJlc2VhcmNoc2EuZWR1LmF1; __utmc=253871064; _shibstate_310a075eb49be4d6e82949dd26300a51cd1ecec9=https%3A%2F%2Fslcs1.arcs.org.au%2FSLCS%2Flogin; _shibsession_310a075eb49be4d6e82949dd26300a51cd1ecec9=_40998d143ab0559a212fdd788561c17a
    static public void main(String[] args){
//    	Log.setThreshold(Log.DEBUG);
//    	ShibUtil util=new ShibUtil();
//    	String cookies="__utma=253871064.1465110725924340500.1230188098.1237530361.1237876387.17; __utmz=253871064.1237440678.15.4.utmcsr=google|utmccn=(organic)|utmcmd=organic|utmctr=grix%20host%20cert; _saml_idp=dXJuOm1hY2U6ZmVkZXJhdGlvbi5vcmcuYXU6dGVzdGZlZDppZHAuZXJlc2VhcmNoc2EuZWR1LmF1; __utmc=253871064; _shibstate_310a075eb49be4d6e82949dd26300a51cd1ecec9=https%3A%2F%2Fslcs1.arcs.org.au%2FSLCS%2Flogin; _shibsession_310a075eb49be4d6e82949dd26300a51cd1ecec9=_68da754b83be351338b774875b51741f";
//    	util.getSLCSCertificate(cookies);
    	Davis davis=new Davis();
    	Davis.adminCertFile="/Users/shundezh/grix/arcs-df.eresearchsa.edu.au/hostcert.pem";
    	Davis.adminKeyFile="/Users/shundezh/grix/arcs-df.eresearchsa.edu.au/hostkey.pem";
    	Davis.serverType="irods";
    	Davis.serverName="arcs-df.eresearchsa.edu.au";
    	Davis.serverPort=1247;
    	ShibUtil util=new ShibUtil();
//    	System.out.println(util.getRandomPassword(8));
    	System.out.println(util.passInShibSession("J-YInIFGT8iQi_9xP0beCkhAhQE","Shunde Zhang"));
    	System.out.println(util.getUsername());
    	System.out.println(util.getPassword());
    }
	public String getUsername() {
		return username;
	}
	public char[] getPassword() {
		return password;
	}
	private char[] getRandomPassword(int length) {
		  char[] buffer = new char[length];
		  Random random = new Random();
//		  System.out.println(random.nextInt(74));
//		  char[] chars = new char[] { 'a', 'b', 'c', 'd' /*you get the picture*/};
		  for ( int i = 0; i < length; i++ ) {
		    buffer[i]=(char) (random.nextInt(74)+48);
		  }
		  return buffer;
		}

}
