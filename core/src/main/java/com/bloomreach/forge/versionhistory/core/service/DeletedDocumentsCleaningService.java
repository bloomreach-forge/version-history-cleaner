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

import java.time.LocalDateTime;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;

import org.apache.jackrabbit.JcrConstants;
import org.hippoecm.repository.api.HippoNodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bloomreach.forge.versionhistory.core.DocumentHistoryTruncaterTask;
import com.bloomreach.forge.versionhistory.core.exception.IllegalDocumentException;
import com.bloomreach.forge.versionhistory.core.query.QueryHelper;

import static com.bloomreach.forge.versionhistory.core.NodeHelper.getNodePath;
import static org.apache.commons.lang3.StringUtils.EMPTY;

public class DeletedDocumentsCleaningService extends BatchedCleaningService {

    private static final Logger log = LoggerFactory.getLogger(DeletedDocumentsCleaningService.class);
    private static final String DELETED_DOCUMENTS_QUERY_BEFORE_DATE =
            "//element(*, " + HippoNodeType.NT_DELETED + ")" +
                    "[@" + HippoNodeType.HIPPO_DELETED_DATE + " < xs:dateTime(\"%s\")]" +
                    " order by @" + JcrConstants.JCR_UUID;
    private static final String DELETED_DOCUMENTS_QUERY_NO_DATE_FILTER =
            "//element(*, " + HippoNodeType.NT_DELETED + ") order by @" + JcrConstants.JCR_UUID;
    private final long minimumMinutesToLive;

    /**
     * Cleaning service responsible for cleaning documents and it's revisions from the attic. When deleted documents
     * are deleted for a longer time than the {@link #minimumMinutesToLive} (according to the
     * {@link HippoNodeType#HIPPO_DELETED_DATE}) the document is illegible for cleaning.
     *
     * @param batchSize            the amount of items to process in one batch (before a batch delay is applied)
     * @param batchDelayMillis     the delay to wait between batches
     * @param minimumMinutesToLive the minimum amount of minutes before a deleted item is eligible for cleaning
     *                             (i.e. can be removed from attic and revision information will be deleted)
     */
    public DeletedDocumentsCleaningService(final int batchSize, final long batchDelayMillis, final long minimumMinutesToLive) {
        super(batchSize, batchDelayMillis);
        this.minimumMinutesToLive = minimumMinutesToLive;
    }

    @Override
    protected Query createQuery(final QueryManager queryManager) throws RepositoryException {
        final String deletedDocumentsQuery = minimumMinutesToLive > 0 ?
                String.format(DELETED_DOCUMENTS_QUERY_BEFORE_DATE,
                        QueryHelper.formatTime(LocalDateTime.now().withNano(0).withSecond(0)
                                .minusMinutes(minimumMinutesToLive))) :
                DELETED_DOCUMENTS_QUERY_NO_DATE_FILTER;
        log.debug("Deleted documents query: {}", deletedDocumentsQuery);
        //noinspection deprecation
        return queryManager.createQuery(deletedDocumentsQuery, Query.XPATH);
    }

    @Override
    protected void cleanNode(final Session session, final Node node) {
        truncateAllVersionsForDeletedNode(session, node);
    }

    private void truncateAllVersionsForDeletedNode(final Session session, final Node deletedNode) {
        try {
            final DocumentHistoryTruncaterTask task = new DocumentHistoryTruncaterTask(session, deletedNode);
            task.execute();

            session.save();
        } catch (final IllegalDocumentException e) {
            log.error("Failed to create truncate revision history task for node at path {}.", getNodePath(deletedNode).orElse(EMPTY), e);
        } catch (final Exception e) {
            log.error("Failed to truncate revision history for deleted document () at {}.", getNodePath(deletedNode).orElse(EMPTY), e);
            try {
                session.refresh(false);
            } catch (final RepositoryException re) {
                log.error("Failed to refresh session.", re);
            }
        }
    }

}
