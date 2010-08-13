package webdavis;

import java.io.ByteArrayInputStream;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

/**
 * Entity resolver which prevents external entities from being referenced.
 * This is used to prevent various XML-based attacks.
 *
 * @author Eric Glass
 */
public class BlockedEntityResolver implements EntityResolver {

    /**
     * Singleton resolver instance.
     */ 
    public static final BlockedEntityResolver INSTANCE =
            new BlockedEntityResolver();

    private BlockedEntityResolver() { }

    /**
     * Returns an empty stream in response to an attempt to resolve an
     * external entity, and logs a warning.
     *
     * @param publicId The public identifier of the external entity.
     * @param systemId The system identifier of the external entity.
     * @return An empty <code>InputSource</code>.
     */ 
    public InputSource resolveEntity(String publicId, String systemId) {
        Log.log(Log.WARNING,
                "Blocked attempt to resolve external entity at {0}.", systemId);
        return new InputSource(new ByteArrayInputStream(new byte[0]));
    }

}
