// package com.function.utils;
// import org.junit.jupiter.api.*;
// import org.mockito.*;
// import java.net.URI;
// import java.net.http.*;
// import java.util.Base64;
// import static org.junit.jupiter.api.Assertions.*;
// import static org.mockito.Mockito.*;
// // ...existing code...
// public class DocuSignDocumentFetcher {
//     private final HttpClient httpClient;

//     public DocuSignDocumentFetcher(HttpClient httpClient) {
//         this.httpClient = httpClient;
//     }

//     public String downloadAndEncodeCombinedDocument(
//         String accountId, String envelopeId, String accessToken, String correlationId
//     ) throws Exception {
//         // ...use this.httpClient instead of HttpClient.newHttpClient()...
//     }
// }
// // ...existing code...
// // File: src/test/java/com/function/utils/DocuSignDocumentFetcherTest.java




// class DocuSignDocumentFetcherTest {

//     @BeforeEach
//     void setUp() {
//         System.setProperty("DOCUSIGN_HTTP_HOST", "demo.docusign.net");
//         System.setProperty("DOCUSIGN_HTTP_PORT", "443");
//         System.setProperty("DOCUSIGN_BASEPATH", "/restapi");
//         System.setProperty("DOCUSIGN_RELATIVE_API_PATH", "/v2.1/accounts");
//     }

//     @AfterEach
//     void tearDown() {
//         System.clearProperty("DOCUSIGN_HTTP_HOST");
//         System.clearProperty("DOCUSIGN_HTTP_PORT");
//         System.clearProperty("DOCUSIGN_BASEPATH");
//         System.clearProperty("DOCUSIGN_RELATIVE_API_PATH");
//     }

//     @Test
//     void testDownloadAndEncodeCombinedDocument_Success() throws Exception {
//         // Arrange
//         String accountId = "123";
//         String envelopeId = "abc";
//         String accessToken = "token";
//         String correlationId = "corr";
//         byte[] pdfBytes = "pdf-content".getBytes();
//         String expectedBase64 = Base64.getEncoder().encodeToString(pdfBytes);

//         HttpClient mockClient = mock(HttpClient.class);
//         HttpRequest anyRequest = any(HttpRequest.class);
//         HttpResponse<byte[]> mockResponse = mock(HttpResponse.class);

//         when(mockResponse.statusCode()).thenReturn(200);
//         when(mockResponse.body()).thenReturn(pdfBytes);
//         when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

//         // Use reflection to inject mock HttpClient
//         try (MockedStatic<HttpClient> mockedHttpClient = Mockito.mockStatic(HttpClient.class)) {
//             mockedHttpClient.when(HttpClient::newHttpClient).thenReturn(mockClient);

//             // Act
//             String result = DocuSignDocumentFetcher.downloadAndEncodeCombinedDocument(accountId, envelopeId, accessToken, correlationId);

//             // Assert
//             assertEquals(expectedBase64, result);
//         }
//     }

//     @Test
//     void testDownloadAndEncodeCombinedDocument_Non200Response() throws Exception {
//         String accountId = "123";
//         String envelopeId = "abc";
//         String accessToken = "token";
//         String correlationId = "corr";
//         byte[] errorBytes = "{\"error\":\"not found\"}".getBytes();

//         HttpClient mockClient = mock(HttpClient.class);
//         HttpResponse<byte[]> mockResponse = mock(HttpResponse.class);

//         when(mockResponse.statusCode()).thenReturn(404);
//         when(mockResponse.body()).thenReturn(errorBytes);
//         when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

//         try (MockedStatic<HttpClient> mockedHttpClient = Mockito.mockStatic(HttpClient.class)) {
//             mockedHttpClient.when(HttpClient::newHttpClient).thenReturn(mockClient);

//             Exception ex = assertThrows(Exception.class, () ->
//                 DocuSignDocumentFetcher.downloadAndEncodeCombinedDocument(accountId, envelopeId, accessToken, correlationId)
//             );
//             assertTrue(ex.getMessage().contains("404"));
//             assertTrue(ex.getMessage().contains("not found"));
//         }
//     }

//     @Test
//     void testDownloadAndEncodeCombinedDocument_MissingEnvVars() {
//         System.clearProperty("DOCUSIGN_HTTP_HOST");
//         System.clearProperty("DOCUSIGN_HTTP_PORT");
//         System.clearProperty("DOCUSIGN_BASEPATH");
//         System.clearProperty("DOCUSIGN_RELATIVE_API_PATH");

//         Exception ex = assertThrows(Exception.class, () ->
//             DocuSignDocumentFetcher.downloadAndEncodeCombinedDocument("id", "env", "tok", "corr")
//         );
//         assertTrue(ex.getMessage().contains("Missing environment variables"));
//     }

//     @Test
//     void testDownloadAndEncodeCombinedDocument_HttpClientThrows() throws Exception {
//         String accountId = "123";
//         String envelopeId = "abc";
//         String accessToken = "token";
//         String correlationId = "corr";

//         HttpClient mockClient = mock(HttpClient.class);
//         when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
//             .thenThrow(new RuntimeException("Network error"));

//         try (MockedStatic<HttpClient> mockedHttpClient = Mockito.mockStatic(HttpClient.class)) {
//             mockedHttpClient.when(HttpClient::newHttpClient).thenReturn(mockClient);

//             Exception ex = assertThrows(Exception.class, () ->
//                 DocuSignDocumentFetcher.downloadAndEncodeCombinedDocument(accountId, envelopeId, accessToken, correlationId)
//             );
//             assertTrue(ex.getMessage().contains("Network error"));
//         }
//     }
// }