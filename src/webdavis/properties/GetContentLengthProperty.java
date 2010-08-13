package webdavis.properties;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import edu.sdsc.grid.io.RemoteFile;

import webdavis.AbstractProperty;

/**
 * Provides access to the <code>getcontentlength</code> property.
 * This implementation returns the size of the resource.
 *
 * @author Eric Glass
 */
public class GetContentLengthProperty extends AbstractProperty {

    public Element createElement(Document document, RemoteFile file)
            throws IOException {
        return file.isDirectory() ? null : super.createElement(document, file);
    }

    public int retrieve(RemoteFile file, Element element)
            throws IOException {
        if (file.isDirectory()) return HttpServletResponse.SC_NOT_FOUND;
        element.setAttributeNS(WEB_FOLDERS_NAMESPACE, "w:dt", "int");
        element.appendChild(element.getOwnerDocument().createTextNode(
                String.valueOf(file.length())));
        return HttpServletResponse.SC_OK;
    }

}
