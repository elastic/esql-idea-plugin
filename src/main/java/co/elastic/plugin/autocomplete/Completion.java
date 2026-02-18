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
package co.elastic.plugin.autocomplete;

import co.elastic.grammar.EsqlBaseParser;
import co.elastic.grammar.completion.CodeCompletionCore;
import co.elastic.grammar.completion.CodeCompletionCore.CandidatesCollection;
import co.elastic.plugin.connection.EsqlPluginQueryManager;

import java.util.*;

import static co.elastic.plugin.CommonUtils.*;

public record Completion(String text, Kind kind) {
    public enum Kind {
        KEYWORD,
        FUNCTION,
        PIPE,
        PLACEHOLDER,
        METADATA,
        NAME,
        FIELD,
    }

    static Set<Completion> computeCompletions(String text, EsqlPluginQueryManager queryManager) {
        var parserInfo = Parser.parse(text);
        return computeCompletions(parserInfo, queryManager);
    }

    static Set<Completion> computeCompletions(Parser.ParserInfo parserInfo, EsqlPluginQueryManager queryManager) {
        var candidates = completionCandidates(parserInfo);
        Set<Completion> completions = new HashSet<>();
        completions.addAll(computeTokenCompletions(candidates));
        completions.addAll(computeSchemaDependentCompletions(parserInfo, candidates, queryManager));
        return completions;
    }

    private static Set<Completion> computeTokenCompletions(CandidatesCollection candidates) {
        Set<Completion> completions = new HashSet<>();

        if (candidates.rules.containsKey(EsqlBaseParser.RULE_indexPattern)) {
            completions.add(new Completion("{string}", Kind.PLACEHOLDER));
        }

        if (candidates.rules.containsKey(EsqlBaseParser.RULE_qualifiedName)) {
            completions.add(new Completion("{var}", Kind.PLACEHOLDER));
        }

        if (candidates.rules.containsKey(EsqlBaseParser.RULE_metadataSource)) {
            completions.add(new Completion("METADATA", Kind.KEYWORD));
            for (String opt : METADATA_OPTIONS) {
                completions.add(new Completion(opt, Kind.METADATA));
            }
        }

        Set<Integer> tokenCompletions = candidates.tokens.keySet();

        for (Integer tokenType : tokenCompletions) {
            switch (tokenType) {
                // replacing QUOTED_STRING and UNQUOTED_SOURCE with just "{string}"
                case EsqlBaseParser.QUOTED_STRING:
                case EsqlBaseParser.UNQUOTED_SOURCE:
                    completions.add(new Completion("{string}", Kind.PLACEHOLDER));
                    break;
                // replacing UNQUOTED_IDENTIFIER and QUOTED_IDENTIFIER with just {var}
                case EsqlBaseParser.UNQUOTED_IDENTIFIER:
                case EsqlBaseParser.QUOTED_IDENTIFIER:
                    completions.add(new Completion("{var}", Kind.PLACEHOLDER));
                    break;
                // replacing NAMED_OR_POSITIONAL_PARAM, NAMED_OR_POSITIONAL_DOUBLE_PARAMS and ID_PATTERN
                // with just {param}
                case EsqlBaseParser.NAMED_OR_POSITIONAL_PARAM:
                case EsqlBaseParser.NAMED_OR_POSITIONAL_DOUBLE_PARAMS:
                case EsqlBaseParser.ID_PATTERN:
                    completions.add(new Completion("{param}", Kind.PLACEHOLDER));
                    break;
                // replacing DECIMAL_LITERAL with just {num}
                case EsqlBaseParser.DECIMAL_LITERAL:
                    completions.add(new Completion("{num}", Kind.PLACEHOLDER));
                    break;
                // LP means functions, adding brackets to token
                case EsqlBaseParser.LP:
                    for (String function : FUNCTIONS) {
                        completions.add(new Completion(function + "()", Kind.FUNCTION));
                    }
                    break;
                case EsqlBaseParser.PIPE:
                    completions.add(new Completion("|", Kind.PIPE));
                    break;
                case EsqlBaseParser.PARAM:
                case EsqlBaseParser.DOUBLE_PARAMS:
                case EsqlBaseParser.EOF:
                    break;
                default:
                    String display = EsqlBaseParser.VOCABULARY.getDisplayName(tokenType);
                    if (display != null && !display.isEmpty() && !display.startsWith("DEV_")) {
                        String tokenText = display.replaceAll("'", "").toUpperCase(Locale.ROOT);
                        completions.add(new Completion(tokenText, Kind.KEYWORD));
                    }
            }
        }

        return completions;
    }

    private static Set<Completion> computeSchemaDependentCompletions(Parser.ParserInfo parserInfo,
                                                                      CandidatesCollection candidates,
                                                                      EsqlPluginQueryManager queryManager) {
        Set<Completion> completions = new HashSet<>();
        if (queryManager == null) {
            return completions;
        }

        if (candidates.rules.containsKey(EsqlBaseParser.RULE_indexPattern)) {
            for (String index : queryManager.getIndices()) {
                completions.add(new Completion(index, Kind.NAME));
            }
        }

        if (candidates.rules.containsKey(EsqlBaseParser.RULE_fieldName)) {
            var queriedIndexes = parserInfo.queriedIndexes();
            if (!queriedIndexes.isEmpty()) {
                String index = queriedIndexes.getLast();
                for (String field : queryManager.getFields(index)) {
                    completions.add(new Completion(field, Kind.FIELD));
                }
            }
        }

        return completions;
    }

    private static CandidatesCollection completionCandidates(Parser.ParserInfo parserInfo) {
        var parser = parserInfo.parser();
        var tokens = parserInfo.tokens();

        var codeCompletionCode = new CodeCompletionCore(parser);
        codeCompletionCode.preferredRules.add(EsqlBaseParser.RULE_indexPattern);
        codeCompletionCode.preferredRules.add(EsqlBaseParser.RULE_fieldName);
        codeCompletionCode.preferredRules.add(EsqlBaseParser.RULE_metadataSource);
        var caretIndex = tokens.size() - 1;
        return codeCompletionCode.collectCandidates(caretIndex, null);
    }
}
