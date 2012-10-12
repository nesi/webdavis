package webdavis.properties;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.irods.jargon.core.pub.io.IRODSFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import webdavis.AbstractProperty;
import webdavis.DavisUtilities;

/**
 * Provides access to the <code>creationdate</code> property.
 * This implementation returns the resource's last modified date.
 *
 * @author Eric Glass
 */
public class GetLastModifiedProperty extends AbstractProperty {

    public Element createElement(Document document, IRODSFile file)
            throws IOException {
        return (file.lastModified() == 0) ? null :
                super.createElement(document, file);
    }

    public int retrieve(IRODSFile file, Element element)
            throws IOException {
        long modified = file.lastModified();
        if (modified == 0) return HttpServletResponse.SC_NOT_FOUND;
        element.setAttributeNS(WEB_FOLDERS_NAMESPACE, "w:dt",
                "dateTime.rfc1123");
        element.appendChild(element.getOwnerDocument().createTextNode(
                DavisUtilities.formatGetLastModified(modified)));
        return HttpServletResponse.SC_OK;
    }

}
