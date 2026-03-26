package com.erp.iam.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortalNavigationItemDesignDefaultsTest {

    @Test
    void newItem_defaultsDesignStatusToPublished() {
        PortalNavigationItem n = new PortalNavigationItem();
        assertThat(n.getDesignStatus()).isEqualTo("PUBLISHED");
    }
}
