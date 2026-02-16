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

import org.junit.Assert;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static co.elastic.plugin.CommonUtils.FUNCTIONS;
import static co.elastic.plugin.CommonUtils.METADATA_OPTIONS;


public class AutoCompleteTest {

    @Test
    public void testStartedQuery() {
        var completions = EsqlCompletionProvider.computeCompletions("FR");

        Set<String> texts = completions.stream().map(Completion::text).collect(Collectors.toSet());
        Assert.assertTrue(texts.contains("FROM"));
        Assert.assertTrue(texts.contains("ROW"));
        Assert.assertTrue(texts.contains("SHOW"));
        Assert.assertTrue(texts.contains("TS"));
        Assert.assertTrue(texts.contains("PROMQL"));
        Assert.assertTrue(completions.stream().allMatch(c -> c.kind() == Completion.Kind.KEYWORD));
    }

    @Test
    public void testEmptyQuery() {
        var completions = EsqlCompletionProvider.computeCompletions("");

        Set<String> texts = completions.stream().map(Completion::text).collect(Collectors.toSet());
        Assert.assertTrue(texts.contains("FROM"));
        Assert.assertTrue(texts.contains("ROW"));
        Assert.assertTrue(texts.contains("SHOW"));
        Assert.assertTrue(texts.contains("TS"));
        Assert.assertTrue(texts.contains("PROMQL"));
        Assert.assertTrue(completions.stream().allMatch(c -> c.kind() == Completion.Kind.KEYWORD));
    }

    @Test
    public void testIndexCompletion() {
        var completions = EsqlCompletionProvider.computeCompletions("FROM ");

        Assert.assertTrue(
            completions.contains(new Completion("{string}", Completion.Kind.PLACEHOLDER))
        );
    }

    @Test
    public void testIndexCompletionMultiSpace() {
        var completions = EsqlCompletionProvider.computeCompletions("FROM        ");

        Assert.assertTrue(
            completions.contains(new Completion("{string}", Completion.Kind.PLACEHOLDER))
        );
    }

    @Test
    public void testAfterSourceExpectsPipeAndMore() {
        var completions = EsqlCompletionProvider.computeCompletions("FROM index ");

        Assert.assertTrue(
            completions.contains(new Completion("|", Completion.Kind.PIPE))
        );
        Assert.assertTrue(
            completions.contains(new Completion("METADATA", Completion.Kind.KEYWORD))
        );
    }

    @Test
    public void testAfterPipeExpectsCommands() {
        var completions = EsqlCompletionProvider.computeCompletions("FROM index | ");

        Set<String> texts = completions.stream().map(Completion::text).collect(Collectors.toSet());
        Assert.assertTrue(texts.contains("WHERE"));
        Assert.assertTrue(texts.contains("EVAL"));
        Assert.assertTrue(texts.contains("SORT"));
        Assert.assertTrue(texts.contains("LIMIT"));
        Assert.assertTrue(texts.contains("STATS"));
        Assert.assertTrue(texts.contains("KEEP"));
        Assert.assertTrue(texts.contains("DROP"));
    }

    @Test
    public void testWhereExpectsVarAndFunctions() {
        var completions = EsqlCompletionProvider.computeCompletions("FROM index | WHERE ");

        Assert.assertTrue(
            completions.contains(new Completion("{var}", Completion.Kind.PLACEHOLDER))
        );

        Set<String> functionTexts = completions.stream()
            .filter(c -> c.kind() == Completion.Kind.FUNCTION)
            .map(Completion::text)
            .collect(Collectors.toSet());
        Assert.assertFalse("Expected function completions", functionTexts.isEmpty());
        for (String fn : FUNCTIONS) {
            Assert.assertTrue("Missing function: " + fn, functionTexts.contains(fn + "()"));
        }
    }

    @Test
    public void testMetadataOptions() {
        var completions = EsqlCompletionProvider.computeCompletions("FROM index METADATA ");

        Set<String> texts = completions.stream().map(Completion::text).collect(Collectors.toSet());
        for (String opt : METADATA_OPTIONS) {
            Assert.assertTrue("Missing metadata option: " + opt, texts.contains(opt));
        }
    }
}
