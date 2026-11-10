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

import co.elastic.plugin.version.GrammarVersion;
import co.elastic.plugin.version.Selection;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ConcurrentCompletionTest {

    @Test
    public void testConcurrentCompletionsWithAlternatingDevSyntax() throws Exception {
        var version = new GrammarVersion("9.5", "9.5", false);
        var dev = new Selection(version, true, null);
        var release = new Selection(version, false, null);
        var expectedDev = Completion.computeCompletions("FROM index | ", null, dev);
        var expectedRelease = Completion.computeCompletions("FROM index | ", null, release);
        Assert.assertNotEquals(expectedDev, expectedRelease);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 400; i++) {
                final boolean useDev = i % 2 == 0;
                Callable<Boolean> task = () -> {
                    Set<Completion> actual = Completion.computeCompletions("FROM index | ", null,
                        useDev ? dev : release);
                    return actual.equals(useDev ? expectedDev : expectedRelease);
                };
                results.add(pool.submit(task));
            }
            for (Future<Boolean> r : results) {
                Assert.assertTrue(r.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
