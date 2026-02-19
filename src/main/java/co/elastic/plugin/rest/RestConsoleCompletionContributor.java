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

import co.elastic.plugin.connection.EsqlPluginQueryManagerImpl;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RestConsoleCompletionContributor extends CompletionContributor {

    private static final Pattern METHOD_PATTERN = Pattern.compile(
        "^\\s*(GET|POST|PUT|DELETE|HEAD|PATCH|OPTIONS)\\s", Pattern.CASE_INSENSITIVE
    );
    private static final List<String> HTTP_METHODS = List.of(
        "GET", "POST", "PUT", "DELETE", "HEAD", "PATCH", "OPTIONS"
    );

    public RestConsoleCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new RestCompletionProvider());
    }

    enum Zone { METHOD, PATH, QUERY_PARAM, BODY }

    record RequestContext(
        Zone zone,
        String method,
        String fullPath,
        String partialPath,
        String queryPrefix,
        Map<String, String> existingParams,
        String bodyText,
        int bodyOffset
    ) {}

    private static class RestCompletionProvider extends CompletionProvider<CompletionParameters> {

        @Override
        protected void addCompletions(
            @NotNull CompletionParameters parameters,
            @NotNull ProcessingContext context,
            @NotNull CompletionResultSet result
        ) {
            Document doc = parameters.getEditor().getDocument();
            int offset = parameters.getOffset();
            String text = doc.getText();

            int lineNum = doc.getLineNumber(offset);
            int lineStart = doc.getLineStartOffset(lineNum);
            String textBeforeCaret = text.substring(lineStart, offset);

            RequestContext ctx = analyzeContext(text, lineNum, lineStart, offset, textBeforeCaret);
            if (ctx == null) return;

            switch (ctx.zone) {
                case METHOD -> addMethodCompletions(result, textBeforeCaret.trim());
                case PATH -> addPathCompletions(result, ctx);
                case QUERY_PARAM -> addQueryParamCompletions(result, ctx);
                case BODY -> addBodyCompletions(result, ctx);
            }
        }

        private RequestContext analyzeContext(String text, int lineNum, int lineStart, int offset, String textBeforeCaret) {
            String trimmed = textBeforeCaret.trim();

            Matcher methodMatcher = METHOD_PATTERN.matcher(textBeforeCaret);
            boolean lineHasMethod = methodMatcher.find();

            if (!lineHasMethod) {
                boolean isPartialMethod = HTTP_METHODS.stream()
                    .anyMatch(m -> m.startsWith(trimmed.toUpperCase()) && !trimmed.isEmpty());
                boolean lineIsEmpty = trimmed.isEmpty();

                if (lineIsEmpty || isPartialMethod) {
                    if (isOnMethodLine(text, lineNum)) {
                        return new RequestContext(Zone.METHOD, null, null, null, null, null, null, 0);
                    }
                }

                MethodPathInfo info = findMethodPathForBodyLine(text, lineNum);
                if (info != null) {
                    String bodyText = extractBody(text, info.bodyStartLine, lineNum, offset);
                    int bodyOffset = bodyText.length();
                    return new RequestContext(
                        Zone.BODY, info.method, info.path, null, null, null, bodyText, bodyOffset
                    );
                }
                return null;
            }

            String method = methodMatcher.group(1).toUpperCase();
            String afterMethod = textBeforeCaret.substring(methodMatcher.end());

            if (afterMethod.contains("?")) {
                int qIdx = afterMethod.indexOf('?');
                String pathPart = afterMethod.substring(0, qIdx).trim();
                String queryPart = afterMethod.substring(qIdx + 1);
                String lastParam = queryPart.contains("&")
                    ? queryPart.substring(queryPart.lastIndexOf('&') + 1)
                    : queryPart;

                Map<String, String> existingParams = parseQueryParams(queryPart);
                return new RequestContext(
                    Zone.QUERY_PARAM, method, pathPart, null, lastParam, existingParams, null, 0
                );
            }

            String partialPath = afterMethod.trim();
            if (!partialPath.startsWith("/")) {
                partialPath = "/" + partialPath;
            }
            return new RequestContext(Zone.PATH, method, partialPath, partialPath, null, null, null, 0);
        }

        private boolean isOnMethodLine(String text, int lineNum) {
            String[] lines = text.split("\n", -1);
            for (int i = lineNum - 1; i >= 0; i--) {
                String line = lines[i].trim();
                if (line.isEmpty()) return true;
                if (METHOD_PATTERN.matcher(line).find()) return true;
                if (line.startsWith("#")) continue;
                return false;
            }
            return true;
        }

        record MethodPathInfo(String method, String path, int bodyStartLine) {}

        private MethodPathInfo findMethodPathForBodyLine(String text, int lineNum) {
            String[] lines = text.split("\n", -1);
            for (int i = lineNum - 1; i >= 0; i--) {
                String line = lines[i].trim();
                if (line.isEmpty()) return null;
                Matcher m = METHOD_PATTERN.matcher(line);
                if (m.find()) {
                    String method = m.group(1).toUpperCase();
                    String pathAndQuery = line.substring(m.end()).trim();
                    String path = pathAndQuery.contains("?")
                        ? pathAndQuery.substring(0, pathAndQuery.indexOf('?'))
                        : pathAndQuery;
                    return new MethodPathInfo(method, path, i + 1);
                }
            }
            return null;
        }

        private String extractBody(String text, int bodyStartLine, int currentLine, int currentOffset) {
            String[] lines = text.split("\n", -1);
            StringBuilder body = new StringBuilder();
            int charOffset = 0;
            for (int i = 0; i < bodyStartLine; i++) {
                charOffset += lines[i].length() + 1;
            }
            for (int i = bodyStartLine; i <= currentLine && i < lines.length; i++) {
                if (i == currentLine) {
                    int lineStart = charOffset;
                    int offsetInLine = currentOffset - lineStart;
                    if (offsetInLine >= 0 && offsetInLine <= lines[i].length()) {
                        body.append(lines[i], 0, offsetInLine);
                    }
                } else {
                    body.append(lines[i]).append("\n");
                }
                charOffset += lines[i].length() + 1;
            }
            return body.toString();
        }

        private Map<String, String> parseQueryParams(String queryString) {
            Map<String, String> params = new LinkedHashMap<>();
            if (queryString == null || queryString.isEmpty()) return params;
            for (String part : queryString.split("&")) {
                int eq = part.indexOf('=');
                if (eq >= 0) {
                    params.put(part.substring(0, eq), part.substring(eq + 1));
                } else if (!part.isEmpty()) {
                    params.put(part, "");
                }
            }
            return params;
        }

        private void addMethodCompletions(CompletionResultSet result, String prefix) {
            for (String method : HTTP_METHODS) {
                LookupElementBuilder element = LookupElementBuilder.create(method)
                    .withIcon(AllIcons.Nodes.Method)
                    .withTypeText("HTTP method")
                    .withInsertHandler((ctx, item) -> {
                        ctx.getEditor().getDocument().insertString(ctx.getTailOffset(), " ");
                        ctx.getEditor().getCaretModel().moveToOffset(ctx.getTailOffset() + 1);
                    })
                    .bold();
                result.addElement(PrioritizedLookupElement.withPriority(element, 100));
            }
        }

        private static final Set<String> INDEX_PARAM_NAMES = Set.of(
            "index", "target", "name", "alias"
        );

        private void addPathCompletions(CompletionResultSet result, RequestContext ctx) {
            RestApiSpecService specService = RestApiSpecService.getInstance();
            String partialPath = ctx.partialPath != null ? ctx.partialPath : "/";

            int lastSlash = partialPath.lastIndexOf('/');
            String prefix = lastSlash >= 0 ? partialPath.substring(lastSlash + 1) : partialPath;

            CompletionResultSet pathResult = result.withPrefixMatcher(
                new PlainPrefixMatcher(prefix)
            );

            List<RestApiSpecService.PathCompletion> completions =
                specService.getPathCompletions(ctx.method, partialPath);

            boolean liveIndicesAdded = false;
            for (RestApiSpecService.PathCompletion pc : completions) {
                String lookupString = pc.text();
                LookupElementBuilder element = LookupElementBuilder.create(lookupString)
                    .withIcon(AllIcons.Nodes.WebFolder)
                    .withTypeText(truncate(pc.description(), 60), true)
                    .withTailText("  " + pc.fullPattern(), true);

                if (pc.paramName() != null) {
                    element = element.withTypeText("{" + pc.paramName() + "}", true);

                    if (!liveIndicesAdded && INDEX_PARAM_NAMES.contains(pc.paramName())) {
                        liveIndicesAdded = true;
                        addLiveIndexCompletions(pathResult, prefix);
                    }
                }

                pathResult.addElement(PrioritizedLookupElement.withPriority(element, 50));
            }
        }

        private void addLiveIndexCompletions(CompletionResultSet result, String prefix) {
            try {
                EsqlPluginQueryManagerImpl queryManager =
                    ApplicationManager.getApplication().getService(EsqlPluginQueryManagerImpl.class);
                if (queryManager == null) return;

                List<String> indices = queryManager.getIndices();
                for (String indexName : indices) {
                    if (!indexName.startsWith(prefix) && !prefix.isEmpty()) continue;
                    LookupElementBuilder element = LookupElementBuilder.create(indexName)
                        .withIcon(AllIcons.Nodes.DataTables)
                        .withTypeText("live index", true)
                        .bold();
                    result.addElement(PrioritizedLookupElement.withPriority(element, 60));
                }
            } catch (Exception ignored) {}
        }

        private void addQueryParamCompletions(CompletionResultSet result, RequestContext ctx) {
            RestApiSpecService specService = RestApiSpecService.getInstance();
            Map<String, RestApiSpecService.ParamSpec> params =
                specService.getQueryParams(ctx.method, ctx.fullPath);

            String prefix = ctx.queryPrefix != null ? ctx.queryPrefix : "";
            boolean isAfterEquals = prefix.contains("=");

            if (isAfterEquals) {
                int eqIdx = prefix.indexOf('=');
                String paramName = prefix.substring(0, eqIdx);
                String valuePrefix = prefix.substring(eqIdx + 1);

                RestApiSpecService.ParamSpec spec = params.get(paramName);
                if (spec != null && spec.options() != null && !spec.options().isEmpty()) {
                    CompletionResultSet valueResult = result.withPrefixMatcher(
                        new PlainPrefixMatcher(valuePrefix)
                    );
                    for (String option : spec.options()) {
                        valueResult.addElement(LookupElementBuilder.create(option)
                            .withIcon(AllIcons.Nodes.Enum)
                            .withTypeText(paramName + " value"));
                    }
                }
                if (spec != null && "boolean".equals(spec.type())) {
                    CompletionResultSet valueResult = result.withPrefixMatcher(
                        new PlainPrefixMatcher(valuePrefix)
                    );
                    valueResult.addElement(LookupElementBuilder.create("true").withIcon(AllIcons.Nodes.Enum));
                    valueResult.addElement(LookupElementBuilder.create("false").withIcon(AllIcons.Nodes.Enum));
                }
                return;
            }

            CompletionResultSet paramResult = result.withPrefixMatcher(
                new PlainPrefixMatcher(prefix)
            );

            Set<String> existing = ctx.existingParams != null ? ctx.existingParams.keySet() : Set.of();
            for (var entry : params.entrySet()) {
                if (existing.contains(entry.getKey())) continue;
                RestApiSpecService.ParamSpec spec = entry.getValue();
                LookupElementBuilder element = LookupElementBuilder.create(entry.getKey())
                    .withIcon(AllIcons.Nodes.Parameter)
                    .withTypeText(spec.type(), true)
                    .withTailText("  " + truncate(spec.description(), 50), true)
                    .withInsertHandler((ctx2, item) -> {
                        ctx2.getEditor().getDocument().insertString(ctx2.getTailOffset(), "=");
                        ctx2.getEditor().getCaretModel().moveToOffset(ctx2.getTailOffset() + 1);
                    });
                paramResult.addElement(PrioritizedLookupElement.withPriority(element, 40));
            }
        }

        private static final Set<String> FIELD_NAME_QUERY_TYPES = Set.of(
            "match", "match_phrase", "match_phrase_prefix", "match_bool_prefix",
            "term", "terms", "range", "prefix", "wildcard", "regexp", "fuzzy",
            "exists", "geo_distance", "geo_bounding_box", "geo_shape",
            "nested", "has_child", "has_parent"
        );

        private void addBodyCompletions(CompletionResultSet result, RequestContext ctx) {
            RestApiSpecService specService = RestApiSpecService.getInstance();

            List<String> jsonPath = resolveJsonPath(ctx.bodyText);
            String prefix = extractBodyPrefix(ctx.bodyText);
            CompletionResultSet bodyResult = result.withPrefixMatcher(
                new PlainPrefixMatcher(prefix)
            );
            Set<String> existingKeys = findExistingKeysAtLevel(ctx.bodyText);

            if (!jsonPath.isEmpty() && FIELD_NAME_QUERY_TYPES.contains(jsonPath.getLast())) {
                addLiveFieldCompletions(bodyResult, ctx.fullPath, existingKeys, prefix);
            }

            JsonNode schema = specService.getBodySchema(ctx.method, ctx.fullPath);
            if (schema == null) return;

            JsonNode currentLevel = navigateSchema(schema, jsonPath);
            if (currentLevel == null) return;

            Iterator<Map.Entry<String, JsonNode>> fields = currentLevel.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                if (existingKeys.contains(key)) continue;

                JsonNode fieldSchema = field.getValue();
                String type = fieldSchema.has("type") ? fieldSchema.get("type").asText() : "object";
                String desc = fieldSchema.has("description") ? fieldSchema.get("description").asText() : "";

                LookupElementBuilder element = LookupElementBuilder.create("\"" + key + "\"")
                    .withPresentableText(key)
                    .withIcon(AllIcons.Nodes.Field)
                    .withTypeText(type, true)
                    .withTailText(desc.isEmpty() ? "" : "  " + truncate(desc, 50), true)
                    .withInsertHandler((ctx2, item) -> {
                        String suffix = "object".equals(type) ? ": {}" : ": ";
                        ctx2.getEditor().getDocument().insertString(ctx2.getTailOffset(), suffix);
                        int moveOffset = "object".equals(type)
                            ? ctx2.getTailOffset() + suffix.length() - 1
                            : ctx2.getTailOffset() + suffix.length();
                        ctx2.getEditor().getCaretModel().moveToOffset(moveOffset);
                    });
                bodyResult.addElement(PrioritizedLookupElement.withPriority(element, 30));
            }
        }

        private void addLiveFieldCompletions(CompletionResultSet result, String path, Set<String> existingKeys, String prefix) {
            try {
                EsqlPluginQueryManagerImpl queryManager =
                    ApplicationManager.getApplication().getService(EsqlPluginQueryManagerImpl.class);
                if (queryManager == null) return;

                String indexName = extractIndexFromPath(path);
                if (indexName == null || indexName.isEmpty()) return;

                List<String> fields = queryManager.getFields(indexName);
                for (String fieldName : fields) {
                    if (existingKeys.contains(fieldName)) continue;
                    String lookup = "\"" + fieldName + "\"";
                    LookupElementBuilder element = LookupElementBuilder.create(lookup)
                        .withPresentableText(fieldName)
                        .withIcon(AllIcons.Nodes.DataColumn)
                        .withTypeText("field (" + indexName + ")", true)
                        .withInsertHandler((ctx2, item) -> {
                            ctx2.getEditor().getDocument().insertString(ctx2.getTailOffset(), ": ");
                            ctx2.getEditor().getCaretModel().moveToOffset(ctx2.getTailOffset() + 2);
                        })
                        .bold();
                    result.addElement(PrioritizedLookupElement.withPriority(element, 35));
                }
            } catch (Exception ignored) {}
        }

        private String extractIndexFromPath(String path) {
            if (path == null || path.isEmpty()) return null;
            String[] segments = path.split("/");
            if (segments.length >= 2 && !segments[1].startsWith("_")) {
                return segments[1];
            }
            return null;
        }

        /**
         * Walks the body text tracking open/close braces to determine the JSON object path.
         */
        private List<String> resolveJsonPath(String bodyText) {
            List<String> path = new ArrayList<>();
            if (bodyText == null) return path;

            Deque<String> stack = new ArrayDeque<>();
            String lastKey = null;
            boolean inString = false;
            boolean escaped = false;

            for (int i = 0; i < bodyText.length(); i++) {
                char c = bodyText.charAt(i);
                if (escaped) { escaped = false; continue; }
                if (c == '\\') { escaped = true; continue; }
                if (c == '"') {
                    if (!inString) {
                        int end = bodyText.indexOf('"', i + 1);
                        if (end > i) {
                            lastKey = bodyText.substring(i + 1, end);
                            i = end;
                        }
                    }
                    inString = !inString;
                    continue;
                }
                if (inString) continue;
                if (c == '{') {
                    if (lastKey != null) {
                        stack.push(lastKey);
                        lastKey = null;
                    }
                } else if (c == '}') {
                    if (!stack.isEmpty()) {
                        stack.pop();
                    }
                    lastKey = null;
                } else if (c == ':') {
                    // lastKey holds the key before the colon
                }
            }

            return new ArrayList<>(stack.reversed());
        }

        private JsonNode navigateSchema(JsonNode schema, List<String> path) {
            JsonNode current = schema;
            for (String key : path) {
                if (current.has(key)) {
                    JsonNode child = current.get(key);
                    if (child.has("properties")) {
                        current = child.get("properties");
                    } else if (child.isObject()) {
                        current = child;
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            }
            return current;
        }

        private String extractBodyPrefix(String bodyText) {
            if (bodyText == null || bodyText.isEmpty()) return "";
            int lastQuote = bodyText.lastIndexOf('"');
            if (lastQuote >= 0) {
                int prevQuote = bodyText.lastIndexOf('"', lastQuote - 1);
                if (prevQuote >= 0) {
                    String between = bodyText.substring(prevQuote + 1, lastQuote);
                    boolean isAfterColon = false;
                    for (int i = lastQuote + 1; i < bodyText.length(); i++) {
                        char c = bodyText.charAt(i);
                        if (c == ':') { isAfterColon = true; break; }
                        if (!Character.isWhitespace(c)) break;
                    }
                    if (!isAfterColon) {
                        return "\"" + between + "\"";
                    }
                }
            }
            return "";
        }

        private Set<String> findExistingKeysAtLevel(String bodyText) {
            Set<String> keys = new LinkedHashSet<>();
            if (bodyText == null) return keys;

            int depth = 0;
            int targetDepth = -1;
            boolean inString = false;
            boolean escaped = false;

            for (int i = 0; i < bodyText.length(); i++) {
                char c = bodyText.charAt(i);
                if (escaped) { escaped = false; continue; }
                if (c == '\\') { escaped = true; continue; }
                if (c == '"') { inString = !inString; continue; }
                if (inString) continue;
                if (c == '{') depth++;
                if (c == '}') depth--;
            }
            targetDepth = depth;

            depth = 0;
            inString = false;
            escaped = false;
            for (int i = 0; i < bodyText.length(); i++) {
                char c = bodyText.charAt(i);
                if (escaped) { escaped = false; continue; }
                if (c == '\\') { escaped = true; continue; }
                if (c == '"') {
                    if (!inString && depth == targetDepth) {
                        int end = bodyText.indexOf('"', i + 1);
                        if (end > i) {
                            int afterEnd = end + 1;
                            while (afterEnd < bodyText.length() && Character.isWhitespace(bodyText.charAt(afterEnd))) {
                                afterEnd++;
                            }
                            if (afterEnd < bodyText.length() && bodyText.charAt(afterEnd) == ':') {
                                keys.add(bodyText.substring(i + 1, end));
                            }
                            i = end;
                        }
                    }
                    inString = !inString;
                    continue;
                }
                if (inString) continue;
                if (c == '{') depth++;
                if (c == '}') depth--;
            }

            return keys;
        }

        private String truncate(String s, int maxLen) {
            if (s == null) return "";
            return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
        }
    }
}
