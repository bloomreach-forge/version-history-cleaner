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
package com.bloomreach.forge.versionhistory.core.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentHistoryCleanerConfigurationTest {

    @Test
    void defaults_maxDaysIsNegativeOne() {
        final DocumentHistoryCleanerConfiguration config = new DocumentHistoryCleanerConfiguration();
        assertEquals(-1L, config.getMaxDays());
    }

    @Test
    void defaults_maxRevisionsIsNegativeOne() {
        final DocumentHistoryCleanerConfiguration config = new DocumentHistoryCleanerConfiguration();
        assertEquals(-1L, config.getMaxRevisions());
    }

    @Test
    void defaults_truncateOnDeleteIsFalse() {
        final DocumentHistoryCleanerConfiguration config = new DocumentHistoryCleanerConfiguration();
        assertFalse(config.isTruncateOnDelete());
    }

    @Test
    void setMaxDays_storesValue() {
        final DocumentHistoryCleanerConfiguration config = new DocumentHistoryCleanerConfiguration();
        config.setMaxDays(90L);
        assertEquals(90L, config.getMaxDays());
    }

    @Test
    void setMaxRevisions_storesValue() {
        final DocumentHistoryCleanerConfiguration config = new DocumentHistoryCleanerConfiguration();
        config.setMaxRevisions(10L);
        assertEquals(10L, config.getMaxRevisions());
    }

    @Test
    void setTruncateOnDelete_trueStoresTrue() {
        final DocumentHistoryCleanerConfiguration config = new DocumentHistoryCleanerConfiguration();
        config.setTruncateOnDelete(true);
        assertTrue(config.isTruncateOnDelete());
    }

    @Test
    void setTruncateOnDelete_falseAfterTrue_returnsFalse() {
        final DocumentHistoryCleanerConfiguration config = new DocumentHistoryCleanerConfiguration();
        config.setTruncateOnDelete(true);
        config.setTruncateOnDelete(false);
        assertFalse(config.isTruncateOnDelete());
    }

    @Test
    void setMaxDays_negativeValue_storesNegative() {
        final DocumentHistoryCleanerConfiguration config = new DocumentHistoryCleanerConfiguration();
        config.setMaxDays(-5L);
        assertEquals(-5L, config.getMaxDays());
    }

    @Test
    void setMaxRevisions_negativeValue_storesNegative() {
        final DocumentHistoryCleanerConfiguration config = new DocumentHistoryCleanerConfiguration();
        config.setMaxRevisions(-99L);
        assertEquals(-99L, config.getMaxRevisions());
    }
}
