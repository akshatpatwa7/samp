package com.function.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DocusignOAuthTokenResponse {
    private String grantType;
    private String assertion;
    private String errorMessage;

    public DocusignOAuthTokenResponse(String grantType, String assertion, String error) {
        this.grantType = grantType;
        this.assertion = assertion;
        this.errorMessage = errorMessage;
    }
    @JsonProperty("grant_type")
    public String getGrantType() {
        return grantType;
    }

    public void setGrantType(String grantType) {
        this.grantType = grantType;
    }

    public String getAssertion() {
        return assertion;
    }

    public void setAssertion(String assertion) {
        this.assertion = assertion;
    }

    public String getError() {
        return errorMessage;
    }

    public void setError(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
