package com.webstersmalley.flickomatic;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.annotation.Resource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by: Matthew Smalley
 * Date: 06/10/13
 */
@Service("flickrDownloader")
public class FlickrDownloader {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Value("${flickomatic.home.savedir}")
    private String saveFolder;

    @Resource(name = "comms")
    private Comms comms;

    /**
     * Helper method to check (and create if necessary) a folder exists
     * @param setName the set name (for folder naming purposes)
     */
    private void checkDirectory(String setName) {
        File saveFolderDirectory = new File(saveFolder + File.separator + setName);
        if (!saveFolderDirectory.exists()) {
            if (!saveFolderDirectory.mkdirs()) {
                logger.error("Failed to make the directory: {}", saveFolder);
                throw new RuntimeException("Failed to make the directory: " + saveFolder);
            }
        }
    }

    /**
     * Helper method to write the binary image data to disk
     *
     * @param setName name of the set (for folder naming purposes)
     * @param photoId name of the photo (for file naming purposes)
     * @param format the format (jpeg/png) (for file naming purposes)
     * @param photoUrl the url to download
     */
    private void savePhoto(String setName, String photoId, String format, String photoUrl) {
        String folder = saveFolder + File.separator + setName;
        try {
            URL url = new URL(photoUrl);
            InputStream is = url.openStream();
            OutputStream os = new FileOutputStream(folder + File.separator + photoId + "." + format);

            byte[] b = new byte[2048];
            int length;

            while ((length = is.read(b)) != -1) {
                os.write(b, 0, length);
            }

            is.close();
            os.close();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper method to write a string to file
     *
     * @param setName name of the set (for folder naming purposes)
     * @param photoId name of the photo (for file naming purposes)
     * @param type type (for file naming purposes)
     * @param contents the string to write
     */
    private void writeStringToFile(String setName, String photoId, String type, String contents) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(saveFolder + File.separator + setName + File.separator + photoId + "." + type + ".xml");
            IOUtils.write(contents, fos);
            IOUtils.closeQuietly(fos);
        } catch (IOException e) {
            logger.error("Error writing file: " + e.getMessage());
            throw new RuntimeException("Error writing file: " + e.getMessage());
        }
    }

    /**
     * Helper method to write a node of XML document to file
     *
     * @param setName name of the set (for folder naming purposes)
     * @param photoId name of the photo (for file naming purposes)
     * @param type type (for file naming purposes)
     * @param node the Node to write
     */
    private void writeNodeToFile(String setName, String photoId, String type, Node node) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            StreamResult result = new StreamResult(new StringWriter());
            DOMSource source = new DOMSource(node);
            transformer.transform(source, result);

            writeStringToFile(setName, photoId, type, result.getWriter().toString());
        } catch (TransformerException e) {
            logger.error("Error writing file: " + e.getMessage());
            throw new RuntimeException("Error writing file: " + e.getMessage());
        }
    }

    /**
     * Downloads everything about a given photo (ie the image itself in original format, the metadata and any comments.
     *
     * @param setName name of the set (for folder naming purposes)
     * @param photoInfo flickr photo xml contents
     */
    private void processPhoto(String setName, String photoInfo) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(photoInfo)));

            NodeList nodeList = doc.getElementsByTagName("photo");
            for (int i = 0; i < nodeList.getLength(); i++) {
                if (nodeList.item(i) instanceof Element) {
                    Element element = (Element) nodeList.item(i);
                    String urlFormat = "http://farm%s.staticflickr.com/%s/%s_%s_o.%s";
                    String farmId = element.getAttribute("farm");
                    String serverId = element.getAttribute("server");
                    String photoId = element.getAttribute("id");
                    String originalSecret = element.getAttribute("originalsecret");
                    String format = element.getAttribute("originalformat");

                    String url = String.format(urlFormat, farmId, serverId, photoId, originalSecret, format);
                    logger.info("Saving image for photo {}", photoId);
                    savePhoto(setName, photoId, format, url);
                    logger.info("Saving metadata for photo {}", photoId);
                    writeNodeToFile(setName, photoId, "info", element);
                    logger.info("Saving comments for photo {}", photoId);
                    Map<String, String> params = new HashMap<String, String>();
                    params.put("method", "flickr.photos.comments.getList");
                    params.put("photo_id", photoId);
                    String comments = comms.sendGetRequest(params);
                    writeStringToFile(setName, photoId, "comments", comments);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Downloads a specific set. Is called by downloadAllSets(), or alternatively can be used directly to specify a
     * non-owned set.
     *
     * @param setId the id of the set to download
     */
    public void downloadSet(String setId) {
        logger.info("Downloading set: {}", setId);
        checkDirectory(setId);
        Map<String, String> params = new HashMap<String, String>();
        params.put("method", "flickr.photosets.getPhotos");
        params.put("photoset_id", setId);
        String setContents = comms.sendGetRequest(params);
        for (Map<String, String> photo: XMLUtils.getListOfAttributesFromElements(setContents, "//photo")) {
            params = new HashMap<String, String>();
            params.put("method", "flickr.photos.getInfo");
            params.put("photo_id", photo.get("id"));
            params.put("secret", photo.get("secret"));
            String photoDetails = comms.sendGetRequest(params);
            processPhoto(setId, photoDetails);
        }
    }

    /**
     * Downloads all the sets owned by the logged-in user. NB this does NOT include sets owned by other users which the
     * logged-in user has access to. Use downloadSet(setId) if you want to download non-native sets.
     */
    public void downloadAllSets() {
        logger.info("Downloading all sets");

        logger.debug("Download list of sets");
        String photosetListXML = comms.sendGetRequest(Collections.singletonMap("method", "flickr.photosets.getList"));
        List<Map<String, String>> setAttributeList = XMLUtils.getListOfAttributesFromElements(photosetListXML, "//photoset");
        for (Map<String, String> setAttributes: setAttributeList) {
            downloadSet(setAttributes.get("id"));
        }
    }
}
