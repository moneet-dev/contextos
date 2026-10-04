package dev.moneet.schema.jdbc;

import dev.moneet.schema.domain.DatabaseSchema;

public interface SchemaMetadataSource {
    DatabaseSchema load();
}
