/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.structure.configurables.dependencies.details;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.PsBaseDependency;
import com.android.tools.idea.gradle.structure.model.PsDeclaredModuleDependency;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsModuleDependency;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.HyperlinkEvent;
import org.jdesktop.swingx.JXLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModuleDependencyDetails implements DependencyDetails {
  @NotNull private final PsContext myContext;
  private final boolean myShowScope;

  private JPanel myMainPanel;
  private JXLabel myNameLabel;
  private JXLabel myGradlePathLabel;
  private JBLabel myScopePromptLabel;
  private HyperlinkLabel myGoToLabel;
  private JComboBox<String> myScope;

  private PsModuleDependency myDependency;

  private boolean comboMaintenance = false;

  public ModuleDependencyDetails(@NotNull PsContext context, boolean showScope) {
    myContext = context;
    myShowScope = showScope;
    myScopePromptLabel.setVisible(showScope);
    myScope.setVisible(showScope);

    myGoToLabel.setHyperlinkText("See Dependencies");
    myGoToLabel.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        assert myDependency != null;
        myContext.getMainConfigurable().navigateTo(
          myContext
            .getProject()
            .findModuleByGradlePath(myDependency.getGradlePath())
            .getPath()
            .getDependenciesPath()
            .getPlaceDestination(myContext),
          true);
      }
    });
  }

  @Override
  @NotNull
  public JPanel getPanel() {
    return myMainPanel;
  }

  @Override
  public void display(@NotNull PsBaseDependency dependency) {
    PsModuleDependency d = (PsModuleDependency) dependency;
    myNameLabel.setText(d.getName());
    myGradlePathLabel.setText(d.getGradlePath());
    if (myShowScope) {
      displayConfiguration(d);
    }
    myDependency = d;
  }

  public void displayConfiguration(@NotNull PsModuleDependency dependency) {
    if (dependency != myDependency) {
      try {
        comboMaintenance = true;
        myScope.removeAllItems();
        String configuration = dependency.getJoinedConfigurationNames();
        myScope.addItem(configuration);
        for (String c : dependency.getParent().getConfigurations(PsModule.ImportantFor.MODULE)) {
          if (c != configuration) myScope.addItem(c);
        }
        myScope.setSelectedItem(configuration);
      } finally {
        comboMaintenance = false;
      }
    }
  }

  @Override
  @NotNull
  public Class<PsModuleDependency> getSupportedModelType() {
    return PsModuleDependency.class;
  }

  @Override
  @Nullable
  public PsModuleDependency getModel() {
    return myDependency;
  }

  private void modifyConfiguration() {
    if (myDependency != null && myScope.getSelectedItem() != null) {
      String selectedConfiguration = (String) myScope.getSelectedItem();
      if (selectedConfiguration != null) {
        PsModule module = myDependency.getParent();
        module.modifyDependencyConfiguration((PsDeclaredModuleDependency) myDependency, selectedConfiguration);
      }
    }
  }

  private void createUIComponents() {
    myScope = new ComboBox<String>();
    myScope.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!comboMaintenance) {
          modifyConfiguration();
        }
      }
    });
  }
}
