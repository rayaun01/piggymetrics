package com.piggymetrics.account.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.net.URI;

/**
 * @author cdov
 */
@Configuration
@EnableConfigurationProperties(ClientCredentialsProperties.class)
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

    @Bean
    public OAuth2AuthorizedClientManager oauth2AuthorizedClientManager(ClientCredentialsProperties properties) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("piggymetrics")
                .clientId(properties.getClientId())
                .clientSecret(properties.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(properties.getScope())
                .tokenUri(properties.getAccessTokenUri())
                .build();
        ClientRegistrationRepository repository = new InMemoryClientRegistrationRepository(registration);
        InMemoryOAuth2AuthorizedClientService service = new InMemoryOAuth2AuthorizedClientService(repository);
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(repository, service);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());
        return manager;
    }

    @Bean
    public RequestInterceptor oauth2FeignRequestInterceptor(OAuth2AuthorizedClientManager manager,
                                                              ClientCredentialsProperties properties) {
        return requestTemplate -> {
            OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                    .withClientRegistrationId("piggymetrics")
                    .principal(properties.getClientId())
                    .build();
            OAuth2AuthorizedClient authorizedClient = manager.authorize(authorizeRequest);
            if (authorizedClient == null) {
                throw new IllegalStateException("Unable to authorize OAuth2 client");
            }
            requestTemplate.header("Authorization", "Bearer "
                    + authorizedClient.getAccessToken().getTokenValue());
        };
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests(a -> a
                        .antMatchers("/", "/demo").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(o -> o.jwt());
    }
}
