package webdavis;

import java.io.IOException;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;

import javax.servlet.http.HttpServletResponse;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.sdsc.grid.io.RemoteFile;

import webdavis.properties.CreationDateProperty;
import webdavis.properties.DisplayNameProperty;
import webdavis.properties.GetContentLengthProperty;
import webdavis.properties.GetContentTypeProperty;
import webdavis.properties.GetETagProperty;
import webdavis.properties.GetLastModifiedProperty;
import webdavis.properties.IsCollectionProperty;
import webdavis.properties.IsHiddenProperty;
import webdavis.properties.IsReadOnlyProperty;
import webdavis.properties.LockDiscoveryProperty;
import webdavis.properties.ResourceTypeProperty;
import webdavis.properties.SupportedLockProperty;

/**
 * Default builder for the PROPFIND result XML document.  This builder
 * supports retrieval for most of the basic WebDAV properties.
 *
 * @author Shunde Zhang
 * @author Eric Glass
 */
public class DefaultPropertiesBuilder implements PropertiesBuilder {

    private static final String XMLNS_NAMESPACE =
            "http://www.w3.org/2000/xmlns/";

    private final Set properties = new HashSet();

    private ServletConfig config;

    public void init(ServletConfig config) throws ServletException {
        this.config = config;
        initProperties(config);
    }

    public void destroy() {
        clearProperties();
        this.config = null;
    }

    public void addPropNames(Document document, RemoteFile file, String href)
            throws IOException {
        Element response = document.createElementNS(Property.DAV_NAMESPACE,
                "response");
        Element hrefElem = document.createElementNS(Property.DAV_NAMESPACE,
                "href");
        hrefElem.appendChild(document.createTextNode(href));
        response.appendChild(hrefElem);
        Element propstat = document.createElementNS(Property.DAV_NAMESPACE,
                "propstat");
        Element status = document.createElementNS(Property.DAV_NAMESPACE,
                "status");
        status.appendChild(document.createTextNode("HTTP/1.1 200 OK"));
        propstat.appendChild(status);
        Iterator iterator = properties.iterator();
        while (iterator.hasNext()) {
            Element prop = ((Property) iterator.next()).createElement(document,
                    file);
            if (prop != null) propstat.appendChild(prop);
        }
        response.appendChild(propstat);
        document.getDocumentElement().appendChild(response);
    }

    public void addAllProps(Document document, RemoteFile file, String href)
            throws IOException {
        List list = new Vector();
        Iterator iterator = properties.iterator();
        while (iterator.hasNext()) {
            Element prop = ((Property) iterator.next()).createElement(document,
                    file);
           if (prop != null) list.add(prop);
        }
        addProps0(document, file, href, (Element[]) list.toArray(new Element[0]));
    }
    
    public void addProps(Document document, RemoteFile file, String href,
            Element[] props) throws IOException {
        addProps0(document, file, href, props);
    }

    public Document createDocument() {
        DocumentBuilderFactory builderFactory =
                DocumentBuilderFactory.newInstance();
        builderFactory.setNamespaceAware(true);
        builderFactory.setExpandEntityReferences(false);
        try {
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            builder.setEntityResolver(BlockedEntityResolver.INSTANCE);
            Document document = builder.newDocument();
            Element multistatus = document.createElementNS(
                    Property.DAV_NAMESPACE, "multistatus");
            multistatus.setAttributeNS(XMLNS_NAMESPACE, "xmlns",
                    Property.DAV_NAMESPACE);
            multistatus.setAttributeNS(XMLNS_NAMESPACE, "xmlns:w",
                    Property.WEB_FOLDERS_NAMESPACE);
            document.appendChild(multistatus);
            return document;
        } catch (Exception ex) {
            throw new IllegalStateException(DavisUtilities.getResource(
                    DefaultPropertiesBuilder.class, "cantCreateDocument",
                            new Object[] { ex }, null));
        }
    }

    /**
     * Returns the servlet configuration.
     *
     * @return A <code>ServletConfig</code> containing the servlet's
     * configuration information.
     */
    protected ServletConfig getServletConfig() {
        return config;
    }

