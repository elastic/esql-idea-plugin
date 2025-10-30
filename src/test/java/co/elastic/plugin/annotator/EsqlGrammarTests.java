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
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class EsqlGrammarTests {

    @Test
    public void testParserLexer() throws IOException {

        String wheeQuery = "FROM ul_logs, apps METADATA _index, _version\n" +
            "| WHEE id IN (13, 14) AND _version == 1\n" +
            "| EVAL key = CONCAT(_index, \"_\", TO_STR(id)) |\n";

        InputStream stream = new ByteArrayInputStream(wheeQuery.getBytes(StandardCharsets.UTF_8));
        EsqlBaseLexer lexer = new EsqlBaseLexer(CharStreams.fromStream(stream, StandardCharsets.UTF_8));
        EsqlBaseParser parser = new EsqlBaseParser(new CommonTokenStream(lexer));

        parser.setBuildParseTree(false);
        EsqlErrorListener errorListener = new EsqlErrorListener(new ArrayList<>());
        parser.addErrorListener(errorListener);

        parser.singleStatement();

        Assert.assertEquals(2,errorListener.errors.size());
        Assert.assertTrue(errorListener.errors.get(0).message.contains("mismatched input 'WHEE'"));
        Assert.assertTrue(errorListener.errors.get(1).message.contains("mismatched input '<EOF>'"));
    }
}
