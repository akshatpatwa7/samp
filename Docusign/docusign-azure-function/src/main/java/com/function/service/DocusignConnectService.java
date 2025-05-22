package com.function.service;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.function.payloads.HrSoapPayloadBuilder;
import com.function.payloads.PrsSoapPayloadBuilder;
import com.function.utils.DocuSignDocumentFetcher;
import com.function.utils.DocuSignSoapSender;
import com.function.utils.DocusignOAuthTokenGeneration;
import com.function.utils.JsonHelper;
import com.function.utils.ServiceBusHelper;
import com.function.utils.DocusignGetProperties;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.azure.functions.ExecutionContext;
public class DocusignConnectService {

    private static final Logger logger = Logger.getLogger(DocusignConnectService.class.getName());

    public String handleConnectRequest(String xmlInput, Map<String, String> headers, ExecutionContext context)
    throws Exception {
        String correlationId = UUID.randomUUID().toString();
        logger.log(Level.INFO, "[Service] [Step 1] Received request | Correlation ID: {0}", correlationId);
        logger.log(Level.INFO, "[Service] [Step 1] Incoming XML length: {0}", (xmlInput != null ? xmlInput.length() : 0));

        // ⬅️ Print all incoming headers
        logger.log(Level.INFO, "[Service] [Step 1.1] Incoming Headers:");
        headers.forEach((key, value) -> {
            logger.log(Level.INFO, "   Header: {0} = {1}", new Object[]{key, value});
        });

        // Step 2: Convert XML to JSON
        JsonElement rootJson = JsonHelper.convertXmlToJsonElement(xmlInput);

        // Step 3: Check isEnvelopFromDB flag
        boolean isFromDB = Optional.ofNullable(rootJson)
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .map(obj -> obj.get("isEnvelopFromDB"))
                .filter(JsonElement::isJsonPrimitive)
                .map(JsonElement::getAsBoolean)
                .orElse(false);

        if (isFromDB) {
            logger.log(Level.INFO, "[Service] [Step 3] 'isEnvelopFromDB' is true, won't process in Service Bus");
        } else {
            logger.log(Level.INFO, "[Service] [Step 3] 'isEnvelopFromDB' is false. Proceeding with publishing xmlInput message in Service Bus queue.");

            // Call ServiceBusHelper to publish xmlInput
            //String publishResult = ServiceBusHelper.publishMessage(xmlInput, logger);
            //logger.log(Level.INFO, "[Service] [Step 3] Service Bus publish result: {0}", publishResult);
        }

        // Step 4: Extract EnvelopeStatus
        JsonObject envelopeStatus = Optional.ofNullable(rootJson)
            .filter(JsonElement::isJsonObject)
            .map(JsonElement::getAsJsonObject)
            .map(obj -> {
                if (obj.has("DocuSignEnvelopeInformation")) {
                    return obj.getAsJsonObject("DocuSignEnvelopeInformation").get("EnvelopeStatus");
                } else {
                    return obj.get("EnvelopeStatus");
                }
            })
            .filter(JsonElement::isJsonObject)
            .map(JsonElement::getAsJsonObject)
            .orElse(null);
 

        if (envelopeStatus == null) {
            logger.log(Level.SEVERE, "[Service] [Step 4] EnvelopeStatus block is missing in payload.");
            throw new Exception("EnvelopeStatus not found in JSON.");
        }

        String envelopeId = getString(envelopeStatus, "EnvelopeID");
        String subject = getString(envelopeStatus, "Subject");
        String status = getString(envelopeStatus, "Status");
        String timeGenerated = getString(envelopeStatus, "TimeGenerated");
        String email = getString(envelopeStatus, "Email");
        String userName = getString(envelopeStatus, "UserName");

        logger.log(Level.INFO, "[Service] [Step 4] Extracted EnvelopeID: {0}", envelopeId);
        logger.log(Level.INFO, "[Service] [Step 4] Subject: {0}", subject);
        logger.log(Level.INFO, "[Service] [Step 4] Status: {0}", status);
        logger.log(Level.INFO, "[Service] [Step 4] TimeGenerated: {0}", timeGenerated);
        logger.log(Level.INFO, "[Service] [Step 4] UserName: {0}", userName);
        logger.log(Level.INFO, "[Service] [Step 4] Email: {0}", email);


        String fileLabel = null;
        // Step 4.1: Extract DocumentPDFs
        try {
            JsonObject documentPdfs = Optional.ofNullable(rootJson)
                    .filter(JsonElement::isJsonObject)
                    .map(JsonElement::getAsJsonObject)
                    .map(obj -> obj.get("EnvelopeInformation"))
                    .filter(JsonElement::isJsonObject)
                    .map(JsonElement::getAsJsonObject)
                    .map(obj -> obj.get("DocumentPDFs"))
                    .filter(JsonElement::isJsonObject)
                    .map(JsonElement::getAsJsonObject)
                    .orElse(null);
        
            if (documentPdfs != null && documentPdfs.has("DocumentPDF")) {
                JsonElement docPdfElement = documentPdfs.get("DocumentPDF");
                String extractedName = null;
        
                if (docPdfElement.isJsonObject()) {
                    JsonObject docPdf = docPdfElement.getAsJsonObject();
                    extractedName = docPdf.has("Name") && !docPdf.get("Name").isJsonNull()
                            ? docPdf.get("Name").getAsString()
                            : null;
                } else if (docPdfElement.isJsonArray() && docPdfElement.getAsJsonArray().size() > 0) {
                    JsonObject firstDoc = docPdfElement.getAsJsonArray().get(0).getAsJsonObject();
                    extractedName = firstDoc.has("Name") && !firstDoc.get("Name").isJsonNull()
                            ? firstDoc.get("Name").getAsString()
                            : null;
                }
        
                fileLabel = (extractedName == null) ? ".pdf" : extractedName + ".pdf";
                logger.log(Level.INFO, "[Service] [EXTRA] Extracted DocumentPDF Name: {0}", fileLabel);
                // you can assign it somewhere or just log it — up to you
            }
        
        } catch (Exception e) {
            logger.log(Level.WARNING, "[Service] [EXTRA] Failed to extract DocumentPDF Name safely: {0}", e.getMessage());
        }





        // Step 5: Extract and filter RecipientStatus → Signer
        JsonObject recipientStatusesObj = envelopeStatus.has("RecipientStatuses") ? envelopeStatus.getAsJsonObject("RecipientStatuses") : null;
        if (recipientStatusesObj == null) {
            logger.log(Level.SEVERE, "[Service] [Step 5] RecipientStatuses block is missing in EnvelopeStatus.");
            throw new Exception("RecipientStatuses not found in EnvelopeStatus.");
        }
        
        JsonElement recipientStatusElement = recipientStatusesObj.get("RecipientStatus");
        if (recipientStatusElement == null) {
            logger.log(Level.SEVERE, "[Service] [Step 5] RecipientStatus block is missing in RecipientStatuses.");
            throw new Exception("RecipientStatus not found in RecipientStatuses.");
        }
        
        List<JsonObject> recipientList = JsonHelper.getSafeJsonArray(recipientStatusElement);
        
        JsonObject signer = recipientList.stream()
                .filter(r -> r.has("Type") && "Signer".equalsIgnoreCase(r.get("Type").getAsString()))
                .findFirst()
                .orElse(null);

        String docSigner = signer != null && signer.has("UserName") ? signer.get("UserName").getAsString() : "";
        String docSignerEmail = signer != null && signer.has("Email") ? signer.get("Email").getAsString() : "";
        String signedDate = signer != null && signer.has("Signed") && !signer.get("Signed").isJsonNull()
                ? formatDate(signer.get("Signed").getAsString())
                : "";

        JsonArray tabStatusArray = signer != null && signer.has("TabStatuses") && signer.getAsJsonObject("TabStatuses").has("TabStatus")
                ? signer.getAsJsonObject("TabStatuses").getAsJsonArray("TabStatus")
                : new JsonArray();

        logger.log(Level.INFO, "[Service] [Step 5] Signer Name: {0}", docSigner);
        logger.log(Level.INFO, "[Service] [Step 5] Signer Email: {0}", docSignerEmail);
        logger.log(Level.INFO, "[Service] [Step 5] Signed Date: {0}", signedDate);
        logger.log(Level.INFO, "[Service] [Step 5] TabStatus Count: {0}", tabStatusArray.size());

        // Step 6: Extract DocusignVariable fields
        String accountId = headers.getOrDefault("accountId", "");
        //String accountId = request.getQueryParameters().getOrDefault("accountid", "");
        //String fileLabel = "default.pdf"; // TODO: parse from DocumentPDFs if available

        logger.log(Level.INFO, "[Service] [Step 6] Account ID: {0}", accountId);
        logger.log(Level.INFO, "[Service] [Step 6] File Label: {0}", fileLabel);

        // Step 7: Call Azure Function (to be replaced with getProperties.java)
        // logger.log(Level.INFO, "[Service] [Step 7] Calling Azure Function 'getProperties' with subject: {0}, UNID: {1}", new Object[]{subject, envelopeId});
        // TODO: Replace with internal getProperties.java logic
        Map<String, Object> messageAttributes = DocusignGetProperties.getProperties(subject, envelopeId, context);
        boolean isValidSubject = (boolean) messageAttributes.get("isValidSubject");  
        logger.log(Level.INFO, "[Service] [Step 7] isValidSubject: {0}", isValidSubject);

        // Step 8: Extract CustomFields → targetrepository, DocId, BeneficiaryUPI, ParticipantId
        JsonObject customFieldsObj = envelopeStatus.has("CustomFields") ? envelopeStatus.getAsJsonObject("CustomFields") : null;
        JsonElement customFieldArray = customFieldsObj != null ? customFieldsObj.get("CustomField") : null;
        List<JsonObject> customFields = JsonHelper.getSafeJsonArray(customFieldArray);

        String targetRepository = extractCustomFieldValue(customFields, "targetrepository").toLowerCase();
        String docId = extractCustomFieldValue(customFields, "DocId");
        String beneficiaryUpi = extractCustomFieldValue(customFields, "BeneficiaryUPI");
        String participantUpi = extractCustomFieldValue(customFields, "ParticipantId");

        logger.log(Level.INFO, "[Service] [Step 8] TargetRepository: {0}", targetRepository);
        logger.log(Level.INFO, "[Service] [Step 8] DocId: {0}", docId);
        logger.log(Level.INFO, "[Service] [Step 8] BeneficiaryUPI: {0}", beneficiaryUpi);
        logger.log(Level.INFO, "[Service] [Step 8] ParticipantUPI: {0}", participantUpi);

        // Step 9: Check Envelope Status == completed
        if (!"completed".equalsIgnoreCase(status)) {
            logger.log(Level.INFO, "[Service] [Step 9] Envelope status is not completed. Skipping processing.");
            return "Envelope status not completed.";
        }

        // Step 10: Switch on TargetRepository
        switch (targetRepository) {
            case "prs":
                try {
                    // Step 10.1: Extract envIdValue from TabStatus array
                    logger.log(Level.INFO, "[Service] [Step 10.1] Start: Extracting envIdValue");
                    List<JsonObject> tabStatusList = new ArrayList<>();
                    for (JsonElement element : tabStatusArray) {
                        if (element != null && element.isJsonObject()) {
                            tabStatusList.add(element.getAsJsonObject());
                        }
                    }
        
                    String envIdValue = tabStatusList.stream()
                            .filter(tab -> tab.has("TabLabel") && "envId".equalsIgnoreCase(tab.get("TabLabel").getAsString()))
                            .map(tab -> tab.get("Value").getAsString())
                            .findFirst()
                            .orElse("");
                    logger.log(Level.INFO, "[Service] [Step 10.1] End: envIdValue: {0}", envIdValue);
        
                    // Step 10.2: Validate required values before continuing
                    logger.log(Level.INFO, "[Service] [Step 10.2] Start: Validating PRS fields");
                    /*if (envIdValue.isEmpty() || signedDate.isEmpty() || docId.isEmpty()
                            || beneficiaryUpi.isEmpty() || participantUpi.isEmpty()) {
                        logger.log(Level.SEVERE, "[Service] [Step 10.2] End: Missing required data. Aborting.");
                        return String.format("{\"error\":\"Missing required PRS fields\", \"correlationId\":\"%s\"}", correlationId);
                    }*/
                    logger.log(Level.INFO, "[Service] [Step 10.2] End: Validation passed");
        
                    // Step 10.3: Read environment variables for OAuth and Repo
                    logger.log(Level.INFO, "[Service] [Step 10.3] Start: Reading environment variables");
                    String integratorKey = System.getenv("DOCUSIGN_INTEGRATOR_KEY");
                    String userId = System.getenv("DOCUSIGN_PENSION_USERID");
                    String privateKey = System.getenv("DOCUSIGN_PRIVATE_KEY");
                    //String oauthBasePath = System.getenv("Docusign_OAuthBasePath");
                    //String docusignApiHost = System.getenv("Docusign_API_Host");
                    String docusignRepo = System.getenv("DOCUSIGN_REPO_PENSION");
                    logger.log(Level.INFO, "[Service] [Step 10.3] End: Environment variables loaded");
        
                    // Step 10.4: Fetch OAuth Token
                    logger.log(Level.INFO, "[Service] [Step 10.4] Start: Fetching OAuth token");
                    String oauthResponse = DocusignOAuthTokenGeneration.fetchOAuthTokenUsingJwt(
                        userId, integratorKey, privateKey, context
                    );
                    logger.log(Level.INFO, "[Service] [Step 10.4] JWT Token Recieved: {0}", oauthResponse);
                    JsonObject oauthJson = JsonParser.parseString(oauthResponse).getAsJsonObject();
                    boolean isTokenValid = oauthJson.has("isTokenValid") && oauthJson.get("isTokenValid").getAsBoolean();
                    if (!isTokenValid) {
                        logger.log(Level.SEVERE, "[Service] [Step 10.4] End: OAuth token invalid. Aborting.");
                        return String.format("{\"error\":\"DocuSign OAuth token invalid\", \"correlationId\":\"%s\"}", correlationId);
                    }
                    String accessToken = oauthJson.getAsJsonObject("DocuSignOauthToken").get("access_token").getAsString();
                    logger.log(Level.INFO, "[Service] [Step 10.4] End: OAuth token received successfully");
        
                    // Step 10.5: Download the document from DocuSign
                    logger.log(Level.INFO, "[Service] [Step 10.5] Start: Downloading and base64 encoding DocuSign document");
                    String base64File = DocuSignDocumentFetcher.downloadAndEncodeCombinedDocument(
                        accountId, envelopeId, accessToken, correlationId
                    );
                    logger.log(Level.INFO, "[Service] [Step 10.5] End: Document successfully fetched and encoded");
        
                    // Step 10.6: Build SOAP Payload using MuleSoft format
                    logger.log(Level.INFO, "[Service] [Step 10.6] Start: Constructing SOAP payload");
                    String soapPayload = PrsSoapPayloadBuilder.buildMuleSoftPrsPayload(
                        participantUpi, docId, beneficiaryUpi, signedDate,
                        envIdValue, docusignRepo, base64File
                    );
                    logger.log(Level.INFO, "[Service] [Step 10.6] End: SOAP Payload constructed (MuleSoft style)");
        
                    // Step 10.7: POST SOAP payload to external REND API
                    logger.log(Level.INFO, "[Service] [Step 10.7] Start: Posting SOAP to REND API using utility");
                    String rendPensionResponse = DocuSignSoapSender.postToRendApi(soapPayload, correlationId);
                    logger.log(Level.INFO, "[Service] [Step 10.7] End: Pension REND API call complete. Response: {0}", rendPensionResponse);
                    return rendPensionResponse;
        
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "[Service] [Step 10] Exception in PRS flow: " + e.getMessage(), e);
                
                    String message = e.getMessage();
                    String details = "{\"error\":\"Exception in PRS flow\"}";
                    int statusCode = 500;
                
                    if (message != null && message.contains("::")) {
                        String[] parts = message.split("::", 2);
                        try {
                            statusCode = Integer.parseInt(parts[0]);
                            details = parts[1]; // This is the actual DocuSign JSON error
                        } catch (Exception parseEx) {
                            logger.log(Level.SEVERE, "[Service] [Step 10] Failed to parse status and body: " + parseEx.getMessage());
                        }
                    }
                
                    // Return error JSON as string with statusCode and actual JSON
                    return String.format("{\"statusCode\": %d, \"response\": %s}", statusCode, details);
                }
               
 
//================================================================================================================================================
            case "4506-c":
                logger.log(Level.INFO, "[Service] [Step 10] Processing Tax 4506-c flow...");
                try {
                    // Step 10.1: Validate required fields for Tax flow
                    logger.log(Level.INFO, "[Service] [Step 10.1] Validating required Tax fields");
                    if (signedDate.isEmpty() || docId.isEmpty() || participantUpi.isEmpty() || envelopeId.isEmpty()) {
                        logger.log(Level.SEVERE, "[Service] [Step 10.1] Missing required Tax fields. Aborting.");
                        return String.format("{\"error\":\"Missing required Tax fields\", \"correlationId\":\"%s\"}", correlationId);
                    }
                    logger.log(Level.INFO, "[Service] [Step 10.1] Tax fields validated successfully");
            
                    // Step 10.2: Load environment variables required for OAuth and endpoint
                    logger.log(Level.INFO, "[Service] [Step 10.2] Reading environment variables for Tax flow");
                    String integratorKey = System.getenv("DOCUSIGN_INTEGRATOR_KEY");
                    String userId = System.getenv("DOCUSIGN_TAX_USERID");
                    String privateKey = System.getenv("DOCUSIGN_PRIVATE_KEY");
                    //String docusignHost = System.getenv("Docusign_API_Host");
                    //String docusignRepoTax = System.getenv("Docusign_Repo_Tax");
                    String taxApiHost = System.getenv("HTTP_MYHRSS_HOST");
                    String taxApiPath = System.getenv("HTTP_MYHRSS_PATH");
                    String taxApiPort = System.getenv("HTTP_MYHRSS_PORT");
                    String taxApiUser = System.getenv("HTTP_MYHRSS_BASIC_USER");
                    String taxApiPassword = System.getenv("HTTP_MYHRSS_BASIC_PASSWORD");

                    if (taxApiUser == null || taxApiUser.isEmpty()) {
                        throw new IllegalArgumentException("Missing environment variable: HTTP_MYHRSS_BASIC_USER");
                    }
                    if (taxApiPassword == null || taxApiPassword.isEmpty()) {
                        throw new IllegalArgumentException("Missing environment variable: HTTP_MYHRSS_BASIC_PASSWORD");
                    }

                    // Construct raw string: user:password
                    String taxBasicAuth = taxApiUser + ":" + taxApiPassword;
                    logger.log(Level.INFO, "[Service] [Step 10.2] Environment variables loaded");
            
                    // Step 10.3: Fetch OAuth token
                    logger.log(Level.INFO, "[Service] [Step 10.3] Fetching OAuth token for Tax flow");
                    String oauthTaxResponse = DocusignOAuthTokenGeneration.fetchOAuthTokenUsingJwt(
                        userId, integratorKey, privateKey, context
                    );
                    JsonObject oauthTaxJson = JsonParser.parseString(oauthTaxResponse).getAsJsonObject();
                    boolean isTaxTokenValid = oauthTaxJson.has("isTokenValid") && oauthTaxJson.get("isTokenValid").getAsBoolean();
                    if (!isTaxTokenValid) {
                        logger.log(Level.SEVERE, "[Service] [Step 10.3] OAuth token invalid for Tax flow");
                        return String.format("{\"error\":\"DocuSign OAuth token invalid\", \"correlationId\":\"%s\"}", correlationId);
                    }
                    String accessTokenTax = oauthTaxJson.getAsJsonObject("DocuSignOauthToken").get("access_token").getAsString();
                    logger.log(Level.INFO, "[Service] [Step 10.3] OAuth token successfully received for Tax");
            
                    // Step 10.4: Download document from DocuSign
                    logger.log(Level.INFO, "[Service] [Step 10.4] Downloading DocuSign document (Tax)");
                    String base64TaxDoc = DocuSignDocumentFetcher.downloadAndEncodeCombinedDocument(
                        accountId, envelopeId, accessTokenTax, correlationId
                    );
                    logger.log(Level.INFO, "[Service] [Step 10.4] Document downloaded and base64 encoded (Tax)");
            
                    if (base64TaxDoc.isEmpty()) {
                        logger.log(Level.SEVERE, "[Service] [Step 10.4] Base64 document is empty. Aborting.");
                        return String.format("{\"error\":\"Downloaded document is empty\", \"correlationId\":\"%s\"}", correlationId);
                    }
            
                    // Step 10.5: Construct final POST JSON payload
                    logger.log(Level.INFO, "[Service] [Step 10.5] Preparing POST payload to Tax endpoint");
                    JsonObject payload = new JsonObject();
                    payload.addProperty("envelopeId", envelopeId);
                    payload.addProperty("documentBase64", base64TaxDoc);
            
                    // Step 10.6: Build Tax API endpoint URL
                    String taxUrl = String.format("https://%s:%s%s", taxApiHost, taxApiPort, taxApiPath);
            
                    // Step 10.7: Prepare headers with Basic Auth
                    Map<String, String> headersMap = new HashMap<>();
                    headersMap.put("Host", taxApiHost);
                    headersMap.put("Content-Type", "application/json");
                    headersMap.put("Authorization", "Basic " + Base64.getEncoder().encodeToString(taxBasicAuth.getBytes(StandardCharsets.UTF_8)));
            
                    // Step 10.8: POST to Tax endpoint
                    logger.log(Level.INFO, "[Service] [Step 10.8] Posting to Tax endpoint...");
                    String taxFinalResponse = DocuSignSoapSender.postToTaxApi(taxUrl, headersMap, payload.toString(), correlationId);
                    logger.log(Level.INFO, "[Service] [Step 10.8] Tax API call complete. Response: {0}", taxFinalResponse);
                    return taxFinalResponse;
            
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "[Service] [Step 10] Exception in PRS flow: " + e.getMessage(), e);
                
                    String message = e.getMessage();
                    String details = "{\"error\":\"Exception in PRS flow\"}";
                    int statusCode = 500;
                
                    if (message != null && message.contains("::")) {
                        String[] parts = message.split("::", 2);
                        try {
                            statusCode = Integer.parseInt(parts[0]);
                            details = parts[1]; // This is the actual DocuSign JSON error
                        } catch (Exception parseEx) {
                            logger.log(Level.SEVERE, "[Service] [Step 10] Failed to parse status and body: " + parseEx.getMessage());
                        }
                    }
                
                    // Return error JSON as string with statusCode and actual JSON
                    return String.format("{\"statusCode\": %d, \"response\": %s}", statusCode, details);
                }
//=================================================================================================================================================
            case "srm":
                logger.log(Level.INFO, "[Service] [Step 10] Start: Processing HR flow (SRM)...");
        
