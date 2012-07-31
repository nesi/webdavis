package webdavis;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;


import org.w3c.dom.Document;
import org.w3c.dom.Element;

import edu.sdsc.grid.io.RemoteFile;

/**
 * This interface provides operations for constructing and retrieving a
 * PROPFIND result XML document.
 *
 * @author Shunde Zhang
 * @author Eric Glass
 */
public interface PropertiesBuilder {

    public void init(ServletConfig config) throws ServletException;

    public void destroy();

    /**
     * Adds a response containing the property names supported by the
     * given resource.
     *
     * @param document The document to which modifications are made.
     * @param file The <code>SmbFile</code> resource whose property names are
     * to be retrieved.
     * @param href The HTTP URL by which the resource was accessed.
     * @throws IOException If an IO error occurs while adding the
     * property names.
     */
    public void addPropNames(Document document, RemoteFile file, String href)
            throws IOException;

    /**
     * Adds a response containing the names and values of all properties
     * supported by the given resource.
     *
     * @param file The <code>SmbFile</code> resource whose property names
     * and values are to be retrieved.
     * @param href The HTTP URL by which the resource was accessed.
     * @throws IOException If an IO error occurs while adding the
     * properties.
     */
    public void addAllProps(Document document, RemoteFile file, String href)
            throws IOException;

    /**
     * Adds a response containing the names and values of the properties
     * specified by the given <code>Element</code> array.
     *
     * @param file The <code>SmbFile</code> resource whose properties
     * are to be retrieved.
     * @param href The HTTP URL by which the resource was accessed.
     * @param props An array of <code>Element</code>s, each of which
     * specifies the name of a property to be retrieved.
     * @throws IOException If an IO error occurs while adding the
     * properties.
     */
    public void addProps(Document document, RemoteFile file, String href,
            Element[] props) throws IOException;

    /**
     * Creates an XML document in which a result can be built.
     *
     * @return A <code>Document</code> object to hold the resulting
     * XML.
     */
    public Document createDocument();

}
