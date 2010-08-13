package webdavis.properties;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.w3c.dom.Element;

import edu.sdsc.grid.io.RemoteFile;

import webdavis.AbstractProperty;

/**
 * Provides access to the <code>iscollection</code> property.
 * This implementation returns an indication of whether
 * the resource is a collection.
 *
 * @author Eric Glass
 */
public class IsCollectionProperty extends AbstractProperty {

    public int retrieve(RemoteFile file, Element element)
            throws IOException {
        element.setAttributeNS(WEB_FOLDERS_NAMESPACE, "w:dt", "boolean");
        element.appendChild(element.getOwnerDocument().createTextNode(
                file.isFile() ? "0" : "1"));
        return HttpServletResponse.SC_OK;
    }

}
