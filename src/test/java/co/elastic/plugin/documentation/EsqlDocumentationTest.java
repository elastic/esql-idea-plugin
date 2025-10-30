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
package co.elastic.plugin.documentation;

import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.List;


@SuppressWarnings("UnstableApiUsage")
public class EsqlDocumentationTest extends BasePlatformTestCase {

    @Test
    public void testInputElementShowsDocumentationJava() {
        myFixture.configureByText("TestJava.java", """
            package co.elastic.plugin;
            
            public class TestJava {
              public static void main(String[] args) {
                // ES|QL
                String wheeQuery = ""\"
                     FRO<caret>M ul_logs, apps METADATA _index, _version\\n" +
                    "| WHERE id IN (13, 14) AND _version == 1\\n" +
                    "| EVAL key = CONCAT(_index, \\"_\\", TO_STR(id))\\n
                    ""\";
              }
            }
            """);

        // pointing cursor at <caret> special word
        PsiFile file = myFixture.getFile();
        int offset = myFixture.getCaretOffset();
        PsiElement leafPsiElement = myFixture.getFile().findElementAt(offset);
        assertNotNull(leafPsiElement);

        // finding our doc provider between all the ones available
        List<DocumentationTargetProvider> providers = DocumentationTargetProvider.EP_NAME.getExtensionList();
        assertNotNull(providers);
        assertTrue(providers.stream().anyMatch(p -> p instanceof EsqlDocumentationProvider));
        EsqlDocumentationProvider esqlDocProvider = (EsqlDocumentationProvider) providers.stream()
            .filter(p -> p instanceof EsqlDocumentationProvider)
            .findFirst().orElse(null);

        // getting the doc target
        List<? extends @NotNull DocumentationTarget> targets = esqlDocProvider
            .documentationTargets(file, offset);
        assertNotNull(targets);
        DocumentationTarget documentationTarget = targets.getFirst();
        assertTrue(documentationTarget instanceof EsqlDocumentationProvider.EsqlDocs);
        EsqlDocumentationProvider.EsqlDocs esqlDocs =
            (EsqlDocumentationProvider.EsqlDocs) documentationTarget;

        DocumentationResult result = esqlDocs.computeDocumentation();
        assertEquals(EsqlDocsMap.getDocforCommand("from"), result);
    }

    @Test
    public void testInputElementShowsDocumentationKotlin() {
        myFixture.configureByText("TestKotlin.kt", """
            package main.kotlin
            
            fun main() {
               // ES|QL
                val a = ""\"
                     FRO<caret>M ul_logs, apps METADATA _index, _version\\n" +
                    "| WHERE id IN (13, 14) AND _version == 1\\n" +
                    "| EVAL key = CONCAT(_index, \\"_\\", TO_STR(id))\\n
                ""\"
            }
            """);

        // pointing cursor at <caret> special word
        PsiFile file = myFixture.getFile();
        int offset = myFixture.getCaretOffset();
        PsiElement leafPsiElement = myFixture.getFile().findElementAt(offset);
        assertNotNull(leafPsiElement);

        // finding our doc provider between all the ones available
        List<DocumentationTargetProvider> providers = DocumentationTargetProvider.EP_NAME.getExtensionList();
        assertNotNull(providers);
        assertTrue(providers.stream().anyMatch(p -> p instanceof EsqlDocumentationProvider));
        EsqlDocumentationProvider esqlDocProvider = (EsqlDocumentationProvider) providers.stream()
            .filter(p -> p instanceof EsqlDocumentationProvider)
            .findFirst().orElse(null);

        // getting the doc target
        List<? extends @NotNull DocumentationTarget> targets = esqlDocProvider
            .documentationTargets(file, offset);
        assertNotNull(targets);
        DocumentationTarget documentationTarget = targets.getFirst();
        assertTrue(documentationTarget instanceof EsqlDocumentationProvider.EsqlDocs);
        EsqlDocumentationProvider.EsqlDocs esqlDocs =
            (EsqlDocumentationProvider.EsqlDocs) documentationTarget;

        DocumentationResult result = esqlDocs.computeDocumentation();
        assertEquals(EsqlDocsMap.getDocforCommand("from"), result);
    }
}
