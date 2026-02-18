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

import co.elastic.plugin.connection.EsqlPluginQueryManager;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static co.elastic.plugin.CommonUtils.FUNCTIONS;
import static co.elastic.plugin.CommonUtils.METADATA_OPTIONS;


public class AutoCompleteTest {

    private static final EsqlPluginQueryManager STUB_QUERY_MANAGER = new EsqlPluginQueryManager() {
        @Override
        public List<String> getIndices() {
            return List.of("my-index", "logs-2024");
        }

        @Override
        public List<String> getFields(String indexName) {
            if ("my-index".equals(indexName)) {
                return List.of("field1", "field2", "@timestamp");
            }
            return List.of();
        }

        @Override
        public void startQueryThreadPool() {
        }
    };

    private void assertCompletesFieldNames(String query) {
        var completions = Completion.computeCompletions(query, STUB_QUERY_MANAGER);
        Assert.assertTrue(completions.contains(new Completion("field1", Completion.Kind.FIELD)));
        Assert.assertTrue(completions.contains(new Completion("field2", Completion.Kind.FIELD)));
        Assert.assertTrue(completions.contains(new Completion("@timestamp", Completion.Kind.FIELD)));
        Assert.assertTrue(completions.contains(new Completion("{var}", Completion.Kind.PLACEHOLDER)));
    }

    @Test
    public void testStartedQuery() {
        var completions = Completion.computeCompletions("FR", null);
        Assert.assertTrue(completions.contains(new Completion("FROM", Completion.Kind.KEYWORD)));
        Assert.assertTrue(completions.contains(new Completion("ROW", Completion.Kind.KEYWORD)));
        Assert.assertTrue(completions.contains(new Completion("SHOW", Completion.Kind.KEYWORD)));
        Assert.assertTrue(completions.contains(new Completion("TS", Completion.Kind.KEYWORD)));
        Assert.assertTrue(completions.contains(new Completion("PROMQL", Completion.Kind.KEYWORD)));
    }

    @Test
    public void testEmptyQuery() {
        var completions = Completion.computeCompletions("", null);
        Assert.assertTrue(completions.contains(new Completion("FROM", Completion.Kind.KEYWORD)));
        Assert.assertTrue(completions.contains(new Completion("ROW", Completion.Kind.KEYWORD)));
        Assert.assertTrue(completions.contains(new Completion("SHOW", Completion.Kind.KEYWORD)));
        Assert.assertTrue(completions.contains(new Completion("TS", Completion.Kind.KEYWORD)));
        Assert.assertTrue(completions.contains(new Completion("PROMQL", Completion.Kind.KEYWORD)));
    }

    @Test
    public void testLowerCaseQuery() {
        var completions = Completion.computeCompletions("from index | EV", null);
        Assert.assertTrue(completions.contains(new Completion("EVAL", Completion.Kind.KEYWORD)));
    }

    @Test
    public void testIndexCompletion() {
        var completions = Completion.computeCompletions("FROM ", null);

        Assert.assertEquals(1, completions.size());
        Assert.assertTrue(
            completions.contains(new Completion("{string}", Completion.Kind.PLACEHOLDER))
        );
    }

    @Test
    public void testIndexCompletionMultiSpace() {
        var completions = Completion.computeCompletions("FROM        ", null);

        Assert.assertTrue(
            completions.contains(new Completion("{string}", Completion.Kind.PLACEHOLDER))
        );
    }

    @Test
    public void testAfterSourceExpectsPipeAndMore() {
        var completions = Completion.computeCompletions("FROM index ", null);

        Assert.assertTrue(
            completions.contains(new Completion("|", Completion.Kind.PIPE))
        );
        Assert.assertTrue(
            completions.contains(new Completion("METADATA", Completion.Kind.KEYWORD))
        );

        Assert.assertFalse(
            completions.contains(new Completion("_source", Completion.Kind.METADATA))
        );
    }

