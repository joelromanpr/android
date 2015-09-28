/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.profiling.view;

import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightToolWindowContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class AnalysisResultsContent implements LightToolWindowContent {
  @Nullable private CapturePanel myCapturePanel;
  @NotNull private JPanel myMainPanel;

  public AnalysisResultsContent() {
    myMainPanel = new JPanel(new BorderLayout());
  }

  @Nullable
  public Icon getIcon() {
    return myCapturePanel != null ? myCapturePanel.getContentsDelegate().getToolIcon() : null;
  }

  @NotNull
  public JComponent getMainPanel() {
    return myMainPanel;
  }

  @NotNull
  public JComponent getFocusComponent() {
    assert myCapturePanel != null;
    return myCapturePanel.getContentsDelegate().getFocusComponent();
  }

  public void update(@Nullable DesignerEditorPanelFacade mainPanel) {
    myMainPanel.removeAll();

    if (mainPanel instanceof CapturePanel && mainPanel != myCapturePanel) {
      myCapturePanel = (CapturePanel)mainPanel;
      myMainPanel.add(myCapturePanel.getContentsDelegate().getComponent(), BorderLayout.CENTER);
    }
    else {
      myCapturePanel = null;
    }
  }

  public boolean canRunAnalysis() {
    return myCapturePanel != null && myCapturePanel.getContentsDelegate().canRunAnalysis();
  }

  public void performAnalysis() {
    if (myCapturePanel != null) {
      myCapturePanel.getContentsDelegate().performAnalysis();
    }
  }

  @Override
  public void dispose() {
  }
}
