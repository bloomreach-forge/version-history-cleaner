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
package com.bloomreach.forge.versionhistory.core.query;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class QueryHelper {

    private static final String JCR_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";

    private QueryHelper() {}

    /**
     * Format a local date time to a JCR XPATH query value
     * @param time the time to format
     * @return JCR formatted date time
     * @throws IllegalArgumentException when time is null
     */
    public static String formatTime(final LocalDateTime time) {
        if(time == null) {
            throw new IllegalArgumentException("Time is not allowed to be null");
        }
        return time.format(DateTimeFormatter.ofPattern(JCR_DATE_TIME_FORMAT));
    }

}
