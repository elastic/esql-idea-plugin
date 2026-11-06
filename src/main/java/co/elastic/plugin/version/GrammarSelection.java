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

import co.elastic.plugin.connection.EsqlPluginQueryManager;
import co.elastic.plugin.settings.EsqlPluginSettings;
import com.intellij.openapi.application.ApplicationManager;

import java.util.List;

/**
 * Works out which grammar settings apply right now, from the plugin settings and the active connection.
 */
public final class GrammarSelection {
    // Until a grammar registry exists, 9.5 is the only released grammar and there is no local build.
    private static final List<GrammarVersion> RELEASED = List.of(new GrammarVersion("9.5", "9.5", false));

    private GrammarSelection() {}

    public static Selection current() {
        var app = ApplicationManager.getApplication();
        EsqlPluginSettings settings = app.getService(EsqlPluginSettings.class);
        EsqlPluginQueryManager manager = app.getService(EsqlPluginQueryManager.class);
        return resolve(settings, manager == null ? null : manager.getActiveServerInfo());
    }

    public static Selection resolve(EsqlPluginSettings settings, ServerInfo detected) {
        return VersionResolver.resolve(settings.esqlVersion, DevSyntaxMode.fromSetting(settings.devSyntax),
            detected, RELEASED, null);
    }
}
