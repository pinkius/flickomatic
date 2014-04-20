package com.webstersmalley.flickomatic;

import com.flickr4java.flickr.Flickr;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
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
import javax.xml.parsers.ParserConfigurationException;
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
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by: Matthew Smalley
 * Date: 06/10/13
 */
@Service("flickrDownloader")
public class FlickrDownloader {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private int threads = 8;

    private final static String PHOTO_URL_FORMAT = "http://farm%s.staticflickr.com/%s/%s_%s_o.%s";

    @Value("${flickomatic.home.savedir.pictures}")
    private String picturesSaveFolder;

    @Value("${flickomatic.home.savedir.metadata}")
    private String metadataSaveFolder;

    @Value("${flickomatic.fulldownload}")
    private boolean fullDownload;

    @Resource(name = "comms")
    private Comms comms;

    private Set<String> photosDownloaded = new HashSet<>();

    /**
     * Helper method to check (and create if necessary) a folder exists
     */
    private void checkDirectories() {
        checkDirectory(new File(picturesSaveFolder));
        checkDirectory(new File(metadataSaveFolder));
    }

    private void checkDirectory(File directory) {
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                logger.error("Failed to make the directory: {}", directory.getAbsolutePath());
                throw new RuntimeException("Failed to make the directory: " + directory.getAbsolutePath());
            }
        }
    }

    /**
     * Helper method to write the binary image data to disk
     *
     * @param setName  name of the set (for folder naming purposes)
     * @param photoId  name of the photo (for file naming purposes)
     * @param format   the format (jpeg/png) (for file naming purposes)
     * @param photoUrl the url to download
     */
    private void savePhoto(String setName, String photoId, String format, String photoUrl, File outputFile) {
        try {
            URL url = new URL(photoUrl);
            InputStream is = url.openStream();
            OutputStream os = new FileOutputStream(outputFile);

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
     * @param outputFile  output file
     * @param contents the string to write
     */
    private void writeStringToFile(File outputFile, String contents) {
        FileOutputStream fos = null;
        try {
//            fos = new FileOutputStream(saveFolder + File.separator + setName + File.separator + photoId + "." + type + ".xml");
            fos = new FileOutputStream(outputFile);
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
     * @param outputFile  output file
     * @param node    the Node to write
     */
    private void writeNodeToFile(File outputFile, Node node) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            StreamResult result = new StreamResult(new StringWriter());
            DOMSource source = new DOMSource(node);
            transformer.transform(source, result);

            writeStringToFile(outputFile, result.getWriter().toString());
        } catch (TransformerException e) {
            logger.error("Error writing file: " + e.getMessage());
            throw new RuntimeException("Error writing file: " + e.getMessage());
        }
    }

    private DocumentBuilder builder;

    public FlickrDownloader() {
        try {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Error creating document builder: " + e.getMessage(), e);
        }
    }

    private long getLastUpdateTime(Element element) {
        long lastUpdateTime = Long.MAX_VALUE;
        NodeList subElementsList = element.getElementsByTagName("dates");
        for (int j = 0; j < subElementsList.getLength(); j++) {
            Element datesElement = (Element) subElementsList.item(j);
            String dateString = datesElement.getAttribute("lastupdate");
            lastUpdateTime = ((Long.valueOf(dateString)*1000));
        }
        return lastUpdateTime;
    }

    private boolean shouldDownload(long lastUpdateTime, File outputFile) {
        if (fullDownload) {
            return true;
        }
        if (!outputFile.exists()) {
            return true;
        }
        logger.debug("Checking date of last download vs lastupdate time for picture: {}", outputFile);
        boolean shouldDownload = outputFile.lastModified() <= lastUpdateTime;
        logger.debug("Server time: " + lastUpdateTime);
        logger.debug("File time: " + outputFile.lastModified());
        logger.debug("Should download? {}", shouldDownload);
        return shouldDownload;
    }



    /**
     * Downloads everything about a given photo (ie the image itself in original format, the metadata and any comments.
     *
     * @param setName   name of the set (for folder naming purposes)
     * @param photoInfo flickr photo xml contents
     */
    private void processPhoto(String setName, String photoInfo) {
        try {
            Document doc = builder.parse(new InputSource(new StringReader(photoInfo)));

            NodeList nodeList = doc.getElementsByTagName("photo");
            for (int i = 0; i < nodeList.getLength(); i++) {
                if (nodeList.item(i) instanceof Element) {
                    Element element = (Element) nodeList.item(i);
                    String farmId = element.getAttribute("farm");
                    String serverId = element.getAttribute("server");
                    String photoId = element.getAttribute("id");
                    String originalSecret = element.getAttribute("originalsecret");
                    String format = element.getAttribute("originalformat");
                    String url = String.format(PHOTO_URL_FORMAT, farmId, serverId, photoId, originalSecret, format);
                    File photoFile = new File(picturesSaveFolder + File.separator + photoId + "." + format);
                    File infoFile = new File(metadataSaveFolder + File.separator + photoId + ".info.xml");
                    File commentsFile = new File(metadataSaveFolder + File.separator + photoId + ".comments.xml");
                    File contextsFile = new File(metadataSaveFolder + File.separator + photoId + ".contexts.xml");
                    long lastUpdateTime = getLastUpdateTime(element);

                    if (shouldDownload(lastUpdateTime, photoFile)) {
                        logger.info("Saving image for photo {}", photoId);
                        savePhoto(setName, photoId, format, url, photoFile);
                    }
                    if (shouldDownload(lastUpdateTime, infoFile)) {
                        logger.info("Saving metadata for photo {}", photoId);
                        writeNodeToFile(infoFile, element);
                    }
                    if (shouldDownload(lastUpdateTime, commentsFile)) {
                        savePhotoResponseToFile("flickr.photos.comments.getList", photoId, commentsFile);
                    }
                    if (shouldDownload(lastUpdateTime, contextsFile)) {
                        savePhotoResponseToFile("flickr.photos.getAllContexts", photoId, contextsFile);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
      
    private void savePhotoResponseToFile(String method, String photoId, File outputFile) {
        logger.info("Writing: {}", outputFile);
        Map<String, String> params = new HashMap<String, String>();
        params.put("method", method);
        params.put("photo_id", photoId);
        String comments = comms.sendGetRequest(params);
        writeStringToFile(outputFile, comments);
    }

    /**
     * Downloads a specific set. Is called by downloadAllSets(), or alternatively can be used directly to specify a
     * non-owned set.
     *
     * @param setId the id of the set to download
     */
    public void downloadSet(String setId) {
        checkDirectories();
        String setContents;
        if (setId == null) {
            logger.info("Downloading photos not in sets");
            Map<String, String> params = new HashMap<String, String>();
            params.put("method", "flickr.photos.getNotInSet");
            setContents = comms.sendGetRequest(params);
        } else {
            logger.info("Downloading set: {}", setId);
            Map<String, String> params = new HashMap<String, String>();
            params.put("method", "flickr.photosets.getPhotos");
            params.put("photoset_id", setId);
            setContents = comms.sendGetRequest(params);
        }
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (Map<String, String> photo : XMLUtils.getListOfAttributesFromElements(setContents, "//photo")) {
            Map<String, String> photoParams = new HashMap<String, String>();
            photoParams.put("method", "flickr.photos.getInfo");
            photoParams.put("photo_id", photo.get("id"));
            photoParams.put("secret", photo.get("secret"));
            executor.execute(new Runnable() {
                public void run() {
                    String photoDetails = comms.sendGetRequest(photoParams);
                    processPhoto(setId, photoDetails);
                }
            });
        }
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Error waiting for tasks to complete: " + e.getMessage(), e);
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
        for (Map<String, String> setAttributes : setAttributeList) {
            downloadSet(setAttributes.get("id"));
        }
        downloadSet(null);
    }
}
