package webdavis;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.sdsc.grid.io.MetaDataCondition;
import edu.sdsc.grid.io.MetaDataRecordList;
import edu.sdsc.grid.io.MetaDataSelect;
import edu.sdsc.grid.io.MetaDataSet;
import edu.sdsc.grid.io.UserMetaData;
import edu.sdsc.grid.io.irods.IRODSAccount;
import edu.sdsc.grid.io.irods.IRODSFileSystem;
import edu.sdsc.grid.io.irods.IRODSMetaDataSet;
import edu.sdsc.grid.io.srb.SRBAccount;
import edu.sdsc.grid.io.srb.SRBFileSystem;
import edu.sdsc.grid.io.srb.SRBMetaDataSet;


public class FSUtilities {
	
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
				recordList = fs
							.query(
									new MetaDataCondition[] {
//									        MetaDataSet.newCondition( IRODSMetaDataSet.RSRC_ACCESS_PRIVILEGE,
//									  	          MetaDataCondition.LIKE, "%write%" ),
//									  	        MetaDataSet.newCondition( IRODSMetaDataSet.RSRC_ACCS_USER_NAME,
//									  	          MetaDataCondition.EQUAL, fs.getUserName()),
//									  	        MetaDataSet.newCondition( IRODSMetaDataSet.RSRC_ACCS_USER_DOMAIN,
//									  	          MetaDataCondition.EQUAL, fs.getDomainName() ),
//									  	        MetaDataSet.newCondition( SRBMetaDataSet.RSRC_ACCS_USER_ZONE,
//									  	          MetaDataCondition.EQUAL, fs.getMcatZone() ),
											
											MetaDataSet
											.newCondition(
													IRODSMetaDataSet.RESOURCE_ZONE,
													MetaDataCondition.EQUAL,
													currentZone) 
													},
									new MetaDataSelect[] { MetaDataSet
											.newSelection(IRODSMetaDataSet.RESOURCE_NAME) });
//				recordList = fs.query(MetaDataSet
//						.newSelection(IRODSMetaDataSet.RESOURCE_NAME));

//				recordList = MetaDataRecordList.getAllResults(recordList);
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
}
