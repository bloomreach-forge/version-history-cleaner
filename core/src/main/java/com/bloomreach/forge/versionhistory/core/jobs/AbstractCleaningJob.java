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

import org.onehippo.repository.scheduling.RepositoryJob;
import org.onehippo.repository.scheduling.RepositoryJobExecutionContext;

import com.bloomreach.forge.versionhistory.core.jobs.context.JobContextHelper;

import static com.bloomreach.forge.versionhistory.core.configuration.CleanerConfigurationConstants.BATCH_SIZE;
import static com.bloomreach.forge.versionhistory.core.configuration.CleanerConfigurationConstants.BATCH_DELAY_MILLIS;
import static com.bloomreach.forge.versionhistory.core.configuration.CleanerConfigurationConstants.DEFAULT_BATCH_SIZE;
import static com.bloomreach.forge.versionhistory.core.configuration.CleanerConfigurationConstants.DEFAULT_BATCH_DELAY_MILLIS;

public abstract class AbstractCleaningJob implements RepositoryJob {

    @Override
    final public void execute(final RepositoryJobExecutionContext context) throws RepositoryException {
        final Session session = context.createSystemSession();
        try {
            doExecute(context, session);
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    protected abstract void doExecute(final RepositoryJobExecutionContext context, final Session session);

    protected int getBatchSize(final RepositoryJobExecutionContext context) {
        return JobContextHelper.parseIntAttribute(context, BATCH_SIZE, DEFAULT_BATCH_SIZE);
    }

    protected long getBatchDelayMillis(final RepositoryJobExecutionContext context) {
        return JobContextHelper.parseLongAttribute(context, BATCH_DELAY_MILLIS, DEFAULT_BATCH_DELAY_MILLIS);
    }

}
