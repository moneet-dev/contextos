package dev.moneet.contextos.code.domain;

public enum DataAccessKind {
    /** {@code @Table(name = "...")} on an entity. The value is a table name. */
    ENTITY_TABLE,
    /** {@code @Entity} without {@code @Table}. The value is the entity name, not necessarily the table name. */
    ENTITY_NAME,
    /** {@code @Query} without {@code nativeQuery = true}. Values are entity names. */
    JPQL_QUERY,
    /** {@code @Query(nativeQuery = true)}. Values are table names. */
    NATIVE_QUERY,
    /** A SQL string literal in code (JdbcTemplate etc.). Values are table names. */
    SQL_LITERAL
}
