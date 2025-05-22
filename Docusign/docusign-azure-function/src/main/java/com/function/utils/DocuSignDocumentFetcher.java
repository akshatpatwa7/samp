package com.function.utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DocuSignDocumentFetcher {

    private static final Logger logger = Logger.getLogger(DocuSignDocumentFetcher.class.getName());

    /**
     * Downloads and base64-encodes the combined document from DocuSign using the standard Logic App behavior.
     * Reusable across PRS, HR, and Tax flows.
     *
     * @param docusignApiHost DocuSign API host (e.g., DEMO.DOCUSIGN.NET)
     * @param accountId       DocuSign account ID
     * @param envelopeId      Envelope ID to fetch the signed document for
     * @param accessToken     Bearer token for authorization
     * @param correlationId   Correlation ID for logging
     * @return Base64-encoded PDF as String
     * @throws Exception if the document could not be downloaded
     */
    public static String downloadAndEncodeCombinedDocument(String accountId, String envelopeId, String accessToken, String correlationId) throws Exception {
        logger.log(Level.INFO, "[Utils] [DocDownload] Start: Preparing HTTP GET to fetch combined signed document");

        try {
        logger.info("[Utils] [DocDownload] Start: Preparing HTTP GET to fetch combined signed document");
 
        String docusignApiHost = System.getenv("DOCUSIGN_HTTP_HOST");
        String docusignApiPort = System.getenv("DOCUSIGN_HTTP_PORT");
        String docusignApiBasepath = System.getenv("DOCUSIGN_BASEPATH");
        String docusignRelativeApiPath = System.getenv("DOCUSIGN_RELATIVE_API_PATH");
 
        if (docusignApiHost == null || docusignApiPort == null || docusignApiBasepath == null || docusignRelativeApiPath == null) {
            logger.severe("[Utils] [DocDownload] Missing environment variables for DocuSign API");
            throw new Exception("Missing environment variables for DocuSign API");
        }
 
        // ✅ Build final document URL
        String documentUrl = String.format(
            "https://%s:%s%s%s/%s/envelopes/%s/documents/combined",
            docusignApiHost,
            docusignApiPort,
            docusignApiBasepath,
            docusignRelativeApiPath,
            accountId,
            envelopeId
        );
 
        logger.info("[Utils] [DocDownload] Request URL: " + documentUrl);
 
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest docRequest = HttpRequest.newBuilder()
                .uri(new URI(documentUrl))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Transfer-Encoding", "base64")
                .GET()
                .build();
 
        HttpResponse<byte[]> docResponse = client.send(docRequest, HttpResponse.BodyHandlers.ofByteArray());
 
        int statusCode = docResponse.statusCode();
        String responseBody = new String(docResponse.body());
 
        logger.info("[Utils] [DocDownload] Response status: " + statusCode);
 
        if (statusCode != 200) {
            logger.severe("[Utils] [DocDownload] Failed to download document. HTTP " + statusCode);
            logger.severe("[Utils] [DocDownload] Error Response Body:\n" + responseBody);
            // ✅ Return original response JSON (assuming DocuSign sends JSON)
            throw new Exception(statusCode +"::"+ responseBody.trim());
        }
 
        String base64File = Base64.getEncoder().encodeToString(docResponse.body());
        logger.info("[Utils] [DocDownload] Success: Document base64 encoded. Size: " + docResponse.body().length + " bytes");
 
        return base64File;
 
    } catch (Exception ex) {
        logger.severe("[Utils] [DocDownload] Exception occurred while fetching document: " + ex.getMessage());
        throw new Exception(ex.getMessage(), ex);
    }
    }
} 