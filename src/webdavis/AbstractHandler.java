package webdavis;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
//import java.net.URLDecoder;

import java.security.Principal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import edu.sdsc.grid.io.RemoteFile;
import edu.sdsc.grid.io.RemoteFileSystem;
import edu.sdsc.grid.io.irods.IRODSFile;
import edu.sdsc.grid.io.irods.IRODSFileSystem;
import edu.sdsc.grid.io.srb.SRBFile;
import edu.sdsc.grid.io.srb.SRBFileSystem;

/**
 * An abstract implementation of the <code>MethodHandler</code> interface.
 * This class serves as a convenient basis for building method handlers.
 * In addition to providing basic <code>init</code> and <code>destroy</code>
 * methods, several useful utility methods are supplied.
 * 
 * @author Shunde Zhang
 * @author Eric Glass
 */
public abstract class AbstractHandler implements MethodHandler {

    private static final Set KNOWN_WORKGROUPS =
            Collections.synchronizedSet(new HashSet());

    private ServletConfig config;
    
   
    /**
     * Initializes the method handler.  This implementation stores the
     * provided <code>ServletConfig</code> object and makes it available
     * via the <code>getServletConfig</code> method.  Subclasses overriding
     * this method should start by invoking
     * <p>
     * <code>super.init(config);</code>
     *
     * @param config a <code>ServletConfig</code> object containing
     * the servlet's configuration and initialization parameters.
     * @throws ServletException If an error occurs during initialization.
     */
    public void init(ServletConfig config) throws ServletException {
        this.config = config;
    }

    public void destroy() {
        config = null;
    }

    /**
     * Returns the <code>ServletConfig</code> object that was provided to the
     * <code>init</code> method.
     *
     * @return A <code>ServletConfig</code> object containing the servlet's
     * configuration and initialization parameters.
     */
    protected ServletConfig getServletConfig() {
        return config;
    }

    /**
     * Returns the charset used to interpret request URIs.  Davis will
     * attempt to use this charset before resorting to UTF-8.
     *
     * @return A <code>String</code> containing the charset name.
     */
    protected String getRequestURICharset() {
        ServletConfig config = getServletConfig();
        String charset = (config == null) ? null : (String)
                config.getServletContext().getAttribute(
                        DavisConfig.REQUEST_URI_CHARSET);
        return (charset != null) ? charset : "UTF-8";
//        return (charset != null) ? charset : "ISO-8859-1";
    }

    /**
     * Rewrites the supplied HTTP URL against the active context base if
     * necessary.
     *
     * @param request The request being serviced.
     * @param url The HTTP URL to process for rewriting.
     * @return A <code>String</code> containing the rewritten URL.  If
     * no rewriting is required, the provided URL will be returned.
     */
    protected String rewriteURL(HttpServletRequest request, String url) {
        String contextBase = (String)
                request.getAttribute(Davis.CONTEXT_BASE);
        if (contextBase == null || url.startsWith(contextBase)) return url;
        String base = request.getContextPath() + request.getServletPath();
        int index = base.startsWith("/") ? url.indexOf(base) :
                url.indexOf("/", url.indexOf("://") + 3);
        if (index == -1) return url;
        Log.log(Log.INFORMATION,
                "Rewriting URL \"{0}\" against context base \"{1}\".",
                        new Object[] { url, contextBase });
        url = url.substring(index);
        if (!contextBase.endsWith("/")) contextBase += "/";
        if (url.startsWith("/")) url = url.substring(1);
        url = contextBase + url;
        Log.log(Log.INFORMATION, "Rewrote URL to \"{0}\".", url);
        return url;
    }

    /**
     * Convenience method to return the HTTP URL from the request, rewritten
     * against the active context base as necessary.
     *
     * @param request The request being serviced.
     * @return A <code>String</code> containing the rewritten request URL.
     */ 
    protected String getRequestURL(HttpServletRequest request) {
        return rewriteURL(request, request.getRequestURL().toString());
    }

    /**
     * Convenience method to convert a given HTTP URL to the corresponding
     * SMB URL.  The provided request is used to determine the servlet base;
     * this is stripped from the given HTTP URL to get the SMB path.
     * Escaped characters within the specified HTTP URL are interpreted
     * as members of the character set returned by
     * <code>getRequestURICharset()</code>.
     * <b>Note:</b> Currently, the jCIFS library does not handle escaped
     * characters in SMB URLs (i.e.,
     * "<code>smb://server/share/my%20file.txt</code>".  The SMB URLs
     * returned by this method are unescaped for compatibility with jCIFS
     * (i.e., "<code>smb://server/share/my file.txt</code>".  This may result
     * in URLs which do not conform with RFC 2396.  Such URLs may not be
     * accepted by systems expecting compliant URLs (such as Java 1.4's
     * <code>java.net.URI</code> class).
     *
     * @param request The servlet request upon which the HTTP URL is based.
     * @param httpUrl An HTTP URL from which the SMB URL is derived.
     * @throws IOException If an SMB URL cannot be constructed from the
     * given request and HTTP URL.
     */
    protected String getRemoteURL(HttpServletRequest request, String httpUrl)
            throws IOException {
        return getRemoteURL(request, httpUrl, getRequestURICharset());
    }

