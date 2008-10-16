package webdavis.properties;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.w3c.dom.Element;

import edu.sdsc.grid.io.RemoteFile;

import webdavis.AbstractProperty;

/**
 * Provides access to the <code>supportedlock</code> property.
 * This implementation returns the server's lock capability.
 *
 * @author Eric Glass
 */
public class SupportedLockProperty extends AbstractProperty {

    public int retrieve(RemoteFile file, Element element) throws IOException {
//        LockManager lockManager = (LockManager)
//                getServletConfig().getServletContext().getAttribute(
//                        Davenport.LOCK_MANAGER);
//        if (lockManager == null) 
        	return HttpServletResponse.SC_NOT_FOUND;
//        int lockSupport = lockManager.getLockSupport(file);
//        if (lockSupport == LockManager.NO_LOCK_SUPPORT) {
//            return HttpServletResponse.SC_OK;
//        }
//        if ((lockSupport & LockManager.EXCLUSIVE_LOCK_SUPPORT) ==
//                LockManager.EXCLUSIVE_LOCK_SUPPORT) {
//            element.appendChild(createLockEntry(element, "exclusive"));
//        }
//        if ((lockSupport & LockManager.SHARED_LOCK_SUPPORT) ==
//                LockManager.SHARED_LOCK_SUPPORT) {
//            element.appendChild(createLockEntry(element, "shared"));
//        }
//        return HttpServletResponse.SC_OK;
    }

    private Element createLockEntry(Element base, String scope) {
        Element lockEntry = createElement(base, "lockentry");
        Element lockScope = createElement(lockEntry, "lockscope");
        lockScope.appendChild(createElement(lockScope, scope));
        lockEntry.appendChild(lockScope);
        Element lockType = createElement(lockEntry, "locktype");
        lockType.appendChild(createElement(lockType, "write"));
        lockEntry.appendChild(lockType);
        return lockEntry;
    }

    private Element createElement(Element base, String tag) {
        String namespace = base.getNamespaceURI();
        if (namespace != null) {
            String prefix = base.getPrefix();
            return base.getOwnerDocument().createElementNS(namespace,
                    prefix == null ? tag : prefix + ":" + tag);
        }
        return base.getOwnerDocument().createElement(tag);
    }

}
