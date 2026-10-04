package dev.moneet.schema.jdbc;

import dev.moneet.schema.domain.Column;
import dev.moneet.schema.domain.DatabaseSchema;
import dev.moneet.schema.domain.Table;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JdbcSchemaMetadataSource implements SchemaMetadataSource {

    private final Connection connection;
    private final String userProvidedSchema;

    private final TableExtractor tableExtractor;
    private final ColumnExtractor columnExtractor;
    private final PrimaryKeyExtractor pkExtractor;
    private final ForeignKeyExtractor fkExtractor;
    private final IndexExtractor indexExtractor;

    public JdbcSchemaMetadataSource(Connection connection, String schema) {
        this.connection = connection;
        this.userProvidedSchema = schema;

        this.tableExtractor = new TableExtractor();
        this.columnExtractor = new ColumnExtractor();
        this.pkExtractor = new PrimaryKeyExtractor();
        this.fkExtractor = new ForeignKeyExtractor();
        this.indexExtractor = new IndexExtractor();
    }

    @Override
    public DatabaseSchema load() {
        try {
            DatabaseMetaData metaData = connection.getMetaData();

            DatabaseVendor vendor =
                    DatabaseVendor.fromProductName(
                            metaData.getDatabaseProductName()
                    );

            String catalog = resolveCatalog(metaData, vendor);
            String schema = resolveSchema(metaData, vendor);

            List<String> tableNames =
                    tableExtractor.extractTables(metaData, catalog, schema);

            List<Table> tables = new ArrayList<>();

            for (String rawTableName : tableNames) {

                String tableName =
                        normalizeIdentifier(metaData, rawTableName);

                List<Column> columns =
                        columnExtractor.extract(metaData, catalog, schema, tableName);

                var primaryKeys =
                        pkExtractor.extract(metaData, catalog, schema, tableName);

                var foreignKeys =
                        fkExtractor.extract(metaData, catalog, schema, tableName);

                var indexes =
                        indexExtractor.extract(metaData, catalog, schema, tableName);

                tables.add(new Table(
                        tableName,
                        columns,
                        primaryKeys,
                        foreignKeys,
                        indexes
                ));
            }

            return new DatabaseSchema(tables);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load schema metadata", e);
        }
    }

    // -----------------------------------------------------
    // Vendor-aware resolution (minimal switch)
    // -----------------------------------------------------

    private String resolveCatalog(DatabaseMetaData metaData,
                                  DatabaseVendor vendor) throws SQLException {

        if (vendor == DatabaseVendor.MYSQL) {
            return connection.getCatalog();
        }

        return null;
    }

    private String resolveSchema(DatabaseMetaData metaData,
                                 DatabaseVendor vendor) throws SQLException {

        if (userProvidedSchema != null) {
            return userProvidedSchema;
        }

        switch (vendor) {
            case POSTGRES:
                return "public";
            case ORACLE:
                return metaData.getUserName();
            case SQLSERVER:
                return "dbo";
            default:
                return null;
        }
    }

    private String normalizeIdentifier(DatabaseMetaData metaData,
                                       String name) throws SQLException {

        if (name == null) return null;

        if (metaData.storesUpperCaseIdentifiers()) {
            return name.toUpperCase();
        }

        if (metaData.storesLowerCaseIdentifiers()) {
            return name.toLowerCase();
        }

        return name;
    }
}