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

import co.elastic.plugin.CommonUtils;
import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPlainText;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.JavaTokenType.TEXT_BLOCK_LITERAL;

public class EsqlCompletionConfidence extends CompletionConfidence {

    @Override
    public @NotNull ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
        ASTNode node = contextElement.getNode();
        if (node == null) {
            return ThreeState.UNSURE;
        }

        boolean isStringLiteral = node.getElementType().equals(TEXT_BLOCK_LITERAL)
            || node.getElementType().toString().equals("REGULAR_STRING_PART")
            || contextElement instanceof PsiPlainText;

        // for kotlin, navigate up to the STRING_TEMPLATE element
        if (contextElement.getLanguage().is(Language.findLanguageByID("kotlin"))) {
            contextElement = contextElement.getParent().getParent();
        }

        if (isStringLiteral && CommonUtils.checkEsqlCommentAbove(contextElement, offset)) {
            return ThreeState.NO;
        }

        return ThreeState.UNSURE;
    }
}
