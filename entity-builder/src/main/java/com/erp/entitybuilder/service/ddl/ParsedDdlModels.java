package com.erp.entitybuilder.service.ddl;

import java.util.List;

/** Immutable parse result for one foreign key column (single-column FK only for auto-relationships). */
record ParsedDdlForeignKey(String sourceColumnRaw, String referencedTableRaw, String referencedColumnRaw) {}

record ParsedDdlColumn(
        String rawColumnName,
        String columnSlug,
        String sqlDataType,
        boolean notNull,
        boolean hasDefault,
        boolean primaryKeyColumn,
        ParsedDdlForeignKey foreignKey
) {}

record ParsedDdlTable(
        String rawTableName,
        String entitySlug,
        List<ParsedDdlColumn> columns
) {}
