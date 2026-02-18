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
package co.elastic.plugin.execution;

import co.elastic.plugin.connection.EsqlPluginQueryManager;
import co.elastic.plugin.connection.EsqlQueryResult;
import co.elastic.plugin.settings.EsqlConnection;
import co.elastic.plugin.settings.EsqlConnectionDialog;
import co.elastic.plugin.settings.EsqlPluginSettings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.List;

public class EsqlResultsPanel extends JPanel {

    private final JBTable table;
    private final JBLabel statusLabel;
    private final JPanel errorPanel;
    private final JBLabel errorLabel;
    private final ComboBox<String> connectionDropdown;

    private final EsqlPluginSettings settings;
    private final EsqlPluginQueryManager queryManager;

    private boolean updatingDropdown = false;

    public EsqlResultsPanel() {
        super(new BorderLayout());

        settings = ApplicationManager.getApplication().getService(EsqlPluginSettings.class);
        queryManager = ApplicationManager.getApplication().getService(EsqlPluginQueryManager.class);

        JPanel topPanel = new JPanel(new BorderLayout());

        JPanel toolbarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        toolbarPanel.setBorder(JBUI.Borders.empty(2, 4));

        connectionDropdown = new ComboBox<>();
        connectionDropdown.setMinimumAndPreferredWidth(200);
        connectionDropdown.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED && !updatingDropdown) {
                String selected = (String) connectionDropdown.getSelectedItem();
                if (selected != null) {
                    settings.activeConnectionName = selected;
                    queryManager.startQueryThreadPool();
                }
            }
        });
        toolbarPanel.add(connectionDropdown);

        JButton addButton = createToolbarButton(AllIcons.General.Add, "Add connection");
        addButton.addActionListener(e -> addConnection());
        toolbarPanel.add(addButton);

        JButton editButton = createToolbarButton(AllIcons.Actions.Edit, "Edit connection");
        editButton.addActionListener(e -> editConnection());
        toolbarPanel.add(editButton);

        JButton removeButton = createToolbarButton(AllIcons.General.Remove, "Remove connection");
        removeButton.addActionListener(e -> removeConnection());
        toolbarPanel.add(removeButton);

        topPanel.add(toolbarPanel, BorderLayout.NORTH);

        statusLabel = new JBLabel("No query executed yet");
        statusLabel.setBorder(JBUI.Borders.empty(4, 8));
        topPanel.add(statusLabel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);

        table = new JBTable();
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(true);

        JBScrollPane scrollPane = new JBScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        errorPanel = new JPanel(new BorderLayout());
        errorPanel.setBorder(JBUI.Borders.empty(8));
        errorLabel = new JBLabel();
        errorLabel.setForeground(UIManager.getColor("Label.errorForeground"));
        errorPanel.add(errorLabel, BorderLayout.CENTER);
        errorPanel.setVisible(false);
        add(errorPanel, BorderLayout.SOUTH);

        refreshDropdown();
    }

    private JButton createToolbarButton(Icon icon, String tooltip) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(28, 28));
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        return button;
    }

    private void refreshDropdown() {
        updatingDropdown = true;
        connectionDropdown.removeAllItems();
        for (String name : settings.getConnectionNames()) {
            connectionDropdown.addItem(name);
        }
        if (!settings.activeConnectionName.isEmpty()) {
            connectionDropdown.setSelectedItem(settings.activeConnectionName);
        }
        updatingDropdown = false;
    }

    private void addConnection() {
        EsqlConnectionDialog dialog = new EsqlConnectionDialog(null);
        if (dialog.showAndGet()) {
            EsqlConnection conn = dialog.getConnection();
            settings.addConnection(conn);
            refreshDropdown();
            queryManager.startQueryThreadPool();
        }
    }

    private void editConnection() {
        EsqlConnection active = settings.getActiveConnection();
        if (active == null) {
            Messages.showInfoMessage("No connection selected.", "Edit Connection");
            return;
        }
        EsqlConnectionDialog dialog = new EsqlConnectionDialog(active);
        if (dialog.showAndGet()) {
            EsqlConnection updated = dialog.getConnection();
            settings.updateConnection(active.name, updated);
            refreshDropdown();
            queryManager.startQueryThreadPool();
        }
    }

    private void removeConnection() {
        String active = settings.activeConnectionName;
        if (active.isEmpty()) {
            Messages.showInfoMessage("No connection selected.", "Remove Connection");
            return;
        }
        int result = Messages.showYesNoDialog(
            "Remove connection \"" + active + "\"?",
            "Remove Connection",
            Messages.getQuestionIcon()
        );
        if (result == Messages.YES) {
            settings.removeConnection(active);
            refreshDropdown();
            queryManager.startQueryThreadPool();
        }
    }

    public void showLoading(String query) {
        statusLabel.setText("Executing query...");
        errorPanel.setVisible(false);
        table.setModel(new DefaultTableModel());
    }

    public void updateResults(EsqlQueryResult result, long elapsedMs) {
        if (result.isError()) {
            showError(result.error());
            return;
        }

        errorPanel.setVisible(false);

        List<EsqlQueryResult.Column> columns = result.columns();
        List<List<Object>> values = result.values();

        String[] columnNames = columns.stream()
            .map(c -> c.name() + " (" + c.type() + ")")
            .toArray(String[]::new);

        Object[][] data = new Object[values.size()][columns.size()];
        for (int i = 0; i < values.size(); i++) {
            List<Object> row = values.get(i);
            for (int j = 0; j < row.size(); j++) {
                Object val = row.get(j);
                data[i][j] = val == null ? "<null>" : val;
            }
        }

        DefaultTableModel model = new DefaultTableModel(data, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table.setModel(model);

        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(150);
        }

        String timeStr = elapsedMs < 1000
            ? elapsedMs + " ms"
            : String.format("%.2f s", elapsedMs / 1000.0);
        statusLabel.setText(values.size() + " rows returned in " + timeStr
                            + "  |  " + columns.size() + " columns");
    }

    public void showError(String errorMessage) {
        statusLabel.setText("Query failed");
        errorLabel.setText("<html>" + errorMessage.replace("\n", "<br>") + "</html>");
        errorPanel.setVisible(true);
        table.setModel(new DefaultTableModel());
    }
}
