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

import co.elastic.plugin.connection.RestQueryExecutor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.text.DefaultHighlighter;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;

public class RestConsolePanel extends JPanel {

    private final JTextArea requestEditor;
    private final JTextArea responseViewer;
    private final JBLabel statusLabel;
    private final JButton executeButton;

    private static final Color HIGHLIGHT_COLOR = new JBColor(
        new Color(232, 242, 255), new Color(50, 53, 56)
    );

    private static final String INITIAL_CONTENT = """
            GET /_cat/indices
            
            """;

    public RestConsolePanel() {
        super(new BorderLayout());

        responseViewer = new JTextArea();
        responseViewer.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        responseViewer.setEditable(false);
        responseViewer.setTabSize(2);
        responseViewer.setMargin(JBUI.insets(8));

        statusLabel = new JBLabel("Ready");
        statusLabel.setBorder(JBUI.Borders.empty(4, 8));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        toolbar.setBorder(JBUI.Borders.empty(2, 4));

        executeButton = new JButton("Execute", AllIcons.Actions.Execute);
        executeButton.setToolTipText("Execute request at cursor (Ctrl+Enter)");
        executeButton.addActionListener(e -> executeRequest());
        toolbar.add(executeButton);

        JButton clearButton = new JButton("Clear Output", AllIcons.Actions.GC);
        clearButton.setToolTipText("Clear response output");
        clearButton.addActionListener(e -> {
            responseViewer.setText("");
            statusLabel.setText("Ready");
            statusLabel.setForeground(JBColor.foreground());
        });
        toolbar.add(clearButton);

        add(toolbar, BorderLayout.NORTH);

        requestEditor = new JTextArea(INITIAL_CONTENT.stripIndent());
        requestEditor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        requestEditor.setTabSize(2);
        requestEditor.setMargin(JBUI.insets(8));

        int modifier = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        requestEditor.getInputMap().put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, modifier), "executeRequest");
        requestEditor.getActionMap().put("executeRequest", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                executeRequest();
            }
        });

        requestEditor.addCaretListener(e -> updateRequestHighlight());

        JPanel requestPanel = new JPanel(new BorderLayout());
        requestPanel.setBorder(BorderFactory.createTitledBorder("Request"));
        requestPanel.add(new JBScrollPane(requestEditor), BorderLayout.CENTER);

        JPanel responsePanel = new JPanel(new BorderLayout());
        responsePanel.setBorder(BorderFactory.createTitledBorder("Response"));
        responsePanel.add(new JBScrollPane(responseViewer), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, requestPanel, responsePanel);
        splitPane.setResizeWeight(0.4);
        add(splitPane, BorderLayout.CENTER);

        add(statusLabel, BorderLayout.SOUTH);

        SwingUtilities.invokeLater(() -> {
            splitPane.setDividerLocation(0.4);
            updateRequestHighlight();
        });
    }

    private void updateRequestHighlight() {
        try {
            int caretPos = requestEditor.getCaretPosition();
            int caretLine = requestEditor.getLineOfOffset(caretPos);

            List<RestQueryExecutor.RequestBlock> blocks =
                RestQueryExecutor.splitRequests(requestEditor.getText());
            RestQueryExecutor.RequestBlock current =
                RestQueryExecutor.findRequestAtLine(blocks, caretLine);

            requestEditor.getHighlighter().removeAllHighlights();

            if (current != null) {
                int startOffset = requestEditor.getLineStartOffset(current.startLine());
                int endLine = Math.min(current.endLine(), requestEditor.getLineCount() - 1);
                int endOffset = requestEditor.getLineEndOffset(endLine);

                requestEditor.getHighlighter().addHighlight(
                    startOffset, endOffset,
                    new DefaultHighlighter.DefaultHighlightPainter(HIGHLIGHT_COLOR)
                );
            }
        } catch (Exception ignored) {}
    }

    private void executeRequest() {
        String text = requestEditor.getText();
        List<RestQueryExecutor.RequestBlock> blocks = RestQueryExecutor.splitRequests(text);
        if (blocks.isEmpty()) {
            responseViewer.setText(
                "No request found.\n\nExpected format:\n  METHOD /path\n  {optional JSON body}\n\n"
                + "Example:\n  GET /_cat/indices\n\n"
                + "  POST /my-index/_doc\n  {\n      \"title\": \"Hello\"\n  }");
            statusLabel.setText("No request found");
            statusLabel.setForeground(JBColor.RED);
            return;
        }

        RestQueryExecutor.RequestBlock block;
        try {
            int caretPos = requestEditor.getCaretPosition();
            int caretLine = requestEditor.getLineOfOffset(caretPos);
            block = RestQueryExecutor.findRequestAtLine(blocks, caretLine);
        } catch (Exception e) {
            block = blocks.getFirst();
        }

        if (block == null) {
            block = blocks.getFirst();
        }

        RestQueryExecutor.ParsedRequest parsed = RestQueryExecutor.parseRequest(block.content());
        if (parsed == null) {
            responseViewer.setText(
                "Could not parse request.\n\nExpected format:\n  METHOD /path\n  {optional JSON body}");
            statusLabel.setText("Parse error");
            statusLabel.setForeground(JBColor.RED);
            return;
        }

        executeButton.setEnabled(false);
        statusLabel.setText("Executing " + parsed.method() + " " + parsed.path() + "...");
        statusLabel.setForeground(JBColor.foreground());
        responseViewer.setText("");

        final RestQueryExecutor.ParsedRequest req = parsed;
        long startTime = System.currentTimeMillis();

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            RestQueryExecutor.RestResult result = RestQueryExecutor.execute(
                req.method(), req.path(), req.body());
            long elapsed = System.currentTimeMillis() - startTime;

            SwingUtilities.invokeLater(() -> {
                executeButton.setEnabled(true);

                if (result.isError()) {
                    responseViewer.setText(result.error());
                    statusLabel.setText("Error");
                    statusLabel.setForeground(JBColor.RED);
                } else {
                    responseViewer.setText(result.body() != null ? result.body() : "");
                    responseViewer.setCaretPosition(0);

                    String timeStr = elapsed < 1000
                        ? elapsed + " ms"
                        : String.format("%.2f s", elapsed / 1000.0);

                    int statusCode = result.statusCode();
                    statusLabel.setText(req.method() + " " + req.path()
                        + "  |  " + statusCode + "  |  " + timeStr);

                    if (statusCode >= 200 && statusCode < 300) {
                        statusLabel.setForeground(
                            new JBColor(new Color(0, 128, 0), new Color(80, 160, 80)));
                    } else if (statusCode >= 400) {
                        statusLabel.setForeground(JBColor.RED);
                    } else {
                        statusLabel.setForeground(JBColor.foreground());
                    }
                }
            });
        });
    }
}
