// package com.function.utils;

// import java.util.List;

// import static org.junit.jupiter.api.Assertions.assertEquals;
// import static org.junit.jupiter.api.Assertions.assertNotNull;
// import static org.junit.jupiter.api.Assertions.assertThrows;
// import static org.junit.jupiter.api.Assertions.assertTrue;
// import org.junit.jupiter.api.Test;

// import com.google.gson.JsonElement;
// import com.google.gson.JsonObject;
// import com.google.gson.JsonParser;

// class JsonHelperTest {

//     @Test
//     void testConvertXmlToJsonElement_ValidXml() throws Exception {
//         String xml = "<root><name>John</name><age>30</age></root>";
//         JsonElement jsonElement = JsonHelper.convertXmlToJsonElement(xml);

//         assertNotNull(jsonElement);
//         assertTrue(jsonElement.isJsonObject());
//         JsonObject jsonObject = jsonElement.getAsJsonObject();
//         assertEquals("John", jsonObject.get("name").getAsString());
//         assertEquals("30", jsonObject.get("age").getAsString());
//     }

//     @Test
//     void testConvertXmlToJsonElement_InvalidXml() {
//         String invalidXml = "<root><name>John</name><age>30</age>";
//         Exception exception = assertThrows(Exception.class, () -> JsonHelper.convertXmlToJsonElement(invalidXml));
//         assertTrue(exception.getMessage().contains("JSON processing error"));
//     }

//     @Test
//     void testConvertXmlToJsonPretty_ValidXml() throws Exception {
//         String xml = "<root><name>John</name><age>30</age></root>";
//         String prettyJson = JsonHelper.convertXmlToJsonPretty(xml);

//         assertNotNull(prettyJson);

//         // Parse the pretty JSON into a structured JSON object
//         JsonElement jsonElement = JsonParser.parseString(prettyJson);
//         assertTrue(jsonElement.isJsonObject());

//         JsonObject jsonObject = jsonElement.getAsJsonObject();

//         // Assert expected key values
//         assertEquals("John", jsonObject.get("name").getAsString());
//         assertEquals("30", jsonObject.get("age").getAsString());
//     }

//     @Test
//     void testGetSafeJsonArray_NullElement() {
//         List<JsonObject> result = JsonHelper.getSafeJsonArray(null);
//         assertNotNull(result);
//         assertTrue(result.isEmpty());
//     }

//     @Test
//     void testGetSafeJsonArray_JsonObject() {
//         JsonElement element = JsonParser.parseString("{\"key\": \"value\"}");
//         List<JsonObject> result = JsonHelper.getSafeJsonArray(element);

//         assertNotNull(result);
//         assertEquals(1, result.size());
//         assertEquals("value", result.get(0).get("key").getAsString());
//     }

//     @Test
//     void testGetSafeJsonArray_JsonArray() {
//         JsonElement element = JsonParser.parseString("[{\"key1\": \"value1\"}, {\"key2\": \"value2\"}]");
//         List<JsonObject> result = JsonHelper.getSafeJsonArray(element);

//         assertNotNull(result);
//         assertEquals(2, result.size());
//         assertEquals("value1", result.get(0).get("key1").getAsString());
//         assertEquals("value2", result.get(1).get("key2").getAsString());
//     }
// }