    /**
     * Convenience method to convert a given HTTP URL to the corresponding
     * SMB URL.  The provided request is used to determine the servlet base;
     * this is stripped from the given HTTP URL to get the SMB path.
     * Escaped characters within the specified HTTP URL are interpreted
     * as members of the given character set.
     * <b>Note:</b> Currently, the jCIFS library does not handle escaped
     * characters in SMB URLs (i.e.,
     * "<code>smb://server/share/my%20file.txt</code>".  The SMB URLs
     * returned by this method are unescaped for compatibility with jCIFS
     * (i.e., "<code>smb://server/share/my file.txt</code>".  This may result
     * in URLs which do not conform with RFC 2396.  Such URLs may not be
     * accepted by systems expecting compliant URLs (such as Java 1.4's
     * <code>java.net.URI</code> class).
     *
     * @param request The servlet request upon which the HTTP URL is based.
     * @param httpUrl An HTTP URL from which the SMB URL is derived.
     * @param charset The character set that should be used to interpret the
     * HTTP URL.
     * @throws IOException If an SMB URL cannot be constructed from the
     * given request and HTTP URL.
     */
    protected String getRemoteURL(HttpServletRequest request, String httpUrl,
            String charset) throws IOException {
        Log.log(Log.DEBUG, "Converting \"{0}\" to path " +
                "using charset \"{1}\".", new Object[] { httpUrl, charset });
        if (httpUrl == null) return null;
       
//		String result = request.getPathInfo();
//		if (result == null) {
//			result = request.getServletPath();
//		}
//		if ((result == null) || (result.equals(""))) {
//			result = "/";
//		}
//        httpUrl = URLDecoder.decode( httpUrl, charset );
//        Log.log(Log.DEBUG, "URLDecoder, httpUrl = \"{0}\".", httpUrl);
      httpUrl = rewriteURL(request, httpUrl);
      String base = request.getContextPath() + request.getServletPath();
      int index;
      if (base.startsWith("/")) {
          index = httpUrl.indexOf(base);
      } else {
          String contextBase = (String)
                  request.getAttribute(Davis.CONTEXT_BASE);
          if (contextBase == null || !httpUrl.startsWith(contextBase)) {
              index = httpUrl.indexOf("/", httpUrl.indexOf("://") + 3);
          } else {
              index = httpUrl.indexOf("/", contextBase.endsWith("/") ?
                      contextBase.length() - 1 : contextBase.length());
          }
      }
      if (index == -1) {
          Log.log(Log.ERROR, "Specified URL is not under this context.");
          return null;
      }
      index += base.length();
      httpUrl = (index < httpUrl.length()) ?
              httpUrl.substring(index) : "/";
      String result=unescape(httpUrl, charset);
//      Log.log(Log.DEBUG, "before Conversion, httpUrl = \"{0}\".", httpUrl);
//      String result = URLDecoder.decode( httpUrl, charset );
      Log.log(Log.INFORMATION, "Converted to path \"{0}\".", result);
      return result;
//		if (result.indexOf("~")>-1) {
//			System.out.println(request.getContextPath());
//			System.out.println(request.getRequestURI());
//			System.out.println(request.getRequestURL());
//			System.out.println(request.getPathInfo());
//			System.out.println(request.getServletPath());
//			String target=result.replaceAll("~",fStore.getHomeDirectory().substring(1));
//			Log.log(Log.DEBUG,"changed path to "+target);
//			return target;
//		}

//        RemoteFile file = new RemoteFile("smb:/" + unescape(httpUrl, charset));
//        String server = file.getServer();
//        base = file.getCanonicalPath();
//        if (server != null && KNOWN_WORKGROUPS.contains(server.toUpperCase())) {
//            Log.log(Log.DEBUG, "Target \"{0}\" is a known workgroup.", server);
//            index = base.indexOf(server);
//            int end = index + server.length();
//            if (end < base.length() && base.charAt(end) == '/') end++;
//            if (end < base.length()) {
//                base = new StringBuffer(base).delete(index, end).toString();
//            }
//        }
//        Log.log(Log.DEBUG, "Converted to path \"{0}\".", base);
//        return base;
    }
    protected String getRemoteParentURL(HttpServletRequest request, String httpUrl,
            String charset) throws IOException {
    	String uri=getRemoteURL(request,httpUrl,charset);
    	return uri.substring(0,uri.lastIndexOf("/"));
    }
    protected RemoteFile getRemoteParentFile(HttpServletRequest request,
    		DavisSession davisSession) throws IOException {
        String url = getRequestURL(request);
        RemoteFileSystem rfs=davisSession.getRemoteFileSystem();
        Log.log(Log.DEBUG, "url:"+url);
        RemoteFile file = null;
        IOException exception = null;
        boolean exists = false;
        String charset = getRequestURICharset();
        Log.log(Log.DEBUG, "charset:"+charset);
        try {
            String uri=getRemoteParentURL(request, url, charset);
            Log.log(Log.DEBUG,"uri(b4 changing home dir,~):'"+uri+"'");
    		if (uri.startsWith("/~")) {
    			uri=uri.replaceAll("/~",davisSession.getHomeDirectory());
    			Log.log(Log.INFORMATION,"changed path to '"+uri+"'");
    		}
            if (rfs instanceof SRBFileSystem){
            	file=new SRBFile((SRBFileSystem) rfs,uri);
            }else if (rfs instanceof IRODSFileSystem){
            	file=new IRODSFile((IRODSFileSystem) rfs,uri);
            }
            exists = file.exists();
        } catch (IOException ex) {
        	ex.printStackTrace();
            exception = ex;
        }
        if (exists) return file;
        Log.log(Log.WARNING, "Returning null getRemoteParentFile (shouldn't happen).");
        return null;
    }
    /**
     * Returns the <code>LockManager</code> used to maintain WebDAV locks.
     *
     * @return The currently installed lock manager.  Returns
     * <code>null</code> if no lock manager is present.
     */ 
    protected LockManager getLockManager() {
        ServletConfig config = getServletConfig();
        return (config == null) ? null : (LockManager)
                config.getServletContext().getAttribute(
                        Davis.LOCK_MANAGER);
    }

