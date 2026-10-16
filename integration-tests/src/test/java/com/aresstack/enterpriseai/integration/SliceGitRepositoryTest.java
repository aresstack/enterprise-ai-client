package com.aresstack.enterpriseai.integration;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.pinakes.LuceneKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexingListener;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexingReport;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexingStatus;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievedChunk;
import com.aresstack.enterpriseai.astu.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.astu.source.SourceSettings;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingAdapter;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingConfiguration;
import com.aresstack.enterpriseai.proasteion.emporion.deigma.ContentCategory;
import com.aresstack.enterpriseai.proasteion.emporion.deigma.DetectedContentType;
import com.aresstack.enterpriseai.proasteion.emporion.deigma.ExtractionRegistry;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.git.GitSourceProvider;
import com.aresstack.enterpriseai.proasteion.exedra.chat.fakeapi.FakeEmbeddingsServer;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SourceScope;
import org.eclipse.jgit.api.Git;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Slice #137: a Git repository as knowledge source through the existing indexing pipeline, without network. Change
 * detection and pruning come from {@link IndexKnowledgeUseCase}; the source only supplies blob ids as revisions.
 */
public class SliceGitRepositoryTest {

    private static final int DIMENSION = 16;

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private FakeEmbeddingsServer embeddingsServer;
    private OpenAiCompatibleEmbeddingAdapter embeddings;
    private EmbeddingModelIdentity space;
    private LuceneKnowledgeIndex index;
    private Path work;
    private Git git;
    private KnowledgeSourcePort source;

    @Before
    public void setUp() throws Exception {
        embeddingsServer = new FakeEmbeddingsServer(DIMENSION);
        embeddings = new OpenAiCompatibleEmbeddingAdapter(OpenAiCompatibleEmbeddingConfiguration.builder(
                embeddingsServer.baseUrl(), "danielheinz/e5-base-sts-en-de", DIMENSION).build(),
                () -> "slice-git-token".toCharArray());
        space = embeddings.modelIdentity();
        index = new LuceneKnowledgeIndex(temp.newFolder("index").toPath());

        work = temp.newFolder("shop").toPath();
        git = Git.init().setDirectory(work.toFile()).setInitialBranch("main").call();
        // Independent of the developer's ~/.gitconfig (JGit 5 rejects e.g. gpg.format=ssh).
        git.getRepository().getConfig().setString("gpg", null, "format", "openpgp");
        git.getRepository().getConfig().save();
        write("README.md", "# Shop\n\nThe checkout service computes the order total.\n");
        write("src/main/java/shop/Checkout.java", "package shop;\n\nclass Checkout {\n    long total() {\n"
                + "        return 42L;\n    }\n}\n");
        write("svc/discount.go", "package svc\n\n// Discount for loyal customers.\nfunc Discount() int { return 5 }\n");
        write("web/src/CartBadge.tsx", "export const CartBadge = () => <span>cart badge</span>;\n");
        write("web/node_modules/left-pad/index.js", "module.exports = 1;\n");
        commit("initial");

        Map<String, String> values = new HashMap<String, String>();
        values.put("url", work.toAbsolutePath().toString());
        values.put("refreshMinutes", "0");
        SourceSettings settings = SourceSettings.of(values);
        GitSourceProvider provider = new GitSourceProvider(temp.newFolder("git-cache").toPath(),
                // Code, Markdown and configuration need no extractor; PDF/Office are not part of this slice.
                (name, type, prefix) -> new DetectedContentType("application/octet-stream", ContentCategory.BINARY, name),
                new ExtractionRegistry());
        assertTrue(provider.validate(settings).isEmpty());
        source = provider.open(SourceAddress.of("git", provider.locator(settings)), settings);
    }

    @After
    public void tearDown() {
        if (git != null) {
            git.close();
        }
        if (index != null) {
            index.close();
        }
        if (embeddingsServer != null) {
            embeddingsServer.close();
        }
    }

    @Test
    public void repositoryIsIndexedAndFollowsChangesRenamesAndDeletions() throws Exception {
        IndexingReport first = indexAll();
        assertEquals(4, first.count(IndexingStatus.INDEXED));
        assertTopHit("order total checkout", "Checkout.java");

        IndexingReport unchanged = indexAll();
        assertEquals(4, unchanged.count(IndexingStatus.UNCHANGED));
        assertEquals(0, unchanged.count(IndexingStatus.INDEXED));

        write("svc/discount.go", "package svc\n\n// Discount for loyal customers.\nfunc Discount() int { return 7 }\n");
        Files.move(work.resolve("web/src/CartBadge.tsx"), work.resolve("web/src/Badge.tsx"));
        Files.delete(work.resolve("README.md"));
        git.add().addFilepattern(".").call();
        git.add().setUpdate(true).addFilepattern(".").call();
        commit("change, rename, delete");

        IndexingReport second = indexAll();
        assertEquals(1, second.count(IndexingStatus.UNCHANGED));
        assertEquals(2, second.count(IndexingStatus.INDEXED));
        assertEquals(2, second.count(IndexingStatus.PRUNED));
        for (RetrievedChunk hit : retrieval().retrieve("The checkout service computes the order total").hits()) {
            assertFalse(hit.resource().title(), "README.md".equals(hit.resource().title()));
            assertFalse(hit.resource().title(), "CartBadge.tsx".equals(hit.resource().title()));
        }
    }

    private IndexingReport indexAll() {
        return new IndexKnowledgeUseCase(index, embeddings, space,
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()))
                .indexSource(source, SourceScope.builder().startPoint(".").maxDepth(30).build(),
                        IndexingListener.none());
    }

    private RetrieveKnowledgeUseCase retrieval() {
        return new RetrieveKnowledgeUseCase(index, embeddings, space, null);
    }

    private void assertTopHit(String question, String title) {
        boolean found = false;
        for (RetrievedChunk hit : retrieval().retrieve(question).hits()) {
            found |= title.equals(hit.resource().title());
        }
        assertTrue(title + " not retrieved", found);
    }

    private void write(String path, String text) throws Exception {
        Path file = work.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private void commit(String message) throws Exception {
        git.add().addFilepattern(".").call();
        git.commit().setSign(false).setMessage(message).setAuthor("Test", "test@example.invalid")
                .setCommitter("Test", "test@example.invalid").call();
    }
}
