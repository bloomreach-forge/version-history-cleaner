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
package com.bloomreach.forge.versionhistory.core.configuration;

import java.util.HashMap;
import java.util.Map;

public final class CleanerConfiguration {

    public static final CleanerConfiguration EMPTY = new CleanerConfiguration(
            new CleanerConfigurationProperties(),
            new HashMap<>());
    private final CleanerConfigurationProperties defaultConfig;
    private final Map<String, CleanerConfigurationProperties> documentTypeConfigs;


    public CleanerConfiguration(final CleanerConfigurationProperties defaultConfig, final Map<String, CleanerConfigurationProperties> documentTypeConfigs) {
        this.defaultConfig = defaultConfig;
        this.documentTypeConfigs = documentTypeConfigs;
    }

    public long getMaxDays(final String documentType) {
        final CleanerConfigurationProperties docTypeConfig = documentTypeConfigs.get(documentType);
        return docTypeConfig != null ? docTypeConfig.getMaxDays() : defaultConfig.getMaxDays();
    }

    public long getMaxRevisions(final String documentType) {
        final CleanerConfigurationProperties docTypeConfig = documentTypeConfigs.get(documentType);
        return docTypeConfig != null ? docTypeConfig.getMaxRevisions() : defaultConfig.getMaxRevisions();
    }

    public boolean isTruncateOnDelete(final String documentType) {
        final CleanerConfigurationProperties docTypeConfig = documentTypeConfigs.get(documentType);
        return docTypeConfig != null ? docTypeConfig.isTruncateOnDelete() : defaultConfig.isTruncateOnDelete();
    }

}
