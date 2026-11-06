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

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class EsqlUnbalancedTest {

    private static void assertUnbalanced(String query, char c, int line, int column) throws IOException {
        List<EsqlErrorListener.Error> errors = EsqlAnnotator.validate(query);
        Assert.assertEquals(1, errors.size());
        EsqlErrorListener.Error error = errors.get(0);
        Assert.assertTrue(error.message, error.message.contains("unbalanced '" + c + "'"));
        Assert.assertEquals(line, error.line);
        Assert.assertEquals(column, error.charPositionInLine);
        Assert.assertEquals(String.valueOf(c), error.offendingToken.orElseThrow());
    }

    @Test
    public void testStrayParenAfterRow() throws IOException {
        assertUnbalanced("ROW a = 1)", ')', 1, 9);
    }

    @Test
    public void testStrayParenAfterIn() throws IOException {
        assertUnbalanced("FROM a | WHERE x IN (1, 2))", ')', 1, 26);
    }

    @Test
    public void testStrayBracket() throws IOException {
        assertUnbalanced("ROW a = 1]", ']', 1, 9);
    }

    @Test
    public void testStrayParenOnThirdLine() throws IOException {
        assertUnbalanced("FROM a\n| WHERE x > 1\n  | LIMIT 5)", ')', 3, 11);
    }

    @Test
    public void testBalancedQueriesHaveNoErrors() throws IOException {
        Assert.assertTrue(EsqlAnnotator.validate("ROW a = (1 + 2)").isEmpty());
        Assert.assertTrue(EsqlAnnotator.validate("FROM a | WHERE x IN (1, 2)").isEmpty());
    }
}
