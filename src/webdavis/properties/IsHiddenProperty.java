/* Davenport WebDAV SMB Gateway
 * Copyright (C) 2003  Eric Glass
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package webdavis.properties;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.w3c.dom.Element;

import edu.sdsc.grid.io.RemoteFile;

import webdavis.AbstractProperty;

/**
 * Provides access to the <code>ishidden</code> property.
 * This implementation returns an indication of whether the resource
 * is hidden.
 *
 * @author Eric Glass
 */
public class IsHiddenProperty extends AbstractProperty {

    public int retrieve(RemoteFile file, Element element)
            throws IOException {
        element.setAttributeNS(WEB_FOLDERS_NAMESPACE, "w:dt", "boolean");
        element.appendChild(element.getOwnerDocument().createTextNode(
                file.isHidden() ? "1" : "0"));
        return HttpServletResponse.SC_OK;
    }

}
