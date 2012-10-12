package webdavis;

import java.io.IOException;

import org.irods.jargon.core.pub.io.IRODSFile;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This class directs a <code>PropertiesBuilder</code> in the creation
 * and retrieval of a PROPFIND result XML document.
 *
 * @author Shunde Zhang
 * @author Eric Glass
 */
public class PropertiesDirector {
	
    private static final int INFINITY = 3;

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
    public Document getPropertyNames(IRODSFile file, String href, int depth)
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
    public Document getAllProperties(IRODSFile file, String href, int depth)
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
    public Document getProperties(IRODSFile file, String href, Element[] props,
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


    private void addPropertyNames(Document document, IRODSFile file, String href,
            int depth) throws IOException {
        getPropertiesBuilder().addPropNames(document, file, href);
        if (depth > 0 && !file.isFile()) {
        	IRODSFile[] children = getChildren(file); //null;
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
                        href + FSUtilities.escape(children[i].getName()), depth);
            }
        }
    }

    private void addAllProperties(Document document, IRODSFile file, String href,
            int depth) throws IOException {
    	getPropertiesBuilder().addAllProps(document, file, href);
    	if (depth > 0 && !file.isFile()) {
    		IRODSFile[] children = getChildren(file);  // null;
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
                        href + FSUtilities.escape(children[i].getName()), depth);
            }
        }
    }
    
    private void addProperties(Document document, IRODSFile file, String href,
            Element[] props, int depth) throws IOException {
        getPropertiesBuilder().addProps(document, file, href, props);
        if (depth > 0 && !file.isFile()) {
        	IRODSFile[] children = getChildren(file);  //null;
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
                        href + FSUtilities.escape(children[i].getName()), props, depth);
            }
        }
    }

    private IRODSFile[] getChildren(IRODSFile file){
    	return (IRODSFile[])file.listFiles();
    }
}
