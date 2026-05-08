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
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.esql.EsqlFormat;
import co.elastic.clients.elasticsearch.esql.QueryRequest;
import co.elastic.clients.transport.endpoints.BinaryResponse;
import co.elastic.plugin.CommonUtils;
import co.elastic.plugin.settings.EsqlPluginSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.application.ApplicationManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EsqlQueryExecutor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static EsqlQueryResult execute(String query) {
        EsqlPluginQueryManager queryManager = ApplicationManager.getApplication()
            .getService(EsqlPluginQueryManager.class);
        if (!queryManager.isActiveConnectionConnected()) {
            return EsqlQueryResult.error("Not connected. Click the connect button in the " +
                                         "Elasticsearch " +
                                         "panel.");
        }

        EsqlPluginSettings settings = ApplicationManager.getApplication()
            .getService(EsqlPluginSettings.class);

        String serverUrl = settings.getServerUrl();
        String apiKey = settings.getApiKey();

        if (serverUrl == null || serverUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            return EsqlQueryResult.error("No connection configured. Add one in the the " +
                                         "Elasticsearch panel.");
        }

        try (ElasticsearchClient client = CommonUtils.createClientInstance(serverUrl,apiKey)) {

            BinaryResponse response = client.esql()
                .query(QueryRequest.of(q -> q.query(query).format(EsqlFormat.Json)));

            String responseBody = new BufferedReader(new InputStreamReader(response.content()))
                .lines().collect(Collectors.joining("\n"));

            return parseSuccessResponse(responseBody);

        }
        catch (ElasticsearchException e) {
            return EsqlQueryResult.error("[" + e.response().status() + "] " + e.getMessage());
        }
        catch (Exception e) {
            return EsqlQueryResult.error("Request failed: " + e.getMessage());
        }
    }


    private static EsqlQueryResult parseSuccessResponse(String responseBody) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            JsonNode columnsNode = root.get("columns");
            JsonNode valuesNode = root.get("values");

            if (columnsNode == null || valuesNode == null) {
                return EsqlQueryResult.error("Unexpected response format: missing 'columns' or 'values'");
            }

            List<EsqlQueryResult.Column> columns = new ArrayList<>();
            for (JsonNode col : columnsNode) {
                columns.add(new EsqlQueryResult.Column(
                    col.get("name").asText(),
                    col.get("type").asText()
                ));
            }

            List<List<Object>> values = new ArrayList<>();
            for (JsonNode row : valuesNode) {
                List<Object> rowValues = new ArrayList<>();
                for (JsonNode cell : row) {
                    if (cell.isNull()) {
                        rowValues.add(null);
                    } else if (cell.isNumber()) {
                        rowValues.add(cell.numberValue());
                    } else if (cell.isBoolean()) {
                        rowValues.add(cell.booleanValue());
                    } else if (cell.isArray() || cell.isObject()) {
                        rowValues.add(cell.toString());
                    } else {
                        rowValues.add(cell.asText());
                    }
                }
                values.add(rowValues);
            }

            return new EsqlQueryResult(columns, values, null);

        } catch (Exception e) {
            return EsqlQueryResult.error("Failed to parse response: " + e.getMessage());
        }
    }
}
