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

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.lang.Language;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class EsqlAutocompleteTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected boolean runInDispatchThread() {
        return false;
    }

    @Test
    public void testCompletionsJava() {
        // <caret> is a special symbol, allowing to move the cursor and
        // type at the desired offset
        // EditorTestUtil.CARET_TAG
        myFixture.configureByText("TestJava.java", """
            package co.elastic.plugin;
            
            public class TestJava {
                public static void main(String[] args) {
                // ES|QL
                String a = ""\"
                        <caret>
                        ""\";
                    }
            }
            """);

        WriteCommandAction.runWriteCommandAction(getProject(), () ->
            myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getCaretOffset()));

        // should autocomplete to FROM
        // completeBasic returns null if it completed the only available result
        myFixture.type("FR");
        Assert.assertNull(myFixture.completeBasic());

        myFixture.checkResult("""
            package co.elastic.plugin;
            
            public class TestJava {
                public static void main(String[] args) {
                // ES|QL
                String a = ""\"
                        FROM
                        ""\";
                    }
            }
            """);

        // typing a space, expecting {string}
        myFixture.type(" ");
        LookupElement[] elements = myFixture.completeBasic();

        Assert.assertNotNull(elements);
        Assert.assertEquals(1, elements.length);
        Assert.assertEquals("{string}", elements[0].getLookupString());

        // applying the completion
        LookupImpl lookup = (LookupImpl) LookupManager.getActiveLookup(myFixture.getEditor());
        Assert.assertNotNull(lookup);
        // if not wrapped, the error will claim that finishLookup, a UI operation,
        // needs to be wrapped in a ReadAction or similar, but the correct way to handle
        // UI/EDT (event dispatch thread) is using EdtTestUtil
        LookupElement finalElement = elements[0];
        EdtTestUtil.runInEdtAndWait(() -> {
            lookup.setCurrentItem(finalElement);
            lookup.finishLookup(Lookup.NORMAL_SELECT_CHAR);
        });

        myFixture.checkResult("""
            package co.elastic.plugin;
            
            public class TestJava {
                public static void main(String[] args) {
                // ES|QL
                String a = ""\"
                        FROM {string}
                        ""\";
                    }
            }
            """);

        // typing another space, expecting 5 elements, including a pipe, which we'll apply
        myFixture.type(" ");
        elements = myFixture.completeBasic();

        Assert.assertNotNull(elements);
        Assert.assertEquals(5, elements.length);
        LookupElement pipe = Arrays.stream(elements)
            .filter(le -> le
                .getLookupString()
                .contains("|"))
            .findFirst().get();
        Assert.assertEquals("|", pipe.getLookupString());

        // applying our choice as before
        LookupImpl lookup1 = (LookupImpl) LookupManager.getActiveLookup(myFixture.getEditor());
        EdtTestUtil.runInEdtAndWait(() -> {
            lookup1.setCurrentItem(pipe);
            lookup1.finishLookup(Lookup.NORMAL_SELECT_CHAR);
        });

        myFixture.checkResult("""
            package co.elastic.plugin;
            
            public class TestJava {
                public static void main(String[] args) {
                // ES|QL
                String a = ""\"
                        FROM {string} |
                        ""\";
                    }
            }
            """);

        // typing one last space, expecting full list of commands, including WHERE, which we'll apply
        myFixture.type(" ");
        elements = myFixture.completeBasic();

        Assert.assertNotNull(elements);
        Assert.assertEquals(17, elements.length);
        LookupElement where = Arrays.stream(elements)
            .filter(le -> le.getLookupString().contains("WHERE"))
            .findFirst().get();
        Assert.assertEquals("WHERE", where.getLookupString());

        // applying our choice as before
        LookupImpl lookup2 = (LookupImpl) LookupManager.getActiveLookup(myFixture.getEditor());
        EdtTestUtil.runInEdtAndWait(() -> {
            lookup2.setCurrentItem(where);
            lookup2.finishLookup(Lookup.NORMAL_SELECT_CHAR);
        });

        myFixture.checkResult("""
            package co.elastic.plugin;
            
            public class TestJava {
                public static void main(String[] args) {
                // ES|QL
                String a = ""\"
                        FROM {string} | WHERE
                        ""\";
                    }
            }
            """);

    }

    @Test
    public void testCompletionsKotlin() {
        // <caret> is a special symbol, allowing to move the cursor and
        // type at the desired offset
        // EditorTestUtil.CARET_TAG
        myFixture.configureByText("TestKotlin.kt", """
            package main.kotlin
            
            fun main() {
            
                // ES|QL
                val a = ""\"
                        <caret>
                        ""\"
            }
            """);

        // making sure the test env recognizes kotlin files
        Assert.assertTrue(myFixture.getFile().getLanguage().is(Language.findLanguageByID("kotlin")));

        WriteCommandAction.runWriteCommandAction(getProject(), () ->
            myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getCaretOffset()));

        // should autocomplete to FROM
        // completeBasic returns null if it completed the only available result
        myFixture.type("FR");
        Assert.assertNull(myFixture.completeBasic());

        myFixture.checkResult("""
            package main.kotlin
            
            fun main() {
            
                // ES|QL
                val a = ""\"
                        FROM
                        ""\"
            }
            """);

        // typing a space, expecting {string}
        myFixture.type(" ");
        LookupElement[] elements = myFixture.completeBasic();

        Assert.assertNotNull(elements);
        Assert.assertEquals(1, elements.length);
        Assert.assertEquals("{string}", elements[0].getLookupString());

        // applying the completion
        LookupImpl lookup = (LookupImpl) LookupManager.getActiveLookup(myFixture.getEditor());
        Assert.assertNotNull(lookup);
        // if not wrapped, the error will claim that finishLookup, a UI operation,
        // needs to be wrapped in a ReadAction or similar, but the correct way to handle
        // UI/EDT (event dispatch thread) is using EdtTestUtil
        LookupElement finalElement = elements[0];
        EdtTestUtil.runInEdtAndWait(() -> {
            lookup.setCurrentItem(finalElement);
            lookup.finishLookup(Lookup.NORMAL_SELECT_CHAR);
        });

        myFixture.checkResult("""
            package main.kotlin
            
            fun main() {
            
                // ES|QL
                val a = ""\"
                        FROM {string}
                        ""\"
            }
            """);

        // typing another space, expecting 5 elements, including a pipe, which we'll apply
        myFixture.type(" ");
        elements = myFixture.completeBasic();

        Assert.assertNotNull(elements);
        Assert.assertEquals(5, elements.length);
        LookupElement pipe = Arrays.stream(elements)
            .filter(le -> le
                .getLookupString()
                .contains("|"))
            .findFirst().get();
        Assert.assertEquals("|", pipe.getLookupString());

        // applying our choice as before
        LookupImpl lookup1 = (LookupImpl) LookupManager.getActiveLookup(myFixture.getEditor());
        EdtTestUtil.runInEdtAndWait(() -> {
            lookup1.setCurrentItem(pipe);
            lookup1.finishLookup(Lookup.NORMAL_SELECT_CHAR);
        });

        myFixture.checkResult("""
            package main.kotlin
            
            fun main() {
            
                // ES|QL
                val a = ""\"
                        FROM {string} |
                        ""\"
            }
            """);

        // typing one last space, expecting full list of commands, including WHERE, which we'll apply
        myFixture.type(" ");
        elements = myFixture.completeBasic();

        Assert.assertNotNull(elements);
        Assert.assertEquals(17, elements.length);
        LookupElement where = Arrays.stream(elements)
            .filter(le -> le.getLookupString().contains("WHERE"))
            .findFirst().get();
        Assert.assertEquals("WHERE", where.getLookupString());

        // applying our choice as before
        LookupImpl lookup2 = (LookupImpl) LookupManager.getActiveLookup(myFixture.getEditor());
        EdtTestUtil.runInEdtAndWait(() -> {
            lookup2.setCurrentItem(where);
            lookup2.finishLookup(Lookup.NORMAL_SELECT_CHAR);
        });

        myFixture.checkResult("""
            package main.kotlin
            
            fun main() {
            
                // ES|QL
                val a = ""\"
                        FROM {string} | WHERE
                        ""\"
            }
            """);

    }
}
