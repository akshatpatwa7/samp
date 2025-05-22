package com.function.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import com.function.model.DocusignOAuthTokenRequest;
import com.function.service.DocusignConnectService;
import com.function.service.DocusignOAuthTokenService;
import com.function.service.PropertiesService;
import com.function.service.DocusignPayloadService;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.function.service.DocusignServiceEnvelopes;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.function.service.ServiceBusQueuePublisherService;

public class DocusignControllerFunction {

    private final DocusignOAuthTokenService tokenService = new DocusignOAuthTokenService();private final PropertiesService propertiesService = new PropertiesService();
    private final DocusignPayloadService payloadService = new DocusignPayloadService();
    private final DocusignConnectService connectService = new DocusignConnectService(); // Docusign Conenct Service Class


    @FunctionName("generateDocusignOAuthToken")
    public HttpResponseMessage generateOAuthToken(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
                    HttpRequestMessage<Optional<DocusignOAuthTokenRequest>> request,
            final ExecutionContext context) {
    
        Logger logger = context.getLogger();
        logger.info("[generateDocusignOAuthToken] Generating OAuth token using JWT - Function triggered.");
    
        DocusignOAuthTokenRequest tokenRequest = request.getBody().orElse(null);
        if (tokenRequest == null ||
                tokenRequest.getDocusignUserId() == null ||
                tokenRequest.getOauthBasePath() == null ||
                tokenRequest.getIntegratorKey() == null) {
    
            logger.severe("[generateDocusignOAuthToken] Generating OAuth token using JWT - Missing required parameters.");
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Missing required parameters: docusignUserId, oauthBasePath, or integratorKey");
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(errorResponse)
                    .build();
        }
    
        try {
            logger.info("[generateDocusignOAuthToken] Generating OAuth token using JWT - Starting generation process...");
            String oauthResponse = tokenService.generateToken(tokenRequest, context);
            logger.info("[generateDocusignOAuthToken] Generating OAuth token using JWT - Token generated successfully.");
    
            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(oauthResponse)
                    .build();
    
        } catch (Exception e) {
            logger.severe("[generateDocusignOAuthToken] Generating OAuth token using JWT - Exception occurred: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body(e.getMessage())
                    .build();
        }
    }


    @FunctionName("generateDocusignDatabasePayload")
    public HttpResponseMessage generateDocusignDatabasePayload(
            @HttpTrigger(
                    name = "generateDocusignPayloadReq",
                    methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.FUNCTION,
                    route = "docusign/generate-docusign-database-payload"
            )
            HttpRequestMessage<Optional<Map<String, Object>>> request,
            final ExecutionContext context
    ) {
        Logger logger = context.getLogger();
        logger.info("[generateDocusignDatabasePayload] Function triggered.");

        try {
            Map<String, Object> requestBody = request.getBody().orElse(new HashMap<>());

            Object dvresponseObj = requestBody.getOrDefault("dvresponse", new HashMap<String, String>());
            Object docusignReportObj = requestBody.getOrDefault("docusignReport", new HashMap<String, Object>());

            Map<String, String> dvresponse = new HashMap<>();
            Map<String, Object> docusignReport = new HashMap<>();

            if (dvresponseObj instanceof Map<?, ?>) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) dvresponseObj).entrySet()) {
                    dvresponse.put(entry.getKey().toString(), entry.getValue() != null ? entry.getValue().toString() : null);
                }
            }

            if (docusignReportObj instanceof Map<?, ?>) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) docusignReportObj).entrySet()) {
                    docusignReport.put(entry.getKey().toString(), entry.getValue());
                }
            }

            Object result = payloadService.generatePayload(dvresponse, docusignReport);

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(result)
                    .build();

        } catch (Exception e) {
            logger.severe("[generateDocusignDatabasePayload] Exception: " + e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Internal server error while generating payload.");
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body(errorResponse)
                    .build();
        }
    }

    @FunctionName("PostDocuSignEnvelope")
    public HttpResponseMessage postEnvelope(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.POST},
            route = "docusign-service/envelopes",
            authLevel = AuthorizationLevel.FUNCTION
        )
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context
    ) {
        String correlationId = UUID.randomUUID().toString();
        context.getLogger().info("[Controller] - Correlation ID: " + correlationId);
        context.getLogger().info("[Controller] - Received request at /docusign-service/envelopes");

        try {
            // Step 1: Extract request body
            String requestBody = request.getBody().orElse("");
            context.getLogger().info("[Controller] - Request body extracted. Length: " + requestBody.length());

            // Step 2: Extract and normalize headers
            Map<String, String> headersMap = new HashMap<>();
            request.getHeaders().forEach((key, value) -> {
                String normalizedKey = key.toLowerCase();
                headersMap.put(normalizedKey, value);
                context.getLogger().info("[Controller] - Header: " + normalizedKey + " = " + value);
            });

            // Step 3: Call the service
            context.getLogger().info("[Controller] - Calling DocusignServiceEnvelopes...");
            DocusignServiceEnvelopes docusignServiceEnvelopes = new DocusignServiceEnvelopes();
            String response = docusignServiceEnvelopes.handleEnvelopeRequest(headersMap, requestBody, context);
            context.getLogger().info("[Controller] - Service call completed.");

            // Step 4: Return 200 OK
            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-ID", correlationId)
                    .body(response)
                    .build();

        } catch (Exception ex) {
            // Step 5: Exception handling
            context.getLogger().severe("[Controller] - Exception occurred: " + ex.getMessage());
            String errorResponse = String.format("{\"error\": \"Unhandled Exception\", \"details\": \"%s\", \"correlationId\": \"%s\"}",
                    ex.getMessage().replace("\"", "'"), correlationId);

            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-ID", correlationId)
                    .body(errorResponse)
                    .build();
        }
    }

    @FunctionName("PostDocuSignConnect")
    public HttpResponseMessage postConnect(
        @HttpTrigger(
            name = "connectReq",
            methods = {HttpMethod.POST},
            route = "docusign-connect",
            authLevel = AuthorizationLevel.FUNCTION
        )
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context
    ) {
        String correlationId = UUID.randomUUID().toString();
        context.getLogger().info(() -> "[Controller] - Correlation ID: " + correlationId);
        context.getLogger().info("[Controller] - Received request at /docusign-connect");
    
        try {
            // Step 1: Extract body and query param
            String requestBody = request.getBody().orElse("");
            String accountId = request.getQueryParameters().getOrDefault("accountId", "");
            context.getLogger().info(() -> "[Controller] - XML Length: " + requestBody.length());
            context.getLogger().info(() -> "[Controller] - Query Param accountId: " + accountId);
    
            // Step 2: Normalize headers
            Map<String, String> headersMap = new HashMap<>();
            request.getHeaders().forEach((key, value) -> {
                String normalizedKey = key.toLowerCase();
                headersMap.put(normalizedKey, value);
                context.getLogger().info(() -> "[Controller] - Header: " + normalizedKey + " = " + value);
            });
    
            // Step 2.1: Inject accountId into header map if missing
            if (!headersMap.containsKey("accountId") && !accountId.isEmpty()) {
                headersMap.put("accountId", accountId);
                context.getLogger().info("[Controller] - Injected accountid into headers " + accountId);
            }
    
            // Step 3: Call service
            context.getLogger().info("[Controller] - Calling service layer...");
            String response = connectService.handleConnectRequest(requestBody, headersMap, context);
    
            // Step 4: Check if service returned a structured JSON with statusCode + response
            try {
                JsonObject responseJson = com.google.gson.JsonParser.parseString(response).getAsJsonObject();
    
                if (responseJson.has("statusCode") && responseJson.has("response")) {
                    int statusCode = responseJson.get("statusCode").getAsInt();
                    JsonElement errorBody = responseJson.get("response");
    
                    return request.createResponseBuilder(HttpStatus.valueOf(statusCode))
                            .header("Content-Type", "application/json")
                            .header("X-Correlation-ID", correlationId)
                            .body(errorBody.toString())
                            .build();
                }
            } catch (Exception parseErr) {
                context.getLogger().warning("[Controller] - No structured error JSON, defaulting to 200");
            }
    
            // Step 5: Default 200 success
            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-ID", correlationId)
                    .body(response)
                    .build();
    
        } catch (Exception e) {
            context.getLogger().severe(() -> "[Controller] - Unhandled Exception: " + e.getMessage());
        
            try {
                // Try parsing the message if it's a structured JSON error thrown from service layer
                JsonObject parsed = com.google.gson.JsonParser.parseString(e.getMessage()).getAsJsonObject();
                int statusCode = parsed.has("statusCode") ? parsed.get("statusCode").getAsInt() : 500;
                JsonElement errorResponse = parsed.has("response") ? parsed.get("response") : parsed;
        
                context.getLogger().severe(() -> "[Controller] - Structured Error Response: " + errorResponse.toString());
        
                return request.createResponseBuilder(HttpStatus.valueOf(statusCode))
                        .header("Content-Type", "application/json")
                        .header("X-Correlation-ID", correlationId)
                        .body(errorResponse.toString())
                        .build();
        
            } catch (Exception inner) {
                // Fallback: Plain error if not parseable
                context.getLogger().severe(() -> "[Controller] - Failed to parse structured error: " + inner.getMessage());
        
                String fallback = String.format(
                    "{\"error\": \"Internal Server Error\", \"details\": \"%s\", \"correlationId\": \"%s\"}",
                    e.getMessage().replace("\"", "'"),
                    correlationId
                );
        
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .header("Content-Type", "application/json")
                        .header("X-Correlation-ID", correlationId)
                        .body(fallback)
                        .build();
            }}
        }

        //=====

    @FunctionName("PostServiceBusMessage")
    public HttpResponseMessage postToServiceBus(
        @HttpTrigger(
            name = "serviceBusReq",
            methods = {HttpMethod.POST},
            route = "send-servicebus-message",
            authLevel = AuthorizationLevel.FUNCTION
        )
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context
    ) {
        // Step 1: Generate correlation ID and log request arrival
        String correlationId = UUID.randomUUID().toString();
        context.getLogger().info("[Controller] [Step 1] Correlation ID generated: " + correlationId);
        context.getLogger().info("[Controller] [Step 1] Received request at /send-servicebus-message");

        try {
            // Step 2: Extract the incoming request body
            String messageBody = request.getBody().orElse("");
            context.getLogger().info("[Controller] [Step 2] Message body extracted. Length: " + messageBody.length());

            // Step 3: Call the ServiceBusQueuePublisherService to publish the message
            context.getLogger().info("[Controller] [Step 3] Calling ServiceBusQueuePublisherService...");
            String publishResult = ServiceBusQueuePublisherService.publishMessage(messageBody, context.getLogger());

            // Step 4: Log the result and return a 200 OK response
            context.getLogger().info("[Controller] [Step 4] Message publishing completed. Result: " + publishResult);

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-ID", correlationId)
                    .body(publishResult)
                    .build();

        } catch (Exception ex) {
            // Step 5: Handle exceptions and return 500 with error details
            context.getLogger().severe("[Controller] [Step 5] Exception occurred while publishing message: " + ex.getMessage());
            String errorResponse = String.format("{\"error\":\"Exception while publishing to Service Bus\",\"details\":\"%s\",\"correlationId\":\"%s\"}",
                    ex.getMessage().replace("\"", "'"), correlationId);

            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .header("X-Correlation-ID", correlationId)
                    .body(errorResponse)
                    .build();
        }
    }
}