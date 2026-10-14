package com.aresstack.enterpriseai.fact.h2;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.fact.api.ArgumentCondition;
import com.aresstack.enterpriseai.fact.api.ArgumentProjection;
import com.aresstack.enterpriseai.fact.api.ExtractionRun;
import com.aresstack.enterpriseai.fact.api.FactAnchor;
import com.aresstack.enterpriseai.fact.api.FactArgument;
import com.aresstack.enterpriseai.fact.api.FactId;
import com.aresstack.enterpriseai.fact.api.FactOrigin;
import com.aresstack.enterpriseai.fact.api.FactPayload;
import com.aresstack.enterpriseai.fact.api.FactProvenance;
import com.aresstack.enterpriseai.fact.api.FactReview;
import com.aresstack.enterpriseai.fact.api.FactRole;
import com.aresstack.enterpriseai.fact.api.FactStoreException;
import com.aresstack.enterpriseai.fact.api.FactStoreException.Reason;
import com.aresstack.enterpriseai.fact.api.FactStorePort;
import com.aresstack.enterpriseai.fact.api.FactType;
import com.aresstack.enterpriseai.fact.api.FactTypeId;
import com.aresstack.enterpriseai.fact.api.MetadataFact;
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
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@link FactStorePort} on one embedded H2 database ({@code <directory>/facts.mv.db}) for all knowledge sources.
 *
 * <p>Every call opens its own JDBC connection and runs one short transaction; connections are never shared between
 * threads. H2 (MVStore) gives row locks with snapshot reads, so readers do not wait for writers; a writer waits at most
 * the lock timeout for another writer of the same row and then fails with {@link Reason#LOCK_TIMEOUT}. The database
 * is opened and migrated on first use and stays open until {@link #close()}. Only one process may open it (H2 file
 * lock); a second one fails with {@link Reason#STORAGE}.
 */
public final class H2FactStore implements FactStorePort, Closeable {

    /** File name of the database without H2's suffix {@code .mv.db}. */
    public static final String DATABASE_NAME = "facts";

    private static final String FACT_COLUMNS = "FACT_ID, RESOURCE_ID, TYPE_ID, SCHEMA_VERSION, PAYLOAD, FACT_ROLE,"
            + " ORIGIN, REVIEW, EXTRACTOR_ID, EXTRACTOR_VERSION, SOURCE_VERSION, SOURCE_MODIFIED_AT, CONTENT_HASH,"
            + " ANCHOR_SECTION, ANCHOR_PATH, ANCHOR_LINE_START, ANCHOR_LINE_END, ANCHOR_CHAR_START, ANCHOR_CHAR_END,"
            + " ANCHOR_QUOTE, ANCHOR_FINGERPRINT, CREATED_AT, UPDATED_AT";
    private static final String SELECT_FACTS = "SELECT " + FACT_COLUMNS + " FROM METADATA_FACT";
    /** Attempts of an idempotent registration that lost an insert race against another writer. */
    private static final int REGISTRATION_ATTEMPTS = 2;

    private final Path directory;
    private final JdbcDataSource dataSource;
    private final Clock clock;
    private final Object lifecycle = new Object();
    private volatile boolean ready;
    private boolean closed;

    /**
     * @param directory         directory of the database file (created on first use)
     * @param lockTimeoutMillis how long a writer waits for a row locked by another transaction
     */
    public H2FactStore(Path directory, int lockTimeoutMillis) {
        this(directory, lockTimeoutMillis, Clock.systemUTC());
    }

    H2FactStore(Path directory, int lockTimeoutMillis, Clock clock) {
        if (directory == null || clock == null) {
            throw new IllegalArgumentException("directory and clock must not be null");
        }
        if (lockTimeoutMillis < 0) {
            throw new IllegalArgumentException("lock timeout must not be negative: " + lockTimeoutMillis);
        }
        String file = directory.toAbsolutePath().resolve(DATABASE_NAME).toString().replace('\\', '/');
        if (file.indexOf(';') >= 0) {
            throw new IllegalArgumentException("fact store directory must not contain ';': " + directory);
        }
        this.directory = directory.toAbsolutePath();
        this.clock = clock;
        this.dataSource = new JdbcDataSource();
        // Keep the database open between calls (closed by close() or at JVM exit); per-session lock timeout.
        this.dataSource.setURL("jdbc:h2:file:" + file + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=" + lockTimeoutMillis);
    }

    /** Directory of the database file. */
    public Path directory() {
        return directory;
    }

    // ---------------------------------------------------------------- types

    @Override
    public void registerType(final FactType type) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        retryingRegistration("register fact type " + type.id(), new SqlWork<Void>() {
            @Override
            public Void run(Connection connection) throws SQLException {
                Instant now = clock.instant();
                boolean projectionChanged;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT DESCRIPTION, SCHEMA_VERSION, JSON_SCHEMA, ARGUMENT_PROJECTION FROM FACT_TYPE"
                                + " WHERE TYPE_ID = ? FOR UPDATE")) {
                    select.setString(1, type.id().value());
                    try (ResultSet rows = select.executeQuery()) {
                        if (!rows.next()) {
                            try (PreparedStatement insert = connection.prepareStatement(
                                    "INSERT INTO FACT_TYPE (TYPE_ID, DESCRIPTION, SCHEMA_VERSION, JSON_SCHEMA,"
                                            + " ARGUMENT_PROJECTION, REGISTERED_AT, UPDATED_AT)"
                                            + " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                                insert.setString(1, type.id().value());
                                insert.setString(2, type.description());
                                insert.setInt(3, type.schemaVersion());
                                insert.setString(4, type.jsonSchema());
                                insert.setString(5, type.argumentProjection().toJson());
                                setInstant(insert, 6, now);
                                setInstant(insert, 7, now);
                                insert.executeUpdate();
                            }
                            return null;
                        }
                        String description = rows.getString(1);
                        int version = rows.getInt(2);
                        String schema = rows.getString(3);
                        projectionChanged = !type.argumentProjection().toJson().equals(rows.getString(4));
                        if (type.schemaVersion() < version) {
                            throw new FactStoreException(Reason.INCOMPATIBLE_SCHEMA, "Fact type " + type.id()
                                    + " is registered in schema version " + version + "; version "
                                    + type.schemaVersion() + " would downgrade it");
                        }
                        if (type.schemaVersion() == version
                                && (!type.jsonSchema().equals(schema) || projectionChanged)) {
                            throw new FactStoreException(Reason.INCOMPATIBLE_SCHEMA, "Fact type " + type.id()
                                    + " has another schema or argument projection in version " + version
                                    + "; raise the schema version");
                        }
                        if (type.schemaVersion() == version && type.description().equals(description)) {
                            return null;
                        }
                    }
                }
                try (PreparedStatement update = connection.prepareStatement("UPDATE FACT_TYPE SET DESCRIPTION = ?,"
                        + " SCHEMA_VERSION = ?, JSON_SCHEMA = ?, ARGUMENT_PROJECTION = ?, UPDATED_AT = ?"
                        + " WHERE TYPE_ID = ?")) {
                    update.setString(1, type.description());
                    update.setInt(2, type.schemaVersion());
                    update.setString(3, type.jsonSchema());
                    update.setString(4, type.argumentProjection().toJson());
                    setInstant(update, 5, now);
                    update.setString(6, type.id().value());
                    update.executeUpdate();
                }
                if (projectionChanged) {
                    // The arguments follow the new rules at once; payloads stay as they are.
                    update(connection, "DELETE FROM FACT_ARGUMENT WHERE FACT_ID IN"
                            + " (SELECT FACT_ID FROM METADATA_FACT WHERE TYPE_ID = ?)", type.id().value());
                    deriveArguments(connection, " WHERE TYPE_ID = ?", type.id().value());
                }
                return null;
            }
        });
    }

    @Override
    public Optional<FactType> type(final FactTypeId id) {
        if (id == null) {
            throw new IllegalArgumentException("type id must not be null");
        }
        List<FactType> types = read("read fact type " + id, new SqlWork<List<FactType>>() {
            @Override
            public List<FactType> run(Connection connection) throws SQLException {
                return queryTypes(connection, " WHERE TYPE_ID = ?", id.value());
            }
        });
        return types.isEmpty() ? Optional.<FactType>empty() : Optional.of(types.get(0));
    }

    @Override
    public List<FactType> types() {
        return read("list fact types", new SqlWork<List<FactType>>() {
            @Override
            public List<FactType> run(Connection connection) throws SQLException {
                return queryTypes(connection, " ORDER BY TYPE_ID");
            }
        });
    }

    // ------------------------------------------------------------ resources

    @Override
    public void registerResource(final KnowledgeResourceId resource, final SourceAddress source) {
        if (resource == null) {
            throw new IllegalArgumentException("resource must not be null");
        }
        retryingRegistration("register resource " + resource, new SqlWork<Void>() {
            @Override
            public Void run(Connection connection) throws SQLException {
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT SOURCE_ADDRESS FROM RESOURCE_REF WHERE RESOURCE_ID = ? FOR UPDATE")) {
                    select.setString(1, resource.value());
                    try (ResultSet rows = select.executeQuery()) {
                        if (rows.next()) {
                            if (source != null && !source.value().equals(rows.getString(1))) {
                                try (PreparedStatement update = connection.prepareStatement(
                                        "UPDATE RESOURCE_REF SET SOURCE_ADDRESS = ? WHERE RESOURCE_ID = ?")) {
                                    update.setString(1, source.value());
                                    update.setString(2, resource.value());
                                    update.executeUpdate();
                                }
                            }
                            return null;
                        }
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO RESOURCE_REF (RESOURCE_ID, SOURCE_ADDRESS, REGISTERED_AT) VALUES (?, ?, ?)")) {
                    insert.setString(1, resource.value());
                    insert.setString(2, source == null ? null : source.value());
                    setInstant(insert, 3, clock.instant());
                    insert.executeUpdate();
                }
                return null;
            }
        });
    }

    @Override
    public boolean isResourceRegistered(final KnowledgeResourceId resource) {
        if (resource == null) {
            throw new IllegalArgumentException("resource must not be null");
        }
        return read("check resource " + resource, new SqlWork<Boolean>() {
            @Override
            public Boolean run(Connection connection) throws SQLException {
                return exists(connection, "SELECT 1 FROM RESOURCE_REF WHERE RESOURCE_ID = ?", resource.value());
            }
        });
    }

    // ---------------------------------------------------------------- facts

    @Override
    public MetadataFact save(final MetadataFact fact) {
        if (fact == null) {
            throw new IllegalArgumentException("fact must not be null");
        }
        return transaction("save fact " + fact.id(), new SqlWork<MetadataFact>() {
            @Override
            public MetadataFact run(Connection connection) throws SQLException {
                writeFact(connection, fact, false);
                writeArguments(connection, fact.id(), fact.resourceId(), fact.typeId(), fact.payload(),
                        new HashMap<FactTypeId, ArgumentProjection>());
                return loadFact(connection, fact.id());
            }
        });
    }

    @Override
    public Optional<MetadataFact> fact(final FactId id) {
        if (id == null) {
            throw new IllegalArgumentException("fact id must not be null");
        }
        return Optional.ofNullable(read("read fact " + id, new SqlWork<MetadataFact>() {
            @Override
            public MetadataFact run(Connection connection) throws SQLException {
                return loadFact(connection, id);
            }
        }));
    }

    @Override
    public boolean removeFact(final FactId id) {
        if (id == null) {
            throw new IllegalArgumentException("fact id must not be null");
        }
        return transaction("remove fact " + id, new SqlWork<Boolean>() {
            @Override
            public Boolean run(Connection connection) throws SQLException {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM METADATA_FACT WHERE FACT_ID = ?")) {
                    delete.setObject(1, id.value());
                    return delete.executeUpdate() > 0;
                }
            }
        });
    }

    @Override
    public List<MetadataFact> factsOf(final KnowledgeResourceId resource) {
        if (resource == null) {
            throw new IllegalArgumentException("resource must not be null");
        }
        return read("read facts of " + resource, new SqlWork<List<MetadataFact>>() {
            @Override
            public List<MetadataFact> run(Connection connection) throws SQLException {
                return queryFacts(connection, " WHERE RESOURCE_ID = ? ORDER BY TYPE_ID, CREATED_AT, FACT_ID",
                        resource.value());
            }
        });
    }

    @Override
    public List<MetadataFact> factsOf(final KnowledgeResourceId resource, final FactTypeId type) {
        if (resource == null || type == null) {
            throw new IllegalArgumentException("resource and type must not be null");
        }
        return read("read " + type + " facts of " + resource, new SqlWork<List<MetadataFact>>() {
            @Override
            public List<MetadataFact> run(Connection connection) throws SQLException {
                return queryFacts(connection, " WHERE RESOURCE_ID = ? AND TYPE_ID = ? ORDER BY CREATED_AT, FACT_ID",
                        resource.value(), type.value());
            }
        });
    }

    @Override
    public List<MetadataFact> factsOfType(final FactTypeId type) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        return read("read " + type + " facts", new SqlWork<List<MetadataFact>>() {
            @Override
            public List<MetadataFact> run(Connection connection) throws SQLException {
                return queryFacts(connection, " WHERE TYPE_ID = ? ORDER BY RESOURCE_ID, CREATED_AT, FACT_ID",
                        type.value());
            }
        });
    }

    @Override
    public Set<KnowledgeResourceId> resourcesWithType(final FactTypeId type) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        return read("read resources with " + type, new SqlWork<Set<KnowledgeResourceId>>() {
            @Override
            public Set<KnowledgeResourceId> run(Connection connection) throws SQLException {
                Set<KnowledgeResourceId> result = new LinkedHashSet<KnowledgeResourceId>();
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT DISTINCT RESOURCE_ID FROM METADATA_FACT WHERE TYPE_ID = ? ORDER BY RESOURCE_ID")) {
                    select.setString(1, type.value());
                    try (ResultSet rows = select.executeQuery()) {
                        while (rows.next()) {
                            result.add(KnowledgeResourceId.of(rows.getString(1)));
                        }
                    }
                }
                return Collections.unmodifiableSet(result);
            }
        });
    }

    @Override
    public int replaceAutomaticFacts(KnowledgeResourceId resource, String extractorId, List<MetadataFact> facts) {
        if (resource == null || extractorId == null || facts == null) {
            throw new IllegalArgumentException("resource, extractor id and facts must not be null");
        }
        return replace(resource, extractorId.trim(), facts, null);
    }

    @Override
    public int replaceAutomaticFacts(ExtractionRun run, List<MetadataFact> facts) {
        if (run == null || facts == null) {
            throw new IllegalArgumentException("run and facts must not be null");
        }
        return replace(run.resourceId(), run.extractorId(), facts, run);
    }

    @Override
    public Optional<ExtractionRun> extractionRun(final KnowledgeResourceId resource, final String extractorId) {
        if (resource == null || extractorId == null) {
            throw new IllegalArgumentException("resource and extractor id must not be null");
        }
        return Optional.ofNullable(read("read run of " + extractorId + " on " + resource, new SqlWork<ExtractionRun>() {
            @Override
            public ExtractionRun run(Connection connection) throws SQLException {
                try (PreparedStatement select = connection.prepareStatement("SELECT * FROM EXTRACTION_RUN"
                        + " WHERE RESOURCE_ID = ? AND EXTRACTOR_ID = ?")) {
                    select.setString(1, resource.value());
                    select.setString(2, extractorId.trim());
                    try (ResultSet rows = select.executeQuery()) {
                        return rows.next() ? extractionRun(rows) : null;
                    }
                }
            }
        }));
    }

    private int replace(final KnowledgeResourceId resource, final String extractorId, List<MetadataFact> facts,
                        final ExtractionRun run) {
        final List<MetadataFact> replacement = new ArrayList<MetadataFact>(facts);
        for (MetadataFact fact : replacement) {
            if (fact == null || !fact.resourceId().equals(resource)
                    || fact.provenance().origin() != FactOrigin.AUTOMATIC
                    || !fact.provenance().extractorId().equals(extractorId)) {
                throw new IllegalArgumentException("replacement fact " + fact + " is no automatic fact of "
                        + extractorId + " on " + resource);
            }
        }
        return transaction("replace facts of " + extractorId + " on " + resource, new SqlWork<Integer>() {
            @Override
            public Integer run(Connection connection) throws SQLException {
                requireResource(connection, resource);
                Set<UUID> replaced = new LinkedHashSet<UUID>();
                try (PreparedStatement select = connection.prepareStatement("SELECT FACT_ID FROM METADATA_FACT"
                        + " WHERE RESOURCE_ID = ? AND ORIGIN = ? AND REVIEW = ? AND EXTRACTOR_ID = ? FOR UPDATE")) {
                    select.setString(1, resource.value());
                    select.setString(2, FactOrigin.AUTOMATIC.name());
                    select.setString(3, FactReview.UNREVIEWED.name());
                    select.setString(4, extractorId.trim());
                    try (ResultSet rows = select.executeQuery()) {
                        while (rows.next()) {
                            replaced.add(rows.getObject(1, UUID.class));
                        }
                    }
                }
                // An old fact that a remaining fact still refers to stays, with everything it refers to itself.
                replaced.removeAll(stillReferenced(connection, resource, replaced));
                deleteFacts(connection, replaced);
                for (MetadataFact fact : replacement) {
                    writeFact(connection, fact, true);
                }
                // All new facts exist now, so they may refer to each other regardless of their order.
                Map<FactTypeId, ArgumentProjection> projections = new HashMap<FactTypeId, ArgumentProjection>();
                for (MetadataFact fact : replacement) {
                    writeArguments(connection, fact.id(), fact.resourceId(), fact.typeId(), fact.payload(),
                            projections);
                }
                if (run != null) {
                    writeRun(connection, run);
                }
                return replaced.size();
            }
        });
    }

    // ------------------------------------------------------------ arguments

    @Override
    public List<FactArgument> argumentsOf(final FactId id) {
        if (id == null) {
            throw new IllegalArgumentException("fact id must not be null");
        }
        return read("read arguments of " + id, new SqlWork<List<FactArgument>>() {
            @Override
            public List<FactArgument> run(Connection connection) throws SQLException {
                List<FactArgument> arguments = new ArrayList<FactArgument>();
                try (PreparedStatement select = connection.prepareStatement("SELECT ARGUMENT_ROLE, ARGUMENT_ORDINAL,"
                        + " VALUE_FACT_ID, NORMALIZED_VALUE FROM FACT_ARGUMENT WHERE FACT_ID = ?"
                        + " ORDER BY ARGUMENT_ROLE, ARGUMENT_ORDINAL")) {
                    select.setObject(1, id.value());
                    try (ResultSet rows = select.executeQuery()) {
                        while (rows.next()) {
                            UUID reference = rows.getObject(3, UUID.class);
                            arguments.add(FactArgument.of(rows.getString(1), rows.getInt(2),
                                    reference == null ? null : FactId.of(reference), rows.getString(4)));
                        }
                    }
                }
                return Collections.unmodifiableList(arguments);
            }
        });
    }

    @Override
    public List<MetadataFact> factsWithArguments(final FactTypeId type, final List<ArgumentCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("at least one argument condition is needed");
        }
        final List<ArgumentCondition> all = new ArrayList<ArgumentCondition>(conditions);
        return read("search facts by arguments " + all, new SqlWork<List<MetadataFact>>() {
            @Override
            public List<MetadataFact> run(Connection connection) throws SQLException {
                StringBuilder where = new StringBuilder(" WHERE ");
                List<String> parameters = new ArrayList<String>();
                for (int i = 0; i < all.size(); i++) {
                    ArgumentCondition condition = all.get(i);
                    if (condition == null) {
                        throw new IllegalArgumentException("argument condition must not be null");
                    }
                    where.append(i == 0 ? "" : " AND ").append(argumentCondition(condition));
                    parameters.add(condition.role());
                    parameters.add(condition.reference() == null ? condition.normalizedValue()
                            : condition.reference().toString());
                }
                if (type != null) {
                    where.append(" AND TYPE_ID = ?");
                    parameters.add(type.value());
                }
                where.append(" ORDER BY RESOURCE_ID, CREATED_AT, FACT_ID");
                return queryFacts(connection, where.toString(), parameters.toArray(new String[0]));
            }
        });
    }

    @Override
    public List<MetadataFact> factsReferencing(final FactId referenced) {
        if (referenced == null) {
            throw new IllegalArgumentException("fact id must not be null");
        }
        return read("read facts referencing " + referenced, new SqlWork<List<MetadataFact>>() {
            @Override
            public List<MetadataFact> run(Connection connection) throws SQLException {
                return queryFacts(connection, " WHERE FACT_ID IN (SELECT A.FACT_ID FROM FACT_ARGUMENT A"
                        + " WHERE A.VALUE_FACT_ID = ?) ORDER BY CREATED_AT, FACT_ID", referenced.toString());
            }
        });
    }

    @Override
    public int rebuildArguments() {
        return transaction("rebuild fact arguments", new SqlWork<Integer>() {
            @Override
            public Integer run(Connection connection) throws SQLException {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DELETE FROM FACT_ARGUMENT");
                }
                return deriveArguments(connection, "");
            }
        });
    }

    /** Index-backed condition on the facts' own id (the subquery runs on one of the argument indexes). */
    static String argumentCondition(ArgumentCondition condition) {
        return condition.reference() == null
                ? "FACT_ID IN (SELECT A.FACT_ID FROM FACT_ARGUMENT A WHERE A.ARGUMENT_ROLE = ?"
                + " AND A.NORMALIZED_VALUE = ?)"
                : "FACT_ID IN (SELECT A.FACT_ID FROM FACT_ARGUMENT A WHERE A.ARGUMENT_ROLE = ?"
                + " AND A.VALUE_FACT_ID = ?)";
    }

    @Override
    public int removeResource(final KnowledgeResourceId resource) {
        if (resource == null) {
            throw new IllegalArgumentException("resource must not be null");
        }
        return transaction("remove resource " + resource, new SqlWork<Integer>() {
            @Override
            public Integer run(Connection connection) throws SQLException {
                // Arguments refer within the resource only; dropping them first frees every fact of it.
                update(connection, "DELETE FROM FACT_ARGUMENT WHERE FACT_ID IN"
                        + " (SELECT FACT_ID FROM METADATA_FACT WHERE RESOURCE_ID = ?)", resource.value());
                int removed = update(connection, "DELETE FROM METADATA_FACT WHERE RESOURCE_ID = ?", resource.value());
                update(connection, "DELETE FROM EXTRACTION_RUN WHERE RESOURCE_ID = ?", resource.value());
                update(connection, "DELETE FROM RESOURCE_REF WHERE RESOURCE_ID = ?", resource.value());
                return removed;
            }
        });
    }

    @Override
    public int removeSource(final SourceAddress source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        return transaction("remove source " + source, new SqlWork<Integer>() {
            @Override
            public Integer run(Connection connection) throws SQLException {
                update(connection, "DELETE FROM FACT_ARGUMENT WHERE FACT_ID IN (SELECT FACT_ID FROM METADATA_FACT"
                        + " WHERE RESOURCE_ID IN (SELECT RESOURCE_ID FROM RESOURCE_REF WHERE SOURCE_ADDRESS = ?))",
                        source.value());
                int removed = update(connection, "DELETE FROM METADATA_FACT WHERE RESOURCE_ID IN"
                        + " (SELECT RESOURCE_ID FROM RESOURCE_REF WHERE SOURCE_ADDRESS = ?)", source.value());
                update(connection, "DELETE FROM EXTRACTION_RUN WHERE RESOURCE_ID IN"
                        + " (SELECT RESOURCE_ID FROM RESOURCE_REF WHERE SOURCE_ADDRESS = ?)", source.value());
                update(connection, "DELETE FROM RESOURCE_REF WHERE SOURCE_ADDRESS = ?", source.value());
                return removed;
            }
        });
    }

    /** Shut the database down; later calls fail with {@link Reason#STORAGE}. */
    @Override
    public void close() throws IOException {
        synchronized (lifecycle) {
            if (closed) {
                return;
            }
            closed = true;
            if (!ready) {
                return;
            }
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            } catch (SQLException e) {
                throw new IOException("Could not close the fact store in " + directory, e);
            }
        }
    }

    @Override
    public String toString() {
        return "H2FactStore[" + directory + "]";
    }

    // ------------------------------------------------------------ test hooks

    /** The execution plan H2 chooses for {@code sql} (tests prove the access paths). */
    String explain(final String sql, final String... parameters) {
        return read("explain", new SqlWork<String>() {
            @Override
            public String run(Connection connection) throws SQLException {
                try (PreparedStatement explain = connection.prepareStatement("EXPLAIN " + sql)) {
                    for (int i = 0; i < parameters.length; i++) {
                        explain.setString(i + 1, parameters[i]);
                    }
                    try (ResultSet rows = explain.executeQuery()) {
                        StringBuilder plan = new StringBuilder();
                        while (rows.next()) {
                            plan.append(rows.getString(1)).append('\n');
                        }
                        return plan.toString();
                    }
                }
            }
        });
    }

    /** A connection of its own, as a second process thread would use it (tests hold locks with it). */
    Connection openConnection() throws SQLException {
        return connection();
    }

    // ------------------------------------------------------------- internals

    private void writeFact(Connection connection, MetadataFact fact, boolean insertOnly) throws SQLException {
        requireResource(connection, fact.resourceId());
        int typeVersion = requireType(connection, fact.typeId());
        if (fact.schemaVersion() > typeVersion) {
            throw new FactStoreException(Reason.INCOMPATIBLE_SCHEMA, "Fact " + fact.id() + " uses schema version "
                    + fact.schemaVersion() + " of " + fact.typeId() + ", registered is " + typeVersion);
        }
        boolean exists = false;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT RESOURCE_ID FROM METADATA_FACT WHERE FACT_ID = ? FOR UPDATE")) {
            select.setObject(1, fact.id().value());
            try (ResultSet rows = select.executeQuery()) {
                if (rows.next()) {
                    exists = true;
                    if (insertOnly || !fact.resourceId().value().equals(rows.getString(1))) {
                        throw new FactStoreException(Reason.CONFLICT, "Fact " + fact.id()
                                + " already exists" + (insertOnly ? "" : " on resource " + rows.getString(1)));
                    }
                }
            }
        }
        Instant now = clock.instant();
        String sql = exists
                ? "UPDATE METADATA_FACT SET TYPE_ID = ?, SCHEMA_VERSION = ?, PAYLOAD = ? FORMAT JSON, FACT_ROLE = ?,"
                + " ORIGIN = ?, REVIEW = ?, EXTRACTOR_ID = ?, EXTRACTOR_VERSION = ?, SOURCE_VERSION = ?,"
                + " SOURCE_MODIFIED_AT = ?, CONTENT_HASH = ?, ANCHOR_SECTION = ?, ANCHOR_PATH = ?,"
                + " ANCHOR_LINE_START = ?, ANCHOR_LINE_END = ?, ANCHOR_CHAR_START = ?, ANCHOR_CHAR_END = ?,"
                + " ANCHOR_QUOTE = ?, ANCHOR_FINGERPRINT = ?, UPDATED_AT = ? WHERE FACT_ID = ?"
                : "INSERT INTO METADATA_FACT (TYPE_ID, SCHEMA_VERSION, PAYLOAD, FACT_ROLE, ORIGIN, REVIEW,"
                + " EXTRACTOR_ID, EXTRACTOR_VERSION, SOURCE_VERSION, SOURCE_MODIFIED_AT, CONTENT_HASH,"
                + " ANCHOR_SECTION, ANCHOR_PATH, ANCHOR_LINE_START, ANCHOR_LINE_END, ANCHOR_CHAR_START,"
                + " ANCHOR_CHAR_END, ANCHOR_QUOTE, ANCHOR_FINGERPRINT, UPDATED_AT, FACT_ID, RESOURCE_ID, CREATED_AT)"
                + " VALUES (?, ?, ? FORMAT JSON, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement write = connection.prepareStatement(sql)) {
            FactProvenance provenance = fact.provenance();
            FactAnchor anchor = fact.anchor();
            int i = 0;
            write.setString(++i, fact.typeId().value());
            write.setInt(++i, fact.schemaVersion());
            write.setString(++i, fact.payload().json());
            write.setString(++i, fact.role() == null ? null : fact.role().value());
            write.setString(++i, provenance.origin().name());
            write.setString(++i, fact.review().name());
            write.setString(++i, provenance.extractorId());
            write.setString(++i, provenance.extractorVersion());
            write.setString(++i, provenance.sourceRevision().version());
            Instant modifiedAt = provenance.sourceRevision().modifiedAt().orElse(null);
            setInstant(write, ++i, modifiedAt);
            write.setString(++i, provenance.contentHash());
            write.setString(++i, anchor == null ? null : emptyAsNull(anchor.section()));
            write.setString(++i, anchor == null ? null : emptyAsNull(anchor.structurePath()));
            setInteger(write, ++i, anchor == null ? null : anchor.lineStart());
            setInteger(write, ++i, anchor == null ? null : anchor.lineEnd());
            setInteger(write, ++i, anchor == null ? null : anchor.charStart());
            setInteger(write, ++i, anchor == null ? null : anchor.charEnd());
            write.setString(++i, anchor == null ? null : emptyAsNull(anchor.quote()));
            write.setString(++i, anchor == null ? null : emptyAsNull(anchor.fingerprint()));
            setInstant(write, ++i, now);
            write.setObject(++i, fact.id().value());
            if (!exists) {
                write.setString(++i, fact.resourceId().value());
                setInstant(write, ++i, now);
            }
            write.executeUpdate();
        } catch (SQLException e) {
            if (isInvalidValue(e)) {
                throw new FactStoreException(Reason.INVALID_PAYLOAD, "Payload of fact " + fact.id()
                        + " is no valid JSON or too large: " + e.getMessage(), e);
            }
            throw e;
        }
    }

    private void writeRun(Connection connection, ExtractionRun run) throws SQLException {
        FactProvenance provenance = run.provenance();
        try (PreparedStatement merge = connection.prepareStatement("MERGE INTO EXTRACTION_RUN (RESOURCE_ID,"
                + " EXTRACTOR_ID, EXTRACTOR_VERSION, SOURCE_VERSION, SOURCE_MODIFIED_AT, CONTENT_HASH, COMPLETED_AT)"
                + " KEY (RESOURCE_ID, EXTRACTOR_ID) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            merge.setString(++i, run.resourceId().value());
            merge.setString(++i, provenance.extractorId());
            merge.setString(++i, provenance.extractorVersion());
            merge.setString(++i, provenance.sourceRevision().version());
            setInstant(merge, ++i, provenance.sourceRevision().modifiedAt().orElse(null));
            merge.setString(++i, provenance.contentHash());
            setInstant(merge, ++i, clock.instant());
            merge.executeUpdate();
        }
    }

    private static ExtractionRun extractionRun(ResultSet rows) throws SQLException {
        KnowledgeRevision revision = KnowledgeRevision.of(instant(rows, "SOURCE_MODIFIED_AT"),
                rows.getString("SOURCE_VERSION"));
        return ExtractionRun.of(KnowledgeResourceId.of(rows.getString("RESOURCE_ID")),
                FactProvenance.of(FactOrigin.AUTOMATIC, rows.getString("EXTRACTOR_ID"),
                        rows.getString("EXTRACTOR_VERSION"), revision, rows.getString("CONTENT_HASH")),
                instant(rows, "COMPLETED_AT"));
    }

    /**
     * Replace the arguments of one fact by those its type projects from the payload, after checking every reference:
     * an existing other fact on the same resource, of the type the rule requires.
     *
     * @return number of arguments written
     */
    private static int writeArguments(Connection connection, FactId id, KnowledgeResourceId resource,
                                      FactTypeId type, FactPayload payload,
                                      Map<FactTypeId, ArgumentProjection> projections) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM FACT_ARGUMENT WHERE FACT_ID = ?")) {
            delete.setObject(1, id.value());
            delete.executeUpdate();
        }
        ArgumentProjection projection = projections.get(type);
        if (projection == null) {
            projection = projectionOf(connection, type);
            projections.put(type, projection);
        }
        List<ArgumentProjection.Projected> arguments;
        try {
            arguments = projection.projectWithTypes(payload);
        } catch (IllegalArgumentException e) {
            throw new FactStoreException(Reason.INVALID_PAYLOAD, "Arguments of fact " + id + " cannot be derived: "
                    + e.getMessage(), e);
        }
        if (arguments.isEmpty()) {
            return 0;
        }
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO FACT_ARGUMENT (FACT_ID,"
                + " ARGUMENT_ROLE, ARGUMENT_ORDINAL, VALUE_FACT_ID, NORMALIZED_VALUE) VALUES (?, ?, ?, ?, ?)")) {
            for (ArgumentProjection.Projected projected : arguments) {
                FactArgument argument = projected.argument();
                if (argument.valueFactId() != null) {
                    checkReference(connection, id, resource, argument, projected.referenceType());
                }
                insert.setObject(1, id.value());
                insert.setString(2, argument.role());
                insert.setInt(3, argument.ordinal());
                if (argument.valueFactId() == null) {
                    insert.setNull(4, Types.OTHER);
                } else {
                    insert.setObject(4, argument.valueFactId().value());
                }
                insert.setString(5, argument.normalizedValue());
                insert.addBatch();
            }
            insert.executeBatch();
        }
        return arguments.size();
    }

    private static void checkReference(Connection connection, FactId owner, KnowledgeResourceId resource,
                                       FactArgument argument, FactTypeId requiredType) throws SQLException {
        if (argument.valueFactId().equals(owner)) {
            throw new FactStoreException(Reason.INVALID_REFERENCE, "Fact " + owner + " refers to itself in "
                    + argument.role());
        }
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT RESOURCE_ID, TYPE_ID FROM METADATA_FACT WHERE FACT_ID = ?")) {
            select.setObject(1, argument.valueFactId().value());
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    throw new FactStoreException(Reason.INVALID_REFERENCE, "Fact " + owner + " refers in "
                            + argument.role() + " to the unknown fact " + argument.valueFactId());
                }
                if (!resource.value().equals(rows.getString(1))) {
                    throw new FactStoreException(Reason.INVALID_REFERENCE, "Fact " + owner + " refers in "
                            + argument.role() + " to a fact of another resource");
                }
                if (requiredType != null && !requiredType.value().equals(rows.getString(2))) {
                    throw new FactStoreException(Reason.INVALID_REFERENCE, "Fact " + owner + " refers in "
                            + argument.role() + " to a " + rows.getString(2) + " fact, expected " + requiredType);
                }
            }
        }
    }

    private static ArgumentProjection projectionOf(Connection connection, FactTypeId type) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT ARGUMENT_PROJECTION FROM FACT_TYPE WHERE TYPE_ID = ?")) {
            select.setString(1, type.value());
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? ArgumentProjection.parse(rows.getString(1)) : ArgumentProjection.none();
            }
        }
    }

    /**
     * Derive the arguments of the stored facts selected by {@code where} (their old arguments must be gone already).
     *
     * @return number of arguments written
     */
    private static int deriveArguments(Connection connection, String where, String... parameters)
            throws SQLException {
        Map<FactTypeId, ArgumentProjection> projections = new HashMap<FactTypeId, ArgumentProjection>();
        int written = 0;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT FACT_ID, RESOURCE_ID, TYPE_ID, PAYLOAD FROM METADATA_FACT" + where)) {
            for (int i = 0; i < parameters.length; i++) {
                select.setString(i + 1, parameters[i]);
            }
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    written += writeArguments(connection, FactId.of(rows.getObject(1, UUID.class)),
                            KnowledgeResourceId.of(rows.getString(2)), FactTypeId.of(rows.getString(3)),
                            FactPayload.json(rows.getString(4)), projections);
                }
            }
        }
        return written;
    }

    /**
     * The facts of {@code candidates} that a fact outside them still refers to, directly or through other candidates
     * (references stay within one resource).
     */
    private static Set<UUID> stillReferenced(Connection connection, KnowledgeResourceId resource, Set<UUID> candidates)
            throws SQLException {
        Set<UUID> kept = new HashSet<UUID>();
        if (candidates.isEmpty()) {
            return kept;
        }
        Map<UUID, List<UUID>> references = new HashMap<UUID, List<UUID>>();
        try (PreparedStatement select = connection.prepareStatement("SELECT A.FACT_ID, A.VALUE_FACT_ID"
                + " FROM FACT_ARGUMENT A JOIN METADATA_FACT F ON F.FACT_ID = A.FACT_ID"
                + " WHERE F.RESOURCE_ID = ? AND A.VALUE_FACT_ID IS NOT NULL")) {
            select.setString(1, resource.value());
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    UUID owner = rows.getObject(1, UUID.class);
                    List<UUID> targets = references.get(owner);
                    if (targets == null) {
                        targets = new ArrayList<UUID>();
                        references.put(owner, targets);
                    }
                    targets.add(rows.getObject(2, UUID.class));
                }
            }
        }
        List<UUID> pending = new ArrayList<UUID>();
        for (Map.Entry<UUID, List<UUID>> entry : references.entrySet()) {
            if (!candidates.contains(entry.getKey())) {
                pending.addAll(entry.getValue());
            }
        }
        while (!pending.isEmpty()) {
            UUID target = pending.remove(pending.size() - 1);
            if (candidates.contains(target) && kept.add(target) && references.containsKey(target)) {
                pending.addAll(references.get(target));
            }
        }
        return kept;
    }

    /** Remove the given facts of one batch: their arguments first, so references among them do not block. */
    private static void deleteFacts(Connection connection, Set<UUID> ids) throws SQLException {
        if (ids.isEmpty()) {
            return;
        }
        try (PreparedStatement arguments = connection.prepareStatement("DELETE FROM FACT_ARGUMENT WHERE FACT_ID = ?")) {
            for (UUID id : ids) {
                arguments.setObject(1, id);
                arguments.addBatch();
            }
            arguments.executeBatch();
        }
        try (PreparedStatement facts = connection.prepareStatement("DELETE FROM METADATA_FACT WHERE FACT_ID = ?")) {
            for (UUID id : ids) {
                facts.setObject(1, id);
                facts.addBatch();
            }
            facts.executeBatch();
        }
    }

    private static boolean isInvalidValue(SQLException e) {
        int code = e.getErrorCode();
        return code == ErrorCode.DATA_CONVERSION_ERROR_1 || code == ErrorCode.INVALID_VALUE_2
                || code == ErrorCode.VALUE_TOO_LONG_2;
    }

    private static void requireResource(Connection connection, KnowledgeResourceId resource) throws SQLException {
        if (!exists(connection, "SELECT 1 FROM RESOURCE_REF WHERE RESOURCE_ID = ?", resource.value())) {
            throw new FactStoreException(Reason.UNKNOWN_RESOURCE, "Resource " + resource + " is not registered");
        }
    }

    /** @return the current schema version of the type */
    private static int requireType(Connection connection, FactTypeId type) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT SCHEMA_VERSION FROM FACT_TYPE WHERE TYPE_ID = ?")) {
            select.setString(1, type.value());
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) {
                    throw new FactStoreException(Reason.UNKNOWN_TYPE, "Fact type " + type + " is not registered");
                }
                return rows.getInt(1);
            }
        }
    }

    private static MetadataFact loadFact(Connection connection, FactId id) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(SELECT_FACTS + " WHERE FACT_ID = ?")) {
            select.setObject(1, id.value());
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? fact(rows) : null;
            }
        }
    }

    private static List<MetadataFact> queryFacts(Connection connection, String where, String... parameters)
            throws SQLException {
        List<MetadataFact> facts = new ArrayList<MetadataFact>();
        try (PreparedStatement select = connection.prepareStatement(SELECT_FACTS + where)) {
            for (int i = 0; i < parameters.length; i++) {
                select.setString(i + 1, parameters[i]);
            }
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    facts.add(fact(rows));
                }
            }
        }
        return Collections.unmodifiableList(facts);
    }

    private static List<FactType> queryTypes(Connection connection, String where, String... parameters)
            throws SQLException {
        List<FactType> types = new ArrayList<FactType>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT TYPE_ID, DESCRIPTION, SCHEMA_VERSION, JSON_SCHEMA, ARGUMENT_PROJECTION FROM FACT_TYPE"
                        + where)) {
            for (int i = 0; i < parameters.length; i++) {
                select.setString(i + 1, parameters[i]);
            }
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    types.add(FactType.of(FactTypeId.of(rows.getString(1)), rows.getString(2), rows.getInt(3),
                            rows.getString(4), ArgumentProjection.parse(rows.getString(5))));
                }
            }
        }
        return Collections.unmodifiableList(types);
    }

    private static MetadataFact fact(ResultSet rows) throws SQLException {
        KnowledgeRevision revision = KnowledgeRevision.of(instant(rows, "SOURCE_MODIFIED_AT"),
                rows.getString("SOURCE_VERSION"));
        FactProvenance provenance = FactProvenance.of(FactOrigin.valueOf(rows.getString("ORIGIN")),
                rows.getString("EXTRACTOR_ID"), rows.getString("EXTRACTOR_VERSION"), revision,
                rows.getString("CONTENT_HASH"));
        String role = rows.getString("FACT_ROLE");
        return MetadataFact.builder(KnowledgeResourceId.of(rows.getString("RESOURCE_ID")),
                        FactTypeId.of(rows.getString("TYPE_ID")), FactPayload.json(rows.getString("PAYLOAD")),
                        provenance)
                .id(FactId.of(rows.getObject("FACT_ID", UUID.class)))
                .schemaVersion(rows.getInt("SCHEMA_VERSION"))
                .role(role == null ? null : FactRole.of(role))
                .review(FactReview.valueOf(rows.getString("REVIEW")))
                .anchor(anchor(rows))
                .timestamps(instant(rows, "CREATED_AT"), instant(rows, "UPDATED_AT"))
                .build();
    }

    private static FactAnchor anchor(ResultSet rows) throws SQLException {
        String section = rows.getString("ANCHOR_SECTION");
        String path = rows.getString("ANCHOR_PATH");
        Integer lineStart = rows.getObject("ANCHOR_LINE_START", Integer.class);
        Integer lineEnd = rows.getObject("ANCHOR_LINE_END", Integer.class);
        Integer charStart = rows.getObject("ANCHOR_CHAR_START", Integer.class);
        Integer charEnd = rows.getObject("ANCHOR_CHAR_END", Integer.class);
        String quote = rows.getString("ANCHOR_QUOTE");
        String fingerprint = rows.getString("ANCHOR_FINGERPRINT");
        if (section == null && path == null && lineStart == null && charStart == null && quote == null
                && fingerprint == null) {
            return null;
        }
        return FactAnchor.builder().section(section).structurePath(path).lines(lineStart, lineEnd)
                .characters(charStart, charEnd).quote(quote).fingerprint(fingerprint).build();
    }

    private static boolean exists(Connection connection, String sql, String parameter) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            select.setString(1, parameter);
            try (ResultSet rows = select.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static int update(Connection connection, String sql, String parameter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            return statement.executeUpdate();
        }
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        OffsetDateTime value = rows.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            statement.setObject(index, OffsetDateTime.ofInstant(value, ZoneOffset.UTC));
        }
    }

    private static void setInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static String emptyAsNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /** Run an idempotent registration; a lost insert race (duplicate key) is retried once. */
    private void retryingRegistration(String what, SqlWork<Void> work) {
        for (int attempt = 1; ; attempt++) {
            try {
                transaction(what, work);
                return;
            } catch (FactStoreException e) {
                if (e.reason() != Reason.CONFLICT || attempt >= REGISTRATION_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    private <T> T transaction(String what, SqlWork<T> work) {
        Connection connection = open(what);
        try {
            connection.setAutoCommit(false);
            T result = work.run(connection);
            connection.commit();
            return result;
        } catch (SQLException e) {
            rollback(connection);
            throw failure(what, e);
        } catch (RuntimeException e) {
            rollback(connection);
            throw e;
        } finally {
            closeQuietly(connection);
        }
    }

    private <T> T read(String what, SqlWork<T> work) {
        Connection connection = open(what);
        try {
            return work.run(connection);
        } catch (SQLException e) {
            throw failure(what, e);
        } finally {
            closeQuietly(connection);
        }
    }

    private Connection open(String what) {
        try {
            return connection();
        } catch (SQLException e) {
            throw failure(what, e);
        }
    }

    private Connection connection() throws SQLException {
        synchronized (lifecycle) {
            if (closed) {
                throw new FactStoreException(Reason.STORAGE, "The fact store in " + directory + " is closed");
            }
            if (!ready) {
                try {
                    Files.createDirectories(directory);
                } catch (IOException e) {
                    throw new FactStoreException(Reason.STORAGE, "Cannot create the fact store directory "
                            + directory, e);
                }
                try (Connection connection = dataSource.getConnection()) {
                    FactSchema.migrate(connection);
                }
                ready = true;
            }
        }
        return dataSource.getConnection();
    }

    private FactStoreException failure(String what, SQLException e) {
        int code = e.getErrorCode();
        Reason reason;
        if (code == ErrorCode.LOCK_TIMEOUT_1 || code == ErrorCode.DEADLOCK_1) {
            reason = Reason.LOCK_TIMEOUT;
        } else if (code == ErrorCode.DUPLICATE_KEY_1 || code == ErrorCode.CONCURRENT_UPDATE_1) {
            reason = Reason.CONFLICT;
        } else if (code == ErrorCode.REFERENTIAL_INTEGRITY_VIOLATED_PARENT_MISSING_1) {
            String message = String.valueOf(e.getMessage());
            reason = message.contains("FK_FACT_TYPE") ? Reason.UNKNOWN_TYPE
                    : message.contains("FK_ARGUMENT_VALUE") ? Reason.INVALID_REFERENCE : Reason.UNKNOWN_RESOURCE;
        } else if (code == ErrorCode.REFERENTIAL_INTEGRITY_VIOLATED_CHILD_EXISTS_1) {
            reason = Reason.CONFLICT; // still referenced by an argument of another fact
        } else {
            reason = Reason.STORAGE;
        }
        return new FactStoreException(reason, "Fact store could not " + what + " (" + directory + "): "
                + e.getMessage(), e);
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // The original failure is reported; a broken connection is closed next.
        }
    }

    private static void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Nothing to report: the work is done or its failure is already reported.
        }
    }

    /** One unit of JDBC work on a connection owned by the caller. */
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }
}
