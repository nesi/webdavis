/* Davenport WebDAV SMB Gateway
 * Copyright (C) 2003  Eric Glass
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package webdavis;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import javax.servlet.http.HttpServletResponse;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import edu.sdsc.grid.io.RemoteFile;

/**
 * This class provides a basic implementation of much of the
 * <code>Property</code> interface.  Subclasses need only provide an
 * implementation of the <code>retrieve</code> method for read-only
 * properties which apply to all resources.
 *
 * @author Eric Glass
 */
public abstract class AbstractProperty implements Property {

    private ServletConfig config;

    private String namespace;

    private String prefix;

    private String name;

    public void init(String name, ServletConfig config)
            throws ServletException {
        this.config = config;
        this.name = name;
        this.namespace = config.getInitParameter("property." + name +
                ".namespace");
        if (namespace == null) namespace = DAV_NAMESPACE;
        this.prefix = config.getInitParameter("property." + name + ".prefix");
    }

    public void destroy() {
        this.name = null;
        this.namespace = null;
        this.prefix = null;
        this.config = null;
    }

    public String getName() {
        return name;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * Creates a property element for the given resource (if applicable),
     * with the specified document as the owner.  The element will not be
     * added to the document. This default implementation creates an empty
     * element, and adds a namespace assignment to the given document
     * if necessary.
     *
     * @param document The document that is to own the property element.
     * @param file The resource being queried.
     * @return An <code>Element</code> for the property.  If this property
     * does not apply to the specified resource, <code>null</code> will
     * be returned.  The default implementation creates an empty element
     * for all resources.
     * @throws IOException If an IO error occurs while creating the element.
     */
    public Element createElement(Document document, RemoteFile file)
            throws IOException {
        String namespace = getNamespace();
        if (namespace != null) {
            String prefix;
            try {
                prefix = getPrefix(document, namespace);
            } catch (IllegalArgumentException ex) {
                prefix = getPrefix();
                addNamespace(document, namespace, prefix);
            }
            return document.createElementNS(namespace, (prefix != null) ?
                    prefix + ":" + getName() : getName());
        } else {
            return document.createElement(getName());
        }
    }

    /**
     * Updates this property on the given resource using the information
     * int the specified element.  This default implementation does nothing,
     * and returns <code>HttpServletResponse.SC_CONFLICT</code>.
     *
     * @param file The resource that is to be updated.
     * @param element The element containing the update information.
     * @return An <code>int</code> containing the HTTP response code.
     * @throws IOException If an IO error occurs while updating the
     * property value.
     */
    public int update(RemoteFile file, Element element)
            throws IOException {
        return HttpServletResponse.SC_CONFLICT;
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof Property)) return false;
        Property other = (Property) obj;
        String name = getName();
        if ((name != null) ? !name.equals(other.getName()) :
                other.getName() != null) {
            return false;
        }
        String namespace = getName();
        return ((namespace != null) ? namespace.equals(other.getNamespace()) :
                other.getNamespace() == null);
    }

    public int hashCode() {
        String name = getName();
        int hashCode = (name != null) ? name.hashCode() : 0;
        String namespace = getNamespace();
        return hashCode ^ ((namespace != null) ? namespace.hashCode() : 0);
    }

    public String toString() {
        StringBuffer buffer = new StringBuffer();
        String namespace = getNamespace();
        if (namespace != null) buffer.append('{').append(namespace).append('}');
        String prefix = getPrefix();
        if (prefix != null) buffer.append(prefix).append(':');
        buffer.append(getName());
        return buffer.toString();
    }

    /**
     * Returns the servlet configuration.
     *
     * @return A <code>ServletConfig</code> containing the servlet's
     * configuration information.
     */
    protected ServletConfig getServletConfig() {
        return config;
    }

    /**
     * Returns the prefix for the specified namespace URI in the given
     * document, or <code>null</code> for the default namespace.
     * If the namespace has not been mapped to a prefix in the
     * document, an <code>IllegalArgumentException</code> is thrown.
     *
     * @param document The document whose assigned prefix for the given
     * namespace URI is to be retrieved.
     * @param namespace The namespace URI whose prefix in the document
     * is to be retrieved.
     * @return A <code>String</code> containing the prefix assigned
     * to the given namespace URI in the specified document.  If the
     * given namespace URI is the default namespace for the document,
     * this method returns <code>null</code>.
     * @throws IllegalArgumentException If the specified namespace URI
     * has not been assigned a prefix in the given document.
     */
    protected String getPrefix(Document document, String namespace) {
        Element root = document.getDocumentElement();
        if (root == null) {
            throw new IllegalArgumentException(DavisUtilities.getResource(
                    AbstractProperty.class, "noDocument", null, null));
        }
        NamedNodeMap attributes = root.getAttributes();
        for (int i = attributes.getLength() - 1; i >= 0; i--) {
            Node node = attributes.item(i);
            if (!namespace.equals(node.getNodeValue())) continue;
            String prefix = node.getPrefix();
            if (prefix == null && "xmlns".equals(node.getLocalName())) {
                return null;
            }
            if ("xmlns".equals(prefix)) return node.getLocalName();
        }
        throw new IllegalArgumentException(DavisUtilities.getResource(
                AbstractProperty.class, "noPrefix",
                        new Object[] { namespace }, null));
    }

    /**
     * Returns the namespace URI with the given assigned prefix in the
     * specified document.  Returns <code>null</code> if the prefix
     * has not been assigned to a namespace.
     * 
     * @param document The document from which the namespace is to be retrieved.
     * @param prefix The prefix for which a namespace is to be retrieved.
     * A value of <code>null</code> for this parameter will retrieve
     * the default namespace of the document.
     * @return A <code>String</code> containing the namespace URI to which
     * the given prefix has been assigned in the specified document.
     * If no such namespace exists, this method returns <code>null</code>.
     */
    protected String getNamespace(Document document, String prefix) {
        Element root = document.getDocumentElement();
        if (root == null) return null;
        String namespace = (prefix == null) ? root.getAttribute("xmlns") :
                root.getAttributeNS(XMLNS_NAMESPACE, prefix);
        return ("".equals(namespace)) ? null : namespace;
    }

    /**
     * Assigns the given prefix to the specified namespace URI in the
     * provided document.
     *
     * @param document The document in which the namespace assignment is
     * to be made.
     * @param namespace The namespace URI for which an assignment is
     * being established.
     * @param prefix The prefix to assign to the namespace URI.
     * @throws IOException If the prefix has already been assigned
     * to another namespace URI in the document.
     */
    protected void addNamespace(Document document, String namespace,
            String prefix) throws IOException {
        String currentNamespace = getNamespace(document, prefix);
        if (currentNamespace != null) {
            if (currentNamespace.equals(namespace)) return;
            throw new IOException(DavisUtilities.getResource(
                    AbstractProperty.class, "prefixAlreadyAssigned",
                            new Object[] { prefix, currentNamespace }, null));
        }
        document.getDocumentElement().setAttributeNS(XMLNS_NAMESPACE,
                prefix != null ? "xmlns:" + prefix : "xmlns",
                        namespace);
    }

    public abstract int retrieve(RemoteFile file, Element element)
            throws IOException;

}
