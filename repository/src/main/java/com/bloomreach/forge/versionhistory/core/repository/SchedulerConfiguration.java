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

/**
 * Configuration for Document History Scheduler services.
 */
public class SchedulerConfiguration {

    /**
     * Whether the scheduler is enabled.
     */
    private boolean enabled;

    /**
     * Cron expression for job execution schedule.
     * Default: "0 0 2 * * ?" (daily at 2 AM)
     */
    private String cronExpression = "0 0 2 * * ?";

    /**
     * Batch size for processing documents per iteration.
     * Default: 100 documents per batch
     */
    private int batchSize = 100;

    /**
     * Number of days to retain documents in the attic.
     * Documents older than this will be deleted.
     * Default: 30 days
     */
    private long atticRetentionDays = 30L;

    /**
     * Whether the document revisions cleaner job is enabled.
     */
    private boolean revisionsCleanerEnabled = true;

    /**
     * Whether the attic cleanup job is enabled.
     */
    private boolean atticCleanupEnabled = true;

    public SchedulerConfiguration() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(final String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            throw new IllegalArgumentException("cronExpression must not be null or empty");
        }
        this.cronExpression = cronExpression;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(final int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than 0");
        }
        this.batchSize = batchSize;
    }

    public long getAtticRetentionDays() {
        return atticRetentionDays;
    }

    public void setAtticRetentionDays(final long atticRetentionDays) {
        if (atticRetentionDays < 0) {
            throw new IllegalArgumentException("atticRetentionDays must be >= 0");
        }
        this.atticRetentionDays = atticRetentionDays;
    }

    public boolean isRevisionsCleanerEnabled() {
        return revisionsCleanerEnabled;
    }

    public void setRevisionsCleanerEnabled(final boolean revisionsCleanerEnabled) {
        this.revisionsCleanerEnabled = revisionsCleanerEnabled;
    }

    public boolean isAtticCleanupEnabled() {
        return atticCleanupEnabled;
    }

    public void setAtticCleanupEnabled(final boolean atticCleanupEnabled) {
        this.atticCleanupEnabled = atticCleanupEnabled;
    }
}
