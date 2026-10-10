/**
 * Vereinter Modellkatalog: führt die {@link com.aresstack.enterpriseai.model.api.ModelCatalogPort}s (Enterprise-API,
 * optionaler lokaler Sidecar) zu einem {@link com.aresstack.enterpriseai.application.modelcatalog.ModelCatalogSnapshot}
 * zusammen. Je Kategorie zeigt die Oberfläche nur passende Modelle; eine nicht erreichbare Quelle behält ihre
 * zuletzt bekannten Modelle.
 */
package com.aresstack.enterpriseai.application.modelcatalog;