    /**
     * Returns the <code>SmbFileFilter</code> used to filter resource
     * requests.  The default implementation uses the global filter
     * installed by the Davenport servlet (if applicable).
     *
     * @return The filter to be applied to requested resources.  Returns
     * <code>null</code> if no filter is to be applied.
     */ 
//    protected SmbFileFilter getFilter() {
//        ServletConfig config = getServletConfig();
//        return (config == null) ? null : (SmbFileFilter)
//                config.getServletContext().getAttribute(
//                        Davis.RESOURCE_FILTER);
//    }

    /**
     * Convenience method to retrieve the <code>SmbFile</code> that
     * is the target of the given request.  This will attempt to obtain
     * the file by interpreting the URL with the character set given by
     * <code>getRequestURICharset()</code>; if this file does not exist, a
     * second attempt will be made using the UTF-8 charset.  If neither file
     * exists, the result of the first attempt will be returned.
     * 
     * @param request The request that is being serviced.
     * @param auth The user's authentication information.
     * @throws IOException If the <code>SmbFile</code> targeted by
     * the specified request could not be created.
     */
    protected RemoteFile getRemoteFile(HttpServletRequest request, DavisSession davisSession) throws IOException {
        String url = getRequestURL(request);
        RemoteFileSystem rfs=davisSession.getRemoteFileSystem();
        Log.log(Log.DEBUG, "url:"+url);
        RemoteFile file = null;
        IOException exception = null;
        boolean exists = false;
        String charset = getRequestURICharset();
        Log.log(Log.DEBUG, "charset:"+charset);
        try {
            String uri=getRemoteURL(request, url, charset);
    		if (uri.startsWith("/~")) {
                Log.log(Log.DEBUG,"uri(b4 changing home dir,~):"+uri);
				uri=uri.replaceAll("/~",davisSession.getHomeDirectory());
				Log.log(Log.INFORMATION,"changed path to "+uri);
			}
    		Log.log(Log.DEBUG,"uri: "+uri);
            
            if (rfs instanceof SRBFileSystem){
            	file=new SRBFile((SRBFileSystem) rfs,uri);
            }else if (rfs instanceof IRODSFileSystem){
            	file=new IRODSFile((IRODSFileSystem) rfs,uri);
            }
            exists = file.exists();
            Log.log(Log.DEBUG,"uri exists: "+exists);
        } catch (IOException ex) {
            exception = ex;
        }
        if (exists) return file;
//        if (charset.equals("UTF-8")) {
//            if (exception != null) {
//                Log.log(Log.DEBUG, exception);
//                throw exception;
//            }
//            return file;
//        }
//        RemoteFile utf8 = null;
//        IOException utf8Exception = null;
//        try {
//            String uri=getRemoteURL(request, url, charset);
//            utf8 = createRemoteFile(getRemoteURL(request, url, "UTF-8"), rfs);
//            exists = utf8.exists();
//        } catch (IOException ex) {
//            utf8Exception = ex;
//        }
//        if (exists) return utf8;
        if (file != null) {
            if (exception != null) {
                Log.log(Log.ERROR, exception);
                throw exception;
            }
            return file;
        }
//        if (utf8 != null) {
//            if (utf8Exception != null) {
//                Log.log(Log.DEBUG, exception);
//                throw utf8Exception;
//            }
//            return utf8;
//        }
        if (exception != null) {
            Log.log(Log.ERROR, exception);
            throw exception;
        }
        Log.log(Log.WARNING, "Returning null RemoteFile (shouldn't happen).");
        return null;
    }
    protected RemoteFile getRemoteFile(String path,	DavisSession davisSession) throws IOException {
        Log.log(Log.DEBUG, "path:"+path);
        RemoteFileSystem rfs=davisSession.getRemoteFileSystem();
		if (path.startsWith("/~")) {
            Log.log(Log.DEBUG,"path(b4 changing home dir,~):"+path);
			path=path.replaceAll("/~",davisSession.getHomeDirectory());
			Log.log(Log.DEBUG,"changed path to "+path);
		}
        RemoteFile file = null;
        try {
            if (rfs instanceof SRBFileSystem){
            	file=new SRBFile((SRBFileSystem) rfs,path);
            }else if (rfs instanceof IRODSFileSystem){
            	file=new IRODSFile((IRODSFileSystem) rfs,path);
            }
        } catch (Exception ex) {
            Log.log(Log.ERROR, ex);
            throw new IOException(ex.getMessage());
       }
       return file;
    }
    /**
     * Convenience method to create an <code>SmbFile</code> object
     * from a specified SMB URL and authentication information.
     * The <code>SmbFile</code> returned will automatically be adjusted
     * to include a trailing slash ("/") in the event that it refers to a
     * directory, share, server, or workgroup.
     *
     * @param smbUrl The SMB URL from which the <code>SmbFile</code> object
     * will be created.
     * @param authentication The authentication information to apply to the
     * <code>SmbFile</code> object.
     * @throws IOException If an <code>SmbFile</code> object could not be
     * created from the provided information.
     */
    protected RemoteFile createRemoteFile(String remoteUrl,
    		RemoteFileSystem rfs) throws IOException {
        try {
            Log.log(Log.DEBUG,
                    "Creating remote file for \"{0}\" with credentials \"{1}\".",
                            new Object[] { remoteUrl, rfs });
            RemoteFile remoteFile = null;
            if (rfs instanceof SRBFileSystem){
            	remoteFile=new SRBFile((SRBFileSystem) rfs,remoteUrl);
            }else if (rfs instanceof IRODSFileSystem){
            	remoteFile=new IRODSFile((IRODSFileSystem) rfs,remoteUrl);
            }
            Log.log(Log.DEBUG,"remoteFile: {0}",remoteFile);
            if (remoteFile.exists()) return remoteFile;
    		if (!remoteFile.createNewFile())
    			throw new IOException("cannot create file: " + remoteUrl);
//            if (!remoteUrl.endsWith("/") && needsSeparator(smbFile)) {
//            	remoteUrl += "/";
//            	remoteFile = (authentication != null) ?
//                        new SmbFile(smbUrl, authentication) :
//                                new SmbFile(remoteUrl);
//            }
//            if (smbFile.getType() == SmbFile.TYPE_WORKGROUP) {
//                String server = smbFile.getServer();
//                if (server != null) {
//                    Log.log(Log.INFORMATION,
//                            "Adding \"{0}\" to the set of known workgroups.",
//                                    server);
//                    KNOWN_WORKGROUPS.add(server.toUpperCase());
//                }
//            }
//            SmbFileFilter filter = getFilter();
//            if (filter != null && !filter.accept(smbFile)) {
//                Log.log(Log.INFORMATION, "Filter blocked access to \"{0}\".",
//                        smbFile);
//                smbFile = new BlockedFile(smbFile);
//            }
            Log.log(Log.DEBUG, "Created remote file \"{0}\".", remoteFile);
            return remoteFile;
//        } catch (SmbException ex) {
//            Log.log(Log.DEBUG, ex);
//            throw ex;
        } catch (IOException ex) {
            String message = DavisUtilities.getResource(AbstractHandler.class,
                    "cantCreateSmbFile", new Object[] { ex }, null);
            Log.log(Log.ERROR, message + "\n{0}", ex);
            throw new IOException(message);
        }
    }