    @Test
    public void testAfterPipeExpectsCommands() {
        var completions = Completion.computeCompletions("FROM index | ", null);
        Assert.assertTrue(completions.contains(new Completion("WHERE", Completion.Kind.KEYWORD)));
        Assert.assertTrue(completions.contains(new Completion("EVAL", Completion.Kind.KEYWORD)));
        Assert.assertTrue(completions.contains(new Completion("SORT", Completion.Kind.KEYWORD)));
        Assert.assertTrue(completions.contains(new Completion("LIMIT", Completion.Kind.KEYWORD)));
        Assert.assertTrue(completions.contains(new Completion("STATS", Completion.Kind.KEYWORD)));
        Assert.assertTrue(completions.contains(new Completion("KEEP", Completion.Kind.KEYWORD)));
        Assert.assertTrue(completions.contains(new Completion("DROP", Completion.Kind.KEYWORD)));
    }

    @Test
    public void testWhereExpectsVarAndFunctions() {
        var completions = Completion.computeCompletions("FROM index | WHERE ", null);

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
        var completions = Completion.computeCompletions("FROM index METADATA ", null);

        Set<String> texts = completions.stream().map(Completion::text).collect(Collectors.toSet());
        for (String opt : METADATA_OPTIONS) {
            Assert.assertTrue("Missing metadata option: " + opt, texts.contains(opt));
        }
    }

    @Test
    public void testFromCompletesFromWithIndices() {
        var completions = Completion.computeCompletions("FROM ", STUB_QUERY_MANAGER);
        Assert.assertTrue(completions.contains(new Completion("my-index", Completion.Kind.NAME)));
        Assert.assertTrue(completions.contains(new Completion("logs-2024", Completion.Kind.NAME)));
        Assert.assertTrue(completions.contains(new Completion("{string}", Completion.Kind.PLACEHOLDER)));
    }

    @Test
    public void testFromCompletesTsWithIndices() {
        var completions = Completion.computeCompletions("TS ", STUB_QUERY_MANAGER);
        Assert.assertTrue(completions.contains(new Completion("my-index", Completion.Kind.NAME)));
        Assert.assertTrue(completions.contains(new Completion("logs-2024", Completion.Kind.NAME)));
        Assert.assertTrue(completions.contains(new Completion("{string}", Completion.Kind.PLACEHOLDER)));
    }

    @Test
    public void testStatsByCompletesWithFields() {
        assertCompletesFieldNames("FROM my-index | STATS COUNT(*) BY ");
    }

    @Test
    public void testMvExpandCompletesWithFields() {
        assertCompletesFieldNames("FROM my-index | MV_EXPAND ");
    }

    @Test
    public void testWhereCompletesWithFields() {
        assertCompletesFieldNames("FROM my-index | WHERE ");
    }

    @Test
    public void testSortCompletesWithFields() {
        assertCompletesFieldNames("FROM my-index | SORT ");
    }

    @Test
    public void testEvalCompletesWithFields() {
        assertCompletesFieldNames("FROM my-index | EVAL ");
    }

    @Test
    public void testFieldsNotReturnedForUnknownIndex() {
        var completions = Completion.computeCompletions("FROM unknown | WHERE ", STUB_QUERY_MANAGER);
        Assert.assertFalse(completions.contains(new Completion("field1", Completion.Kind.FIELD)));
        Assert.assertFalse(completions.contains(new Completion("field2", Completion.Kind.FIELD)));
        Assert.assertFalse(completions.contains(new Completion("@timestamp", Completion.Kind.FIELD)));
        Assert.assertTrue(completions.contains(new Completion("{var}", Completion.Kind.PLACEHOLDER)));
    }

    @Test
    public void testNoSchemaCompletionsWithNullQueryManager() {
        var completions = Completion.computeCompletions("FROM ", null);
        Assert.assertFalse(completions.contains(new Completion("my-index", Completion.Kind.NAME)));
        Assert.assertFalse(completions.contains(new Completion("logs-2024", Completion.Kind.NAME)));
        Assert.assertTrue(completions.contains(new Completion("{string}", Completion.Kind.PLACEHOLDER)));
    }

    @Test
    public void testTsExpectsIndexPattern() {
        var completions = Completion.computeCompletions("TS ", null);
        Assert.assertTrue(completions.contains(new Completion("{string}", Completion.Kind.PLACEHOLDER)));
    }

    @Test
    public void testFromCommaExpectsAdditionalIndexPattern() {
        var completions = Completion.computeCompletions("FROM index1, ", null);
    }

