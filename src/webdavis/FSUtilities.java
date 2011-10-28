package webdavis;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.Vector;

import edu.sdsc.grid.io.GeneralFile;
import edu.sdsc.grid.io.GeneralMetaData;
import edu.sdsc.grid.io.MetaDataCondition;
import edu.sdsc.grid.io.MetaDataRecordList;
import edu.sdsc.grid.io.MetaDataSelect;
import edu.sdsc.grid.io.MetaDataSet;
import edu.sdsc.grid.io.Namespace;
import edu.sdsc.grid.io.RemoteFile;
import edu.sdsc.grid.io.RemoteFileSystem;
import edu.sdsc.grid.io.ResourceMetaData;
import edu.sdsc.grid.io.UserMetaData;
import edu.sdsc.grid.io.irods.IRODSAccount;
import edu.sdsc.grid.io.irods.IRODSConstants;
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
	
	public static SimpleDateFormat dateFormat = new SimpleDateFormat("E dd MMM yyyy HH:mm:ss z");

	private static final boolean[] ESCAPED;

    static {
        ESCAPED = new boolean[128];
        for (int i = 0; i < 128; i++) {
            ESCAPED[i] = true;
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            ESCAPED[i] = false;
        }
        for (int i = 'a'; i <= 'z'; i++) {
            ESCAPED[i] = false;
        }
        for (int i = '0'; i <= '9'; i++) {
            ESCAPED[i] = false;
        }
        ESCAPED['+'] = false;
        ESCAPED['-'] = false;
        ESCAPED['='] = false;
        ESCAPED['.'] = false;
        ESCAPED['_'] = false;
        ESCAPED['*'] = false;
        ESCAPED['('] = false;
        ESCAPED[')'] = false;
        ESCAPED[','] = false;
        ESCAPED['@'] = false;
        ESCAPED['\''] = false;
        ESCAPED['$'] = false;
        ESCAPED[':'] = false;
        ESCAPED['&'] = false;
        ESCAPED['!'] = false;
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public static String escape(String name) throws IOException {
        boolean dir = name.endsWith("/");
        if (dir) name = name.substring(0, name.length() - 1);
        StringBuffer buffer = new StringBuffer();
        char[] chars = name.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] > 0x7f || ESCAPED[chars[i]]) {
                byte[] bytes = new String(chars, i, 1).getBytes("UTF-8");
                for (int j = 0; j < bytes.length; j++) {
                    buffer.append("%");
                    buffer.append(Integer.toHexString((bytes[j] >> 4) & 0x0f));
                    buffer.append(Integer.toHexString(bytes[j] & 0x0f));
                }
            } else {
                buffer.append(chars[i]);
            }
        }
        if (dir) buffer.append("/");
        return buffer.toString();
    }

	public static String getiRODSUsernameByDN(IRODSFileSystem fs, String dn){
		MetaDataRecordList[] recordList = null;
		Log.log(Log.DEBUG, "getiRODSUsernameByDN '"+dn+"' from "+fs);
		try {
			recordList = fs.query(new MetaDataCondition[]{MetaDataSet.newCondition(IRODSMetaDataSet.USER_DN, MetaDataCondition.EQUAL, dn)},
								new MetaDataSelect[]{ MetaDataSet.newSelection(IRODSMetaDataSet.USER_NAME)});
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
        if(recordList != null&&recordList.length>0) 
            return (String)recordList[0].getValue(IRODSMetaDataSet.USER_NAME);

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
	      rl = fs.query(conditions, selects);

	      if (rl == null) {
	        //Same as above, just no zone
	        //Metadata to determine available resources was added only after SRB3
	        rl = fs.query(SRBMetaDataSet.RESOURCE_NAME);
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
				new MetaDataSelect[] {MetaDataSet.newSelection(/*IRODSMetaDataSet.RESOURCE_NAME*/ResourceMetaData.COLL_RESOURCE_NAME)});
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
			recordList = fs.query(new MetaDataCondition[]{MetaDataSet.newCondition(SRBMetaDataSet.RSRC_ACCESS_PRIVILEGE, MetaDataCondition.LIKE, "%write%" ),
								  	    MetaDataSet.newCondition( SRBMetaDataSet.RSRC_ACCS_USER_NAME, MetaDataCondition.EQUAL, fs.getUserName()),
								  	    MetaDataSet.newCondition( SRBMetaDataSet.RSRC_ACCS_USER_DOMAIN, MetaDataCondition.EQUAL, fs.getDomainName() ),
//								  	        MetaDataSet.newCondition( SRBMetaDataSet.RSRC_ACCS_USER_ZONE,
//								  	          MetaDataCondition.EQUAL, fs.getMcatZone() ),										
										MetaDataSet.newCondition(SRBMetaDataSet.RSRC_OWNER_ZONE, MetaDataCondition.EQUAL, zoneName)},
								new MetaDataSelect[]{MetaDataSet.newSelection(SRBMetaDataSet.RESOURCE_NAME)});
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
			rl = fs.query(conditions, selects);
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
			rl = fs.query(conditions1, selects);
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
			rl = fs.query(conditions1, selects);
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
			rl = fs.query(conditions, selects);
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
			recordList = fs.query(new MetaDataSelect[]{MetaDataSet.newSelection(IRODSMetaDataSet.USER_NAME), 
									MetaDataSet.newSelection(IRODSMetaDataSet.USER_ZONE)}, DavisConfig.JARGON_MAX_QUERY_NUM);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	String[] names;
        if(recordList != null) {
            names = new String[recordList.length];
            for(int i = 0; i < recordList.length; i++) {
                names[i] = (String)recordList[i].getValue(IRODSMetaDataSet.USER_NAME);
                if (!recordList[i].getValue(IRODSMetaDataSet.USER_ZONE).equals(fs.getZone())) 
                	names[i] += "#"+(String)recordList[i].getValue(IRODSMetaDataSet.USER_ZONE);
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
			recordList = fs.query(new MetaDataCondition[] {
//								        MetaDataSet.newCondition( IRODSMetaDataSet.FILE_NAME,
//								  	          MetaDataCondition.LIKE, file.getName() ),
//								  	        MetaDataSet.newCondition( IRODSMetaDataSet.,
//								  	          MetaDataCondition.EQUAL, fs.getUserName()),
//								  	        MetaDataSet.newCondition( IRODSMetaDataSet.RSRC_ACCS_USER_DOMAIN,
//								  	          MetaDataCondition.EQUAL, fs.getDomainName() ),
//								  	        MetaDataSet.newCondition( SRBMetaDataSet.RSRC_ACCS_USER_ZONE,
//								  	          MetaDataCondition.EQUAL, fs.getMcatZone() ),
									MetaDataSet.newCondition(IRODSMetaDataSet.FILE_NAME, MetaDataCondition.LIKE, file.getName())},
								new MetaDataSelect[]{MetaDataSet.newSelection(IRODSMetaDataSet.USER_NAME),
												MetaDataSet.newSelection(IRODSMetaDataSet.ACCESS_CONSTRAINT)}, DavisConfig.JARGON_MAX_QUERY_NUM);
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
	
	public static HashMap<String, FileMetadata> getIRODSCollectionMetadata(RemoteFile collection){
		
		return getIRODSCollectionMetadata(collection, null);
	}
		
	public static HashMap<String, FileMetadata> getIRODSCollectionMetadata(RemoteFile collection, String attrName){

		HashMap<String, FileMetadata> results = new HashMap<String, FileMetadata>();
		
		MetaDataSelect selectsFile[] = 
			MetaDataSet.newSelection(new String[] {
					IRODSMetaDataSet.META_DATA_ATTR_NAME,
					IRODSMetaDataSet.META_DATA_ATTR_VALUE,
					IRODSMetaDataSet.FILE_NAME,
					IRODSMetaDataSet.DIRECTORY_NAME
			});
		MetaDataCondition conditionsFile[];
		MetaDataCondition conditionsDir[];
		if (attrName == null) {
			conditionsFile = new MetaDataCondition[] {
				MetaDataSet.newCondition(GeneralMetaData.DIRECTORY_NAME, MetaDataCondition.EQUAL, collection.getAbsolutePath())
			};
			conditionsDir = new MetaDataCondition[] {
				MetaDataSet.newCondition(IRODSMetaDataSet.PARENT_DIRECTORY_NAME, MetaDataCondition.EQUAL, collection.getAbsolutePath())
			};
		} else {
			conditionsFile = new MetaDataCondition[] {
				MetaDataSet.newCondition(GeneralMetaData.DIRECTORY_NAME, MetaDataCondition.EQUAL, collection.getAbsolutePath()),
				MetaDataSet.newCondition(IRODSMetaDataSet.META_DATA_ATTR_NAME, MetaDataCondition.EQUAL, attrName),
			};
			conditionsDir = new MetaDataCondition[] {
				MetaDataSet.newCondition(IRODSMetaDataSet.PARENT_DIRECTORY_NAME, MetaDataCondition.EQUAL, collection.getAbsolutePath()),
				MetaDataSet.newCondition(IRODSMetaDataSet.META_COLL_ATTR_NAME, MetaDataCondition.EQUAL, attrName),
			};
		}
		MetaDataSelect selectsDir[] =
			MetaDataSet.newSelection(new String[] {
				IRODSMetaDataSet.META_COLL_ATTR_NAME,
				IRODSMetaDataSet.META_COLL_ATTR_VALUE,
				IRODSMetaDataSet.DIRECTORY_NAME
			});
		try {
			MetaDataRecordList[] fileDetails = ((IRODSFileSystem)collection.getFileSystem()).query(conditionsFile, selectsFile, DavisConfig.JARGON_MAX_QUERY_NUM);
    		MetaDataRecordList[] dirDetails = ((IRODSFileSystem)collection.getFileSystem()).query(conditionsDir, selectsDir, DavisConfig.JARGON_MAX_QUERY_NUM, Namespace.DIRECTORY);
 			if (fileDetails == null) 
    			fileDetails = new MetaDataRecordList[0];
    		if (dirDetails == null) 
    			dirDetails = new MetaDataRecordList[0];
    		
    		for (MetaDataRecordList p:fileDetails) {
    			String path = (String)p.getValue(IRODSMetaDataSet.DIRECTORY_NAME)+"/"+(String)p.getValue(IRODSMetaDataSet.FILE_NAME);
    			FileMetadata mdata = results.get(path);
    			if (mdata == null) {
	    			mdata = new FileMetadata((RemoteFileSystem)collection.getFileSystem(), (String)p.getValue(IRODSMetaDataSet.DIRECTORY_NAME), (String)p.getValue(IRODSMetaDataSet.FILE_NAME));
	    			results.put(path, mdata);
    			}
    			mdata.addItem((String)p.getValue(IRODSMetaDataSet.META_DATA_ATTR_NAME), (String)p.getValue(IRODSMetaDataSet.META_DATA_ATTR_VALUE));
    		}
    		for (MetaDataRecordList p:dirDetails) {
    			String path = (String)p.getValue(IRODSMetaDataSet.DIRECTORY_NAME);
    			FileMetadata mdata = results.get(path);
    			if (mdata == null) {
	    			mdata = new FileMetadata((RemoteFileSystem)collection.getFileSystem(), collection.getAbsolutePath(), (String)p.getValue(IRODSMetaDataSet.DIRECTORY_NAME));
	    			results.put(path, mdata);
    			}
    			mdata.addItem((String)p.getValue(IRODSMetaDataSet.META_COLL_ATTR_NAME), (String)p.getValue(IRODSMetaDataSet.META_COLL_ATTR_VALUE));
    		}
    		Log.log(Log.DEBUG, "IRODSCollectionMetadata for file '"+collection.getAbsolutePath()+"' for user '"+((IRODSFileSystem)collection.getFileSystem()).getUserName()+"': \n"+results);
    		return results;
		} catch (NullPointerException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static CachedFile[] buildCache(MetaDataRecordList[] fileDetails, MetaDataRecordList[] dirDetails, RemoteFileSystem fileSystem, HashMap<String, FileMetadata> metadata, boolean sort, boolean getFiles, boolean getMetadata) {
		
		Comparator<Object> comparator = null;
		if (sort)
			comparator= new Comparator<Object>() {
			public int compare(Object file1, Object file2) {
				return (((GeneralFile)file1).getName().toLowerCase().compareTo(((GeneralFile)file2).getName().toLowerCase()));
				}     			
			};

		if (fileDetails == null) 
			fileDetails = new MetaDataRecordList[0];
		if (dirDetails == null) 
			dirDetails = new MetaDataRecordList[0];
		Vector <CachedFile> fileList = new Vector();
		CachedFile[] dirs = new CachedFile[dirDetails.length];
		int i = 0;
		Log.log(Log.DEBUG, "Length of file query result: "+fileDetails.length);
		Log.log(Log.DEBUG, "Length of collections query result: "+dirDetails.length);
		String lastName = null;
		String lastParent = null;
		if (getFiles)
    		for (MetaDataRecordList p:fileDetails) {
    			CachedFile file = new CachedFile(fileSystem, (String)p.getValue(IRODSMetaDataSet.DIRECTORY_NAME), (String)p.getValue(IRODSMetaDataSet.FILE_NAME));
    			if (file.getName().equals(lastName) && file.getParent().equals(lastParent)) {
    				String replica = (String)p.getValue(IRODSMetaDataSet.FILE_REPLICA_STATUS);
    				if (replica != null)
    					if (replica.equals("1")) // Clean replica - replace previous replica in list
    						fileList.removeElementAt(fileList.size()-1);	// Delete last item so that this replica replaces it
    					else
    						continue;	// Dirty replica. Given we already have a dirty or clean replica, just discard it
    			}	
    			lastName = file.getName();
    			lastParent = file.getParent();
    			fileList.add(file);
    			file.setLastModified(Long.parseLong((String) p.getValue(IRODSMetaDataSet.MODIFICATION_DATE))*1000);
    			file.setLength(Long.parseLong((String)p.getValue(IRODSMetaDataSet.SIZE)));
    			file.setDirFlag(false);
				file.setCanWriteFlag(true);
//				file.setCanonicalPath((String)p.getValue(IRODSMetaDataSet.DIRECTORY_NAME));
//System.err.println("######file="+file+" canon="+file.getCanonicalPath()+" parent="+file.getParent());
				if (getMetadata && metadata != null) {
					String path = (String)p.getValue(IRODSMetaDataSet.DIRECTORY_NAME)+"/"+(String)p.getValue(IRODSMetaDataSet.FILE_NAME);
					if (metadata.containsKey(path)) 
						file.setMetadata(metadata.get(path).getMetadata());
				}
				String replica = (String)p.getValue(IRODSMetaDataSet.FILE_REPLICA_STATUS);
    			if (replica != null && replica.equals("0")) {
    				String s = "";
    				if (file.length() == 0)
    					s = " (its length is 0)";
    				Log.log(Log.WARNING, "Using a dirty copy of "+file.getAbsolutePath()+s);
    			}
    			i++;
    		}
		CachedFile[] files = fileList.toArray(new CachedFile[0]);
		if (sort)
			Arrays.sort((Object[])files, comparator);
		
		i = 0;
		for (MetaDataRecordList p:dirDetails) {
			dirs[i] = new CachedFile(fileSystem, (String)p.getValue(IRODSMetaDataSet.DIRECTORY_NAME));
			try {
				dirs[i].setLastModified(Long.parseLong((String)p.getValue(IRODSMetaDataSet.DIRECTORY_MODIFY_DATE))*1000);
			} catch (Exception e) {
				Log.log(Log.ERROR, "failed to parse last modified time for "+dirs[i].getAbsolutePath()+": "+(String)p.getValue(IRODSMetaDataSet.DIRECTORY_MODIFY_DATE));
				dirs[i].setLastModified(0);
			}
			dirs[i].setDirFlag(true);
			dirs[i].setCanWriteFlag(true);
//			dirs[i].setCanonicalPath((String)p.getValue(IRODSMetaDataSet.PARENT_DIRECTORY_NAME));
//System.err.println("######dir="+dirs[i]+" canon="+dirs[i].getCanonicalPath()+" parent="+dirs[i].getParent());
			if (getMetadata && metadata != null) {
				String path = (String)p.getValue(IRODSMetaDataSet.DIRECTORY_NAME);
				if (metadata.containsKey(path)) 
					dirs[i].setMetadata(metadata.get(path).getMetadata());
			}
			i++;
		}
		if (sort)
			Arrays.sort((Object[])dirs, comparator);
//System.err.println("************files:"+files.length);		
//System.err.println("************dirs:"+dirs.length);		
		CachedFile[] detailList = new CachedFile[files.length+dirs.length];
		System.arraycopy(dirs, 0, detailList, 0, dirs.length);
		System.arraycopy(files, 0, detailList, dirs.length, files.length);	
		return detailList;
	}
	
	public static RemoteFile[] getIRODSCollectionDetails(RemoteFile file){
		
		return getIRODSCollectionDetails(file, true, true, false);
	}

	public static CachedFile[] getIRODSCollectionDetails(RemoteFile collection, boolean sort, boolean getFiles, boolean getMetadata){
		
		// For files with multiple replicas, a clean replica will be returned in the result. If only a dirty copy is found, then that will be used.
		HashMap<String, FileMetadata> metadata = null;
		if (getMetadata)
			metadata = getIRODSCollectionMetadata(collection);
		Log.log(Log.DEBUG, "getIRODSCollectionDetails '"+collection.getAbsolutePath()+"' for "+((IRODSFileSystem)collection.getFileSystem()).getUserName());
		MetaDataCondition conditionsFile[] = {
			MetaDataSet.newCondition(GeneralMetaData.DIRECTORY_NAME, MetaDataCondition.EQUAL, collection.getAbsolutePath()),
//			MetaDataSet.newCondition(IRODSMetaDataSet.FILE_REPLICA_STATUS, MetaDataCondition.EQUAL, "1"),
//			MetaDataSet.newCondition(IRODSMetaDataSet.FILE_REPLICA_NUM,	MetaDataCondition.EQUAL, 0),
//			MetaDataSet.newCondition(IRODSMetaDataSet.USER_NAME, MetaDataCondition.EQUAL, ((IRODSFileSystem)file.getFileSystem()).getUserName()),
		};
		MetaDataSelect selectsFile[] = MetaDataSet.newSelection(new String[]{
				IRODSMetaDataSet.FILE_NAME,
				IRODSMetaDataSet.DIRECTORY_NAME,
				IRODSMetaDataSet.CREATION_DATE,
				IRODSMetaDataSet.MODIFICATION_DATE,
				IRODSMetaDataSet.SIZE,
				IRODSMetaDataSet.RESOURCE_NAME,
				IRODSMetaDataSet.FILE_REPLICA_STATUS,
//				IRODSMetaDataSet.META_DATA_ATTR_NAME,
//				IRODSMetaDataSet.META_DATA_ATTR_VALUE,
//				IRODSMetaDataSet.FILE_ACCESS_TYPE 
			});
		MetaDataCondition conditionsDir[] = {
			MetaDataSet.newCondition(IRODSMetaDataSet.PARENT_DIRECTORY_NAME, MetaDataCondition.EQUAL, collection.getAbsolutePath()),
//##			MetaDataSet.newCondition(IRODSMetaDataSet.FILE_REPLICA_STATUS, MetaDataCondition.EQUAL, "1"),
//			MetaDataSet.newCondition(IRODSMetaDataSet.DIRECTORY_USER_NAME, MetaDataCondition.EQUAL, ((IRODSFileSystem)file.getFileSystem()).getUserName()),
		};
		MetaDataSelect selectsDir[] = MetaDataSet.newSelection(new String[]{
				IRODSMetaDataSet.DIRECTORY_NAME,
				IRODSMetaDataSet.DIRECTORY_TYPE,
				IRODSMetaDataSet.DIRECTORY_CREATE_DATE,
				IRODSMetaDataSet.DIRECTORY_MODIFY_DATE,
				IRODSMetaDataSet.PARENT_DIRECTORY_NAME,
//##				IRODSMetaDataSet.RESOURCE_NAME,
//				IRODSMetaDataSet.META_COLL_ATTR_NAME,
//				IRODSMetaDataSet.META_COLL_ATTR_VALUE,
//				IRODSMetaDataSet.DIRECTORY_ACCESS_TYPE
			});
		try {
			MetaDataRecordList[] fileDetails = null;
			if (getFiles)
				fileDetails = ((IRODSFileSystem)collection.getFileSystem()).query(conditionsFile, selectsFile, DavisConfig.JARGON_MAX_QUERY_NUM);
    		MetaDataRecordList[] dirDetails = ((IRODSFileSystem)collection.getFileSystem()).query(conditionsDir, selectsDir, DavisConfig.JARGON_MAX_QUERY_NUM, Namespace.DIRECTORY);
    		return buildCache(fileDetails, dirDetails, (RemoteFileSystem)collection.getFileSystem(), metadata, sort, getFiles, getMetadata);
		} catch (NullPointerException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Test an iRODS session connection
	 * 
	 * @param davisSession
	 * @return String Null if connection is ok, an exception message if not.
	 */
	public static String testConnection(final DavisSession davisSession) {
		
		if (!(davisSession.getRemoteFileSystem() instanceof IRODSFileSystem))
			return null;
		
		String message = null;
		try {
			((IRODSFileSystem)davisSession.getRemoteFileSystem()).miscServerInfo();
		} catch (ProtocolException e) {
			message = e.getMessage();
			if (message == null)
				message = "ProtocolException";
		} catch (SocketException e) {
			message = e.getMessage();
			if (message == null)
				message = "SocketException";
		} catch (Exception e) {
			message = e.getMessage();
			if (message == null)
				message = "Exception";
			Log.log(Log.WARNING, "Jargon exception when testing for connection: "+e+DavisUtilities.getStackTrace(e));					
		}
		return message;
	}
	
	public static String escapeJSONArg(String s) {
		return "\""+escapeJSON(s)+"\"";
	}

	public static String escapeJSON(String s) {
    	return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
	}
	
	public static String generateJSONFileListing(CachedFile[] fileList, RemoteFile collection, Comparator<Object> comparator, String requestUIHandle, int start, int count, boolean directoriesOnly, boolean directoryListing, boolean truncated, int totalResults) throws IOException {
		
		StringBuffer json = new StringBuffer();
		boolean emptyDir = (fileList.length == 0);
		if (!emptyDir && comparator != null)
			Arrays.sort((Object[]) fileList, comparator);

//		if (!directoryListing && start == 0)
//			start = -1;
		json.append("{\n"+escapeJSONArg("numRows")+":"+escapeJSONArg(""+(fileList.length+(directoryListing ? 1:0)+(emptyDir ? 1:0)))+",");
		if (truncated)
			json.append(escapeJSONArg("truncated")+":"+escapeJSONArg("true")+",");
		if (totalResults > -1)
			json.append(escapeJSONArg("totalResults")+":"+escapeJSONArg(""+totalResults)+",");
		if (requestUIHandle == null)
			requestUIHandle = "null";
		json.append(escapeJSONArg("uiHandle")+":"+escapeJSONArg(requestUIHandle)+",");
		if (collection != null)
			json.append(escapeJSONArg("readOnly")+":"+escapeJSONArg(""+!collection.canWrite())+",");
		json.append(escapeJSONArg("items")+":[\n");
		if (directoryListing && start == 0) {
			json.append("{\"name\":{\"name\":\"... Parent Directory\",\"type\":\"top\",\"parent\":\"..\"},"
						+ "\"date\":{\"value\":\"0\",\"type\":\"top\"},"
						+ "\"size\":{\"size\":\"0\",\"type\":\"top\"},"
						+ "\"sharing\":{\"value\":\"\",\"type\":\"top\"},"
						+ "\"metadata\":{\"value\":\"\",\"type\":\"top\"}}");
			count--;
		}
		if (directoryListing && start > 0)
			start--;
		for (int i = start; i < start + count; i++) {
			if (i >= fileList.length)
				break;
			if ((directoryListing && start == 0) || i > start)
				json.append(",\n");
			String type = fileList[i].isDirectory() ? "d" : "f";
			json.append("{\"name\":{\"name\":"+"\""+FSUtilities.escape(fileList[i].getName())+"\""+",\"type\":"+escapeJSONArg(type)+",\"parent\":"+"\""+escape/*JSONArg*/(fileList[i].getParent())+"\""+"}"
					+",\"date\":{\"value\":"+escapeJSONArg(dateFormat.format(fileList[i].lastModified()))+",\"type\":"+escapeJSONArg(type)+"},"
					+"\"size\":{\"size\":"+escapeJSONArg(""+fileList[i].length())+",\"type\":"+escapeJSONArg(type)+"},"
					+"\"sharing\":{\"value\":"+escapeJSONArg(/*sharingValue*/fileList[i].getSharingValue())+",\"type\":"+escapeJSONArg(type)+"},"
					+"\"metadata\":{\"values\":[");

			HashMap<String, ArrayList<String>> metadata = fileList[i].getMetadata();
			if (metadata != null) {
				json.append("\n");
				String[] names = metadata.keySet().toArray(new String[0]);
				for (int j = 0; j < names.length; j++) {
					if (j > 0)
						json.append(",\n");
					String name = names[j];
					ArrayList<String> values = metadata.get(name);
					for (int k = 0; k < values.size(); k++) {
						if (k > 0)
							json.append(",\n");
						json.append("    {"+escapeJSONArg("name")+":"+escapeJSONArg(name)+","+escapeJSONArg("value")+":"
								+escapeJSONArg(values.get(k))+"}");
					}
				}
			}
			json.append("]");
			if (metadata != null)
				json.append("\n    ");
			json.append(",\"type\":" + escapeJSONArg(type) + "}}");
		}
		if (emptyDir) {
			if (directoryListing)
				json.append(",\n");
			json.append("{\"name\":{\"name\":\""	+ (!directoryListing ? "(No matches)" : "("+(directoriesOnly?"No directories found":"Directory is empty")+")")
							+ "\",\"type\":\"bottom\",\"parent\":\"\"}," + "\"date\":{\"value\":\"0\",\"type\":\"bottom\"},"
							+ "\"size\":{\"size\":\"0\",\"type\":\"bottom\"},"
							+ "\"sharing\":{\"value\":\"\",\"type\":\"bottom\"},"
							+ "\"metadata\":{\"value\":\"\",\"type\":\"bottom\"}}");
		}
		json.append("\n]}");
//System.err.println("!!!!!!!!!returning json:"+json);
		return json.toString();
	}
	
	final static Integer lock = new Integer(0);
	
	public static IRODSFileSystem createIRODSFileSystem(IRODSAccount account, int timeout) throws IOException {
		
		synchronized (lock) {
			IRODSConstants.CONNECTION_TIMEOUT_VALUE = timeout;
			return new IRODSFileSystem(account);
		}
	}

	public static void dumpQueryResult(MetaDataRecordList[] results, String prefix) {

		if (results == null)
			return;
		if (prefix == null)
			prefix = "";
		for (int i = 0; i < results.length; i++)
			System.err.println(prefix+results[i]);
	}
}
