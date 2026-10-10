package com.aresstack.enterpriseai.resource.holkas;

import com.aresstack.enterpriseai.domain.resource.ResourceScheme;
import com.aresstack.enterpriseai.domain.resource.VirtualResourceRef;

import java.io.IOException;

/**
 * Port for fetching raw resources by their virtual reference.
 *
 * <p>Implementations connect to a specific protocol/scheme and return
 * raw bytes plus basic metadata.
 */
public interface ResourceConnector {

    /**
     * Returns the scheme this connector handles.
     */
    ResourceScheme supportedScheme();

    /**
     * Returns {@code true} if this connector handles the given scheme.
     */
    default boolean supports(ResourceScheme scheme) {
        return supportedScheme().equals(scheme);
    }

    /**
     * Fetches the raw resource content addressed by the given reference.
     *
     * @param ref the resource reference (must use a scheme supported by this connector)
     * @return the raw resource with content and metadata
     * @throws IOException if the resource cannot be read
     * @throws IllegalArgumentException if the scheme is not supported
     */
    RawResource fetch(VirtualResourceRef ref) throws IOException;

    /**
     * Lists child resources addressed by the given container reference.
     *
     * @param ref the container resource reference
     * @return the raw resource listing
     * @throws IOException if the resource cannot be listed
     */
    ResourceListing list(VirtualResourceRef ref) throws IOException;
}
