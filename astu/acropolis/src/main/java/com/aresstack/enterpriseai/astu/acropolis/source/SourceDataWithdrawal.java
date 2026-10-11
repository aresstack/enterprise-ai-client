package com.aresstack.enterpriseai.astu.acropolis.source;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeSourceId;

/**
 * Zieht weitere abgeleitete Daten einer entfernten Quelle zurück, über den Wissensindex hinaus (etwa die
 * Archiveinträge der Ressourcenschicht).
 */
public interface SourceDataWithdrawal {

    void withdraw(KnowledgeSourceId sourceId);
}
