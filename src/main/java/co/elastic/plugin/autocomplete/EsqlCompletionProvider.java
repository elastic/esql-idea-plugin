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
import co.elastic.plugin.connection.EsqlPluginQueryManager;
import co.elastic.plugin.settings.EsqlPluginSettings;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.JBColor;
import com.intellij.util.ProcessingContext;
import org.antlr.v4.runtime.Token;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static co.elastic.plugin.CommonUtils.FUNCTIONS;
import static co.elastic.plugin.CommonUtils.METADATA_OPTIONS;
import static co.elastic.plugin.CommonUtils.checkEsqlCommentAbove;

public class EsqlCompletionProvider extends CompletionProvider<CompletionParameters> {

    EsqlPluginQueryManager queryManager =
        ApplicationManager.getApplication().getService(EsqlPluginQueryManager.class);

    enum ServerOperation {
        indices,
        fields
    }

    static final Map<Integer, ServerOperation> serverOperationsMap =
        Map.ofEntries(
            Map.entry(EsqlBaseParser.FROM, ServerOperation.indices),
            Map.entry(EsqlBaseParser.SORT, ServerOperation.fields),
            Map.entry(EsqlBaseParser.EVAL, ServerOperation.fields),
            Map.entry(EsqlBaseParser.WHERE, ServerOperation.fields)
        );

    static List<Completion> computeCompletions(String text, EsqlPluginQueryManager queryManager) {
        Parser.ParserInfo parserInfo = Parser.parse(text);
        List<Completion> completions = new ArrayList<>();
        completions.addAll(computeTokenCompletions(text, parserInfo));
        completions.addAll(computeSchemaDependentCompletions(parserInfo, queryManager));
        return completions;
    }

    private static List<Completion> computeTokenCompletions(String text, Parser.ParserInfo parserInfo) {
        List<Completion> completions = new ArrayList<>();

        Token lastToken = parserInfo.lastToken();
        if (lastToken != null && lastToken.getType() == EsqlBaseParser.METADATA) {
            for (String opt : METADATA_OPTIONS) {
                completions.add(new Completion(opt, Completion.Kind.KEYWORD));
            }
        }

        Set<Integer> tokenCompletions = CompletionCore.completions(parserInfo).getTokens().keySet();

        for (Integer tokenType : tokenCompletions) {
            switch (tokenType) {
                case EsqlBaseParser.QUOTED_STRING:
                case EsqlBaseParser.UNQUOTED_SOURCE:
                    completions.add(new Completion("{string}", Completion.Kind.PLACEHOLDER));
                    break;
                case EsqlBaseParser.UNQUOTED_IDENTIFIER:
                case EsqlBaseParser.QUOTED_IDENTIFIER:
                    completions.add(new Completion("{var}", Completion.Kind.PLACEHOLDER));
                    break;
                case EsqlBaseParser.NAMED_OR_POSITIONAL_PARAM:
                case EsqlBaseParser.NAMED_OR_POSITIONAL_DOUBLE_PARAMS:
                case EsqlBaseParser.ID_PATTERN:
                    completions.add(new Completion("{param}", Completion.Kind.PLACEHOLDER));
                    break;
                case EsqlBaseParser.DECIMAL_LITERAL:
                    completions.add(new Completion("{num}", Completion.Kind.PLACEHOLDER));
                    break;
                case EsqlBaseParser.LP:
                    for (String function : FUNCTIONS) {
                        completions.add(new Completion(function + "()", Completion.Kind.FUNCTION));
                    }
                    break;
                case EsqlBaseParser.PIPE:
                    completions.add(new Completion("|", Completion.Kind.PIPE));
                    break;
                default:
                    String display = EsqlBaseParser.VOCABULARY.getDisplayName(tokenType);
                    if (display != null && !display.isEmpty() && !display.contains("DEV_")) {
                        String tokenText = display.replaceAll("'", "").toUpperCase(Locale.ROOT);
                        if (!tokenText.equals("EOF") && !tokenText.equals("??") && !tokenText.equals("?")) {
                            completions.add(new Completion(tokenText, Completion.Kind.KEYWORD));
                        }
                    }
            }
        }

        return completions;
    }

    private static List<Completion> computeSchemaDependentCompletions(Parser.ParserInfo parserInfo,
                                                                      EsqlPluginQueryManager queryManager) {
        List<Completion> completions = new ArrayList<>();
        if (queryManager == null) {
            return completions;
        }

        Token lastToken = parserInfo.lastToken();
        if (lastToken == null) {
            return completions;
        }

        ServerOperation serverOp = serverOperationsMap.get(lastToken.getType());
        if (serverOp == null) {
            return completions;
        }

        switch (serverOp) {
            case indices: {
                for (String index : queryManager.getIndices()) {
                    completions.add(new Completion(index, Completion.Kind.KEYWORD));
                }
                break;
            }
            case fields: {
                String index = findIndexFromTokens(parserInfo.tokens());
                if (!index.isEmpty()) {
                    for (String field : queryManager.getFields(index)) {
                        completions.add(new Completion(field, Completion.Kind.KEYWORD));
                    }
                }
                break;
            }
        }

        return completions;
    }

    static String findIndexFromTokens(List<Token> tokens) {
        for (int i = 0; i < tokens.size() - 1; i++) {
            if (tokens.get(i).getType() == EsqlBaseParser.FROM) {
                for (int j = i + 1; j < tokens.size(); j++) {
                    Token next = tokens.get(j);
                    if (next.getType() != Token.EOF && next.getChannel() == Token.DEFAULT_CHANNEL) {
                        return next.getText().trim();
                    }
                }
            }
        }
        return "";
    }

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {

        var elementAtOffset = parameters.getOriginalPosition();
        String text = elementAtOffset.getText();

        if (elementAtOffset.getLanguage().is(Language.findLanguageByID("JAVA"))) {
            text = text.substring(3, text.length() - 3);
        }
        text = text.trim();

        if (!checkEsqlCommentAbove(elementAtOffset)) {
            return;
        }

        var completions = computeCompletions(text, queryManager);
        for (Completion c : completions) {
            int priority = switch (c.kind()) {
                case PIPE -> 6;
                default -> 5;
            };
            result.withPrefixMatcher(new PermissivePrefixMatcher(text))
                .addElement(PrioritizedLookupElement.withPriority(
                    LookupElementBuilder.create(c.text()), priority));
        }
    }

    private static void insertLookupWithColor(@NotNull CompletionResultSet result, String token) {
        LookupElement lookup = LookupElementBuilder.create(token);
        result.withPrefixMatcher(new PermissivePrefixMatcher())
            .addElement(PrioritizedLookupElement
                .withPriority(LookupElementDecorator.withRenderer(lookup, new LookupElementRenderer<>() {
                    public void renderElement(LookupElementDecorator<LookupElement> element,
                                              LookupElementPresentation presentation) {
                        element.getDelegate().renderElement(presentation);
                        presentation.setItemTextForeground(JBColor.YELLOW);
                    }
                }), 10));
    }
}

