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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionResolver {
    public static final String AUTO = "auto";
    public static final String LOCAL = "local";

    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.\\d+)?.*$");

    private VersionResolver() {}

    public static Selection resolve(String versionSetting, DevSyntaxMode devMode, ServerInfo detected,
                                    List<GrammarVersion> released, GrammarVersion local) {
        GrammarVersion newest = released.get(released.size() - 1);
        boolean dev = isDevSyntax(devMode, detected);
        String setting = versionSetting == null || versionSetting.isEmpty() ? AUTO : versionSetting;

        if (LOCAL.equals(setting)) {
            if (local != null) {
                return new Selection(local, dev, null);
            }
            return new Selection(newest, dev, "local build not available, using " + newest.id());
        }
        if (!AUTO.equals(setting)) {
            GrammarVersion chosen = find(released, setting);
            if (chosen == null) {
                return new Selection(newest, dev, "unknown version " + setting + ", using " + newest.id());
            }
            return new Selection(chosen, dev, mismatchNote(chosen, detected));
        }
        return autoSelect(detected, released, newest, dev);
    }

    private static Selection autoSelect(ServerInfo detected, List<GrammarVersion> released,
                                        GrammarVersion newest, boolean dev) {
        if (detected == null) {
            return new Selection(newest, dev, "no connection, using " + newest.id());
        }
        // serverless runs ES|QL newer than any release, whatever version number it reports
        if (detected.serverless()) {
            return new Selection(newest, dev, "serverless, using " + newest.id());
        }
        String number = detected.number();
        Matcher m = number == null ? null : VERSION.matcher(number.trim());
        if (m == null || !m.matches()) {
            return new Selection(newest, dev, "version unknown, using " + newest.id());
        }
        int major = Integer.parseInt(m.group(1));
        if (major < 9) {
            GrammarVersion oldest = released.get(0);
            return new Selection(oldest, dev,
                    "server " + number + " is older than " + oldest.id() + ", using " + oldest.id() + " (approximate)");
        }
        GrammarVersion same = major == 9 ? find(released, major + "." + m.group(2)) : null;
        if (same == null) {
            return new Selection(newest, dev,
                    "server " + number + " is newer than supported, using " + newest.id());
        }
        return new Selection(same, dev, "server " + number + ", using " + same.id());
    }

    private static String mismatchNote(GrammarVersion chosen, ServerInfo detected) {
        if (detected == null || detected.number() == null || detected.number().isEmpty()) {
            return null;
        }
        Matcher m = VERSION.matcher(detected.number().trim());
        if (m.matches() && (m.group(1) + "." + m.group(2)).equals(chosen.id())) {
            return null;
        }
        return "server " + detected.number() + ", using " + chosen.id() + " as selected";
    }

    private static GrammarVersion find(List<GrammarVersion> released, String id) {
        for (GrammarVersion v : released) {
            if (v.id().equals(id)) {
                return v;
            }
        }
        return null;
    }

    private static boolean isDevSyntax(DevSyntaxMode mode, ServerInfo detected) {
        return switch (mode == null ? DevSyntaxMode.AUTO : mode) {
            case ON -> true;
            case OFF -> false;
            case AUTO -> detected != null && detected.snapshot();
        };
    }
}
