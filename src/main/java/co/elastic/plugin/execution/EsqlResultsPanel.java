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
import co.elastic.plugin.connection.EsqlQueryResult;
import co.elastic.plugin.settings.EsqlPluginSettings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class EsqlResultsPanel extends JPanel {

    private final ConnectionToolbar connectionToolbar;
    private final JBTabbedPane tabbedPane;

    private final EsqlPluginSettings settings;
    private final EsqlPluginQueryManagerImpl queryManager;

    public EsqlResultsPanel() {
        super(new BorderLayout());

        settings = ApplicationManager.getApplication().getService(EsqlPluginSettings.class);
        queryManager = ApplicationManager.getApplication().getService(EsqlPluginQueryManagerImpl.class);

        connectionToolbar = new ConnectionToolbar();
        connectionToolbar.setConnectionChangeListener(this::restoreCachedResults);
        add(connectionToolbar, BorderLayout.NORTH);

        tabbedPane = new JBTabbedPane();
        add(tabbedPane, BorderLayout.CENTER);
    }

    public void showLoading(String query) {
        connectionToolbar.getStatusLabel().setText("Executing query...");
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
        JBLabel statusLabel = connectionToolbar.getStatusLabel();
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
        connectionToolbar.getStatusLabel().setText("Query failed");
    }
}