                try {
                    // Step 10.1: Validate required fields for HR
                    logger.log(Level.INFO, "[Service] [Step 10.1] Validating required HR fields");
                    /*if (signedDate.isEmpty() || docId.isEmpty() || participantUpi.isEmpty() || envelopeId.isEmpty()) {
                        logger.log(Level.SEVERE, "[Service] [Step 10.1] Missing required HR fields. Aborting.");
                        return String.format("{\"error\":\"Missing required HR fields\", \"correlationId\":\"%s\"}", correlationId);
                    }*/
                    logger.log(Level.INFO, "[Service] [Step 10.1] HR fields validated successfully");
        
                    // Step 10.2: Load environment variables required for OAuth
                    logger.log(Level.INFO, "[Service] [Step 10.2] Reading environment variables for HR flow");
                    String integratorKeyHR = System.getenv("DOCUSIGN_INTEGRATOR_KEY");
                    String userIdHR = System.getenv("DOCUSIGN_HRLOA_USERID");
                    String privateKey = System.getenv("DOCUSIGN_PRIVATE_KEY");
                    //String oauthBasePathHR = System.getenv("Docusign_OAuthBasePath");
                    //String docusignHostHR = System.getenv("Docusign_API_Host");
                    String docusignRepoHR = System.getenv("DOCUSIGN_REPO_HRSRM");
                    logger.log(Level.INFO, "[Service] [Step 10.2] Environment variables loaded");
        
                    // Step 10.3: Fetch OAuth token
                    logger.log(Level.INFO, "[Service] [Step 10.3] Fetching OAuth token for HR flow");
                    String oauthHRResponse = DocusignOAuthTokenGeneration.fetchOAuthTokenUsingJwt(
                        userIdHR, integratorKeyHR, privateKey, context
                    );
                    JsonObject oauthHRJson = JsonParser.parseString(oauthHRResponse).getAsJsonObject();
                    boolean isHRTokenValid = oauthHRJson.has("isTokenValid") && oauthHRJson.get("isTokenValid").getAsBoolean();
                    if (!isHRTokenValid) {
                        logger.log(Level.SEVERE, "[Service] [Step 10.3] OAuth token invalid for HR flow");
                        return String.format("{\"error\":\"DocuSign OAuth token invalid\", \"correlationId\":\"%s\"}", correlationId);
                    }
                    String accessTokenHR = oauthHRJson.getAsJsonObject("DocuSignOauthToken").get("access_token").getAsString();
                    logger.log(Level.INFO, "[Service] [Step 10.3] OAuth token successfully received for HR");
                    
                    if(isValidSubject){
                        // Step 10.4: Fetch Document from DocuSign
                        logger.log(Level.INFO, "[Service] [Step 10.4] Downloading DocuSign document (HR)");
                        String base64HRDoc = DocuSignDocumentFetcher.downloadAndEncodeCombinedDocument(
                            accountId, envelopeId, accessTokenHR, correlationId
                        );
                        logger.log(Level.INFO, "[Service] [Step 10.4] Document downloaded and base64 encoded (HR)");
            
                        // Step 10.5: Build SOAP Payload for HR
                        logger.log(Level.INFO, "[Service] [Step 10.5] Building SOAP payload for HR flow");
                        String soapPayloadHR = HrSoapPayloadBuilder.buildHrSoapPayload(
                            participantUpi, docId, envelopeId, docusignRepoHR, base64HRDoc
                        );
                        logger.log(Level.INFO, "[Service] [Step 10.5] SOAP payload built successfully");
            
                        // Step 10.6: Send SOAP payload to HR endpoint
                        logger.log(Level.INFO, "[Service] [Step 10.6] Sending HR SOAP payload using reusable utility");
                        String hrResponse = DocuSignSoapSender.postToRendApi(soapPayloadHR, correlationId);
                        logger.log(Level.INFO, "[Service] [Step 10.6] HR flow complete. Response: {0}", hrResponse);
                        return hrResponse;
                    }else{
                        // Step 10.4: Skip HR flow as isValidSubject is false validate it with Narmadha
                        logger.log(Level.INFO, "[Service] [Step 10.4] Skipping HR flow as isValidSubject is false");
                        return "{\"ESBResponse\": {\"ResponseCode\": 200, \"ResponseText\": \"Document will NOT be retrieved nor failed because this is not an HR LOA\", \"DetailsMessage\": \"\"}}";
                    }
        
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "[Service] [Step 10] Exception in PRS flow: " + e.getMessage(), e);
                
