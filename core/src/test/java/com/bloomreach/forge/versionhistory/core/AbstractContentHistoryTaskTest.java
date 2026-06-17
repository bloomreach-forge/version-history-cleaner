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

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class AbstractContentHistoryTaskTest {

    @Mock
    private Session session;

    /**
     * Minimal concrete subclass used only to test the abstract base.
     */
    private static class TestTask extends AbstractContentHistoryTask {

        private boolean executed = false;

        TestTask(final Session session) {
            super(session);
        }

        @Override
        protected void doExecute() throws RepositoryException {
            executed = true;
        }

        boolean wasExecuted() {
            return executed;
        }
    }

    @Test
    void constructor_nullSession_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new TestTask(null));
    }

    @Test
    void getSession_returnsInjectedSession() {
        final TestTask task = new TestTask(session);
        assertSame(session, task.getSession());
    }

    @Test
    void getDefaultLogger_returnsNonNull() {
        final TestTask task = new TestTask(session);
        assertNotNull(task.getDefaultLogger());
    }

    @Test
    void getLogger_noCustomLogger_returnsDefaultLogger() {
        final TestTask task = new TestTask(session);
        assertSame(task.getDefaultLogger(), task.getLogger());
    }

    @Test
    void getLogger_customLoggerSet_returnsCustomLogger() {
        final TestTask task = new TestTask(session);
        final Logger custom = LoggerFactory.getLogger("custom");
        task.setLogger(custom);
        assertSame(custom, task.getLogger());
    }

    @Test
    void setLogger_null_fallsBackToDefaultLogger() {
        final TestTask task = new TestTask(session);
        final Logger custom = LoggerFactory.getLogger("custom");
        task.setLogger(custom);
        task.setLogger(null);
        assertSame(task.getDefaultLogger(), task.getLogger());
    }

    @Test
    void execute_invokesDoExecute() throws RepositoryException {
        final TestTask task = new TestTask(session);
        task.execute();
        assertEquals(true, task.wasExecuted());
    }
}
