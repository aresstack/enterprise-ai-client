package com.aresstack.enterpriseai.astu.acropolis.chalcotheca;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.KnowledgeIndexPortContractTest;

public class InMemoryKnowledgeIndexContractTest extends KnowledgeIndexPortContractTest {

    @Override
    protected KnowledgeIndexPort createIndex() {
        return new InMemoryKnowledgeIndex();
    }
}
