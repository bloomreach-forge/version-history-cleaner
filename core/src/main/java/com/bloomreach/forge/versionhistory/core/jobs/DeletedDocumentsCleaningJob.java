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

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.onehippo.repository.scheduling.RepositoryJobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bloomreach.forge.versionhistory.core.jobs.context.JobContextHelper;
import com.bloomreach.forge.versionhistory.core.service.DeletedDocumentsCleaningService;

import static com.bloomreach.forge.versionhistory.core.configuration.CleanerConfigurationConstants.DEFAULT_MINIMUM_MINUTES_TO_LIVE;
import static com.bloomreach.forge.versionhistory.core.configuration.CleanerConfigurationConstants.MINIMUM_MINUTES_TO_LIVE;

public class DeletedDocumentsCleaningJob extends AbstractCleaningJob {

    private static final Logger log = LoggerFactory.getLogger(DeletedDocumentsCleaningJob.class);

    @Override
    public void doExecute(final RepositoryJobExecutionContext context, final Session session) {
        final long minimumMinutesToLive = JobContextHelper.parseLongAttribute(context, MINIMUM_MINUTES_TO_LIVE, DEFAULT_MINIMUM_MINUTES_TO_LIVE);
        final DeletedDocumentsCleaningService deletedDocumentsCleaningService = new DeletedDocumentsCleaningService(
                getBatchSize(context),
                getBatchDelayMillis(context),
                minimumMinutesToLive);
        try {
            deletedDocumentsCleaningService.cleanNodes(session);
        } catch (final RepositoryException e) {
            log.error("Error while trying to clean deleted nodes", e);
        }
    }

}
