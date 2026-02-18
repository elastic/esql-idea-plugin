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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.XCollection;

import java.util.ArrayList;
import java.util.List;

@State(
    name = "ESQLPluginSettings",
    storages = @Storage("esql-plugin.xml")
)
public class EsqlPluginSettings implements PersistentStateComponent<EsqlPluginSettings> {

    @XCollection(elementTypes = EsqlConnection.class)
    public List<EsqlConnection> connections = new ArrayList<>();

    public String activeConnectionName = "";

    @Override
    public EsqlPluginSettings getState() {
        return this;
    }

    @Override
    public void loadState(EsqlPluginSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public EsqlConnection getActiveConnection() {
        return connections.stream()
            .filter(c -> c.name.equals(activeConnectionName))
            .findFirst()
            .orElse(null);
    }

    public String getServerUrl() {
        EsqlConnection active = getActiveConnection();
        return active != null ? active.serverUrl : "";
    }

    public String getApiKey() {
        EsqlConnection active = getActiveConnection();
        return active != null ? active.apiKey : "";
    }

    public int getRefreshInterval() {
        EsqlConnection active = getActiveConnection();
        return active != null ? active.refreshInterval : 60;
    }

    public void addConnection(EsqlConnection connection) {
        connections.add(connection);
        activeConnectionName = connection.name;
    }

    public void removeConnection(String name) {
        connections.removeIf(c -> c.name.equals(name));
        if (activeConnectionName.equals(name)) {
            activeConnectionName = connections.isEmpty() ? "" : connections.get(0).name;
        }
    }

    public void updateConnection(String oldName, EsqlConnection updated) {
        for (int i = 0; i < connections.size(); i++) {
            if (connections.get(i).name.equals(oldName)) {
                connections.set(i, updated);
                if (activeConnectionName.equals(oldName)) {
                    activeConnectionName = updated.name;
                }
                return;
            }
        }
    }

    public List<String> getConnectionNames() {
        return connections.stream().map(c -> c.name).toList();
    }
}
