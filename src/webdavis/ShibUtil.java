package webdavis;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.http.HttpRequest;
import org.ietf.jgss.GSSCredential;

import au.org.mams.slcs.client.SLCSConfig;

public class ShibUtil {
	private SLCSConfig config;
	private String slcsLoginURL;
	
	public ShibUtil(){
		config = SLCSConfig.getInstance();
		this.slcsLoginURL = config.getSLCSServer();
    	Log.log(Log.DEBUG, "slcsLoginURL:"+slcsLoginURL);
	}
	public GSSCredential getSLCSCertificate(HttpServletRequest request){
//		try {
//			HttpRequest r=new HttpRequest(request);
//			String slcsLogin=loginSLCS(cookies);
//			Log.log(Log.DEBUG, "slcs login result:"+slcsLogin);
//		} catch (HttpException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
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
    //Cookie: SESS3d4e795375e8d8d39b2952e0a7e7882d=1v7bcfkuigqdes7u2d2qbc4ch0; _saml_idp=dXJuOm1hY2U6ZmVkZXJhdGlvbi5vcmcuYXU6dGVzdGZlZDppZHAuZXJlc2VhcmNoc2EuZWR1LmF1; _shibstate_015ed05fb42d0d8b4678e4f9baca4ee92a5ccb50=http%3A%2F%2Farcs-df.eresearchsa.edu.au%2FARCS; _shibsession_015ed05fb42d0d8b4678e4f9baca4ee92a5ccb50=_0ac596db0cc1f42eea2e18a91c5c77ed; JSESSIONID=sqm29pnf9tz0


    //_saml_idp=dXJuOm1hY2U6ZmVkZXJhdGlvbi5vcmcuYXU6dGVzdGZlZDppZHAuZXJlc2VhcmNoc2EuZWR1LmF1; __utmc=253871064; _shibstate_310a075eb49be4d6e82949dd26300a51cd1ecec9=https%3A%2F%2Fslcs1.arcs.org.au%2FSLCS%2Flogin; _shibsession_310a075eb49be4d6e82949dd26300a51cd1ecec9=_40998d143ab0559a212fdd788561c17a
    static public void main(String[] args){
    	Log.setThreshold(Log.DEBUG);
    	ShibUtil util=new ShibUtil();
    	String cookies="__utma=253871064.1465110725924340500.1230188098.1237530361.1237876387.17; __utmz=253871064.1237440678.15.4.utmcsr=google|utmccn=(organic)|utmcmd=organic|utmctr=grix%20host%20cert; _saml_idp=dXJuOm1hY2U6ZmVkZXJhdGlvbi5vcmcuYXU6dGVzdGZlZDppZHAuZXJlc2VhcmNoc2EuZWR1LmF1; __utmc=253871064; _shibstate_310a075eb49be4d6e82949dd26300a51cd1ecec9=https%3A%2F%2Fslcs1.arcs.org.au%2FSLCS%2Flogin; _shibsession_310a075eb49be4d6e82949dd26300a51cd1ecec9=_68da754b83be351338b774875b51741f";
//    	util.getSLCSCertificate(cookies);
    }
}