    /**
     * Checks if a conditional request should apply.  If the client specifies
     * one or more conditional cache headers ("<code>If-Match</code>",
     * "<code>If-None-Match</code>", "<code>If-Modified-Since</code>", or
     * "<code>If-Unmodified-Since" -- "<code>If-Range</code>" is not
     * currently supported), this method will indicate whether the
     * request should be processed.  If locking is supported, this
     * method will additionally check the "<code>If</code>" header to
     * determine whether the request should apply based on the status
     * of the relevant locks. 
     *
     * @param request The servlet request whose conditional cache headers
     * will be examined.
     * @param file The resource that is being examined.
     * @return An HTTP status code indicating the result.  This will be one of:
     * <ul>
     * <li><code>200</code> (<code>HttpServletResponse.SC_OK</code>) --
     * if the request should be serviced normally</li>
     * <li><code>304</code> (<code>HttpServletResponse.SC_NOT_MODIFIED</code>)
     * -- if the resource has not been modified</li>
     * <li><code>400</code>
     * (<code>HttpServletResponse.SC_BAD_REQUEST</code>) --
     * if the client has submitted a malformed conditional header</li>
     * <li><code>412</code>
     * (<code>HttpServletResponse.SC_PRECONDITION_FAILED</code>) --
     * if no matching entity was found, or the request should not proceed
     * based on the current lock status</li>
     * </ul>
     * @throws SmbException If an error occurs while examining the resource.
     */
    protected int checkConditionalRequest(HttpServletRequest request,
            RemoteFile file) throws IOException {
        Enumeration values = request.getHeaders("If-None-Match");
        if (values.hasMoreElements()) {
            String etag = DavisUtilities.getETag(file);
            if (etag != null) {
                boolean match = false;
                do {
                    String value = (String) values.nextElement();
                    Log.log(Log.DEBUG,
                            "Checking If-None-Match: {0} against ETag {1}",
                                    new Object[] { value, etag });
                    if ("*".equals(value) || etag.equals(value)) match = true;
                } while (!match && values.hasMoreElements());
                if (match) {
                    Log.log(Log.DEBUG, "If-None-Match - match found.");
                    long timestamp = request.getDateHeader("If-Modified-Since");
                    Log.log(Log.DEBUG, "Checking If-Modified-Since: {0}",
                            new Long(timestamp));
                    if (timestamp == -1 ||
                            timestamp >= (file.lastModified() / 1000 * 1000)) {
                        Log.log(Log.INFORMATION,
                                "Resource has not been modified.");
                        return HttpServletResponse.SC_NOT_MODIFIED;
                    } else {
                        Log.log(Log.DEBUG,
                                "Resource has been modified - proceed.");
                    }
                } else {
                    Log.log(Log.DEBUG,
                        "If-None-Match - no match found - proceed.");
                }
            }
        } else {
            values = request.getHeaders("If-Match");
            if (values.hasMoreElements()) {
                String etag = DavisUtilities.getETag(file);
                if (etag == null) {
                    Log.log(Log.INFORMATION, "Precondition failed (no ETag).");
                    return HttpServletResponse.SC_PRECONDITION_FAILED;
                }
                boolean match = false;
                do {
                    String value = (String) values.nextElement();
                    Log.log(Log.DEBUG,
                            "Checking If-Match: {0} against ETag {1}",
                                    new Object[] { value, etag });
                    if ("*".equals(value) || etag.equals(value)) match = true;
                } while (!match && values.hasMoreElements());
                if (!match) {
                    Log.log(Log.INFORMATION, "Precondition failed (no match).");
                    return HttpServletResponse.SC_PRECONDITION_FAILED;
                } else {
                    Log.log(Log.DEBUG, "If-Match - match found - proceed.");
                }
            }
            long timestamp = request.getDateHeader("If-Unmodified-Since");
            Log.log(Log.DEBUG, "Checking If-Unmodified-Since: {0}",
                    new Long(timestamp));
            if (timestamp != -1) {
                if ((file.lastModified() / 1000 * 1000) > timestamp) {
                    Log.log(Log.INFORMATION, "Precondition failed (modified).");
                    return HttpServletResponse.SC_PRECONDITION_FAILED;
                } else {
                    Log.log(Log.DEBUG,
                            "Resource has not been modified - proceed.");
                }
            } else {
                timestamp = request.getDateHeader("If-Modified-Since");
                Log.log(Log.DEBUG, "Checking If-Modified-Since: {0}",
                        new Long(timestamp));
                if (timestamp != -1 &&
                        timestamp >= (file.lastModified() / 1000 * 1000)) {
                    Log.log(Log.INFORMATION, "Resource has not been modified.");
                    return HttpServletResponse.SC_NOT_MODIFIED;
                } else {
                    Log.log(Log.DEBUG, "Resource has been modified - proceed.");
                }
            }
        }
        if (getLockManager() == null) 
        	return HttpServletResponse.SC_OK;
        return checkLockCondition(request, file);
    }

