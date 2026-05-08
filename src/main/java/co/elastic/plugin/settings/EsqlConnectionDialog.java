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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.plugin.CommonUtils;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class EsqlConnectionDialog extends DialogWrapper {

    private static final int FIELD_COLUMNS = 40;

    private final JTextField nameField = new JTextField(FIELD_COLUMNS);
    private final JTextField urlField = new JTextField(FIELD_COLUMNS);
    private final JPasswordField apiKeyField = new JPasswordField(FIELD_COLUMNS);
    private final ComboBox<String> refreshRateField;
    private final JLabel testResultLabel = new JLabel(" ");
    private final Map<String, Integer> refreshRateMap = new LinkedHashMap<>();

    public EsqlConnectionDialog(@Nullable EsqlConnection existing) {
        super(true);
        setTitle(existing == null ? "Add Connection" : "Edit Connection");

        refreshRateMap.put("10 seconds", 10);
        refreshRateMap.put("1 minute", 60);
        refreshRateMap.put("5 minutes", 300);
        refreshRateMap.put("10 minutes", 600);
        refreshRateField = new ComboBox<>(refreshRateMap.keySet().toArray(new String[0]));

        if (existing != null) {
            nameField.setText(existing.name);
            urlField.setText(existing.serverUrl);
            apiKeyField.setText(existing.apiKey);
            refreshRateField.setSelectedItem(
                refreshRateMap.entrySet().stream()
                    .filter(e -> e.getValue() == existing.refreshInterval)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse("1 minute")
            );
        }

        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JButton testButton = new JButton("Test Connection");
        testButton.addActionListener(e -> testConnection());

        testResultLabel.setBorder(JBUI.Borders.emptyLeft(8));

        JPanel testPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        testPanel.add(testButton);
        testPanel.add(testResultLabel);

        JPanel panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Name:", nameField)
            .addLabeledComponent("URL:", urlField)
            .addLabeledComponent("API key:", apiKeyField)
            .addLabeledComponent("Refresh rate:", refreshRateField)
            .addComponent(testPanel)
            .getPanel();

        panel.setBorder(JBUI.Borders.empty(10));
        return panel;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (nameField.getText().trim().isEmpty()) {
            return new ValidationInfo("Name is required", nameField);
        }
        if (urlField.getText().trim().isEmpty()) {
            return new ValidationInfo("URL is required", urlField);
        }
        if (apiKeyField.getPassword().length == 0) {
            return new ValidationInfo("API key is required", apiKeyField);
        }
        return null;
    }

    public EsqlConnection getConnection() {
        String selectedRate = (String) refreshRateField.getSelectedItem();
        int refreshInterval = selectedRate != null ? refreshRateMap.getOrDefault(selectedRate, 60) : 60;
        return new EsqlConnection(
            nameField.getText().trim(),
            urlField.getText().trim(),
            new String(apiKeyField.getPassword()),
            refreshInterval
        );
    }

    private void testConnection() {
        String serverUrl = urlField.getText().trim();
        String apiKey = new String(apiKeyField.getPassword());

        if (serverUrl.isEmpty() || apiKey.isEmpty()) {
            testResultLabel.setForeground(UIManager.getColor("Label.errorForeground"));
            testResultLabel.setText("URL and API key are required");
            return;
        }

        testResultLabel.setForeground(UIManager.getColor("Label.foreground"));
        testResultLabel.setText("Testing...");

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                try (ElasticsearchClient client = CommonUtils.createClientInstance(serverUrl,apiKey)) {
                    client.ping();
                    return null;
                } catch (ElasticsearchException e) {
                    return "ERR:HTTP" + e.response().status();
                } catch (Exception e) {
                    return "ERR:" + e.getMessage();
                }
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    if (result == null) {
                        testResultLabel.setForeground(new Color(80, 160, 80));
                        testResultLabel.setText("Connected successfully");
                    } else if (result.startsWith("ERR:")) {
                        testResultLabel.setForeground(UIManager.getColor("Label.errorForeground"));
                        testResultLabel.setText(result.substring(4));
                    } else {
                        testResultLabel.setForeground(UIManager.getColor("Label.errorForeground"));
                        testResultLabel.setText(result);
                    }
                } catch (Exception e) {
                    testResultLabel.setForeground(UIManager.getColor("Label.errorForeground"));
                    testResultLabel.setText("Test failed");
                }
            }
        };
        worker.execute();
    }
}
