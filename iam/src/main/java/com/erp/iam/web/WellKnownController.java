package com.erp.iam.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * JWKS endpoint for ERP modules. When using RSA keys, expose public keys here.
 * With HMAC (development), no public key is exposed; clients validate via IAM or shared secret.
 */
@RestController
public class WellKnownController {

    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok(Map.of(
                "keys", java.util.List.of()
        ));
    }
}
