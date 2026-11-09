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
import co.elastic.grammar.EsqlBaseParserBaseListener;
import co.elastic.grammar.EsqlConfig;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that the syntax added or promoted in the ES 9.5 grammar parses, and that dev-only syntax stays gated.
 * The example queries come from the Elasticsearch csv-spec and parser tests.
 */
public class EsqlGrammar95Tests {

    private record SyntaxError(int line, int charPositionInLine, String message) {
        @Override
        public String toString() {
            return line + ":" + charPositionInLine + " " + message;
        }
    }

    private static List<SyntaxError> syntaxErrors(String query, boolean isDevVersion) {
        List<SyntaxError> errors = new ArrayList<>();
        BaseErrorListener collector = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                    int charPositionInLine, String msg, RecognitionException e) {
                errors.add(new SyntaxError(line, charPositionInLine, msg));
            }
        };

        EsqlConfig config = new EsqlConfig(isDevVersion);
        EsqlBaseLexer lexer = new EsqlBaseLexer(CharStreams.fromString(query));
        lexer.removeErrorListeners();
        lexer.addErrorListener(collector);
        lexer.setEsqlConfig(config);

        EsqlBaseParser parser = new EsqlBaseParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(collector);
        parser.setEsqlConfig(config);
        parser.setBuildParseTree(false);
        parser.singleStatement();
        return errors;
    }

    private static void assertParses(List<String> queries, boolean isDevVersion) {
        List<String> failures = new ArrayList<>();
        for (String query : queries) {
            List<SyntaxError> errors = syntaxErrors(query, isDevVersion);
            if (errors.isEmpty() == false) {
                failures.add(query + "\n    " + errors);
            }
        }
        Assert.assertTrue("Queries failed to parse (dev=" + isDevVersion + "):\n" + String.join("\n", failures),
            failures.isEmpty());
    }

    private static void assertParsesInBothModes(List<String> queries) {
        assertParses(queries, false);
        assertParses(queries, true);
    }

    private static void assertFirstError(String query, int line, int charPositionInLine) {
        List<SyntaxError> errors = syntaxErrors(query, false);
        Assert.assertFalse(errors.isEmpty());
        Assert.assertEquals(line, errors.getFirst().line());
        Assert.assertEquals(charPositionInLine, errors.getFirst().charPositionInLine());
    }

    private static final List<String> NEW_COMMANDS = List.of(
        "FROM test | URI_PARTS parts = last_name | STATS scheme_count = COUNT() BY parts.scheme | LIMIT 10",
        "FROM test | REGISTERED_DOMAIN rd = last_name | STATS c = COUNT() BY rd.registered_domain | LIMIT 10",
        "FROM test | USER_AGENT ua = input",
        "FROM test | IP_LOCATION g = ip WITH { \"properties\": [\"country_iso_code\", \"city_name\"] } | KEEP g.*",
        "TS k8s | METRICS_INFO | KEEP metric_name, metric_type | SORT metric_name",
        "TS k8s | TS_INFO | SORT metric_name, dimensions",
        "PROMQL index=empty_index start=\"2025-01-01T00:00:00Z\" end=\"2025-01-01T00:02:00Z\" step=1m "
            + "result=(1 + 1) | TS_COLLAPSE",
        "FROM test | MMR ON dense_embedding LIMIT 10",
        "FROM test | LIMIT 100 | MMR published_date ON vector LIMIT 10"
    );

    private static final List<String> SUBQUERIES = List.of(
        "FROM (FROM employees | WHERE salary > 100000)",
        "FROM (ROW emp_no = 99999, languages = 99)",
        "FROM (TS \"k8s\" | WHERE @timestamp <= \"2024-05-10T00:01:00.000Z\")",
        "FROM employees, (ROW emp_no = 99999)"
    );

    private static final List<String> BY_CLAUSES = List.of(
        "FROM colors | LIMIT 1 BY color",
        "FROM colors | LIMIT 2 BY color, shade",
        "FROM test | CHANGE_POINT value ON k BY grp",
        "FROM test | CHANGE_POINT value ON k BY region, service"
    );

    private static final List<String> IN_EXPRESSIONS = List.of(
        "FROM employees | WHERE emp_no IN (13, 14)",
        "FROM employees | WHERE name IN (\"a\", \"b\")",
        "FROM employees | WHERE emp_no IN (FROM employees | SORT emp_no ASC | LIMIT 3 | KEEP emp_no)",
        "FROM employees | WHERE emp_no NOT IN (FROM employees | WHERE salary < 10000 | KEEP emp_no)",
        "FROM employees | WHERE emp_no IN (FROM employees, (ROW emp_no = 99999) | KEEP emp_no)",
        // comments and newlines between the opening parenthesis and the keyword
        "FROM employees | WHERE emp_no IN ( /* subquery */\n  FROM employees | KEEP emp_no)",
        "FROM employees\n| WHERE emp_no IN (\n    // pick a few\n    ROW emp_no = 1)",
        // a parenthesised expression that is not a subquery must still lex as a plain expression
        "FROM employees | WHERE (emp_no + 1) IN (1, 2) AND (salary > 10)"
    );

    @Test
    public void testNewCommandsParse() {
        assertParsesInBothModes(NEW_COMMANDS);
    }

    @Test
    public void testSubqueriesParse() {
        assertParsesInBothModes(SUBQUERIES);
    }

    @Test
    public void testByClausesParse() {
        assertParsesInBothModes(BY_CLAUSES);
    }

    @Test
    public void testInExpressionsParse() {
        assertParsesInBothModes(IN_EXPRESSIONS);
    }

    @Test
    public void testMultipleMetadataFieldsParse() {
        assertParses(List.of("FROM a METADATA _id, _index, _version"), false);
    }

    @Test
    public void testDevOnlySyntaxIsGated() {
        List<String> devOnly = List.of(
            "FROM foo | DEDUP",
            "FROM foo | HIGHLIGHT \"elasticsearch\" ON title WITH { \"fragment_size\": 150, \"number_of_fragments\": 2 }",
            "EXTERNAL \"s3://bucket/table\""
        );

        assertParses(devOnly, true);
        for (String query : devOnly) {
            Assert.assertFalse("Expected a syntax error outside dev mode: " + query,
                syntaxErrors(query, false).isEmpty());
        }
    }

    /**
     * The lexer rewinds after looking ahead for a subquery after IN (. The error position of tokens that follow
     * must be unaffected by that.
     */
    @Test
    public void testErrorPositionAfterInSubquery() {
        String sameLine = "FROM a | WHERE x IN (FROM b | KEEP x) WHEE y";
        assertFirstError(sameLine, 1, sameLine.indexOf("WHEE"));

        assertFirstError("FROM a\n| WHERE x IN (\n  FROM b | KEEP x)\n| WHEE y", 4, 2);
    }

    /**
     * The plugin's grammar splits METADATA fields into a metadataSource rule, which autocomplete relies on. It
     * is a local change, so a sync from Elasticsearch that drops it should fail here with a clear reason.
     */
    @Test
    public void testMetadataFieldsUseMetadataSourceRule() {
        EsqlBaseLexer lexer = new EsqlBaseLexer(CharStreams.fromString("FROM a METADATA _id, _index"));
        EsqlBaseParser parser = new EsqlBaseParser(new CommonTokenStream(lexer));

        List<String> sources = new ArrayList<>();
        ParseTreeWalker.DEFAULT.walk(new EsqlBaseParserBaseListener() {
            @Override
            public void exitMetadataSource(EsqlBaseParser.MetadataSourceContext ctx) {
                sources.add(ctx.getText());
            }
        }, parser.singleStatement());

        Assert.assertEquals(List.of("_id", "_index"), sources);
    }
}
