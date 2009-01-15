package webdavis.properties;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.w3c.dom.Element;

import edu.sdsc.grid.io.RemoteFile;

import webdavis.AbstractProperty;

/**
 * Provides access to the <code>displayname</code> property.
 * This implementation returns the file name component of the SMB URL.
 *
 * @author Eric Glass
 */
public class DisplayNameProperty extends AbstractProperty {

    public int retrieve(RemoteFile file, Element element)
            throws IOException {
        element.appendChild(element.getOwnerDocument().createTextNode(
                file.getName()));
        return HttpServletResponse.SC_OK;
    }

}
