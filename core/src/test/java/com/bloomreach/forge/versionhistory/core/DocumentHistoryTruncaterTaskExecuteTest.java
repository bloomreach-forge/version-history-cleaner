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

import java.util.Calendar;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.nodetype.NodeType;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionIterator;
import javax.jcr.version.VersionManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DocumentHistoryTruncaterTask#doExecute()} logic using mocked JCR.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentHistoryTruncaterTaskExecuteTest {

    private static final String DOC_PATH = "/content/documents/mySite/myDoc";

    @Mock
    private Session session;
    @Mock
    private Workspace workspace;
    @Mock
    private VersionManager versionManager;
    @Mock
    private Node documentNode;
    @Mock
    private NodeType primaryNodeType;
    @Mock
    private VersionHistory versionHistory;
    @Mock
    private VersionIterator versionIterator;
    @Mock
    private Version version1;
    @Mock
    private Version version2;
    @Mock
    private Version rootVersion;

    @BeforeEach
    void setUpValidNode() throws RepositoryException {
        when(documentNode.isNodeType("mix:versionable")).thenReturn(true);
        when(documentNode.getPath()).thenReturn(DOC_PATH);
        when(documentNode.getPrimaryNodeType()).thenReturn(primaryNodeType);
        when(primaryNodeType.getName()).thenReturn("myhippo:basedocument");

        when(session.getWorkspace()).thenReturn(workspace);
        when(workspace.getVersionManager()).thenReturn(versionManager);
        when(versionManager.getVersionHistory(DOC_PATH)).thenReturn(versionHistory);
    }

    @Test
    void execute_removesAllNonRootVersions() throws RepositoryException {
        final Calendar ts = Calendar.getInstance();

        when(versionIterator.hasNext()).thenReturn(true, true, true, false);
        when(versionIterator.nextVersion()).thenReturn(rootVersion, version1, version2);
        when(versionHistory.getAllVersions()).thenReturn(versionIterator);

        when(rootVersion.getName()).thenReturn("jcr:rootVersion");
        when(version1.getName()).thenReturn("1.0");
        when(version1.getCreated()).thenReturn(ts);
        when(version1.getPath()).thenReturn(DOC_PATH + "/jcr:versionStorage/1.0");
        when(version2.getName()).thenReturn("1.1");
        when(version2.getCreated()).thenReturn(ts);
        when(version2.getPath()).thenReturn(DOC_PATH + "/jcr:versionStorage/1.1");

        final DocumentHistoryTruncaterTask task = new DocumentHistoryTruncaterTask(session, documentNode);
        task.execute();

        verify(versionHistory).removeVersion("1.0");
        verify(versionHistory).removeVersion("1.1");
        verify(versionHistory, never()).removeVersion("jcr:rootVersion");
    }

    @Test
    void execute_rootVersionOnly_neverCallsRemoveVersion() throws RepositoryException {
        when(versionIterator.hasNext()).thenReturn(true, false);
        when(versionIterator.nextVersion()).thenReturn(rootVersion);
        when(versionHistory.getAllVersions()).thenReturn(versionIterator);
        when(rootVersion.getName()).thenReturn("jcr:rootVersion");

        final DocumentHistoryTruncaterTask task = new DocumentHistoryTruncaterTask(session, documentNode);
        task.execute();

        verify(versionHistory, never()).removeVersion(anyString());
    }

    @Test
    void execute_nullVersionInIterator_skipsNull() throws RepositoryException {
        // Simulate iterator returning null (edge case defensiveness)
        when(versionIterator.hasNext()).thenReturn(true, false);
        when(versionIterator.nextVersion()).thenReturn(null);
        when(versionHistory.getAllVersions()).thenReturn(versionIterator);

        final DocumentHistoryTruncaterTask task = new DocumentHistoryTruncaterTask(session, documentNode);
        task.execute();

        verify(versionHistory, never()).removeVersion(anyString());
    }

    @Test
    void execute_atticPath_removesHandleBeforeTruncating() throws RepositoryException {
        final String atticPath = "/content/attic/mySite/myDoc";
        when(documentNode.getPath()).thenReturn(atticPath);
        when(versionManager.getVersionHistory(atticPath)).thenReturn(versionHistory);

        final Node handleNode = org.mockito.Mockito.mock(Node.class);
        when(documentNode.getParent()).thenReturn(handleNode);

        final Calendar ts = Calendar.getInstance();
        when(versionIterator.hasNext()).thenReturn(true, false);
        when(versionIterator.nextVersion()).thenReturn(version1);
        when(versionHistory.getAllVersions()).thenReturn(versionIterator);
        when(version1.getName()).thenReturn("1.0");
        when(version1.getCreated()).thenReturn(ts);
        when(version1.getPath()).thenReturn(atticPath + "/jcr:versionStorage/1.0");

        final DocumentHistoryTruncaterTask task = new DocumentHistoryTruncaterTask(session, documentNode);
        task.execute();

        // handle.remove() and session.save() must be called for attic documents
        verify(handleNode).remove();
        verify(session).save();
        verify(versionHistory).removeVersion("1.0");
    }
}
