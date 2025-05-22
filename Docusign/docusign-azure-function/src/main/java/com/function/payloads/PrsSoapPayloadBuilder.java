package com.function.payloads;

public class PrsSoapPayloadBuilder {

    public static String buildMuleSoftPrsPayload(
            String participantUpi,
            String docId,
            String beneficiaryUpi,
            String signedDate,
            String envIdValue,
            String docusignRepo,
            String base64File
    ) {
        String profile = "~~Participant_UPI=" + participantUpi +
                "~~Doc_ID=" + docId +
                "~~Beneficiary_UPI=" + beneficiaryUpi +
                "~~capture_source=eSignature" +
                "~~SubSystem=DocuSign" +
                "~~document_date=" + signedDate +
                "~~doc_title=LifeCertificate" +
                "~~Unid=" + envIdValue;

        String fileName = participantUpi.toString() + ".pdf";

        StringBuilder soapBuilder = new StringBuilder();
        soapBuilder.append("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\" ");
        soapBuilder.append("xmlns:api=\"http://api.rend.worldbank.org/\" api:name=\"\">");
        soapBuilder.append("<soap:Header/>");
        soapBuilder.append("<soap:Body>");
        soapBuilder.append("<api:render>");
        soapBuilder.append("<profile>").append(escapeXml(profile)).append("</profile>");
        soapBuilder.append("<unid>").append(escapeXml(envIdValue)).append("</unid>");
        soapBuilder.append("<source>PENSION</source>");
        soapBuilder.append("<repo>").append(escapeXml(docusignRepo)).append("</repo>");
        soapBuilder.append("<attachment>");
        soapBuilder.append("<fileData>").append(escapeXml(base64File)).append("</fileData>");
        soapBuilder.append("<fileName>").append(escapeXml(fileName)).append("</fileName>");
        soapBuilder.append("<firstFile>false</firstFile>");
        soapBuilder.append("</attachment>");
        soapBuilder.append("</api:render>");
        soapBuilder.append("</soap:Body>");
        soapBuilder.append("</soap:Envelope>");

        return soapBuilder.toString();
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
} 