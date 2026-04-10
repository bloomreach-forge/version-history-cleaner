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
package com.bloomreach.forge.versionhistory.core.jobs;

import java.util.Optional;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.onehippo.repository.scheduling.RepositoryJobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bloomreach.forge.versionhistory.core.NodeHelper;
import com.bloomreach.forge.versionhistory.core.configuration.CleanerConfiguration;
import com.bloomreach.forge.versionhistory.core.jobs.context.JobContextHelper;
import com.bloomreach.forge.versionhistory.core.service.DocumentRevisionsCleaningService;

import static com.bloomreach.forge.versionhistory.core.configuration.CleanerConfigurationConstants.CLEANUP_PATH;
import static com.bloomreach.forge.versionhistory.core.configuration.CleanerConfigurationConstants.DEFAULT_CLEANUP_PATH;

public class DocumentRevisionsCleaningJob extends AbstractCleaningJob {

    private static final Logger log = LoggerFactory.getLogger(DocumentRevisionsCleaningJob.class);

    @Override
    protected void doExecute(final RepositoryJobExecutionContext context, final Session session) {
        final String cleanupPath = JobContextHelper.getStringAttribute(context, CLEANUP_PATH).orElse(DEFAULT_CLEANUP_PATH);
        final Optional<String> cleanUpPathIdentifier = NodeHelper.getNodeIdentifier(session, cleanupPath);
        if (cleanUpPathIdentifier.isEmpty()) {
            log.error("Unable to execute job; identifier could not be found for cleanup path: {}", cleanupPath);
            return;
        }
        final CleanerConfiguration cleanerConfiguration = JobContextHelper.loadCleaningConfiguration(context);

        final DocumentRevisionsCleaningService documentRevisionsCleaningService = new DocumentRevisionsCleaningService(
                getBatchSize(context),
                getBatchDelayMillis(context),
                cleanUpPathIdentifier.get(),
                cleanerConfiguration);
        try {
            documentRevisionsCleaningService.cleanNodes(session);
        } catch (final RepositoryException e) {
            log.error("Error while trying to clean deleted nodes", e);
        }
    }

}
