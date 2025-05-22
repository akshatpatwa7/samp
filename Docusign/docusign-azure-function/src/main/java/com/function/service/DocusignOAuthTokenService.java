package com.function.service;
 
import com.function.model.DocusignOAuthTokenRequest;
import com.function.utils.DocusignOAuthTokenGeneration;
import com.google.gson.JsonObject;
import com.microsoft.azure.functions.ExecutionContext;
 
public class DocusignOAuthTokenService {
 
    public String generateToken(DocusignOAuthTokenRequest request, ExecutionContext context) throws Exception {
        String userId = request.getDocusignUserId();
        String integratorKey = request.getIntegratorKey();
        String basePath = request.getOauthBasePath();
 
        context.getLogger().info("[OAuthService] Generating token for user: " + userId);
 
        try {
            // ✅ Fetch private key from environment
            String privateKey = System.getenv("DOCUSIGN_PRIVATE_KEY");
            if (privateKey == null || privateKey.isEmpty()) {
                context.getLogger().severe("[OAuthService] Missing private key from environment.");
                throw new Exception("{\"statusCode\": 500, \"response\": {\"error\": \"Missing private key in environment\"}}");
            }
 
            // ✅ Generate token via utility
            String rawResponse = DocusignOAuthTokenGeneration.fetchOAuthTokenUsingJwt(
                userId,
                integratorKey,
                privateKey,
                context
            );
 
            context.getLogger().info("[OAuthService] OAuth token response received.");
            return rawResponse;
 
        } catch (Exception ex) {
            context.getLogger().severe("[OAuthService] Exception while generating token: " + ex.getMessage());
 
            // ✅ If already structured, pass as-is; else wrap as 500
            try {
                JsonObject tryParse = com.google.gson.JsonParser.parseString(ex.getMessage()).getAsJsonObject();
                if (tryParse.has("statusCode") && tryParse.has("response")) {
                    throw new Exception(ex.getMessage());
                }
            } catch (Exception ignored) {
                // Not structured JSON, fallback to wrapped error
            }
 
            String fallbackJson = String.format(
                "{\"statusCode\": 500, \"response\": {\"error\": \"Internal Error during DocuSign token generation\", \"details\": \"%s\"}}",
                ex.getMessage().replace("\"", "'")
            );
            throw new Exception(fallbackJson, ex);
        }
    }
}