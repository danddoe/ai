package com.erp.entitybuilder.service.ddl;

import net.sf.jsqlparser.JSQLParserException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DdlTableParserTest {

    @Test
    void parsesSimpleTable() throws Exception {
        String ddl = """
                CREATE TABLE orders (
                  id UUID NOT NULL,
                  name VARCHAR(100) NOT NULL,
                  PRIMARY KEY (id)
                );
                """;
        List<ParsedDdlTable> tables = DdlTableParser.parseCreateTables(ddl);
        assertThat(tables).hasSize(1);
        ParsedDdlTable t = tables.get(0);
        assertThat(t.entitySlug()).isEqualTo("orders");
        assertThat(t.columns()).hasSize(2);
        assertThat(t.columns().get(0).columnSlug()).isEqualTo("id");
        assertThat(t.columns().get(0).primaryKeyColumn()).isTrue();
        assertThat(t.columns().get(0).notNull()).isTrue();
        assertThat(t.columns().get(1).columnSlug()).isEqualTo("name");
        assertThat(t.columns().get(1).notNull()).isTrue();
        assertThat(t.columns().get(1).foreignKey()).isNull();
    }

    @Test
    void parsesMultiTableWithForeignKey() throws Exception {
        String ddl = """
                CREATE TABLE parent (id UUID NOT NULL, PRIMARY KEY (id));
                CREATE TABLE child (
                  id UUID NOT NULL,
                  parent_id UUID NOT NULL,
                  PRIMARY KEY (id),
                  CONSTRAINT fk_c FOREIGN KEY (parent_id) REFERENCES parent(id)
                );
                """;
        List<ParsedDdlTable> tables = DdlTableParser.parseCreateTables(ddl);
        assertThat(tables).hasSize(2);
        ParsedDdlTable child = tables.stream().filter(x -> x.entitySlug().equals("child")).findFirst().orElseThrow();
        ParsedDdlColumn fkCol = child.columns().stream().filter(c -> c.columnSlug().equals("parent_id")).findFirst().orElseThrow();
        assertThat(fkCol.foreignKey()).isNotNull();
        assertThat(fkCol.foreignKey().referencedTableRaw()).containsIgnoringCase("parent");
    }

    @Test
    void parsesTableLevelForeignKey() throws Exception {
        String ddl = """
                CREATE TABLE customer (id UUID PRIMARY KEY);
                CREATE TABLE invoice (
                  id UUID PRIMARY KEY,
                  customer_id UUID NOT NULL,
                  CONSTRAINT fk_inv_cust FOREIGN KEY (customer_id) REFERENCES customer(id)
                );
                """;
        List<ParsedDdlTable> tables = DdlTableParser.parseCreateTables(ddl);
        ParsedDdlTable inv = tables.stream().filter(x -> x.entitySlug().equals("invoice")).findFirst().orElseThrow();
        ParsedDdlColumn c = inv.columns().stream().filter(x -> x.columnSlug().equals("customer_id")).findFirst().orElseThrow();
        assertThat(c.foreignKey()).isNotNull();
        assertThat(c.foreignKey().referencedTableRaw()).containsIgnoringCase("customer");
    }

    @Test
    void rejectsCreateTableAsSelect() {
        String ddl = "CREATE TABLE t AS SELECT 1 x;";
        assertThatThrownBy(() -> DdlTableParser.parseCreateTables(ddl))
                .isInstanceOf(JSQLParserException.class)
                .hasMessageContaining("AS SELECT");
    }

    @Test
    void parsesSqlServerFullwidthBracketDelimiters() throws Exception {
        String ddl = """
                CREATE TABLE \uFF3Bdbo\uFF3D.\uFF3Blegal_party_role\uFF3D (
                    \uFF3Brole_code\uFF3D VARCHAR(20) NOT NULL,
                    PRIMARY KEY (\uFF3Brole_code\uFF3D)
                );
                """;
        List<ParsedDdlTable> tables = DdlTableParser.parseCreateTables(ddl);
        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).entitySlug()).isEqualTo("dbo_legal_party_role");
    }

    @Test
    void normalizationPreservesBracketsInsideStringLiterals() {
        String in = """
                CREATE TABLE [dbo].[t] (
                  [code] VARCHAR(20) DEFAULT '[literal]' NOT NULL,
                  CONSTRAINT [pk_t] PRIMARY KEY ([code])
                );
                """;
        String out = DdlTableParser.normalizeDdlForParser(in);
        assertThat(out).contains("'[literal]'");
        assertThat(out).doesNotContain("[dbo]");
    }

    @Test
    void parsesWhenNoSemicolonBetweenCreateCloseAndAlter() throws Exception {
        String ddl = """
                CREATE TABLE [dbo].[legal_party_role] (
                    [role_code] VARCHAR(20) NOT NULL,
                    [description] VARCHAR(100) NOT NULL
                )
                ALTER TABLE [dbo].[legal_party_role]
                ADD DEFAULT ('A') FOR [role_code];
                """;
        List<ParsedDdlTable> tables = DdlTableParser.parseCreateTables(ddl);
        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).entitySlug()).isEqualTo("dbo_legal_party_role");
        assertThat(tables.get(0).columns()).hasSize(2);
    }

    @Test
    void parsesCreateTableWhenScriptIncludesTsqlAlterDefaultAndGo() throws Exception {
        String ddl = """
                CREATE TABLE [dbo].[legal_party_role] (
                    [role_code] VARCHAR(20) NOT NULL,
                    [description] VARCHAR(100) NOT NULL,
                    [status_id] VARCHAR(1) NOT NULL,
                    [created_at] DATETIME2(7) NOT NULL,
                    [version] INT NOT NULL,

                    CONSTRAINT [pk_legal_party_role] PRIMARY KEY ([role_code]),

                    CONSTRAINT [chk_legal_party_role_status]
                        CHECK ([status_id] IN ('A','I','D'))
                );

                ALTER TABLE [dbo].[legal_party_role]
                ADD DEFAULT ('A') FOR [status_id];

                ALTER TABLE [dbo].[legal_party_role]
                ADD DEFAULT (GETDATE()) FOR [created_at];

                ALTER TABLE [dbo].[legal_party_role]
                ADD DEFAULT (0) FOR [version];
                GO
                """;
        List<ParsedDdlTable> tables = DdlTableParser.parseCreateTables(ddl);
        assertThat(tables).hasSize(1);
        ParsedDdlTable t = tables.get(0);
        assertThat(t.rawTableName()).isEqualTo("dbo_legal_party_role");
        assertThat(t.columns()).hasSize(5);
    }

    @Test
    void parsesSqlServerBracketIdentifiersAndTableLevelCheck() throws Exception {
        String ddl = """
                CREATE TABLE [dbo].[legal_entity_type] (
                    [legal_entity_type_code] VARCHAR(20) NOT NULL,
                    [description] VARCHAR(100) NOT NULL,
                    [status_id] VARCHAR(1) NOT NULL,
                    [created_at] DATETIME2(7) NOT NULL,
                    [updated_at] DATETIME2(7) NULL,
                    [version] INT NOT NULL,

                    CONSTRAINT [pk_legal_entity_type] PRIMARY KEY ([legal_entity_type_code]),

                    CONSTRAINT [chk_legal_entity_type_status]
                        CHECK ([status_id] IN ('A','I','D'))
                );
                """;
        List<ParsedDdlTable> tables = DdlTableParser.parseCreateTables(ddl);
        assertThat(tables).hasSize(1);
        ParsedDdlTable t = tables.get(0);
        assertThat(t.rawTableName()).isEqualTo("dbo_legal_entity_type");
        assertThat(t.columns()).hasSize(6);
        assertThat(t.columns().stream().filter(ParsedDdlColumn::primaryKeyColumn).map(ParsedDdlColumn::columnSlug))
                .containsExactly("legal_entity_type_code");
    }

    @Test
    void rejectsDuplicateColumnSlugs() {
        String ddl = """
                CREATE TABLE bad (
                  "Foo" INT,
                  foo INT
                );
                """;
        assertThatThrownBy(() -> DdlTableParser.parseCreateTables(ddl))
                .isInstanceOf(JSQLParserException.class)
                .hasMessageContaining("Duplicate column slug");
    }
}
