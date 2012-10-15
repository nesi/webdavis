package webdavis.quickshare;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.pub.DataObjectAO;
import org.irods.jargon.core.pub.IRODSFileSystem;
import org.irods.jargon.core.pub.domain.DataObject;
import org.irods.jargon.core.pub.io.IRODSFile;
import org.irods.jargon.core.pub.io.IRODSFileFactory;
import org.irods.jargon.core.pub.io.IRODSRandomAccessFile;
import org.irods.jargon.core.query.AVUQueryElement;
import org.irods.jargon.core.query.AVUQueryOperatorEnum;
import org.irods.jargon.core.query.JargonQueryException;
import org.irods.jargon.core.query.RodsGenQueryEnum;

import webdavis.DavisConfig;
import webdavis.Log;
import webdavis.DavisUtilities;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class QuickShare extends HttpServlet {

    private IRODSAccount account = null;
    private ServletConfig config = null;
    private String key = null;
   // private final static String CONFIGPATH = "/WEB-INF/quickshare.properties";
    private String username = null;
    DavisConfig davisConfig;

    public void init(ServletConfig config) throws ServletException {
    	
		davisConfig = new DavisConfig();
		davisConfig.initConfig(config);

        Log.log(Log.INFORMATION, "QuickShare is using Davis' configuration class");
        this.config = config;
      //  Properties properties = new Properties();
     //   try {
        //	Log.log(Log.INFORMATION, "QuickShare config path: " + config.getServletContext().getRealPath(CONFIGPATH));
         //   properties.load(new FileInputStream(config.getServletContext().getRealPath(CONFIGPATH)));
           // username = properties.getProperty("username");
            username = davisConfig.getSharingUser();
           //String password = properties.getProperty("password");
            String password = davisConfig.getSharingPassword();
           
            //String host = properties.getProperty("irods-host");
            String host = davisConfig.getSharingHost();
            //int port = Integer.valueOf(properties.getProperty("irods-port"));
            int port = davisConfig.getSharingPort();
            //String zone = properties.getProperty("irods-zone");
            String zone = davisConfig.getSharingZone();
            //key = properties.getProperty("metadata-key");
            key = davisConfig.getSharingKey();
			account = new IRODSAccount(host, port, username, password,  "/" + zone + "/home", zone, "");
      //  } catch(IOException e) {
      //      Log.log(Log.ERROR, "QuickShare: Cannot open "+CONFIGPATH+".  Please make sure it's in the classpath.");
      //      throw new ServletException("FATAL: Cannot start QuickShare servlet");
      //  }
    }

    public IRODSFileFactory getFileFactory() throws IOException {
    	IRODSFileSystem fileSystem;
		try {
			fileSystem = IRODSFileSystem.instance();
	        return fileSystem.getIRODSFileFactory(account);
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
	        return fileSystem.getIRODSAccessObjectFactory().getDataObjectAO(account);
		} catch (JargonException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new IOException(e.getMessage());
		}
    }
    private void closeFileSystem(){
    	IRODSFileSystem fileSystem;
		try {
			fileSystem = IRODSFileSystem.instance();
	        fileSystem.close(account);
		} catch (JargonException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }


    protected void sendFile(IRODSFile file, HttpServletResponse response) throws IOException {
    	
        String contentType = config.getServletContext().getMimeType(file.getName());
        response.setHeader("Content-Length", String.valueOf(file.length()));
        response.setContentType((contentType != null) ? contentType : "application/octet-stream");
        response.setContentLength((int) file.length());

        long bufferSize = file.length() / 100;
        // minimum buf size of 50KiloBytes
        if (bufferSize < 51200)
            bufferSize = 51200;
        // maximum buf size of 5MegaByte
        else if (bufferSize > 5242880)
            bufferSize = 5242880;
        byte[] buf = new byte[(int)bufferSize];
        int count = 0;
        ServletOutputStream output = response.getOutputStream();
        IRODSRandomAccessFile input = null;
        try {
        	input = getFileFactory().instanceIRODSRandomAccessFile(file);
        } catch (SecurityException e) {
        	response.sendError(HttpServletResponse.SC_FORBIDDEN, "The file is not readable by the "+username+" user.");
        	return;
        } catch (JargonException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new IOException(e.getMessage());
		}

        while ((count = input.read(buf)) > 0) 
            output.write(buf, 0, count);

        output.flush();
        output.close();
    }

    protected IRODSFile findFile(IRODSFileSystem sys, String path) throws IOException {
    	
    	Log.log(Log.DEBUG, "QuickShare: locating file: "+path);
        String pattern = "http%/"+path;
    	
		List<AVUQueryElement> queryElements = new ArrayList<AVUQueryElement>();



		List<DataObject> dataObjects;
		try {
			queryElements.add(AVUQueryElement.instanceForValueQuery(
					AVUQueryElement.AVUQueryPart.ATTRIBUTE,
					AVUQueryOperatorEnum.LIKE, key));
			queryElements.add(AVUQueryElement.instanceForValueQuery(
					AVUQueryElement.AVUQueryPart.VALUE,
					AVUQueryOperatorEnum.LIKE, pattern));
			dataObjects = getDataObjectAO().findDomainByMetadataQuery(queryElements);
	        if((dataObjects != null) && (dataObjects.size() > 0)) {

	            return getFileFactory().instanceIRODSFile(dataObjects.get(0).getAbsolutePath());
	        }
		} catch (JargonQueryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JargonException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    	

        // Can't find anything that matches the request path
       	Log.log(Log.ERROR, "QuickShare: cannot find file "+path);
        return null;
    }

//    public String encodeURLString(String url) {
//        
//          String s = null;
//          try {
//        	  s = URLEncoder.encode(url, "UTF-8");
//          } catch (UnsupportedEncodingException e) {}
//          s = s.replace("+", "%2B");
//          return s;
//    }

    protected void doGet(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
		
    	Log.log(Log.DEBUG, "QuickShare: received get request: "+req);
		IRODSFileSystem sys = null;
		try {
			
			String uri = req.getRequestURI();
			int i = uri.lastIndexOf('/');
			uri = uri.substring(0, i+1);
			i = req.getPathInfo().lastIndexOf('/');
			uri = uri+DavisUtilities.encodeFileName(req.getPathInfo().substring(i+1));
//System.err.println(req.getRequestURI());
//System.err.println(req.getPathInfo());
//System.err.println(req.getPathTranslated());
//System.err.println(req.getServletPath());
//System.err.println("path! " + uri);
			String context = req.getContextPath() + "/";
			
			//String cut = req.getRequestURI().substring(context.length());
			String cut = uri.substring(context.length());
//System.err.println("cut: " + cut);
			
			//findPath
			IRODSFile file = findFile(sys, cut);
			if (file != null) {
			   //write to outputStream
			   sendFile(file, response);
			} else
			   response.sendError(HttpServletResponse.SC_NOT_FOUND, "QuickShare can't find the file. It may not currently be shared.");
		}
		catch(IOException e) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND,  "QuickShare can't locate the file. It may not currently be shared.");
		}
		finally {
			closeFileSystem();
		}
    }
}
