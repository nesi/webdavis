package webdavis;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.globus.gsi.GlobusCredential;
import org.globus.gsi.GlobusCredentialException;
import org.globus.gsi.gssapi.GlobusGSSCredentialImpl;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.pub.IRODSFileSystem;
import org.irods.jargon.core.pub.UserAO;
import org.irods.jargon.core.pub.domain.User;
import org.irods.jargon.core.query.RodsGenQueryEnum;

public class ShibUtil {
	
	public ShibUtil(){
//		config = SLCSConfig.getInstance();
//		this.slcsLoginURL = config.getSLCSServer();
//    	Log.log(Log.DEBUG, "slcsLoginURL:"+slcsLoginURL);
	}
    public Map passInShibSession(String sharedToken, String commonName){
    	String username=null;
    	String password=null;
    	Map result=new HashMap();
    	if (sharedToken==null) return null;
		GlobusCredential adminCred;
		DavisConfig config=Davis.getConfig();
		try {
			adminCred = new GlobusCredential(config.getAdminCertFile(), config.getAdminKeyFile());
	        GSSCredential gssCredential = new GlobusGSSCredentialImpl(adminCred, GSSCredential.INITIATE_AND_ACCEPT);
        	IRODSAccount adminAccount=IRODSAccount.instance(config.getServerName(),config.getServerPort(),gssCredential);
//	        	adminAccount.setZone(config.getInitParameter("zone-name", null));
//		        adminAccount.setUserName(config.getInitParameter("adminUsername", "rods"));
	        IRODSFileSystem irodsFileSystem = IRODSFileSystem.instance();
	        UserAO userAO=irodsFileSystem.getIRODSAccessObjectFactory().getUserAO(adminAccount);
//		        password=getRandomPassword(12);
	        
//		        createUser(irodsFileSystem,commonName,String.valueOf(password),sharedToken);
	        
	        StringBuilder sb = new StringBuilder();
			sb.append(RodsGenQueryEnum.COL_USER_INFO.getName());
			sb.append(" LIKE '%<ST>");
			sb.append(sharedToken);
			sb.append("</ST>%'");
			List<User> users = userAO.findWhere(sb.toString());
			if (users.size()>0) {
				username=users.get(0).getName();
		        password=userAO.getTemporaryPasswordForASpecifiedUser(username);
			}else{
				return null;
			}
		        
//		        String[] selectFieldNames = {
//						IRODSMetaDataSet.USER_NAME,
//					};
//				MetaDataCondition conditions[] = {
//									MetaDataSet.newCondition(
//											IRODSMetaDataSet.USER_INFO,	MetaDataCondition.LIKE, "%<ST>"+sharedToken+"</ST>%")
//								};
//				MetaDataSelect selects[] =
//						MetaDataSet.newSelection( selectFieldNames );
//				MetaDataRecordList[] userDetails = irodsFileSystem.query(conditions,selects,1);
////				IRODSAdmin admin = new IRODSAdmin(irodsFileSystem);
//				if (userDetails!=null) {
//					username=(String) userDetails[0].getValue(IRODSMetaDataSet.USER_NAME);
////					password=getRandomPassword(12);
////					admin.USER.modifyPassword(username, new String(password));
////					changePasswordRule(irodsFileSystem, username, new String(password));
//				}else {
//					irodsFileSystem.close();
//					return null;
//					String[] names=commonName.split(" ");
//					String base=names[0].toLowerCase()+"."+names[names.length-1].toLowerCase();
//					for (int i=0;i<20;i++){
//						if (i>0) 
//							username=base+i;
//						else
//							username=base;
//						conditions[0] = MetaDataSet.newCondition(
//										IRODSMetaDataSet.USER_NAME,	MetaDataCondition.LIKE, username);
//						userDetails = irodsFileSystem.query(conditions,selects,1);
//						if (userDetails==null||userDetails.length==0){
//							Log.log(Log.DEBUG, "Creating new user "+username);
//							admin.USER.addUser(username, "rodsuser");
//							admin.USER.modifyInfo(username, "<ST>"+sharedToken+"</ST>");
//							password=getRandomPassword(12);
////							admin.USER.modifyPassword(username, new String(password));
//							changePasswordRule(irodsFileSystem, username, new String(password));
//							break;
//						}
//					}
//				}
			result.put("username", username);
			result.put("password", password);
//				irodsFileSystem.close();
	       	return result;
		} catch (GlobusCredentialException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GSSException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NullPointerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JargonException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
    }
    //Cookie: SESS3d4e795375e8d8d39b2952e0a7e7882d=1v7bcfkuigqdes7u2d2qbc4ch0; _saml_idp=dXJuOm1hY2U6ZmVkZXJhdGlvbi5vcmcuYXU6dGVzdGZlZDppZHAuZXJlc2VhcmNoc2EuZWR1LmF1; _shibstate_015ed05fb42d0d8b4678e4f9baca4ee92a5ccb50=http%3A%2F%2Farcs-df.eresearchsa.edu.au%2FARCS; _shibsession_015ed05fb42d0d8b4678e4f9baca4ee92a5ccb50=_0ac596db0cc1f42eea2e18a91c5c77ed; JSESSIONID=sqm29pnf9tz0


    //_saml_idp=dXJuOm1hY2U6ZmVkZXJhdGlvbi5vcmcuYXU6dGVzdGZlZDppZHAuZXJlc2VhcmNoc2EuZWR1LmF1; __utmc=253871064; _shibstate_310a075eb49be4d6e82949dd26300a51cd1ecec9=https%3A%2F%2Fslcs1.arcs.org.au%2FSLCS%2Flogin; _shibsession_310a075eb49be4d6e82949dd26300a51cd1ecec9=_40998d143ab0559a212fdd788561c17a
    static public void main(String[] args){
//    	Log.setThreshold(Log.DEBUG);
//    	ShibUtil util=new ShibUtil();
//    	String cookies="__utma=253871064.1465110725924340500.1230188098.1237530361.1237876387.17; __utmz=253871064.1237440678.15.4.utmcsr=google|utmccn=(organic)|utmcmd=organic|utmctr=grix%20host%20cert; _saml_idp=dXJuOm1hY2U6ZmVkZXJhdGlvbi5vcmcuYXU6dGVzdGZlZDppZHAuZXJlc2VhcmNoc2EuZWR1LmF1; __utmc=253871064; _shibstate_310a075eb49be4d6e82949dd26300a51cd1ecec9=https%3A%2F%2Fslcs1.arcs.org.au%2FSLCS%2Flogin; _shibsession_310a075eb49be4d6e82949dd26300a51cd1ecec9=_68da754b83be351338b774875b51741f";
//    	util.getSLCSCertificate(cookies);
    	DavisConfig config=Davis.getConfig();
    	config.setAdminCertFile("/Users/shundezh/grix/arcs-df.eresearchsa.edu.au/hostcert.pem");
    	config.setAdminKeyFile("/Users/shundezh/grix/arcs-df.eresearchsa.edu.au/hostkey.pem");
    	config.setServerType("irods");
    	config.setServerName("arcs-df.eresearchsa.edu.au");
    	config.setServerPort(1247);
    	ShibUtil util=new ShibUtil();
//    	System.out.println(util.getRandomPassword(8));
    	System.out.println(util.passInShibSession("J-YInIFGT8iQi_9xP0beCkhAhQE","Shunde Zhang"));
//    	System.out.println(util.getUsername());
//    	System.out.println(util.getPassword());
    }
//	public String getUsername() {
//		return username;
//	}
//	public char[] getPassword() {
//		return password;
//	}
	private char[] getRandomPassword(int length) {
		  byte[] buffer = new byte[length];
		  SecureRandom random = new SecureRandom();
//		  System.out.println(random.nextInt(74));
//		  char[] chars = new char[] { 'a', 'b', 'c', 'd' /*you get the picture*/};
		  for ( int i = 0; i < length; i++ ) {
		    buffer[i]=(byte) (random.nextInt(63)+63);
		  }
		  return new String((Base64.encodeBase64(buffer))).toCharArray();
		}

}
