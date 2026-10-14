package com.aresstack.enterpriseai.fact.h2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Versioned schema of the fact store. Each migration runs once and is recorded in {@code FACT_STORE_SCHEMA};
 * add new versions at the end, never edit an applied one. H2 commits DDL at once, so every statement is idempotent
 * ({@code IF NOT EXISTS}): a migration interrupted halfway is completed on the next start. A database written by a newer
 * application version is refused instead of being changed.
 */
final class FactSchema {

    static final String RESOURCE_TYPE_INDEX = "IDX_FACT_RESOURCE_TYPE";
    static final String TYPE_RESOURCE_INDEX = "IDX_FACT_TYPE_RESOURCE";

    /** Migration {@code i} brings the schema from version {@code i} to {@code i + 1}. */
    private static final String[][] MIGRATIONS = {{
            "CREATE TABLE IF NOT EXISTS RESOURCE_REF ("
                    + " RESOURCE_ID VARCHAR(2048) PRIMARY KEY,"
                    + " SOURCE_ADDRESS VARCHAR(2048),"
                    + " REGISTERED_AT TIMESTAMP(9) WITH TIME ZONE NOT NULL)",
            "CREATE INDEX IF NOT EXISTS IDX_RESOURCE_SOURCE ON RESOURCE_REF(SOURCE_ADDRESS)",
            "CREATE TABLE IF NOT EXISTS FACT_TYPE ("
                    + " TYPE_ID VARCHAR(64) PRIMARY KEY,"
                    + " DESCRIPTION VARCHAR(4000) NOT NULL,"
                    + " SCHEMA_VERSION INT NOT NULL CHECK (SCHEMA_VERSION >= 1),"
                    + " JSON_SCHEMA CHARACTER LARGE OBJECT NOT NULL,"
                    + " REGISTERED_AT TIMESTAMP(9) WITH TIME ZONE NOT NULL,"
                    + " UPDATED_AT TIMESTAMP(9) WITH TIME ZONE NOT NULL)",
            "CREATE TABLE IF NOT EXISTS METADATA_FACT ("
                    + " FACT_ID UUID PRIMARY KEY,"
                    + " RESOURCE_ID VARCHAR(2048) NOT NULL,"
                    + " TYPE_ID VARCHAR(64) NOT NULL,"
                    + " SCHEMA_VERSION INT NOT NULL CHECK (SCHEMA_VERSION >= 1),"
                    + " PAYLOAD JSON NOT NULL,"
                    + " FACT_ROLE VARCHAR(64),"
                    + " ORIGIN VARCHAR(16) NOT NULL CHECK (ORIGIN IN ('AUTOMATIC', 'MANUAL')),"
                    + " REVIEW VARCHAR(16) NOT NULL CHECK (REVIEW IN ('UNREVIEWED', 'CONFIRMED', 'REJECTED')),"
                    + " EXTRACTOR_ID VARCHAR(128) NOT NULL,"
                    + " EXTRACTOR_VERSION VARCHAR(128) NOT NULL,"
                    + " SOURCE_VERSION VARCHAR(512) NOT NULL,"
                    + " SOURCE_MODIFIED_AT TIMESTAMP(9) WITH TIME ZONE,"
                    + " CONTENT_HASH VARCHAR(256) NOT NULL,"
                    + " ANCHOR_SECTION VARCHAR(1024),"
                    + " ANCHOR_PATH VARCHAR(1024),"
                    + " ANCHOR_LINE_START INT,"
                    + " ANCHOR_LINE_END INT,"
                    + " ANCHOR_CHAR_START INT,"
                    + " ANCHOR_CHAR_END INT,"
                    + " ANCHOR_QUOTE VARCHAR(1000),"
                    + " ANCHOR_FINGERPRINT VARCHAR(1024),"
                    + " CREATED_AT TIMESTAMP(9) WITH TIME ZONE NOT NULL,"
                    + " UPDATED_AT TIMESTAMP(9) WITH TIME ZONE NOT NULL)",
            // The two access paths (#106); H2 adds single-column indexes for the foreign keys on its own.
            "CREATE INDEX IF NOT EXISTS " + RESOURCE_TYPE_INDEX + " ON METADATA_FACT(RESOURCE_ID, TYPE_ID)",
            "CREATE INDEX IF NOT EXISTS " + TYPE_RESOURCE_INDEX + " ON METADATA_FACT(TYPE_ID, RESOURCE_ID)",
            "ALTER TABLE METADATA_FACT ADD CONSTRAINT IF NOT EXISTS FK_FACT_RESOURCE FOREIGN KEY (RESOURCE_ID)"
                    + " REFERENCES RESOURCE_REF(RESOURCE_ID) ON DELETE RESTRICT",
            "ALTER TABLE METADATA_FACT ADD CONSTRAINT IF NOT EXISTS FK_FACT_TYPE FOREIGN KEY (TYPE_ID)"
                    + " REFERENCES FACT_TYPE(TYPE_ID) ON DELETE RESTRICT"}, {
            // Last completed run per resource and extractor (#107), written with the run's facts.
            "CREATE TABLE IF NOT EXISTS EXTRACTION_RUN ("
                    + " RESOURCE_ID VARCHAR(2048) NOT NULL,"
                    + " EXTRACTOR_ID VARCHAR(128) NOT NULL,"
                    + " EXTRACTOR_VERSION VARCHAR(128) NOT NULL,"
                    + " SOURCE_VERSION VARCHAR(512) NOT NULL,"
                    + " SOURCE_MODIFIED_AT TIMESTAMP(9) WITH TIME ZONE,"
                    + " CONTENT_HASH VARCHAR(256) NOT NULL,"
                    + " COMPLETED_AT TIMESTAMP(9) WITH TIME ZONE NOT NULL,"
                    + " PRIMARY KEY (RESOURCE_ID, EXTRACTOR_ID))",
            "ALTER TABLE EXTRACTION_RUN ADD CONSTRAINT IF NOT EXISTS FK_RUN_RESOURCE FOREIGN KEY (RESOURCE_ID)"
                    + " REFERENCES RESOURCE_REF(RESOURCE_ID) ON DELETE RESTRICT"}};

    static final int CURRENT_VERSION = MIGRATIONS.length;

    private FactSchema() {
    }

    /** Bring the database to {@link #CURRENT_VERSION}. */
    static void migrate(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS FACT_STORE_SCHEMA ("
                    + " VERSION INT PRIMARY KEY, APPLIED_AT TIMESTAMP(9) WITH TIME ZONE NOT NULL)");
            connection.commit();
            int version = version(statement);
            if (version > CURRENT_VERSION) {
                throw new SQLException("fact store schema version " + version + " is newer than this application ("
                        + CURRENT_VERSION + ")");
            }
            for (int next = version; next < CURRENT_VERSION; next++) {
                try {
                    for (String sql : MIGRATIONS[next]) {
                        statement.execute(sql);
                    }
                    statement.execute("INSERT INTO FACT_STORE_SCHEMA VALUES (" + (next + 1) + ", CURRENT_TIMESTAMP)");
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                }
            }
        }
    }

    static int version(Statement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery("SELECT COALESCE(MAX(VERSION), 0) FROM FACT_STORE_SCHEMA")) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
