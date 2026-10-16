package com.aresstack.enterpriseai.memory.h2;

import com.aresstack.enterpriseai.memory.api.Memory;
import com.aresstack.enterpriseai.memory.api.MemoryId;
import com.aresstack.enterpriseai.memory.api.MemoryOwner;
import com.aresstack.enterpriseai.memory.api.MemorySaved;
import com.aresstack.enterpriseai.memory.api.MemoryScope;
import com.aresstack.enterpriseai.memory.api.MemoryStoreException;
import com.aresstack.enterpriseai.memory.api.MemoryStorePort;
import org.h2.api.ErrorCode;
import org.h2.jdbcx.JdbcDataSource;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * {@link MemoryStorePort} on one embedded H2 database ({@code memories.mv.db}). Opened lazily on first use; every
 * write runs in its own transaction.
 */
public final class H2MemoryStore implements MemoryStorePort, Closeable {

    static final String DATABASE_NAME = "memories";
    static final int MAX_CONTENT_LENGTH = 4000;

    private static final String COLUMNS = "MEMORY_NUMBER, SCOPE, OWNER_KEY, CONTENT, CREATED_AT, ROW_VERSION";

    private final Path directory;
    private final Clock clock;
    private final JdbcDataSource dataSource;
    private final Object lifecycle = new Object();
    private boolean ready;
    private boolean closed;

    /**
     * @param directory         directory of the database file (created on first use)
     * @param lockTimeoutMillis how long a writer waits for a row locked by another transaction
     */
    public H2MemoryStore(Path directory, int lockTimeoutMillis) {
        this(directory, lockTimeoutMillis, Clock.systemUTC());
    }

    H2MemoryStore(Path directory, int lockTimeoutMillis, Clock clock) {
        if (directory == null || clock == null) {
            throw new IllegalArgumentException("directory and clock must not be null");
        }
        if (lockTimeoutMillis < 0) {
            throw new IllegalArgumentException("lock timeout must not be negative: " + lockTimeoutMillis);
        }
        String file = directory.toAbsolutePath().resolve(DATABASE_NAME).toString().replace('\\', '/');
        if (file.indexOf(';') >= 0) {
            throw new IllegalArgumentException("memory store directory must not contain ';': " + directory);
        }
        this.directory = directory.toAbsolutePath();
        this.clock = clock;
        this.dataSource = new JdbcDataSource();
        this.dataSource.setURL("jdbc:h2:file:" + file + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=" + lockTimeoutMillis);
    }

    public Path directory() {
        return directory;
    }

    @Override
    public MemorySaved save(MemoryOwner owner, String content) {
        if (owner == null) {
            throw new IllegalArgumentException("owner must not be null");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("content must not be empty");
        }
        String text = content.trim();
        if (text.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("content too long: " + text.length() + " > " + MAX_CONTENT_LENGTH);
        }
        String hash = sha256(text);
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                Memory existing = findByContent(connection, owner, hash);
                if (existing != null) {
                    connection.commit();
                    return new MemorySaved(existing, true);
                }
                long number;
                try (Statement statement = connection.createStatement();
                     ResultSet rows = statement.executeQuery("SELECT NEXT VALUE FOR MEMORY_NUMBER")) {
                    rows.next();
                    number = rows.getLong(1);
                }
                Instant now = clock.instant();
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO MEMORY(" + COLUMNS
                        + ", CONTENT_HASH, UPDATED_AT) VALUES (?, ?, ?, ?, ?, 1, ?, ?)")) {
                    insert.setLong(1, number);
                    insert.setString(2, owner.scope().name());
                    insert.setString(3, owner.key());
                    insert.setString(4, text);
                    insert.setObject(5, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
                    insert.setString(6, hash);
                    insert.setObject(7, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
                    insert.executeUpdate();
                }
                connection.commit();
                return new MemorySaved(new Memory(MemoryId.of(number), owner, text, now, 1L), false);
            } catch (SQLException e) {
                connection.rollback();
                if (e.getErrorCode() == ErrorCode.DUPLICATE_KEY_1) {
                    // A concurrent save of the same content committed first: report that one, write nothing.
                    Memory existing = findByContent(connection, owner, hash);
                    if (existing != null) {
                        return new MemorySaved(existing, true);
                    }
                }
                throw e;
            }
        } catch (SQLException e) {
            throw failure("save memory", e);
        }
    }

