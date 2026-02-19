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

import co.elastic.plugin.connection.EsqlPluginQueryManagerImpl;
import co.elastic.plugin.settings.EsqlConnection;
import co.elastic.plugin.settings.EsqlConnectionDialog;
import co.elastic.plugin.settings.EsqlPluginSettings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionToolbar extends JPanel {

    public interface ConnectionChangeListener {
        void onConnectionChanged();
    }

    private static final Set<ConnectionToolbar> ALL_INSTANCES = ConcurrentHashMap.newKeySet();

    private final ComboBox<String> connectionDropdown;
    private final JButton connectButton;
    private final JBLabel statusLabel;
    private final EsqlPluginSettings settings;
    private final EsqlPluginQueryManagerImpl queryManager;
    private ConnectionChangeListener changeListener;
    private boolean updatingDropdown = false;

    public ConnectionToolbar() {
        super(new BorderLayout());

        ALL_INSTANCES.add(this);

        settings = ApplicationManager.getApplication().getService(EsqlPluginSettings.class);
        queryManager = ApplicationManager.getApplication().getService(EsqlPluginQueryManagerImpl.class);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        buttonsPanel.setBorder(JBUI.Borders.empty(2, 4));

        connectionDropdown = new ComboBox<>();
        connectionDropdown.setMinimumAndPreferredWidth(200);
        connectionDropdown.setRenderer(new ConnectionListCellRenderer());
        connectionDropdown.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED && !updatingDropdown) {
                String selected = (String) connectionDropdown.getSelectedItem();
                if (selected != null) {
                    settings.activeConnectionName = selected;
                    updateConnectButton();
                    notifyChanged();
                    refreshOtherInstances();
                }
            }
        });
        buttonsPanel.add(connectionDropdown);

        connectButton = createToolbarButton(AllIcons.Debugger.ThreadStates.Socket, "Connect");
        connectButton.addActionListener(e -> toggleConnection());
        buttonsPanel.add(connectButton);

        JButton addButton = createToolbarButton(AllIcons.General.Add, "Add connection");
        addButton.addActionListener(e -> addConnection());
        buttonsPanel.add(addButton);

        JButton editButton = createToolbarButton(AllIcons.Actions.Edit, "Edit connection");
        editButton.addActionListener(e -> editConnection());
        buttonsPanel.add(editButton);

        JButton removeButton = createToolbarButton(AllIcons.General.Delete, "Remove connection");
        removeButton.addActionListener(e -> removeConnection());
        buttonsPanel.add(removeButton);

        add(buttonsPanel, BorderLayout.NORTH);

        statusLabel = new JBLabel("Not connected");
        statusLabel.setBorder(JBUI.Borders.empty(4, 8));
        add(statusLabel, BorderLayout.SOUTH);

        refreshDropdown();
        updateConnectButton();
    }

    public void setConnectionChangeListener(ConnectionChangeListener listener) {
        this.changeListener = listener;
    }

    public JBLabel getStatusLabel() {
        return statusLabel;
    }

    public void refresh() {
        refreshDropdown();
        updateConnectButton();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        ALL_INSTANCES.add(this);
        refresh();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        ALL_INSTANCES.remove(this);
    }

    private void refreshOtherInstances() {
        for (ConnectionToolbar instance : ALL_INSTANCES) {
            if (instance != this) {
                instance.refresh();
            }
        }
    }

    private void notifyChanged() {
        if (changeListener != null) {
            changeListener.onConnectionChanged();
        }
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
        refreshOtherInstances();
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
            notifyChanged();
            refreshOtherInstances();
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
        refreshOtherInstances();
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
            updateConnectButton();
            notifyChanged();
            refreshOtherInstances();
        }
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
