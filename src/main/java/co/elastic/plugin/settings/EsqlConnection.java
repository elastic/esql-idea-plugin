/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.plugin.settings;

import com.intellij.util.xmlb.annotations.Tag;

@Tag("connection")
public class EsqlConnection {
    public String name = "";
    public String serverUrl = "";
    public String apiKey = "";
    public int refreshInterval = 60;

    public EsqlConnection() {}

    public EsqlConnection(String name, String serverUrl, String apiKey, int refreshInterval) {
        this.name = name;
        this.serverUrl = serverUrl;
        this.apiKey = apiKey;
        this.refreshInterval = refreshInterval;
    }

    public EsqlConnection copy() {
        return new EsqlConnection(name, serverUrl, apiKey, refreshInterval);
    }
}
