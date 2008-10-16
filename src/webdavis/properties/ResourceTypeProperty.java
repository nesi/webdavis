package webdavis.properties;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.w3c.dom.Element;

import edu.sdsc.grid.io.RemoteFile;

import webdavis.AbstractProperty;

/**
 * Provides access to the <code>resourcetype</code> property.
 * This implementation returns an indicator of whether
 * the resource is a collection.
 *
 * @author Eric Glass
 */
public class ResourceTypeProperty extends AbstractProperty {

    public int retrieve(RemoteFile file, Element element)
            throws IOException {
        if (!file.isFile()) {
            String namespace = element.getNamespaceURI();
            if (namespace != null) {
                String prefix = element.getPrefix();
                element.appendChild(element.getOwnerDocument().createElementNS(
                        namespace, prefix == null ? "collection" :
                                prefix + ":collection"));
            } else {
                element.appendChild(element.getOwnerDocument().createElement(
                        "collection"));
            }
        }
        return HttpServletResponse.SC_OK;
    }

}
