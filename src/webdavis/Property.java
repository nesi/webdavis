package webdavis;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import edu.sdsc.grid.io.RemoteFile;

/**
 * Represents a WebDAV property.  This provides an interface for retrieving
 * and updating properties of resources.
 *
 * @author Shunde Zhang
 * @author Eric Glass
 * @author Jani Heikkinen <jani.heikkinen @ csc.fi> - CSC, National Research Data project (TTA), Finland
 */
public interface Property {

    /**
     * The XMLNS namespace URI,
     * "<code>http://www.w3.org/2000/xmlns/</code>".
     */ 
    public static final String XMLNS_NAMESPACE =
            "http://www.w3.org/2000/xmlns/";

    /**
     * The WebDAV namespace URI, "<code>DAV:</code>".
     */
    public static final String DAV_NAMESPACE = "DAV:";
    public static final String DAV_PREFIX = "D";

    /**
     * The Web Folders attribute namespace URI,
     * "<code>urn:uuid:c2f41010-65b3-11d1-a29f-00aa00c14882/</code>".
     */
    public static final String WEB_FOLDERS_NAMESPACE =
            "urn:uuid:c2f41010-65b3-11d1-a29f-00aa00c14882/";

    /**
     * Initilizes the property and identifies it with the provided name.
     *
     * @param name The name by which the property will be identified.
     * @param config The servlet configuration object containing
     * initialization information for the property.
     * @throws ServletException If the property could not be initialized.
     */
    public void init(String name, ServletConfig config)
            throws ServletException;

    /**
     * Disposes of the property.
     */
    public void destroy();

    /**
     * Returns the property name.
     * 
     * @return A <code>String</code> containing the name of the property.
     * This will be the local name of the element in the XML document.
     */
    public String getName();

    /**
     * Returns the namespace URI of the property.
     *
     * @return A <code>String</code> containing the namespace URI in
     * which the property resides.
     */
    public String getNamespace();

    /**
     * Returns the prefix used when creating elements for this property.
     * If the namespace URI for this property already has a prefix
     * associated with it, the existing prefix will be used.
     *
     * @return A <code>String</code> containing the prefix applied
     * to elements created by this property.
     */
    public String getPrefix();

    /**
     * Creates a property element for the given resource (if applicable),
     * with the specified document as the owner.  The element will not be
     * added to the document.  The document may be modified, however,
     * to apply namespace attributes or otherwise prepare the document
     * to receive the element in the future.
     *
     * @param document The document that is to own the property element.
     * @param file The resource being queried.
     * @return An <code>Element</code> for the property.  If this property
     * does not apply to the specified resource, <code>null</code> will
     * be returned.
     * @throws IOException If an IO error occurs while creating the element.
     */
    public Element createElement(Document document, RemoteFile file)
            throws IOException;

    /**
     * Updates this property on the given resource using the information
     * int the specified element.
     *
     * @param file The resource that is to be updated.
     * @param element The element containing the update information.
     * @return An <code>int</code> containing the HTTP response code.
     * For a successful update, this will be
     * <code>HttpServletResponse.SC_OK</code>.
     * @throws IOException If an IO error occurs while updating the
     * property value.
     */
    public int update(RemoteFile file, Element element)
            throws IOException;

    /**
     * Indicates whether an <code>Object</code> is equivalent to this
     * <code>Property</code> object.  Two properties are equal if they
     * reside in the same namespace and have the same name (regardless
     * of prefix equivalence).
     *
     * @param obj The object to compare to this property
     * @return A <code>boolean</code> indicating whether the given object
     * is equivalent to this property.
     */
    public boolean equals(Object obj);

    /**
     * Returns the hash code for this property.  The hash code for a
     * property object is defined as the hash code of the property's name
     * XORed with the hash code of the property's namespace.
     * 
     * @return An <code>int</code> containing the hash code for the property.
     */
    public int hashCode();

    /**
     * Populates the provided <code>Element</code> with the current value
     * of the property for the given resource.
     *
     * @param file The resource whose property value is to be retrieved.
     * @param element The element which receives the value.
     * @return An <code>int</code> containing the HTTP response code.
     * For a successful retrieval, this will be
     * <code>HttpServletResponse.SC_OK</code>.
     * @throws IOException If an IO error occurs while retrieving the
     * property value.
     */
    public int retrieve(RemoteFile file, Element element)
            throws IOException;

}
