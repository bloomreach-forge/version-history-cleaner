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

import java.util.Optional;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.hippoecm.repository.HippoStdPubWfNodeType;
import org.hippoecm.repository.api.HippoNodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeHelper {

    private static final Logger log = LoggerFactory.getLogger(NodeHelper.class);

    public static Optional<String> getNodePath(final Node node) {
        try {
            return Optional.of(node.getPath());
        } catch (final RepositoryException e) {
            return Optional.empty();
        }
    }

    public static Optional<String> getNodeIdentifier(final Session session, final String path) {
        try {
            if(session.nodeExists(path)) {
                return Optional.of(session.getNode(path).getIdentifier());
            } else {
                log.error("Content path {} does not exist", path);
            }
        } catch (RepositoryException e) {
            log.error("Unable to determine identifier for cleanup path: {}", path);
        }
        return Optional.empty();
    }

    public static Optional<Node> getHandle(final Node node) {
        try {
            if(node == null) {
                return Optional.empty();
            } else if(node.isNodeType(HippoNodeType.NT_HANDLE)) {
                return Optional.of(node);
            } else if(node.isNodeType(HippoStdPubWfNodeType.HIPPOSTDPUBWF_DOCUMENT) || node.isNodeType(HippoNodeType.NT_COMPOUND)) {
                return getHandle(node.getParent());
            }
        } catch (final Exception e) {
            log.error("Unable to get handle for node: {}", getNodePath(node).orElse(StringUtils.EMPTY), e);
        }
        return Optional.empty();
    }
}
