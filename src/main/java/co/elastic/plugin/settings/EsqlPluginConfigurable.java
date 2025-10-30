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
package co.elastic.plugin.settings;

import co.elastic.plugin.EsqlIcon;
import co.elastic.plugin.connection.EsqlPluginQueryManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class EsqlPluginConfigurable implements SearchableConfigurable {
    private JPanel mainPanel;
    private JTextField urlField;
    private JPasswordField apiKeyField;
    private ComboBox refreshRateField;

    private final Map<String, Integer> refreshRateMap = new HashMap<>();

    EsqlPluginSettings state = ApplicationManager.getApplication().getService(EsqlPluginSettings.class);
    EsqlPluginQueryManager queryManager =
        ApplicationManager.getApplication().getService(EsqlPluginQueryManager.class);

    @Override
    public String getDisplayName() {
        return "ES|QL Plugin Settings";
    }

    @Override
    public JComponent createComponent() {
        refreshRateMap.put("10 seconds", 10);
        refreshRateMap.put("1 minute", 60);
        refreshRateMap.put("5 minutes", 300);
        refreshRateMap.put("10 minutes", 600);

        urlField = new JTextField();

        apiKeyField = new JPasswordField();

        refreshRateField = new ComboBox<>(refreshRateMap.keySet().toArray());
        refreshRateField.
            setSelectedItem(refreshRateMap.entrySet().stream()
                .filter(entry -> entry.getValue() == state.getRefreshInterval())
                .map(Map.Entry::getKey)
                .findFirst().get());


        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("URL", urlField)
            .addLabeledComponent("API key", apiKeyField)
            .addLabeledComponent("Refresh rate", refreshRateField)
            .getPanel();

        JPanel outerPanel = new JPanel(new BorderLayout());
        outerPanel.add(mainPanel, BorderLayout.NORTH);

        return outerPanel;
    }

    @Override
    public boolean isModified() {
        boolean refreshRateChanged = Optional.ofNullable(refreshRateField.getSelectedItem())
                                         .map(x -> refreshRateMap.get(x.toString()))
                                         .orElse(60) != state.getRefreshInterval();
        return !urlField.getText().equals(state.serverUrl) || !apiKeyField.getText().equals(state.apiKey)
               || refreshRateChanged;
    }

    @Override
    public void apply() {
        state.setServerUrl(urlField.getText());
        state.setApiKey(new String(apiKeyField.getPassword()));
        // defaulting to 1 minute
        int refreshRate = Optional.ofNullable(refreshRateField.getSelectedItem())
            .map(x -> refreshRateMap.get(x.toString()))
            .orElse(60);
        state.setRefreshInterval(refreshRate);

        queryManager.startQueryThreadPool();
    }

    @Override
    public void reset() {
        urlField.setText(state.serverUrl);
        apiKeyField.setText(state.apiKey);
        refreshRateField.setSelectedItem(state.getRefreshInterval());
    }

    public Icon getIcon() {
        return EsqlIcon.ESQL_ICON;
    }

    @Override
    public @NotNull @NonNls String getId() {
        return "ES|QL Plugin Settings";
    }
}
