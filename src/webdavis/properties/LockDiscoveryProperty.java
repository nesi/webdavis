package webdavis.properties;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.w3c.dom.Element;

import edu.sdsc.grid.io.RemoteFile;

import webdavis.AbstractProperty;

/**
 * Provides access to the <code>lockdiscovery</code> property.
 * This implementation returns the list of active locks on the resource.
 *
 * @author Eric Glass
 */
public class LockDiscoveryProperty extends AbstractProperty {

    public int retrieve(RemoteFile file, Element element) throws IOException {
//        LockManager lockManager = (LockManager)
//                getServletConfig().getServletContext().getAttribute(
//                        Davenport.LOCK_MANAGER);
//        if (lockManager == null) 
        	return HttpServletResponse.SC_NOT_FOUND;
//        DavisUtilities.lockDiscovery(file, lockManager, element);
//        return HttpServletResponse.SC_OK;
    }

}
