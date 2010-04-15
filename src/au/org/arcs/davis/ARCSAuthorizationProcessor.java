package au.org.arcs.davis;

import org.ietf.jgss.GSSCredential;

import webdavis.AuthorizationProcessor;
import webdavis.DavisConfig;

public class ARCSAuthorizationProcessor extends AuthorizationProcessor {

	@Override
	protected boolean isExtendedAuthScheme(String schemeName) {
		if ("arcs".equalsIgnoreCase(schemeName)) return true;
		return super.isExtendedAuthScheme(schemeName);
	}

	@Override
	protected GSSCredential processExtendedAuthScheme(String schemeName,
			String user, char[] password, String domain, String serverName,
			String defaultResource) {
		if ("arcs".equalsIgnoreCase(schemeName)) {
			return myproxyLogin(user, password, DavisConfig.getInstance().getInitParameter("arcs-myproxy-server", true));
		}
		return super.processExtendedAuthScheme(schemeName, user, password, domain, serverName, defaultResource);
	}

}
