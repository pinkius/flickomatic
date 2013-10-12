package com.webstersmalley.flickomatic;

import org.junit.Test;
import org.w3c.dom.Document;

import static org.junit.Assert.*;

/**
 * Created by: Matthew Smalley
 * Date: 12/10/13
 */
public class TestXMLUtils {
    private String sampleXML = "<?xml version=\"1.0\" encoding=\"utf-8\" ?><root attribute=\"rootattribute\"></root>";
    @Test
    public void testGetDocumentFromString() {
        Document document = XMLUtils.getDocumentFromString(sampleXML);
        assertNotNull(document);
    }

    @Test
    public void testGetAttribute() {
        assertEquals("rootattribute", XMLUtils.getAttributeValue(sampleXML, "root", "attribute"));
    }
}
