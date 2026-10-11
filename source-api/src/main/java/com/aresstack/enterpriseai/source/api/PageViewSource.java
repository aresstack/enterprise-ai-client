package com.aresstack.enterpriseai.source.api;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;

/**
 * Optionale Fähigkeit einer Quelle: die Lese-Ansicht einer Seite, so wie die Quelle sie selbst darstellt (HTML mit
 * Bildern an ihrer Stelle). Sie ist ein eigener Pfad neben {@link KnowledgeSourcePort#load}: das
 * {@link com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument} bleibt der kanonische Text für Index und
 * Modell, die Ansicht dient nur dem Lesen.
 */
public interface PageViewSource {

    /** Die Lese-Ansicht der Seite; wirft {@link KnowledgeSourceException}, wenn die Quelle sie nicht kennt. */
    PageView pageView(KnowledgeResourceId page) throws KnowledgeSourceException;

    /**
     * Die Bytes eines Bildes der Ansicht über die Anmeldung der Quelle (wie MainframeMates {@code ByteDownloader});
     * nur Adressen der Quelle selbst, alles andere {@code UNSUPPORTED}.
     *
     * @param src die Adresse aus {@link PageView.Image#src()}
     */
    byte[] loadViewImage(String src) throws KnowledgeSourceException;
}
