package com.webstersmalley.flickomatic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by: Matthew Smalley
 * Date: 05/10/13
 */
public class Launcher {
    private static Logger logger = LoggerFactory.getLogger(Launcher.class);
    public static void main(String[] args) {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("beans.xml");
        FlickrDownloader fd = applicationContext.getBean("flickrDownloader", FlickrDownloader.class);
        if (args.length > 0) {
            logger.info("Starting download of set: {}", args[0]);
            fd.downloadSet(args[0]);
        } else {
            logger.info("Starting download of all sets");
            fd.downloadAllSets();
        }
        logger.info("Download complete");
    }
}
