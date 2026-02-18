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
import co.elastic.plugin.connection.EsqlPluginQueryManager;
import co.elastic.plugin.connection.EsqlPluginQueryManagerImpl;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.*;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.JBColor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import static co.elastic.plugin.CommonUtils.*;

public class EsqlCompletionProvider extends CompletionProvider<CompletionParameters> {

    private final EsqlPluginQueryManagerImpl queryManager =
        ApplicationManager.getApplication().getService(EsqlPluginQueryManagerImpl.class);

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

        var parserInfo = Parser.parse(text);
        var completions = Completion.computeCompletions(parserInfo, queryManager);
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
}
