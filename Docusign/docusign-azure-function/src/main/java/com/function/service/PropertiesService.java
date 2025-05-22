package com.function.service;

import java.util.Map;
import java.util.logging.Logger;

import com.function.model.PropertiesRequest;
import com.function.model.PropertiesResponse;
import com.function.utils.SubjectParserUtil;

public class PropertiesService {

    public PropertiesResponse processProperties(PropertiesRequest request, Logger logger) {
        logger.info("Processing properties for subject: " + request.getSubject());

        // Parse subject
        Map<String, String> extractedData = SubjectParserUtil.extractDocIdAndUpi(request.getSubject(), logger);

        // Construct response
        PropertiesResponse response = new PropertiesResponse();
        response.setDocId(extractedData.get("DOCID"));
        response.setUpi(extractedData.get("UPI"));

        boolean isValidSubject = response.getDocId() != null && response.getUpi() != null;
        response.setValidSubject(isValidSubject);

        if (!isValidSubject) {
            response.setSubjectMessage(generateSubjectMessage(request.getUnid(), response.getDocId(), response.getUpi()));
            response.setBodyMessage(generateBodyMessage(request.getUnid(), response.getDocId(), response.getUpi()));
            logger.warning("Invalid subject detected: " + response.getSubjectMessage());
        }

        return response;
    }

    private String generateSubjectMessage(String unid, String docId, String upi) {
        if (docId == null && upi != null) return "Envelope ID '" + unid + "' is missing DOCID in the subject!";
        if (docId != null && upi == null) return "Envelope ID '" + unid + "' is missing UPI in the subject!";
        return "Envelope ID '" + unid + "' is missing DOCID and UPI in the subject!";
    }

    private String generateBodyMessage(String unid, String docId, String upi) {
        if (docId == null && upi != null) return "This document with envelope ID '" + unid + "' is missing DOCID.";
        if (docId != null && upi == null) return "This document with envelope ID '" + unid + "' is missing UPI.";
        return "This document with envelope ID '" + unid + "' is missing DOCID and UPI.";
    }
}

