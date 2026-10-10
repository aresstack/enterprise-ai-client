package com.aresstack.enterpriseai.domain.resource;

/**
 * Structural classification of a virtual resource.
 *
 * <p>This distinguishes what a resource structurally is — a single file, a directory,
 * a message, a dataset — regardless of which backend or protocol delivers it.
 *
 * <p>Adapted from MainframeMate's {@code VirtualResourceKind} (FILE, DIRECTORY) with
 * additional categories needed by the broader Corenth resource model.
 */
public enum VirtualResourceKind {

    /** A single file or document. */
    FILE,

    /** A directory or container of resources. */
    DIRECTORY,

    /** A mail message. */
    MESSAGE,

    /** A structured dataset (table, spreadsheet, query result). */
    DATASET,

    /** A continuous data stream. */
    STREAM,

    /** A resource whose kind is not yet determined or does not fit other categories. */
    OTHER
}
