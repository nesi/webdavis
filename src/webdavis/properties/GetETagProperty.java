package webdavis.properties;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.irods.jargon.core.pub.io.IRODSFile;
import org.w3c.dom.Element;

import webdavis.AbstractProperty;
import webdavis.DavisUtilities;

/**
 * Provides access to the <code>getetag</code> property.
 * This implementation returns an ETag for the resource.
 *
 * @author Eric Glass
 */
public class GetETagProperty extends AbstractProperty {

    public int retrieve(IRODSFile file, Element element)
            throws IOException {
        String etag = DavisUtilities.getETag(file);
        if (etag == null) return HttpServletResponse.SC_NOT_FOUND;
        element.appendChild(element.getOwnerDocument().createTextNode(etag));
        return HttpServletResponse.SC_OK;
    }

}
