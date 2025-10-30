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
package co.elastic.plugin.annotator;

import co.elastic.grammar.EsqlBaseLexer;
import co.elastic.grammar.EsqlBaseParser;
import co.elastic.plugin.EsqlIcon;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.java.PsiJavaTokenImpl;
import com.intellij.psi.tree.IElementType;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static co.elastic.plugin.CommonUtils.ESQL_SEPARATORS;
import static co.elastic.plugin.CommonUtils.FUNCTIONS;
import static co.elastic.plugin.CommonUtils.PROCESSING_COMMANDS;
import static co.elastic.plugin.CommonUtils.SOURCE_COMMANDS;
import static co.elastic.plugin.CommonUtils.checkEsqlCommentAbove;
import static com.intellij.psi.JavaTokenType.TEXT_BLOCK_LITERAL;

/**
 * Checks the query syntax and underlines errors.
 */
public class EsqlAnnotator implements Annotator {

    private static Map<String, String> replacementsMap =
        Map.ofEntries(
            Map.entry("?", ""),
            Map.entry("??", ""),
            // making errors easier to understand
            Map.entry("mismatched input '<EOF>'", "unexpected end of line,"),
            Map.entry("QUOTED_STRING, UNQUOTED_SOURCE", "string"),
            Map.entry("QUOTED_STRING", "string"),
            Map.entry("INTEGER_LITERAL, DECIMAL_LITERAL", "num"),
            Map.entry("NAMED_OR_POSITIONAL_PARAM, " + "NAMED_OR_POSITIONAL_DOUBLE_PARAMS", "parameter"),
            Map.entry("UNQUOTED_IDENTIFIER, QUOTED_IDENTIFIER", "var"),
            Map.entry("LP", "any function"),
            Map.entry("OPENING_BRACKET", "brackets"),
            Map.entry("expecting <EOF>", ", expecting end of line or processor/function arguments"),
            Map.entry("expecting {<EOF>", ", expecting {end of line"),
            Map.entry("at '<EOF>'", "at end of line")
        );

