package com.aresstack.enterpriseai.astu.acropolis.chalcotheca;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeMetadata;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeStructuralBlock;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Die Bronze-Form eines Wissensdokuments: was ein Holkas-Connector über Wiki, Confluence oder lokale Dateien
 * beschafft, liegt im Archiv als Bytes ({@link BronzeContent}). Die Quellen dieses Clients liefern schon
 * extrahierten Text; damit Titel, Ort, Stand und Metadaten den Weg durch Tamias und Chalcotheca überstehen, stehen
 * sie als Kopfzeilen vor dem Text:
 *
 * <pre>
 * bronze/knowledge-document/1
 * id=wiki:intranet/Hauptseite
 * source=wiki
 * title=Hauptseite
 * ...
 *
 * Text des Dokuments
 * </pre>
 *
 * Strukturblöcke des Dokuments (askai {@code StructuralBlock}) stehen in Lesereihenfolge als
 * {@code block=<Art>:<Länge des Pfads>:<Pfad><Text>} im Kopf, damit sie die Bronze-Kopie überstehen.
 * Werte sind zeilenweise kodiert ({@code \\}, {@code \n}, {@code \r}); UTF-8.
 */
public final class KnowledgeBronze {

    static final String MAGIC = "bronze/knowledge-document/1";
    private static final String META = "meta.";
    private static final String BLOCK = "block";

    private KnowledgeBronze() {
    }

    public static byte[] encode(KnowledgeDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        KnowledgeResource r = document.resource();
        StringBuilder out = new StringBuilder(MAGIC).append('\n');
        line(out, "id", r.id().value());
        line(out, "source", r.sourceId().value());
        line(out, "title", r.title());
        line(out, "type", r.contentType());
        if (r.revision().modifiedAt().isPresent()) {
            line(out, "modified", String.valueOf(r.revision().modifiedAt().get().toEpochMilli()));
        }
        line(out, "version", r.revision().version());
        if (r.parentId().isPresent()) {
            line(out, "parent", r.parentId().get().value());
        }
        line(out, "scope", r.scope());
        if (r.location().isPresent()) {
            line(out, "location", r.location().get().toString());
        }
        for (Map.Entry<String, String> entry : r.metadata().asMap().entrySet()) {
            line(out, META + entry.getKey(), entry.getValue());
        }
        for (KnowledgeStructuralBlock block : document.blocks()) {
            // Art, Länge des Pfads, Pfad, Text: Pfad und Text dürfen jedes Zeichen enthalten.
            line(out, BLOCK, block.kind().name() + ':' + block.headingPath().length() + ':' + block.headingPath()
                    + block.text());
        }
        out.append('\n').append(document.text());
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** @throws IOException wenn die Bytes keine Bronze-Form eines Wissensdokuments sind */
    public static KnowledgeDocument decode(byte[] bytes) throws IOException {
        if (bytes == null) {
            throw new IOException("keine Bronze-Daten");
        }
        String all = new String(bytes, StandardCharsets.UTF_8);
        int headerEnd = all.indexOf("\n\n");
        if (!all.startsWith(MAGIC + "\n") || headerEnd < 0) {
            throw new IOException("keine Bronze-Form eines Wissensdokuments");
        }
        Map<String, String> header = new TreeMap<String, String>();
        Map<String, String> metadata = new TreeMap<String, String>();
        List<KnowledgeStructuralBlock> blocks = new ArrayList<KnowledgeStructuralBlock>();
        for (String line : all.substring(MAGIC.length() + 1, headerEnd).split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) {
                throw new IOException("ungültige Kopfzeile in der Bronze-Form");
            }
            String key = unescape(line.substring(0, eq));
            String value = unescape(line.substring(eq + 1));
            if (BLOCK.equals(key)) {
                blocks.add(block(value));
            } else if (key.startsWith(META)) {
                metadata.put(key.substring(META.length()), value);
            } else {
                header.put(key, value);
            }
        }
        try {
            KnowledgeResource.Builder resource = KnowledgeResource.builder(
                    KnowledgeResourceId.of(header.get("id")), SourceAddress.of(header.get("source")));
            resource.title(orEmpty(header.get("title")));
            if (header.containsKey("type")) {
                resource.contentType(header.get("type"));
            }
            String modified = header.get("modified");
            resource.revision(KnowledgeRevision.of(modified == null ? null : Instant.ofEpochMilli(Long.parseLong(modified)),
                    header.get("version")));
            if (header.containsKey("parent")) {
                resource.parentId(KnowledgeResourceId.of(header.get("parent")));
            }
            resource.scope(orEmpty(header.get("scope")));
            if (header.containsKey("location")) {
                resource.location(URI.create(header.get("location")));
            }
            resource.metadata(KnowledgeMetadata.of(metadata));
            return KnowledgeDocument.of(resource.build(), all.substring(headerEnd + 2), blocks);
        } catch (RuntimeException e) {
            throw new IOException("Bronze-Form eines Wissensdokuments unvollständig: " + e.getClass().getSimpleName());
        }
    }

    private static KnowledgeStructuralBlock block(String value) throws IOException {
        int kindEnd = value.indexOf(':');
        int lengthEnd = kindEnd < 0 ? -1 : value.indexOf(':', kindEnd + 1);
        try {
            KnowledgeStructuralBlock.Kind kind = KnowledgeStructuralBlock.Kind.valueOf(value.substring(0, kindEnd));
            int pathEnd = lengthEnd + 1 + Integer.parseInt(value.substring(kindEnd + 1, lengthEnd));
            return new KnowledgeStructuralBlock(kind, value.substring(lengthEnd + 1, pathEnd),
                    value.substring(pathEnd));
        } catch (RuntimeException e) {
            throw new IOException("ungültiger Strukturblock in der Bronze-Form");
        }
    }

    private static void line(StringBuilder out, String key, String value) {
        out.append(escape(key).replace("=", "\\e")).append('=').append(escape(value == null ? "" : value))
                .append('\n');
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String unescape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                sb.append(next == 'n' ? '\n' : next == 'r' ? '\r' : next == 'e' ? '=' : next);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
