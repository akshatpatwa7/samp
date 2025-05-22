package com.function.model;

public class PropertiesResponse {
    private boolean isValidSubject;
    private String subjectMessage;
    private String bodyMessage;
    private String docId;
    private String upi;

    // Getters and Setters
    public boolean isValidSubject() { return isValidSubject; }
    public void setValidSubject(boolean validSubject) { isValidSubject = validSubject; }

    public String getSubjectMessage() { return subjectMessage; }
    public void setSubjectMessage(String subjectMessage) { this.subjectMessage = subjectMessage; }

    public String getBodyMessage() { return bodyMessage; }
    public void setBodyMessage(String bodyMessage) { this.bodyMessage = bodyMessage; }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }

    public String getUpi() { return upi; }
    public void setUpi(String upi) { this.upi = upi; }
}

