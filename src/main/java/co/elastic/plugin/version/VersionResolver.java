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
            return new Selection(newest, dev, using("local build not available", newest));
        }
        if (!AUTO.equals(setting)) {
            GrammarVersion chosen = find(released, setting);
            if (chosen == null) {
                return new Selection(newest, dev, using("unknown version " + setting, newest));
            }
            return new Selection(chosen, dev, mismatchNote(chosen, detected));
        }
        return autoSelect(detected, released, newest, dev);
    }

    private static Selection autoSelect(ServerInfo detected, List<GrammarVersion> released,
                                        GrammarVersion newest, boolean dev) {
        if (detected == null) {
            return new Selection(newest, dev, using("no connection", newest));
        }
        // serverless runs ES|QL newer than any release, whatever version number it reports
        if (detected.serverless()) {
            return new Selection(newest, dev, using("serverless", newest));
        }
        String number = detected.number();
        MajorMinor parsed = parseMajorMinor(number);
        if (parsed == null) {
            return new Selection(newest, dev, using("version unknown", newest));
        }
        int major = Integer.parseInt(parsed.major());
        if (major < 9) {
            GrammarVersion oldest = released.get(0);
            return new Selection(oldest, dev,
                    using("server " + number + " is older than " + oldest.id(), oldest) + " (approximate)");
        }
        GrammarVersion same = major == 9 ? find(released, major + "." + parsed.minor()) : null;
        if (same == null) {
            return new Selection(newest, dev,
                    using("server " + number + " is newer than supported", newest));
        }
        return new Selection(same, dev, using("server " + number, same));
    }

    private static String mismatchNote(GrammarVersion chosen, ServerInfo detected) {
        if (detected == null || detected.number() == null || detected.number().isEmpty()) {
            return null;
        }
        MajorMinor parsed = parseMajorMinor(detected.number());
        if (parsed != null && (parsed.major() + "." + parsed.minor()).equals(chosen.id())) {
            return null;
        }
        return using("server " + detected.number(), chosen) + " as selected";
    }

    /** The major and minor digits of a version number such as "9.4.0-rc1", as written. */
    private record MajorMinor(String major, String minor) {}

    /** The major and minor of a version number, or null when it is null or not a version number. */
    private static MajorMinor parseMajorMinor(String number) {
        if (number == null) {
            return null;
        }
        Matcher m = VERSION.matcher(number.trim());
        return m.matches() ? new MajorMinor(m.group(1), m.group(2)) : null;
    }

    /** A note that says why a version was chosen, such as "no connection, using 9.5". */
    private static String using(String reason, GrammarVersion version) {
        return reason + ", using " + version.id();
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
