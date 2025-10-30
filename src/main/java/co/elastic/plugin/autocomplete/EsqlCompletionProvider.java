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
import co.elastic.grammar.completion.CompletionCoreApiKt;
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
import com.intellij.ide.projectWizard.NewProjectWizardConstants;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.JBColor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static co.elastic.plugin.CommonUtils.FUNCTIONS;
import static co.elastic.plugin.CommonUtils.METADATA_OPTIONS;
import static co.elastic.plugin.CommonUtils.SOURCE_COMMANDS;
import static co.elastic.plugin.CommonUtils.checkEsqlCommentAbove;

public class EsqlCompletionProvider extends CompletionProvider<CompletionParameters> {

    EsqlPluginSettings settings = ApplicationManager.getApplication().getService(EsqlPluginSettings.class);
    EsqlPluginQueryManager queryManager =
        ApplicationManager.getApplication().getService(EsqlPluginQueryManager.class);

    // maximum number of characters a source command will have, including space
    private final int STARTER_QUERY = 5;

    private enum ServerOperation {
        indices,
        fields
    }

    private static Map<String, ServerOperation> serverOperationsMap =
        Map.ofEntries(
            Map.entry("FROM", ServerOperation.indices),
            Map.entry("SORT", ServerOperation.fields),
            Map.entry("EVAL", ServerOperation.fields),
            Map.entry("WHERE", ServerOperation.fields)
        );

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {

        // getting the full text from the element at cursor, and not from the prefix matcher,
        // because an ES|QL query can include "/", which is considered as a separator character
        var elementAtOffset = parameters.getOriginalPosition();
        String text = elementAtOffset.getText();

        // removing triple quotes if java, trimming
        if (elementAtOffset.getLanguage().is(Language.findLanguageByID("JAVA"))) {
            text = text.substring(3, text.length() - 3);
        }
        text = text.trim();


        if (!checkEsqlCommentAbove(elementAtOffset)) {
            return;
        }

        // suggesting possible fields/indices by also querying elasticsearch if configured
        autofillQuery(result, text);

        // using antlr grammar to figure out next token
        Set<Integer> expectedTokenTypes = CompletionCoreApiKt.completions(text, EsqlBaseLexer.class,
            EsqlBaseParser.class);

        // try to complete string if there's no suggestions
        // and if the full text is short enough that we're probably at the beginning of the query
        // source commands first, just one
        if (expectedTokenTypes.isEmpty() && text.length() < STARTER_QUERY) {
            for (String source : SOURCE_COMMANDS) {
                if (source.startsWith(text)) {
                    result.withPrefixMatcher(new PermissivePrefixMatcher(text))
                        .addElement(PrioritizedLookupElement
                            .withPriority(LookupElementBuilder.create(source), 20));
                }
            }
            return;
        }
        putResult(result, expectedTokenTypes);
    }

    private static void putResult(@NotNull CompletionResultSet result, Set<Integer> expectedTokenTypes) {

        for (Integer tokenType : expectedTokenTypes) {
            String token = EsqlBaseParser.VOCABULARY.getDisplayName(tokenType);
            if (token != null && !token.isEmpty() && !token.contains("DEV_")) {
                token = token.replaceAll("'", "");
                token = token.toUpperCase(Locale.ROOT);

                switch (token) {
                    // replacing QUOTED_STRING and UNQUOTED_SOURCE with just "{string}"
                    case "QUOTED_STRING":
                    case "UNQUOTED_SOURCE":
                        result.withPrefixMatcher(new PermissivePrefixMatcher()).addElement(PrioritizedLookupElement
                            .withPriority(LookupElementBuilder.create("{string}"), 5));
                        break;
                    // replacing UNQUOTED_IDENTIFIER and QUOTED_IDENTIFIER with just {var}
                    case "UNQUOTED_IDENTIFIER":
                    case "QUOTED_IDENTIFIER":
                        result.withPrefixMatcher(new PermissivePrefixMatcher()).addElement(PrioritizedLookupElement
                            .withPriority(LookupElementBuilder.create("{var}"), 5));
                        break;
                    // replacing NAMED_OR_POSITIONAL_PARAM, NAMED_OR_POSITIONAL_DOUBLE_PARAMS and ID_PATTERN
                    // with just {param}
                    case "NAMED_OR_POSITIONAL_PARAM":
                    case "NAMED_OR_POSITIONAL_DOUBLE_PARAMS":
                    case "ID_PATTERN":
                        result.withPrefixMatcher(new PermissivePrefixMatcher()).addElement(PrioritizedLookupElement
                            .withPriority(LookupElementBuilder.create("{param}"), 5));
                        break;
                    // replacing DECIMAL_LITERAL with just {num}
                    case "DECIMAL_LITERAL":
                        result.withPrefixMatcher(new PermissivePrefixMatcher()).addElement(PrioritizedLookupElement
                            .withPriority(LookupElementBuilder.create("{num}"), 5));
                        break;
                    // LP means functions, adding brackets to token
                    case "LP":
                        for (String function : FUNCTIONS) {
                            result.withPrefixMatcher(new PermissivePrefixMatcher()).addElement(PrioritizedLookupElement
                                .withPriority(LookupElementBuilder.create(function + "()"), 5));
                        }
                        break;
                    // putting pipe | first in selection by increasing priority
                    case "|":
                        result.withPrefixMatcher(new PermissivePrefixMatcher())
                            .addElement(PrioritizedLookupElement
                                .withPriority(LookupElementBuilder.create(token), 6));
                        break;
                    default:
                        // skipping "EOF", "?" and "??", not useful for users
                        if (!token.equals("EOF") && !token.equals("??") && !token.equals("?")) {
                            result.withPrefixMatcher(new PermissivePrefixMatcher()).addElement(PrioritizedLookupElement
                                .withPriority(LookupElementBuilder.create(token), 5));
                        }
                }
            }
        }
    }

    private void autofillQuery(@NotNull CompletionResultSet result, String text) {
        // find last command
        String[] words = text.split("[ ()=\"']+");
        String lastWord = words[words.length - 1].trim().toUpperCase();

        // metadata special case
        if (lastWord.equals("METADATA")) {
            for (String metadataOpt : METADATA_OPTIONS) {
                insertLookupWithColor(result, metadataOpt);
            }
        }

        // online only options
        if (!settings.getServerUrl().isEmpty() && !settings.getApiKey().isEmpty()) {

            ServerOperation serverOp = serverOperationsMap.get(lastWord);
            if (serverOp == null) {
                return;
            }

            // hardcoded for now
            switch (serverOp) {
                case indices: {
                    List<String> indices = queryManager.getIndices();
                    for (String index : indices) {
                        insertLookupWithColor(result, index);
                    }
                    break;
                }
                case fields: {
                    // find index used. should be the word after FROM
                    String index = "";
                    for (int i = 0; i < words.length - 1; i++) {
                        if (words[i].trim().equals("FROM")) {
                            index = words[i + 1].trim();
                            break;
                        }
                    }
                    if (index.isEmpty()) return;

                    List<String> fields = queryManager.getFields(index);
                    for (String field : fields) {
                        insertLookupWithColor(result, field);
                    }
                    break;
                }
            }
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