    /**
     * Obtains the requesting principal.
     *
     * @param request The request being serviced.
     * @return A <code>Principal</code> object containing the authenticated
     * requesting principal.
     */
    protected DavisSession getPrincipal(HttpServletRequest request)
            throws IOException {
        return (DavisSession) AuthorizationProcessor.getInstance().getDavisSessionByID((String) request.getSession().getAttribute(Davis.SESSION_ID));
    }

    /**
     * Checks lock ownership.  This ensures that either no lock is outstanding
     * on the requested resource, or at least one of the outstanding locks on
     * the resource is held by the requesting principal
     *
     * @param request The request being serviced.
     * @param file The requested resource.
     * @return An <code>int</code> containing the return HTTP status code.
     * @throws IOException If an IO error occurs.
     */ 
    protected int checkLockOwnership(HttpServletRequest request, RemoteFile file)
            throws IOException {
        LockManager lockManager = getLockManager();
        if (lockManager == null) return HttpServletResponse.SC_OK;
        Lock[] locks = lockManager.getActiveLocks(file);
        if (locks == null || locks.length == 0) {
            Log.log(Log.DEBUG, "No outstanding locks on resource - proceed.");
            return HttpServletResponse.SC_OK;
        }
        DavisSession requestor = getPrincipal(request);
        if (requestor == null) {
            Log.log(Log.DEBUG,
                    "Outstanding locks, but unidentified requestor.");
            return SC_LOCKED;
        }
        if (Log.getThreshold() < Log.INFORMATION) {
            StringBuffer outstanding = new StringBuffer();
            for (int i = 0; i < locks.length; i++) {
                outstanding.append("    ").append(locks[i]);
                if (i + 1 < locks.length) outstanding.append("\n");
            }
            Log.log(Log.DEBUG, "Outstanding locks:\n{0}", outstanding);
        }
        String name = requestor.getSessionID();
        for (int i = locks.length - 1; i >= 0; i--) {
        	DavisSession owner = locks[i].getDavisSession();
            if (owner == null) continue;
            if (name.equals(owner.getSessionID())) {
                Log.log(Log.DEBUG, "Found lock - proceed: {0}", locks[i]);
                return HttpServletResponse.SC_OK;
            }
        }
        Log.log(Log.DEBUG, "Outstanding locks, but none held by requestor.");
        return SC_LOCKED;
    }

