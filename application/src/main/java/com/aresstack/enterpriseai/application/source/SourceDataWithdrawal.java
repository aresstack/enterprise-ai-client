package com.aresstack.enterpriseai.application.source;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;

/**
 * Zieht weitere abgeleitete Daten einer entfernten Quelle zurück, über den Wissensindex hinaus (etwa die
 * Archiveinträge der Ressourcenschicht).
 */
public interface SourceDataWithdrawal {

    void withdraw(KnowledgeSourceId sourceId);
}
