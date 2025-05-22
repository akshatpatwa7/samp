package com.function.service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

import com.function.utils.DocusignOAuthTokenGeneration;
import com.microsoft.azure.functions.ExecutionContext;

public class DocusignServiceEnvelopes {

    public String handleEnvelopeRequest(Map<String, String> headers, String body, ExecutionContext context) {
        String correlationId = UUID.randomUUID().toString();
        context.getLogger().info(() -> "[DocusignServiceEnvelopes] - Correlation ID: " + correlationId);

        try {
            context.getLogger().info(() -> "[START] Step 1: Extract accountId");
            String accountId = headers.getOrDefault("accountid", System.getenv("DocuSignHRLOAAccountId"));
            String docusignApiPath = "/accounts/" + accountId + "/envelopes";
            context.getLogger().info(() -> "[END] Step 1: Extracted accountId: " + accountId);

            context.getLogger().info(() -> "[START] Step 2: Validate Azure token");
            String azureTokenValidationUrl = "https://int.apiqa.worldbank.org/azure/v2.0/validate-token";
            String resourceId = System.getenv("docusign.services.esb.azuretoken.resource.id");

            HttpURLConnection tokenConn = (HttpURLConnection) new URL(azureTokenValidationUrl).openConnection();
            tokenConn.setRequestMethod("GET");
            tokenConn.setRequestProperty("resource", resourceId);
            tokenConn.setRequestProperty("Authorization", headers.get("Authorization"));

            int tokenResponseCode = tokenConn.getResponseCode();
            Scanner tokenScanner = new Scanner(
                (tokenResponseCode >= 200 && tokenResponseCode < 300) ? tokenConn.getInputStream() : tokenConn.getErrorStream()
            );
            StringBuilder tokenResponse = new StringBuilder();
            while (tokenScanner.hasNextLine()) {
                tokenResponse.append(tokenScanner.nextLine());
            }
            tokenScanner.close();
            context.getLogger().info(() -> "Azure Token Validation Response Code: " + tokenResponseCode);
            context.getLogger().info(() -> "Azure Token Validation Response Body: " + tokenResponse);
            context.getLogger().info(() -> "[END] Step 2: Token validation completed");

            if (!tokenResponse.toString().contains("\"isTokenValid\":true")) {
                return String.format("{\"error\": \"Invalid Azure Token\", \"statusCode\": %d, \"correlationId\": \"%s\"}", tokenResponseCode, correlationId);
            }

            context.getLogger().info(() -> "[START] Step 3: Generate DocuSign OAuth Token");
            String userId = headers.getOrDefault("DocusignUserID", System.getenv("DocuSignHRLOAUserId"));
            String integratorKey = System.getenv("docusign.services.integrator_key");
            String privateKey = System.getenv("DOCUSIGN_PRIVATE_KEY");

            String oauthResponse = DocusignOAuthTokenGeneration.fetchOAuthTokenUsingJwt(userId, integratorKey, privateKey, context);
            context.getLogger().info(() -> "OAuth Response: " + oauthResponse);
            String accessToken = extractAccessToken(oauthResponse);
            context.getLogger().info(() -> "[END] Step 3: Access token extraction completed");

            if (accessToken == null) {
                return String.format("{\"error\": \"OAuth Token Invalid\", \"details\": %s, \"correlationId\": \"%s\"}", oauthResponse, correlationId);
            }

            context.getLogger().info(() -> "[START] Step 4: Call DocuSign Envelope API");
            String host = System.getenv("docusign.services.http.host");
            String port = System.getenv("docusign.services.http.port");
            String basePath = System.getenv("docusign.services.basepath");
            String uri = String.format("https://%s:%s%s%s", host, port, basePath, docusignApiPath);

            context.getLogger().info(() -> "DocuSign Envelope API URI: " + uri);
            HttpURLConnection conn = (HttpURLConnection) new URL(uri).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Host", host);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes("UTF-8"));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            Scanner scanner = new Scanner(
                (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream()
            );
            StringBuilder response = new StringBuilder();
            while (scanner.hasNextLine()) {
                response.append(scanner.nextLine());
            }
            scanner.close();
            context.getLogger().info(() -> "DocuSign API Response Code: " + responseCode);
            context.getLogger().info(() -> "DocuSign API Response Body: " + response);
            context.getLogger().info(() -> "[END] Step 4: DocuSign envelope API call completed");

            context.getLogger().info(() -> "[START] Step 5: Prepare and return final response");
            if (responseCode == 200 || responseCode == 201) {
                context.getLogger().info(() -> "[END] Step 5: Successful DocuSign call");
                return String.format("{\"statusCode\": %d, \"body\": %s, \"correlationId\": \"%s\"}", responseCode, response, correlationId);
            } else {
                context.getLogger().info(() -> "[END] Step 5: Failed DocuSign call");
                return String.format("{\"statusCode\": %d, \"error\": %s, \"correlationId\": \"%s\"}", responseCode, response, correlationId);
            }

        } catch (Exception ex) {
            context.getLogger().severe(() -> "Exception: " + ex.getMessage());
            return String.format("{\"error\": \"Internal Server Error\", \"details\": \"%s\", \"correlationId\": \"%s\"}", ex.getMessage().replace("\"", "'"), correlationId);
        }
    }

    private String extractAccessToken(String jsonResponse) {
        try {
            int index = jsonResponse.indexOf("\"access_token\":\"");
            if (index == -1) return null;
            int start = index + 17;
            int end = jsonResponse.indexOf("\"", start);
            return jsonResponse.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }
}
