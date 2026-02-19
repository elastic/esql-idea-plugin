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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EsqlResultsToolWindowFactory implements com.intellij.openapi.wm.ToolWindowFactory {

    public static final String TOOL_WINDOW_ID = "Elasticsearch";

    private static final Map<Project, EsqlResultsPanel> panels = new ConcurrentHashMap<>();

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        EsqlResultsPanel esqlPanel = new EsqlResultsPanel();
        panels.put(project, esqlPanel);

        RestConsolePanel restConsolePanel = new RestConsolePanel(project);

        ContentFactory contentFactory = ContentFactory.getInstance();

        Content esqlContent = contentFactory.createContent(esqlPanel, "ES|QL", false);
        toolWindow.getContentManager().addContent(esqlContent);

        Content restContent = contentFactory.createContent(restConsolePanel, "REST Console", false);
        toolWindow.getContentManager().addContent(restContent);
    }

    public static void showLoading(@NotNull Project project, String query) {
        ApplicationManager.getApplication().invokeLater(() -> {
            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
            if (toolWindow != null) {
                toolWindow.show();
                selectEsqlTab(toolWindow);
                EsqlResultsPanel panel = panels.get(project);
                if (panel != null) {
                    panel.showLoading(query);
                }
            }
        });
    }

    public static void showResults(@NotNull Project project, @NotNull String query, @NotNull EsqlQueryResult result, long elapsedMs) {
        ApplicationManager.getApplication().invokeLater(() -> {
            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
            if (toolWindow != null) {
                toolWindow.show();
                selectEsqlTab(toolWindow);
                EsqlResultsPanel panel = panels.get(project);
                if (panel != null) {
                    panel.updateResults(query, result, elapsedMs);
                }
            }
        });
    }

    private static void selectEsqlTab(ToolWindow toolWindow) {
        Content esqlContent = toolWindow.getContentManager().findContent("ES|QL");
        if (esqlContent != null) {
            toolWindow.getContentManager().setSelectedContent(esqlContent);
        }
    }
}
