package webdavis;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.sdsc.grid.io.MetaDataRecordList;
import edu.sdsc.grid.io.RemoteFile;
import edu.sdsc.grid.io.RemoteFileSystem;
import edu.sdsc.grid.io.irods.IRODSFile;
import edu.sdsc.grid.io.irods.IRODSFileInputStream;
import edu.sdsc.grid.io.irods.IRODSFileSystem;
import edu.sdsc.grid.io.srb.SRBFile;
import edu.sdsc.grid.io.srb.SRBFileInputStream;
import edu.sdsc.grid.io.srb.SRBFileSystem;
import edu.sdsc.grid.io.MetaDataField;

/**
 * Default implementation of a handler for requests using the HTTP POST
 * method.
 *
 * @author Eric Glass
 */
public class DefaultPostHandler extends AbstractHandler {

    /**
     * Services requests which use the HTTP POST method.
     * This may, at some point, implement some sort of useful behavior.
     * Right now it doesn't do anything.
     *
     * @param request The request being serviced.
     * @param response The servlet response.
     * @param auth The user's authentication information.
     * @throws SerlvetException If an application error occurs.
     * @throws IOException If an IO error occurs while handling the request.
     */
    public void service(HttpServletRequest request,
            HttpServletResponse response, DavisSession davisSession)
                    throws ServletException, IOException { 
    	String method=request.getParameter("method");
    	String url=getRemoteURL(request,getRequestURL(request),getRequestURICharset());
    	Log.log(Log.DEBUG, "url:"+url+" method:"+method);
        RemoteFile file = getRemoteFile(request, davisSession);
        Log.log(Log.DEBUG, "GET Request for resource \"{0}\".", file);
        if (!file.exists()) {
            Log.log(Log.DEBUG, "File does not exist.");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String requestUrl = getRequestURL(request);
        Log.log(Log.DEBUG, "Request URL: {0}", requestUrl);
        MetaDataRecordList[] permissions=null;
        if (file.getFileSystem() instanceof SRBFileSystem) {
        	permissions=((SRBFile)file).getPermissions(true);
        }else if (file.getFileSystem() instanceof IRODSFileSystem) {
//        	permissions=((IRODSFile)file).getPermissions(true);
        }
		StringBuffer str=new StringBuffer();
		str.append("{\nitems:[");
		if (permissions!=null){
			for (int i=0;i<permissions.length;i++){
//				for (MetaDataField f:p.getFields()){
//					Log.log(Log.DEBUG, f.getName()+" "+p.getValue(f));
//				}
				if (i>0) 
					str.append(",\n");
				else
					str.append("\n");
				str.append("{username:'").append(permissions[i].getValue("user name")).append("', ");
				str.append("domain:'").append(permissions[i].getValue("user domain")).append("', ");
				str.append("permission:'").append(permissions[i].getValue("file access constraint")).append("'}");
			}
		}
		str.append("\n");
		str.append("]}");
        
		ServletOutputStream op = response.getOutputStream ();
		byte[] buf=str.toString().getBytes();
		Log.log(Log.DEBUG, "output("+buf.length+"):\n"+str);
		op.write(buf);
		op.flush();
		op.close();

    }

}
