package uk.cpjsmith.ponypaper;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Builds DocumentBuilder instances with external entity / DTD resolution disabled
 * so untrusted custom-pony XML cannot trigger XXE or large entity expansion.
 */
final class SecureXml {
    
    private SecureXml() {}
    
    static DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        // Prefer rejecting DOCTYPE entirely when the parser supports it.
        setFeatureQuietly(dbf, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeatureQuietly(dbf, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeatureQuietly(dbf, "http://xml.org/sax/features/external-general-entities", false);
        setFeatureQuietly(dbf, "http://xml.org/sax/features/external-parameter-entities", false);
        setFeatureQuietly(dbf, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try {
            dbf.setXIncludeAware(false);
        } catch (AbstractMethodError | UnsupportedOperationException e) {
            // Older implementations may not support this.
        }
        try {
            dbf.setExpandEntityReferences(false);
        } catch (AbstractMethodError | UnsupportedOperationException e) {
            // Older implementations may not support this.
        }
        return dbf.newDocumentBuilder();
    }
    
    private static void setFeatureQuietly(DocumentBuilderFactory dbf, String name, boolean value) {
        try {
            dbf.setFeature(name, value);
        } catch (ParserConfigurationException | AbstractMethodError | UnsupportedOperationException e) {
            // Feature unsupported on this runtime; best-effort hardening continues.
        }
    }
    
}
