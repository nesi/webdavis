package webdavis.properties;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.irods.jargon.core.pub.io.IRODSFile;
import org.w3c.dom.Element;

import webdavis.AbstractProperty;

/**
 * Provides access to the <code>getcontenttype</code> property.
 * This implementation returns the content type of the resource
 * as set by the deployment descriptor.
 *
 * @author Eric Glass
 */
public class GetContentTypeProperty extends AbstractProperty {

    public int retrieve(IRODSFile file, Element element)
            throws IOException {
        String contentType;
        if (file.isDirectory()) {
            contentType = "httpd/unix-directory";
        } else {
            contentType = getServletConfig().getServletContext().getMimeType(
                    file.getName());
            if (contentType == null) contentType = "application/octet-stream";
        }
        element.appendChild(element.getOwnerDocument().createTextNode(
                contentType));
        return HttpServletResponse.SC_OK;
    }

}
