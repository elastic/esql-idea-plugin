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

import co.elastic.plugin.connection.EsqlQueryResult;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class EsqlResultsPanel extends JPanel {

    private final JBTable table;
    private final JBLabel statusLabel;
    private final JPanel errorPanel;
    private final JBLabel errorLabel;

    public EsqlResultsPanel() {
        super(new BorderLayout());

        statusLabel = new JBLabel("No query executed yet");
        statusLabel.setBorder(JBUI.Borders.empty(4, 8));
        add(statusLabel, BorderLayout.NORTH);

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
