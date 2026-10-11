/**
 * Use Case „Dokumentnavigation aufbauen und aktualisieren“: aus der Textfassung eines geöffneten Dokuments und den
 * Passagen, die die Index-Pipeline für genau diese Fassung gebildet hat (askai arch {@code PassageSegmentation}),
 * entsteht eine Kapitelhierarchie aus Originalüberschriften und automatisch erkannten Themenabschnitten
 * ({@code TopicClusterer}). Keine zweite Zerlegung, keine Swing-Typen; die Oberfläche bekommt nur
 * {@code astu.navigation}.
 */
package com.aresstack.enterpriseai.astu.acropolis.navigation;
