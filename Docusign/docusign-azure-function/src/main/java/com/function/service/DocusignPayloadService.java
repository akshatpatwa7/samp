package com.function.service;

import java.util.Map;
import java.util.UUID;

public class DocusignPayloadService {

    public Object generatePayload(Map<String, String> dvresponse, Map<String, Object> docusignReport) throws Exception {

        String workOui = null, upi = null;

        if (dvresponse.get("WORKOUI") != null) {
            workOui = dvresponse.get("WORKOUI");
        }
        if (dvresponse.get("UPI") != null) {
            upi = dvresponse.get("UPI");
        }

        docusignReport.put("WorkOui", workOui);
        docusignReport.put("upi", upi);
        docusignReport.put("Guid", UUID.randomUUID().toString());

        return docusignReport;
    }
}