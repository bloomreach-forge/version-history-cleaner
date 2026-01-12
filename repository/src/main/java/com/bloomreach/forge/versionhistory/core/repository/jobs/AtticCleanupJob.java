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
package com.bloomreach.forge.versionhistory.core.repository.jobs;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bloomreach.forge.versionhistory.core.AtticDocumentScannerTask;
import com.bloomreach.forge.versionhistory.core.DocumentHistoryTruncaterTask;
import com.bloomreach.forge.versionhistory.core.repository.SchedulerConfiguration;

/**
 * Runnable job for cleaning up old documents from the attic.
 * Deletes documents and their version history based on retention policy.
 * This is instantiated by the DocumentHistorySchedulerDaemonModule with configuration.
 */
public class AtticCleanupJob implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(AtticCleanupJob.class);

    private final Session session;
    private final SchedulerConfiguration schedulerConfig;

    public AtticCleanupJob(final Session session, final SchedulerConfiguration schedulerConfig) {
        this.session = session;
        this.schedulerConfig = schedulerConfig;
    }

    @Override
    public void run() {
        final long startTime = System.currentTimeMillis();
        int totalProcessed = 0;
        int totalDeleted = 0;
        int errorCount = 0;

        try {
            if (schedulerConfig == null) {
                LOG.error("Job configuration is incomplete, missing schedulerConfig");
                return;
            }

            final int batchSize = schedulerConfig.getBatchSize();
            final long atticRetentionDays = schedulerConfig.getAtticRetentionDays();

            LOG.info("Starting AtticCleanupJob with batch size: {}, retention days: {}", batchSize,
                    atticRetentionDays);

            final AtticDocumentScannerTask scanner = new AtticDocumentScannerTask(session);
            scanner.setLogger(LOG);

            // Calculate cutoff time once (now - retentionDays)
            final long nowInMillis = System.currentTimeMillis();
            final long retentionInMillis = atticRetentionDays * (24L * 60L * 60L * 1000L);
            final long cutoffInMillis = nowInMillis - retentionInMillis;

            int offset = 0;
            int batchNumber = 1;
            int batchCount = 0;

            while (true) {
                final NodeIterator nodeIterator =
                        scanner.scanOldAtticDocuments(atticRetentionDays, batchSize, offset);

                if (!nodeIterator.hasNext()) {
                    LOG.info("Batch {} has no attic documents, ending scan", batchNumber);
                    break;
                }

                while (nodeIterator.hasNext()) {
                    final Node documentNode = nodeIterator.nextNode();
                    final String docPath = documentNode.getPath();

                    try {
                        // Check if document is actually old enough to delete
                        if (scanner.isDocumentOlderThan(documentNode, cutoffInMillis)) {
                            deleteAtticDocument(documentNode);
                            totalDeleted++;
                            LOG.debug("Successfully deleted attic document at {}", docPath);
                        } else {
                            LOG.debug("Skipping document at {} (not old enough)", docPath);
                        }
                    } catch (final RepositoryException e) {
                        LOG.warn("Failed to process attic document at {}: {}", docPath, e.getMessage(), e);
                        errorCount++;
                    } finally {
                        totalProcessed++;
                        batchCount++;
                    }
                }

                // Save session after each batch
                try {
                    session.save();
                    LOG.info("Batch {} complete: processed {}, deleted {}, errors so far: {}",
                            batchNumber, batchCount, totalDeleted, errorCount);
                } catch (final RepositoryException e) {
                    LOG.error("Failed to save session after batch {}: {}", batchNumber, e.getMessage(), e);
                    session.refresh(false);
                    errorCount += batchCount;
                }

                batchNumber++;
                offset += batchSize;
                batchCount = 0;
            }

            final long duration = System.currentTimeMillis() - startTime;
            LOG.info("AtticCleanupJob completed successfully. Total processed: {}, deleted: {}, errors: {}, "
                    + "duration: {} ms", totalProcessed, totalDeleted, errorCount, duration);

        } catch (final Exception e) {
            LOG.error("AtticCleanupJob failed with error: {}", e.getMessage(), e);
        }
    }

    /**
     * Deletes an attic document and its version history.
     */
    private void deleteAtticDocument(final Node documentNode) throws RepositoryException {

        if (!documentNode.isNodeType("mix:versionable")) {
            LOG.debug("Document at {} is not versionable, removing node only", documentNode.getPath());
            documentNode.remove();
            return;
        }

        // First truncate the version history using DocumentHistoryTruncaterTask
        final DocumentHistoryTruncaterTask truncaterTask = new DocumentHistoryTruncaterTask(session, documentNode);
        truncaterTask.setLogger(LOG);

        try {
            truncaterTask.execute();
        } catch (final RepositoryException e) {
            LOG.warn("Failed to truncate version history for {}: {}", documentNode.getPath(), e.getMessage(), e);
            // Continue with node removal even if truncation fails
        }

        // Then remove the document node itself (if not already removed by truncater for attic docs)
        try {
            if (documentNode.getSession().nodeExists(documentNode.getPath())) {
                documentNode.remove();
            }
        } catch (final RepositoryException e) {
            LOG.debug("Document node at {} was already removed", documentNode.getPath());
        }
    }
}
