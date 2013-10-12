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
import java.util.HashMap;
import java.util.Map;

/**
 * Created by: Matthew Smalley
 * Date: 06/10/13
 */
@Service("flickrDownloader")
public class FlickrDownloader {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private String saveFolder = "c:/temp/flickrtemp";

    @Value("${flickomatic.api.key}")
    private String apiKey;

    @Value("${flickomatic.api.secret}")
    private String apiSecret;

    @Value("${token}")
    private String token;

    private void authenticate() {
        service = new ServiceBuilder().provider(FlickrApi.class).apiKey(apiKey).apiSecret(apiSecret).build();
        accessToken = new Token(token.split(",")[0], token.split(",")[1]);
    }

    public String sendRequest(Map<String, String> parameters) {
        OAuthRequest request = new OAuthRequest(Verb.GET, PROTECTED_RESOURCE_URL);
        for (String param: parameters.keySet()) {
            request.addQuerystringParameter(param, parameters.get(param));
        }
        service.signRequest(accessToken, request);
        Response response = request.send();

        return response.getBody();
    }

    private OAuthService service;
    private Token accessToken;

    private static final String PROTECTED_RESOURCE_URL = "https://secure.flickr.com/services/rest/";

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
                    String comments = sendRequest(params);
                    writeStringToFile(photoId, "comments", comments);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void go() {
        checkDirectory();
        authenticate();
        //System.out.println(sendRequest(Collections.singletonMap("method", "flickr.photosets.getList")));


        String setId = "72157635980974845";
        Map<String, String> params = new HashMap<String, String>();
        params.put("method", "flickr.photosets.getPhotos");
        params.put("photoset_id", setId);
        System.out.println(sendRequest(params));


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


    public static void main(String[] args)
    {
        // Replace these with your own api key and secret

/*
        Scanner in = new Scanner(System.in);

        System.out.println("=== Flickr's OAuth Workflow ===");
        System.out.println();

        // Obtain the Request Token
        System.out.println("Fetching the Request Token...");
        Token requestToken = service.getRequestToken();
        System.out.println("Got the Request Token!");
        System.out.println();

        System.out.println("Now go and authorize Scribe here:");
        String authorizationUrl = service.getAuthorizationUrl(requestToken);
        System.out.println(authorizationUrl + "&perms=read");
        System.out.println("And paste the verifier here");
        System.out.print(">>");
        Verifier verifier = new Verifier(in.nextLine());
        System.out.println();

        // Trade the Request Token and Verfier for the Access Token
        System.out.println("Trading the Request Token for an Access Token...");
        Token accessToken = service.getAccessToken(requestToken, verifier);
        System.out.println("Got the Access Token!");
        System.out.println("(if your curious it looks like this: " + accessToken + " )");
        System.out.println();

*/

        new FlickrDownloader().go();


    }
}
