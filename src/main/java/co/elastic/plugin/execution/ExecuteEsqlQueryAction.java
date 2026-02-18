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

import co.elastic.plugin.connection.EsqlQueryExecutor;
import co.elastic.plugin.connection.EsqlQueryResult;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ExecuteEsqlQueryAction extends AnAction {

    private final Project project;
    private final String queryText;

    public ExecuteEsqlQueryAction(@NotNull Project project, @NotNull String queryText) {
        super("Execute ES|QL Query");
        this.project = project;
        this.queryText = queryText;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        EsqlResultsToolWindowFactory.showLoading(project, queryText);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Executing ES|QL Query", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                long startTime = System.currentTimeMillis();
                EsqlQueryResult result = EsqlQueryExecutor.execute(queryText);
                long elapsedMs = System.currentTimeMillis() - startTime;
                EsqlResultsToolWindowFactory.showResults(project, queryText, result, elapsedMs);
            }
        });
    }
}