    @Test
    public void testFromCommaCompletesWithIndicesAfterComma() {
        var completions = Completion.computeCompletions("FROM index1, ", STUB_QUERY_MANAGER);
        Assert.assertTrue(completions.contains(new Completion("my-index", Completion.Kind.NAME)));
        Assert.assertTrue(completions.contains(new Completion("logs-2024", Completion.Kind.NAME)));
        Assert.assertTrue(completions.contains(new Completion("{string}", Completion.Kind.PLACEHOLDER)));
    }

    @Test
    public void testLookupJoinExpectsIndexPattern() {
        var completions = Completion.computeCompletions("FROM index | LOOKUP JOIN ", null);
        Assert.assertTrue(completions.contains(new Completion("{string}", Completion.Kind.PLACEHOLDER)));
    }

    @Test
    public void testLookupJoinCompletesWithIndices() {
        var completions = Completion.computeCompletions("FROM my-index | LOOKUP JOIN ", STUB_QUERY_MANAGER);
        Assert.assertTrue(completions.contains(new Completion("my-index", Completion.Kind.NAME)));
        Assert.assertTrue(completions.contains(new Completion("logs-2024", Completion.Kind.NAME)));
        Assert.assertTrue(completions.contains(new Completion("{string}", Completion.Kind.PLACEHOLDER)));
    }

    @Test
    public void testStatsExpectsFieldName() {
        var completions = Completion.computeCompletions("FROM my-index | STATS ", STUB_QUERY_MANAGER);
        Assert.assertTrue(completions.contains(new Completion("{var}", Completion.Kind.PLACEHOLDER)));
        // TODO Fix this
        //Assert.assertFalse(completions.contains(new Completion("field1", Completion.Kind.FIELD)));
    }

    @Test
    public void testKeepCompletesWithFields() {
        assertCompletesFieldNames("FROM my-index | KEEP ");
    }

    @Test
    public void testDropCompletesWithFields() {
        assertCompletesFieldNames("FROM my-index | DROP ");
    }

    @Test
    public void testRenameCompletesWithFields() {
        assertCompletesFieldNames("FROM my-index | RENAME ");
    }

    @Test
    public void testWhereAfterAndCompletesWithFields() {
        assertCompletesFieldNames("FROM my-index | WHERE x > 1 AND ");
    }

    @Test
    public void testWhereAfterOrCompletesWithFields() {
        assertCompletesFieldNames("FROM my-index | WHERE x > 1 OR ");
    }

    @Test
    public void testSortCommaCompletesWithFields() {
        assertCompletesFieldNames("FROM my-index | SORT field1, ");
    }

    @Test
    public void testEvalAssignmentRhsCompletesWithFields() {
        assertCompletesFieldNames("FROM my-index | EVAL x = ");
    }

    @Test
    public void testDissectCompletesWithFields() {
        assertCompletesFieldNames("FROM my-index | DISSECT ");
    }

    @Test
    public void testGrokCompletesWithFields() {
        assertCompletesFieldNames("FROM my-index | GROK ");
    }

    // --- metadataSource rule: additional contexts ---

    @Test
    public void testTsMetadataOptions() {
        var completions = Completion.computeCompletions("TS index METADATA ", null);
        Set<String> texts = completions.stream().map(Completion::text).collect(Collectors.toSet());
        for (String opt : METADATA_OPTIONS) {
            Assert.assertTrue("Missing metadata option: " + opt, texts.contains(opt));
        }
    }

    @Test
    public void testMetadataCommaExpectsMoreOptions() {
        var completions = Completion.computeCompletions("FROM index METADATA _id, ", null);
        Set<String> texts = completions.stream().map(Completion::text).collect(Collectors.toSet());
        for (String opt : METADATA_OPTIONS) {
            Assert.assertTrue("Missing metadata option: " + opt, texts.contains(opt));
        }
    }

    @Test
    public void testAfterMetadataFieldExpectsPipeOrComma() {
        var completions = Completion.computeCompletions("FROM index METADATA _id ", null);
        Assert.assertTrue(completions.contains(new Completion("|", Completion.Kind.PIPE)));
    }
}
