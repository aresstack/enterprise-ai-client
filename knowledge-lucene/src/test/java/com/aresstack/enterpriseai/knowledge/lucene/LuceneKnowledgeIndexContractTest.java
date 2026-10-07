package com.aresstack.enterpriseai.knowledge.lucene;

import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexPort;
import com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexPortContractTest;

import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;

/** Der Port-Vertrag gegen den Lucene-Index; jede Prüfung liest nach Schließen und Neuöffnen. */
public class LuceneKnowledgeIndexContractTest extends KnowledgeIndexPortContractTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private Path directory;
    private LuceneKnowledgeIndex current;

    @Override
    protected KnowledgeIndexPort createIndex() {
        try {
            directory = folder.newFolder("index").toPath();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        current = new LuceneKnowledgeIndex(directory);
        return current;
    }

    @Override
    protected KnowledgeIndexPort reopen(KnowledgeIndexPort index) {
        ((LuceneKnowledgeIndex) index).close();
        current = new LuceneKnowledgeIndex(directory);
        return current;
    }

    @After
    public void closeIndex() {
        if (current != null) {
            current.close();
        }
    }
}
