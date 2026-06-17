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
 * Tests for {@link DocumentHistoryCleanerTask#doExecute()} logic using mocked JCR.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentHistoryCleanerTaskExecuteTest {

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

    @BeforeEach
    void setUpValidNode() throws RepositoryException {
        when(documentNode.isNodeType("mix:versionable")).thenReturn(true);
        when(documentNode.getPath()).thenReturn(DOC_PATH);
        when(documentNode.getPrimaryNodeType()).thenReturn(primaryNodeType);
        when(primaryNodeType.getName()).thenReturn("myhippo:basedocument");
    }

    @Test
    void execute_bothNegative_neverAccessesVersionManager() throws RepositoryException {
        final DocumentHistoryCleanerTask task = new DocumentHistoryCleanerTask(session, documentNode);
        // maxDays=-1, maxRevisions=-1 (defaults) → early return, no JCR access
        task.execute();
        verify(session, never()).getWorkspace();
    }

    @Test
    void execute_maxRevisionsZero_removesAllNonRootUnlabelledVersions() throws RepositoryException {
        // Set up JCR mocks
        when(session.getWorkspace()).thenReturn(workspace);
        when(workspace.getVersionManager()).thenReturn(versionManager);
        when(versionManager.getVersionHistory(DOC_PATH)).thenReturn(versionHistory);

        // Two plain versions (not root, no labels)
        final Calendar old = Calendar.getInstance();
        old.setTimeInMillis(System.currentTimeMillis() - 100_000L);

        when(versionIterator.hasNext()).thenReturn(true, true, false);
        when(versionIterator.nextVersion()).thenReturn(version1, version2);
        when(versionHistory.getAllVersions()).thenReturn(versionIterator);

        when(version1.getName()).thenReturn("1.0");
        when(version1.getCreated()).thenReturn(old);
        when(version1.getPath()).thenReturn(DOC_PATH + "/jcr:versionStorage/1.0");
        when(versionHistory.getVersionLabels(version1)).thenReturn(new String[]{});

        when(version2.getName()).thenReturn("1.1");
        when(version2.getCreated()).thenReturn(old);
        when(version2.getPath()).thenReturn(DOC_PATH + "/jcr:versionStorage/1.1");
        when(versionHistory.getVersionLabels(version2)).thenReturn(new String[]{});

        final DocumentHistoryCleanerTask task = new DocumentHistoryCleanerTask(session, documentNode);
        task.setMaxRevisions(0L);
        task.execute();

        // Both versions should be removed (0 to keep, 2 surplus)
        verify(versionHistory).removeVersion("1.0");
        verify(versionHistory).removeVersion("1.1");
    }

    @Test
    void execute_maxRevisionsOne_keepsMostRecentVersion() throws RepositoryException {
        when(session.getWorkspace()).thenReturn(workspace);
        when(workspace.getVersionManager()).thenReturn(versionManager);
        when(versionManager.getVersionHistory(DOC_PATH)).thenReturn(versionHistory);

        final Calendar old = Calendar.getInstance();
        old.setTimeInMillis(System.currentTimeMillis() - 100_000L);

        when(versionIterator.hasNext()).thenReturn(true, true, false);
        when(versionIterator.nextVersion()).thenReturn(version1, version2);
        when(versionHistory.getAllVersions()).thenReturn(versionIterator);

        when(version1.getName()).thenReturn("1.0");
        when(version1.getCreated()).thenReturn(old);
        when(version1.getPath()).thenReturn(DOC_PATH + "/jcr:versionStorage/1.0");
        when(versionHistory.getVersionLabels(version1)).thenReturn(new String[]{});

        when(version2.getName()).thenReturn("1.1");
        when(version2.getCreated()).thenReturn(old);
        when(version2.getPath()).thenReturn(DOC_PATH + "/jcr:versionStorage/1.1");
        when(versionHistory.getVersionLabels(version2)).thenReturn(new String[]{});

        final DocumentHistoryCleanerTask task = new DocumentHistoryCleanerTask(session, documentNode);
        task.setMaxRevisions(1L);
        task.execute();

        // Only the oldest (version1) should be removed
        verify(versionHistory).removeVersion("1.0");
        verify(versionHistory, never()).removeVersion("1.1");
    }

    @Test
    void execute_rootVersionSkipped_neverRemovesJcrRootVersion() throws RepositoryException {
        when(session.getWorkspace()).thenReturn(workspace);
        when(workspace.getVersionManager()).thenReturn(versionManager);
        when(versionManager.getVersionHistory(DOC_PATH)).thenReturn(versionHistory);

        final Version rootVersion = version1;
        when(versionIterator.hasNext()).thenReturn(true, false);
        when(versionIterator.nextVersion()).thenReturn(rootVersion);
        when(versionHistory.getAllVersions()).thenReturn(versionIterator);
        when(rootVersion.getName()).thenReturn("jcr:rootVersion");
        when(versionHistory.getVersionLabels(rootVersion)).thenReturn(new String[]{});

        final DocumentHistoryCleanerTask task = new DocumentHistoryCleanerTask(session, documentNode);
        task.setMaxRevisions(0L);
        task.execute();

        verify(versionHistory, never()).removeVersion(anyString());
    }

    @Test
    void execute_labelledVersionSkipped_neverRemovesRevisionVariant() throws RepositoryException {
        when(session.getWorkspace()).thenReturn(workspace);
        when(workspace.getVersionManager()).thenReturn(versionManager);
        when(versionManager.getVersionHistory(DOC_PATH)).thenReturn(versionHistory);

        when(versionIterator.hasNext()).thenReturn(true, false);
        when(versionIterator.nextVersion()).thenReturn(version1);
        when(versionHistory.getAllVersions()).thenReturn(versionIterator);
        when(version1.getName()).thenReturn("1.0");
        // version with a label is a "revision variant" → must not be removed
        when(versionHistory.getVersionLabels(version1)).thenReturn(new String[]{"live"});

        final DocumentHistoryCleanerTask task = new DocumentHistoryCleanerTask(session, documentNode);
        task.setMaxRevisions(0L);
        task.execute();

        verify(versionHistory, never()).removeVersion(anyString());
    }

    @Test
    void execute_maxDays_removesOldVersions() throws RepositoryException {
        when(session.getWorkspace()).thenReturn(workspace);
        when(workspace.getVersionManager()).thenReturn(versionManager);
        when(versionManager.getVersionHistory(DOC_PATH)).thenReturn(versionHistory);

        // version1 is old (61 days ago), version2 is recent (1 day ago)
        final Calendar old = Calendar.getInstance();
        old.setTimeInMillis(System.currentTimeMillis() - (61L * 24 * 60 * 60 * 1000));

        final Calendar recent = Calendar.getInstance();
        recent.setTimeInMillis(System.currentTimeMillis() - (1L * 24 * 60 * 60 * 1000));

        when(versionIterator.hasNext()).thenReturn(true, true, false);
        when(versionIterator.nextVersion()).thenReturn(version1, version2);
        when(versionHistory.getAllVersions()).thenReturn(versionIterator);

        when(version1.getName()).thenReturn("1.0");
        when(version1.getCreated()).thenReturn(old);
        when(version1.getPath()).thenReturn(DOC_PATH + "/jcr:versionStorage/1.0");
        when(versionHistory.getVersionLabels(version1)).thenReturn(new String[]{});

        when(version2.getName()).thenReturn("1.1");
        when(version2.getCreated()).thenReturn(recent);
        when(version2.getPath()).thenReturn(DOC_PATH + "/jcr:versionStorage/1.1");
        when(versionHistory.getVersionLabels(version2)).thenReturn(new String[]{});

        final DocumentHistoryCleanerTask task = new DocumentHistoryCleanerTask(session, documentNode);
        task.setMaxDays(60L); // keep 60 days

        task.execute();

        // Only version1 (61 days old) should be removed
        verify(versionHistory).removeVersion("1.0");
        verify(versionHistory, never()).removeVersion("1.1");
    }
}
