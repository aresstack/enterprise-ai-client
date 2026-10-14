package com.aresstack.enterpriseai.fact.h2;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.fact.api.ExtractionRun;
import com.aresstack.enterpriseai.fact.api.FactAnchor;
import com.aresstack.enterpriseai.fact.api.FactFreshness;
import com.aresstack.enterpriseai.fact.api.FactId;
import com.aresstack.enterpriseai.fact.api.FactPayload;
import com.aresstack.enterpriseai.fact.api.FactProvenance;
import com.aresstack.enterpriseai.fact.api.FactReview;
import com.aresstack.enterpriseai.fact.api.FactRole;
import com.aresstack.enterpriseai.fact.api.FactStoreException;
import com.aresstack.enterpriseai.fact.api.FactStoreException.Reason;
import com.aresstack.enterpriseai.fact.api.FactType;
import com.aresstack.enterpriseai.fact.api.FactTypeId;
import com.aresstack.enterpriseai.fact.api.MetadataFact;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class H2FactStoreTest {

    private static final int LOCK_TIMEOUT_MILLIS = 300;

    private static final FactTypeId ACRONYM = FactTypeId.of("ACRONYM");
    private static final FactTypeId BUSINESS_RULE = FactTypeId.of("BUSINESS_RULE");
    private static final FactTypeId DEFINITION = FactTypeId.of("DEFINITION");

    private static final SourceAddress WIKI = SourceAddress.of("wiki://wiki.example/w");
    private static final SourceAddress CONFLUENCE = SourceAddress.of("confluence://cf.example/confluence");
    private static final KnowledgeResourceId GLOSSARY = KnowledgeResourceId.of("wiki:wiki.example/w/Glossar");
    private static final KnowledgeResourceId RULES = KnowledgeResourceId.of("confluence:cf.example/confluence/page/42");
    private static final KnowledgeResourceId PROGRAM = KnowledgeResourceId.of("ndv:host.example:2700/LIB/PGM1");

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final TickingClock clock = new TickingClock(Instant.parse("2026-10-14T08:00:00.123456789Z"));
    private Path directory;
    private H2FactStore store;

    @Before
    public void openStore() throws Exception {
        directory = folder.newFolder("facts").toPath();
        store = new H2FactStore(directory, LOCK_TIMEOUT_MILLIS, clock);
        store.registerType(FactType.of(ACRONYM, "Acronym and its expansion"));
        store.registerType(FactType.of(BUSINESS_RULE, "Business rule"));
        store.registerResource(GLOSSARY, WIKI);
        store.registerResource(RULES, CONFLUENCE);
    }

    @After
    public void closeStore() throws Exception {
        store.close();
    }

    // ---------------------------------------------------------------- types

    @Test
    public void registersTypesAtRuntimeWithoutSchemaChange() throws Exception {
        store.registerType(FactType.of(DEFINITION, "Definition of a term"));
        store.registerType(FactType.of(FactTypeId.of("DATA_STRUCTURE"), "Record layout", 1,
                "{\"type\":\"object\"}"));

        assertEquals(Arrays.asList("ACRONYM", "BUSINESS_RULE", "DATA_STRUCTURE", "DEFINITION"), typeNames());
        assertEquals("{\"type\":\"object\"}", store.type(FactTypeId.of("DATA_STRUCTURE")).get().jsonSchema());
        // RESOURCE_REF, FACT_TYPE, METADATA_FACT, EXTRACTION_RUN (#107) and FACT_ARGUMENT (#108); types add no table.
        assertEquals(5, tableCount());
    }

    @Test
    public void typeRegistrationNeverOverwritesAnIncompatibleSchema() {
        FactType v1 = FactType.of(DEFINITION, "Definition", 1, "{\"required\":[\"term\"]}");
        store.registerType(v1);
        store.registerType(v1);
        store.registerType(FactType.of(DEFINITION, "Definition of a term", 1, "{\"required\":[\"term\"]}"));
        assertEquals("Definition of a term", store.type(DEFINITION).get().description());

        expect(Reason.INCOMPATIBLE_SCHEMA, new Runnable() {
            @Override
            public void run() {
                store.registerType(FactType.of(DEFINITION, "Definition", 1, "{\"required\":[\"name\"]}"));
            }
        });
        FactType v2 = FactType.of(DEFINITION, "Definition", 2, "{\"required\":[\"term\",\"text\"]}");
        store.registerType(v2);
        assertEquals(v2, store.type(DEFINITION).get());
        expect(Reason.INCOMPATIBLE_SCHEMA, new Runnable() {
            @Override
            public void run() {
                store.registerType(FactType.of(DEFINITION, "Definition", 1, "{\"required\":[\"term\"]}"));
            }
        });
    }

    @Test
    public void factsOfOlderSchemaVersionsStayReadable() {
        store.registerType(FactType.of(DEFINITION, "Definition", 1, null));
        MetadataFact old = store.save(fact(GLOSSARY, DEFINITION, "{\"term\":\"Batch\"}"));
        store.registerType(FactType.of(DEFINITION, "Definition", 2, "{\"required\":[\"term\",\"text\"]}"));
        final MetadataFact newer = store.save(fact(GLOSSARY, DEFINITION, "{\"term\":\"Job\",\"text\":\"Unit\"}")
                .toBuilder().schemaVersion(2).build());

        assertEquals(Arrays.asList(old, newer), store.factsOf(GLOSSARY, DEFINITION));
        expect(Reason.INCOMPATIBLE_SCHEMA, new Runnable() {
            @Override
            public void run() {
                store.save(newer.toBuilder().id(FactId.random()).schemaVersion(3).build());
            }
        });
    }

    // ---------------------------------------------------------------- facts

    @Test
    public void factRoundTripsWithUuidJsonAnchorAndProvenance() {
        FactAnchor anchor = FactAnchor.builder().section("Abkuerzungen").structurePath("Glossar/A")
                .lines(12, 14).characters(340, 389).quote("IAR steht fuer").fingerprint("sha1:0f0f").build();
        MetadataFact draft = MetadataFact.builder(GLOSSARY, ACRONYM,
                        FactPayload.json("{\"acronym\":\"IAR\",\"senses\":[{\"text\":\"Beispiel\",\"weight\":0.5}],"
                                + "\"meta\":{\"umlaut\":\"äöü\",\"empty\":{},\"flag\":true,\"none\":null}}"),
                        FactProvenance.automatic("acronym-extractor", "1.2.0")
                                .withSourceRevision(KnowledgeRevision.of(
                                        Instant.parse("2026-10-01T07:30:00.987654321Z"), "1234"))
                                .withContentHash("sha256:abc"))
                .role(FactRole.defines()).anchor(anchor).build();

        MetadataFact saved = store.save(draft);

        assertEquals(draft, saved);
        assertNotNull(saved.createdAt());
        assertEquals(saved.createdAt(), saved.updatedAt());
        // Nanosecond precision survives the database.
        assertEquals(clock.last(), saved.createdAt());
        MetadataFact loaded = store.fact(draft.id()).get();
        assertEquals(saved, loaded);
        assertEquals(anchor, loaded.anchor());
        assertEquals(draft.provenance(), loaded.provenance());
        // The anchor never changes the bookmark the fact is bound to.
        assertEquals(GLOSSARY, loaded.resourceId());
    }

    @Test
    public void oneResourceCarriesManyMixedFactsAndTwoSourcesShareATypeWithoutLucene() {
        List<MetadataFact> glossary = new ArrayList<MetadataFact>();
        for (int i = 0; i < 200; i++) {
            glossary.add(store.save(fact(GLOSSARY, i % 2 == 0 ? ACRONYM : BUSINESS_RULE, "{\"n\":" + i + "}")));
        }
        MetadataFact confluenceAcronym = store.save(fact(RULES, ACRONYM, "{\"acronym\":\"ZAUF\"}"));
        MetadataFact confluenceRule = store.save(fact(RULES, BUSINESS_RULE,
                "{\"rule\":\"Order total must not be negative\",\"severity\":\"error\"}"));

        assertEquals(200, store.factsOf(GLOSSARY).size());
        assertEquals(100, store.factsOf(GLOSSARY, ACRONYM).size());
        assertEquals(101, store.factsOfType(ACRONYM).size());
        assertTrue(store.factsOfType(ACRONYM).contains(confluenceAcronym));
        assertEquals(Arrays.asList(confluenceRule), store.factsOf(RULES, BUSINESS_RULE));
        assertEquals(new LinkedHashSet<KnowledgeResourceId>(Arrays.asList(RULES, GLOSSARY)),
                store.resourcesWithType(BUSINESS_RULE));
        assertTrue(store.resourcesWithType(FactTypeId.of("UNUSED")).isEmpty());
        // Only the database file of the catalog exists in its directory: no Lucene index, no second database.
        for (File file : directory.toFile().listFiles()) {
            assertTrue(file.getName(), file.getName().startsWith(H2FactStore.DATABASE_NAME + "."));
        }
    }

    @Test
    public void updatesAndRemovesSingleFactsById() {
        MetadataFact saved = store.save(fact(GLOSSARY, ACRONYM, "{\"acronym\":\"ABC\"}"));
        MetadataFact edited = store.save(saved.toBuilder().review(FactReview.CONFIRMED).build());

        assertEquals(FactReview.CONFIRMED, store.fact(saved.id()).get().review());
        assertEquals(saved.createdAt(), edited.createdAt());
        assertTrue(edited.updatedAt().isAfter(saved.updatedAt()));
        assertTrue(store.removeFact(saved.id()));
        assertFalse(store.removeFact(saved.id()));
        assertFalse(store.fact(saved.id()).isPresent());
    }

    @Test
    public void rejectsUnknownTypeUnknownResourceAndInvalidPayload() {
        expect(Reason.UNKNOWN_TYPE, new Runnable() {
            @Override
            public void run() {
                store.save(fact(GLOSSARY, FactTypeId.of("NOT_REGISTERED"), "{}"));
            }
        });
        expect(Reason.UNKNOWN_RESOURCE, new Runnable() {
            @Override
            public void run() {
                store.save(fact(PROGRAM, ACRONYM, "{}"));
            }
        });
        expect(Reason.INVALID_PAYLOAD, new Runnable() {
            @Override
            public void run() {
                store.save(fact(GLOSSARY, ACRONYM, "{\"acronym\": IAR}"));
            }
        });
        expect(Reason.INVALID_PAYLOAD, new Runnable() {
            @Override
            public void run() {
                store.save(fact(GLOSSARY, ACRONYM, "{\"a\":1} {\"b\":2}"));
            }
        });
        assertTrue(store.factsOf(GLOSSARY).isEmpty());
    }

    @Test
    public void aStoredFactKeepsItsResource() {
        final MetadataFact saved = store.save(fact(GLOSSARY, ACRONYM, "{\"acronym\":\"ABC\"}"));

        expect(Reason.CONFLICT, new Runnable() {
            @Override
            public void run() {
                store.save(MetadataFact.builder(RULES, ACRONYM, saved.payload(), saved.provenance())
                        .id(saved.id()).build());
            }
        });
        assertEquals(GLOSSARY, store.fact(saved.id()).get().resourceId());
    }

    @Test
    public void bothAccessPathsUseTheirCompositeIndex() {
        String factSheet = store.explain("SELECT FACT_ID FROM METADATA_FACT WHERE RESOURCE_ID = ? AND TYPE_ID = ?",
                GLOSSARY.value(), ACRONYM.value());
        String resourcesWithType = store.explain(
                "SELECT DISTINCT RESOURCE_ID FROM METADATA_FACT WHERE TYPE_ID = ? ORDER BY RESOURCE_ID",
                BUSINESS_RULE.value());

        assertTrue(factSheet, factSheet.contains(FactSchema.RESOURCE_TYPE_INDEX));
        assertTrue(resourcesWithType, resourcesWithType.contains(FactSchema.TYPE_RESOURCE_INDEX));
    }

    @Test
    public void changedRevisionKeepsIdentityAndMarksOlderFindingsStale() {
        store.save(fact(GLOSSARY, ACRONYM, "{\"acronym\":\"ABC\"}").toBuilder().build());
        MetadataFact analysed = store.save(MetadataFact.builder(GLOSSARY, ACRONYM,
                FactPayload.json("{\"acronym\":\"XYZ\"}"), FactProvenance.automatic("acronyms", "1")
                        .withSourceRevision(KnowledgeRevision.version("7")).withContentHash("sha256:old")).build());

        // The resource moved to revision 8 with new content; registering it again changes nothing at the identity.
        store.registerResource(GLOSSARY, WIKI);
        MetadataFact reloaded = store.fact(analysed.id()).get();
        assertEquals(GLOSSARY, reloaded.resourceId());
        assertEquals(FactFreshness.STALE, reloaded.freshness(KnowledgeRevision.version("8"), "sha256:new"));
        assertEquals(FactFreshness.CURRENT, reloaded.freshness(KnowledgeRevision.version("7"), "sha256:old"));
        assertEquals(2, store.factsOf(GLOSSARY).size());
    }

    // -------------------------------------------------------------- replace

    @Test
    public void replacingExtractorResultsKeepsManualReviewedAndForeignFacts() {
        MetadataFact old1 = store.save(automatic(GLOSSARY, "acronyms", "1", "{\"acronym\":\"AAA\"}"));
        store.save(automatic(GLOSSARY, "acronyms", "1", "{\"acronym\":\"BBB\"}"));
        MetadataFact reviewed = store.save(automatic(GLOSSARY, "acronyms", "1", "{\"acronym\":\"CCC\"}")
                .toBuilder().review(FactReview.CONFIRMED).build());
        MetadataFact manual = store.save(MetadataFact.builder(GLOSSARY, ACRONYM,
                FactPayload.json("{\"acronym\":\"DDD\"}"), FactProvenance.manual("manual")).build());
        MetadataFact otherExtractor = store.save(automatic(GLOSSARY, "rules", "3", "{\"acronym\":\"EEE\"}"));
        MetadataFact otherResource = store.save(automatic(RULES, "acronyms", "1", "{\"acronym\":\"FFF\"}"));

        MetadataFact fresh = automatic(GLOSSARY, "acronyms", "2", "{\"acronym\":\"GGG\"}");
        int removed = store.replaceAutomaticFacts(GLOSSARY, "acronyms", Collections.singletonList(fresh));

        assertEquals(2, removed);
        assertFalse(store.fact(old1.id()).isPresent());
        List<MetadataFact> remaining = store.factsOf(GLOSSARY);
        assertEquals(4, remaining.size());
        assertTrue(remaining.containsAll(Arrays.asList(reviewed, manual, otherExtractor, fresh)));
        assertTrue(store.fact(otherResource.id()).isPresent());
    }

    @Test
    public void failedReplaceChangesNothing() {
        final MetadataFact old = store.save(automatic(GLOSSARY, "acronyms", "1", "{\"acronym\":\"AAA\"}"));
        final MetadataFact manual = store.save(MetadataFact.builder(GLOSSARY, ACRONYM,
                FactPayload.json("{\"acronym\":\"DDD\"}"), FactProvenance.manual("manual")).build());

        expect(Reason.INVALID_PAYLOAD, new Runnable() {
            @Override
            public void run() {
                store.replaceAutomaticFacts(GLOSSARY, "acronyms", Arrays.asList(
                        automatic(GLOSSARY, "acronyms", "2", "{\"acronym\":\"OK\"}"),
                        automatic(GLOSSARY, "acronyms", "2", "{broken}")));
            }
        });
        // A replacement must not take over a manual fact by reusing its id.
        expect(Reason.CONFLICT, new Runnable() {
            @Override
            public void run() {
                store.replaceAutomaticFacts(GLOSSARY, "acronyms", Collections.singletonList(
                        automatic(GLOSSARY, "acronyms", "2", "{\"acronym\":\"X\"}").toBuilder().id(manual.id()).build()));
            }
        });
        try {
            store.replaceAutomaticFacts(GLOSSARY, "acronyms",
                    Collections.singletonList(automatic(RULES, "acronyms", "2", "{}")));
            fail("accepted a fact of another resource");
        } catch (IllegalArgumentException expected) {
            // rejected before touching the database
        }

        assertEquals(Arrays.asList(old, manual), sortedByPayload(store.factsOf(GLOSSARY)));
    }

    @Test
    public void aRunIsRecordedWithItsFactsAlsoWhenItFoundNothing() {
        KnowledgeRevision first = KnowledgeRevision.version("1");
        ExtractionRun empty = ExtractionRun.of(GLOSSARY,
                FactProvenance.automatic("acronyms", "1").withSourceRevision(first).withContentHash("h1"));
        assertFalse(store.extractionRun(GLOSSARY, "acronyms").isPresent());

        store.replaceAutomaticFacts(empty, Collections.<MetadataFact>emptyList());

        ExtractionRun stored = store.extractionRun(GLOSSARY, "acronyms").get();
        assertEquals(empty, stored);
        assertNotNull(stored.completedAt());
        assertTrue(stored.covers("1", first, "h1"));
        assertFalse(stored.covers("2", first, "h1"));
        assertFalse(stored.covers("1", first, "h2"));
        assertFalse(store.extractionRun(GLOSSARY, "rules").isPresent());
        assertFalse(store.extractionRun(RULES, "acronyms").isPresent());

        MetadataFact fresh = automatic(GLOSSARY, "acronyms", "1", "{\"acronym\":\"AAA\"}");
        ExtractionRun second = ExtractionRun.of(GLOSSARY,
                FactProvenance.automatic("acronyms", "1").withSourceRevision(first).withContentHash("h2"));
        store.replaceAutomaticFacts(second, Collections.singletonList(fresh));
        assertEquals(second, store.extractionRun(GLOSSARY, "acronyms").get());
        assertEquals(Collections.singletonList(fresh), store.factsOf(GLOSSARY));
    }

    @Test
    public void aRejectedRunKeepsTheRecordedOne() {
        ExtractionRun first = ExtractionRun.of(GLOSSARY,
                FactProvenance.automatic("acronyms", "1").withContentHash("h1"));
        store.replaceAutomaticFacts(first, Collections.<MetadataFact>emptyList());
        expect(Reason.INVALID_PAYLOAD, new Runnable() {
            @Override
            public void run() {
                store.replaceAutomaticFacts(ExtractionRun.of(GLOSSARY,
                        FactProvenance.automatic("acronyms", "2").withContentHash("h2")),
                        Collections.singletonList(automatic(GLOSSARY, "acronyms", "2", "{broken}")));
            }
        });
        assertEquals(first, store.extractionRun(GLOSSARY, "acronyms").get());
    }

    // ------------------------------------------------------------ lifecycle

    @Test
    public void removingAResourceOrSourceIsExplicitAndLeavesOthers() {
        store.registerResource(PROGRAM, SourceAddress.of("ndv://host.example:2700"));
        store.save(fact(GLOSSARY, ACRONYM, "{\"acronym\":\"AAA\"}"));
        store.save(MetadataFact.builder(GLOSSARY, BUSINESS_RULE, FactPayload.json("{\"rule\":\"r\"}"),
                FactProvenance.manual("manual")).build());
        store.save(fact(RULES, ACRONYM, "{\"acronym\":\"BBB\"}"));
        MetadataFact program = store.save(fact(PROGRAM, BUSINESS_RULE, "{\"rule\":\"p\"}"));

        store.replaceAutomaticFacts(ExtractionRun.of(GLOSSARY, FactProvenance.automatic("acronyms", "1")),
                Collections.<MetadataFact>emptyList());
        store.replaceAutomaticFacts(ExtractionRun.of(RULES, FactProvenance.automatic("acronyms", "1")),
                Collections.<MetadataFact>emptyList());

        assertEquals(2, store.removeSource(WIKI));
        assertFalse(store.extractionRun(GLOSSARY, "acronyms").isPresent());
        assertFalse(store.isResourceRegistered(GLOSSARY));
        assertEquals(1, store.factsOfType(ACRONYM).size());
        assertEquals(1, store.removeResource(RULES));
        assertFalse(store.isResourceRegistered(RULES));
        assertFalse(store.extractionRun(RULES, "acronyms").isPresent());
        assertEquals(Arrays.asList(program), store.factsOfType(BUSINESS_RULE));
        assertEquals(0, store.removeResource(RULES));
    }

    @Test
    public void foreignKeysProtectFactsFromImplicitDeletes() throws Exception {
        store.save(fact(GLOSSARY, ACRONYM, "{\"acronym\":\"AAA\"}"));

        try (Connection connection = store.openConnection(); Statement statement = connection.createStatement()) {
            try {
                statement.executeUpdate("DELETE FROM RESOURCE_REF WHERE RESOURCE_ID = '" + GLOSSARY.value() + "'");
                fail("deleted a resource that still carries facts");
            } catch (java.sql.SQLException expected) {
                // RESTRICT: facts are never removed as a side effect
            }
            try {
                statement.executeUpdate("DELETE FROM FACT_TYPE WHERE TYPE_ID = 'ACRONYM'");
                fail("deleted a type that is still in use");
            } catch (java.sql.SQLException expected) {
                // RESTRICT
            }
        }
        assertEquals(1, store.factsOf(GLOSSARY).size());
    }

    @Test
    public void factsSurviveARestart() throws Exception {
        MetadataFact saved = store.save(fact(GLOSSARY, ACRONYM, "{\"acronym\":\"AAA\",\"n\":[1,2,3]}"));
        store.close();

        store = new H2FactStore(directory, LOCK_TIMEOUT_MILLIS, clock);
        assertEquals(saved, store.fact(saved.id()).get());
        assertEquals(saved.createdAt(), store.fact(saved.id()).get().createdAt());
        assertEquals(Arrays.asList("ACRONYM", "BUSINESS_RULE"), typeNames());
        assertTrue(store.isResourceRegistered(RULES));
    }

    @Test
    public void closedStoreRefusesCalls() throws Exception {
        store.close();
        store.close();
        expect(Reason.STORAGE, new Runnable() {
            @Override
            public void run() {
                store.types();
            }
        });
    }

    @Test
    public void refusesADatabaseOfANewerApplication() throws Exception {
        try (Connection connection = store.openConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO FACT_STORE_SCHEMA VALUES (" + (FactSchema.CURRENT_VERSION + 1)
                    + ", CURRENT_TIMESTAMP)");
        }
        store.close();
        store = new H2FactStore(directory, LOCK_TIMEOUT_MILLIS, clock);
        expect(Reason.STORAGE, new Runnable() {
            @Override
            public void run() {
                store.types();
            }
        });
    }

    // ---------------------------------------------------------- concurrency

    @Test
    public void parallelReadersAndWritersUseIndependentConnections() throws Exception {
        final int writers = 4;
        final int factsPerWriter = 50;
        final AtomicBoolean writing = new AtomicBoolean(true);
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(writers + 2);
        try {
            List<Future<Integer>> results = new ArrayList<Future<Integer>>();
            for (int w = 0; w < writers; w++) {
                final KnowledgeResourceId resource = w % 2 == 0 ? GLOSSARY : RULES;
                final int writer = w;
                results.add(pool.submit(new Callable<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        start.await();
                        for (int i = 0; i < factsPerWriter; i++) {
                            store.save(fact(resource, i % 2 == 0 ? ACRONYM : BUSINESS_RULE,
                                    "{\"writer\":" + writer + ",\"n\":" + i + "}"));
                        }
                        return factsPerWriter;
                    }
                }));
            }
            List<Future<Integer>> readers = new ArrayList<Future<Integer>>();
            for (int r = 0; r < 2; r++) {
                readers.add(pool.submit(new Callable<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        start.await();
                        int reads = 0;
                        while (writing.get()) {
                            int sheet = store.factsOf(GLOSSARY).size();
                            int acronyms = store.factsOfType(ACRONYM).size();
                            assertTrue(sheet >= 0 && acronyms >= 0);
                            store.resourcesWithType(BUSINESS_RULE);
                            reads++;
                        }
                        return reads;
                    }
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertEquals(Integer.valueOf(factsPerWriter), result.get(60, TimeUnit.SECONDS));
            }
            writing.set(false);
            for (Future<Integer> reader : readers) {
                assertTrue(reader.get(60, TimeUnit.SECONDS) > 0);
            }
        } finally {
            writing.set(false);
            pool.shutdownNow();
        }
        assertEquals(writers * factsPerWriter / 2, store.factsOf(GLOSSARY).size());
        assertEquals(writers * factsPerWriter / 2, store.factsOfType(ACRONYM).size());
    }

    @Test
    public void aWriterWaitsAtMostTheLockTimeoutWhileReadersGoOn() throws Exception {
        final MetadataFact saved = store.save(fact(GLOSSARY, ACRONYM, "{\"acronym\":\"AAA\"}"));

        try (Connection holder = store.openConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement(
                    "UPDATE METADATA_FACT SET REVIEW = 'CONFIRMED' WHERE FACT_ID = ?")) {
                lock.setObject(1, saved.id().value());
                assertEquals(1, lock.executeUpdate());
            }

            // Readers see the last committed state without waiting.
            assertEquals(FactReview.UNREVIEWED, store.fact(saved.id()).get().review());
            long started = System.nanoTime();
            expect(Reason.LOCK_TIMEOUT, new Runnable() {
                @Override
                public void run() {
                    store.save(saved.toBuilder().review(FactReview.REJECTED).build());
                }
            });
            long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue("waited " + waitedMillis + " ms", waitedMillis < 10L * LOCK_TIMEOUT_MILLIS);
            holder.rollback();
        }
        assertNotNull(store.save(saved.toBuilder().review(FactReview.REJECTED).build()));
        assertEquals(FactReview.REJECTED, store.fact(saved.id()).get().review());
    }

    // -------------------------------------------------------------- helpers

    private static MetadataFact fact(KnowledgeResourceId resource, FactTypeId type, String json) {
        return MetadataFact.builder(resource, type, FactPayload.json(json), FactProvenance.automatic("test", "1"))
                .build();
    }

    private static MetadataFact automatic(KnowledgeResourceId resource, String extractor, String version,
                                          String json) {
        return MetadataFact.builder(resource, ACRONYM, FactPayload.json(json),
                FactProvenance.automatic(extractor, version)).build();
    }

    private List<String> typeNames() {
        List<String> names = new ArrayList<String>();
        for (FactType type : store.types()) {
            names.add(type.id().value());
        }
        return names;
    }

    private int tableCount() throws Exception {
        try (Connection connection = store.openConnection(); Statement statement = connection.createStatement();
             java.sql.ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES"
                     + " WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME <> 'FACT_STORE_SCHEMA'")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static List<MetadataFact> sortedByPayload(List<MetadataFact> facts) {
        List<MetadataFact> sorted = new ArrayList<MetadataFact>(facts);
        Collections.sort(sorted, new java.util.Comparator<MetadataFact>() {
            @Override
            public int compare(MetadataFact a, MetadataFact b) {
                return a.payload().json().compareTo(b.payload().json());
            }
        });
        return sorted;
    }

    private static void expect(Reason reason, Runnable action) {
        try {
            action.run();
            fail("expected " + reason);
        } catch (FactStoreException e) {
            assertEquals(e.getMessage(), reason, e.reason());
        }
    }

    /** A clock that moves one nanosecond-precise step per reading, so creation order is deterministic. */
    private static final class TickingClock extends Clock {

        private Instant now;

        TickingClock(Instant start) {
            this.now = start;
        }

        synchronized Instant last() {
            return now;
        }

        @Override
        public synchronized Instant instant() {
            now = now.plusNanos(1000001L);
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
