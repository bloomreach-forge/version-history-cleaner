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
package com.bloomreach.forge.versionhistory.core.configuration;

import java.util.regex.Pattern;

public class CleanerConfigurationConstants {

    public static final Pattern DOCTYPE_PREFIXED_PROP_NAME_PATTERN = Pattern
            .compile("^([A-Za-z_\\-]+:[A-Za-z_\\-]+)\\.(.+)$");

    public static final String DEFAULT_MAX_DAYS = "default.max.days";
    public static final String DEFAULT_MAX_REVISIONS = "default.max.revisions";
    public static final String DEFAULT_TRUNCATE_ONDELETE = "default.truncate.ondelete";
    public static final String MAX_DAYS = "max.days";
    public static final String MAX_REVISIONS = "max.revisions";
    public static final String TRUNCATE_ONDELETE = "truncate.ondelete";
    public static final String MINIMUM_MINUTES_TO_LIVE = "minimum.minutes.to.live";
    public static final long DEFAULT_MINIMUM_MINUTES_TO_LIVE = 60 * 24 * 365;
    public static final String BATCH_SIZE = "batch.size";
    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final String BATCH_DELAY_MILLIS = "batch.delay.millis";
    public static final long DEFAULT_BATCH_DELAY_MILLIS = 1000;
    public static final String CLEANUP_PATH = "cleanup.path";
    public static final String DEFAULT_CLEANUP_PATH = "/content"; // i.e documents and attic
    public static final String DEPUBLICATION_DATE_PROPERTY_NAME = "depublication.date.property.name";

    /**
     * For now this property doesn't exist and should be replaced with a project specific property. The project specific
     * implementation will be responsible for setting a depublication date property to the preview document variants
     * when documents are taken offline.
     */
    public static final String DEFALT_DEPUBLICATION_PROPERTY_NAME = "hippostdpubwf:depublicationDate";



}
