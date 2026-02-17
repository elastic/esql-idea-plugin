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

import co.elastic.plugin.execution.ExecuteEsqlQueryAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public final class EsqlIcon extends GutterIconRenderer {

    public static final Icon ESQL_ICON = IconLoader.getIcon("/META-INF/elasticsearch.svg", EsqlIcon.class);

    private final Project project;
    private final String queryText;

    public EsqlIcon(@NotNull Project project, @NotNull String queryText) {
        this.project = project;
        this.queryText = queryText;
    }

    @Override
    public @NotNull Icon getIcon() {
        return ESQL_ICON;
    }

    @Override
    public @Nullable String getTooltipText() {
        return "Execute ES|QL Query";
    }

    @Override
    public @Nullable AnAction getClickAction() {
        return new ExecuteEsqlQueryAction(project, queryText);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EsqlIcon that = (EsqlIcon) o;
        return Objects.equals(queryText, that.queryText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queryText);
    }
}
