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
import co.elastic.plugin.settings.EsqlPluginSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class EsqlPluginQueryManager {

    ScheduledExecutorService scheduler = AppExecutorUtil.getAppScheduledExecutorService();
    ScheduledFuture currentTask;

    EsqlPluginSettings settings = ApplicationManager.getApplication().getService(EsqlPluginSettings.class);

    private ConcurrentHashMap<String, List<String>> indicesAndFields = new ConcurrentHashMap<>();

    public List<String> getIndices() {
        return new ArrayList<>(indicesAndFields.keySet());
    }

    public List<String> getFields(String indexName) {
        List<String> result = indicesAndFields.get(indexName);
        if (result == null) {
            result = new ArrayList<>();
        }
        return result;
    }

    public void startQueryThreadPool() {
        if (!settings.getServerUrl().isEmpty() && !settings.getApiKey().isEmpty()) {
            if (currentTask != null) {
                currentTask.cancel(true);
            }
            currentTask = scheduler.scheduleWithFixedDelay(() -> {
                try (ElasticsearchClient client = ElasticsearchClient.of(b -> b
                    .host(settings.serverUrl)
                    .apiKey(settings.apiKey)
                )) {
                    List<String> indices = client.indices().get(g -> g.index("*"))
                        .indices().keySet().stream()
                        // removing internal indices
                        .filter(x -> !x.startsWith(".internal") && !x.startsWith(".ds"))
                        .toList();

                    for (String index : indices) {
                        TypeMapping mappings = client.indices().get(g -> g.index(index))
                            .indices().get(index).mappings();
                        if (mappings != null) {
                            List<String> fields = mappings.properties().keySet().stream().toList();
                            indicesAndFields.put(index, fields);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Elasticsearch query failed: " + e.getMessage(), e);
                }
            }, 0, settings.getRefreshInterval(), TimeUnit.SECONDS);
        }
    }
}
