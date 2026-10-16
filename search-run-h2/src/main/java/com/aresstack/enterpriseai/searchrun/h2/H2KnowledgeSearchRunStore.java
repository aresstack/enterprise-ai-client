package com.aresstack.enterpriseai.searchrun.h2;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.searchrun.api.CandidateEvent;
import com.aresstack.enterpriseai.searchrun.api.CandidateEventKind;
import com.aresstack.enterpriseai.searchrun.api.CandidateOccurrence;
import com.aresstack.enterpriseai.searchrun.api.CandidateOrigin;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeCandidate;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeSearchRun;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeSearchRunPort;
import com.aresstack.enterpriseai.searchrun.api.SearchRunId;
import com.aresstack.enterpriseai.searchrun.api.SearchRunStoreException;
import org.h2.api.ErrorCode;
import org.h2.jdbcx.JdbcDataSource;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link KnowledgeSearchRunPort} on one embedded H2 database ({@code search-runs.mv.db}). Opened lazily on first use; a
 * run is written in one transaction, so a run is either complete or absent.
 */
public final class H2KnowledgeSearchRunStore implements KnowledgeSearchRunPort, Closeable {

    static final String DATABASE_NAME = "search-runs";

    private final Path directory;
    private final JdbcDataSource dataSource;
    private final Object lifecycle = new Object();
    private boolean ready;
    private boolean closed;

