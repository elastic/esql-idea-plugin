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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import java.util.Set;
import java.util.regex.Pattern;

public class RestQueryExecutor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private static final Set<String> HTTP_METHODS = Set.of(
        "GET", "POST", "PUT", "DELETE", "HEAD", "PATCH", "OPTIONS"
    );

    private static final Pattern METHOD_LINE_PATTERN = Pattern.compile(
        "^(GET|POST|PUT|DELETE|HEAD|PATCH|OPTIONS)\\s+\\S+.*$", Pattern.CASE_INSENSITIVE
    );

    public record RestResult(int statusCode, String body, String error) {
        public boolean isError() {
            return error != null && !error.isEmpty();
        }

        public static RestResult error(String message) {
            return new RestResult(-1, null, message);
        }
    }

    public record ParsedRequest(String method, String path, String body) {}

    public record RequestBlock(int startLine, int endLine, String content) {}

    public static boolean isMethodLine(String line) {
        String trimmed = line.strip();
        return !trimmed.isEmpty() && METHOD_LINE_PATTERN.matcher(trimmed).matches();
    }

    /**
     * Splits a multi-request console document into individual request blocks,
     * each starting with a HTTP method line (e.g. {@code GET /path}).
     * Lines starting with {@code #} are treated as comments and skipped.
     */
    public static List<RequestBlock> splitRequests(String input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }

        List<RequestBlock> blocks = new ArrayList<>();
        String[] lines = input.split("\n", -1);
        int currentStart = -1;
        StringBuilder currentContent = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String stripped = lines[i].strip();
            if (stripped.startsWith("#")) continue;

            if (isMethodLine(stripped)) {
                if (currentStart >= 0) {
                    blocks.add(new RequestBlock(currentStart, i - 1, currentContent.toString().stripTrailing()));
                }
                currentStart = i;
                currentContent = new StringBuilder();
            }

            if (currentStart >= 0) {
                currentContent.append(lines[i]).append("\n");
            }
        }

        if (currentStart >= 0) {
            blocks.add(new RequestBlock(currentStart, Math.max(lines.length - 1, currentStart),
                currentContent.toString().stripTrailing()));
        }

        return blocks;
    }

    public static RequestBlock findRequestAtLine(List<RequestBlock> blocks, int line) {
        for (RequestBlock block : blocks) {
            if (line >= block.startLine() && line <= block.endLine()) {
                return block;
            }
        }
        RequestBlock nearest = null;
        for (RequestBlock block : blocks) {
            if (block.startLine() <= line) {
                nearest = block;
            }
        }
        return nearest;
    }

    public static ParsedRequest parseRequest(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        StringBuilder cleaned = new StringBuilder();
        for (String line : input.split("\n")) {
            if (!line.strip().startsWith("#")) {
                cleaned.append(line).append("\n");
            }
        }

        String trimmed = cleaned.toString().strip();
        if (trimmed.isEmpty()) return null;

        int firstNewline = trimmed.indexOf('\n');
        String firstLine;
        String body;

        if (firstNewline < 0) {
            firstLine = trimmed;
            body = null;
        } else {
            firstLine = trimmed.substring(0, firstNewline).strip();
            body = trimmed.substring(firstNewline + 1).strip();
            if (body.isEmpty()) body = null;
        }

        String[] parts = firstLine.split("\\s+", 2);
        if (parts.length < 1) return null;

        String method = parts[0].toUpperCase();
        if (!HTTP_METHODS.contains(method)) return null;

        String path = parts.length > 1 ? parts[1].strip() : "/";

        return new ParsedRequest(method, path, body);
    }

    public static RestResult execute(String method, String path, String body) {
        EsqlPluginQueryManagerImpl queryManager = ApplicationManager.getApplication()
            .getService(EsqlPluginQueryManagerImpl.class);
        if (!queryManager.isActiveConnectionConnected()) {
            return RestResult.error("Not connected. Use the connection controls in the ES|QL Results tab to connect first.");
        }

        EsqlPluginSettings settings = ApplicationManager.getApplication()
            .getService(EsqlPluginSettings.class);

        String serverUrl = settings.getServerUrl();
        String apiKey = settings.getApiKey();

        if (serverUrl == null || serverUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            return RestResult.error("No connection configured. Add one in the ES|QL Results tab.");
        }

        try {
            String baseUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
            String fullPath = path.startsWith("/") ? path : "/" + path;
            String url = baseUrl + fullPath;

            HttpClient httpClient = createHttpClient(serverUrl);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("Authorization", "ApiKey " + apiKey);

            boolean hasBody = body != null && !body.isBlank();

            switch (method.toUpperCase()) {
                case "GET" -> requestBuilder.GET();
                case "DELETE" -> requestBuilder.DELETE();
                case "HEAD" -> requestBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody());
                default -> {
                    if (hasBody) {
                        requestBuilder.header("Content-Type", "application/json");
                        requestBuilder.method(method.toUpperCase(), HttpRequest.BodyPublishers.ofString(body));
                    } else {
                        requestBuilder.method(method.toUpperCase(), HttpRequest.BodyPublishers.noBody());
                    }
                }
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            String responseBody = response.body();
            if (responseBody != null && !responseBody.isEmpty()) {
                try {
                    Object json = OBJECT_MAPPER.readValue(responseBody, Object.class);
                    responseBody = OBJECT_MAPPER.writeValueAsString(json);
                } catch (Exception ignored) {}
            }

            return new RestResult(response.statusCode(), responseBody, null);

        } catch (Exception e) {
            return RestResult.error("Request failed: " + e.getMessage());
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
        } catch (Exception ignored) {}
        return HttpClient.newHttpClient();
    }
}
