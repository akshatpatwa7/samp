package com.function.utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DocuSignSoapSender {

    private static final Logger logger = Logger.getLogger(DocuSignSoapSender.class.getName());

    /**
     * Sends the given SOAP payload to the external REND API.
     * Reusable across PRS, HR, and Tax flows.
     *
     * @param soapPayload   The SOAP XML payload to send
     * @param correlationId Correlation ID for tracing and logs
     * @return JSON String with statusCode, responseBody, and correlationId
     */
    public static String postToRendApi(String soapPayload, String correlationId) {
        logger.log(Level.INFO, "[Utils] [SOAPPOST] Start: Sending SOAP to REND endpoint");

        try {
            // Step 1: Read environment variables for endpoint
            String rendHost = System.getenv("HTTP_DOCAPI_HOST");
            String rendPort = System.getenv("HTTP_DOCAPI_PORT");
            String rendBasePath = System.getenv("HTTP_DOCAPI_BASEPATH");
            String rendPath = System.getenv("HTTP_DOCAPI_PATH");

            // Step 2: Construct full URL
            String rendUrl = String.format("https://%s:%s%s%s", rendHost, rendPort, rendBasePath, rendPath);
            logger.log(Level.INFO, "[Utils] [SOAPPOST] REND URL: {0}", rendUrl);

            // Step 3: Create HTTP client and request
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest rendRequest = HttpRequest.newBuilder()
                    .uri(new URI(rendUrl))
                    .header("Content-Type", "application/xml")
                    .header("host", rendHost)
                    .POST(HttpRequest.BodyPublishers.ofString(soapPayload))
                    .build();

            // Step 4: Execute HTTP call
            HttpResponse<String> rendResponse = client.send(rendRequest, HttpResponse.BodyHandlers.ofString());
            int statusCode = rendResponse.statusCode();
            String responseBody = rendResponse.body();

            // Step 5: Log and return response
            logger.log(Level.INFO, "[Utils] [SOAPPOST] REND Response Code: {0}", statusCode);
            logger.log(Level.INFO, "[Utils] [SOAPPOST] REND Response Body: {0}", responseBody);

            return String.format("{\"statusCode\":%d, \"response\":\"%s\", \"correlationId\":\"%s\"}",
                    statusCode, responseBody.replaceAll("\"", "\\\""), correlationId);
        } catch (Exception ex) {
            // Step 6: Exception handling
            logger.log(Level.SEVERE, "[Utils] [SOAPPOST] Exception while calling REND API: {0}", ex.getMessage());
            return String.format("{\"error\":\"Failed to call REND API\", \"details\":\"%s\", \"correlationId\":\"%s\"}",
                    ex.getMessage().replace("\"", "'"), correlationId);
        }
    }

    /**
     * Sends a POST request with JSON payload to the Tax endpoint.
     *
     * @param url            The full endpoint URL
     * @param headers        Map of HTTP headers (includes Basic Auth, Content-Type)
     * @param jsonPayload    JSON payload to be sent
     * @param correlationId  Correlation ID for tracing
     * @return Response as a JSON string with status code and response
     */
    public static String postToTaxApi(String url, Map<String, String> headers, String jsonPayload, String correlationId) {
        logger.log(Level.INFO, "[Utils] [TaxPOST] Start: Sending POST to Tax API endpoint");

        try {
            // Step 1: Build request
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload));

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.header(entry.getKey(), entry.getValue());
            }

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = requestBuilder.build();

            // Step 2: Send request
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String responseBody = response.body();

            // Step 3: Log and return response
            logger.log(Level.INFO, "[Utils] [TaxPOST] Tax API Response Code: {0}", statusCode);
            logger.log(Level.INFO, "[Utils] [TaxPOST] Tax API Response Body: {0}", responseBody);

            return String.format("{\"statusCode\":%d, \"response\":\"%s\", \"correlationId\":\"%s\"}",
                    statusCode, responseBody.replaceAll("\"", "\\\""), correlationId);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Utils] [TaxPOST] Exception while calling Tax API: {0}", e.getMessage());
            return String.format("{\"error\":\"Exception in Tax POST\", \"details\":\"%s\", \"correlationId\":\"%s\"}",
                    e.getMessage().replace("\"", "'"), correlationId);
        }
    }
} 