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
package com.bloomreach.forge.versionhistory.core.jobs.context;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;

import org.apache.commons.lang3.StringUtils;
import org.onehippo.repository.scheduling.RepositoryJobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bloomreach.forge.versionhistory.core.configuration.CleanerConfiguration;
import com.bloomreach.forge.versionhistory.core.configuration.CleanerConfigurationProperties;

import static com.bloomreach.forge.versionhistory.core.configuration.CleanerConfigurationConstants.DEFAULT_MAX_DAYS;
import static com.bloomreach.forge.versionhistory.core.configuration.CleanerConfigurationConstants.DEFAULT_MAX_REVISIONS;
import static com.bloomreach.forge.versionhistory.core.configuration.CleanerConfigurationConstants.DEFAULT_TRUNCATE_ONDELETE;
import static com.bloomreach.forge.versionhistory.core.configuration.CleanerConfigurationConstants.DOCTYPE_PREFIXED_PROP_NAME_PATTERN;
import static com.bloomreach.forge.versionhistory.core.configuration.CleanerConfigurationConstants.MAX_DAYS;
import static com.bloomreach.forge.versionhistory.core.configuration.CleanerConfigurationConstants.MAX_REVISIONS;
import static com.bloomreach.forge.versionhistory.core.configuration.CleanerConfigurationConstants.TRUNCATE_ONDELETE;

public final class JobContextHelper {

    private static final Logger log = LoggerFactory.getLogger(JobContextHelper.class);

    private JobContextHelper() {
    }

    public static int parseIntAttribute(final RepositoryJobExecutionContext context, final String attrName, final int defaultValue) {
        final String attrValue = context.getAttribute(attrName);
        if (StringUtils.isNotBlank(attrValue)) {
            try {
                return Integer.parseInt(attrValue.trim());
            } catch (final NumberFormatException e) {
                log.warn("'{}' configuration attribute cannot be parsed. Expected a long but was '{}'", attrName, attrValue);
            }
        }
        return defaultValue;
    }

    public static long parseLongAttribute(final RepositoryJobExecutionContext context, final String attrName, final long defaultValue) {
        final String attrValue = context.getAttribute(attrName);
        if (StringUtils.isNotBlank(attrValue)) {
            try {
                return Long.parseLong(attrValue.trim());
            } catch (final NumberFormatException e) {
                log.warn("'{}' configuration attribute cannot be parsed. Expected a long but was '{}'", attrName, attrValue);
            }
        }
        return defaultValue;
    }

    public static boolean parseBooleanAttribute(final RepositoryJobExecutionContext context, final String attrName, final boolean defaultValue) {
        final String attrValue = context.getAttribute(attrName);
        if (StringUtils.isNotBlank(attrValue)) {
            return Boolean.parseBoolean(attrValue.trim());
        }
        return defaultValue;
    }

    public static Optional<String> getStringAttribute(final RepositoryJobExecutionContext context, final String attrName) {
        final String attrValue = context.getAttribute(attrName);
        if (StringUtils.isNotBlank(attrValue)) {
            return Optional.of(attrValue.trim());
        }
        return Optional.empty();
    }

    public static CleanerConfiguration loadCleaningConfiguration(final RepositoryJobExecutionContext context) {
        final CleanerConfigurationProperties defaultConfig = new CleanerConfigurationProperties();
        defaultConfig.setMaxDays(parseLongAttribute(context, DEFAULT_MAX_DAYS, -1L));
        defaultConfig.setMaxRevisions(parseLongAttribute(context, DEFAULT_MAX_REVISIONS, -1L));
        defaultConfig.setTruncateOnDelete(parseBooleanAttribute(context, DEFAULT_TRUNCATE_ONDELETE, false));

        final Map<String, CleanerConfigurationProperties> documentTypeConfigs = new HashMap<>();

        for (final String attributeName : context.getAttributeNames()) {
            final Matcher matcher = DOCTYPE_PREFIXED_PROP_NAME_PATTERN.matcher(attributeName);
            if (matcher.matches()) {
                final String docTypeName = matcher.group(1);

                final CleanerConfigurationProperties documentTypeConfig;
                if (documentTypeConfigs.containsKey(docTypeName)) {
                    documentTypeConfig = documentTypeConfigs.get(docTypeName);
                } else {
                    documentTypeConfig = new CleanerConfigurationProperties();

                    // Set default values
                    documentTypeConfig.setMaxDays(defaultConfig.getMaxDays());
                    documentTypeConfig.setMaxRevisions(defaultConfig.getMaxRevisions());
                    documentTypeConfig.setTruncateOnDelete(defaultConfig.isTruncateOnDelete());

                    documentTypeConfigs.put(docTypeName, documentTypeConfig);
                }

                final String configPropName = matcher.group(2);

                if (MAX_DAYS.equals(configPropName)) {
                    documentTypeConfig.setMaxDays(parseLongAttribute(context, attributeName, -1L));
                } else if (MAX_REVISIONS.equals(configPropName)) {
                    documentTypeConfig.setMaxRevisions(parseLongAttribute(context, attributeName, -1L));
                } else if (TRUNCATE_ONDELETE.equals(configPropName)) {
                    documentTypeConfig.setTruncateOnDelete(parseBooleanAttribute(context, attributeName, false));
                }
            }
        }
        return new CleanerConfiguration(defaultConfig, documentTypeConfigs);
    }

}
