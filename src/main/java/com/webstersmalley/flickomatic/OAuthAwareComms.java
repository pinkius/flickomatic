package com.webstersmalley.flickomatic;

import org.apache.commons.io.IOUtils;
import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.FlickrApi;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Scanner;

/**
 * Created by: Matthew Smalley
 * Date: 12/10/13
 */
@Service("comms")
public class OAuthAwareComms implements Comms {
    private Logger logger = LoggerFactory.getLogger(getClass());

    private boolean loggedIn = false;

    @Value("${flickomatic.api.key}")
    private String apiKey;

    @Value("${flickomatic.api.secret}")
    private String apiSecret;

    private Token accessToken;
    private OAuthService service;

    @Value("${flickomatic.api.url}")
    private String protectedUrl;

    @Value("${flickomatic.home.authfile}")
    private String authfile;

    private void getTokenFromAuthFile() {
        InputStream is = null;
        try {
            is = new FileInputStream(authfile);
            String tokenString = IOUtils.toString(is);
            if (tokenString != null) {
                accessToken = new Token(tokenString.split(",")[0], tokenString.split(",")[1]);
            }
        } catch (IOException e) {
            logger.debug("Error reading authentication file: " + e.getMessage());
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    private void saveAuthFile(String contents) {
        OutputStream os = null;
        try {
            os = new FileOutputStream(authfile);
            IOUtils.write(contents, os);
        } catch (IOException e) {
            logger.error("Error writing to authfile: " + e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(os);
        }
    }

    private void checkAuthentication() {
        // If we've got a token, let's try it
        // If not, or if the token didn't work, we'll need to ask for a new one
        logger.debug("Checking authentication");
        if (loggedIn) {
            logger.debug("Already logged in");
            return;
        }

        if (service == null) {
            logger.debug("Creating service");
            service = new ServiceBuilder().provider(FlickrApi.class).apiKey(apiKey).apiSecret(apiSecret).build();
        }
        if (checkToken()) {
            loggedIn = true;
            logger.debug("Successfully logged in");
        } else {
            logger.debug("Token missing or invalid ... will need to request a new one.");
            Token requestToken = service.getRequestToken();
            String authorizationUrl = service.getAuthorizationUrl(requestToken);
            System.out.println("Paste this into your browser: " + authorizationUrl + "&perms=read");
            System.out.print("Then paste the verification here: ");
            Verifier verifier = new Verifier(new Scanner(System.in).nextLine());

            accessToken = service.getAccessToken(requestToken, verifier);
            logger.info("Got a new token: let's take it for a spin");
            logger.info("Writing to cache");
            saveAuthFile(accessToken.getToken() + "," + accessToken.getSecret());
            if (checkToken()) {
                loggedIn = true;
                logger.debug("Successfully logged in");
            } else {
                logger.error("Can't get the new token to work. Bailing.");
                throw new RuntimeException("Authentication error");
            }
        }

    }


    private boolean checkToken() {
        if (accessToken == null) {
            logger.debug("Token is null - seeing if there's a cached token");
            getTokenFromAuthFile();
            if (accessToken == null) {
                logger.debug("No cached token found");
                return false;
            } else {
                logger.debug("Found cached token");
            }
        }
        logger.debug("Sending login test request");
        String responseAttribute = XMLUtils.getAttributeValue(sendRequest(Collections.singletonMap("method", "flickr.test.login")), "rsp", "stat");
        return ("ok".equals(responseAttribute));
    }

    public String sendRequest(Map<String, String> parameters) {
        logger.debug("Sending request {}", parameters.get("method"));
        OAuthRequest request = new OAuthRequest(Verb.GET, protectedUrl);
        for (String param: parameters.keySet()) {
            request.addQuerystringParameter(param, parameters.get(param));
        }
        service.signRequest(accessToken, request);
        Response response = request.send();

        return response.getBody();
    }


    public String sendGetRequest(Map<String, String> params) {
        checkAuthentication();
        return sendRequest(params);
    }
}
