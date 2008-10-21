package webdavis;

import java.io.Serializable;

import edu.sdsc.grid.io.RemoteFileSystem;
import edu.sdsc.grid.io.irods.IRODSFileSystem;
import edu.sdsc.grid.io.srb.SRBFileSystem;

public class DavisSession implements Serializable{
	private RemoteFileSystem remoteFileSystem;
	private String username;
	private String defaultResource;
	private String homeDirectory;
	private String account;
	private String domain;
	private String zone;
	private String serverName;
	private int serverPort;
	private String dn;
	private String sessionID;
	public String getSessionID() {
		return sessionID;
	}
	public void setSessionID(String sessionID) {
		this.sessionID = sessionID;
	}
	public String getAccount() {
		return account;
	}
	public void setAccount(String account) {
		this.account = account;
	}
	public String getDomain() {
		return domain;
	}
	public void setDomain(String domain) {
		this.domain = domain;
	}
	public String getZone() {
		return zone;
	}
	public void setZone(String zone) {
		this.zone = zone;
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
	public String getDn() {
		return dn;
	}
	public void setDn(String dn) {
		this.dn = dn;
	}
	public String getHomeDirectory() {
		return homeDirectory;
	}
	public void setHomeDirectory(String homeDirectory) {
		this.homeDirectory = homeDirectory;
	}
	public DavisSession(){}
	public RemoteFileSystem getRemoteFileSystem() {
		return remoteFileSystem;
	}
	public void setRemoteFileSystem(RemoteFileSystem remoteFileSystem) {
		this.remoteFileSystem = remoteFileSystem;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getDefaultResource() {
		return defaultResource;
	}
	public void setDefaultResource(String defaultResource) {
		this.defaultResource = defaultResource;
	}
	public String toString(){
		StringBuffer buffer=new StringBuffer();
//		buffer.append("");
//		buffer.append(username);
//		buffer.append("^");
		if (remoteFileSystem instanceof SRBFileSystem) buffer.append("srb");
		if (remoteFileSystem instanceof IRODSFileSystem) buffer.append("irods");
		buffer.append("://").append(account).append(".").append(domain).append("@").append(serverName).append(":").append(serverPort);
		buffer.append("{").append(defaultResource).append("}");
		buffer.append("[").append(homeDirectory).append("]");
		return buffer.toString();
	}
}