    private int checkLockCondition(HttpServletRequest request, RemoteFile file)
            throws IOException {
        Enumeration values = request.getHeaders("If");
        if (!values.hasMoreElements()) return HttpServletResponse.SC_OK;
        try {
            while (values.hasMoreElements()) {
                String header = (String) values.nextElement();
                Log.log(Log.DEBUG, "Checking If: {0}", header);
                int index = header.indexOf('<');
                int result;
                if (index == -1 || index > header.indexOf('(')) {
                    index = header.indexOf('(');
                    String noTagList = header.substring(index,
                            header.lastIndexOf(')') + 1);
                    result = processNoTagList(noTagList, request, file);
                } else {
                    String taggedList = header.substring(index,
                            header.lastIndexOf(')') + 1);
                    result = processTaggedList(taggedList, request, file);
                }
                if (result == HttpServletResponse.SC_OK) {
                    Log.log(Log.DEBUG, "If condition met - proceed.");
                    return HttpServletResponse.SC_OK;
                } else if (result !=
                        HttpServletResponse.SC_PRECONDITION_FAILED) {
                    Log.log(Log.DEBUG, "Unexpected status: {0}",
                            new Integer(result));
                    return result;
                }
            }
            Log.log(Log.DEBUG, "If condition not satisfied.");
            return HttpServletResponse.SC_PRECONDITION_FAILED;
        } catch (IllegalStateException ex) {
            Log.log(Log.INFORMATION,
                    "Error parsing the client's \"If\" header: {0}", ex);
            return HttpServletResponse.SC_BAD_REQUEST;
        }
    }

    private int processNoTagList(String noTagList, HttpServletRequest request,
            RemoteFile file) throws IOException {
        Log.log(Log.DEBUG, "Processing No-tag-list against \"{0}\": {1}",
                new Object[] { file, noTagList });
        boolean inQuote = false;
        boolean inEtag = false;
        boolean inList = false;
        boolean inLockToken = false;
        StringBuffer etag = null;
        StringBuffer lockToken = null;
        Set requiredEtags = null;
        Set requiredLockTokens = null;
        String resourceEtag = DavisUtilities.getETag(file);
        Log.log(Log.DEBUG, "Resource ETag is {0}", resourceEtag);
        StringTokenizer tokenizer =
                new StringTokenizer(noTagList, "()[]<> \"", true);
        String token;
        while (tokenizer.hasMoreTokens()) {
            token = tokenizer.nextToken();
            if (inQuote) etag.append(token);
            if ("\"".equals(token)) {
                if (!(inList && inEtag)) {
                    Log.log(Log.DEBUG,
                            "\" token encountered outside Etag in List.");
                    throw new IllegalStateException();
                }
                inQuote = !inQuote;
                if (inQuote) etag.append("\"");
                continue;
            }
            if (inQuote) continue;
            if (" ".equals(token)) {
            } else if ("(".equals(token)) {
                if (inList) {
                    Log.log(Log.DEBUG, "( token encountered inside List.");
                    throw new IllegalStateException();
                }
                inList = true;
                if (requiredLockTokens == null) {
                    requiredLockTokens = new HashSet();
                    requiredEtags = new HashSet();
                } else {
                    requiredLockTokens.clear();
                    requiredEtags.clear();
                }
            } else if (")".equals(token)) {
                if (!inList) {
                    Log.log(Log.DEBUG, ") token encountered outside List.");
                    throw new IllegalStateException();
                }
                inList = false;
                boolean match = true;
                Iterator iterator = requiredEtags.iterator();
                while (iterator.hasNext()) {
                    String requiredEtag = (String) iterator.next();
                    if (!("*".equals(requiredEtag) ||
                            resourceEtag.equals(requiredEtag))) {
                        match = false;
                        Log.log(Log.DEBUG, "Unmatched ETag: {0}", requiredEtag);
                        break;
                    } else {
                        Log.log(Log.DEBUG, "Matched ETag {0}", requiredEtag);
                    }
                }
                if (match) {
                    Log.log(Log.DEBUG, "All required ETags matched - proceed.");
                } else {
                    Log.log(Log.DEBUG, "Unmatched ETags detected.");
                    continue;
                }
                LockManager lockManager = getLockManager();
                iterator = requiredLockTokens.iterator();
                while (iterator.hasNext()) {
                    String requiredLockToken = (String) iterator.next();
                    if (!lockManager.isLocked(file, requiredLockToken)) {
                        match = false;
                        Log.log(Log.DEBUG, "Unmatched lock token: {0}",
                                requiredLockToken);
                        break;
                    } else {
                        Log.log(Log.DEBUG, "Matched lock token: {0}",
                                requiredLockToken);
                    }
                }
                if (match) {
                    Log.log(Log.DEBUG,
                            "All required lock tokens matched - proceed.");
                    return HttpServletResponse.SC_OK;
                } else {
                    Log.log(Log.DEBUG, "Unmatched lock tokens detected.");
                }
            } else if ("<".equals(token)) {
                if (!inList || inLockToken) {
                    Log.log(Log.DEBUG, "< token encountered outside List " +
                            "or inside LockToken.");
                    throw new IllegalStateException();
                }
                inLockToken = true;
                if (lockToken == null) {
                    lockToken = new StringBuffer();
                } else {
                    lockToken.setLength(0);
                }
            } else if (">".equals(token)) {
                if (!(inList && inLockToken)) {
                    Log.log(Log.DEBUG,
                            "> token encountered outside LockToken in List.");
                    throw new IllegalStateException();
                }
                inLockToken = false;
                requiredLockTokens.add(lockToken.toString());
            } else if ("[".equals(token)) {
                if (!inList || inEtag) {
                    Log.log(Log.DEBUG,
                            "[ token encountered outside List or inside Etag.");
                    throw new IllegalStateException();
                }
                inEtag = true;
                if (etag == null) {
                    etag = new StringBuffer();
                } else {
                    etag.setLength(0);
                }
            } else if ("]".equals(token)) {
                if (!(inList && inEtag)) {
                    Log.log(Log.DEBUG,
                            "] token encountered outside Etag in List.");
                    throw new IllegalStateException();
                }
                inEtag = false;
                requiredEtags.add(etag.toString());
            } else {
                if (inLockToken) {
                    lockToken.append(token);
                } else if (inEtag) {
                    etag.append(token);
                }
            }
        }
        Log.log(Log.DEBUG, "Unsatisfied No-tag-list: {0}", noTagList);
        return HttpServletResponse.SC_PRECONDITION_FAILED;
    }

