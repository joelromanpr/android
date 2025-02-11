/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.pickers

import com.android.tools.adtui.LightCalloutPopup
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.idea.compose.preview.ComposePreviewBundle.message
import com.android.tools.idea.compose.preview.pickers.properties.PsiPropertyItem
import com.android.tools.idea.compose.preview.pickers.properties.PsiPropertyModel
import com.android.tools.idea.compose.preview.pickers.properties.PsiPropertyView
import com.android.tools.idea.compose.preview.pickers.properties.enumsupport.EnumSupportValuesProvider
import com.android.tools.property.panel.api.PropertiesPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import java.awt.Point
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.LayoutFocusTraversalPolicy

internal object PsiPickerManager {

  /**
   * Shows a picker for editing a [PsiPropertyModel]s. The user can modify the model using this dialog.
   */
  fun show(location: Point, model: PsiPropertyModel, valuesProvider: EnumSupportValuesProvider) {
    val tracker = model.tracker
    val disposable = Disposer.newDisposable()
    val onClosedOrCancelled: () -> Unit = {
      Disposer.dispose(disposable)
      tracker.pickerClosed()
      ApplicationManager.getApplication().executeOnPooledThread(tracker::logUsageData)
    }
    val popup = LightCalloutPopup(closedCallback = onClosedOrCancelled, cancelCallBack = onClosedOrCancelled)
    val previewPickerPanel = createPreviewPickerPanel(disposable, popup::close, model, valuesProvider)

    tracker.pickerShown()
    popup.show(previewPickerPanel, null, location)
  }
}

private fun createPreviewPickerPanel(
  disposable: Disposable,
  closePopupCallBack: () -> Unit,
  model: PsiPropertyModel,
  valuesProvider: EnumSupportValuesProvider
): JPanel {
  val propertiesPanel = PropertiesPanel<PsiPropertyItem>(disposable).also { it.addView(PsiPropertyView(model, valuesProvider)) }

  return JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    isOpaque = false
    border = JBUI.Borders.empty(0, 4)
    add(JLabel(message("picker.preview.title")).apply {
      border = JBUI.Borders.empty(8, 0)
    })
    add(JSeparator())
    add(propertiesPanel.component.apply {
      isOpaque = false
      border = JBUI.Borders.empty(0, 0, 8, 0)
    })
    isFocusCycleRoot = true
    isFocusTraversalPolicyProvider = true
    focusTraversalPolicy = LayoutFocusTraversalPolicy()
    registerActionKey(closePopupCallBack, KeyStrokes.ESCAPE, name = "close", condition = JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
  }
}