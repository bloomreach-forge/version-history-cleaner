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

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract content history management task.
 * <P>
 * <EM>Note:</EM> Any tasks extending this are not thread-safe.
 * Therefore, you should keep the lifecycle of tasks in the same thread execution cycle. e.g, in a function.
 */
public abstract class AbstractContentHistoryTask {

    private static final Logger defaultLogger = LoggerFactory.getLogger(AbstractContentHistoryTask.class);

    /**
     * The JCR session dedicated to this task.
     */
    private final Session session;

    /**
     * The logger dedicated to this task.
     * <P>
     * <EM>Note:</EM> For most flexibility, any tasks extending this are supposed to use this logger instead of
     * creating a new logger in implementations. This helps reusability in different execution contexts such as
     * groovy update scripts executing this kind of tasks will setting groovy updater engine's logger.
     */
    private Logger logger;

    public AbstractContentHistoryTask(final Session session) {
        if (session == null) {
            throw new IllegalArgumentException("session must be not null.");
        }

        this.session = session;
    }

    public Logger getDefaultLogger() {
        return defaultLogger;
    }

    public Logger getLogger() {
        return logger != null ? logger : getDefaultLogger();
    }

    public void setLogger(final Logger logger) {
        this.logger = logger;
    }

    public Session getSession() {
        return session;
    }

    public final void execute() throws RepositoryException {
        doBeforeExecute();
        doExecute();
        doAfterExecute();
    }

    protected void doBeforeExecute() throws RepositoryException {
    }

    abstract protected void doExecute() throws RepositoryException;

    protected void doAfterExecute() throws RepositoryException {
    }

}
