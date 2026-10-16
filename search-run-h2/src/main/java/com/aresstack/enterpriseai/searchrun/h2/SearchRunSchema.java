package com.aresstack.enterpriseai.searchrun.h2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Versioned schema of the search run log, recorded in {@code SEARCH_RUN_STORE_SCHEMA}. Add new versions at the end,
 * never edit an applied one; every statement is idempotent so an interrupted migration completes on the next start. A
 * database written by a newer application version is refused.
 */
final class SearchRunSchema {

    /** Migration {@code i} brings the schema from version {@code i} to {@code i + 1}. */
    private static final String[][] MIGRATIONS = {{
            "CREATE TABLE IF NOT EXISTS SEARCH_RUN ("
                    + " RUN_ID VARCHAR(64) PRIMARY KEY,"
                    + " QUERY VARCHAR(1000000) NOT NULL,"
                    + " STARTED_AT TIMESTAMP(9) WITH TIME ZONE NOT NULL)",
            // Every source the run asked, in order; FAILURE is set when its search failed.
            "CREATE TABLE IF NOT EXISTS SEARCH_RUN_SOURCE ("
                    + " RUN_ID VARCHAR(64) NOT NULL REFERENCES SEARCH_RUN(RUN_ID) ON DELETE CASCADE,"
                    + " POSITION INT NOT NULL,"
                    + " SOURCE_ID VARCHAR(2048) NOT NULL,"
                    + " FAILURE VARCHAR(4000),"
                    + " PRIMARY KEY (RUN_ID, POSITION))",
            // Discovery facts: written once with the run, never updated.
            "CREATE TABLE IF NOT EXISTS SEARCH_CANDIDATE ("
                    + " RUN_ID VARCHAR(64) NOT NULL REFERENCES SEARCH_RUN(RUN_ID) ON DELETE CASCADE,"
                    + " CANDIDATE_NUMBER INT NOT NULL CHECK (CANDIDATE_NUMBER >= 1),"
                    + " SOURCE_ID VARCHAR(2048) NOT NULL,"
                    + " RESOURCE_ID VARCHAR(1000000) NOT NULL,"
                    + " TITLE VARCHAR(1000000) NOT NULL,"
                    + " SNIPPET VARCHAR(1000000) NOT NULL,"
                    + " URL VARCHAR(1000000),"
                    + " REVISION_MODIFIED_AT TIMESTAMP(9) WITH TIME ZONE,"
                    + " REVISION_VERSION VARCHAR(4000) NOT NULL,"
                    + " ORIGIN VARCHAR(16) NOT NULL CHECK (ORIGIN IN ('SOURCE', 'INDEX')),"
                    + " PRIMARY KEY (RUN_ID, CANDIDATE_NUMBER))",
            "CREATE TABLE IF NOT EXISTS SEARCH_CANDIDATE_OCCURRENCE ("
                    + " RUN_ID VARCHAR(64) NOT NULL,"
                    + " CANDIDATE_NUMBER INT NOT NULL,"
                    + " POSITION INT NOT NULL,"
                    + " SOURCE_ID VARCHAR(2048) NOT NULL,"
                    + " SOURCE_RANK INT NOT NULL CHECK (SOURCE_RANK >= 0),"
                    + " PRIMARY KEY (RUN_ID, CANDIDATE_NUMBER, POSITION),"
                    + " FOREIGN KEY (RUN_ID, CANDIDATE_NUMBER) REFERENCES SEARCH_CANDIDATE(RUN_ID, CANDIDATE_NUMBER)"
                    + " ON DELETE CASCADE)",
            // Append-only: what happened to a candidate after discovery.
            "CREATE SEQUENCE IF NOT EXISTS SEARCH_CANDIDATE_EVENT_NUMBER START WITH 1 INCREMENT BY 1",
            "CREATE TABLE IF NOT EXISTS SEARCH_CANDIDATE_EVENT ("
                    + " EVENT_NUMBER BIGINT PRIMARY KEY,"
                    + " RUN_ID VARCHAR(64) NOT NULL,"
                    + " CANDIDATE_NUMBER INT NOT NULL,"
                    + " KIND VARCHAR(64) NOT NULL,"
                    + " OCCURRED_AT TIMESTAMP(9) WITH TIME ZONE NOT NULL,"
                    + " DETAIL VARCHAR(4000),"
                    + " SCORE DOUBLE PRECISION,"
                    + " FOREIGN KEY (RUN_ID, CANDIDATE_NUMBER) REFERENCES SEARCH_CANDIDATE(RUN_ID, CANDIDATE_NUMBER)"
                    + " ON DELETE CASCADE)",
            "CREATE INDEX IF NOT EXISTS IDX_SEARCH_CANDIDATE_EVENT_RUN ON SEARCH_CANDIDATE_EVENT(RUN_ID, EVENT_NUMBER)",
            "CREATE INDEX IF NOT EXISTS IDX_SEARCH_RUN_STARTED ON SEARCH_RUN(STARTED_AT)"}};

    static final int CURRENT_VERSION = MIGRATIONS.length;

    private SearchRunSchema() {
    }

    static void migrate(Connection connection) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS SEARCH_RUN_STORE_SCHEMA ("
                    + " VERSION INT PRIMARY KEY, APPLIED_AT TIMESTAMP(9) WITH TIME ZONE NOT NULL)");
            connection.commit();
            int version = 0;
            try (ResultSet rows = statement.executeQuery(
                    "SELECT COALESCE(MAX(VERSION), 0) FROM SEARCH_RUN_STORE_SCHEMA")) {
                if (rows.next()) {
                    version = rows.getInt(1);
                }
            }
            if (version > CURRENT_VERSION) {
                throw new SQLException("Search run store schema version " + version + " is newer than supported "
                        + CURRENT_VERSION);
            }
            for (int next = version; next < CURRENT_VERSION; next++) {
                for (String ddl : MIGRATIONS[next]) {
                    statement.execute(ddl);
                }
                statement.execute("INSERT INTO SEARCH_RUN_STORE_SCHEMA(VERSION, APPLIED_AT) VALUES (" + (next + 1)
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
