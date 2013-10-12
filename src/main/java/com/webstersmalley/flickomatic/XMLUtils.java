package com.webstersmalley.flickomatic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;

/**
 * Created by: Matthew Smalley
 * Date: 12/10/13
 */
public class XMLUtils {
    private final static Logger logger = LoggerFactory.getLogger(XMLUtils.class);
    private XMLUtils() {

    }

    public static Document getDocumentFromString(String contents) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(contents)));
        } catch (Exception e) {
            logger.error("Error parsing contents: " + e.getMessage(), e);
            throw new RuntimeException("Error parsing contents: " + e.getMessage(), e);
        }
    }

    public static String getAttributeValue(String document, String xpath, String attribute) {
        return getAttributeValue(getDocumentFromString(document), xpath, attribute);
    }

    public static String getAttributeValue(Document document, String xpath, String attribute) {
        try {
            XPath xPath = XPathFactory.newInstance().newXPath();
            XPathExpression xPathExpression = xPath.compile(xpath + "/@" + attribute);
            String attributeValue = "" + xPathExpression.evaluate(document, XPathConstants.STRING);
            return attributeValue;
        } catch (Exception e) {
            logger.error("Error parsing contents: " + e.getMessage(), e);
            throw new RuntimeException("Error parsing contents: " + e.getMessage(), e);
        }
    }
}
