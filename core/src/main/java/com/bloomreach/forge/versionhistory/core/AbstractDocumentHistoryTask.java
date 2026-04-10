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
package com.bloomreach.forge.versionhistory.core;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.JcrConstants;

import com.bloomreach.forge.versionhistory.core.exception.IllegalDocumentException;

/**
 * Abstract document history management task.
 * <p>
 * <EM>Note:</EM> Any tasks extending this are not thread-safe.
 * Therefore, you should keep the lifecycle of tasks in the same thread execution cycle. e.g, in a function.
 */
public abstract class AbstractDocumentHistoryTask extends AbstractContentHistoryTask {

    /**
     * The versionable document variant node. i.e. the preview variant node which keeps the JCR version history.
     */
    private final Node documentNode;

    /**
     * Construct a content history task for a document and apply validation checks on the content node. Document
     * should confirm to the following:
     * - document should not be null
     * - document should be versionable
     * - document should be in /content folder
     * - document shouldn't be a configuration node
     *
     * @param session      the JCR session
     * @param documentNode the document node
     * @throws RepositoryException      when repository exception occurs
     * @throws IllegalDocumentException when document is null, document is not versionable, document resides
     *                                  outside the /content folder or document is of a restricted document type.
     * @throws IllegalArgumentException when session is null.
     */
    public AbstractDocumentHistoryTask(final Session session, final Node documentNode) throws RepositoryException, IllegalDocumentException {
        super(session);

        if (documentNode == null) {
            throw new IllegalDocumentException("Document node must be not null.");
        }

        if (!documentNode.isNodeType(JcrConstants.MIX_VERSIONABLE)) {
            throw new IllegalDocumentException("Document node must be of type, mix:versionable.");
        }

        if (!documentNode.getPath().startsWith("/content/")) {
            throw new IllegalDocumentException("Document node must be under /content/.");
        }

        if (documentNode.getPrimaryNodeType().getName().startsWith("hst:")
                || documentNode.getPath().startsWith("/hippo:configuration/")
                || documentNode.getPath().equals("/hippo:namespaces")
                || documentNode.getPath().startsWith("/hippo:namespaces/")) {
            throw new IllegalDocumentException("Not a document node, but a configuration node.");
        }

        this.documentNode = documentNode;
    }

    protected Node getDocumentNode() {
        return documentNode;
    }
}
