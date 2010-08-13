package webdavis.properties;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import edu.sdsc.grid.io.RemoteFile;

import webdavis.AbstractProperty;
import webdavis.DavisUtilities;

/**
 * Provides access to the <code>creationdate</code> property.
 * This implementation returns the last modified date
 * (as the real creation date is unavailable via jCIFS).
 *
 * @author Eric Glass
 */
public class CreationDateProperty extends AbstractProperty {

    public Element createElement(Document document, RemoteFile file)
            throws IOException {
        return (file.lastModified() == 0) ? null :
                super.createElement(document, file);
    }

    public int retrieve(RemoteFile file, Element element)
            throws IOException {
        long modified = file.lastModified();
        if (modified == 0) return HttpServletResponse.SC_NOT_FOUND;
        element.setAttributeNS(WEB_FOLDERS_NAMESPACE, "w:dt", "dateTime.tz");
        element.appendChild(element.getOwnerDocument().createTextNode(
                DavisUtilities.formatCreationDate(modified)));
        return HttpServletResponse.SC_OK;
    }

}
