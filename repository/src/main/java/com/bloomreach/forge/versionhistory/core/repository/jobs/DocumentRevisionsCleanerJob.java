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

import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bloomreach.forge.versionhistory.core.AtticDocumentScannerTask;
import com.bloomreach.forge.versionhistory.core.DocumentHistoryCleanerTask;
import com.bloomreach.forge.versionhistory.core.repository.DocumentHistoryCleanerConfiguration;
import com.bloomreach.forge.versionhistory.core.repository.SchedulerConfiguration;

/**
 * Runnable job for cleaning document revisions.
 * Scans all documents and applies revision cleaning based on configured policies.
 * This is instantiated by the DocumentHistorySchedulerDaemonModule with configuration.
 */
public class DocumentRevisionsCleanerJob implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentRevisionsCleanerJob.class);

    private final Session session;
    private final DocumentHistoryCleanerConfiguration defaultConfig;
    private final Map<String, DocumentHistoryCleanerConfiguration> documentTypeConfigs;
    private final SchedulerConfiguration schedulerConfig;

    public DocumentRevisionsCleanerJob(final Session session,
            final DocumentHistoryCleanerConfiguration defaultConfig,
            final Map<String, DocumentHistoryCleanerConfiguration> documentTypeConfigs,
            final SchedulerConfiguration schedulerConfig) {
        this.session = session;
        this.defaultConfig = defaultConfig;
        this.documentTypeConfigs = documentTypeConfigs;
        this.schedulerConfig = schedulerConfig;
    }

    @Override
    public void run() {
        final long startTime = System.currentTimeMillis();
        int totalProcessed = 0;
        int totalCleaned = 0;
        int errorCount = 0;

        try {
            if (defaultConfig == null || schedulerConfig == null) {
                LOG.error("Job configuration is incomplete, missing defaultConfig or schedulerConfig");
                return;
            }

            final int batchSize = schedulerConfig.getBatchSize();

            LOG.info("Starting DocumentRevisionsCleanerJob with batch size: {}", batchSize);

            final AtticDocumentScannerTask scanner = new AtticDocumentScannerTask(session);
            scanner.setLogger(LOG);

            int offset = 0;
            int batchNumber = 1;
            int batchCount = 0;

            while (true) {
                final NodeIterator nodeIterator = scanner.scanAllDocuments(batchSize, offset);

                if (!nodeIterator.hasNext()) {
                    LOG.info("Batch {} has no documents, ending scan", batchNumber);
                    break;
                }

                while (nodeIterator.hasNext()) {
                    final Node documentNode = nodeIterator.nextNode();

                    try {
                        cleanDocumentRevisions(documentNode);
                        totalCleaned++;
                    } catch (final RepositoryException e) {
                        LOG.warn("Failed to clean revisions for document at {}: {}", documentNode.getPath(),
                                e.getMessage(), e);
                        errorCount++;
                    } finally {
                        totalProcessed++;
                        batchCount++;
                    }
                }

                // Save session after each batch
                try {
                    session.save();
                    LOG.info("Batch {} complete: processed {}, cleaned {}, errors so far: {}",
                            batchNumber, batchCount, totalCleaned, errorCount);
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
            LOG.info(
                    "DocumentRevisionsCleanerJob completed successfully. "
                            + "Total processed: {}, cleaned: {}, errors: {}, duration: {} ms",
                    totalProcessed, totalCleaned, errorCount, duration);

        } catch (final Exception e) {
            LOG.error("DocumentRevisionsCleanerJob failed with error: {}", e.getMessage(), e);
        }
    }

    /**
     * Cleans revisions for a single document using configured policies.
     */
    private void cleanDocumentRevisions(final Node documentNode) throws RepositoryException {

        if (!documentNode.isNodeType("mix:versionable")) {
            return;
        }

        // Determine which configuration to use (document-type-specific or default)
        DocumentHistoryCleanerConfiguration config = defaultConfig;

        if (documentTypeConfigs != null && !documentTypeConfigs.isEmpty()) {
            final String primaryNodeType = documentNode.getPrimaryNodeType().getName();

            if (documentTypeConfigs.containsKey(primaryNodeType)) {
                config = documentTypeConfigs.get(primaryNodeType);
            }
        }

        // Create and execute cleaning task
        final DocumentHistoryCleanerTask cleanerTask = new DocumentHistoryCleanerTask(session, documentNode);
        cleanerTask.setLogger(LOG);
        cleanerTask.setMaxDays(config.getMaxDays());
        cleanerTask.setMaxRevisions(config.getMaxRevisions());

        cleanerTask.execute();
    }
}