    /**
     * @param directory         directory of the database file (created on first use)
     * @param lockTimeoutMillis how long a writer waits for a row locked by another transaction
     */
    public H2KnowledgeSearchRunStore(Path directory, int lockTimeoutMillis) {
        if (directory == null) {
            throw new IllegalArgumentException("directory must not be null");
        }
        if (lockTimeoutMillis < 0) {
            throw new IllegalArgumentException("lock timeout must not be negative: " + lockTimeoutMillis);
        }
        String file = directory.toAbsolutePath().resolve(DATABASE_NAME).toString().replace('\\', '/');
        if (file.indexOf(';') >= 0) {
            throw new IllegalArgumentException("search run store directory must not contain ';': " + directory);
        }
        this.directory = directory.toAbsolutePath();
        this.dataSource = new JdbcDataSource();
        this.dataSource.setURL("jdbc:h2:file:" + file + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=" + lockTimeoutMillis);
    }

    public Path directory() {
        return directory;
    }

    @Override
    public void record(KnowledgeSearchRun run) {
        if (run == null) {
            throw new IllegalArgumentException("run must not be null");
        }
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                insertRun(connection, run);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                if (e.getErrorCode() == ErrorCode.DUPLICATE_KEY_1) {
                    throw new SearchRunStoreException("Search run " + run.id() + " already exists", e);
                }
                throw e;
            }
        } catch (SQLException e) {
            throw failure("record search run " + run.id(), e);
        }
    }

    @Override
    public Optional<KnowledgeSearchRun> find(SearchRunId id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        try (Connection connection = connection()) {
            String query;
            Instant startedAt;
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT QUERY, STARTED_AT FROM SEARCH_RUN WHERE RUN_ID = ?")) {
                select.setString(1, id.value());
                try (ResultSet rows = select.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    query = rows.getString(1);
                    startedAt = rows.getObject(2, OffsetDateTime.class).toInstant();
                }
            }
            List<SourceAddress> sources = new ArrayList<SourceAddress>();
            Map<SourceAddress, String> failures = new LinkedHashMap<SourceAddress, String>();
            try (PreparedStatement select = connection.prepareStatement("SELECT SOURCE_ID, FAILURE"
                    + " FROM SEARCH_RUN_SOURCE WHERE RUN_ID = ? ORDER BY POSITION")) {
                select.setString(1, id.value());
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        SourceAddress source = SourceAddress.of(rows.getString(1));
                        sources.add(source);
                        String failure = rows.getString(2);
                        if (failure != null) {
                            failures.put(source, failure);
                        }
                    }
                }
            }
            Map<Integer, List<CandidateOccurrence>> occurrences = occurrences(connection, id);
            List<KnowledgeCandidate> candidates = new ArrayList<KnowledgeCandidate>();
            try (PreparedStatement select = connection.prepareStatement("SELECT CANDIDATE_NUMBER, SOURCE_ID,"
                    + " RESOURCE_ID, TITLE, SNIPPET, URL, REVISION_MODIFIED_AT, REVISION_VERSION, ORIGIN"
                    + " FROM SEARCH_CANDIDATE WHERE RUN_ID = ? ORDER BY CANDIDATE_NUMBER")) {
                select.setString(1, id.value());
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        int number = rows.getInt(1);
                        OffsetDateTime modified = rows.getObject(7, OffsetDateTime.class);
                        KnowledgeRevision revision = KnowledgeRevision.of(
                                modified == null ? null : modified.toInstant(), rows.getString(8));
                        List<CandidateOccurrence> seen = occurrences.get(number);
                        candidates.add(new KnowledgeCandidate(number, SourceAddress.of(rows.getString(2)),
                                KnowledgeResourceId.of(rows.getString(3)), rows.getString(4), rows.getString(5),
                                rows.getString(6), revision, CandidateOrigin.valueOf(rows.getString(9)),
                                seen == null ? Collections.<CandidateOccurrence>emptyList() : seen));
                    }
                }
            }
            return Optional.of(new KnowledgeSearchRun(id, query, startedAt, sources, failures, candidates));
        } catch (SQLException e) {
            throw failure("read search run " + id, e);
        }
    }

    @Override
    public void append(CandidateEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        appendAll(Collections.singletonList(event));
    }

    @Override
    public void appendAll(List<CandidateEvent> events) {
        if (events == null) {
            throw new IllegalArgumentException("events must not be null");
        }
        for (CandidateEvent event : events) {
            if (event == null) {
                throw new IllegalArgumentException("events must not contain null");
            }
        }
        if (events.isEmpty()) {
            return;
        }
        SearchRunId run = events.get(0).run();
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO SEARCH_CANDIDATE_EVENT("
                    + "EVENT_NUMBER, RUN_ID, CANDIDATE_NUMBER, KIND, OCCURRED_AT, DETAIL, SCORE)"
                    + " VALUES (NEXT VALUE FOR SEARCH_CANDIDATE_EVENT_NUMBER, ?, ?, ?, ?, ?, ?)")) {
                for (CandidateEvent event : events) {
                    insert.setString(1, event.run().value());
                    insert.setInt(2, event.candidateNumber());
                    insert.setString(3, event.kind().name());
                    insert.setObject(4, OffsetDateTime.ofInstant(event.at(), ZoneOffset.UTC));
                    insert.setString(5, event.detail());
                    if (event.score() == null) {
                        insert.setNull(6, Types.DOUBLE);
                    } else {
                        insert.setDouble(6, event.score());
                    }
                    // one statement per event keeps the sequence order equal to the list order
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                if (e.getErrorCode() == ErrorCode.REFERENTIAL_INTEGRITY_VIOLATED_PARENT_MISSING_1) {
                    throw new SearchRunStoreException("An event names a candidate that search run " + run
                            + " does not have", e);
                }
                throw e;
            }
        } catch (SQLException e) {
            throw failure("append events to search run " + run, e);
        }
    }

    @Override
    public List<CandidateEvent> events(SearchRunId id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        List<CandidateEvent> events = new ArrayList<CandidateEvent>();
        try (Connection connection = connection();
             PreparedStatement select = connection.prepareStatement("SELECT CANDIDATE_NUMBER, KIND, OCCURRED_AT,"
                     + " DETAIL, SCORE FROM SEARCH_CANDIDATE_EVENT WHERE RUN_ID = ? ORDER BY EVENT_NUMBER")) {
            select.setString(1, id.value());
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    double score = rows.getDouble(5);
                    Double boxed = rows.wasNull() ? null : Double.valueOf(score);
                    events.add(new CandidateEvent(id, rows.getInt(1), CandidateEventKind.valueOf(rows.getString(2)),
                            rows.getObject(3, OffsetDateTime.class).toInstant(), rows.getString(4), boxed));
                }
            }
            return events;
        } catch (SQLException e) {
            throw failure("read the events of search run " + id, e);
        }
    }

    @Override
    public void close() {
        synchronized (lifecycle) {
            if (closed) {
                return;
            }
            closed = true;
            if (!ready) {
                return;
            }
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            } catch (SQLException e) {
                throw failure("close the search run store", e);
            }
        }
    }

    private static void insertRun(Connection connection, KnowledgeSearchRun run) throws SQLException {
        String id = run.id().value();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO SEARCH_RUN(RUN_ID, QUERY, STARTED_AT) VALUES (?, ?, ?)")) {
            insert.setString(1, id);
            insert.setString(2, run.query());
            insert.setObject(3, OffsetDateTime.ofInstant(run.startedAt(), ZoneOffset.UTC));
            insert.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO SEARCH_RUN_SOURCE(RUN_ID, POSITION, SOURCE_ID, FAILURE) VALUES (?, ?, ?, ?)")) {
            int position = 0;
            for (SourceAddress source : run.sources()) {
                insert.setString(1, id);
                insert.setInt(2, position++);
                insert.setString(3, source.value());
                insert.setString(4, run.failures().get(source));
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (PreparedStatement candidate = connection.prepareStatement("INSERT INTO SEARCH_CANDIDATE(RUN_ID,"
                + " CANDIDATE_NUMBER, SOURCE_ID, RESOURCE_ID, TITLE, SNIPPET, URL, REVISION_MODIFIED_AT,"
                + " REVISION_VERSION, ORIGIN) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
             PreparedStatement occurrence = connection.prepareStatement("INSERT INTO SEARCH_CANDIDATE_OCCURRENCE("
                     + "RUN_ID, CANDIDATE_NUMBER, POSITION, SOURCE_ID, SOURCE_RANK) VALUES (?, ?, ?, ?, ?)")) {
            for (KnowledgeCandidate found : run.candidates()) {
                candidate.setString(1, id);
                candidate.setInt(2, found.number());
                candidate.setString(3, found.sourceId().value());
                candidate.setString(4, found.resourceId().value());
                candidate.setString(5, found.title());
                candidate.setString(6, found.snippet());
                candidate.setString(7, found.url());
                Optional<Instant> modified = found.revision().modifiedAt();
                candidate.setObject(8, modified.isPresent()
                        ? OffsetDateTime.ofInstant(modified.get(), ZoneOffset.UTC) : null);
                candidate.setString(9, found.revision().version());
                candidate.setString(10, found.origin().name());
                candidate.addBatch();
                int position = 0;
                for (CandidateOccurrence seen : found.occurrences()) {
                    occurrence.setString(1, id);
                    occurrence.setInt(2, found.number());
                    occurrence.setInt(3, position++);
                    occurrence.setString(4, seen.source().value());
                    occurrence.setInt(5, seen.rank());
                    occurrence.addBatch();
                }
            }
            candidate.executeBatch();
            occurrence.executeBatch();
        }
    }

    private static Map<Integer, List<CandidateOccurrence>> occurrences(Connection connection, SearchRunId id)
            throws SQLException {
        Map<Integer, List<CandidateOccurrence>> result = new LinkedHashMap<Integer, List<CandidateOccurrence>>();
        try (PreparedStatement select = connection.prepareStatement("SELECT CANDIDATE_NUMBER, SOURCE_ID, SOURCE_RANK"
                + " FROM SEARCH_CANDIDATE_OCCURRENCE WHERE RUN_ID = ? ORDER BY CANDIDATE_NUMBER, POSITION")) {
            select.setString(1, id.value());
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    int number = rows.getInt(1);
                    List<CandidateOccurrence> list = result.get(number);
                    if (list == null) {
                        list = new ArrayList<CandidateOccurrence>();
                        result.put(number, list);
                    }
                    list.add(new CandidateOccurrence(SourceAddress.of(rows.getString(2)), rows.getInt(3)));
                }
            }
        }
        return result;
    }

    private Connection connection() throws SQLException {
        synchronized (lifecycle) {
            if (closed) {
                throw new SearchRunStoreException("The search run store in " + directory + " is closed");
            }
            if (!ready) {
                try {
                    Files.createDirectories(directory);
                } catch (IOException e) {
                    throw new SearchRunStoreException("Cannot create the search run store directory " + directory, e);
                }
                try (Connection connection = dataSource.getConnection()) {
                    SearchRunSchema.migrate(connection);
                }
                ready = true;
            }
        }
        return dataSource.getConnection();
    }

    private static SearchRunStoreException failure(String what, SQLException e) {
        // Never include queries or excerpts in the message: they may contain internal content.
        return new SearchRunStoreException("Cannot " + what + " (H2 error " + e.getErrorCode() + ")", e);
    }
}
