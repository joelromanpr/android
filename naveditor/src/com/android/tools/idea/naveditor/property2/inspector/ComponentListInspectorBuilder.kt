/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property2.inspector

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.model.isDestination
import com.android.tools.idea.naveditor.property2.ui.ComponentList
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SortedListModel

/**
 * Adds a ComponentList component to an [InspectorPanel] to display groups of subtags in a list format within an expandable title.
 * Assumes that the currently selected component is a destination.
 * Parameters:
 * [tagName]: the tag name of the child elements to be displayed
 * [title]: the caption for the expandable title
 * [cellRenderer]: the cell renderer to be used for the list items
 */
open class ComponentListInspectorBuilder(val tagName: String, val title: String, val cellRenderer: ColoredListCellRenderer<NlComponent>)
  : InspectorBuilder<NelePropertyItem> {
  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    val component = properties.first?.components?.singleOrNull() ?: return
    if (!component.isDestination) {
      return
    }

    val model = SortedListModel<NlComponent>(compareBy { it.id })
    model.addAll(component.children.filter { it.tagName == tagName })

    val titleModel = inspector.addExpandableTitle(title, model.size > 0);
    inspector.addComponent(ComponentList(model, cellRenderer), titleModel)
  }
}