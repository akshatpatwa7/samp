package com.function.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SubjectParserUtil {

    private static final Pattern SUBJECT_PATTERN = Pattern.compile("\\((.*?)\\)");

    public static Map<String, String> extractDocIdAndUpi(String subject, Logger logger) {
        Map<String, String> extractedData = new HashMap<>();
        extractedData.put("DOCID", null);
        extractedData.put("UPI", null);

        if (subject == null || subject.isEmpty()) {
            logger.warning("Subject is empty.");
            return extractedData;
        }

        Matcher matcher = SUBJECT_PATTERN.matcher(subject);
        while (matcher.find()) {
            String[] split = matcher.group(1).split("=");
            if (split.length == 2) {
                String key = split[0].trim().toUpperCase();
                String value = split[1].trim();
                if ("DOCID".equals(key)) {
                    extractedData.put("DOCID", value);
                } else if ("UPI".equals(key)) {
                    extractedData.put("UPI", value);
                }
            }
        }

        logger.info("Extracted values - DOCID: " + extractedData.get("DOCID") + ", UPI: " + extractedData.get("UPI"));
        return extractedData;
    }
}

