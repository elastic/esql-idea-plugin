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
package co.elastic.plugin.settings;

import co.elastic.plugin.EditorRefresh;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.Locale;

/**
 * Settings page under Tools | ES|QL.
 */
public class EsqlSettingsConfigurable implements Configurable {
    private static final String[] DEV_SYNTAX_OPTIONS = {"Auto", "On", "Off"};

    private ComboBox<String> devSyntax;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "ES|QL";
    }

    @Override
    public @Nullable JComponent createComponent() {
        devSyntax = new ComboBox<>(DEV_SYNTAX_OPTIONS);
        JPanel panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Dev-only syntax:", devSyntax)
            .addComponentToRightColumn(new JBLabel(
                "Auto turns it on only when the active connection is a snapshot build."))
            .addComponentFillVertically(new JPanel(), 0)
            .getPanel();
        reset();
        return panel;
    }

    private static EsqlPluginSettings settings() {
        return ApplicationManager.getApplication().getService(EsqlPluginSettings.class);
    }

    private static String toOption(String stored) {
        for (String option : DEV_SYNTAX_OPTIONS) {
            if (option.equalsIgnoreCase(stored)) {
                return option;
            }
        }
        return DEV_SYNTAX_OPTIONS[0];
    }

    @Override
    public boolean isModified() {
        return devSyntax != null && !toOption(settings().devSyntax).equals(devSyntax.getSelectedItem());
    }

    @Override
    public void apply() {
        if (devSyntax == null) {
            return;
        }
        settings().devSyntax = ((String) devSyntax.getSelectedItem()).toLowerCase(Locale.ROOT);
        EditorRefresh.restartAll();
    }

    @Override
    public void reset() {
        if (devSyntax != null) {
            devSyntax.setSelectedItem(toOption(settings().devSyntax));
        }
    }

    @Override
    public void disposeUIResources() {
        devSyntax = null;
    }
}
