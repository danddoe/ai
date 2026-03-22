package com.erp.iam.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PortalNavigationAdminServiceRouteTest {

    @Test
    void allowsRecordsListAndNew() {
        UUID e = UUID.randomUUID();
        String base = "/entities/" + e + "/records";
        assertThat(PortalNavigationAdminService.isAllowedEntityRecordRoute(base)).isTrue();
        assertThat(PortalNavigationAdminService.isAllowedEntityRecordRoute(base + "/new")).isTrue();
    }

    @Test
    void allowsSimplePortalRootsForInternalSpa() {
        assertThat(PortalNavigationAdminService.isAllowedSimplePortalRoute("/home")).isTrue();
        assertThat(PortalNavigationAdminService.isAllowedSimplePortalRoute("/entities")).isTrue();
        assertThat(PortalNavigationAdminService.isAllowedSimplePortalRoute("/home/")).isTrue();
        assertThat(PortalNavigationAdminService.isAllowedInternalSpaRoute("/home")).isTrue();
        assertThat(PortalNavigationAdminService.isAllowedInternalSpaRoute("/entities")).isTrue();
        assertThat(PortalNavigationAdminService.isAllowedSimplePortalRoute("/home?x=1")).isFalse();
        assertThat(PortalNavigationAdminService.isAllowedSimplePortalRoute("/evil")).isFalse();
    }

    @Test
    void rejectsArbitraryPaths() {
        assertThat(PortalNavigationAdminService.isAllowedEntityRecordRoute("/evil")).isFalse();
        assertThat(PortalNavigationAdminService.isAllowedEntityRecordRoute("/entities/not-uuid/records")).isFalse();
        assertThat(PortalNavigationAdminService.isAllowedEntityRecordRoute("/entities/" + UUID.randomUUID() + "/other")).isFalse();
    }

    @Test
    void allowsRecordsListWithWhitelistedQuery() {
        UUID e = UUID.randomUUID();
        UUID v = UUID.randomUUID();
        String base = "/entities/" + e + "/records";
        assertThat(PortalNavigationAdminService.isAllowedEntityRecordRoute(base + "?cols=a,b&inline=a&actions=1")).isTrue();
        assertThat(PortalNavigationAdminService.isAllowedEntityRecordRoute(base + "?page=1&pageSize=50&q=hello")).isTrue();
        assertThat(PortalNavigationAdminService.isAllowedEntityRecordRoute(base + "?view=" + v)).isTrue();
    }

    @Test
    void rejectsQueryOnRecordsNew() {
        UUID e = UUID.randomUUID();
        assertThat(PortalNavigationAdminService.isAllowedEntityRecordRoute(
                "/entities/" + e + "/records/new?cols=a")).isFalse();
    }

    @Test
    void rejectsUnknownQueryKeyOrBadSlug() {
        UUID e = UUID.randomUUID();
        String base = "/entities/" + e + "/records";
        assertThat(PortalNavigationAdminService.isAllowedEntityRecordRoute(base + "?evil=1")).isFalse();
        assertThat(PortalNavigationAdminService.isAllowedEntityRecordRoute(base + "?cols=a,,b")).isFalse();
        assertThat(PortalNavigationAdminService.isAllowedEntityRecordRoute(base + "?cols=a%2Fb")).isFalse();
        assertThat(PortalNavigationAdminService.isAllowedEntityRecordRoute(base + "?view=not-a-uuid")).isFalse();
    }

    @Test
    void validateQueryString_unit() {
        assertThat(PortalNavigationAdminService.validateEntityRecordsQueryString("cols=x&actions=0")).isTrue();
        assertThat(PortalNavigationAdminService.validateEntityRecordsQueryString("page=0")).isFalse();
        assertThat(PortalNavigationAdminService.validateEntityRecordsQueryString("pageSize=201")).isFalse();
    }
}
