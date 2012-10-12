package webdavis.properties;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.irods.jargon.core.pub.io.IRODSFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;


import webdavis.AbstractProperty;

/**
 * Provides access to the <code>getcontentlength</code> property.
 * This implementation returns the size of the resource.
 *
 * @author Eric Glass
 */
public class GetContentLengthProperty extends AbstractProperty {

    public Element createElement(Document document, IRODSFile file)
            throws IOException {
        return file.isDirectory() ? null : super.createElement(document, file);
    }

    public int retrieve(IRODSFile file, Element element)
            throws IOException {
        if (file.isDirectory()) return HttpServletResponse.SC_NOT_FOUND;
        element.setAttributeNS(WEB_FOLDERS_NAMESPACE, "w:dt", "int");
        element.appendChild(element.getOwnerDocument().createTextNode(
                String.valueOf(file.length())));
        return HttpServletResponse.SC_OK;
    }

}
