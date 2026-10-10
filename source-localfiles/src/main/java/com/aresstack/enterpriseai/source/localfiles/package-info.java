/**
 * Lokale Dateien als Wissensquelle: ein Verzeichnis wird rekursiv gelesen, jede Datei über den neutralen
 * Extraktions-Port ({@code document-api}) in Text überführt. Tika kennt dieses Modul nicht; die Composition Root
 * gibt Erkennung und Extraktoren mit.
 */
package com.aresstack.enterpriseai.source.localfiles;
