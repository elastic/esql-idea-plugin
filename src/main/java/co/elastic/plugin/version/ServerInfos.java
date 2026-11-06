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

import java.util.Objects;

public final class ServerInfos {

    private ServerInfos() {
    }

    /** The build flavor of Elasticsearch serverless projects. */
    static final String SERVERLESS_FLAVOR = "serverless";

    /**
     * Reads the server version, snapshot flag and serverless build flavor from an info response.
     * Returns null when the response or its version is missing.
     */
    public static ServerInfo fromInfo(InfoResponse response) {
        if (response == null || response.version() == null) {
            return null;
        }
        var version = response.version();
        return new ServerInfo(version.number(), version.buildSnapshot(),
            SERVERLESS_FLAVOR.equals(version.buildFlavor()));
    }

    /** Connected, but the version is unknown. This resolves to the newest version with a "version unknown" note. */
    public static final ServerInfo UNKNOWN = new ServerInfo(null, false, false);

    /**
     * The server info to keep after a refresh. {@code detected} is null if the refresh could not read the
     * version. Then the version from an earlier refresh stays, so a short failure does not change the grammar.
     * If there is none, the result is {@link #UNKNOWN}.
     */
    public static ServerInfo next(ServerInfo previous, ServerInfo detected) {
        if (detected != null) {
            return detected;
        }
        return previous != null && previous.number() != null ? previous : UNKNOWN;
    }

    /** Whether the server info changed, so editors need a refresh. */
    public static boolean needsRestart(ServerInfo previous, ServerInfo next) {
        return !Objects.equals(previous, next);
    }
}
