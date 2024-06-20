/*
 *  Copyright 2019-2024 BloomReach, Inc. (https://www.bloomreach.com)
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
package com.bloomreach.forge.versionhistory.core.configuration;

/**
 * Configuration for Document History Cleaner task.
 */
public class CleanerConfigurationProperties {

    /**
     * Max revisions to keep in the version history.
     * If this is set to a negative integer, then this option will be ignored.
     */
    private long maxRevisions = -1L;

    /**
     * Max days to keep in the version history since the version created time.
     * If this is set to a negative integer, then this option will be ignored.
     */
    private long maxDays = -1L;

    /**
     * Whether or not to truncate all the version history when a document is deleted.
     */
    private boolean truncateOnDelete;

    public CleanerConfigurationProperties() {
    }

    public long getMaxDays() {
        return maxDays;
    }

    public void setMaxDays(long maxDays) {
        this.maxDays = maxDays;
    }

    public long getMaxRevisions() {
        return maxRevisions;
    }

    public void setMaxRevisions(long maxRevisions) {
        this.maxRevisions = maxRevisions;
    }

    public boolean isTruncateOnDelete() {
        return truncateOnDelete;
    }

    public void setTruncateOnDelete(boolean truncateOnDelete) {
        this.truncateOnDelete = truncateOnDelete;
    }

}
