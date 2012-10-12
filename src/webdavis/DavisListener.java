package webdavis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

/**
 * A session listener to destroy idle sessions
 * @author Shunde Zhang
 */
public class DavisListener implements HttpSessionListener {

	public void sessionCreated(HttpSessionEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	public void sessionDestroyed(HttpSessionEvent event) {
		HttpSession session = event.getSession();
		String sessionID = (String) session.getAttribute(Davis.SESSION_ID);
		Log.log(Log.INFORMATION,"HTTP session to destroy: "+session.getId()+". Davis session to destroy: "+sessionID);
		AuthorizationProcessor.getInstance().destroy(sessionID);
//		Map sessMap = (Map)session.getAttribute(Davis.CREDENTIALS);
//		if (sessMap==null) return;
//		List creds= new ArrayList(sessMap.values());
//		Log.log(Log.DEBUG,"Going to dump credentials:"+creds);
//		DavisSession davisSession=null;
//		Map contMap;
//		for (int i=0;i<creds.size();i++){
//			davisSession=(DavisSession)creds.get(i);
//			contMap = (Map) session.getServletContext().getAttribute(Davis.CREDENTIALS);
//			if (contMap != null) {
//				Log.log(Log.DEBUG,"Dumping credential cache:"+davisSession.getSessionID());
//				davisSession = (DavisSession) contMap.remove(davisSession.getSessionID());
//			}
//			if (davisSession!=null) davisSession.disconnect();
//		}
		
		
	}

}
