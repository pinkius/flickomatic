package com.webstersmalley.flickomatic;

import org.apache.commons.io.IOUtils;
import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.FlickrApi;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.oauth.OAuthService;
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

    private void checkDirectory() {
        File saveFolderDirectory = new File(saveFolder);
        if (!saveFolderDirectory.exists()) {
            if (!saveFolderDirectory.mkdirs()) {
                logger.error("Failed to make the directory: {}", saveFolder);
                throw new RuntimeException("Failed to make the directory: " + saveFolder);
            }
        }
    }

    private void savePhoto(String photoId, String format, String photoUrl) {
        try {
            URL url = new URL(photoUrl);
            InputStream is = url.openStream();
            OutputStream os = new FileOutputStream(saveFolder + File.separator + photoId + "." + format);

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

    private void writeStringToFile(String photoId, String type, String contents) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(saveFolder + File.separator + photoId + "." + type + ".xml");
            IOUtils.write(contents, fos);
            IOUtils.closeQuietly(fos);
        } catch (IOException e) {
            logger.error("Error writing file: " + e.getMessage());
            throw new RuntimeException("Error writing file: " + e.getMessage());
        }
    }

    private void writeNodeToFile(String photoId, String type, Node node) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            StreamResult result = new StreamResult(new StringWriter());
            DOMSource source = new DOMSource(node);
            transformer.transform(source, result);

            writeStringToFile(photoId, type, result.getWriter().toString());
        } catch (TransformerException e) {
            logger.error("Error writing file: " + e.getMessage());
            throw new RuntimeException("Error writing file: " + e.getMessage());
        }
    }

    private void processPhotos(String photoInfo) {
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
                    savePhoto(photoId, format, url);
                    logger.info("Saving metadata for photo {}", photoId);
                    writeNodeToFile(photoId, "info", element);
                    logger.info("Saving comments for photo {}", photoId);
                    Map<String, String> params = new HashMap<String, String>();
                    params.put("method", "flickr.photos.comments.getList");
                    params.put("photo_id", photoId);
                    String comments = comms.sendGetRequest(params);
                    writeStringToFile(photoId, "comments", comments);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void go() {
        checkDirectory();
        //System.out.println(sendRequest(Collections.singletonMap("method", "flickr.photosets.getList")));


        String setId = "72157635980974845";
        Map<String, String> params = new HashMap<String, String>();
        params.put("method", "flickr.photosets.getPhotos");
        params.put("photoset_id", setId);
        System.out.println(comms.sendGetRequest(params));


/*
        String photoId = "";
        String secret = "";

        Map<String, String> params = new HashMap<String, String>();
        params.put("method", "flickr.photos.getInfo");
        params.put("photo_id", photoId);
        params.put("secret", secret);

        String photoInfo = sendRequest(params);

        processPhotos(photoInfo);
*/



    }
}
