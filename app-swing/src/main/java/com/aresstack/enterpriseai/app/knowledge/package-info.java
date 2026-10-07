/**
 * Indexierung beim Start der Desktop-Anwendung (AP23): alle konfigurierten Quellen nacheinander über die
 * {@code KnowledgeIndexingBinding} aus AP22, damit Fortschritt, Ergebnis und Abbrechen-Knopf in der Statuszeile
 * der Shell erscheinen. Lässt sich per Konfiguration abschalten und beim Beenden abbrechen. Sieht nur Use Cases
 * und Bindings, keine Adapter.
 *
 * <p>Herkunft: askai-java8 (Semantic Index beim Start im Hintergrund), MainframeMate {@code IndexingService}
 * (abbrechbarer Lauf mit Fortschritt an die Oberfläche).
 */
package com.aresstack.enterpriseai.app.knowledge;
