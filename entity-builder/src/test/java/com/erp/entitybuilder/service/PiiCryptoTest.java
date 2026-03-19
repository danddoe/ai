package com.erp.entitybuilder.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiCryptoTest {

    @Test
    void encryptThenDecrypt_roundTrips() {
        PiiCrypto crypto = new PiiCrypto("test-key-material");
        PiiCrypto.EncryptedValue enc = crypto.encrypt("hello-world");
        assertThat(enc.encryptedValueBase64()).isNotBlank();
        String plain = crypto.decrypt(enc.encryptedValueBase64());
        assertThat(plain).isEqualTo("hello-world");
    }
}

