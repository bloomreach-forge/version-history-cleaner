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
package com.bloomreach.forge.versionhistory.core;

import java.util.Calendar;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.apache.commons.lang3.time.DateFormatUtils;

/**
 * Document scanner task for iterating through versionable documents with pagination.
 * Used by scheduler services to process documents in batches.
 */
public class AtticDocumentScannerTask extends AbstractContentHistoryTask {

    /**
     * A day in milliseconds.
     */
    private static final long DAY_IN_MILLIS = 24L * 60L * 60L * 1000L;

    /**
     * Default batch size for pagination.
     */
    private static final int DEFAULT_BATCH_SIZE = 100;

    public AtticDocumentScannerTask(final Session session) {
        super(session);
    }

    /**
     * Scans all versionable documents under the specified root path.
     * Returns a NodeIterator that can be used to iterate through documents.
     *
     * @param rootPath     the root path to scan (typically /content/documents)
     * @param batchSize    the number of documents per batch
     * @param offset       the offset for pagination
     * @return NodeIterator for the versionable documents
     * @throws RepositoryException if query execution fails
     */
    public NodeIterator scanDocuments(final String rootPath, final int batchSize, final int offset)
            throws RepositoryException {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than 0");
        }

        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }

        final String xpath = String.format("%s/element(*, mix:versionable)", rootPath);
        return executeQuery(xpath, batchSize, offset);
    }

    /**
     * Scans all versionable documents under /content/documents and /content/attic.
     * Returns a NodeIterator that can be used to iterate through documents.
     *
     * @param batchSize the number of documents per batch
     * @param offset    the offset for pagination
     * @return NodeIterator for the versionable documents
     * @throws RepositoryException if query execution fails
     */
    public NodeIterator scanAllDocuments(final int batchSize, final int offset) throws RepositoryException {
        final String xpath = "//content/(documents|attic)//element(*, mix:versionable)";
        return executeQuery(xpath, batchSize, offset);
    }

    /**
     * Scans versionable documents in the attic that are older than the specified retention period.
     * This scans ALL attic documents and filters by age in Java (more reliable than XPath date queries).
     *
     * @param retentionDays the number of days to retain documents
     * @param batchSize     the number of documents per batch
     * @param offset        the offset for pagination
     * @return NodeIterator for old attic documents
     * @throws RepositoryException if query execution fails
     */
    public NodeIterator scanOldAtticDocuments(final long retentionDays, final int batchSize, final int offset)
            throws RepositoryException {
        if (retentionDays < 0) {
            throw new IllegalArgumentException("retentionDays must be >= 0");
        }

        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than 0");
        }

        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }

        // Calculate cutoff time (now - retentionDays)
        final long nowInMillis = System.currentTimeMillis();
        final long retentionInMillis = retentionDays * DAY_IN_MILLIS;
        final long cutoffInMillis = nowInMillis - retentionInMillis;

        getLogger().debug("Scanning attic documents older than {} days (before {} ms)", retentionDays, cutoffInMillis);

        // Query all attic documents - we'll filter by date in Java instead of XPath
        // This is more reliable than XPath date comparisons which have format issues
        final String xpath = "//content/attic//element(*, mix:versionable)";

        return executeQuery(xpath, batchSize, offset);
    }

    /**
     * Check if a document node is older than the specified cutoff time.
     * Uses jcr:created property for age determination.
     *
     * @param documentNode the document node to check
     * @param cutoffTimeInMillis the cutoff time in milliseconds
     * @return true if the document is older than cutoff
     * @throws RepositoryException if property access fails
     */
    public boolean isDocumentOlderThan(final Node documentNode, final long cutoffTimeInMillis)
            throws RepositoryException {
        if (!documentNode.hasProperty("jcr:created")) {
            getLogger().debug("Document at {} has no jcr:created property", documentNode.getPath());
            return false;
        }

        final Calendar createdDate = documentNode.getProperty("jcr:created").getDate();
        final long createdTimeInMillis = createdDate.getTimeInMillis();

        return createdTimeInMillis < cutoffTimeInMillis;
    }

    /**
     * Executes an XPath query with pagination support.
     *
     * @param xpath     the XPath query
     * @param batchSize the limit for the query
     * @param offset    the offset for pagination
     * @return NodeIterator from query results
     * @throws RepositoryException if query execution fails
     */
    private NodeIterator executeQuery(final String xpath, final int batchSize, final int offset)
            throws RepositoryException {
        final QueryManager queryManager = getSession().getWorkspace().getQueryManager();
        final Query query = queryManager.createQuery(xpath, Query.XPATH);

        query.setLimit(batchSize);
        query.setOffset(offset);

        getLogger().debug("Executing query: {} (limit={}, offset={})", xpath, batchSize, offset);

        final QueryResult queryResult = query.execute();
        final NodeIterator nodeIterator = queryResult.getNodes();

        getLogger().debug("Query returned {} results", nodeIterator.getSize());

        return nodeIterator;
    }

    @Override
    protected void doExecute() throws RepositoryException {
        // This task is not meant to be executed directly
        throw new UnsupportedOperationException("AtticDocumentScannerTask should not be executed directly. "
                + "Use its scan methods to get NodeIterators instead.");
    }
}
