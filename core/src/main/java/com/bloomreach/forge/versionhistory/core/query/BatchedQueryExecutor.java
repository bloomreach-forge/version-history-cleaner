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
package com.bloomreach.forge.versionhistory.core.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.InvalidQueryException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;

import org.apache.commons.lang3.StringUtils;
import org.hippoecm.repository.api.HippoNodeIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Query executor that executes a query in two steps to limit the change that the query result is affected by
 * the processing that happens on the results (e.g. while deleting nodes while querying). A consumer will receive
 * all the results one at the time.
 * The first step will execute the query (by default in batches of a 1000) to quickly fetch all documents identifiers
 * that are a result of the query.
 * After all affected IDs are determined it will go through the total list of document identifiers and process them
 * in batches (determined by the batch size). Each time an item is processed it will fetch the corresponding node,
 * based on identifier, and pass it on to the consumer. Between each batch it will wait for the specified batch
 * delay (of provided).
 */
public class BatchedQueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(BatchedQueryExecutor.class);

    private final int batchSize;
    private final long batchDelay;
    private int itemDelay = -1;
    private int queryBatchSize = 1000;
    private static final TimeUnit DELAY_TIME_UNIT = TimeUnit.MILLISECONDS;


    /**
     * Create a batched delay executor with the batch size used to process item and a batch delay in {@link #DELAY_TIME_UNIT}.
     * @param batchSize the batch size, negative or 0 means no batch size will be used
     * @param batchDelay the batch delay in {@link #DELAY_TIME_UNIT}
     */
    public BatchedQueryExecutor(final int batchSize,
                                final long batchDelay) {
        this.batchSize = batchSize;
        this.batchDelay = batchDelay;
    }

    /**
     * Execute a query and supply the query results one by one to the consumer, while batch delays are taken into account
     * while processing batches. (Optionally it is possible to also specify an item delay that will apply for each
     * individual result.)
     * @param session the JCR session
     * @param query the query to execute (used to fetch affected identifiers)
     * @param consumer the consumer to process the query results
     * @throws RepositoryException when repository exception occurs
     */
    public void execute(final Session session, final Query query, final Consumer<Node> consumer) throws RepositoryException {
        final Collection<String> identifiers = queryIdentifiers(query);
        long processedItems = 0;
        long skippedItems = 0;
        final long totalItems = identifiers.size();
        log.debug("Found {} nodes to process in batches of {} (batchDelay={})", totalItems, batchSize, batchDelay);
        for (final String identifier : identifiers) {

            // Wait for batch delay if needed (execution will stop if thread is interrupted)
            if (batchSize > 0 && processedItems > 0 && processedItems % batchSize == 0) {
                try {
                    waitDelay(batchDelay);
                } catch (InterruptedException e) {
                    log.warn("Thread interrupted; stop processing (processed {} items out of {})", processedItems, totalItems);
                    return;
                }
            }

            // Fetch node and propagate to consumer
            final Optional<Node> node = fetchNode(session, identifier);
            if (node.isPresent()) {
                log.debug("Node found with identifier {}; propagate node to consumer", identifier);
                processItem(node.get(), consumer);
                processedItems++;

                try {
                    waitDelay(itemDelay);
                } catch (final InterruptedException e) {
                    log.warn("Thread interrupted; stop processing (processed {} items out of {})", processedItems, totalItems);
                    return;
                }
            } else {
                skippedItems++;
                log.warn("Node with identifier {} could not be found; ignoring item from result", identifier);
            }
        }
        log.debug("Processed {} nodes and skipped {} nodes of total of {}", processedItems, skippedItems, totalItems);
    }

    protected void processItem(final Node node, final Consumer<Node> consumer) {
        consumer.accept(node);
    }

    private Optional<Node> fetchNode(final Session session, final String identifier) {
        try {
            return Optional.of(session.getNodeByIdentifier(identifier));
        } catch (final ItemNotFoundException e) {
            log.debug("Node with ID '{}' could not be found", identifier);
        } catch (final RepositoryException e) {
            log.error("Unable to retrieve node with id '{}'", identifier, e);
        }
        return Optional.empty();
    }

    private Collection<String> queryIdentifiers(final Query query) throws RepositoryException {
        if (queryBatchSize > 0) {
            return queryIdentifiersInBatches(query);
        } else {
            return queryAllIdentifiersAtOnce(query);
        }
    }

    private Collection<String> queryAllIdentifiersAtOnce(final Query query) throws RepositoryException {
        final QueryResult queryResult = query.execute();
        final NodeIterator result = queryResult.getNodes();
        return processQueryResult(result, getTotalSize(result), 0L);
    }

    private Collection<String> queryIdentifiersInBatches(final Query query) throws RepositoryException {
        if (!StringUtils.containsIgnoreCase(query.getStatement(), " order by ")) {
            throw new InvalidQueryException("Unable to perform batch processing for query without ordering: " + query.getStatement());
        }
        query.setLimit(queryBatchSize);

        final NodeIterator firstBatch = queryNextBatch(query, 0L);
        long totalSize = getTotalSize(firstBatch);
        final Set<String> identifiers = new LinkedHashSet<>(
                processQueryResult(firstBatch, totalSize, 0L));

        for (long offset = queryBatchSize; offset < totalSize; offset += queryBatchSize) {

            // For now no delay between queries to limit the window of execution

            final NodeIterator nextBatch = queryNextBatch(query, offset);

            /*
                Update the total size based on latest results (this new value will be part of logging info)
                Note: changes in result set can potentially cause missed identifiers, because it can affect ordering.
                The effect is limited by determining the result nodes from the query in.
             */
            totalSize = getTotalSize(nextBatch);

            identifiers.addAll(processQueryResult(nextBatch, totalSize, offset));
        }

        return identifiers;
    }

    private Collection<String> processQueryResult(final NodeIterator batch, final long totalSize, final long offset) throws RepositoryException {
        final List<String> identifiers = new ArrayList<>();
        log.info("Process results of {} nodes from total of {} nodes (batchSize={}, offset={})",
                batch.getSize(), totalSize, queryBatchSize, offset);

        while (batch.hasNext()) {
            identifiers.add(batch.nextNode().getIdentifier());
        }
        log.debug("Batch (offset={}) found {} identifiers of total of {}", offset, identifiers.size(), totalSize);
        return identifiers;
    }

    private NodeIterator queryNextBatch(final Query query, final long offset) throws RepositoryException {
        query.setOffset(offset);
        final QueryResult queryResult = query.execute();
        return queryResult.getNodes();
    }

    private long getTotalSize(final NodeIterator nodeIterator) {
        if (nodeIterator instanceof HippoNodeIterator) {
            return ((HippoNodeIterator) nodeIterator).getTotalSize();
        } else {
            return -1L;
        }
    }

    private void waitDelay(final long delay) throws InterruptedException {
        if (delay > 0) {
            log.trace("Batch processing waiting for {} {} before processing next batch", delay, DELAY_TIME_UNIT);
            DELAY_TIME_UNIT.sleep(delay);
        }
    }

    /**
     * Option to override the default query batch size used to fetch the identifiers from JCR.
     * A value <= 0 will result in a query without using batches to fetch the data from JCR, but all data
     * will be returned as part of 1 single query without limit.
     * @param queryBatchSize the batch size (or <=0 to query all at once without limit)
     */
    public void setQueryBatchSize(final int queryBatchSize) {
        this.queryBatchSize = queryBatchSize;
    }

    /**
     * Additional option to specify an item delay after a single item has been processed.
     * @param itemDelay delay in {@link #DELAY_TIME_UNIT}
     */
    public void setItemDelay(final int itemDelay) {
        this.itemDelay = itemDelay;
    }

}
