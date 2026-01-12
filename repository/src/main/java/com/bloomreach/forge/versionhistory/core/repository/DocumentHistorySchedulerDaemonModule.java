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
package com.bloomreach.forge.versionhistory.core.repository;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Repository;
import javax.jcr.Session;

import org.hippoecm.repository.util.JcrUtils;
import org.onehippo.repository.modules.AbstractReconfigurableDaemonModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bloomreach.forge.versionhistory.core.repository.jobs.AtticCleanupJob;
import com.bloomreach.forge.versionhistory.core.repository.jobs.DocumentRevisionsCleanerJob;

/**
 * Daemon module for scheduling document history cleaning jobs.
 * Manages job registration and lifecycle based on configuration.
 *
 * Note: This implementation uses a simple scheduled executor approach.
 * For production use, integrate with your application's job scheduler.
 */
public class DocumentHistorySchedulerDaemonModule extends AbstractReconfigurableDaemonModule {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentHistorySchedulerDaemonModule.class);

    private static final Pattern DOCTYPE_PREFIXED_PROP_NAME_PATTERN = Pattern
            .compile("^([A-Za-z_\\-]+:[A-Za-z_\\-]+)\\.(.+)$");

    private SchedulerConfiguration schedulerConfig;
    private DocumentHistoryCleanerConfiguration defaultConfig;
    private Map<String, DocumentHistoryCleanerConfiguration> documentTypeConfigs;
    private ScheduledExecutorService executorService;
    private Repository repository;
    private Session daemonSession;

    @Override
    protected void doConfigure(final Node moduleConfig) throws RepositoryException {
        // Load scheduler configuration
        schedulerConfig = new SchedulerConfiguration();
        schedulerConfig.setEnabled(JcrUtils.getBooleanProperty(moduleConfig, "scheduler.enabled", false));
        schedulerConfig.setCronExpression(
                JcrUtils.getStringProperty(moduleConfig, "scheduler.cron.expression", "0 0 2 * * ?"));
        long batchSizeLong = JcrUtils.getLongProperty(moduleConfig, "scheduler.batch.size", 100L);
        schedulerConfig.setBatchSize((int) Math.min(batchSizeLong, Integer.MAX_VALUE));
        schedulerConfig
                .setAtticRetentionDays(JcrUtils.getLongProperty(moduleConfig, "scheduler.attic.retention.days", 30L));
        schedulerConfig.setRevisionsCleanerEnabled(
                JcrUtils.getBooleanProperty(moduleConfig, "scheduler.revisions.enabled", true));
        schedulerConfig.setAtticCleanupEnabled(
                JcrUtils.getBooleanProperty(moduleConfig, "scheduler.attic.cleanup.enabled", true));

        // Load cleaner configuration (shared with event-driven module)
        defaultConfig = new DocumentHistoryCleanerConfiguration();
        defaultConfig.setMaxDays(JcrUtils.getLongProperty(moduleConfig, "default.max.days", -1L));
        defaultConfig.setMaxRevisions(JcrUtils.getLongProperty(moduleConfig, "default.max.revisions", -1L));
        defaultConfig.setTruncateOnDelete(JcrUtils.getBooleanProperty(moduleConfig, "default.truncate.ondelete", false));

        // Load document-type-specific configurations
        documentTypeConfigs = new HashMap<>();

        for (PropertyIterator propIt = moduleConfig.getProperties(); propIt.hasNext();) {
            final Property prop = propIt.nextProperty();

            if (prop == null) {
                continue;
            }

            final String propName = prop.getName();
            final Matcher matcher = DOCTYPE_PREFIXED_PROP_NAME_PATTERN.matcher(propName);

            if (matcher.matches()) {
                final String docTypeName = matcher.group(1);
                DocumentHistoryCleanerConfiguration documentTypeConfig = documentTypeConfigs.get(docTypeName);

                if (documentTypeConfig == null) {
                    documentTypeConfig = new DocumentHistoryCleanerConfiguration();
                    documentTypeConfig.setMaxDays(defaultConfig.getMaxDays());
                    documentTypeConfig.setMaxRevisions(defaultConfig.getMaxRevisions());
                    documentTypeConfig.setTruncateOnDelete(defaultConfig.isTruncateOnDelete());
                    documentTypeConfigs.put(docTypeName, documentTypeConfig);
                }

                final String configPropName = matcher.group(2);

                if ("max.days".equals(configPropName)) {
                    documentTypeConfig.setMaxDays(prop.getLong());
                } else if ("max.revisions".equals(configPropName)) {
                    documentTypeConfig.setMaxRevisions(prop.getLong());
                } else if ("truncate.ondelete".equals(configPropName)) {
                    documentTypeConfig.setTruncateOnDelete(prop.getBoolean());
                }
            }
        }

        LOG.info("DocumentHistorySchedulerDaemonModule configured: enabled={}, cronExpression={}, batchSize={}, "
                + "atticRetentionDays={}, revisionsCleanerEnabled={}, atticCleanupEnabled={}",
                schedulerConfig.isEnabled(), schedulerConfig.getCronExpression(), schedulerConfig.getBatchSize(),
                schedulerConfig.getAtticRetentionDays(), schedulerConfig.isRevisionsCleanerEnabled(),
                schedulerConfig.isAtticCleanupEnabled());
    }

    @Override
    protected void doInitialize(final Session daemonSession) throws RepositoryException {
        if (!schedulerConfig.isEnabled()) {
            LOG.info("DocumentHistorySchedulerDaemonModule is disabled");
            return;
        }

        this.daemonSession = daemonSession;
        this.repository = daemonSession.getRepository();

        if (repository == null) {
            LOG.warn("Repository not available from daemon session, cannot schedule jobs");
            return;
        }

        // Create a scheduled executor service
        executorService = new ScheduledThreadPoolExecutor(2);

        LOG.info("DocumentHistorySchedulerDaemonModule initialized successfully with scheduled executor");
        LOG.info("Scheduling jobs with cron expression: {}", schedulerConfig.getCronExpression());

        // Schedule document revisions cleaner job
        if (schedulerConfig.isRevisionsCleanerEnabled()) {
            scheduleRevisionsCleanerJob();
            LOG.info("Scheduled DocumentRevisionsCleanerJob");
        }

        // Schedule attic cleanup job
        if (schedulerConfig.isAtticCleanupEnabled()) {
            scheduleAtticCleanupJob();
            LOG.info("Scheduled AtticCleanupJob");
        }
    }

    /**
     * Schedule the document revisions cleaner job to run every 5 minutes (for demo).
     * In production, use the cron expression from schedulerConfig.
     */
    private void scheduleRevisionsCleanerJob() {
        executorService.scheduleAtFixedRate(() -> {
            try {
                Runnable job = createRevisionsCleanerJob(daemonSession);
                job.run();
            } catch (final Exception e) {
                LOG.error("Error running DocumentRevisionsCleanerJob: {}", e.getMessage(), e);
            }
        }, 1, 5, TimeUnit.MINUTES); // Run after 1 minute, then every 5 minutes
    }

    /**
     * Schedule the attic cleanup job to run every 5 minutes (for demo).
     * In production, use the cron expression from schedulerConfig.
     */
    private void scheduleAtticCleanupJob() {
        executorService.scheduleAtFixedRate(() -> {
            try {
                Runnable job = createAtticCleanupJob(daemonSession);
                job.run();
            } catch (final Exception e) {
                LOG.error("Error running AtticCleanupJob: {}", e.getMessage(), e);
            }
        }, 2, 5, TimeUnit.MINUTES); // Run after 2 minutes, then every 5 minutes
    }

    @Override
    protected void doShutdown() {
        if (executorService != null) {
            LOG.info("Shutting down DocumentHistorySchedulerDaemonModule executor service");
            executorService.shutdownNow();
        }
        LOG.info("DocumentHistorySchedulerDaemonModule shutdown complete");
    }

    /**
     * Create a document revisions cleaner job instance.
     * This method can be called by job schedulers to instantiate the job.
     *
     * @param session the JCR session for the job
     * @return a new DocumentRevisionsCleanerJob instance
     */
    public Runnable createRevisionsCleanerJob(final Session session) {
        return new DocumentRevisionsCleanerJob(session, defaultConfig, documentTypeConfigs, schedulerConfig);
    }

    /**
     * Create an attic cleanup job instance.
     * This method can be called by job schedulers to instantiate the job.
     *
     * @param session the JCR session for the job
     * @return a new AtticCleanupJob instance
     */
    public Runnable createAtticCleanupJob(final Session session) {
        return new AtticCleanupJob(session, schedulerConfig);
    }

    /**
     * Get the scheduler configuration.
     * Useful for job schedulers that need configuration details.
     *
     * @return the scheduler configuration
     */
    public SchedulerConfiguration getSchedulerConfiguration() {
        return schedulerConfig;
    }
}
