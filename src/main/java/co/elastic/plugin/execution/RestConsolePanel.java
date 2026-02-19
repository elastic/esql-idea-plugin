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
import co.elastic.plugin.rest.ElasticsearchRestFileType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;

public class RestConsolePanel extends JPanel implements Disposable {

    private final Editor requestEditor;
    private final Editor responseEditor;
    private final JBLabel statusLabel;
    private final JButton executeButton;
    private RangeHighlighter requestHighlighter;

    private static final Color HIGHLIGHT_COLOR = new JBColor(
        new Color(232, 242, 255), new Color(50, 53, 56)
    );

    private static final String INITIAL_CONTENT = "GET /_cat/indices\n\n";

    public RestConsolePanel(Project project) {
        super(new BorderLayout());

        // Request editor: IntelliJ Editor backed by custom ElasticsearchREST FileType for PSI/completion support
        LightVirtualFile requestFile = new LightVirtualFile(
            "request.es-rest", ElasticsearchRestFileType.INSTANCE, INITIAL_CONTENT
        );
        Document requestDocument = FileDocumentManager.getInstance().getDocument(requestFile);
        if (requestDocument == null) {
            requestDocument = EditorFactory.getInstance().createDocument(INITIAL_CONTENT);
        }
        requestEditor = EditorFactory.getInstance().createEditor(requestDocument, project, requestFile, false);

        requestEditor.getSettings().setLineNumbersShown(false);
        requestEditor.getSettings().setFoldingOutlineShown(false);
        requestEditor.getSettings().setLineMarkerAreaShown(false);
        requestEditor.getSettings().setGutterIconsShown(false);
        requestEditor.getSettings().setAdditionalLinesCount(1);
        requestEditor.getSettings().setUseSoftWraps(false);

        requestEditor.getCaretModel().addCaretListener(new CaretListener() {
            @Override
            public void caretPositionChanged(@SuppressWarnings("NotNullFieldNotInitialized") CaretEvent e) {
                updateRequestHighlight();
            }
        });

        int cmdCtrl = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        AnAction executeAction = new AnAction() {
            @Override
            public void actionPerformed(@org.jetbrains.annotations.NotNull AnActionEvent e) {
                executeRequest();
            }
        };
        executeAction.registerCustomShortcutSet(
            new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, cmdCtrl | KeyEvent.SHIFT_DOWN_MASK)),
            requestEditor.getComponent(), this
        );

        // Response editor: PSI-backed JSON editor for syntax highlighting, brace matching, and Ctrl+F search
        FileType jsonFileType = FileTypeManager.getInstance().getFileTypeByExtension("json");
        LightVirtualFile responseFile = new LightVirtualFile("response.json", jsonFileType, "");
        Document responseDocument = FileDocumentManager.getInstance().getDocument(responseFile);
        if (responseDocument == null) {
            responseDocument = EditorFactory.getInstance().createDocument("");
        }
        responseEditor = EditorFactory.getInstance().createEditor(responseDocument, project, responseFile, true);

        responseEditor.getSettings().setLineNumbersShown(false);
        responseEditor.getSettings().setFoldingOutlineShown(true);
        responseEditor.getSettings().setLineMarkerAreaShown(false);
        responseEditor.getSettings().setIndentGuidesShown(true);
        responseEditor.getSettings().setUseSoftWraps(true);
        responseEditor.getSettings().setGutterIconsShown(false);
        responseEditor.getSettings().setAdditionalLinesCount(0);
        responseEditor.getSettings().setAdditionalColumnsCount(0);

        applyJqColorScheme(responseEditor);

        statusLabel = new JBLabel("Ready");
        statusLabel.setBorder(JBUI.Borders.empty(4, 8));

        ConnectionToolbar connectionToolbar = new ConnectionToolbar();

        JPanel executeToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        executeToolbar.setBorder(JBUI.Borders.empty(2, 4));

        executeButton = new JButton("Execute", AllIcons.Actions.Execute);
        executeButton.setToolTipText("Execute request at cursor (Ctrl+Shift+Enter / Cmd+Shift+Enter)");
        executeButton.addActionListener(e -> executeRequest());
        executeToolbar.add(executeButton);

        JButton clearButton = new JButton("Clear Output", AllIcons.Actions.GC);
        clearButton.setToolTipText("Clear response output");
        clearButton.addActionListener(e -> {
            setResponseText("");
            statusLabel.setText("Ready");
            statusLabel.setForeground(JBColor.foreground());
        });
        executeToolbar.add(clearButton);

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(connectionToolbar, BorderLayout.NORTH);
        northPanel.add(executeToolbar, BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);

        JPanel requestPanel = new JPanel(new BorderLayout());
        requestPanel.setBorder(BorderFactory.createTitledBorder("Request"));
        requestPanel.add(requestEditor.getComponent(), BorderLayout.CENTER);

        JPanel responsePanel = new JPanel(new BorderLayout());
        responsePanel.setBorder(BorderFactory.createTitledBorder("Response"));
        responsePanel.add(responseEditor.getComponent(), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, requestPanel, responsePanel);
        splitPane.setResizeWeight(0.4);
        add(splitPane, BorderLayout.CENTER);

        add(statusLabel, BorderLayout.SOUTH);

        SwingUtilities.invokeLater(() -> {
            splitPane.setDividerLocation(0.4);
            updateRequestHighlight();
        });
    }

    @Override
    public void dispose() {
        EditorFactory.getInstance().releaseEditor(requestEditor);
        EditorFactory.getInstance().releaseEditor(responseEditor);
    }

    private void setResponseText(String text) {
        ApplicationManager.getApplication().runWriteAction(() ->
            responseEditor.getDocument().setText(text)
        );
    }

    private void scrollResponseToTop() {
        ApplicationManager.getApplication().runReadAction(() -> {
            responseEditor.getCaretModel().moveToOffset(0);
            responseEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        });
    }

    private static void applyJqColorScheme(Editor editor) {
        EditorColorsScheme scheme = editor.getColorsScheme();

        TextAttributes keyAttrs = new TextAttributes();
        keyAttrs.setForegroundColor(new JBColor(
            new Color(0x04, 0x51, 0xA5), new Color(0x56, 0x9C, 0xD6)
        ));
        keyAttrs.setFontType(Font.BOLD);
        scheme.setAttributes(TextAttributesKey.createTextAttributesKey("JSON.PROPERTY_KEY"), keyAttrs);

        TextAttributes stringAttrs = new TextAttributes();
        stringAttrs.setForegroundColor(new JBColor(
            new Color(0x09, 0x86, 0x58), new Color(0x6A, 0x99, 0x55)
        ));
        scheme.setAttributes(TextAttributesKey.createTextAttributesKey("JSON.STRING"), stringAttrs);
    }

    private void updateRequestHighlight() {
        if (requestHighlighter != null) {
            requestEditor.getMarkupModel().removeHighlighter(requestHighlighter);
            requestHighlighter = null;
        }

        int startOffset = -1;
        int endOffset = -1;
        try {
            Document doc = requestEditor.getDocument();
            int caretOffset = ApplicationManager.getApplication().runReadAction(
                (com.intellij.openapi.util.Computable<Integer>) () -> requestEditor.getCaretModel().getOffset()
            );
            int caretLine = doc.getLineNumber(caretOffset);

            List<RestQueryExecutor.RequestBlock> blocks =
                RestQueryExecutor.splitRequests(doc.getText());
            RestQueryExecutor.RequestBlock current =
                RestQueryExecutor.findRequestAtLine(blocks, caretLine);

            if (current != null) {
                startOffset = doc.getLineStartOffset(current.startLine());
                int endLine = Math.min(current.endLine(), doc.getLineCount() - 1);
                endOffset = doc.getLineEndOffset(endLine);
            }
        } catch (Exception ignored) {}

        if (startOffset >= 0 && endOffset > startOffset) {
            TextAttributes attrs = new TextAttributes();
            attrs.setBackgroundColor(HIGHLIGHT_COLOR);
            requestHighlighter = requestEditor.getMarkupModel().addRangeHighlighter(
                startOffset, endOffset,
                HighlighterLayer.SELECTION - 1,
                attrs,
                HighlighterTargetArea.LINES_IN_RANGE
            );
        }
    }

    private void executeRequest() {
        Document doc = requestEditor.getDocument();
        String text = doc.getText();
        List<RestQueryExecutor.RequestBlock> blocks = RestQueryExecutor.splitRequests(text);
        if (blocks.isEmpty()) {
            setResponseText(
                "No request found.\n\nExpected format:\n  METHOD /path\n  {optional JSON body}\n\n"
                + "Example:\n  GET /_cat/indices\n\n"
                + "  POST /my-index/_doc\n  {\n      \"title\": \"Hello\"\n  }");
            statusLabel.setText("No request found");
            statusLabel.setForeground(JBColor.RED);
            return;
        }

        RestQueryExecutor.RequestBlock block;
        try {
            int caretOffset = ApplicationManager.getApplication().runReadAction(
                (com.intellij.openapi.util.Computable<Integer>) () -> requestEditor.getCaretModel().getOffset()
            );
            int caretLine = doc.getLineNumber(caretOffset);
            block = RestQueryExecutor.findRequestAtLine(blocks, caretLine);
        } catch (Exception e) {
            block = blocks.getFirst();
        }

        if (block == null) {
            block = blocks.getFirst();
        }

        RestQueryExecutor.ParsedRequest parsed = RestQueryExecutor.parseRequest(block.content());
        if (parsed == null) {
            setResponseText(
                "Could not parse request.\n\nExpected format:\n  METHOD /path\n  {optional JSON body}");
            statusLabel.setText("Parse error");
            statusLabel.setForeground(JBColor.RED);
            return;
        }

        executeButton.setEnabled(false);
        statusLabel.setText("Executing " + parsed.method() + " " + parsed.path() + "...");
        statusLabel.setForeground(JBColor.foreground());
        setResponseText("");

        final RestQueryExecutor.ParsedRequest req = parsed;
        long startTime = System.currentTimeMillis();

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            RestQueryExecutor.RestResult result = RestQueryExecutor.execute(
                req.method(), req.path(), req.body());
            long elapsed = System.currentTimeMillis() - startTime;

            SwingUtilities.invokeLater(() -> {
                executeButton.setEnabled(true);

                if (result.isError()) {
                    setResponseText(result.error());
                    statusLabel.setText("Error");
                    statusLabel.setForeground(JBColor.RED);
                } else {
                    setResponseText(result.body() != null ? result.body() : "");
                    scrollResponseToTop();

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
