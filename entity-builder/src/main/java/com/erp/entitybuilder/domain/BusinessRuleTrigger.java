package com.erp.entitybuilder.domain;

/**
 * When a rule runs. UI triggers are evaluated only in the portal; server triggers in {@link com.erp.entitybuilder.service.RecordsService}.
 */
public enum BusinessRuleTrigger {
    BEFORE_CREATE,
    BEFORE_UPDATE,
    ON_FORM_LOAD,
    ON_FORM_CHANGE
}
