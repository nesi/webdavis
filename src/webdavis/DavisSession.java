package webdavis;

import java.io.IOException;
import java.io.Serializable;
import java.util.Hashtable;

import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.pub.DataObjectAO;
import org.irods.jargon.core.pub.DataTransferOperations;
import org.irods.jargon.core.pub.IRODSFileSystem;
import org.irods.jargon.core.pub.UserAO;
import org.irods.jargon.core.pub.io.IRODSFileFactory;

import webdavis.DefaultPostHandler.Tracker;
/**
 * A wrapper class of session information
 * @author Shunde Zhang
 */
public class DavisSession implements Serializable{
	private IRODSAccount iRODSAccount;
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
	private String currentRoot;
	private String currentResource;
	private int sharedSessionNumber;
	
	private Hashtable<String, ClientInstance> clientInstances = new Hashtable(); // Client instance specific items - one per unique UI


	public String getAuthenticationScheme(boolean queryFileSystem) {
		if (sessionID != null && !queryFileSystem) {
			if (sessionID.endsWith("*shib|"))
				return "shib";
			if (sessionID.endsWith("*basic|"))
				return "basic";
			return null;
		}
		return iRODSAccount.getAuthenticationScheme().name();
	}
	
	public void disconnect() throws RuntimeException {
		IRODSFileSystem fileSystem;
		try {
			fileSystem = IRODSFileSystem.instance();
			fileSystem.getIrodsSession().currentConnection(iRODSAccount).disconnect();
		} catch (JargonException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		}
	}
	
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
	public String getTrashDirectory() {
		return "/"+getZone()+"/trash"+getHomeDirectory().replaceFirst("/"+getZone(), "");
	}
	public void setHomeDirectory(String homeDirectory) {
		this.homeDirectory = homeDirectory;
	}
	public DavisSession(){
		currentRoot=null;
		sharedSessionNumber=0;
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
		buffer.append("irods://").append(account);
		if (domain!=null) buffer.append(".").append(domain);
		buffer.append("@").append(serverName).append(":").append(serverPort);
		buffer.append("{").append(defaultResource).append("}");
		buffer.append("[").append(homeDirectory).append("]");
		buffer.append("<").append(currentRoot).append(":").append(currentResource).append(">");
		buffer.append("(shared session num:").append(sharedSessionNumber).append(")");
		return buffer.toString();
	}
	public String getCurrentRoot() {
		return currentRoot;
	}
	public void setCurrentRoot(String currentRoot) {
		this.currentRoot = currentRoot;
	}
	public String getCurrentResource() {
		return currentResource;
	}
	public void setCurrentResource(String currentResource) {
		this.currentResource = currentResource;
	}
	public void increaseSharedNumber() {
		sharedSessionNumber++;
	}
	public void descreaseSharedNumber() {
		sharedSessionNumber--;
	}
	public boolean isShared(){
		return sharedSessionNumber>0;
	}
	public boolean isConnected() {
		IRODSFileSystem fileSystem;
		try {
			fileSystem = IRODSFileSystem.instance();
			return fileSystem.getIrodsSession().currentConnection(iRODSAccount).isConnected();
		} catch (JargonException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}
	public ClientInstance getClientInstance(String clientID) {
		return clientInstances.get(clientID);
	}
	public Hashtable<String, ClientInstance> getClientInstances() {
		return clientInstances;
	}

	public IRODSAccount getIRODSAccount() {
		return iRODSAccount;
	}

	public void setIRODSAccount(IRODSAccount iRODSAccount) {
		this.iRODSAccount = iRODSAccount;
	}
	public IRODSFileFactory getFileFactory() throws IOException {
		IRODSFileSystem fileSystem;
		try {
			fileSystem = IRODSFileSystem.instance();
			return fileSystem.getIRODSFileFactory(iRODSAccount);
		} catch (JargonException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new IOException(e.getMessage());
		}
	}
	public DataObjectAO getDataObjectAO() throws IOException {
		IRODSFileSystem fileSystem;
		try {
			fileSystem = IRODSFileSystem.instance();
	        return fileSystem.getIRODSAccessObjectFactory().getDataObjectAO(iRODSAccount);
		} catch (JargonException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new IOException(e.getMessage());
		}
	}
	public DataTransferOperations getDataTransferOperations() throws IOException {
		IRODSFileSystem fileSystem;
		try {
			fileSystem = IRODSFileSystem.instance();
	        return fileSystem.getIRODSAccessObjectFactory().getDataTransferOperations(iRODSAccount);
		} catch (JargonException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new IOException(e.getMessage());
		}
	}
}
