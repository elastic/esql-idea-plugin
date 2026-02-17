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
import co.elastic.grammar.EsqlBaseParser;
import co.elastic.grammar.EsqlConfig;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;


public class Parser {
    public record ParserInfo(EsqlBaseParser parser, List<Token> tokens) {}

    public static Parser.ParserInfo parse(String query) {
        EsqlConfig config = new EsqlConfig(false);
        EsqlBaseLexer lexer = new EsqlBaseLexer(CharStreams.fromString(query));
        lexer.removeErrorListeners();
        lexer.setEsqlConfig(config);

        CommonTokenStream tokenStream = new CommonTokenStream(lexer);

        // Fill the token stream (i.e. tokenize the input)
        tokenStream.fill();
        var tokens = new ArrayList<>(tokenStream.getTokens());
        // Remove EOF
        tokens.removeLast();
        EsqlBaseParser parser = new EsqlBaseParser(tokenStream);
        parser.removeErrorListeners();
        parser.setEsqlConfig(config);

        return new ParserInfo(parser, tokens);
    }
}
