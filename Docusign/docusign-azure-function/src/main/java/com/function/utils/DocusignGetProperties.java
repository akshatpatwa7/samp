package com.function.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.microsoft.azure.functions.ExecutionContext;

public class DocusignGetProperties {
    /**
     * Parses the subject string to extract DOCID and UPI values.
     *
     * @param subject The subject line to parse
     * @param UNID    The envelope ID for reference in messages
     * @param context The Azure Functions ExecutionContext for logging
     * @return Map containing isValidSubject, subjectmsg, bodymsg, DOCID, UPI
     */
    public static Map<String, Object> getProperties(String subject, String UNID, ExecutionContext context) {
        Map<String, Object> messageAttributes = new HashMap<>();

        if (subject != null) {
            String docid = null;
            String upi = null;
            String subjectmsg = null;
            String bodymsg = null;
            boolean isValidSubject = false;

            Pattern p = Pattern.compile("\\((.*?)\\)");
            Matcher m = p.matcher(subject);
            while (m.find()) {
                String[] split = m.group(1).split("=");
                if (split.length == 2) {
                    if (split[0].equalsIgnoreCase("DOCID")) {
                        docid = split[1];
                    } else if (split[0].equalsIgnoreCase("UPI")) {
                        upi = split[1];
                    }
                }
            }
            context.getLogger().info("DOCID: " + docid + " | UPI: " + upi);

            if (docid != null && upi != null) {
                isValidSubject = true;
            } else {
                subjectmsg = "Envelope id '" + UNID + "' does not have ";
                bodymsg = "This is to notify you that the document with envelope id '" + UNID + "' does not have ";

                if (docid == null && upi != null) {
                    subjectmsg += "DOCID";
                    bodymsg += "DOCID";
                } else if (docid != null && upi == null) {
                    subjectmsg += "UPI";
                    bodymsg += "UPI";
                } else {
                    subjectmsg += "DOCID and UPI";
                    bodymsg += "DOCID and UPI";
                }

                subjectmsg += " in the Subject!";
                bodymsg += " in the Subject. Please find details.";

                context.getLogger().info("subjectmsg: " + subjectmsg);
                context.getLogger().info("bodymsg: " + bodymsg);
            }

            messageAttributes.put("isValidSubject", isValidSubject);
            messageAttributes.put("subjectmsg", subjectmsg);
            messageAttributes.put("bodymsg", bodymsg);
            messageAttributes.put("DOCID", docid);
            messageAttributes.put("UPI", upi);
        }

        return messageAttributes;
    }
    
}
