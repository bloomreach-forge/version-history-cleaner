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
import org.hippoecm.repository.HippoStdNodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bloomreach.forge.versionhistory.core.DocumentDeleteTask;
import com.bloomreach.forge.versionhistory.core.exception.IllegalDocumentException;
import com.bloomreach.forge.versionhistory.core.query.QueryHelper;

import static com.bloomreach.forge.versionhistory.core.NodeHelper.getNodePath;
import static org.apache.commons.lang3.StringUtils.EMPTY;

public class UnpublishedDocumentsCleaningService extends BatchedCleaningService {

    private static final Logger log = LoggerFactory.getLogger(UnpublishedDocumentsCleaningService.class);
    private static final String UNPUBLISHED_DOCUMENTS_QUERY_BEFORE_DATE =
            "//element(*, " + JcrConstants.MIX_VERSIONABLE + ")" +
                    "[" +
                    "@" + HippoStdNodeType.HIPPOSTD_STATESUMMARY + " = 'new' and " +
                    "@%s and " +
                    "@%s < xs:dateTime(\"%s\")" +
                    "]" +
                    " order by @" + JcrConstants.JCR_UUID;
    private static final String UNPUBLISHED_DOCUMENTS_QUERY_NO_DATE_FILTER =
            "//element(*, " + JcrConstants.MIX_VERSIONABLE + ")" +
                    "[@" + HippoStdNodeType.HIPPOSTD_STATESUMMARY + " = 'new']" +
                    " order by @" + JcrConstants.JCR_UUID;
    private final String depublicationDateProperty;
    private final long minimumMinutesToLive;

    /**
     * Cleaning service responsible for deleting unpublished documents (i.e. move them to the attic using document
     * workflow). When documents have been unpublished for longer time than the {@link #minimumMinutesToLive}
     * (according to the configured depublicationDateProperty) the document is illegible for deletion.
     *
     * @param batchSize                 the amount of items to process in one batch (before a batch delay is applied)
     * @param batchDelayMillis          the delay to wait between batches
     * @param depublicationDateProperty the depublication date property used to determine if item is allowed to be cleaned
     *                                  according to the allowed minutes to live ({@link #minimumMinutesToLive})
     * @param minimumMinutesToLive      the minimum amount of minutes before an unpublished item is eligible for cleaning (i.e. can be deleted)
     */
    public UnpublishedDocumentsCleaningService(final int batchSize,
                                               final long batchDelayMillis,
                                               final String depublicationDateProperty,
                                               final long minimumMinutesToLive) {
        super(batchSize, batchDelayMillis);
        this.depublicationDateProperty = depublicationDateProperty;
        this.minimumMinutesToLive = minimumMinutesToLive;
    }

    @Override
    protected Query createQuery(final QueryManager queryManager) throws RepositoryException {
        final String unpublishedDocumentsQuery = minimumMinutesToLive > 0 ?
                String.format(UNPUBLISHED_DOCUMENTS_QUERY_BEFORE_DATE,
                        depublicationDateProperty,
                        depublicationDateProperty,
                        QueryHelper.formatTime(LocalDateTime.now().withNano(0).withSecond(0)
                                .minusMinutes(minimumMinutesToLive))) :
                UNPUBLISHED_DOCUMENTS_QUERY_NO_DATE_FILTER;
        log.debug("Unpublished documents query: {}", unpublishedDocumentsQuery);
        //noinspection deprecation
        return queryManager.createQuery(unpublishedDocumentsQuery, Query.XPATH);
    }

    @Override
    protected void cleanNode(final Session session, final Node node) {
        deleteDocument(session, node);
    }

    private void deleteDocument(final Session session, final Node unpublishedNode) {
        try {
            log.debug("Create delete document task for document: {}", getNodePath(unpublishedNode).orElse(EMPTY));
            final DocumentDeleteTask task = new DocumentDeleteTask(session, unpublishedNode);
            task.execute();

            session.save();
        } catch (final IllegalDocumentException e) {
            log.error("Failed to create delete document task for node at path {}.", getNodePath(unpublishedNode).orElse(EMPTY), e);
        } catch (final Exception e) {
            log.error("Failed to delete document at path {}.", getNodePath(unpublishedNode).orElse(EMPTY), e);
            try {
                session.refresh(false);
            } catch (final RepositoryException re) {
                log.error("Failed to refresh session.", re);
            }
        }
    }

}
