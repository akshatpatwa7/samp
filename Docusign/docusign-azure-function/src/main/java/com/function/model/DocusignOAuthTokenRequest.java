package com.function.model;

public class DocusignOAuthTokenRequest {
    private String docusignUserId;
    private String oauthBasePath;
    private String integratorKey;

    // Getters and setters
    public String getDocusignUserId() {
        return docusignUserId;
    }

    public void setDocusignUserId(String docusignUserId) {
        this.docusignUserId = docusignUserId;
    }

    public String getOauthBasePath() {
        return oauthBasePath;
    }

    public void setOauthBasePath(String oauthBasePath) {
        this.oauthBasePath = oauthBasePath;
    }

    public String getIntegratorKey() {
        return integratorKey;
    }

    public void setIntegratorKey(String integratorKey) {
        this.integratorKey = integratorKey;
    }
}
