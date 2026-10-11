package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.embedding.api.EmbeddingException;

import java.util.List;

/**
 * Zerlegt einen Dokumenttext in semantische Passagen, jede schon mit eigenem Embedding (askai arch
 * {@code PassageSegmentation}: Struktur zuerst, Satzgrenzen, Themenwechsel über Satzfenster). Die Indexierung kennt
 * nur diesen Port; welche Satzerkennung und welche Schwellen gelten, verrät {@link #fingerprint()}, der in den
 * Indexzustand eingeht.
 */
public interface KnowledgePassageSplitter {

    /** Kennung von Verfahren und Parametern; ändert sie sich, wird neu zerlegt. */
    String fingerprint();

    /** Passagen in Lesereihenfolge; leer für leeren Text. */
    List<Passage> split(String resourceId, String text) throws EmbeddingException;

    /** Eine Passage: Abschnittspfad (Überschriften, getrennt durch " > "), Text und Embedding des ganzen Textes. */
    final class Passage {

        private final String headingPath;
        private final String text;
        private final float[] vector;

        public Passage(String headingPath, String text, float[] vector) {
            if (text == null || vector == null) {
                throw new IllegalArgumentException("text und vector sind Pflicht");
            }
            this.headingPath = headingPath == null ? "" : headingPath;
            this.text = text;
            this.vector = vector.clone();
        }

        public String headingPath() {
            return headingPath;
        }

        public String text() {
            return text;
        }

        public float[] vector() {
            return vector.clone();
        }
    }
}
