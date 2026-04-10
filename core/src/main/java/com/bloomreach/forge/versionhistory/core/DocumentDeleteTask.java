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

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.hippoecm.repository.api.HippoWorkspace;
import org.hippoecm.repository.api.Workflow;
import org.hippoecm.repository.api.WorkflowException;
import org.hippoecm.repository.api.WorkflowManager;
import org.onehippo.repository.documentworkflow.DocumentWorkflow;

import com.bloomreach.forge.versionhistory.core.exception.IllegalDocumentException;

import static com.bloomreach.forge.versionhistory.core.NodeHelper.getNodePath;
import static org.apache.commons.lang3.StringUtils.EMPTY;

/**
 * Document version history cleaner task.
 */
public class DocumentDeleteTask extends AbstractDocumentHistoryTask {

    public static final String DELETE = "delete";
    public static final String EDITING = "editing";

    /**
     * Task to delete unpublished documents.
     *
     * {@inheritDoc}
     */
    public DocumentDeleteTask(final Session session, final Node documentNode) throws RepositoryException, IllegalDocumentException {
        super(session, documentNode);
    }

    @Override
    protected void doExecute() throws RepositoryException {
        final Node documentNode = getDocumentNode();
        final Optional<Node> handle = NodeHelper.getHandle(documentNode);
        if(handle.isPresent()) {
            final WorkflowManager workflowManager = getWorkflowManager(getSession());
            final Node handleNode = handle.get();
            final Optional<DocumentWorkflow> documentWorkflow = getDocumentWorkflow(workflowManager, handleNode);
            if(documentWorkflow.isPresent()) {
                try {
                    final DocumentWorkflow workflow = documentWorkflow.get();
                    final Map<String, Serializable> hints = workflow.hints();
                    if(hints.containsKey(DELETE)) {
                        getLogger().info("Delete document: {}", getNodePath(handleNode).orElse(EMPTY));
                        workflow.delete();
                    } else {
                        getLogger().error("Not allowed to delete document (hints={}):  {}",
                                hints.entrySet().stream()
                                        .map(entry -> entry.getKey() + "=" + entry.getValue().toString())
                                        .collect(Collectors.joining(", ")),
                                NodeHelper.getNodePath(handleNode).orElse(EMPTY)
                        );
                    }
                } catch (final WorkflowException | RemoteException e) {
                    logNodeError("Error in workflow; unable to delete the document: {}", handleNode);
                }
            } else {
                logNodeError("Document workflow is not available for node: {}", documentNode);
            }
        } else {
            logNodeError("No handle found for node: {}", documentNode);
        }
    }

    private void logNodeError(final String errorMessage, final Node node) {
        getLogger().warn(errorMessage, NodeHelper.getNodePath(node).orElse(EMPTY));
    }

    private Optional<DocumentWorkflow> getDocumentWorkflow(final WorkflowManager workflowManager, final Node handle) throws RepositoryException {
        final Workflow workflow = workflowManager.getWorkflow(EDITING, handle);
        if(workflow instanceof DocumentWorkflow) {
            return Optional.of((DocumentWorkflow) workflow);
        }
        return Optional.empty();
    }

    private WorkflowManager getWorkflowManager(final Session session) throws RepositoryException {
        final HippoWorkspace workspace = (HippoWorkspace) session.getWorkspace();
        return workspace.getWorkflowManager();
    }

}
