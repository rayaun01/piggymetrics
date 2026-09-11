package com.piggymetrics.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.oauth2.client")
public class ClientCredentialsProperties {

    // Real values come from the config server's security.oauth2.client.* shared YAML.
    // Placeholder defaults, including the unreachable token URI, only let tests load without a config server.
    private String clientId = "unconfigured";
    private String clientSecret = "";
    private String accessTokenUri = "http://localhost:1";
    private String scope = "server";

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getAccessTokenUri() {
        return accessTokenUri;
    }

    public void setAccessTokenUri(String accessTokenUri) {
        this.accessTokenUri = accessTokenUri;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}
