package com.function.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JsonHelper {

    // ✅ GSON with null serialization
    private static final Gson gson = new GsonBuilder().serializeNulls().create();
    private static final Logger logger = Logger.getLogger(JsonHelper.class.getName());

    /**
     * Converts raw XML string to JsonElement using Jackson + GSON
     * Handles nested elements, arrays, and keeps null values.
     *
     * @param xmlString Raw XML input
     * @return JsonElement for further traversal
     * @throws Exception on parse error
     */
    public static JsonElement convertXmlToJsonElement(String xmlString) throws Exception {
        logger.info("[JsonHelper] Starting XML to JSON conversion...");

        try {
            // Convert XML to Map (preserves nulls)
            XmlMapper xmlMapper = new XmlMapper();
            Map<String, Object> map = xmlMapper.readValue(xmlString, new TypeReference<LinkedHashMap<String, Object>>() {});
            logger.info("[JsonHelper] XML successfully converted to Map.");

            // Convert Map to JSON string
            String jsonString = new ObjectMapper().writeValueAsString(map);
            logger.info("[JsonHelper] Map successfully converted to JSON string.");

            // Parse JSON string to JsonElement
            JsonElement element = JsonParser.parseString(jsonString);
            logger.info("[JsonHelper] JSON string successfully parsed to JsonElement.");

            // ✅ Log pretty JSON
            logger.info("[JsonHelper] Final Converted JSON:\n" + gson.toJson(element));

            return element;

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.log(Level.SEVERE, "[JsonHelper] JSON processing error: " + e.getMessage(), e);
            throw new Exception("❌ JSON processing error during XML to JSON conversion: " + e.getMessage(), e);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[JsonHelper] General error: " + e.getMessage(), e);
            throw new Exception("❌ General error during XML to JSON conversion: " + e.getMessage(), e);
        }
    }

    /**
     * Converts raw XML string to pretty printed JSON string using GSON
     *
     * @param xmlString Raw XML
     * @return JSON string (pretty format)
     * @throws Exception on failure
     */
    public static String convertXmlToJsonPretty(String xmlString) throws Exception {
        logger.info("[JsonHelper] Pretty print conversion started.");
        JsonElement element = convertXmlToJsonElement(xmlString);
        String pretty = gson.toJson(element);
        logger.info("[JsonHelper] Pretty JSON generated successfully.");
        return pretty;
    }

    public static List<JsonObject> getSafeJsonArray(JsonElement element) {
    List<JsonObject> result = new ArrayList<>();
    if (element == null || element.isJsonNull()) return result;
    if (element.isJsonObject()) result.add(element.getAsJsonObject());
    else if (element.isJsonArray()) {
        for (JsonElement item : element.getAsJsonArray()) {
            if (item.isJsonObject()) result.add(item.getAsJsonObject());
        }
    }
    return result;
}
}
