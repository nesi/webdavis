package webdavis;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

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

import org.irods.jargon.core.pub.io.IRODSFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Default implementation of a handler for requests using the WebDAV
 * PROPPATCH method.
 *
 * @author Shunde Zhang
 * @author Eric Glass
 * @author Jani Heikkinen <jani.heikkinen @ csc.fi> - CSC, National Research Data project (TTA), Finland
 */
public class DefaultProppatchHandler extends AbstractHandler {

	private static final String XMLNS_NAMESPACE = "http://www.w3.org/2000/xmlns/";
	
	private long maximumXmlRequest;
	
	public void init(ServletConfig config) throws ServletException {
        super.init(config);
//        String maximumXmlRequest = config.getInitParameter("maximumXmlRequest");
//        this.maximumXmlRequest = (maximumXmlRequest != null) ? Long.parseLong(maximumXmlRequest) : 20000l;
        maximumXmlRequest = Davis.getConfig().getMaximumXmlRequest();
    }
	
    /**
     * Services requests which use the WebDAV PROPPATCH method.
     * This implementation doesn't currently do anything.
     *
     * @param request The request being serviced.
     * @param response The servlet response.
     * @param auth The user's authentication information.
     * @throws SerlvetException If an application error occurs.
     * @throws IOException If an IO error occurs while handling the request.
     */
    public void service(HttpServletRequest request, HttpServletResponse response, DavisSession davisSession)
                    throws ServletException, IOException {
    	
    	IRODSFile file = getIRODSFile(request, davisSession);
        Log.log(Log.DEBUG, "PROPPATCH Request for resource \"{0}\".", file);
 	
    	StringBuffer body = new StringBuffer();
        String encoding = request.getCharacterEncoding();
        if (encoding == null) encoding = "ISO-8859-1";
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new LimitInputStream(request.getInputStream(),
                        maximumXmlRequest), encoding));
        String line;
        while ((line = reader.readLine()) != null) body.append(line);
        line = body.toString().trim();
        if (!"".equals(line)) {
            Log.log(Log.DEBUG, "Received PROPPATCH request body:\n{0}", line);
        }
    	
        Document output = null;
        output = createDocument();
        patchProps(output, getRequestURL(request), null);
        if (output != null) outputDocument(output, response);
        response.setStatus(SC_MULTISTATUS);
        response.flushBuffer();
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
                    Property.DAV_NAMESPACE, Property.DAV_PREFIX + ":multistatus");
            multistatus.setAttributeNS(XMLNS_NAMESPACE, "xmlns",
                    Property.DAV_NAMESPACE);
            multistatus.setPrefix(Property.DAV_PREFIX);
            document.appendChild(multistatus);
            return document;
        } catch (Exception ex) {
            throw new IllegalStateException(DavisUtilities.getResource(
                    DefaultPropertiesBuilder.class, "cantCreateDocument",
                            new Object[] { ex }, null));
        }
    }

    private void patchProps(Document document, String href, Element[] props) throws IOException {
    	Element response = document.createElementNS(Property.DAV_NAMESPACE,
                Property.DAV_PREFIX + ":response");
    	response.setPrefix(Property.DAV_PREFIX);
        Element hrefElem = document.createElementNS(Property.DAV_NAMESPACE,
        		Property.DAV_PREFIX + ":href");
        hrefElem.setPrefix(Property.DAV_PREFIX);
        hrefElem.appendChild(document.createTextNode(href));    
        response.appendChild(hrefElem);
        if (props == null || props.length == 0) {
            Element propstat = document.createElementNS(Property.DAV_NAMESPACE,
            		Property.DAV_PREFIX + ":propstat");
            propstat.setPrefix(Property.DAV_PREFIX);
            Element status = document.createElementNS(Property.DAV_NAMESPACE,
            		Property.DAV_PREFIX + ":status");
            status.setPrefix(Property.DAV_PREFIX);
            status.appendChild(document.createTextNode("HTTP/1.1 200 OK"));
            propstat.appendChild(status);
            response.appendChild(propstat);
            document.getDocumentElement().appendChild(response);
            return;
        }
    }
       
    private void outputDocument(Document output, HttpServletResponse response) throws ServletException, IOException {
    	try {
    		Transformer transformer =
    		TransformerFactory.newInstance().newTransformer();
    		transformer.setOutputProperty("encoding", "UTF-8");
    		ByteArrayOutputStream collector = new ByteArrayOutputStream();
    		transformer.transform(new DOMSource(output),
    		new StreamResult(collector));
    		if (Log.getThreshold() < Log.INFORMATION) {
    			Log.log(Log.DEBUG, "PROPPATCH response body:\n{0}", collector.toString("UTF-8"));
    		}
    		response.setContentType("text/xml; charset=\"utf-8\"");
    		collector.writeTo(response.getOutputStream());
    	} catch (TransformerException ex) {
    		throw new IOException(ex.getMessage());
    	}
    }
    
}
