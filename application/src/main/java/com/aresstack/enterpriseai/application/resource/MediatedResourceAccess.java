package com.aresstack.enterpriseai.application.resource;

import com.aresstack.enterpriseai.resource.api.BronzeContent;
import com.aresstack.enterpriseai.resource.api.BronzeListing;

import com.aresstack.enterpriseai.application.resource.policy.ResourceAccessRequest;

/**
 * Narrow mediated-access contract through which lifecycle use cases obtain bronze resources.
 *
 * <p>This is the only contract the Acropolis lifecycle may use to read or list resources.
 * It is implemented by {@link MediatedResourceService}, the archive counter, which consults
 * Tamias for every request and acquires missing content internally through the
 * {@link AcquisitionPort}. Callers never see connectors, the acquisition port or secrets;
 * they receive a {@link MediatedResult} that is either a bronze payload, a typed Tamias
 * decision that withholds the payload, or an acquisition error.
 *
 * <p>Keeping the lifecycle on this interface instead of the concrete service lets the
 * composition point (see {@code docs/adr/0001-composition-root.md}) decide how the counter
 * is built, and keeps {@code acropolis} independent of the service's internal caches and of
 * administrative operations such as {@code deleteEntry}.
 */
public interface MediatedResourceAccess {

    /**
     * Reads the content of a resource, mediated by Tamias.
     *
     * @param request access request carrying actor, target and
     *                {@link com.aresstack.enterpriseai.application.resource.policy.ResourceOperation#READ_CONTENT}
     * @return the mediated result; never {@code null}
     */
    MediatedResult<BronzeContent> readContent(ResourceAccessRequest request);

    /**
     * Lists the children of a container resource, mediated by Tamias.
     *
     * @param request access request carrying actor, target and
     *                {@link com.aresstack.enterpriseai.application.resource.policy.ResourceOperation#LIST_CHILDREN}
     * @return the mediated result; never {@code null}
     */
    MediatedResult<BronzeListing> listChildren(ResourceAccessRequest request);
}
