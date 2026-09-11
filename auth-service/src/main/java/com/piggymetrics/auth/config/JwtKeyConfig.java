package com.piggymetrics.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;
import org.springframework.security.oauth2.provider.token.TokenEnhancerChain;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class JwtKeyConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtKeyConfig.class);

    @Value("${security.jwt.private-key:}")
    private String privateKeyPem;

    @Bean
    public KeyPair keyPair() {
        if (privateKeyPem == null || privateKeyPem.trim().isEmpty()) {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                LOGGER.warn("no security.jwt.private-key configured - generated an ephemeral development RSA key pair");
                return generator.generateKeyPair();
            } catch (Exception ex) {
                throw new IllegalStateException("Could not generate RSA key pair", ex);
            }
        }

        try {
            String encoded = privateKeyPem.replace("\\n", "\n")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getMimeDecoder().decode(encoded);
            RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            return new KeyPair(publicKey, privateKey);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not parse security.jwt.private-key", ex);
        }
    }

    @Bean
    public JwtAccessTokenConverter accessTokenConverter(KeyPair keyPair) {
        JwtAccessTokenConverter converter = new JwtAccessTokenConverter();
        converter.setKeyPair(keyPair);
        return converter;
    }

    @Bean
    public TokenStore tokenStore(JwtAccessTokenConverter accessTokenConverter) {
        return new JwtTokenStore(accessTokenConverter);
    }

    @Bean
    public TokenEnhancerChain tokenEnhancerChain(JwtAccessTokenConverter accessTokenConverter) {
        TokenEnhancer subEnhancer = (accessToken, authentication) -> {
            DefaultOAuth2AccessToken enhanced = new DefaultOAuth2AccessToken(accessToken);
            Map<String, Object> additionalInformation = new HashMap<>(accessToken.getAdditionalInformation());
            additionalInformation.put("sub", authentication.getName());
            enhanced.setAdditionalInformation(additionalInformation);
            return enhanced;
        };
        List<TokenEnhancer> tokenEnhancers = new ArrayList<>();
        tokenEnhancers.add(subEnhancer);
        tokenEnhancers.add(accessTokenConverter);
        TokenEnhancerChain chain = new TokenEnhancerChain();
        chain.setTokenEnhancers(tokenEnhancers);
        return chain;
    }

    @Bean
    public JwtDecoder jwtDecoder(KeyPair keyPair) {
        return NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) keyPair.getPublic())
                .build();
    }
}