    @Override
    public List<Memory> search(Collection<MemoryOwner> owners, String query, int limit) {
        List<Memory> result = new ArrayList<Memory>();
        if (owners == null || owners.isEmpty() || limit < 1) {
            return result;
        }
        List<MemoryOwner> distinct = new ArrayList<MemoryOwner>(new LinkedHashSet<MemoryOwner>(owners));
        List<String> words = words(query);
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS).append(" FROM MEMORY WHERE (");
        for (int i = 0; i < distinct.size(); i++) {
            sql.append(i == 0 ? "" : " OR ").append("(SCOPE = ? AND OWNER_KEY = ?)");
        }
        sql.append(')');
        for (int i = 0; i < words.size(); i++) {
            sql.append(" AND LOWER(CONTENT) LIKE ? ESCAPE '\\'");
        }
        sql.append(" ORDER BY MEMORY_NUMBER DESC FETCH FIRST ? ROWS ONLY");
        try (Connection connection = connection();
             PreparedStatement select = connection.prepareStatement(sql.toString())) {
            int index = 1;
            for (MemoryOwner owner : distinct) {
                select.setString(index++, owner.scope().name());
                select.setString(index++, owner.key());
            }
            for (String word : words) {
                select.setString(index++, "%" + escapeLike(word) + "%");
            }
            select.setInt(index, limit);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    result.add(memory(rows));
                }
            }
            return result;
        } catch (SQLException e) {
            throw failure("search memories", e);
        }
    }

    @Override
    public Memory find(MemoryId id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        try (Connection connection = connection();
             PreparedStatement select = connection.prepareStatement("SELECT " + COLUMNS
                     + " FROM MEMORY WHERE MEMORY_NUMBER = ?")) {
            select.setLong(1, id.number());
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? memory(rows) : null;
            }
        } catch (SQLException e) {
            throw failure("find memory " + id, e);
        }
    }

    @Override
    public boolean delete(MemoryId id, long expectedVersion) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        try (Connection connection = connection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM MEMORY WHERE MEMORY_NUMBER = ? AND ROW_VERSION = ?")) {
            delete.setLong(1, id.number());
            delete.setLong(2, expectedVersion);
            return delete.executeUpdate() == 1;
        } catch (SQLException e) {
            throw failure("delete memory " + id, e);
        }
    }

    @Override
    public int deleteAll(MemoryOwner owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner must not be null");
        }
        try (Connection connection = connection();
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM MEMORY WHERE SCOPE = ? AND OWNER_KEY = ?")) {
            delete.setString(1, owner.scope().name());
            delete.setString(2, owner.key());
            return delete.executeUpdate();
        } catch (SQLException e) {
            throw failure("delete memories of " + owner.scope().wireName(), e);
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
                throw failure("close the memory store", e);
            }
        }
    }

    private Memory findByContent(Connection connection, MemoryOwner owner, String hash) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT " + COLUMNS
                + " FROM MEMORY WHERE SCOPE = ? AND OWNER_KEY = ? AND CONTENT_HASH = ?")) {
            select.setString(1, owner.scope().name());
            select.setString(2, owner.key());
            select.setString(3, hash);
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? memory(rows) : null;
            }
        }
    }

    private static Memory memory(ResultSet rows) throws SQLException {
        MemoryScope scope = MemoryScope.valueOf(rows.getString(2));
        OffsetDateTime created = rows.getObject(5, OffsetDateTime.class);
        return new Memory(MemoryId.of(rows.getLong(1)), MemoryOwner.of(scope, rows.getString(3)),
                rows.getString(4), created.toInstant(), rows.getLong(6));
    }

    private static List<String> words(String query) {
        List<String> words = new ArrayList<String>();
        if (query == null) {
            return words;
        }
        for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!word.isEmpty() && words.size() < 16) {
                words.add(word);
            }
        }
        return words;
    }

    private static String escapeLike(String word) {
        return word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private Connection connection() throws SQLException {
        synchronized (lifecycle) {
            if (closed) {
                throw new MemoryStoreException("The memory store in " + directory + " is closed");
            }
            if (!ready) {
                try {
                    Files.createDirectories(directory);
                } catch (IOException e) {
                    throw new MemoryStoreException("Cannot create the memory store directory " + directory, e);
                }
                try (Connection connection = dataSource.getConnection()) {
                    MemorySchema.migrate(connection);
                }
                ready = true;
            }
        }
        return dataSource.getConnection();
    }

    private static MemoryStoreException failure(String what, SQLException e) {
        // Never include content in the message: memories must not leak into logs.
        return new MemoryStoreException("Cannot " + what + " (H2 error " + e.getErrorCode() + ")", e);
    }
}
