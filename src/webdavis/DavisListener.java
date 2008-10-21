package webdavis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import edu.sdsc.grid.io.irods.IRODSFileSystem;
import edu.sdsc.grid.io.srb.SRBFileSystem;

public class DavisListener implements HttpSessionListener {

	public void sessionCreated(HttpSessionEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	public void sessionDestroyed(HttpSessionEvent event) {
		HttpSession session = event.getSession();
		System.out.println("session destroyed: "+session.getId());
		Map sessMap = (Map)session.getAttribute(Davis.CREDENTIALS);
		List creds= new ArrayList(sessMap.values());
		DavisSession davisSession=null;
		Map contMap;
		for (int i=0;i<creds.size();i++){
			davisSession=(DavisSession)sessMap.get(i);
			contMap = (Map) session.getServletContext().getAttribute(Davis.CREDENTIALS);
			if (contMap != null) {
				// System.out.println("Dumping credential cache:"+credentials);
				davisSession = (DavisSession) contMap.remove(davisSession.getSessionID());
			}
			if (davisSession.getRemoteFileSystem().isConnected()){
				if (davisSession.getRemoteFileSystem() instanceof SRBFileSystem){
					try {
						((SRBFileSystem)davisSession.getRemoteFileSystem()).close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else if (davisSession.getRemoteFileSystem() instanceof IRODSFileSystem){
					try {
						((IRODSFileSystem)davisSession.getRemoteFileSystem()).close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

			}
		}
		
		
	}

}
