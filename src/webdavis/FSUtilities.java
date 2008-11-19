package webdavis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.sdsc.grid.io.MetaDataCondition;
import edu.sdsc.grid.io.MetaDataRecordList;
import edu.sdsc.grid.io.MetaDataSelect;
import edu.sdsc.grid.io.MetaDataSet;
import edu.sdsc.grid.io.UserMetaData;
import edu.sdsc.grid.io.srb.SRBAccount;
import edu.sdsc.grid.io.srb.SRBFileSystem;
import edu.sdsc.grid.io.srb.SRBMetaDataSet;


public class FSUtilities {
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
								new MetaDataCondition[] { MetaDataSet
										.newCondition(
												SRBMetaDataSet.RSRC_OWNER_ZONE,
												MetaDataCondition.EQUAL,
												zoneName) },
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
