package com.aresstack.enterpriseai.domain.knowledge;

import org.junit.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class KnowledgeModelTest {

    private static void assertRejected(Runnable action) {
        try {
            action.run();
            fail("IllegalArgumentException erwartet");
        } catch (IllegalArgumentException expected) {
            // erwartet
        }
    }

    @Test
    public void sourceIdAcceptsConfigurationKeysOnly() {
        assertEquals("confluence-dc", KnowledgeSourceId.of("confluence-dc").value());
        assertEquals(KnowledgeSourceId.of("wiki:intranet"), KnowledgeSourceId.of("wiki:intranet"));
        for (final String invalid : new String[]{null, "", " wiki", "wiki intranet", "-x", "ä"}) {
            assertRejected(new Runnable() {
                public void run() {
                    KnowledgeSourceId.of(invalid);
                }
            });
        }
    }

    @Test
    public void resourceIdIsAUriLikeStableKey() {
        KnowledgeResourceId id = KnowledgeResourceId.of("wiki", "intranet/Haupt Seite");
        assertEquals("wiki:intranet/Haupt Seite", id.value());
        assertEquals("wiki", id.scheme());
        assertEquals("intranet/Haupt Seite", id.schemeSpecificPart());
        assertEquals(id, KnowledgeResourceId.of("wiki:intranet/Haupt Seite"));
        assertEquals("wiki:intranet/Haupt Seite", id.toString());
        assertRejected(new Runnable() {
            public void run() {
                KnowledgeResourceId.of("wiki", null);
            }
        });
        assertRejected(new Runnable() {
            public void run() {
                KnowledgeResourceId.of(null, "x");
            }
        });
        for (final String invalid : new String[]{null, "", "ohne-schema", "Wiki:x", "wiki:", " wiki:x", "wiki:x ",
                "wiki:a\nb", "1x:y"}) {
            assertRejected(new Runnable() {
                public void run() {
                    KnowledgeResourceId.of(invalid);
                }
            });
        }
    }

    @Test
    public void metadataIsImmutableSortedAndValidated() {
        Map<String, String> source = new LinkedHashMap<String, String>();
        source.put("space", "DEV");
        source.put("labels", "howto");
        KnowledgeMetadata metadata = KnowledgeMetadata.of(source);
        source.put("space", "OPS");

        assertEquals("DEV", metadata.get("space").get());
        assertEquals("[labels, space]", metadata.keys().toString());
        assertFalse(metadata.get("missing").isPresent());
        KnowledgeMetadata extended = metadata.with("author", "Angelo");
        assertEquals(2, metadata.asMap().size());
        assertEquals(3, extended.asMap().size());
        assertTrue(KnowledgeMetadata.empty().isEmpty());
        assertRejected(new Runnable() {
            public void run() {
                KnowledgeMetadata.empty().with(" key", "v");
            }
        });
        assertRejected(new Runnable() {
            public void run() {
                KnowledgeMetadata.empty().with("key", null);
            }
        });
        try {
            metadata.asMap().put("x", "y");
            fail();
        } catch (UnsupportedOperationException expected) {
            // erwartet
        }
    }

    @Test
    public void revisionComparesTimestampAndVersion() {
        Instant now = Instant.parse("2026-10-07T12:00:00Z");
        assertFalse(KnowledgeRevision.unknown().isKnown());
        assertEquals(KnowledgeRevision.of(now, "42"), KnowledgeRevision.of(now, " 42 "));
        assertNotEquals(KnowledgeRevision.of(now, "42"), KnowledgeRevision.of(now, "43"));
        assertEquals("17", KnowledgeRevision.version("17").version());
        assertEquals(now, KnowledgeRevision.modifiedAt(now).modifiedAt().get());
        assertEquals("", KnowledgeRevision.modifiedAt(now).version());
    }

    @Test
    public void resourceCarriesEverythingASourceNeedsToDescribe() {
        Instant modified = Instant.parse("2026-10-01T08:30:00Z");
        KnowledgeResource resource = KnowledgeResource
                .builder(KnowledgeResourceId.of("confluence:dc/page/4711"), KnowledgeSourceId.of("confluence-dc"))
                .title(" Betriebshandbuch ")
                .contentType("text/html")
                .revision(KnowledgeRevision.of(modified, "12"))
                .parentId(KnowledgeResourceId.of("confluence:dc/page/1"))
                .scope("OPS")
                .location(URI.create("https://confluence.example/pages/viewpage.action?pageId=4711"))
                .metadata(KnowledgeMetadata.empty().with("space", "OPS"))
                .build();

        assertEquals("Betriebshandbuch", resource.title());
        assertEquals("text/html", resource.contentType());
        assertEquals("12", resource.revision().version());
        assertEquals(modified, resource.revision().modifiedAt().get());
        assertEquals("confluence:dc/page/1", resource.parentId().get().value());
        assertEquals("OPS", resource.scope());
        assertTrue(resource.location().isPresent());
        assertEquals(resource, resource.toBuilder().build());
        assertNotEquals(resource, resource.toBuilder().revision(KnowledgeRevision.version("13")).build());
    }

    @Test
    public void resourceDefaultsAndGuards() {
        final KnowledgeResourceId id = KnowledgeResourceId.of("file:docs/readme.md");
        final KnowledgeSourceId source = KnowledgeSourceId.of("files");
        KnowledgeResource minimal = KnowledgeResource.builder(id, source).build();

        assertEquals("file:docs/readme.md", minimal.title());
        assertEquals(KnowledgeResource.DEFAULT_CONTENT_TYPE, minimal.contentType());
        assertFalse(minimal.revision().isKnown());
        assertFalse(minimal.parentId().isPresent());
        assertEquals("", minimal.scope());
        assertTrue(minimal.metadata().isEmpty());
        assertRejected(new Runnable() {
            public void run() {
                KnowledgeResource.builder(id, source).location(URI.create("https://user:secret@host/x"));
            }
        });
        assertRejected(new Runnable() {
            public void run() {
                KnowledgeResource.builder(id, source).parentId(id).build();
            }
        });
        assertRejected(new Runnable() {
            public void run() {
                KnowledgeResource.builder(null, source);
            }
        });
    }

    @Test
    public void documentNormalizesTextAndHashesItWithoutPrintingIt() {
        KnowledgeResource resource = KnowledgeResource
                .builder(KnowledgeResourceId.of("wiki:x/A"), KnowledgeSourceId.of("wiki")).build();
        KnowledgeDocument nfd = KnowledgeDocument.of(resource, "Größe\r\nZeile");
        KnowledgeDocument nfc = KnowledgeDocument.of(resource, "Größe\nZeile");

        assertEquals("Größe\nZeile", nfd.text());
        assertEquals(nfc, nfd);
        assertEquals(nfc.contentHash(), nfd.contentHash());
        assertEquals(64, nfc.contentHash().length());
        assertNotEquals(nfc.contentHash(), KnowledgeDocument.of(resource, "Größe").contentHash());
        assertFalse(nfc.toString().contains("Zeile"));
        assertTrue(KnowledgeDocument.of(resource, null).isBlank());
    }

    @Test
    public void chunkIdRoundTripsAndChunkValidates() {
        KnowledgeResourceId resourceId = KnowledgeResourceId.of("wiki:x/Seite#Anker");
        KnowledgeChunkId id = KnowledgeChunkId.of(resourceId, 7);
        assertEquals("wiki:x/Seite#Anker#chunk-7", id.value());
        assertEquals(id, KnowledgeChunkId.parse(id.value()));
        assertEquals(resourceId, KnowledgeChunkId.parse(id.value()).resourceId());
        KnowledgeChunkId largest = KnowledgeChunkId.of(resourceId, Integer.MAX_VALUE);
        assertEquals(largest, KnowledgeChunkId.parse(largest.value()));
        assertRejected(new Runnable() {
            public void run() {
                KnowledgeChunkId.parse("wiki:x#chunk-2147483648");
            }
        });
        for (final String invalid : new String[]{"wiki:x", "#chunk-1", "wiki:x#chunk-", "wiki:x#chunk-01",
                "wiki:x#chunk--1", null}) {
            assertRejected(new Runnable() {
                public void run() {
                    KnowledgeChunkId.parse(invalid);
                }
            });
        }

        final KnowledgeSourceId source = KnowledgeSourceId.of("wiki");
        KnowledgeChunk chunk = new KnowledgeChunk(id, source, Collections.singletonList("Kapitel"), "Text.", 3);
        assertEquals(7, chunk.ordinal());
        assertFalse(chunk.toString().contains("Text."));
        assertRejected(new Runnable() {
            public void run() {
                new KnowledgeChunk(KnowledgeChunkId.of(KnowledgeResourceId.of("wiki:x"), 0), source, null, " ", 0);
            }
        });
        assertRejected(new Runnable() {
            public void run() {
                new KnowledgeChunk(KnowledgeChunkId.of(KnowledgeResourceId.of("wiki:x"), 0), source,
                        Collections.singletonList(""), "x", 0);
            }
        });
    }
}
