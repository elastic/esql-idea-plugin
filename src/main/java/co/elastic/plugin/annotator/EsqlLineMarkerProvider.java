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

import co.elastic.plugin.EsqlIcon;
import co.elastic.plugin.execution.ExecuteEsqlQueryAction;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiPlainText;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.java.PsiJavaTokenImpl;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static co.elastic.plugin.CommonUtils.checkEsqlCommentAbove;
import static co.elastic.plugin.CommonUtils.findEsqlBlocksInPlainText;
import static co.elastic.plugin.CommonUtils.isEsqlTextBlock;
import static com.intellij.psi.JavaTokenType.TEXT_BLOCK_LITERAL;

public class EsqlLineMarkerProvider implements LineMarkerProvider {

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        return null;
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements,
                                        @NotNull Collection<? super LineMarkerInfo<?>> result) {
        for (PsiElement element : elements) {


            if (element instanceof PsiPlainText) {
                List<TextRange> ranges = findEsqlBlocksInPlainText(element);
                Document document = element.getContainingFile().getViewProvider().getDocument();
                if (document == null) return;
                for (TextRange range : ranges) {
                    addMarkers(result, element, range, document.getText(range));
                }
                return;
            }

            if (!isEsqlTextBlock(element)) continue;

            String text = element.getText();
            String query = text.substring(3, text.length() - 3).trim();
            addMarkers(result, element, element.getTextRange(), query);
        }
    }

    private static void addMarkers(Collection<? super LineMarkerInfo<?>> result,
                                    PsiElement element, TextRange range, String query) {
        result.add(new LineMarkerInfo<>(
            element, range, EsqlIcon.ESQL_ICON,
            e -> "ES|QL Query", null,
            GutterIconRenderer.Alignment.LEFT, () -> "ES|QL Query"
        ));
        result.add(new LineMarkerInfo<>(
            element, range, AllIcons.Actions.Execute,
            e -> "Execute ES|QL Query",
            (mouseEvent, elt) -> ExecuteEsqlQueryAction.execute(elt.getProject(), query),
            GutterIconRenderer.Alignment.RIGHT, () -> "Execute ES|QL Query"
        ));
    }


}
