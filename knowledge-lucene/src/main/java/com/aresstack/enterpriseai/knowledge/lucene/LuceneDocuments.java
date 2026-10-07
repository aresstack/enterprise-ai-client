package com.aresstack.enterpriseai.knowledge.lucene;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunk;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeMetadata;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexException;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.util.BytesRef;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Abbildung zwischen {@link KnowledgeIndexEntry} und Lucene-{@link Document}. Ressource und Chunk werden
 * verlustfrei als gespeicherte Felder abgelegt, damit ein Treffer ohne Rückgriff auf die Quelle vollständig ist.
 * Durchsucht wird ein zusammengesetztes Feld aus Titel, Überschriften und Text.
 */
final class LuceneDocuments {

    static final String CHUNK_ID = "chunkId";
    static final String CHUNK_ID_SORT = "chunkIdSort";
    static final String RESOURCE_ID = "resourceId";
    static final String SOURCE_ID = "sourceId";
    static final String CONTENT = "content";
    private static final String TEXT = "text";
    private static final String HEADINGS = "headings";
    private static final String TOKENS = "tokens";
    private static final String TITLE = "title";
    private static final String CONTENT_TYPE = "contentType";
    private static final String MODIFIED_AT = "modifiedAt";
    private static final String VERSION = "version";
    private static final String PARENT_ID = "parentId";
    private static final String SCOPE = "scope";
    private static final String LOCATION = "location";
    private static final String METADATA = "metadata";

    private LuceneDocuments() {
    }

    /** Ressource und Chunk eines gespeicherten Dokuments. */
    static final class Stored {
        final KnowledgeResource resource;
        final KnowledgeChunk chunk;

        Stored(KnowledgeResource resource, KnowledgeChunk chunk) {
            this.resource = resource;
            this.chunk = chunk;
        }
    }

    static Document toDocument(KnowledgeIndexEntry entry) {
        KnowledgeResource resource = entry.resource();
        KnowledgeChunk chunk = entry.chunk();
        Document doc = new Document();
        doc.add(new StringField(CHUNK_ID, chunk.id().value(), Field.Store.YES));
        doc.add(new SortedDocValuesField(CHUNK_ID_SORT, new BytesRef(chunk.id().value())));
        doc.add(new StringField(RESOURCE_ID, resource.id().value(), Field.Store.YES));
        doc.add(new StringField(SOURCE_ID, resource.sourceId().value(), Field.Store.YES));
        doc.add(new TextField(CONTENT, resource.title() + "\n" + chunk.headingLine() + "\n" + chunk.text(),
                Field.Store.NO));

        doc.add(new StoredField(TEXT, chunk.text()));
        doc.add(new StoredField(HEADINGS, encode(chunk.headingPath())));
        doc.add(new StoredField(TOKENS, chunk.tokenCount()));
        doc.add(new StoredField(TITLE, resource.title()));
        doc.add(new StoredField(CONTENT_TYPE, resource.contentType()));
        if (resource.revision().modifiedAt().isPresent()) {
            doc.add(new StoredField(MODIFIED_AT, resource.revision().modifiedAt().get().toString()));
        }
        doc.add(new StoredField(VERSION, resource.revision().version()));
        if (resource.parentId().isPresent()) {
            doc.add(new StoredField(PARENT_ID, resource.parentId().get().value()));
        }
        doc.add(new StoredField(SCOPE, resource.scope()));
        if (resource.location().isPresent()) {
            doc.add(new StoredField(LOCATION, resource.location().get().toString()));
        }
        List<String> metadata = new ArrayList<String>();
        for (Map.Entry<String, String> item : resource.metadata().asMap().entrySet()) {
            metadata.add(item.getKey());
            metadata.add(item.getValue());
        }
        doc.add(new StoredField(METADATA, encode(metadata)));
        return doc;
    }

    static Stored fromDocument(Document doc) {
        try {
            KnowledgeResourceId resourceId = KnowledgeResourceId.of(doc.get(RESOURCE_ID));
            KnowledgeSourceId sourceId = KnowledgeSourceId.of(doc.get(SOURCE_ID));
            String parentId = doc.get(PARENT_ID);
            String location = doc.get(LOCATION);
            List<String> metadataItems = decode(doc.get(METADATA));
            Map<String, String> metadata = new LinkedHashMap<String, String>();
            for (int i = 0; i + 1 < metadataItems.size(); i += 2) {
                metadata.put(metadataItems.get(i), metadataItems.get(i + 1));
            }
            KnowledgeResource resource = KnowledgeResource.builder(resourceId, sourceId)
                    .title(doc.get(TITLE))
                    .contentType(doc.get(CONTENT_TYPE))
                    .revision(revisionOf(doc))
                    .parentId(parentId == null ? null : KnowledgeResourceId.of(parentId))
                    .scope(doc.get(SCOPE))
                    .location(location == null ? null : URI.create(location))
                    .metadata(KnowledgeMetadata.of(metadata))
                    .build();
            KnowledgeChunk chunk = new KnowledgeChunk(KnowledgeChunkId.parse(doc.get(CHUNK_ID)), sourceId,
                    decode(doc.get(HEADINGS)), doc.get(TEXT), doc.getField(TOKENS).numericValue().intValue());
            return new Stored(resource, chunk);
        } catch (RuntimeException ex) {
            throw new KnowledgeIndexException("Gespeicherter Indexeintrag ist beschädigt: " + doc.get(CHUNK_ID), ex);
        }
    }

    /** Revision aus den gespeicherten Feldern; Dokumente ohne Felder gelten als {@link KnowledgeRevision#unknown()}. */
    static KnowledgeRevision revisionOf(Document doc) {
        String modifiedAt = doc.get(MODIFIED_AT);
        return KnowledgeRevision.of(modifiedAt == null ? null : Instant.parse(modifiedAt), doc.get(VERSION));
    }

    /** Liste als {@code <länge>:<wert>}-Folge; jedes Zeichen ist im Wert erlaubt. */
    static String encode(List<String> values) {
        StringBuilder encoded = new StringBuilder();
        for (String value : values) {
            encoded.append(value.length()).append(':').append(value);
        }
        return encoded.toString();
    }

    static List<String> decode(String encoded) {
        List<String> values = new ArrayList<String>();
        if (encoded == null) {
            return values;
        }
        int position = 0;
        while (position < encoded.length()) {
            int colon = encoded.indexOf(':', position);
            if (colon < 0) {
                throw new IllegalArgumentException("Ungültige Listenkodierung");
            }
            int length = Integer.parseInt(encoded.substring(position, colon));
            int end = colon + 1 + length;
            if (length < 0 || end > encoded.length()) {
                throw new IllegalArgumentException("Ungültige Listenkodierung");
            }
            values.add(encoded.substring(colon + 1, end));
            position = end;
        }
        return values;
    }
}
