package com.erp.entitybuilder.service.storage;

import com.erp.entitybuilder.domain.EntityField;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldStorageTest {

    @Test
    void defaultsToEavWhenStorageMissing() {
        EntityField f = new EntityField();
        f.setConfig(null);
        assertFalse(FieldStorage.isCoreDomain(f));
        assertTrue(FieldStorage.isEavExtension(f));
    }

    @Test
    void coreDomainDetected() {
        EntityField f = new EntityField();
        f.setConfig(FieldStorage.configJson(Map.of(FieldStorage.CONFIG_KEY_STORAGE, FieldStorage.STORAGE_CORE_DOMAIN)));
        assertTrue(FieldStorage.isCoreDomain(f));
        assertFalse(FieldStorage.isEavExtension(f));
    }

    @Test
    void eavExtensionExplicit() {
        EntityField f = new EntityField();
        f.setConfig(FieldStorage.configJson(Map.of(FieldStorage.CONFIG_KEY_STORAGE, FieldStorage.STORAGE_EAV_EXTENSION)));
        assertFalse(FieldStorage.isCoreDomain(f));
    }

    @Test
    void normalizeStorageInput() {
        assertEquals(FieldStorage.STORAGE_EAV_EXTENSION, FieldStorage.normalizeStorageInput(null));
        assertEquals(FieldStorage.STORAGE_CORE_DOMAIN, FieldStorage.normalizeStorageInput("core_domain"));
    }
}
