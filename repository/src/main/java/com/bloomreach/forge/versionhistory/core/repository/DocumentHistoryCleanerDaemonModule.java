/*
 *  Copyright 2026 BloomReach, Inc. (https://www.bloomreach.com)
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
import java.util.regex.Pattern;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.hippoecm.repository.util.JcrUtils;
import org.onehippo.cms7.services.eventbus.HippoEventListenerRegistry;
import org.onehippo.repository.modules.AbstractReconfigurableDaemonModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Document History Cleaner Daemon Module.
 */
public class DocumentHistoryCleanerDaemonModule extends AbstractReconfigurableDaemonModule {

    private static final Logger log = LoggerFactory.getLogger(DocumentHistoryCleanerDaemonModule.class);

    private static final Pattern DOCTYPE_PREFIXED_PROP_NAME_PATTERN = Pattern
            .compile("^([A-Za-z_\\-]+:[A-Za-z_\\-]+)\\.(.+)$");

    private DocumentHistoryCleanerConfiguration defaultConfig = new DocumentHistoryCleanerConfiguration();
    private Map<String, DocumentHistoryCleanerConfiguration> documentTypeConfigs = new HashMap<>();
    private DocumentHistoryCleanerListener documentHistoryCleanerListener;

    @Override
    protected void doConfigure(final Node moduleConfig) throws RepositoryException {
        defaultConfig.setMaxDays(JcrUtils.getLongProperty(moduleConfig, "default.max.days", -1L));
        defaultConfig.setMaxRevisions(JcrUtils.getLongProperty(moduleConfig, "default.max.revisions", -1L));
        defaultConfig.setTruncateOnDelete(JcrUtils.getBooleanProperty(moduleConfig, "default.truncate.ondelete", false));

        documentTypeConfigs.clear();

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

        log.debug("DocumentHistoryCleanerDaemonModule configured: default=[maxDays={}, maxRevisions={}, truncateOnDelete={}], docTypeOverrides={}",
                defaultConfig.getMaxDays(), defaultConfig.getMaxRevisions(), defaultConfig.isTruncateOnDelete(),
                documentTypeConfigs.keySet());
    }

    @Override
    protected void doInitialize(final Session daemonSession) throws RepositoryException {
        documentHistoryCleanerListener = new DocumentHistoryCleanerListener(daemonSession, defaultConfig,
                documentTypeConfigs);
        HippoEventListenerRegistry.get().register(documentHistoryCleanerListener);
        log.info("DocumentHistoryCleanerDaemonModule initialized and listener registered.");
    }

    @Override
    protected void doShutdown() {
        if (documentHistoryCleanerListener != null) {
            HippoEventListenerRegistry.get().unregister(documentHistoryCleanerListener);
            documentHistoryCleanerListener = null;
        }
        log.info("DocumentHistoryCleanerDaemonModule shut down.");
    }
}
