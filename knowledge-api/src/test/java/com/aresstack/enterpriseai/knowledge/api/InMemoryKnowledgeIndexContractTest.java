package com.aresstack.enterpriseai.knowledge.api;

import com.aresstack.enterpriseai.knowledge.api.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexPortContractTest;

public class InMemoryKnowledgeIndexContractTest extends KnowledgeIndexPortContractTest {

    @Override
    protected KnowledgeIndexPort createIndex() {
        return new InMemoryKnowledgeIndex();
    }
}