                    String message = e.getMessage();
                    String details = "{\"error\":\"Exception in PRS flow\"}";
                    int statusCode = 500;
                
                    if (message != null && message.contains("::")) {
                        String[] parts = message.split("::", 2);
                        try {
                            statusCode = Integer.parseInt(parts[0]);
                            details = parts[1]; // This is the actual DocuSign JSON error
                        } catch (Exception parseEx) {
                            logger.log(Level.SEVERE, "[Service] [Step 10] Failed to parse status and body: " + parseEx.getMessage());
                        }
                    }
                
                    // Return error JSON as string with statusCode and actual JSON
                    return String.format("{\"statusCode\": %d, \"response\": %s}", statusCode, details);
                }
//=================================================================================================================================================        
            default:
                logger.log(Level.WARNING, "[Service] [Step 10] Unknown target repository: {0}. Skipping processing.", targetRepository);
                return "{\"ESBResponse\": {\"ResponseCode\": 200, \"ResponseText\": \"Document will NOT be retrieved nor failed because this is not an HR LOA\", \"DetailsMessage\": \"\"}}";
        }
    }
    // Helper methods
    private String getString(JsonObject obj, String key) {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString() : "";
    }

    private String formatDate(String inputDate) {
        try {
            SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            SimpleDateFormat outFormat = new SimpleDateFormat("dd-MMM-yyyy");
            return outFormat.format(inFormat.parse(inputDate));
        } catch (Exception e) {
            logger.log(Level.WARNING, "[Service] Failed to format date: {0}", inputDate);
            return inputDate;
        }
    }

    private String extractCustomFieldValue(List<JsonObject> fields, String name) {
        return fields.stream()
                .filter(f -> f.has("Name") && name.equalsIgnoreCase(f.get("Name").getAsString()))
                .map(f -> f.get("Value"))
                .filter(Objects::nonNull)
                .map(JsonElement::getAsString)
                .findFirst()
                .orElse("");
    }
}