    private int processTaggedList(String taggedList, HttpServletRequest request,
    		RemoteFile file) throws IOException {
        Log.log(Log.DEBUG, "Processing Tagged-list against \"{0}\": {1}",
                new Object[] { file, taggedList });
        boolean inQuote = false;
        boolean inResource = false;
        boolean inList = false;
        StringBuffer resource = null;
        StringBuffer list = null;
        StringTokenizer tokenizer =
                new StringTokenizer(taggedList, "<>() \"", true);
        String token;
        while (tokenizer.hasMoreTokens()) {
            token = tokenizer.nextToken();
            if (inList) list.append(token);
            if ("\"".equals(token)) {
                inQuote = !inQuote;
                continue;
            }
            if (inQuote) continue;
            if (" ".equals(token)) {
                if (!inList) list.append(token);
            } else if ("<".equals(token)) {
                if (inList) continue;
                if (inResource) {
                    Log.log(Log.DEBUG, "< token encountered inside Resource.");
                    throw new IllegalStateException();
                }
                if (resource == null) {
                    resource = new StringBuffer();
                    list = new StringBuffer();
                } else {
                    int result = processNoTagList(list.toString().trim(),
                            request, getRelativeFile(request, file,
                                    resource.toString().trim()));
                    list.setLength(0);
                    resource.setLength(0);
                    if (result != HttpServletResponse.SC_OK) return result;
                }
                inResource = true;
            } else if (">".equals(token)) {
                if (inList) continue;
                if (!inResource) {
                    Log.log(Log.DEBUG, "> token encountered outside Resource.");
                    throw new IllegalStateException();
                }
                inResource = false;
            } else if ("(".equals(token)) {
                if (inList) {
                    Log.log(Log.DEBUG, "( token encountered inside List.");
                    throw new IllegalStateException();
                }
                inList = true;
                list.append(token);
            } else if (")".equals(token)) {
                if (!inList) {
                    Log.log(Log.DEBUG, ") token encountered outside List.");
                    throw new IllegalStateException();
                }
                inList = false;
            } else if (inResource) {
                resource.append(token);
            }
        }
        if (inList || inResource || inQuote) throw new IllegalStateException();
        return processNoTagList(list.toString().trim(), request,
                getRelativeFile(request, file, resource.toString().trim()));
    }

    private RemoteFile getRelativeFile(HttpServletRequest request, RemoteFile base,
            String httpUrl) throws IOException {
    	RemoteFile file = null;
        IOException exception = null;
        boolean exists = false;
        String charset = getRequestURICharset();
        try {
        	if (base.getFileSystem() instanceof SRBFileSystem){
                file = new SRBFile((SRBFile)base, getRemoteURL(request, httpUrl, charset));
        	}else if (base.getFileSystem() instanceof IRODSFileSystem){
                file = new IRODSFile((IRODSFile)base, getRemoteURL(request, httpUrl, charset));
        	}
            exists = file.exists();
        } catch (IOException ex) {
            exception = ex;
        }
        if (exists) return file;
        if (charset.equals("UTF-8")) {
            if (exception != null) {
                Log.log(Log.DEBUG, exception);
                throw exception;
            }
            return file;
        }
        RemoteFile utf8 = null;
        IOException utf8Exception = null;
        try {
        	if (base.getFileSystem() instanceof SRBFileSystem){
                file = new SRBFile((SRBFile)base, getRemoteURL(request, httpUrl, "UTF-8"));
        	}else if (base.getFileSystem() instanceof IRODSFileSystem){
                file = new IRODSFile((IRODSFile)base, getRemoteURL(request, httpUrl, "UTF-8"));
        	}
            exists = utf8.exists();
        } catch (IOException ex) {
            utf8Exception = ex;
        }
        if (exists) return utf8;
        if (file != null) {
            if (exception != null) {
                Log.log(Log.DEBUG, exception);
                throw exception;
            }
            return file;
        }
        if (utf8 != null) {
            if (utf8Exception != null) {
                Log.log(Log.DEBUG, exception);
                throw utf8Exception;
            }
            return utf8;
        }
        if (exception != null) {
            Log.log(Log.DEBUG, exception);
            throw exception;
        }
        Log.log(Log.WARNING, "Returning null SmbFile (shouldn't happen).");
        return null;
    }

    private boolean needsSeparator(RemoteFile file) throws IOException {
        if (file.getName().endsWith("/")) return true;
//        int type = file.getType();
//        if (type == SmbFile.TYPE_WORKGROUP || type == SmbFile.TYPE_SERVER ||
//                type == SmbFile.TYPE_SHARE) {
//            return true;
//        }
        return (file.isDirectory());
    }

