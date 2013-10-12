package com.webstersmalley.flickomatic;

import java.util.Map;

/**
 * Created by: Matthew Smalley
 * Date: 12/10/13
 */
public interface Comms {
    String sendGetRequest(Map<String, String> params);
}
