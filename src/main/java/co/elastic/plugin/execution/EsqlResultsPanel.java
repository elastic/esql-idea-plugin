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
import co.elastic.plugin.connection.EsqlPluginQueryManagerImpl;
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
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class EsqlResultsPanel extends JPanel {

    private final JBLabel statusLabel;
    private final JBTabbedPane tabbedPane;
    private final ComboBox<String> connectionDropdown;
    private final JButton connectButton;

    private final EsqlPluginSettings settings;
    private final EsqlPluginQueryManagerImpl queryManager;

    private boolean updatingDropdown = false;

    public EsqlResultsPanel() {
        super(new BorderLayout());

        settings = ApplicationManager.getApplication().getService(EsqlPluginSettings.class);
        queryManager = ApplicationManager.getApplication().getService(EsqlPluginQueryManagerImpl.class);

        JPanel topPanel = new JPanel(new BorderLayout());

        JPanel toolbarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        toolbarPanel.setBorder(JBUI.Borders.empty(2, 4));

        connectionDropdown = new ComboBox<>();
        connectionDropdown.setMinimumAndPreferredWidth(200);
        connectionDropdown.setRenderer(new ConnectionListCellRenderer());
        connectionDropdown.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED && !updatingDropdown) {
                String selected = (String) connectionDropdown.getSelectedItem();
                if (selected != null) {
                    settings.activeConnectionName = selected;
                    updateConnectButton();
                    restoreCachedResults();
                }
            }
        });
        toolbarPanel.add(connectionDropdown);

        connectButton = createToolbarButton(AllIcons.Debugger.ThreadStates.Socket, "Connect");
        connectButton.addActionListener(e -> toggleConnection());
        toolbarPanel.add(connectButton);

        JButton addButton = createToolbarButton(AllIcons.General.Add, "Add connection");
        addButton.addActionListener(e -> addConnection());
        toolbarPanel.add(addButton);

        JButton editButton = createToolbarButton(AllIcons.Actions.Edit, "Edit connection");
        editButton.addActionListener(e -> editConnection());
        toolbarPanel.add(editButton);

        JButton removeButton = createToolbarButton(AllIcons.General.Delete, "Remove connection");
        removeButton.addActionListener(e -> removeConnection());
        toolbarPanel.add(removeButton);

        topPanel.add(toolbarPanel, BorderLayout.NORTH);

        statusLabel = new JBLabel("Not connected");
        statusLabel.setBorder(JBUI.Borders.empty(4, 8));
        topPanel.add(statusLabel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);

        tabbedPane = new JBTabbedPane();
        add(tabbedPane, BorderLayout.CENTER);

        refreshDropdown();
        updateConnectButton();
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

    private void updateConnectButton() {
        if (queryManager.isActiveConnectionConnected()) {
            connectButton.setIcon(AllIcons.Actions.OfflineMode);
            connectButton.setToolTipText("Disconnect");
            statusLabel.setText("Connected");
        } else {
            connectButton.setIcon(AllIcons.Debugger.ThreadStates.Socket);
            connectButton.setToolTipText("Connect");
            if (settings.connections.isEmpty()) {
                statusLabel.setText("No connections configured");
            } else {
                statusLabel.setText("Not connected");
            }
        }
    }

    private void toggleConnection() {
        if (queryManager.isActiveConnectionConnected()) {
            queryManager.disconnect();
        } else {
            if (settings.getActiveConnection() == null) {
                Messages.showInfoMessage(
                    "No connection selected. Add one with the + button and select it from the dropdown.",
                    "Connect"
                );
                return;
            }
            queryManager.connect();
        }
        updateConnectButton();
        connectionDropdown.repaint();
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
        connectionDropdown.repaint();
    }

    private void addConnection() {
        EsqlConnectionDialog dialog = new EsqlConnectionDialog(null);
        if (dialog.showAndGet()) {
            EsqlConnection conn = dialog.getConnection();
            settings.addConnection(conn);
            refreshDropdown();
            updateConnectButton();
        }
    }

    private void editConnection() {
        EsqlConnection active = settings.getActiveConnection();
        if (active == null) {
            Messages.showInfoMessage("No connection selected.", "Edit Connection");
            return;
        }
        boolean wasConnected = queryManager.isConnected(active.name);
        if (wasConnected) {
            queryManager.disconnect(active.name);
        }
        EsqlConnectionDialog dialog = new EsqlConnectionDialog(active);
        if (dialog.showAndGet()) {
            EsqlConnection updated = dialog.getConnection();
            settings.updateConnection(active.name, updated);
            refreshDropdown();
        }
        updateConnectButton();
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
            if (queryManager.isConnected(active)) {
                queryManager.disconnect(active);
            }
            queryManager.clearCachedResults(active);
            settings.removeConnection(active);
            refreshDropdown();
            restoreCachedResults();
            updateConnectButton();
        }
    }

    public void showLoading(String query) {
        statusLabel.setText("Executing query...");
    }

    public void updateResults(String query, EsqlQueryResult result, long elapsedMs) {
        queryManager.addCachedResult(query, result, elapsedMs);
        addResultTab(query, result, elapsedMs);
    }

    private void addResultTab(String query, EsqlQueryResult result, long elapsedMs) {
        String tabTitle = truncateQuery(query);
        JPanel tabContent = createResultPanel(result);
        
        tabbedPane.addTab(tabTitle, tabContent);
        int tabIndex = tabbedPane.getTabCount() - 1;
        tabbedPane.setToolTipTextAt(tabIndex, query);
        tabbedPane.setTabComponentAt(tabIndex, createTabHeader(tabTitle));
        tabbedPane.setSelectedIndex(tabIndex);
        
        updateStatusForResult(result, elapsedMs);
    }

    private JPanel createTabHeader(String title) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        header.setOpaque(false);
        
        JLabel titleLabel = new JLabel(title);
        header.add(titleLabel);
        
        JButton closeButton = new JButton(AllIcons.Actions.Close);
        closeButton.setPreferredSize(new Dimension(16, 16));
        closeButton.setContentAreaFilled(false);
        closeButton.setBorderPainted(false);
        closeButton.setFocusPainted(false);
        closeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = tabbedPane.indexOfTabComponent(header);
                if (index >= 0) {
                    closeTab(index);
                }
            }
        });
        header.add(closeButton);
        
        return header;
    }

    private void closeTab(int tabIndex) {
        if (tabIndex >= 0 && tabIndex < tabbedPane.getTabCount()) {
            tabbedPane.removeTabAt(tabIndex);
            queryManager.removeCachedResult(settings.activeConnectionName, tabIndex);
        }
    }

    private String truncateQuery(String query) {
        String normalized = query.replaceAll("\\s+", " ").trim();
        if (normalized.length() > 30) {
            return normalized.substring(0, 27) + "...";
        }
        return normalized;
    }

    private JPanel createResultPanel(EsqlQueryResult result) {
        JPanel panel = new JPanel(new BorderLayout());
        
        if (result.isError()) {
            JPanel errorPanel = new JPanel(new BorderLayout());
            errorPanel.setBorder(JBUI.Borders.empty(8));
            JBLabel errorLabel = new JBLabel();
            errorLabel.setForeground(UIManager.getColor("Label.errorForeground"));
            errorLabel.setText("<html>" + result.error().replace("\n", "<br>") + "</html>");
            errorPanel.add(errorLabel, BorderLayout.CENTER);
            panel.add(errorPanel, BorderLayout.CENTER);

            return panel;
        }

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
        
        JBTable table = new JBTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(true);

        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(150);
        }

        JBScrollPane scrollPane = new JBScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void updateStatusForResult(EsqlQueryResult result, long elapsedMs) {
        if (result.isError()) {
            statusLabel.setText("Query failed");
        } else {
            List<List<Object>> values = result.values();
            List<EsqlQueryResult.Column> columns = result.columns();
            String timeStr = elapsedMs < 1000
                ? elapsedMs + " ms"
                : String.format("%.2f s", elapsedMs / 1000.0);
            statusLabel.setText(values.size() + " rows returned in " + timeStr
                                + "  |  " + columns.size() + " columns");
        }
    }

    private void restoreCachedResults() {
        tabbedPane.removeAll();
        
        List<EsqlPluginQueryManagerImpl.CachedResult> results = queryManager.getCachedResults();
        for (int i = 0; i < results.size(); i++) {
            EsqlPluginQueryManagerImpl.CachedResult cached = results.get(i);
            String tabTitle = truncateQuery(cached.query());
            JPanel tabContent = createResultPanel(cached.result());
            
            tabbedPane.addTab(tabTitle, tabContent);
            tabbedPane.setToolTipTextAt(i, cached.query());
            tabbedPane.setTabComponentAt(i, createTabHeader(tabTitle));
        }
        
        if (!results.isEmpty()) {
            tabbedPane.setSelectedIndex(results.size() - 1);
            EsqlPluginQueryManagerImpl.CachedResult last = results.get(results.size() - 1);
            updateStatusForResult(last.result(), last.elapsedMs());
        }
    }

    public void showError(String errorMessage) {
        statusLabel.setText("Query failed");
    }

    private class ConnectionListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, 
                                                       boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof String connectionName) {
                if (queryManager.isConnected(connectionName)) {
                    label.setIcon(AllIcons.General.InspectionsOK);
                } else {
                    label.setIcon(null);
                }
            }
            
            return label;
        }
    }
}