    private void addProps0(Document document, RemoteFile file, String href,
            Element[] props) throws IOException {

    	Element response = document.createElementNS(Property.DAV_NAMESPACE,
                "response");
        Element hrefElem = document.createElementNS(Property.DAV_NAMESPACE,
                "href");
        hrefElem.appendChild(document.createTextNode(href));
        response.appendChild(hrefElem);
        if (props == null || props.length == 0) {
            Element propstat = document.createElementNS(Property.DAV_NAMESPACE,
                    "propstat");
            Element status = document.createElementNS(Property.DAV_NAMESPACE,
                    "status");
            status.appendChild(document.createTextNode("HTTP/1.1 200 OK"));
            propstat.appendChild(status);
            response.appendChild(propstat);
            document.getDocumentElement().appendChild(response);
            return;
        }
        Map results = new HashMap();
        for (int i = props.length - 1; i >= 0; i--) {
            Element prop;
            if (document != props[i].getOwnerDocument()) {
                prop = (Element) document.importNode(props[i], false);
            } else {
                prop = (Element) props[i].cloneNode(false);
            }
            Iterator iterator = properties.iterator();
            int result = HttpServletResponse.SC_NOT_FOUND;
            while (iterator.hasNext()) {
                Property property = (Property) iterator.next();
                String name = property.getName();
                String namespace = property.getNamespace();
                if (namespace == null) namespace = Property.DAV_NAMESPACE;
                if (!prop.getLocalName().equals(name)) continue;
                if (!prop.getNamespaceURI().equals(namespace)) continue;
                result = property.retrieve(file, prop);
                break;
            }
            Integer resultCode = new Integer(result);
            List resultList = (List) results.get(resultCode);
            if (resultList == null) {
                results.put(resultCode, resultList = new Vector());
            }
            resultList.add(prop);
        }
        Iterator entries = results.entrySet().iterator();
        Element documentElement = document.getDocumentElement();
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry) entries.next();
            int result = ((Integer) entry.getKey()).intValue();
            Element propstat = document.createElementNS(Property.DAV_NAMESPACE,
                    "propstat");
            Element status = document.createElementNS(Property.DAV_NAMESPACE,
                    "status");
            status.appendChild(document.createTextNode("HTTP/1.1 " + result +
                    " MultiStatus"));
            propstat.appendChild(status);
            response.appendChild(propstat);
            Element prop = document.createElementNS(Property.DAV_NAMESPACE,
                    "prop");
            propstat.appendChild(prop);
            Iterator resultProps = ((List) entry.getValue()).iterator();
            while (resultProps.hasNext()) {
                Element element = (Element) resultProps.next();
                prop.appendChild(element);
                String prefix = element.getPrefix();
                if (prefix != null && !documentElement.hasAttributeNS(
                        XMLNS_NAMESPACE, "xmlns:" + prefix)) {
                    documentElement.setAttributeNS(XMLNS_NAMESPACE,
                            "xmlns:" + prefix, element.getNamespaceURI());
                }
            }
        }
        documentElement.appendChild(response);
    }

    private void initProperties(ServletConfig config) throws ServletException {
        Map propertyMap = new HashMap();
        propertyMap.put("creationdate", CreationDateProperty.class);
        propertyMap.put("displayname", DisplayNameProperty.class);
        propertyMap.put("getcontentlength", GetContentLengthProperty.class);
        propertyMap.put("getcontenttype", GetContentTypeProperty.class);
        propertyMap.put("getlastmodified", GetLastModifiedProperty.class);
        propertyMap.put("ishidden", IsHiddenProperty.class);
        propertyMap.put("isreadonly", IsReadOnlyProperty.class);
        propertyMap.put("iscollection", IsCollectionProperty.class);
        propertyMap.put("getetag", GetETagProperty.class);
        if (config.getServletContext().getAttribute(Davis.LOCK_MANAGER) !=
                null) {
            propertyMap.put("lockdiscovery", LockDiscoveryProperty.class);
            propertyMap.put("supportedlock", SupportedLockProperty.class);
        }
        propertyMap.put("resourcetype", ResourceTypeProperty.class);
        Enumeration parameters = config.getInitParameterNames();
        while (parameters.hasMoreElements()) {
            String name = (String) parameters.nextElement();
            if (!name.startsWith("property.")) continue;
            if (name.endsWith(".prefix") || name.endsWith(".namespace")) {
                continue;
            }
            String propertyClass = config.getInitParameter(name);
            name = name.substring(9);
            if ("".equals(propertyClass)) {
                propertyMap.remove(name);
            } else {
                try {
                    propertyMap.put(name, Class.forName(propertyClass));
                } catch (Exception ex) {
                    throw new UnavailableException(DavisUtilities.getResource(
                            DefaultPropertiesBuilder.class,
                                    "cantCreateProperty", new Object[] {
                                            name, ex }, null));
                }
            }
        }
        clearProperties();
        Iterator entries = propertyMap.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry) entries.next();
            String name = (String) entry.getKey();
            try {
                Property property = (Property)
                        ((Class) entry.getValue()).newInstance();
                property.init(name, config);
                replace(property);
            } catch (Exception ex) {
                throw new UnavailableException(DavisUtilities.getResource(
                        DefaultPropertiesBuilder.class, "cantCreateProperty",
                                new Object[] { name, ex }, null));
            }
        }
    }

    private void replace(Property property) {
        Iterator iterator = properties.iterator();
        while (iterator.hasNext()) {
            Property other = (Property) iterator.next();
            if (property.equals(other)) {
                other.destroy();
                iterator.remove();
                break;
            }
        }
        properties.add(property);
    }

    private void clearProperties() {
        Iterator iterator = properties.iterator();
        while (iterator.hasNext()) {
            ((Property) iterator.next()).destroy();
            iterator.remove();
        }
    }

}
