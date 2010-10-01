package webdavis.quickshare;

import edu.sdsc.grid.io.MetaDataCondition;
import edu.sdsc.grid.io.MetaDataRecordList;
import edu.sdsc.grid.io.MetaDataSelect;
import edu.sdsc.grid.io.MetaDataSet;
import edu.sdsc.grid.io.irods.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import webdavis.DavisConfig;
import webdavis.Log;
import webdavis.DavisUtilities;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class QuickShare extends HttpServlet {

    private IRODSAccount account = null;
    private ServletConfig config = null;
    private String key = null;
   // private final static String CONFIGPATH = "/WEB-INF/quickshare.properties";
    private String username = null;

    public void init(ServletConfig config) throws ServletException {
    	
    	while (DavisConfig.getInstance(false) == null) {
    		//Log.log(Log.INFORMATION, "QuickShare: Davis config is not yet loaded - waiting...");
    		System.err.println("QuickShare: Davis config is not yet loaded - waiting...");
    		try {Thread.sleep(10000);} catch(Exception e) {}
//    		throw new ServletException("QuickShare service is not running.");
    	}
        Log.log(Log.INFORMATION, "QuickShare is using Davis' configuration class");
        this.config = config;
      //  Properties properties = new Properties();
     //   try {
        //	Log.log(Log.INFORMATION, "QuickShare config path: " + config.getServletContext().getRealPath(CONFIGPATH));
         //   properties.load(new FileInputStream(config.getServletContext().getRealPath(CONFIGPATH)));
           // username = properties.getProperty("username");
            username = DavisConfig.getInstance().getSharingUser();
           //String password = properties.getProperty("password");
            String password = DavisConfig.getInstance().getSharingPassword();
           
            //String host = properties.getProperty("irods-host");
            String host = DavisConfig.getInstance().getSharingHost();
            //int port = Integer.valueOf(properties.getProperty("irods-port"));
            int port = DavisConfig.getInstance().getSharingPort();
            //String zone = properties.getProperty("irods-zone");
            String zone = DavisConfig.getInstance().getSharingZone();
            //key = properties.getProperty("metadata-key");
            key = DavisConfig.getInstance().getSharingKey();
			account = new IRODSAccount(host, port, username, password,  "/" + zone + "/home", zone, "");
      //  } catch(IOException e) {
      //      Log.log(Log.ERROR, "QuickShare: Cannot open "+CONFIGPATH+".  Please make sure it's in the classpath.");
      //      throw new ServletException("FATAL: Cannot start QuickShare servlet");
      //  }
    }

    public IRODSFileSystem getFilesystem() throws IOException {
    	
        return new IRODSFileSystem(account);
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
        	input = new IRODSRandomAccessFile(file, "r");
        } catch (SecurityException e) {
        	response.sendError(HttpServletResponse.SC_FORBIDDEN, "The file is not readable by the "+username+" user.");
        	return;
        }

        while ((count = input.read(buf)) > 0) 
            output.write(buf, 0, count);

        output.flush();
        output.close();
    }

    protected IRODSFile findFile(IRODSFileSystem sys, String path) throws IOException {
    	
    	Log.log(Log.DEBUG, "QuickShare: locating file: "+path);
        MetaDataSelect selectFile[] = MetaDataSet.newSelection(new String[] {
            IRODSMetaDataSet.FILE_NAME,
            IRODSMetaDataSet.DIRECTORY_NAME,
            IRODSMetaDataSet.PATH_NAME,
            IRODSMetaDataSet.PARENT_DIRECTORY_NAME   
        });

//        MetaDataCondition[] condList = new MetaDataCondition[1];
        MetaDataCondition[] condList = new MetaDataCondition[1];

        String pattern = "http%/"+path;
        condList[0] = MetaDataSet.newCondition(key, MetaDataCondition.LIKE, pattern);
//System.err.println("matching with '"+pattern+"'");

        MetaDataRecordList[] recordList = sys.query(condList, selectFile);
//System.err.println("recordList="+recordList);
//if((recordList != null) && (recordList.length  > 0)) {
//System.err.println("list size="+recordList.length);
//for (int i = 0; i < recordList.length; i++) {
//MetaDataRecordList p = recordList[i]; 
//System.err.println("value="+(String)p.getValue(IRODSMetaDataSet.FILE_NAME));
//}
//}
//        if((recordList != null) && (recordList.length == 1))
        if((recordList != null) && (recordList.length > 0)) {
            MetaDataRecordList record = recordList[0];
//            String parent = (String)(record.getValue(record.getFieldIndex(IRODSMetaDataSet.PARENT_DIRECTORY_NAME)));
            String filename = (String)(record.getValue(record.getFieldIndex(IRODSMetaDataSet.FILE_NAME)));
            String dirname = (String)(record.getValue(record.getFieldIndex(IRODSMetaDataSet.DIRECTORY_NAME)));

//System.err.println("parent: " + parent);
//System.err.println("filename: " + filename);
//System.err.println("dirname: " + dirname);

            IRODSFile file = new IRODSFile(sys, dirname + "/" + filename);
            return file;
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
			sys = getFilesystem();
			
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
			if(sys != null)
				sys.close();
		}
    }
}
