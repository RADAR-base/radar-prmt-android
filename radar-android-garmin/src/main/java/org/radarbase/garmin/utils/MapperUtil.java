/*
 * Copyright (C) 2016 Vibrent Inc. All Rights Reserved.
 */

package org.radarbase.garmin.utils;

import android.util.Log;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.MapType;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

//utils class for mapping json to object and vice versa
public class MapperUtil {
    //object of ObjectMapper
    private static final ObjectMapper mapper;

    //static block to initialize ObjectMapper
    static {
        mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    //empty constructor
    private MapperUtil() {
    }

    //
    public static ObjectMapper getMapper() {
        return mapper;
    }

    public static String getJSONString(Object sourceObject) throws JsonProcessingException {
        if (sourceObject == null) {
            return null;
        }

        return new String(mapper.writeValueAsBytes(sourceObject));
    }

    /**
     * create json from input object
     * @param inputObject: input type of object
     * @return: string data in json form
     */
    public static String toJson(Object inputObject) {
        try {
            return mapper.writeValueAsString(inputObject);
        } catch (JsonProcessingException e) {
            Log.e("convert to json error", e.getMessage());
        }
        return null;
    }

    /**
     *
     * @param jsonString: json input string
     * @param outputClass: class to map to from input json string
     * @param <T>
     * @return
     */
    public static <T> T fromJson(String jsonString, Class<T> outputClass) {
        try {
            return fromJsonWithException(jsonString, outputClass);
        } catch (IOException e) {
            Log.e("convert from json error", e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * create map from input json data
     * @param jsonString: input json data
     * @param mapType: map type
     * @return: map
     */
    public static Map fromJson(String jsonString, MapType mapType) {
        if (jsonString == null) {
            return null;
        }

        try {
            return mapper.readValue(jsonString, mapType);
        } catch (IOException e) {
            Log.e("convert from json error", e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * create output class object from input string json data and handle exception
     * @param jsonString: string json data
     * @param outputClass: output class
     * @param <T>
     * @return
     * @throws IOException
     */
    public static <T> T fromJsonWithException(String jsonString, Class<T> outputClass) throws
            IOException {
        if (jsonString == null) {
            return null;
        }
        return mapper.readValue(jsonString, outputClass);
    }

    /**
     * create collection from string json data
     * @param jsonString: string json data
     * @param collectionType: collection to map string data into
     * @return
     */
    public static Collection fromJson(String jsonString, CollectionType collectionType) {
        if (jsonString == null) {
            return null;
        }
        try {
            return mapper.readValue(jsonString, collectionType);
        } catch (IOException e) {
            Log.e("convert from json error", e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * create map from json string
     * @param jsonString: json string data
     * @return: map
     */
    public static Map<String, String> getMapFromJson(String jsonString) {
        Map<String, String> map = new HashMap<>();

        // convert JSON string to Map
        try {
            map = mapper.readValue(jsonString, new TypeReference<Map<String, String>>() {
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        return map;
    }

    /**
     * create map from json string data
     * @param json: json string data
     * @return: map
     */
    public static HashMap<String, String> getJsonAsMap(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            TypeReference<Map<String, String>> typeRef = new TypeReference<Map<String, String>>() {
            };

            return mapper.readValue(json, typeRef);
        } catch (Exception e) {
            throw new RuntimeException("Couldnt parse json:" + json, e);
        }
    }
}
