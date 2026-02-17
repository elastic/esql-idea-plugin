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

import co.elastic.grammar.EsqlBaseLexer;
import co.elastic.grammar.EsqlBaseParser;
import co.elastic.grammar.completion.CodeCompletionCore;
import co.elastic.grammar.completion.CodeCompletionCore.CandidatesCollection;
import co.elastic.plugin.connection.EsqlPluginQueryManager;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.*;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.JBColor;
import com.intellij.util.ProcessingContext;
import org.antlr.v4.runtime.Token;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static co.elastic.plugin.CommonUtils.*;

public class EsqlCompletionProvider extends CompletionProvider<CompletionParameters> {

    private final EsqlPluginQueryManager queryManager =
        ApplicationManager.getApplication().getService(EsqlPluginQueryManager.class);

    private enum ServerOperation {
        indices,
        fields
    }

    private static Parser.ParserInfo parserInfo;

    private static final Map<Integer, ServerOperation> serverOperationsMap =
        Map.ofEntries(
            Map.entry(EsqlBaseParser.FROM, ServerOperation.indices),
            Map.entry(EsqlBaseParser.SORT, ServerOperation.fields),
            Map.entry(EsqlBaseParser.EVAL, ServerOperation.fields),
            Map.entry(EsqlBaseParser.WHERE, ServerOperation.fields)
        );

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
        @NotNull ProcessingContext context,
        @NotNull CompletionResultSet result) {

        var elementAtOffset = parameters.getOriginalPosition();
        String text = elementAtOffset.getText();
        int caretOffset = parameters.getOffset();
        int elementStart = elementAtOffset.getTextRange().getStartOffset();

        // Get the text until the cursor
        int relativeOffset = caretOffset - elementStart;
        text = text.substring(0, relativeOffset);
        // removing triple quotes if java
        if (elementAtOffset.getLanguage().is(Language.findLanguageByID("JAVA"))) {
            // skip opening """ (3 chars) and spaces
            text = text.substring(3);
        }

        if (!checkEsqlCommentAbove(elementAtOffset)) {
            return;
        }

        var completions = computeCompletions(text, queryManager);
        var lastToken = parserInfo.tokens().getLast();
        for (Completion c : completions) {
            int priority = switch (c.kind()) {
                case PIPE -> 6;
                case METADATA, NAME, FIELD -> 10;
                default -> 5;
            };

            switch (c.kind()) {
                case METADATA, NAME, FIELD:
                    LookupElement lookup = LookupElementBuilder.create(lastToken.getText());
                    result.withPrefixMatcher(new PermissivePrefixMatcher())
                        .addElement(PrioritizedLookupElement
                            .withPriority(LookupElementDecorator.withRenderer(lookup, new LookupElementRenderer<>() {
                                public void renderElement(LookupElementDecorator<LookupElement> element,
                                    LookupElementPresentation presentation) {
                                    element.getDelegate().renderElement(presentation);
                                    presentation.setItemTextForeground(JBColor.YELLOW);
                                }
                        }), priority));
                    break;
                default:
                    // If the last token is not a space (spaces are in a hidden channel), use it on matching to replace the current word
                    var matcher = lastToken.getChannel() == EsqlBaseLexer.DEFAULT_TOKEN_CHANNEL ? new PermissivePrefixMatcher(lastToken.getText()) : new PermissivePrefixMatcher();
                    result.withPrefixMatcher(matcher).addElement(PrioritizedLookupElement.withPriority(
                        LookupElementBuilder.create(c.text()),
                        priority)
                    );

            }
        }
    }

    static Set<Completion> computeCompletions(String text, EsqlPluginQueryManager queryManager) {
        parserInfo = Parser.parse(text);
        Set<Completion> completions = new HashSet<>();
        completions.addAll(computeTokenCompletions(parserInfo));
        completions.addAll(computeSchemaDependentCompletions(parserInfo, queryManager));
        return completions;
    }

    private static Set<Completion> computeTokenCompletions(Parser.ParserInfo parserInfo) {
        Set<Completion> completions = new HashSet<>();

        Token lastNonSpaceToken = parserInfo.lastNonSpacetoken();

        // metadata special case
        if (lastNonSpaceToken != null && lastNonSpaceToken.getType() == EsqlBaseParser.METADATA) {
            for (String opt : METADATA_OPTIONS) {
                completions.add(new Completion(opt, Completion.Kind.METADATA));
            }
        }

        Set<Integer> tokenCompletions = completionCandidates(parserInfo).tokens.keySet();

        for (Integer tokenType : tokenCompletions) {
            switch (tokenType) {
                // replacing QUOTED_STRING and UNQUOTED_SOURCE with just "{string}"
                case EsqlBaseParser.QUOTED_STRING:
                case EsqlBaseParser.UNQUOTED_SOURCE:
                    completions.add(new Completion("{string}", Completion.Kind.PLACEHOLDER));
                    break;
                // replacing UNQUOTED_IDENTIFIER and QUOTED_IDENTIFIER with just {var}
                case EsqlBaseParser.UNQUOTED_IDENTIFIER:
                case EsqlBaseParser.QUOTED_IDENTIFIER:
                    completions.add(new Completion("{var}", Completion.Kind.PLACEHOLDER));
                    break;
                // replacing NAMED_OR_POSITIONAL_PARAM, NAMED_OR_POSITIONAL_DOUBLE_PARAMS and ID_PATTERN
                // with just {param}
                case EsqlBaseParser.NAMED_OR_POSITIONAL_PARAM:
                case EsqlBaseParser.NAMED_OR_POSITIONAL_DOUBLE_PARAMS:
                case EsqlBaseParser.ID_PATTERN:
                    completions.add(new Completion("{param}", Completion.Kind.PLACEHOLDER));
                    break;
                // replacing DECIMAL_LITERAL with just {num}
                case EsqlBaseParser.DECIMAL_LITERAL:
                    completions.add(new Completion("{num}", Completion.Kind.PLACEHOLDER));
                    break;
                // LP means functions, adding brackets to token
                case EsqlBaseParser.LP:
                    for (String function : FUNCTIONS) {
                        completions.add(new Completion(function + "()", Completion.Kind.FUNCTION));
                    }
                    break;
                case EsqlBaseParser.PIPE:
                    completions.add(new Completion("|", Completion.Kind.PIPE));
                    break;
                case EsqlBaseParser.PARAM:
                case EsqlBaseParser.DOUBLE_PARAMS:
                case EsqlBaseParser.EOF:
                    break;
                default:
                    String display = EsqlBaseParser.VOCABULARY.getDisplayName(tokenType);
                    if (display != null && !display.isEmpty() && !display.startsWith("DEV_")) {
                        String tokenText = display.replaceAll("'", "").toUpperCase(Locale.ROOT);
                        completions.add(new Completion(tokenText, Completion.Kind.KEYWORD));
                    }
            }
        }

        return completions;
    }

    private static Set<Completion> computeSchemaDependentCompletions(Parser.ParserInfo parserInfo,
                                                                      EsqlPluginQueryManager queryManager) {
        Set<Completion> completions = new HashSet<>();
        if (queryManager == null) {
            return completions;
        }

        Token lastNonSpaceToken = parserInfo.lastNonSpacetoken();
        if (lastNonSpaceToken == null) {
            return completions;
        }

        ServerOperation serverOp = serverOperationsMap.get(lastNonSpaceToken.getType());
        if (serverOp == null) {
            return completions;
        }

        switch (serverOp) {
            case indices: {
                for (String index : queryManager.getIndices()) {
                    completions.add(new Completion(index, Completion.Kind.NAME));
                }
                break;
            }
            case fields: {
                String index = findQueriedIndex(parserInfo.tokens());
                if (!index.isEmpty()) {
                    for (String field : queryManager.getFields(index)) {
                        completions.add(new Completion(field, Completion.Kind.FIELD));
                    }
                }
                break;
            }
        }

        return completions;
    }


    private static CandidatesCollection completionCandidates(Parser.ParserInfo parserInfo) {
        var parser = parserInfo.parser();
        var tokens = parserInfo.tokens();

        var codeCompletionCode = new CodeCompletionCore(parser);
        var caretIndex = tokens.size() - 1;
        return codeCompletionCode.collectCandidates(caretIndex, null);
    }

    static String findQueriedIndex(List<Token> tokens) {
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
}

