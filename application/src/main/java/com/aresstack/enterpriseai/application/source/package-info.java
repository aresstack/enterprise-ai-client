/**
 * Verwaltung der Wissensquellen: welche Quelltypen es gibt (aus den
 * {@link com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider} der Adapter), Prüfen, Speichern,
 * An- und Abwählen und Entfernen samt Rückzug der abgeleiteten Indexdaten
 * ({@link com.aresstack.enterpriseai.application.source.KnowledgeSourceManagement}). Die Oberfläche spricht nur
 * diesen Use Case an und verzweigt nie nach dem Quelltyp.
 */
package com.aresstack.enterpriseai.application.source;
