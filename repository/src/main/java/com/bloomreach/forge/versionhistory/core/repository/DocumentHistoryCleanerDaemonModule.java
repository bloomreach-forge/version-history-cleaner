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
package com.bloomreach.forge.versionhistory.core.repository;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.hippoecm.repository.util.JcrUtils;
import org.onehippo.cms7.services.eventbus.HippoEventListenerRegistry;
import org.onehippo.repository.modules.AbstractReconfigurableDaemonModule;

import com.bloomreach.forge.versionhistory.core.configuration.CleanerConfiguration;
import com.bloomreach.forge.versionhistory.core.configuration.CleanerConfigurationConstants;
import com.bloomreach.forge.versionhistory.core.configuration.CleanerConfigurationProperties;

/**
 * Document History Cleaner Daemon Module.
 */
public class DocumentHistoryCleanerDaemonModule extends AbstractReconfigurableDaemonModule {

    private CleanerConfiguration cleanerConfiguration = CleanerConfiguration.EMPTY;
    private DocumentHistoryCleanerListener documentHistoryCleanerListener;

    @Override
    protected void doConfigure(final Node moduleConfig) throws RepositoryException {
        cleanerConfiguration = readConfiguration(moduleConfig);
    }

    private CleanerConfiguration readConfiguration(final Node moduleConfig) throws RepositoryException {
        final CleanerConfigurationProperties defaultConfig = new CleanerConfigurationProperties();
        defaultConfig.setMaxDays(JcrUtils.getLongProperty(moduleConfig, CleanerConfigurationConstants.DEFAULT_MAX_DAYS, -1L));
        defaultConfig.setMaxRevisions(JcrUtils.getLongProperty(moduleConfig, CleanerConfigurationConstants.DEFAULT_MAX_REVISIONS, -1L));
        defaultConfig.setTruncateOnDelete(JcrUtils.getBooleanProperty(moduleConfig, CleanerConfigurationConstants.DEFAULT_TRUNCATE_ONDELETE, false));

        final Map<String, CleanerConfigurationProperties> documentTypeConfigs = new HashMap<>();

        for (final PropertyIterator propIt = moduleConfig.getProperties(); propIt.hasNext(); ) {
            final Property prop = propIt.nextProperty();

            if (prop == null) {
                continue;
            }

            final String propName = prop.getName();
            final Matcher matcher = CleanerConfigurationConstants.DOCTYPE_PREFIXED_PROP_NAME_PATTERN.matcher(propName);

            if (matcher.matches()) {
                final String docTypeName = matcher.group(1);
                CleanerConfigurationProperties documentTypeConfig = documentTypeConfigs.get(docTypeName);

                if (documentTypeConfig == null) {
                    documentTypeConfig = new CleanerConfigurationProperties();
                    documentTypeConfig.setMaxDays(defaultConfig.getMaxDays());
                    documentTypeConfig.setMaxRevisions(defaultConfig.getMaxRevisions());
                    documentTypeConfig.setTruncateOnDelete(defaultConfig.isTruncateOnDelete());
                    documentTypeConfigs.put(docTypeName, documentTypeConfig);
                }

                final String configPropName = matcher.group(2);

                if (CleanerConfigurationConstants.MAX_DAYS.equals(configPropName)) {
                    documentTypeConfig.setMaxDays(prop.getLong());
                } else if (CleanerConfigurationConstants.MAX_REVISIONS.equals(configPropName)) {
                    documentTypeConfig.setMaxRevisions(prop.getLong());
                } else if (CleanerConfigurationConstants.TRUNCATE_ONDELETE.equals(configPropName)) {
                    documentTypeConfig.setTruncateOnDelete(prop.getBoolean());
                }
            }
        }
        return new CleanerConfiguration(defaultConfig, documentTypeConfigs);
    }

    @Override
    protected void doInitialize(final Session daemonSession) throws RepositoryException {
        documentHistoryCleanerListener = new DocumentHistoryCleanerListener(daemonSession, cleanerConfiguration);
        HippoEventListenerRegistry.get().register(documentHistoryCleanerListener);
    }

    @Override
    protected void doShutdown() {
        if (documentHistoryCleanerListener != null) {
            HippoEventListenerRegistry.get().unregister(documentHistoryCleanerListener);
            documentHistoryCleanerListener = null;
        }
    }

}
