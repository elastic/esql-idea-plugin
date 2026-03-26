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
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiPlainText;
import com.intellij.ui.JBColor;
import com.intellij.util.ProcessingContext;
import org.antlr.v4.runtime.Token;
import org.jetbrains.annotations.NotNull;

import static co.elastic.plugin.CommonUtils.checkEsqlCommentAbove;
import static co.elastic.plugin.CommonUtils.findEsqlBlocksInPlainText;

public class EsqlCompletionProvider extends CompletionProvider<CompletionParameters> {

    private final EsqlPluginQueryManager queryManager =
        ApplicationManager.getApplication().getService(EsqlPluginQueryManager.class);

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {

        var elementAtOffset = parameters.getOriginalPosition();
        int caretOffset = parameters.getOffset();
        String text;

        // for txt files, the element will be the whole file, so need to get the specific block
        if (elementAtOffset instanceof PsiPlainText) {
            TextRange block = findEsqlBlocksInPlainText(elementAtOffset).stream()
                .filter(range -> range.getStartOffset() <= caretOffset && caretOffset <= range.getEndOffset())
                .findFirst()
                .orElse(null);
            if (block == null) return;
            text = elementAtOffset.getText().substring(
                block.getStartOffset() - elementAtOffset.getTextRange().getStartOffset(),
                caretOffset - elementAtOffset.getTextRange().getStartOffset());
        } else {
            int elementStart = elementAtOffset.getTextRange().getStartOffset();
            text = elementAtOffset.getText().substring(0, caretOffset - elementStart);
            // remove triple quotes if java
            if (elementAtOffset.getLanguage().is(Language.findLanguageByID("JAVA"))) {
                text = text.substring(3);
            }
        }

        if (!checkEsqlCommentAbove(elementAtOffset, caretOffset)) {
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

            var matcher = getMatcher(lastToken);

            switch (c.kind()) {
                case METADATA, NAME, FIELD:
                    insertLookupWithColor(result, c.text(), priority, matcher);
                    break;
                default:
                    result.withPrefixMatcher(matcher).addElement(PrioritizedLookupElement.withPriority(
                        LookupElementBuilder.create(c.text()),
                        priority)
                    );

            }
        }
    }

    private static @NotNull PrefixMatcher getMatcher(Token lastToken) {
        // If the last token is not a space (spaces are in a hidden channel) or a parenthesis, or a comma,
        // use it on matching to replace the current word
        return isSpaceOrParenthesisOrComma(lastToken) ? new PermissivePrefixMatcher() :
            new PermissivePrefixMatcher(lastToken.getText());
    }

    private static boolean isSpaceOrParenthesisOrComma(Token lastToken) {
        return lastToken.getChannel() != EsqlBaseLexer.DEFAULT_TOKEN_CHANNEL ||
               lastToken.getType() == EsqlBaseLexer.COMMA ||
               (lastToken.getType() == EsqlBaseLexer.LP || lastToken.getType() == EsqlBaseLexer.RP);
    }

    private static void insertLookupWithColor(@NotNull CompletionResultSet result, String token,
                                              int priority, PrefixMatcher matcher) {
        LookupElement lookup = LookupElementBuilder.create(token);
        result.withPrefixMatcher(matcher)
            .addElement(PrioritizedLookupElement
                .withPriority(LookupElementDecorator.withRenderer(lookup, new LookupElementRenderer<>() {
                    public void renderElement(LookupElementDecorator<LookupElement> element,
                                              LookupElementPresentation presentation) {
                        element.getDelegate().renderElement(presentation);
                        presentation.setItemTextForeground(JBColor.YELLOW);
                    }
                }), priority));
    }
}
