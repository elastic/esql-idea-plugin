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
package co.elastic.plugin.version;

import co.elastic.clients.elasticsearch.core.InfoResponse;
import org.junit.Assert;
import org.junit.Test;

public class ServerInfosTest {

    @Test
    public void testFromInfoReadsNumberAndSnapshotFlag() {
        InfoResponse response = InfoResponse.of(r -> r
            .clusterName("c").clusterUuid("u").name("n").tagline("t")
            .version(v -> v
                .number("9.6.0-SNAPSHOT").buildSnapshot(true)
                .buildFlavor("default").buildType("tar").buildHash("h")
                .buildDate(co.elastic.clients.util.DateTime.ofEpochMilli(0))
                .luceneVersion("10.0.0")
                .minimumIndexCompatibilityVersion("8.0.0")
                .minimumWireCompatibilityVersion("8.0.0")));
        Assert.assertEquals(new ServerInfo("9.6.0-SNAPSHOT", true, false), ServerInfos.fromInfo(response));
    }

    @Test
    public void testFromInfoReadsServerlessBuildFlavor() {
        InfoResponse response = InfoResponse.of(r -> r
            .clusterName("c").clusterUuid("u").name("n").tagline("t")
            .version(v -> v
                .number("8.11.0").buildSnapshot(false)
                .buildFlavor("serverless").buildType("docker").buildHash("h")
                .buildDate(co.elastic.clients.util.DateTime.ofEpochMilli(0))
                .luceneVersion("9.7.0")
                .minimumIndexCompatibilityVersion("8.11.0")
                .minimumWireCompatibilityVersion("8.11.0")));
        Assert.assertEquals(new ServerInfo("8.11.0", false, true), ServerInfos.fromInfo(response));
    }

    @Test
    public void testFromInfoWithNullVersionReturnsNull() {
        Assert.assertNull(ServerInfos.fromInfo(null));
    }

    private static final ServerInfo KNOWN = new ServerInfo("9.3.4", false, false);

    @Test
    public void testFailedRefreshKeepsAKnownVersion() {
        Assert.assertSame(KNOWN, ServerInfos.next(KNOWN, null));
        Assert.assertFalse(ServerInfos.needsRestart(KNOWN, ServerInfos.next(KNOWN, null)));
    }

    @Test
    public void testFailedFirstRefreshGivesTheUnknownState() {
        Assert.assertEquals(ServerInfos.UNKNOWN, ServerInfos.next(null, null));
        Assert.assertNull(ServerInfos.UNKNOWN.number());
        Assert.assertTrue(ServerInfos.needsRestart(null, ServerInfos.UNKNOWN));
        // an earlier failure is not a known version either
        Assert.assertEquals(ServerInfos.UNKNOWN, ServerInfos.next(ServerInfos.UNKNOWN, null));
    }

    @Test
    public void testSuccessfulRefreshReplacesThePreviousValue() {
        ServerInfo upgraded = new ServerInfo("9.4.0", false, false);
        Assert.assertEquals(upgraded, ServerInfos.next(KNOWN, upgraded));
        Assert.assertTrue(ServerInfos.needsRestart(KNOWN, upgraded));
        Assert.assertEquals(KNOWN, ServerInfos.next(ServerInfos.UNKNOWN, KNOWN));
        Assert.assertTrue(ServerInfos.needsRestart(ServerInfos.UNKNOWN, KNOWN));
    }

    @Test
    public void testUnchangedValueNeedsNoRestart() {
        ServerInfo same = new ServerInfo("9.3.4", false, false);
        Assert.assertFalse(ServerInfos.needsRestart(KNOWN, ServerInfos.next(KNOWN, same)));
    }
}
