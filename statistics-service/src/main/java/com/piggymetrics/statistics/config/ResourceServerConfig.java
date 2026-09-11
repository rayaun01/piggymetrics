package com.piggymetrics.statistics.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.net.URI;

/**
 * @author cdov
 */
@Configuration
public class ResourceServerConfig extends WebSecurityConfigurerAdapter {

    @Value("${security.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    @Value("${security.oauth2.resource.user-info-uri:http://localhost:5000/uaa/users/current}")
    private String userInfoUri;

    @Bean
    public JwtDecoder jwtDecoder() {
        // The user-info URI is set for both docker and local profiles in shared YAML; security.jwt.jwk-set-uri overrides it.
        String uri = jwkSetUri.isEmpty()
                ? URI.create(userInfoUri).resolve("/uaa/.well-known/jwks.json").toString()
                : jwkSetUri;
        return NimbusJwtDecoder.withJwkSetUri(uri).build();
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests(a -> a.anyRequest().authenticated())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(o -> o.jwt());
    }
}
