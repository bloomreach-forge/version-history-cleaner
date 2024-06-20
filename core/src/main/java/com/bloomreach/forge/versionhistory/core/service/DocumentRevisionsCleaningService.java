/*
 *  Copyright 2024 BloomReach, Inc. (https://www.bloomreach.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.bloomreach.forge.versionhistory.core.service;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;

import org.apache.jackrabbit.JcrConstants;
import org.hippoecm.repository.api.HippoNodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bloomreach.forge.versionhistory.core.DocumentHistoryCleanerTask;
import com.bloomreach.forge.versionhistory.core.configuration.CleanerConfiguration;
import com.bloomreach.forge.versionhistory.core.exception.IllegalDocumentException;

import static com.bloomreach.forge.versionhistory.core.NodeHelper.getNodePath;
import static org.apache.commons.lang3.StringUtils.EMPTY;

public class DocumentRevisionsCleaningService extends BatchedCleaningService {

    private static final Logger log = LoggerFactory.getLogger(DocumentRevisionsCleaningService.class);
    private static final String VERSIONABLE_DOCUMENT_QUERY_FORMAT =
            "//element(*, " + JcrConstants.MIX_VERSIONABLE + ")" +
                    "[@" + HippoNodeType.HIPPO_PATHS + "='%s']" +
                    " order by @" + JcrConstants.JCR_UUID;

    private final CleanerConfiguration cleanerConfiguration;
    private final String cleanupPathIdentifier;

    /**
     * Cleaning service responsible for deleting revision history of documents using the provided cleaner configuration.
     * For all documents below a certain path in the repository (based on path that matches the provided
     * {@link #cleanupPathIdentifier}) it will clean up old revisions (based on the cleaner configuration; using max days
     * and max revisions).
     *
     * @param batchSize             the amount of items to process in one batch (before a batch delay is applied)
     * @param batchDelayMillis      the delay to wait between batches
     * @param cleanupPathIdentifier the UUID of the path to delete the items from (e.g. the UUID of the node /content)
     * @param cleanerConfiguration  the {@link CleanerConfiguration} to use for cleaning documents.
     */
    public DocumentRevisionsCleaningService(final int batchSize,
                                            final long batchDelayMillis,
                                            final String cleanupPathIdentifier,
                                            final CleanerConfiguration cleanerConfiguration) {
        super(batchSize, batchDelayMillis);
        this.cleanupPathIdentifier = cleanupPathIdentifier;
        this.cleanerConfiguration = cleanerConfiguration;
    }

    @Override
    protected Query createQuery(final QueryManager queryManager) throws RepositoryException {
        final String versionableDocumentQuery = String.format(VERSIONABLE_DOCUMENT_QUERY_FORMAT, cleanupPathIdentifier);
        log.debug("Versionable document query: {}", versionableDocumentQuery);
        //noinspection deprecation
        return queryManager.createQuery(versionableDocumentQuery, Query.XPATH);
    }

    @Override
    protected void cleanNode(final Session session, final Node node) {
        cleanUpOldVersions(session, node);
    }

    private void cleanUpOldVersions(final Session session, final Node versionableNode) {
        try {
            final String documentType = versionableNode.getPrimaryNodeType().getName();
            log.info("Cleanup old revisions for node (type={}): {}", documentType, getNodePath(versionableNode).orElse(EMPTY));

            final DocumentHistoryCleanerTask task = new DocumentHistoryCleanerTask(session, versionableNode);
            task.setMaxDays(cleanerConfiguration.getMaxDays(documentType));
            task.setMaxRevisions(cleanerConfiguration.getMaxRevisions(documentType));
            task.execute();

            session.save();
        } catch (final IllegalDocumentException e) {
            log.error("Failed to create clean revision history task for node at path {}.", getNodePath(versionableNode).orElse(EMPTY), e);
        } catch (Exception e) {
            log.error("Failed to clean revision history for the document () at {}.", getNodePath(versionableNode).orElse(EMPTY), e);
            try {
                session.refresh(false);
            } catch (final RepositoryException re) {
                log.error("Failed to refresh session.", re);
            }
        }
    }

}
