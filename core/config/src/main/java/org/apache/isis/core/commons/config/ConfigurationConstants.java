/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.isis.core.commons.config;

import java.util.List;

import org.apache.isis.commons.internal.base._Strings;
import org.apache.isis.commons.internal.collections._Lists;

public final class ConfigurationConstants {
    
    public static final String ROOT = "isis.";

    public static final String LIST_SEPARATOR = ",";
    public static final String DELIMITER = ".";
    public static final String DEFAULT_CONFIG_DIRECTORY = "config";
    public static final String WEBINF_DIRECTORY = "WEB-INF";
    public static final String WEBINF_FULL_DIRECTORY = "src/main/webapp/" + WEBINF_DIRECTORY;

    public static final String DEFAULT_CONFIG_FILE = "isis.properties";
    public static final String WEB_CONFIG_FILE = "web.properties";

    public static final List<String> PROTECTED_KEYS =
            _Lists.of("password", "apiKey", "authToken");

    public static String maskIfProtected(final String key, final String value) {
        return isProtected(key) ? "********" : value;
    }
    
    // -- HELPER
    
    private ConfigurationConstants() {}

    static boolean isProtected(final String key) {
        if(_Strings.isNullOrEmpty(key)) {
            return false;
        }
        final String toLowerCase = key.toLowerCase();
        for (String protectedKey : ConfigurationConstants.PROTECTED_KEYS) {
            if(toLowerCase.contains(protectedKey.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
}