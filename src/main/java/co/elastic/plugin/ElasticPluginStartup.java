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
package co.elastic.plugin;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static co.elastic.plugin.CommonUtils.ESQL_COMMENT;

public class ElasticPluginStartup implements ProjectActivity {

    @Override
    public @Nullable Object execute(@NotNull Project project,
                                    @NotNull Continuation<? super Unit> continuation) {
        PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
            @Override
            public void childReplaced(@NotNull PsiTreeChangeEvent event) {
                restartIfCommentChanged(project, event);
            }

            @Override
            public void childAdded(@NotNull PsiTreeChangeEvent event) {
                restartIfCommentChanged(project, event);
            }

            @Override
            public void childRemoved(@NotNull PsiTreeChangeEvent event) {
                restartIfCommentChanged(project, event);
            }
        }, project);
        return null;
    }

    private static void restartIfCommentChanged(@NotNull Project project,
                                                 @NotNull PsiTreeChangeEvent event) {
        if (event.getChild() instanceof PsiComment comment
            && comment.getText().contains(ESQL_COMMENT)
            && event.getFile() != null) {
            DaemonCodeAnalyzer.getInstance(project).restart(event.getFile());
        }
    }
}
