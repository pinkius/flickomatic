package com.webstersmalley.flickomatic;

import org.junit.Test;
import org.w3c.dom.Document;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Created by: Matthew Smalley
 * Date: 12/10/13
 */
public class TestXMLUtils {
    private String sampleXML = "<?xml version=\"1.0\" encoding=\"utf-8\" ?><root attribute=\"rootattribute\"><inner id=\"first\"/><inner id=\"second\"/></root>";
    @Test
    public void testGetDocumentFromString() {
        Document document = XMLUtils.getDocumentFromString(sampleXML);
        assertNotNull(document);
    }

    @Test
    public void testGetAttribute() {
        assertEquals("rootattribute", XMLUtils.getAttributeValue(sampleXML, "root", "attribute"));
        assertEquals("second", XMLUtils.getAttributeValue(sampleXML, "//inner[2]", "id"));
    }

    @Test
    public void testGetListOfAttributesFromElements() {
        List<Map<String, String>> list = XMLUtils.getListOfAttributesFromElements(sampleXML, "//inner");
        assertEquals(2, list.size());
        assertEquals("first", list.get(0).get("id"));
        assertEquals("second", list.get(1).get("id"));
    }
}
