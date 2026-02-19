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
package co.elastic.plugin.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RestApiSpecService {

    private static final Logger LOG = Logger.getInstance(RestApiSpecService.class);
    private static final String SPEC_PREFIX = "rest-api-spec/api/";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record PartSpec(String type, String description) {}
    public record ParamSpec(String type, String description, Object defaultValue, List<String> options) {}
    public record UrlPath(String pattern, List<String> methods, Map<String, PartSpec> parts) {}
    public record EndpointSpec(
        String name,
        String description,
        List<UrlPath> paths,
        Map<String, ParamSpec> queryParams,
        boolean hasBody
    ) {}

    private final List<EndpointSpec> endpoints = new ArrayList<>();
    private final Map<String, List<EndpointSpec>> byMethod = new ConcurrentHashMap<>();
    private Map<String, JsonNode> bodySchemas = Collections.emptyMap();

    public RestApiSpecService() {
        loadFromClasspath();
        loadBodySchemas();
    }

    public static RestApiSpecService getInstance() {
        return ApplicationManager.getApplication().getService(RestApiSpecService.class);
    }

    public List<EndpointSpec> getAllEndpoints() {
        return Collections.unmodifiableList(endpoints);
    }

    public List<EndpointSpec> getEndpointsForMethod(String method) {
        return byMethod.getOrDefault(method.toUpperCase(), Collections.emptyList());
    }

    /**
     * Finds endpoints matching the given method and partial/full path.
     * Path segments surrounded by braces (e.g. {index}) are treated as wildcards.
     */
    public List<EndpointSpec> matchEndpoints(String method, String path) {
        List<EndpointSpec> candidates = getEndpointsForMethod(method);
        if (path == null || path.isEmpty() || path.equals("/")) {
            return candidates;
        }
        String[] inputSegments = path.split("/");
        List<EndpointSpec> result = new ArrayList<>();
        for (EndpointSpec ep : candidates) {
            for (UrlPath urlPath : ep.paths()) {
                if (!urlPath.methods().contains(method.toUpperCase())) continue;
                String[] patternSegments = urlPath.pattern().split("/");
                if (matchesPath(inputSegments, patternSegments)) {
                    result.add(ep);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Returns path completions for a partial path given a method.
     * E.g. method=GET, partialPath="/_cat/" -> ["/_cat/indices", "/_cat/health", ...]
     */
    public List<PathCompletion> getPathCompletions(String method, String partialPath) {
        List<EndpointSpec> candidates = getEndpointsForMethod(method);
        Set<String> seen = new LinkedHashSet<>();
        List<PathCompletion> completions = new ArrayList<>();

        String normalizedPartial = partialPath.startsWith("/") ? partialPath : "/" + partialPath;
        String[] partialSegments = normalizedPartial.split("/", -1);

        for (EndpointSpec ep : candidates) {
            for (UrlPath urlPath : ep.paths()) {
                if (!urlPath.methods().contains(method.toUpperCase())) continue;
                String pattern = urlPath.pattern();
                String[] patternSegments = pattern.split("/", -1);

                if (patternSegments.length < partialSegments.length) continue;

                boolean matches = true;
                for (int i = 1; i < partialSegments.length - 1; i++) {
                    if (i >= patternSegments.length) { matches = false; break; }
                    String ps = patternSegments[i];
                    String is = partialSegments[i];
                    if (!ps.startsWith("{") && !ps.equals(is)) { matches = false; break; }
                    if (ps.startsWith("{") && is.isEmpty()) { matches = false; break; }
                }
                if (!matches) continue;

                int completionIdx = partialSegments.length - 1;
                if (completionIdx < patternSegments.length) {
                    String nextSegment = patternSegments[completionIdx];
                    String typedPrefix = partialSegments[completionIdx];

                    if (nextSegment.startsWith("{")) {
                        String paramName = nextSegment.substring(1, nextSegment.length() - 1);
                        String placeholder = "<" + paramName + ">";
                        String key = pattern + ":" + paramName;
                        if (seen.add(key)) {
                            completions.add(new PathCompletion(
                                placeholder, pattern, ep.description(), paramName
                            ));
                        }
                    } else {
                        if (nextSegment.startsWith(typedPrefix)) {
                            if (seen.add(nextSegment)) {
                                String suffix = completionIdx + 1 < patternSegments.length ? "/" : "";
                                completions.add(new PathCompletion(
                                    nextSegment + suffix, pattern, ep.description(), null
                                ));
                            }
                        }
                    }
                }
            }
        }
        return completions;
    }

    public record PathCompletion(String text, String fullPattern, String description, String paramName) {}

    /**
     * Returns query param specs for the best-matching endpoint given method + path.
     */
    public Map<String, ParamSpec> getQueryParams(String method, String path) {
        List<EndpointSpec> matched = matchEndpoints(method, path);
        if (matched.isEmpty()) return Collections.emptyMap();
        return matched.getFirst().queryParams();
    }

    public void setBodySchemas(Map<String, JsonNode> schemas) {
        this.bodySchemas = schemas;
    }

    public Map<String, JsonNode> getBodySchemas() {
        return bodySchemas;
    }

    /**
     * Get body schema for a matched endpoint.
     */
    public JsonNode getBodySchema(String method, String path) {
        List<EndpointSpec> matched = matchEndpoints(method, path);
        if (matched.isEmpty()) return null;
        return bodySchemas.get(matched.getFirst().name());
    }

    private boolean matchesPath(String[] inputSegments, String[] patternSegments) {
        if (inputSegments.length != patternSegments.length) return false;
        for (int i = 0; i < inputSegments.length; i++) {
            String ps = patternSegments[i];
            String is = inputSegments[i];
            if (ps.startsWith("{")) continue;
            if (!ps.equals(is)) return false;
        }
        return true;
    }

    private void loadFromClasspath() {
        try {
            ClassLoader cl = getClass().getClassLoader();
            Enumeration<URL> resources = cl.getResources(SPEC_PREFIX);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                String protocol = url.getProtocol();
                if ("jar".equals(protocol)) {
                    loadFromJar(url);
                }
            }
            if (endpoints.isEmpty()) {
                loadFromJarScan(cl);
            }
        } catch (Exception e) {
            LOG.warn("Failed to load REST API specs from classpath", e);
        }
        buildIndex();
        LOG.info("Loaded " + endpoints.size() + " REST API endpoint specs");
    }

    private void loadFromJar(URL dirUrl) throws Exception {
        String jarPath = dirUrl.getPath();
        int bangIdx = jarPath.indexOf('!');
        if (bangIdx < 0) return;
        String jarFile = jarPath.substring(5, bangIdx);
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith(SPEC_PREFIX) && name.endsWith(".json") && !name.equals(SPEC_PREFIX)) {
                    try (InputStream is = jar.getInputStream(entry)) {
                        parseSpecFile(is);
                    }
                }
            }
        }
    }

    private void loadFromJarScan(ClassLoader cl) {
        try {
            Enumeration<URL> allJars = cl.getResources("rest-api-spec/");
            while (allJars.hasMoreElements()) {
                URL url = allJars.nextElement();
                if ("jar".equals(url.getProtocol())) {
                    loadFromJar(url);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed jar scan for REST API specs", e);
        }
    }

    private void parseSpecFile(InputStream is) {
        try {
            JsonNode root = MAPPER.readTree(is);
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String name = entry.getKey();
                if (name.startsWith("_")) continue; // skip _common.json etc.
                JsonNode spec = entry.getValue();
                parseEndpoint(name, spec);
            }
        } catch (Exception e) {
            LOG.debug("Failed to parse spec file", e);
        }
    }

    private void parseEndpoint(String name, JsonNode spec) {
        String description = "";
        JsonNode docNode = spec.get("documentation");
        if (docNode != null && docNode.has("description")) {
            description = docNode.get("description").asText("");
        }

        List<UrlPath> paths = new ArrayList<>();
        JsonNode urlNode = spec.get("url");
        if (urlNode != null && urlNode.has("paths")) {
            for (JsonNode pathNode : urlNode.get("paths")) {
                String pattern = pathNode.get("path").asText();
                List<String> methods = new ArrayList<>();
                if (pathNode.has("methods")) {
                    for (JsonNode m : pathNode.get("methods")) {
                        methods.add(m.asText().toUpperCase());
                    }
                }
                Map<String, PartSpec> parts = new LinkedHashMap<>();
                if (pathNode.has("parts")) {
                    Iterator<Map.Entry<String, JsonNode>> partFields = pathNode.get("parts").fields();
                    while (partFields.hasNext()) {
                        Map.Entry<String, JsonNode> pe = partFields.next();
                        parts.put(pe.getKey(), new PartSpec(
                            pe.getValue().path("type").asText("string"),
                            pe.getValue().path("description").asText("")
                        ));
                    }
                }
                paths.add(new UrlPath(pattern, methods, parts));
            }
        }

        Map<String, ParamSpec> queryParams = new LinkedHashMap<>();
        JsonNode paramsNode = spec.get("params");
        if (paramsNode != null) {
            Iterator<Map.Entry<String, JsonNode>> paramFields = paramsNode.fields();
            while (paramFields.hasNext()) {
                Map.Entry<String, JsonNode> pe = paramFields.next();
                JsonNode pv = pe.getValue();
                List<String> options = new ArrayList<>();
                if (pv.has("options")) {
                    for (JsonNode opt : pv.get("options")) {
                        options.add(opt.asText());
                    }
                }
                queryParams.put(pe.getKey(), new ParamSpec(
                    pv.path("type").asText("string"),
                    pv.path("description").asText(""),
                    pv.has("default") ? pv.get("default").asText() : null,
                    options
                ));
            }
        }

        boolean hasBody = spec.has("body") && !spec.get("body").isNull();

        endpoints.add(new EndpointSpec(name, description, paths, queryParams, hasBody));
    }

    private void loadBodySchemas() {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("rest-api/body-schemas.json");
            if (is == null) {
                LOG.info("No bundled body-schemas.json found");
                return;
            }
            JsonNode root = MAPPER.readTree(is);
            Map<String, JsonNode> schemas = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                schemas.put(entry.getKey(), entry.getValue());
            }
            this.bodySchemas = schemas;
            LOG.info("Loaded body schemas for " + schemas.size() + " endpoints");
        } catch (Exception e) {
            LOG.warn("Failed to load body schemas", e);
        }
    }

    private void buildIndex() {
        byMethod.clear();
        for (EndpointSpec ep : endpoints) {
            for (UrlPath path : ep.paths()) {
                for (String method : path.methods()) {
                    byMethod.computeIfAbsent(method, k -> new ArrayList<>()).add(ep);
                }
            }
        }
        for (var entry : byMethod.entrySet()) {
            List<EndpointSpec> unique = entry.getValue().stream().distinct().collect(Collectors.toList());
            entry.setValue(unique);
        }
    }
}
