package webdavis;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import edu.sdsc.grid.io.GeneralFile;
import edu.sdsc.grid.io.GeneralMetaData;
import edu.sdsc.grid.io.MetaDataCondition;
import edu.sdsc.grid.io.MetaDataRecordList;
import edu.sdsc.grid.io.MetaDataSelect;
import edu.sdsc.grid.io.MetaDataSet;
import edu.sdsc.grid.io.Namespace;
import edu.sdsc.grid.io.RemoteFile;
import edu.sdsc.grid.io.RemoteFileSystem;
import edu.sdsc.grid.io.UserMetaData;
import edu.sdsc.grid.io.irods.IRODSAccount;
import edu.sdsc.grid.io.irods.IRODSFile;
import edu.sdsc.grid.io.irods.IRODSFileSystem;
import edu.sdsc.grid.io.irods.IRODSMetaDataSet;
import edu.sdsc.grid.io.srb.SRBAccount;
import edu.sdsc.grid.io.srb.SRBFileSystem;
import edu.sdsc.grid.io.srb.SRBMetaDataSet;

/**
 * Utilities for SRB/iRODS
 * 
 * @author Shunde Zhang
 *
 */
public class FSUtilities {
	
	private static final int MAX_QUERY_NUM = 100000;

	public static String getiRODSUsernameByDN(IRODSFileSystem fs, String dn){
		MetaDataRecordList[] recordList = null;
		Log.log(Log.DEBUG, "getiRODSUsernameByDN '"+dn+"' from "+fs);
		try {
			recordList = fs
						.query(
								new MetaDataCondition[] {
										MetaDataSet
										.newCondition(
												IRODSMetaDataSet.USER_DN,
												MetaDataCondition.EQUAL,
												dn) 
												},
								new MetaDataSelect[] { MetaDataSet
										.newSelection(IRODSMetaDataSet.USER_NAME) });
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
        if(recordList != null&&recordList.length>0) {
            return (String)recordList[0].getValue(IRODSMetaDataSet.USER_NAME);
        }

        return null;
		
	}
	
	 public static String[] getAvailableResource( SRBFileSystem fs ) throws IOException{
		 return getAvailableResource(fs,fs.getMcatZone());
	 }
	 public static String[] getAvailableResource( SRBFileSystem fs, String zone )
	    throws IOException
	  {
	    MetaDataRecordList[] rl = null;
	    if (fs.getVersionNumber() >= 3)
	    {
	      String userName = fs.getUserName();
	      String mdasDomain = fs.getDomainName();
//	      String zone = fs.getMcatZone();


	      //Query file system to determine this SRBFile's storage resource,
	      //find what resources the user can access, pick one at random
	      //then use that for the default resource of its fileSystem object
	      MetaDataCondition[] conditions = {
	        MetaDataSet.newCondition( SRBMetaDataSet.RSRC_ACCESS_PRIVILEGE,
	          MetaDataCondition.LIKE, "%write%" ),
	        MetaDataSet.newCondition( SRBMetaDataSet.RSRC_ACCS_USER_NAME,
	          MetaDataCondition.EQUAL, userName),
	        MetaDataSet.newCondition( SRBMetaDataSet.RSRC_ACCS_USER_DOMAIN,
	          MetaDataCondition.EQUAL, mdasDomain ),
	        MetaDataSet.newCondition( SRBMetaDataSet.RSRC_ACCS_USER_ZONE,
	          MetaDataCondition.EQUAL, zone ),
	      };
	      MetaDataSelect[] selects = {
	        MetaDataSet.newSelection( SRBMetaDataSet.RESOURCE_NAME ) };
	      rl = fs.query( conditions, selects );

	      if (rl == null) {
	        //Same as above, just no zone
	        //Metadata to determine available resources was added only after SRB3
	        rl = fs.query( SRBMetaDataSet.RESOURCE_NAME );
	        if ((rl == null) && (!userName.equals("public"))) {
	          //if null then file does not exist (or is dir?)
	          //public user never has resources, so can't commit files, so it doesn't matter.
	          throw new FileNotFoundException( "No resources available" );
	        }
	      }
	    }
	    if (rl != null) {
	      String[] resArray=new String[rl.length];
	      for (int i=0;i<rl.length;i++){
	    	  resArray[i]=rl[i].getValue( SRBMetaDataSet.RESOURCE_NAME ).toString();
	      }
	      return resArray;
	    }
	    return new String[0];
	  }
	
	 
	public static String[] getIRODSResources(IRODSFileSystem fs) throws IOException {
		return getIRODSResources(fs,((IRODSAccount)fs.getAccount()).getZone());
	}
	public static String[] getIRODSResources(IRODSFileSystem fs, String currentZone) throws IOException {
		MetaDataRecordList[] recordList = null;
		Log.log(Log.DEBUG, "getting res for "+currentZone+" from "+fs);
		try {
			recordList = fs.query(
				new MetaDataCondition[] {
//						MetaDataSet.newCondition( IRODSMetaDataSet.RSRC_ACCESS_PRIVILEGE, MetaDataCondition.LIKE, "%write%"),
//						MetaDataSet.newCondition( IRODSMetaDataSet.RSRC_ACCS_USER_NAME, MetaDataCondition.EQUAL, fs.getUserName()),
//						MetaDataSet.newCondition( IRODSMetaDataSet.RSRC_ACCS_USER_DOMAIN, MetaDataCondition.EQUAL, fs.getDomainName()),
//						MetaDataSet.newCondition( SRBMetaDataSet.RSRC_ACCS_USER_ZONE, MetaDataCondition.EQUAL, fs.getMcatZone()),											
						MetaDataSet.newCondition(IRODSMetaDataSet.RESOURCE_ZONE, MetaDataCondition.EQUAL, currentZone)},		
				new MetaDataSelect[] {MetaDataSet.newSelection(IRODSMetaDataSet.RESOURCE_NAME)});
//			recordList = fs.query(MetaDataSet.newSelection(IRODSMetaDataSet.RESOURCE_NAME));
//			recordList = MetaDataRecordList.getAllResults(recordList);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	   	String[] names;
	    if(recordList != null) {
	    	names = new String[recordList.length];
	    	for(int i=0; i<recordList.length; i++) 
	    		names[i] = (String)recordList[i].getValue(IRODSMetaDataSet.RESOURCE_NAME);
	    } else 
	    	names = new String[]{};

	    return names;
	 }	 
	 
	 public static String[] getSRBResources(SRBFileSystem fs) throws IOException {
	     return getSRBResources(fs,fs.getMcatZone());
	 }
	    
	 public static String[] getSRBResources(SRBFileSystem fs, String currentZone) throws IOException {

        //we want to get all of the zones
        MetaDataCondition conditions[] = {
            MetaDataSet.newCondition(SRBMetaDataSet.ZONE_NAME, MetaDataCondition.NOT_EQUAL, " ")
        };

        //with all of the required information to connect to them
        MetaDataSelect selects[] = {
            MetaDataSet.newSelection(SRBMetaDataSet.ZONE_NAME),
            MetaDataSet.newSelection(SRBMetaDataSet.ZONE_LOCALITY),
            MetaDataSet.newSelection(SRBMetaDataSet.ZONE_NETPREFIX),
            MetaDataSet.newSelection(SRBMetaDataSet.ZONE_PORT_NUM),
            MetaDataSet.newSelection(SRBMetaDataSet.ZONE_STATUS),
            MetaDataSet.newSelection(SRBMetaDataSet.ZONE_COMMENTS)
        };

        //run the query on the local zones mcat
        MetaDataRecordList[] rl= fs.query(conditions, selects);

        //search through the list and find the local zone, if the current zone is local then just query the local mcat for resources
        for(int i = 0; i < rl.length; i++) {
            String zoneLocality = rl[i].getValue(SRBMetaDataSet.ZONE_LOCALITY).toString();
            String zoneName = rl[i].getValue(SRBMetaDataSet.ZONE_NAME).toString();

            if(zoneLocality.equals("1") && zoneName.equals(currentZone))
                return convert( fs, zoneName);
        }

        //if the zone we want resources for is a remote zone we need to make a connection to the remote server
        for(int i = 0; i < rl.length; i++) {
            String zoneLocality = rl[i].getValue(SRBMetaDataSet.ZONE_LOCALITY).toString();

            //only want to know about the remote zones
            if(zoneLocality.equals("0")) {
                String zone="", location="", domain="", user="", port="";
                zone = rl[i].getValue(SRBMetaDataSet.ZONE_NAME).toString();
                //if(!zoneHidden(fileSystem, zone) && zone.equals(currentZone)) {
                if(zone.equals(currentZone)) {
                    location = rl[i].getValue(SRBMetaDataSet.ZONE_NETPREFIX).toString();
                    port = rl[i].getValue(SRBMetaDataSet.ZONE_PORT_NUM).toString();

                    location = location.split(":")[0];

                    //create an account to the remote zone mcat with a ticket user account
                    SRBAccount tempAccount = new SRBAccount(
                              location, Integer.parseInt(port),
                              "ticketuser", "", "", "sdsc", "" );

                    //create the remote filesystem connection
                    SRBFileSystem remoteFs = new SRBFileSystem(tempAccount);

                    //get the remote zone resources
                    String[] resources = convert(remoteFs, zone);

                    //close the remote connection
                    remoteFs.close();

                    //return the resources
                    return resources;
                }
            }
        }

        return new String[0];
    }
    
    public static String[] convert(SRBFileSystem fs, String zoneName) {
		MetaDataRecordList[] recordList = null;
		try {
			recordList = fs
						.query(
								new MetaDataCondition[] {
								        MetaDataSet.newCondition( SRBMetaDataSet.RSRC_ACCESS_PRIVILEGE,
								  	          MetaDataCondition.LIKE, "%write%" ),
								  	        MetaDataSet.newCondition( SRBMetaDataSet.RSRC_ACCS_USER_NAME,
								  	          MetaDataCondition.EQUAL, fs.getUserName()),
								  	        MetaDataSet.newCondition( SRBMetaDataSet.RSRC_ACCS_USER_DOMAIN,
								  	          MetaDataCondition.EQUAL, fs.getDomainName() ),
//								  	        MetaDataSet.newCondition( SRBMetaDataSet.RSRC_ACCS_USER_ZONE,
//								  	          MetaDataCondition.EQUAL, fs.getMcatZone() ),
										
										MetaDataSet
										.newCondition(
												SRBMetaDataSet.RSRC_OWNER_ZONE,
												MetaDataCondition.EQUAL,
												zoneName) 
												},
								new MetaDataSelect[] { MetaDataSet
										.newSelection(SRBMetaDataSet.RESOURCE_NAME) });
			recordList = MetaDataRecordList.getAllResults(recordList);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    	
    	String[] names;
        if(recordList != null) {
            names = new String[recordList.length];

            for(int i=0; i<recordList.length; i++) {
                names[i] = (String)recordList[i].getValue(SRBMetaDataSet.RESOURCE_NAME);
            }
        } else {
            names = new String[]{};
        }

        return names;
    }
    
    public static String[] getDomains(SRBFileSystem fs){
    	List<String> domains=new ArrayList<String>();
    	domains.add("groups");
		MetaDataCondition conditions[] = {
				MetaDataSet.newCondition(
						UserMetaData.USER_TYPE, MetaDataCondition.LIKE, "staff" ),
		//						MetaDataSet.newCondition(
		//								UserMetaData.USER_TYPE, MetaDataCondition.LIKE, "public" ),
		//								MetaDataSet.newCondition(
		//										UserMetaData.USER_TYPE, MetaDataCondition.LIKE, "sysadmin" ),
		};
		MetaDataSelect[] selects ={ MetaDataSet.newSelection( SRBMetaDataSet.USER_DOMAIN) };
		MetaDataRecordList[] rl;
		try {
			rl = fs.query(conditions,selects);
			if (rl != null) { 
				for (int i=0;i<rl.length;i++) {
					if (!rl[i].getValue(SRBMetaDataSet.USER_DOMAIN).equals("home")&&!domains.contains(rl[i].getValue(SRBMetaDataSet.USER_DOMAIN))) domains.add((String) rl[i].getValue(SRBMetaDataSet.USER_DOMAIN));
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		MetaDataCondition conditions1[] = {
								MetaDataSet.newCondition(
										UserMetaData.USER_TYPE, MetaDataCondition.LIKE, "public" ),
		};
		try {
			rl = fs.query(conditions1,selects);
			if (rl != null) { 
				for (int i=0;i<rl.length;i++) {
					if (!rl[i].getValue(SRBMetaDataSet.USER_DOMAIN).equals("home")&&!domains.contains(rl[i].getValue(SRBMetaDataSet.USER_DOMAIN))) domains.add((String) rl[i].getValue(SRBMetaDataSet.USER_DOMAIN));
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		MetaDataCondition conditions2[] = {
				MetaDataSet.newCondition(
						UserMetaData.USER_TYPE, MetaDataCondition.LIKE, "sysadmin" ),
		};
		try {
			rl = fs.query(conditions1,selects);
			if (rl != null) { 
				for (int i=0;i<rl.length;i++) {
					if (!rl[i].getValue(SRBMetaDataSet.USER_DOMAIN).equals("home")&&!domains.contains(rl[i].getValue(SRBMetaDataSet.USER_DOMAIN))) domains.add((String) rl[i].getValue(SRBMetaDataSet.USER_DOMAIN));
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String[] rt=new String[domains.size()];
		int i=0;
		for (String s:domains) {
			rt[i]=s;
			i++;
		}
		
    	return rt;
    }
    
    public static String[] getUsernamesByDomainName(SRBFileSystem fs, String domainName){
    	List<String> usernames=new ArrayList<String>();
		MetaDataCondition[] conditions = {
				MetaDataSet.newCondition(
						SRBMetaDataSet.USER_DOMAIN, MetaDataCondition.LIKE, domainName ),
//						MetaDataSet.newCondition(
//								UserMetaData.USER_TYPE, MetaDataCondition.LIKE, "public" ),
//								MetaDataSet.newCondition(
//										UserMetaData.USER_TYPE, MetaDataCondition.LIKE, "sysadmin" ),
		};
		MetaDataSelect[] selects ={
		MetaDataSet.newSelection( SRBMetaDataSet.USER_NAME ),
		//MetaDataSet.newSelection( SRBMetaDataSet.USER_DOMAIN)
		};
		MetaDataRecordList[] rl;
		try {
			rl = fs.query(conditions,selects);
			if (rl != null) {
				for (int i=0;i<rl.length;i++) {
					usernames.add((String) rl[i].getValue(SRBMetaDataSet.USER_NAME));
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		String[] rt=new String[usernames.size()];
		int i=0;
		for (String s:usernames) {
			rt[i]=s;
			i++;
		}
		
    	return rt;
    	
    }

	public static String[] getUsernames(IRODSFileSystem fs) {
		MetaDataRecordList[] recordList = null;
		Log.log(Log.DEBUG, "getUsernames  from "+fs);
		try {
			recordList = fs
						.query(
								new MetaDataSelect[] { MetaDataSet
										.newSelection(IRODSMetaDataSet.USER_NAME),MetaDataSet
										.newSelection(IRODSMetaDataSet.USER_ZONE) });
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	String[] names;
        if(recordList != null) {
            names = new String[recordList.length];

            for(int i=0; i<recordList.length; i++) {
                names[i] = (String)recordList[i].getValue(IRODSMetaDataSet.USER_NAME);
                if (!recordList[i].getValue(IRODSMetaDataSet.USER_ZONE).equals(fs.getZone())) names[i] += "#"+(String)recordList[i].getValue(IRODSMetaDataSet.USER_ZONE);
            }
        } else {
            names = new String[]{};
        }
        Arrays.sort(names);

        return names;

	}
	
	public static String[] getFilePermissions(IRODSFile file){
		MetaDataRecordList[] recordList = null;
		IRODSFileSystem fs=(IRODSFileSystem) file.getFileSystem();
		Log.log(Log.DEBUG, "getFilePermissions for "+file+" from "+fs);
		try {
			recordList = fs
						.query(
								new MetaDataCondition[] {
//								        MetaDataSet.newCondition( IRODSMetaDataSet.FILE_NAME,
//								  	          MetaDataCondition.LIKE, file.getName() ),
//								  	        MetaDataSet.newCondition( IRODSMetaDataSet.,
//								  	          MetaDataCondition.EQUAL, fs.getUserName()),
//								  	        MetaDataSet.newCondition( IRODSMetaDataSet.RSRC_ACCS_USER_DOMAIN,
//								  	          MetaDataCondition.EQUAL, fs.getDomainName() ),
//								  	        MetaDataSet.newCondition( SRBMetaDataSet.RSRC_ACCS_USER_ZONE,
//								  	          MetaDataCondition.EQUAL, fs.getMcatZone() ),
										
										MetaDataSet
										.newCondition(
												IRODSMetaDataSet.FILE_NAME,
												MetaDataCondition.LIKE,
												file.getName()) 
												},
								new MetaDataSelect[] { MetaDataSet
										.newSelection(IRODSMetaDataSet.USER_NAME),
										MetaDataSet
										.newSelection(IRODSMetaDataSet.ACCESS_CONSTRAINT)
										});
//			recordList = fs.query(MetaDataSet
//					.newSelection(IRODSMetaDataSet.RESOURCE_NAME));

//			recordList = MetaDataRecordList.getAllResults(recordList);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    	
    	String[] names;
        if(recordList != null) {
            names = new String[recordList.length];

            for(int i=0; i<recordList.length; i++) {
                names[i] = (String)recordList[i].getValue(IRODSMetaDataSet.RESOURCE_NAME);
            }
        } else {
            names = new String[]{};
        }

        return names;
		
	}
	
	public static RemoteFile[] getIRODSCollectionDetails(RemoteFile file){
		Log.log(Log.DEBUG, "getIRODSCollectionDetails '"+file.getAbsolutePath()+"' for "+((IRODSFileSystem)file.getFileSystem()).getUserName());
		String[] selectFieldNames = {
				IRODSMetaDataSet.FILE_NAME,
				IRODSMetaDataSet.DIRECTORY_NAME,
				IRODSMetaDataSet.CREATION_DATE,
				IRODSMetaDataSet.MODIFICATION_DATE,
				IRODSMetaDataSet.SIZE,
//				IRODSMetaDataSet.FILE_ACCESS_TYPE 
			};
		MetaDataCondition conditions[] = {
							MetaDataSet.newCondition(
									GeneralMetaData.DIRECTORY_NAME,	MetaDataCondition.EQUAL, file.getAbsolutePath() ),
							MetaDataSet.newCondition(
									IRODSMetaDataSet.FILE_REPLICA_NUM,	MetaDataCondition.EQUAL, 0 ),
//							MetaDataSet.newCondition(
//									IRODSMetaDataSet.USER_NAME,	MetaDataCondition.EQUAL, ((IRODSFileSystem)file.getFileSystem()).getUserName() ),
						};
		MetaDataSelect selects[] =
				MetaDataSet.newSelection( selectFieldNames );
		
		MetaDataCondition conditions1[] = {
				MetaDataSet.newCondition(
						IRODSMetaDataSet.PARENT_DIRECTORY_NAME,	MetaDataCondition.EQUAL, file.getAbsolutePath() ),
//				MetaDataSet.newCondition(
//						IRODSMetaDataSet.DIRECTORY_USER_NAME,	MetaDataCondition.EQUAL, ((IRODSFileSystem)file.getFileSystem()).getUserName() ),
			};
		MetaDataSelect selects1[] =
			MetaDataSet.newSelection( new String[]{
					IRODSMetaDataSet.DIRECTORY_NAME,
					IRODSMetaDataSet.DIRECTORY_TYPE,
					IRODSMetaDataSet.DIRECTORY_CREATE_DATE,
					IRODSMetaDataSet.DIRECTORY_MODIFY_DATE,
//					IRODSMetaDataSet.DIRECTORY_ACCESS_TYPE
					} );
		Comparator<Object> comparator = new Comparator<Object>() {
			public int compare(Object file1, Object file2) {
				return (((GeneralFile)file1).getName().toLowerCase().compareTo(((GeneralFile)file2).getName().toLowerCase()));
			}     			
		};
		try {
			MetaDataRecordList[] fileDetails = ((IRODSFileSystem)file.getFileSystem()).query(conditions,selects,MAX_QUERY_NUM);
    		MetaDataRecordList[] dirDetails = ((IRODSFileSystem)file.getFileSystem()).query(conditions1,selects1,MAX_QUERY_NUM,Namespace.DIRECTORY);
    		if (fileDetails==null) fileDetails=new MetaDataRecordList[0];
    		if (dirDetails==null) dirDetails=new MetaDataRecordList[0];
    		CachedFile[] files=new CachedFile[fileDetails.length];
    		CachedFile[] dirs=new CachedFile[dirDetails.length];
    		int i=0;
    		Log.log(Log.DEBUG, "file num:"+fileDetails.length);
    		for (MetaDataRecordList p:fileDetails) {
    			files[i]=new CachedFile((RemoteFileSystem)file.getFileSystem(),(String)p.getValue(IRODSMetaDataSet.DIRECTORY_NAME),(String)p.getValue(IRODSMetaDataSet.FILE_NAME));
    			files[i].setLastModified(Long.parseLong((String) p.getValue(IRODSMetaDataSet.MODIFICATION_DATE))*1000);
    			files[i].setLength(Long.parseLong((String)p.getValue(IRODSMetaDataSet.SIZE)));
    			files[i].setDirFlag(false);
//    			int permission=Integer.parseInt((String)p.getValue(IRODSMetaDataSet.FILE_ACCESS_TYPE));
//    			if (permission>=1120)
    				files[i].setCanWriteFlag(true);
//    			else
//    				files[i].setCanWriteFlag(false);
    			i++;
    		}
    		Arrays.sort((Object[])files, comparator);
    		Log.log(Log.DEBUG, "col num:"+dirDetails.length);
    		i=0;
    		for (MetaDataRecordList p:dirDetails) {
    			dirs[i]=new CachedFile((RemoteFileSystem)file.getFileSystem(),file.getAbsolutePath(),(String)p.getValue(IRODSMetaDataSet.DIRECTORY_NAME));
    			dirs[i].setLastModified(Long.parseLong((String)p.getValue(IRODSMetaDataSet.DIRECTORY_MODIFY_DATE))*1000);
    			dirs[i].setDirFlag(true);
//    			int permission=Integer.parseInt((String)p.getValue(IRODSMetaDataSet.DIRECTORY_ACCESS_TYPE));
//    			if (permission>=1120)
    			dirs[i].setCanWriteFlag(true);
//    			else
//    				files[i].setCanWriteFlag(false);
    			i++;
    		}
    		Arrays.sort((Object[])dirs, comparator);
    		
    		CachedFile[] detailList=new CachedFile[files.length+dirs.length];
    		System.arraycopy(dirs, 0, detailList, 0, dirs.length);
    		System.arraycopy(files, 0, detailList, dirs.length, files.length);
    		
    		return detailList;
		} catch (NullPointerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
}
