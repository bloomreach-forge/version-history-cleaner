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
import javax.jcr.query.InvalidQueryException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bloomreach.forge.versionhistory.core.query.BatchedQueryExecutor;

public abstract class BatchedCleaningService {

    private static final Logger log = LoggerFactory.getLogger(BatchedCleaningService.class);
    private static final int DEFAULT_QUERY_LIMIT = 1000;
    private final BatchedQueryExecutor batchedQueryExecutor;

    public BatchedCleaningService(final int batchSize, final long batchDelayMillis) {
        this.batchedQueryExecutor = createQueryExecutor(batchSize, batchDelayMillis);
    }

    protected BatchedQueryExecutor createQueryExecutor(final int batchSize, final long batchDelayMillis) {
        log.debug("Create batch query executor: batchSize={}, delay={}ms, queryBatchSize={}",
                batchSize, batchDelayMillis, DEFAULT_QUERY_LIMIT);
        final BatchedQueryExecutor queryExecutor = new BatchedQueryExecutor(batchSize, batchDelayMillis);
        // Split underlying queries to determine affected nodes in batches of 1000 (as a result queries need to have ordering)
        queryExecutor.setQueryBatchSize(DEFAULT_QUERY_LIMIT);
        return queryExecutor;
    }

    /**
     * Create the base query and execute it using the {@link BatchedQueryExecutor} which will first determine the affected nodes
     * and afterwards process all the results in batches (using the configured batch size and delay).
     *
     * @param session the session to use
     * @throws InvalidQueryException when query is not valid
     * @throws RepositoryException   when repository exception occurs
     */
    public final void cleanNodes(final Session session) throws RepositoryException {
        final QueryManager queryManager = session.getWorkspace().getQueryManager();
        batchedQueryExecutor.execute(session, createQuery(queryManager), node -> cleanNode(session, node));
    }

    protected abstract Query createQuery(final QueryManager queryManager) throws RepositoryException;

    protected abstract void cleanNode(final Session session, final Node node);

}
