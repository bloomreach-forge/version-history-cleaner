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

/**
 * Document version history truncater task.
 */
public class DocumentHistoryTruncaterTask extends AbstractContentHistoryTask {

    /**
     * The versionable document variant node. i.e. the preview variant node which keeps the JCR version history.
     */
    private final Node documentNode;

    public DocumentHistoryTruncaterTask(final Session session, final Node documentNode) throws RepositoryException {
        super(session);

        if (documentNode == null) {
            throw new IllegalArgumentException("document node must be not null.");
        }

        if (!documentNode.isNodeType("mix:versionable")) {
            throw new IllegalArgumentException("document node must be of type, mix:versionable.");
        }

        if (!documentNode.getPath().startsWith("/content/")) {
            throw new IllegalArgumentException("document node must be under /content/.");
        }

        if (documentNode.getPrimaryNodeType().getName().startsWith("hst:")
                || documentNode.getPath().startsWith("/hippo:configuration/")
                || documentNode.getPath().equals("/hippo:namespaces")
                || documentNode.getPath().startsWith("/hippo:namespaces/")) {
            throw new IllegalArgumentException("Not a document node, but a configuration node.");
        }

        this.documentNode = documentNode;
    }

    @Override
    protected void doExecute() throws RepositoryException {
        final String documentNodePath = documentNode.getPath();
        final VersionManager versionManager = getSession().getWorkspace().getVersionManager();
        final VersionHistory versionHistory = versionManager.getVersionHistory(documentNodePath);

        if (StringUtils.startsWith(documentNodePath, "/content/attic/")) {
            final Node handle = documentNode.getParent();
            // delete handle node which contains a node referencing a version before truncating versinos.
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
                        version.getName(), DateFormatUtils.ISO_DATETIME_TIME_ZONE_FORMAT.format(created),
                        version.getPath(), documentNodePath, version.getName());

                versionHistory.removeVersion(version.getName());
            }
        }
    }
}
