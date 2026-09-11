package com.piggymetrics.auth.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;

@RestController
public class JwkSetController {

    private final RSAPublicKey publicKey;

    public JwkSetController(KeyPair keyPair) {
        this.publicKey = (RSAPublicKey) keyPair.getPublic();
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return new JWKSet(new RSAKey.Builder(publicKey).build()).toJSONObject();
    }
}