    private static final TextAttributesKey MY_COLOR =
        TextAttributesKey.createTextAttributesKey("MY_COLOR", DefaultLanguageHighlighterColors.KEYWORD);

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (accept(element)) {
            String text = element.getText();
            applyColor(element, holder, text);
            validateText(element, holder, text);
        }
    }

    /**
     * To filter unwanted elements
     *
     * @return <ttt>true</ttt> to accept or <tt>false</tt> to drop
     */
    boolean accept(PsiElement element) {

        if (element == null || element.getNode() == null) {
            return false;
        }

        // skip whitespaces
        IElementType type = element.getNode().getElementType();
        if (type == TokenType.WHITE_SPACE) {
            return false;
        }

        // it's a literal expression
        // PsiLiteralExpression for java
        if (element instanceof PsiLiteralExpression) {
            // it's a text block (triple quote)
            if (((PsiJavaTokenImpl) element.getFirstChild()).getElementType().equals(TEXT_BLOCK_LITERAL)) {

                return checkEsqlCommentAbove(element);
            }
        }

        // STRING_TEMPLATE to match kotlin triple quote
        if (element.toString().equals("STRING_TEMPLATE")) {
            return checkEsqlCommentAbove(element);
        }

        return false;
    }

    private void applyColor(@NotNull PsiElement element, @NotNull AnnotationHolder holder,
                            @NotNull String text) {

        // showing plugin icon regardless
        holder.newAnnotation(HighlightSeverity.INFORMATION, "")
            .gutterIconRenderer(new EsqlIcon())
            .range(element.getTextRange()).create();

        // different color for different types of keywords
        List<String> allKeywords = new ArrayList<>();
        allKeywords.addAll(List.of(SOURCE_COMMANDS));
        allKeywords.addAll(List.of(PROCESSING_COMMANDS));
        allKeywords.addAll(List.of(FUNCTIONS));

        for (String keyword : allKeywords) {
            if (text.contains(keyword)) {

                int index = text.indexOf(keyword);
                // must find all occurrences of word in string
                while (index >= 0) {
                    // making sure it's not a substring (example: "COS" and "COSH", "E" and "ENRICH")
                    if (notASubstring(text, keyword, index)) {
                        int rangeStart = element.getTextRange().getStartOffset() + index;
                        int rangeEnd = rangeStart + keyword.length();

                        TextRange range = new TextRange(rangeStart, rangeEnd);
                        holder.newAnnotation(HighlightSeverity.INFORMATION, "")
                            .textAttributes(MY_COLOR)
                            .range(range).create();
                    }
                    index = text.indexOf(keyword, index + 1);
                }
            }
        }
    }

    // looking for either a space, bracket, quote or equals sign before or after
    // test b regex instead of separators
    private boolean notASubstring(String text, String substring, int index) {
        if (index == 0 || index == text.length() - 1) return true;
        char before = text.charAt(index - 1);
        char after = text.charAt(index + substring.length());
        return !(ESQL_SEPARATORS.indexOf(before) == -1) && !(ESQL_SEPARATORS.indexOf(after) == -1);
    }


    /**
     * Validate the text and create error messaged from the validation result.
     *
     * @param element - Element to parse
     * @param holder  - Container for the different error messages and it's test range
     * @param text    - String to validate
     */
    private void validateText(@NotNull PsiElement element, @NotNull AnnotationHolder holder,
                              @NotNull String text) {

        // remove triple quotes at the beginning and the end, also trim
        String query = text.substring(3, text.length() - 3).trim();
        // calculating range of inner string
        // also needed in case of exception
        int startingPosition = element.getTextRange().getStartOffset() + text.indexOf(query);
        int endingPosition = startingPosition + query.length();
        TextRange wholeStringRange = new TextRange(startingPosition, endingPosition);

        try {
            InputStream stream = new ByteArrayInputStream(query.getBytes(StandardCharsets.UTF_8));
            EsqlBaseLexer lexer = new EsqlBaseLexer(CharStreams.fromStream(stream, StandardCharsets.UTF_8));

            EsqlBaseParser parser = new EsqlBaseParser(new CommonTokenStream(lexer));

            parser.setBuildParseTree(false);
            EsqlErrorListener errorListener = new EsqlErrorListener(new ArrayList<>());

            lexer.removeErrorListeners();
            lexer.addErrorListener(errorListener);
            parser.removeErrorListeners();
            parser.addErrorListener(errorListener);

            // rule check
            parser.singleStatement();

            for (EsqlErrorListener.Error error : errorListener.errors) {

                // need to retrieve specific line where error is
                int absoluteCharPosition = calculateAbsoluteCharPosition(error, element, startingPosition);

                AtomicInteger start = new AtomicInteger(absoluteCharPosition);

                int end = error.offendingToken.map(t ->
                    {
                        // if end of line, just underline the last char of string
                        if (t.equals("<EOF>")) {
                            return start.get() + 1;
                        }
                        return start.get() + t.length();
                    })
                    .orElse(endingPosition);

                TextRange range = new TextRange(start.get(), end);

                // remove all DEV_ experimental fields
                String message = error.message.replaceAll("DEV_.*?,", "");

                for (Map.Entry<String, String> replacement : replacementsMap.entrySet()) {
                    message = message.replace(replacement.getKey(), replacement.getValue());
                }

                holder.newAnnotation(HighlightSeverity.ERROR, message)
                    .range(range).create();
            }

        } catch (Exception e) {
            holder.newAnnotation(HighlightSeverity.ERROR,
                    "annotator error. \nexception: " + e.getClass() +
                    "\nmessage: " + e.getMessage())
                .range(wholeStringRange).create();
        }
    }

    private int calculateAbsoluteCharPosition(EsqlErrorListener.Error error, PsiElement element,
                                              int queryStart) {
        if (error.line == 1) {
            return queryStart + error.charPositionInLine;
        }
        PsiFile file = element.getContainingFile();
        Document document = file.getViewProvider().getDocument();
        int lineStartOffset =
            document.getLineStartOffset(document.getLineNumber(element.getTextRange().getStartOffset()) + error.line);
        return lineStartOffset + error.charPositionInLine;
    }
}
