package dev.moneet.schema.jdbc;

public enum DatabaseVendor {
    POSTGRES,
    MYSQL,
    SQLITE,
    ORACLE,
    SQLSERVER,
    UNKNOWN;

    public static DatabaseVendor fromProductName(String productName) {
        if (productName == null) return UNKNOWN;

        String name = productName.toLowerCase();

        if (name.contains("postgres")) return POSTGRES;
        if (name.contains("mysql")) return MYSQL;
        if (name.contains("sqlite")) return SQLITE;
        if (name.contains("oracle")) return ORACLE;
        if (name.contains("microsoft sql server")) return SQLSERVER;

        return UNKNOWN;
    }
}