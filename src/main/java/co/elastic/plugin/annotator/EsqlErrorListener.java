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

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.LexerNoViableAltException;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.List;
import java.util.Optional;

public class EsqlErrorListener extends BaseErrorListener {

    static class Error {
        String message;
        int line;
        int charPositionInLine;
        Optional<String> offendingToken;

        public Error(String message, int line, int charPositionInLine, Optional<String> offendingToken) {
            this.message = message;
            this.line = line;
            this.charPositionInLine = charPositionInLine;
            this.offendingToken = offendingToken;
        }
    }

    List<Error> errors;

    EsqlErrorListener(List<Error> errors) {
        this.errors = errors;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer,
                            Object offendingSymbol,
                            int line,
                            int charPositionInLine,
                            String msg,
                            RecognitionException e) {
        // Lexer exceptions are different and don't have offending token details
        errors.add(new Error(msg, line, charPositionInLine,
            Optional.ofNullable(e)
                .filter(ex -> !(ex instanceof LexerNoViableAltException))
                .map(ex -> ex.getOffendingToken().getText())));
    }

}
