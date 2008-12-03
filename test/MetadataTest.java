import java.io.FileNotFoundException;
import java.io.IOException;

import webdavis.FSUtilities;

import edu.sdsc.grid.io.FileFactory;
import edu.sdsc.grid.io.srb.SRBAccount;
import edu.sdsc.grid.io.srb.SRBFileSystem;


public class MetadataTest {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	public static void main(String[] args) throws FileNotFoundException, IOException {
		// TODO Auto-generated method stub
		SRBFileSystem fileSystem = (SRBFileSystem) FileFactory.newFileSystem( new SRBAccount( ) );
		System.out.println(fileSystem);
		
//		Object[] domains=FSUtilities.getDomains(fileSystem);
//		for (Object s:domains) System.out.println("domain:"+s);
//		System.out.println("================");
//		Object[] usernames=FSUtilities.getUsernamesByDomainName(fileSystem, (String) domains[domains.length-1]);
//		for (Object s:usernames) System.out.println("user:"+s);
//		fileSystem.close();
		System.out.println("fileSystem zone:"+fileSystem.getMcatZone());
//		String[] res=FSUtilities.getSRBResources(fileSystem, "srb.sapac.edu.au");
		String[] res=FSUtilities.getAvailableResource(fileSystem);
		for (String s:res) System.out.println("res:"+s);

	}

}
