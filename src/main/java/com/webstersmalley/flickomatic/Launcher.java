package com.webstersmalley.flickomatic;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by: Matthew Smalley
 * Date: 05/10/13
 */
public class Launcher {
    public static void main(String[] args) {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("beans.xml");
        FlickrDownloader fd = applicationContext.getBean("flickrDownloader", FlickrDownloader.class);
        fd.go();
    }
}
