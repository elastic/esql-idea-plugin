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
package co.elastic.plugin.connection;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.plugin.settings.EsqlConnection;
import co.elastic.plugin.settings.EsqlPluginSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class EsqlPluginQueryManagerImpl implements EsqlPluginQueryManager {

    private static class ConnectionState {
        ScheduledFuture<?> task;
        final ConcurrentHashMap<String, List<String>> indicesAndFields = new ConcurrentHashMap<>();
    }

    public record CachedResult(String query, EsqlQueryResult result, long elapsedMs) {}

    private final ScheduledExecutorService scheduler = AppExecutorUtil.getAppScheduledExecutorService();
    private final EsqlPluginSettings settings = ApplicationManager.getApplication().getService(EsqlPluginSettings.class);
    private final ConcurrentHashMap<String, ConnectionState> activeConnections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<CachedResult>> cachedResults = new ConcurrentHashMap<>();

    public boolean isConnected(String connectionName) {
        return activeConnections.containsKey(connectionName);
    }

    public boolean isActiveConnectionConnected() {
        return isConnected(settings.activeConnectionName);
    }

    public List<String> getIndices() {
        ConnectionState state = activeConnections.get(settings.activeConnectionName);
        if (state == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(state.indicesAndFields.keySet());
    }

    public List<String> getFields(String indexName) {
        ConnectionState state = activeConnections.get(settings.activeConnectionName);
        if (state == null) {
            return new ArrayList<>();
        }
        List<String> result = state.indicesAndFields.get(indexName);
        return result != null ? result : new ArrayList<>();
    }

    public void addCachedResult(String query, EsqlQueryResult result, long elapsedMs) {
        String connectionName = settings.activeConnectionName;
        if (!connectionName.isEmpty()) {
            cachedResults.computeIfAbsent(connectionName, k -> new CopyOnWriteArrayList<>())
                .add(new CachedResult(query, result, elapsedMs));
        }
    }

    public List<CachedResult> getCachedResults() {
        List<CachedResult> results = cachedResults.get(settings.activeConnectionName);
        return results != null ? results : Collections.emptyList();
    }

    public List<CachedResult> getCachedResults(String connectionName) {
        List<CachedResult> results = cachedResults.get(connectionName);
        return results != null ? results : Collections.emptyList();
    }

    public void removeCachedResult(String connectionName, int index) {
        List<CachedResult> results = cachedResults.get(connectionName);
        if (results != null && index >= 0 && index < results.size()) {
            results.remove(index);
        }
    }

    public void clearCachedResults(String connectionName) {
        cachedResults.remove(connectionName);
    }

    public void connect() {
        String connectionName = settings.activeConnectionName;
        EsqlConnection conn = settings.getActiveConnection();
        if (conn == null || conn.serverUrl.isEmpty() || conn.apiKey.isEmpty()) {
            return;
        }
        if (activeConnections.containsKey(connectionName)) {
            return;
        }
        ConnectionState state = new ConnectionState();
        activeConnections.put(connectionName, state);
        startQueryThreadPool(connectionName, conn, state);
    }

    public void disconnect() {
        disconnect(settings.activeConnectionName);
    }

    public void disconnect(String connectionName) {
        ConnectionState state = activeConnections.remove(connectionName);
        if (state != null && state.task != null) {
            state.task.cancel(true);
        }
    }

    public void disconnectAll() {
        for (String connectionName : new ArrayList<>(activeConnections.keySet())) {
            disconnect(connectionName);
        }
    }

    private void startQueryThreadPool(String connectionName, EsqlConnection conn, ConnectionState state) {
        state.task = scheduler.scheduleWithFixedDelay(() -> {
            try (ElasticsearchClient client = ElasticsearchClient.of(b -> b
                .host(conn.serverUrl)
                .apiKey(conn.apiKey)
            )) {
                List<String> indices = client.indices().get(g -> g.index("*"))
                    .indices().keySet().stream()
                    .filter(x -> !x.startsWith(".internal") && !x.startsWith(".ds"))
                    .toList();

                for (String index : indices) {
                    TypeMapping mappings = client.indices().get(g -> g.index(index))
                        .indices().get(index).mappings();
                    if (mappings != null) {
                        List<String> fields = mappings.properties().keySet().stream().toList();
                        state.indicesAndFields.put(index, fields);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Elasticsearch query failed: " + e.getMessage(), e);
            }
        }, 0, conn.refreshInterval, TimeUnit.SECONDS);
    }
}
