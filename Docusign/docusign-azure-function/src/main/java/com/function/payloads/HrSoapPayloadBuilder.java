package com.function.payloads;

public class HrSoapPayloadBuilder {

    public static String buildHrSoapPayload(
            String upi,
            String docId,
            String unid,
            String docusignRepo,
            String base64File
    ) {
        String profile = "" +
                "~~UPI=" + upi +
                "~~Doc_ID=" + docId +
                "~~Sender_UPI=" +
                "~~HRSSC_PROCESSING=N" +
                "~~UPI_NOTIFICATION=N" +
                "~~SENDER_NOTIFICATION=N" +
                "~~ParentUnid=" +
                "~~Unid=" + unid +
                "~~Deposited_By=" +
                "~~RA=" +
                "~~RTG=" +
                "~~PageCount=N/A" +
                "~~CaptureSource=eSignature" +
                "~~#HD=" +
                "~~SubSystem=DocuSign" +
                "~~ABBN=" +
                "~~Rev=N" +
                "~~Subject=" +
                "~~CC=~~";

        String fileName = upi + "-LOA.pdf";

        StringBuilder sb = new StringBuilder();
        sb.append("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\" ");
        sb.append("xmlns:api=\"http://api.rend.worldbank.org/\" api:name=\"\">");
        sb.append("<soap:Header/>");
        sb.append("<soap:Body>");
        sb.append("<api:render>");
        sb.append("<profile>").append(escapeXml(profile)).append("</profile>");
        sb.append("<unid>").append(escapeXml(unid)).append("</unid>");
        sb.append("<source>HRSRM</source>");
        sb.append("<repo>").append(escapeXml(docusignRepo)).append("</repo>");
        sb.append("<attachment>");
        sb.append("<fileData>").append(escapeXml(base64File)).append("</fileData>");
        sb.append("<fileName>").append(escapeXml(fileName)).append("</fileName>");
        sb.append("<firstFile>false</firstFile>");
        sb.append("</attachment>");
        sb.append("</api:render>");
        sb.append("</soap:Body>");
        sb.append("</soap:Envelope>");

        return sb.toString();
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
