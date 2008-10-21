package webdavis;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.List;
import java.util.Vector;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;

import javax.xml.transform.dom.DOMSource;

import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.sdsc.grid.io.RemoteFile;
import edu.sdsc.grid.io.RemoteFileSystem;

/**
 * Default implementation of a handler for requests using the WebDAV
 * PROPFIND method.
 *
 * @author Eric Glass
 */
public class DefaultPropfindHandler extends AbstractHandler {

    private static final String DAV_NAMESPACE = "DAV:";

    private PropertiesBuilder propertiesBuilder;

    private long maximumXmlRequest;

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        propertiesBuilder = new DefaultPropertiesBuilder();
        propertiesBuilder.init(config);
        String maximumXmlRequest = config.getInitParameter("maximumXmlRequest");
        this.maximumXmlRequest = (maximumXmlRequest != null) ?
                Long.parseLong(maximumXmlRequest) : 20000l;
    }

    public void destroy() {
        propertiesBuilder.destroy();
        propertiesBuilder = null;
        super.destroy();
    }

    /**
     * Services requests which use the WebDAV PROPFIND method.
     * This implementation builds and returns an XML document
     * containing an appropriate PROPFIND result.
     * <br>
     * If the specified resource does not exist, a 404 (Not Found) error
     * is sent to the client.
     * <br>
     * If the PROPFIND request is not properly formed, a 400 (Bad Request)
     * error is sent to the client.
     *
     * @param request The request being serviced.
     * @param response The servlet response.
     * @param auth The user's authentication information.
     * @throws ServletException If an application error occurs.
     * @throws IOException If an IO error occurs while handling the request.
     */
    public void service(HttpServletRequest request,
            HttpServletResponse response, DavisSession davisSession)
                    throws ServletException, IOException {
        int depth = DavisUtilities.parseDepth(request.getHeader("Depth"));
        RemoteFile file = getRemoteFile(request, davisSession.getRemoteFileSystem());
        if (!file.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String requestUrl = getRequestURL(request);
        Log.log(Log.DEBUG, "requestUrl: {0}", requestUrl);
        PropertiesDirector director = new PropertiesDirector(
                getPropertiesBuilder());
        Document properties = null;
        if (request.getContentLength() > 0) {
            DocumentBuilderFactory builderFactory =
                    DocumentBuilderFactory.newInstance();
            builderFactory.setNamespaceAware(true);
            builderFactory.setExpandEntityReferences(false);
            builderFactory.setIgnoringComments(true);
            builderFactory.setCoalescing(true);
            Document document = null;
            try {
                DocumentBuilder builder = builderFactory.newDocumentBuilder();
                builder.setEntityResolver(BlockedEntityResolver.INSTANCE);
                document = builder.parse(
                        new LimitInputStream(request.getInputStream(),
                                maximumXmlRequest));
            } catch (Exception ex) {
                throw new IOException(DavisUtilities.getResource(
                        DefaultPropfindHandler.class, "parseError",
                                new Object[] { ex }, request.getLocale()));
            }
            Element propfind = document.getDocumentElement();
            Node child = null;
            NodeList nodes = propfind.getChildNodes();
            for (int i = nodes.getLength() - 1; i >= 0; i--) {
                Node node = nodes.item(i);
                if (!(node instanceof Element)) continue;
                if (DAV_NAMESPACE.equals(node.getNamespaceURI())) {
                    child = node;
                    break;
                }
            }
            String name = (child != null) ? child.getLocalName() : null;
            if (child == null || "allprop".equals(name)) {
                properties = director.getAllProperties(file, requestUrl, depth);
            } else if ("propname".equals(name)) {
                properties = director.getPropertyNames(file, requestUrl, depth);
            } else if ("prop".equals(name)) {
                List propList = new Vector();
                nodes = child.getChildNodes();
                int count = nodes.getLength();
                for (int i = 0; i < count; i++) {
                    Node node = nodes.item(i);
                    if (node instanceof Element) propList.add(node);
                }
                Element[] props = (Element[]) propList.toArray(new Element[0]);
                properties = director.getProperties(file, requestUrl, props,
                        depth);
            } else  {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
        } else {
            properties = director.getAllProperties(file, requestUrl, depth);
        }
        try {
            Transformer transformer =
                    TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty("encoding", "UTF-8");
            ByteArrayOutputStream collector = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(properties),
                    new StreamResult(collector));
            response.setStatus(SC_MULTISTATUS);
            response.setContentType("text/xml; charset=\"utf-8\"");
            collector.writeTo(response.getOutputStream());
            response.flushBuffer();
        } catch (TransformerException ex) {
            throw new IOException(ex.getMessage());
        }
    }

    /**
     * Returns the <code>PropertiesBuilder</code> that will be used
     * to build the PROPFIND result XML document.
     *
     * @return The <code>PropertiesBuilder</code> that is used
     * to build the XML document.
     */
    protected PropertiesBuilder getPropertiesBuilder() {
        return propertiesBuilder;
    }

}
