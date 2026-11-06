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

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class VersionResolverTest {

    private static final List<GrammarVersion> RELEASED = new ArrayList<>();

    static {
        for (int minor = 0; minor <= 5; minor++) {
            String id = "9." + minor;
            RELEASED.add(new GrammarVersion(id, id, false));
        }
    }

    private static final GrammarVersion LOCAL = new GrammarVersion("local", "Local build", true);

    private static Selection auto(ServerInfo detected) {
        return VersionResolver.resolve(VersionResolver.AUTO, DevSyntaxMode.AUTO, detected, RELEASED, null);
    }

    @Test
    public void testAutoUsesSameMinorOfDetectedVersion() {
        Selection s = auto(new ServerInfo("9.3.4", false, false));
        Assert.assertEquals("9.3", s.version().id());
        Assert.assertTrue(s.note().contains("9.3.4"));
    }

    @Test
    public void testSpecificVersionWinsAndMismatchIsExplained() {
        Selection s = VersionResolver.resolve("9.3", DevSyntaxMode.AUTO, new ServerInfo("9.4.1", false, false),
                RELEASED, null);
        Assert.assertEquals("9.3", s.version().id());
        Assert.assertTrue(s.note().contains("9.4.1"));
    }

    @Test
    public void testOlderThanNineUsesNineZeroWithApproximateNote() {
        Selection s = auto(new ServerInfo("8.19.0", false, false));
        Assert.assertEquals("9.0", s.version().id());
        Assert.assertTrue(s.note().contains("approximate"));
    }

    @Test
    public void testNewerThanNewestUsesNewest() {
        Assert.assertEquals("9.5", auto(new ServerInfo("9.9.0", false, false)).version().id());
    }

    @Test
    public void testAutoOnServerlessUsesNewestWhateverNumberItReports() {
        for (String number : new String[] {"8.11.0", "9.4.0", "9.5.0", null}) {
            Selection s = auto(new ServerInfo(number, false, true));
            Assert.assertEquals("9.5", s.version().id());
            Assert.assertEquals("serverless, using 9.5", s.note());
        }
    }

    @Test
    public void testSpecificVersionWinsOnServerless() {
        Selection s = VersionResolver.resolve("9.3", DevSyntaxMode.AUTO, new ServerInfo("9.4.0", false, true),
                RELEASED, null);
        Assert.assertEquals("9.3", s.version().id());
    }

    @Test
    public void testNoConnectionUsesNewest() {
        Selection s = auto(null);
        Assert.assertEquals("9.5", s.version().id());
        Assert.assertTrue(s.note().contains("9.5"));
    }

    @Test
    public void testUnparseableVersionUsesUnknownNote() {
        for (String number : new String[] {"abc", "", null}) {
            Selection s = auto(new ServerInfo(number, false, false));
            Assert.assertEquals("9.5", s.version().id());
            Assert.assertEquals("version unknown, using 9.5", s.note());
        }
    }

    @Test
    public void testSnapshotAndPrereleaseSuffixesAreIgnored() {
        Assert.assertEquals("9.4", auto(new ServerInfo("9.4.0-rc1", false, false)).version().id());
        Assert.assertEquals("9.5", auto(new ServerInfo("9.6.0-SNAPSHOT", true, false)).version().id());
    }

    @Test
    public void testUnknownSavedSettingFallsBackToNewest() {
        Selection s = VersionResolver.resolve("9.7", DevSyntaxMode.AUTO, null, RELEASED, null);
        Assert.assertEquals("9.5", s.version().id());
        Assert.assertTrue(s.note().contains("9.7"));
    }

    @Test
    public void testLocalBuildNeverChosenByAuto() {
        Selection s = VersionResolver.resolve(VersionResolver.AUTO, DevSyntaxMode.AUTO,
                new ServerInfo("9.6.0-SNAPSHOT", true, false), RELEASED, LOCAL);
        Assert.assertFalse(s.version().local());
    }

    @Test
    public void testLocalBuildChosenWhenSelected() {
        Selection s = VersionResolver.resolve("local", DevSyntaxMode.AUTO, null, RELEASED, LOCAL);
        Assert.assertEquals("local", s.version().id());
        Assert.assertTrue(s.version().local());
    }

    @Test
    public void testMissingLocalSettingFallsBackToNewest() {
        Selection s = VersionResolver.resolve("local", DevSyntaxMode.AUTO, null, RELEASED, null);
        Assert.assertEquals("9.5", s.version().id());
        Assert.assertTrue(s.note().contains("local"));
    }

    private static boolean dev(DevSyntaxMode mode, ServerInfo detected) {
        return VersionResolver.resolve(VersionResolver.AUTO, mode, detected, RELEASED, null).devSyntax();
    }

    @Test
    public void testDevSyntaxAuto() {
        Assert.assertTrue(dev(DevSyntaxMode.AUTO, new ServerInfo("9.6.0-SNAPSHOT", true, false)));
        Assert.assertFalse(dev(DevSyntaxMode.AUTO, new ServerInfo("9.5.0", false, false)));
        Assert.assertFalse(dev(DevSyntaxMode.AUTO, null));
    }

    @Test
    public void testDevSyntaxOnOffAlwaysWin() {
        ServerInfo[] all = {
            null, new ServerInfo("9.6.0-SNAPSHOT", true, false), new ServerInfo("9.5.0", false, false),
            new ServerInfo("abc", false, false)
        };
        for (ServerInfo info : all) {
            Assert.assertTrue(dev(DevSyntaxMode.ON, info));
            Assert.assertFalse(dev(DevSyntaxMode.OFF, info));
        }
    }

    @Test
    public void testFromSetting() {
        Assert.assertEquals(DevSyntaxMode.ON, DevSyntaxMode.fromSetting("ON"));
        Assert.assertEquals(DevSyntaxMode.AUTO, DevSyntaxMode.fromSetting(null));
        Assert.assertEquals(DevSyntaxMode.AUTO, DevSyntaxMode.fromSetting("bogus"));
    }
}
