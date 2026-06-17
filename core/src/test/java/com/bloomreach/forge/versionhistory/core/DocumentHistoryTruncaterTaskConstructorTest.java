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
import javax.jcr.nodetype.NodeType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentHistoryTruncaterTaskConstructorTest {

    @Mock
    private Session session;

    @Mock
    private Node documentNode;

    @Mock
    private NodeType primaryNodeType;

    @BeforeEach
    void setUpValidNode() throws RepositoryException {
        when(documentNode.isNodeType("mix:versionable")).thenReturn(true);
        when(documentNode.getPath()).thenReturn("/content/documents/mySite/myDoc");
        when(documentNode.getPrimaryNodeType()).thenReturn(primaryNodeType);
        when(primaryNodeType.getName()).thenReturn("myhippo:basedocument");
    }

    @Test
    void constructor_nullDocumentNode_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentHistoryTruncaterTask(session, null));
    }

    @Test
    void constructor_nonVersionableNode_throwsIllegalArgumentException() throws RepositoryException {
        when(documentNode.isNodeType("mix:versionable")).thenReturn(false);
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentHistoryTruncaterTask(session, documentNode));
    }

    @Test
    void constructor_nodeNotUnderContent_throwsIllegalArgumentException() throws RepositoryException {
        when(documentNode.getPath()).thenReturn("/some/other/path");
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentHistoryTruncaterTask(session, documentNode));
    }

    @Test
    void constructor_hstPrimaryType_throwsIllegalArgumentException() throws RepositoryException {
        when(primaryNodeType.getName()).thenReturn("hst:sitemap");
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentHistoryTruncaterTask(session, documentNode));
    }

    @Test
    void constructor_hippoConfigurationPath_throwsIllegalArgumentException() throws RepositoryException {
        when(documentNode.getPath()).thenReturn("/hippo:configuration/hippo:domains");
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentHistoryTruncaterTask(session, documentNode));
    }

    @Test
    void constructor_hippoNamespacesRootPath_throwsIllegalArgumentException() throws RepositoryException {
        when(documentNode.getPath()).thenReturn("/hippo:namespaces");
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentHistoryTruncaterTask(session, documentNode));
    }

    @Test
    void constructor_hippoNamespacesChildPath_throwsIllegalArgumentException() throws RepositoryException {
        when(documentNode.getPath()).thenReturn("/hippo:namespaces/myhippo:mytype");
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentHistoryTruncaterTask(session, documentNode));
    }

    @Test
    void constructor_validNode_createsTaskSuccessfully() throws RepositoryException {
        final DocumentHistoryTruncaterTask task = new DocumentHistoryTruncaterTask(session, documentNode);
        assertNotNull(task);
    }
}
