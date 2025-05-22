package com.function.utils;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Scanner;

import com.microsoft.azure.functions.ExecutionContext;

/**
 * Utility class to fetch DocuSign OAuth Token using JWT Grant flow.
 * Values like OAuth Host and Path are fetched from environment variables.
 */
public class DocusignOAuthTokenGeneration {

    /**
     * Fetches OAuth token by generating JWT and making HTTP request to DocuSign OAuth endpoint.
     *
     * @param userId             The DocuSign user ID (GUID) to impersonate
     * @param integratorKey      The DocuSign integrator key (client ID)
     * @param privateKeyFileName The filename of the private key (inside resources)
     * @param context            Azure Function's ExecutionContext for logging
     * @return JSON string containing access_token and isTokenValid flag
     */
    public static String fetchOAuthTokenUsingJwt(
            String userId,
            String integratorKey,
            String privateKeyFileName,
            ExecutionContext context
    ) {
        HttpURLConnection connection = null;
        Scanner scanner = null;

        try {
            // ✅ Step 1: Read OAuth endpoint from environment variables
            String oauthHost = System.getenv("DOCUSIGN_OAUTH_HOST"); // e.g. account-d.docusign.com
            String oauthPath = System.getenv("DOCUSIGN_OAUTH_PATH"); // e.g. /oauth/token

            if (oauthHost == null || oauthPath == null || userId== null || integratorKey == null) {
                context.getLogger().severe("Missing environment variables: DOCUSIGN_OAUTH_HOST, DOCUSIGN_OAUTH_PATH, userId or integratorKey");
                return "{ \"error\": \"Missing OAuth host, path, userId or integratorKey from environment\", \"isTokenValid\": false }";
                //return "{ \"error\": \"Missing OAuth host or path from environment\", \"isTokenValid\": false }";
            }

            String finalUrl = String.format("https://%s:%d%s", oauthHost, 443, oauthPath);
            context.getLogger().info("Constructed DocuSign OAuth URL: " + finalUrl);

            // ✅ Step 2: Generate JWT assertion
            context.getLogger().info("Generating JWT assertion...");
            DocusignJwtToken jwtUtil = new DocusignJwtToken();
            jwtUtil.setPrivateKey(privateKeyFileName);
            jwtUtil.setIntegratorKey(integratorKey);

            @SuppressWarnings("unchecked")
            Map<String, String> jwtData = (Map<String, String>) jwtUtil.onCall(userId, oauthHost, integratorKey);

            String assertion = jwtData.get("assertion");
            String grantType = jwtData.get("grant_type");

            if (assertion == null || grantType == null) {
                context.getLogger().severe("JWT generation failed: assertion or grant_type is null");
                return "{ \"error\": \"Missing assertion or grant_type from JWT\", \"isTokenValid\": false }";
            }

            // ✅ Step 3: Prepare and send HTTP request
            context.getLogger().info("Sending POST request to DocuSign OAuth endpoint...");
            URL url = new URL(finalUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Host", oauthHost);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);

            // ✅ Step 4: Write form-urlencoded body
            String requestBody = "grant_type=" + grantType + "&assertion=" + assertion;
            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestBody.getBytes("UTF-8"));
                os.flush();
            }

            // ✅ Step 5: Read the response
            int responseCode = connection.getResponseCode();
            context.getLogger().info("Received response from DocuSign: HTTP " + responseCode);

            scanner = new Scanner(
                    (responseCode >= 200 && responseCode < 300)
                            ? connection.getInputStream()
                            : connection.getErrorStream()
            );

            StringBuilder response = new StringBuilder();
            while (scanner.hasNextLine()) {
                response.append(scanner.nextLine());
            }

            // ✅ Step 6: Return Logic App-style response
            return String.format(
                    "{ \"DocuSignOauthToken\": %s, \"isTokenValid\": %s }",
                    response.toString(),
                    (responseCode >= 200 && responseCode < 300) ? "true" : "false"
            );

        } catch (Exception e) {
            context.getLogger().severe("Exception while generating DocuSign OAuth token: " + e.getMessage());
            return String.format(
                    "{ \"error\": \"%s\", \"isTokenValid\": false }",
                    e.getMessage().replace("\"", "'")
            );
        } finally {
            if (scanner != null) scanner.close();
            if (connection != null) connection.disconnect();
        }
    }
}
