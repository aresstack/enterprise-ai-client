package com.aresstack.enterpriseai.research.store;

import com.aresstack.enterpriseai.research.domain.scope.ResearchScopeDraft;
import com.aresstack.enterpriseai.research.api.ResearchScopeDraftStore;
import com.aresstack.enterpriseai.research.api.ScopeDraftLoadResult;

import java.io.File;
import java.io.IOException;

/**
 * The project-scoped, VERSIONED persistence of the {@link ResearchScopeDraft}: {@code scope-draft.json} next
 * to the other project state, written atomically so a crash never leaves a half-written scope.
 * <p>
 * The store owns the revision counter, not the caller and certainly not a model: {@link #save} always
 * persists the NEXT revision of what it is given. Continuity of the scope is an application property.
 */
public final class FileResearchScopeDraftStore implements ResearchScopeDraftStore {

    static final String FILE_NAME = "scope-draft.json";

    private final File file;

    public FileResearchScopeDraftStore(File projectDirectory) {
        this.file = new File(projectDirectory, FILE_NAME);
    }

    /** Where the draft is persisted (diagnostics, repair hints). */
    public File getFile() {
        return file;
    }

    /** Load the draft — typed, never throwing: a damaged draft must BLOCK, not silently start empty. */
    @Override
    public synchronized ScopeDraftLoadResult load() {
        if (!file.isFile()) {
            return ScopeDraftLoadResult.missing();
        }
        String content;
        try {
            content = StoreIo.readUtf8(file);
        } catch (IOException unreadable) {
            return ScopeDraftLoadResult.failed(ScopeDraftLoadResult.Status.CORRUPT,
                    "cannot read " + file + ": " + unreadable.getMessage());
        }
        try {
            return ScopeDraftLoadResult.loaded(ResearchScopeDraftCodec.fromJson(content));
        } catch (ResearchScopeDraftCodec.UnsupportedSchemaException futureSchema) {
            return ScopeDraftLoadResult.failed(ScopeDraftLoadResult.Status.UNSUPPORTED_SCHEMA,
                    futureSchema.getMessage());
        } catch (RuntimeException unusable) {
            return ScopeDraftLoadResult.failed(ScopeDraftLoadResult.Status.CORRUPT,
                    "cannot parse " + file + ": " + unusable.getMessage());
        }
    }

    /**
     * Persist the NEXT revision of this draft.
     *
     * @return the draft as persisted (with its new revision), so the caller keeps working on exactly what
     *         is on disk rather than on a copy that only looks the same
     */
    @Override
    public synchronized ResearchScopeDraft save(ResearchScopeDraft draft) throws IOException {
        ResearchScopeDraft next = draft.toBuilder().nextRevision().build();
        StoreIo.atomicWrite(file, ResearchScopeDraftCodec.toJson(next));
        return next;
    }
}
