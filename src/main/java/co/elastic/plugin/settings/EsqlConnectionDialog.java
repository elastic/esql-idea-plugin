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

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
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

    private final @Nullable EsqlConnection existing;

    public EsqlConnectionDialog(@Nullable EsqlConnection existing) {
        super(true);
        this.existing = existing;
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
                try {
                    HttpClient httpClient = createHttpClient(serverUrl);
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverUrl))
                        .header("Authorization", "ApiKey " + apiKey)
                        .GET()
                        .build();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() < 400) {
                        return "OK:" + response.statusCode();
                    }
                    return "ERR:HTTP " + response.statusCode();
                } catch (Exception e) {
                    return "ERR:" + e.getMessage();
                }
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    if (result.startsWith("OK:")) {
                        testResultLabel.setForeground(new Color(80, 160, 80));
                        testResultLabel.setText("Connected successfully");
                    } else {
                        testResultLabel.setForeground(UIManager.getColor("Label.errorForeground"));
                        testResultLabel.setText(result.substring(4));
                    }
                } catch (Exception e) {
                    testResultLabel.setForeground(UIManager.getColor("Label.errorForeground"));
                    testResultLabel.setText("Test failed");
                }
            }
        };
        worker.execute();
    }

    private static HttpClient createHttpClient(String serverUrl) {
        try {
            if (serverUrl.startsWith("https://")) {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }}, new SecureRandom());
                return HttpClient.newBuilder().sslContext(sslContext).build();
            }
        } catch (Exception ignored) {
        }
        return HttpClient.newHttpClient();
    }
}
