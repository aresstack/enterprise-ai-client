package com.aresstack.enterpriseai.memory.h2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Versioned schema of the memory store, recorded in {@code MEMORY_STORE_SCHEMA}. Add new versions at the end, never
 * edit an applied one; every statement is idempotent so an interrupted migration completes on the next start. A
 * database written by a newer application version is refused.
 */
final class MemorySchema {

    /** Migration {@code i} brings the schema from version {@code i} to {@code i + 1}. */
    private static final String[][] MIGRATIONS = {{
            // Visible ids come from this sequence; numbers of deleted memories are never handed out again.
            "CREATE SEQUENCE IF NOT EXISTS MEMORY_NUMBER START WITH 1 INCREMENT BY 1",
            "CREATE TABLE IF NOT EXISTS MEMORY ("
                    + " MEMORY_NUMBER BIGINT PRIMARY KEY,"
                    + " SCOPE VARCHAR(16) NOT NULL CHECK (SCOPE IN ('CHAT', 'PROJECT', 'GLOBAL')),"
                    + " OWNER_KEY VARCHAR(256) NOT NULL,"
                    + " CONTENT VARCHAR(4000) NOT NULL,"
                    + " CONTENT_HASH VARCHAR(64) NOT NULL,"
                    + " CREATED_AT TIMESTAMP(9) WITH TIME ZONE NOT NULL,"
                    + " UPDATED_AT TIMESTAMP(9) WITH TIME ZONE NOT NULL,"
                    + " ROW_VERSION BIGINT NOT NULL CHECK (ROW_VERSION >= 1))",
            // One memory per owner and content: a concurrent duplicate save fails here instead of writing twice.
            "CREATE UNIQUE INDEX IF NOT EXISTS IDX_MEMORY_OWNER_CONTENT ON MEMORY(SCOPE, OWNER_KEY, CONTENT_HASH)"}};

    static final int CURRENT_VERSION = MIGRATIONS.length;

    private MemorySchema() {
    }

    static void migrate(Connection connection) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS MEMORY_STORE_SCHEMA ("
                    + " VERSION INT PRIMARY KEY, APPLIED_AT TIMESTAMP(9) WITH TIME ZONE NOT NULL)");
            connection.commit();
            int version = 0;
            try (ResultSet rows = statement.executeQuery("SELECT COALESCE(MAX(VERSION), 0) FROM MEMORY_STORE_SCHEMA")) {
                if (rows.next()) {
                    version = rows.getInt(1);
                }
            }
            if (version > CURRENT_VERSION) {
                throw new SQLException("Memory store schema version " + version + " is newer than supported "
                        + CURRENT_VERSION);
            }
            for (int next = version; next < CURRENT_VERSION; next++) {
                for (String ddl : MIGRATIONS[next]) {
                    statement.execute(ddl);
                }
                statement.execute("INSERT INTO MEMORY_STORE_SCHEMA(VERSION, APPLIED_AT) VALUES (" + (next + 1)
                        + ", CURRENT_TIMESTAMP)");
                connection.commit();
            }
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }
}
