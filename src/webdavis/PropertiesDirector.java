package webdavis;

import java.io.IOException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import edu.sdsc.grid.io.GeneralFileSystem;
import edu.sdsc.grid.io.RemoteFile;
import edu.sdsc.grid.io.irods.IRODSFile;
import edu.sdsc.grid.io.irods.IRODSFileSystem;
import edu.sdsc.grid.io.srb.SRBFile;
import edu.sdsc.grid.io.srb.SRBFileSystem;

/**
 * This class directs a <code>PropertiesBuilder</code> in the creation
 * and retrieval of a PROPFIND result XML document.
 *
 * @author Eric Glass
 */
public class PropertiesDirector {

    private static final int INFINITY = 3;

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
    }

    private final PropertiesBuilder builder;

    /**
     * Creates a <code>PropertiesDirector</code> which uses the specified
     * builder to create the PROPFIND XML document.
     * 
     * @param builder The <code>PropertiesBuilder</code> used to
     * create the PROPFIND result XML document.
     * @param filter An <code>SmbFileFilter</code> to apply when obtaining
     * child resources.
     */
    public PropertiesDirector(PropertiesBuilder builder) {
        this.builder = builder;
    }

    /**
     * Returns the PROPFIND result XML document for the specified resource
     * containing the names of all supported properties.
     *
     * @param file The resource whose property names are to be retrieved.
     * @param href The HTTP URL by which the resource was accessed.
     * @param depth The depth to which the request is applied.  One of
     * <code>SmbDAVUtilities.RESOURCE_ONLY_DEPTH</code>
     * (applied to the resource only),
     * <code>SmbDAVUtilities.CHILDREN_DEPTH</code>
     * (applied to the resource and its immediate children), or
     * <code>SmbDAVUtilities.INFINITE_DEPTH</code>
     * (the resource and all of its progeny).
     * @return An XML <code>Document</code> containing the PROPFIND result.
     * @throws IOException If an IO error occurs during the construction
     * of the document.
     */
    public Document getPropertyNames(RemoteFile file, String href, int depth)
            throws IOException {
        if (depth == DavisUtilities.INFINITE_DEPTH) depth = INFINITY;
        Document document = getPropertiesBuilder().createDocument();
        addPropertyNames(document, file, href, depth);
        return document;
    }

    /**
     * Returns the PROPFIND result XML document for the specified resource
     * containing the names and values of all supported properties.
     *
     * @param file The resource whose properties are to be retrieved.
     * @param href The HTTP URL by which the resource was accessed.
     * @param depth The depth to which the request is applied.  One of
     * <code>SmbDAVUtilities.RESOURCE_ONLY_DEPTH</code>
     * (applied to the resource only),
     * <code>SmbDAVUtilities.CHILDREN_DEPTH</code>
     * (applied to the resource and its immediate children), or
     * <code>SmbDAVUtilities.INFINITE_DEPTH</code>
     * (the resource and all of its progeny).
     * @return An XML <code>Document</code> containing the PROPFIND result.
     * @throws IOException If an IO error occurs during the construction
     * of the document.
     */
    public Document getAllProperties(RemoteFile file, String href, int depth)
            throws IOException {
        if (depth == DavisUtilities.INFINITE_DEPTH) depth = INFINITY;
        Document document = getPropertiesBuilder().createDocument();
        addAllProperties(document, file, href, depth);
        return document;
    }

    /**
     * Returns the PROPFIND result XML document for the specified resource
     * containing the values of the specifed properties.
     *
     * @param file The resource whose properties are to be retrieved.
     * @param href The HTTP URL by which the resource was accessed.
     * @param props The names of the properties which are to be retrieved.
     * @param depth The depth to which the request is applied.  One of
     * <code>SmbDAVUtilities.RESOURCE_ONLY_DEPTH</code>
     * (applied to the resource only),
     * <code>SmbDAVUtilities.CHILDREN_DEPTH</code>
     * (applied to the resource and its immediate children), or
     * <code>SmbDAVUtilities.INFINITE_DEPTH</code>
     * (the resource and all of its progeny).
     * @return An XML <code>Document</code> containing the PROPFIND result.
     * @throws IOException If an IO error occurs during the construction
     * of the document.
     */
    public Document getProperties(RemoteFile file, String href, Element[] props,
            int depth) throws IOException {
        if (depth == DavisUtilities.INFINITE_DEPTH) depth = INFINITY;
        Document document= getPropertiesBuilder().createDocument();
        addProperties(document, file, href, props, depth);
        return document;
    }

    /**
     * Returns the builder used to construct the XML document.
     * 
     * @return The <code>PropertiesBuilder</code> object that will be used
     * to create the PROPFIND result XML document.
     */
    protected PropertiesBuilder getPropertiesBuilder() {
        return builder;
    }


    private void addPropertyNames(Document document, RemoteFile file, String href,
            int depth) throws IOException {
        getPropertiesBuilder().addPropNames(document, file, href);
        if (depth > 0 && !file.isFile()) {
            RemoteFile[] children = getChildren(file); //null;
//            SmbFileFilter filter = getFilter();
//            try {
//                children = (filter != null) ? file.listFiles(filter) :
//                        file.listFiles();
//            } catch (IOException ex) { }
            if (children == null) return;
            int count = children.length;
            if (count == 0) return;
            if (!href.endsWith("/")) href += "/";
            --depth;
//            if (file.getType() == SmbFile.TYPE_WORKGROUP &&
//                    !"smb://".equals(file.toString())) {
//                int index = href.lastIndexOf(file.getName());
//                if (index != -1) href = href.substring(0, index);
//            }
            for (int i = 0; i < count; i++) {
                addPropertyNames(document, children[i],
                        href + escape(children[i].getName()), depth);
            }
        }
    }

    private void addAllProperties(Document document, RemoteFile file, String href,
            int depth) throws IOException {
        getPropertiesBuilder().addAllProps(document, file, href);
        if (depth > 0 && !file.isFile()) {
        	RemoteFile[] children = getChildren(file);  // null;
//            SmbFileFilter filter = getFilter();
//            try {
//                children = (filter != null) ? file.listFiles(filter) :
//                        file.listFiles();
//            } catch (SmbException ex) { }
            if (children == null) return;
            int count = children.length;
            if (count == 0) return;
            if (!href.endsWith("/")) href += "/";
            --depth;
//            if (file.getType() == SmbFile.TYPE_WORKGROUP &&
//                    !"smb://".equals(file.toString())) {
//                int index = href.lastIndexOf(file.getName());
//                if (index != -1) href = href.substring(0, index);
//            }
            for (int i = 0; i < count; i++) {
                addAllProperties(document, children[i],
                        href + escape(children[i].getName()), depth);
            }
        }
    }

    private void addProperties(Document document, RemoteFile file, String href,
            Element[] props, int depth) throws IOException {
        getPropertiesBuilder().addProps(document, file, href, props);
        if (depth > 0 && !file.isFile()) {
        	RemoteFile[] children = getChildren(file);  //null;
//            SmbFileFilter filter = getFilter();
//            try {
//                children = (filter != null) ? file.listFiles(filter) :
//                        file.listFiles();
//            } catch (SmbException ex) { }
            if (children == null) return;
            int count = children.length;
            if (count == 0) return;
            if (!href.endsWith("/")) href += "/";
            --depth;
//            if (file.getType() == SmbFile.TYPE_WORKGROUP &&
//                    !"smb://".equals(file.toString())) {
//                int index = href.lastIndexOf(file.getName());
//                if (index != -1) href = href.substring(0, index);
//            }
            for (int i = 0; i < count; i++) {
                addProperties(document, children[i],
                        href + escape(children[i].getName()), props, depth);
            }
        }
    }

    private String escape(String name) throws IOException {
        boolean dir = name.endsWith("/");
        if (dir) name = name.substring(0, name.length() - 1);
        StringBuffer buffer = new StringBuffer();
        char[] chars = name.toCharArray();
        int count = chars.length;
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

    private RemoteFile[] getChildren(RemoteFile file){
    	GeneralFileSystem gfs=file.getFileSystem();
    	if (gfs instanceof SRBFileSystem){
    		gfs=(SRBFileSystem)gfs;
    		// may change to do a query
    		String[] children=((SRBFile)file).list();
    		RemoteFile[] files=new RemoteFile[children.length];
    		for (int i=0;i<children.length;i++){
        		Log.log(Log.DEBUG, "children[i] {0}",children[i]);
    			files[i]=new SRBFile((SRBFile)file,children[i]);
    		}
    		return files;
    	}else if (gfs instanceof IRODSFileSystem){
    		gfs=(IRODSFileSystem)gfs;
    		// may change to do a query
    		String[] children=file.list();
    		RemoteFile[] files=new RemoteFile[children.length];
    		for (int i=0;i<children.length;i++){
    			files[i]=new IRODSFile((IRODSFile)file,children[i]);
    		}
    		return files;
    	}
    	return null;
    }
}