    private String unescape(String escaped, String charset) throws IOException {
        StringTokenizer tokenizer = new StringTokenizer(escaped, "%", true);
        StringBuffer buffer = new StringBuffer();
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (!"%".equals(token)) {
                buffer.append(token);
                continue;
            }
            while (tokenizer.hasMoreTokens() && token.equals("%")) {
                token = tokenizer.nextToken();
                encoded.write(Integer.parseInt(token.substring(0, 2), 16));
                token = token.substring(2);
                if ("".equals(token) && tokenizer.hasMoreTokens()) {
                    token = tokenizer.nextToken();
                }
            }
            buffer.append(encoded.toString(charset));
            encoded.reset();
            if (!token.equals("%")) buffer.append(token);
        }
        return buffer.toString();
    }
    
    public String getServerType(){
    	return /*config.getInitParameter("server-type")*/ DavisConfig.getInstance().getServerType();
    }

    protected JSONArray getJSONContent(HttpServletRequest request) throws IOException {
    	
        if (request.getContentLength() <= 0) 
        	return null;
        
        InputStream input = request.getInputStream();
        String s = "";
        byte[] buf = new byte[request.getContentLength()];
        while (true) {
        	int count=input.read(buf);
        	if (count < 0)
        		break;
        	s += new String(buf, 0, count);
//     break;
        }
        Log.log(Log.DEBUG, "read:"+s.length());
        Log.log(Log.DEBUG, "received json data: "+s);
		return (JSONArray)JSONValue.parse(s);
    }

//    protected boolean getFileList(HttpServletRequest request, DavisSession davisSession, ArrayList<RemoteFile> fileList) throws IOException, ServletException {
//    	
//    	return getFileList(request, davisSession, fileList, getJSONContent(request));
//    }
    
    protected boolean getFileList(HttpServletRequest request, DavisSession davisSession, ArrayList<RemoteFile> fileList, JSONArray jsonArray) throws IOException, ServletException {
    
    	boolean batch = false;
    	RemoteFile uriFile = getRemoteFile(request, davisSession);
        if (request.getContentLength() <= 0) {
        	fileList.add(uriFile);
        } else {
        	batch = true;
//	        InputStream input = request.getInputStream();
//	        byte[] buf = new byte[request.getContentLength()];
//	        int count=input.read(buf);
//	        Log.log(Log.DEBUG, "read:"+count);
//	        Log.log(Log.DEBUG, "received file list: " + new String(buf));
//
//			JSONArray jsonArray=(JSONArray)JSONValue.parse(new String(buf));
			JSONObject jsonObject = null;
			if (jsonArray != null) {	
				jsonObject = (JSONObject)jsonArray.get(0);
				JSONArray fileNamesArray = (JSONArray)jsonObject.get("files");
				if (fileNamesArray != null)
					for (int i = 0; i < fileNamesArray.size(); i++) {
						String name = (String)fileNamesArray.get(i);
						if (name.trim().length() == 0)
							continue;	// If for any reason name is "", we MUST skip it because that's equivalent to home!   	 
						fileList.add(getRemoteFile(uriFile.getAbsolutePath()+uriFile.getPathSeparator()+name, davisSession));
					}			
			} else
				throw new ServletException("Internal error reading file list: error parsing JSON");
		}
    	if (batch && fileList.size() == 0) {
    		String cacheID = request.getParameter("uihandle");
//    		DefaultGetHandler getHandler = (DefaultGetHandler)davis.getHandler("GET");
    		CachedFile[] files = davisSession.getCacheByID(cacheID);
    		ArrayList<Integer> indicesList = new ArrayList<Integer>();
    	    getIndicesList(indicesList, jsonArray);
    		for (int i = 0; i < indicesList.size(); i++) {
    			RemoteFile file = files[indicesList.get(i).intValue()];
    			fileList.add(getRemoteFile(file.getAbsolutePath(), davisSession));
    		}
    	}
    	Log.log(Log.DEBUG, "file list is: "+fileList);
        return batch;
    }
    
    protected void getIndicesList(ArrayList<Integer> indicesList, JSONArray jsonArray) throws ServletException {
        
		JSONObject jsonObject = null;
		if (jsonArray != null) {	
			jsonObject = (JSONObject)jsonArray.get(0);
			JSONArray indicesArray = (JSONArray)jsonObject.get("indices");
			if (indicesArray != null)
				for (int i = 0; i < indicesArray.size(); i++) {
					Integer index = null;
					try {
						index = Integer.valueOf((String)indicesArray.get(i));
					} catch (NumberFormatException e) {
						Log.log(Log.ERROR, "Internal error parsing index string: "+(String)indicesArray.get(i));
						continue;
					}
					indicesList.add(index);
				}			
		} else
			throw new ServletException("Internal error reading indices list: error parsing JSON");
   }
    
    public String escapeJSONArg(String s) {
    	
    	return "\""+escapeJSON(s)+"\"";
    }
    
    protected String escapeJSON(String s) {
    	
    	return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    
    public String wrapJSONInHTML(String s) {
    	
    	return "<html><body><textarea>{"+s+"}</textarea></body></html>";
    }

    public abstract void service(HttpServletRequest request,
            HttpServletResponse response, DavisSession davisSession)
                    throws IOException, ServletException;

}
