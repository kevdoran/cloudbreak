package com.sequenceiq.it.util;

import java.io.IOException;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;

public class ResourceUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceUtil.class);

    private static final int RAWDATA_START = 4;

    private ResourceUtil() {
    }

    public static String readStringFromResource(ResourceLoader applicationContext, String resourceLocation) throws IOException {
        return resourceLocation.startsWith("raw:") ? resourceLocation.substring(RAWDATA_START) : new String(readResource(applicationContext, resourceLocation));
    }

    public static String readBase64EncodedContentFromResource(ResourceLoader applicationContext, String resourceLocation) throws IOException {
        return resourceLocation.startsWith("raw:") ? resourceLocation.substring(RAWDATA_START)
                : Base64.encodeBase64String(readResource(applicationContext, resourceLocation));
    }

    public static byte[] readResource(ResourceLoader applicationContext, String resourceLocation) throws IOException {
        Resource resource = applicationContext.getResource(resourceLocation);
        return StreamUtils.copyToByteArray(resource.getInputStream());
    }

    public static String readResourceAsString(ResourceLoader applicationContext, String resourceLocation) throws IOException {
        return new String(readResource(applicationContext, resourceLocation));
    }

    public static JSONObject readResourceAsJson(ResourceLoader applicationContext, String resourceLocation) throws IOException {
        try {
            return new JSONObject(readResourceAsString(applicationContext, resourceLocation));
        } catch (JSONException e) {
            LOGGER.error("Cannot read JSON from ({}) location", resourceLocation);
            return null;
        }
    }
}
