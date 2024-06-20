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
package com.bloomreach.forge.versionhistory.core;

import java.util.Calendar;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionIterator;
import javax.jcr.version.VersionManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import com.bloomreach.forge.versionhistory.core.exception.IllegalDocumentException;

/**
 * Document version history truncater task.
 */
public class DocumentHistoryTruncaterTask extends AbstractDocumentHistoryTask {

    /**
     * Task to truncate document history and document node from attic when deleted.
     *
     * {@inheritDoc}
     */
    public DocumentHistoryTruncaterTask(final Session session, final Node documentNode) throws RepositoryException, IllegalDocumentException {
        super(session, documentNode);
    }

    @Override
    protected void doExecute() throws RepositoryException {
        final Node documentNode = getDocumentNode();
        final String documentNodePath = documentNode.getPath();
        final VersionManager versionManager = getSession().getWorkspace().getVersionManager();
        final VersionHistory versionHistory = versionManager.getVersionHistory(documentNodePath);

        if (StringUtils.startsWith(documentNodePath, "/content/attic/")) {
            final Node handle = documentNode.getParent();
            // delete handle node which contains a node referencing a version before truncating versions.
            handle.remove();
            // to remove all the version references in the attic node.
            getSession().save();
        }

        for (VersionIterator versionIt = versionHistory.getAllVersions(); versionIt.hasNext();) {
            final Version version = versionIt.nextVersion();

            if (version == null) {
                continue;
            }

            if (!version.getName().equals("jcr:rootVersion")) {
                final Calendar created = version.getCreated();
                getLogger().info("Truncating version, '{}' created on {} at {}, of document node at {}: {}",
                        version.getName(), DateFormatUtils.ISO_8601_EXTENDED_DATETIME_TIME_ZONE_FORMAT.format(created),
                        version.getPath(), documentNodePath, version.getName());

                versionHistory.removeVersion(version.getName());
            }
        }
    }
}
