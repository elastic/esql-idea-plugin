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

import co.elastic.plugin.settings.EsqlPluginSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.application.ApplicationManager;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class EsqlQueryExecutor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static EsqlQueryResult execute(String query) {
        EsqlPluginSettings settings = ApplicationManager.getApplication()
            .getService(EsqlPluginSettings.class);

        String serverUrl = settings.getServerUrl();
        String apiKey = settings.getApiKey();

        if (serverUrl == null || serverUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            return EsqlQueryResult.error("No connection configured. Add one in the ES|QL Results tool window.");
        }

        try {
            String url = serverUrl.endsWith("/")
                ? serverUrl + "_query"
                : serverUrl + "/_query";

            String requestBody = OBJECT_MAPPER.writeValueAsString(
                OBJECT_MAPPER.createObjectNode().put("query", query)
            );

            HttpClient httpClient = createHttpClient(serverUrl);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "ApiKey " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                return parseErrorResponse(response.body(), response.statusCode());
            }

            return parseSuccessResponse(response.body());

        } catch (Exception e) {
            return EsqlQueryResult.error("Request failed: " + e.getMessage());
        }
    }

    private static HttpClient createHttpClient(String serverUrl) {
        try {
            if (serverUrl.startsWith("https://")) {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }}, new SecureRandom());
                return HttpClient.newBuilder().sslContext(sslContext).build();
            }
        } catch (Exception ignored) {
        }
        return HttpClient.newHttpClient();
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

    private static EsqlQueryResult parseErrorResponse(String responseBody, int statusCode) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            JsonNode error = root.get("error");
            if (error != null) {
                String reason = error.has("reason") ? error.get("reason").asText() : error.asText();
                String type = error.has("type") ? error.get("type").asText() : "";
                return EsqlQueryResult.error("[" + statusCode + "] " + type + ": " + reason);
            }
        } catch (Exception ignored) {
        }
        return EsqlQueryResult.error("[" + statusCode + "] " + responseBody);
    }
}
