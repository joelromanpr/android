/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.property.ptable2.impl

import org.jdesktop.swingx.plaf.basic.core.BasicTransferable
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import javax.swing.JComponent
import javax.swing.TransferHandler

class PTableTransferHandler : TransferHandler() {

  /**
   * Create a Transferable to use as the source for a data transfer.
   */
  override fun createTransferable(component: JComponent?): Transferable? {
    val table = component as? PTableImpl ?: return null
    val rows = table.selectedRows
    if (rows.isEmpty()) {
      return null
    }
    val plainStr = StringBuilder()
    val htmlStr = StringBuilder()
    htmlStr.append("<html>\n<body>\n<table>\n")
    for (row in rows) {
      htmlStr.append("<tr>\n")
      val item = table.item(row)
      plainStr.append("${item.name}\t${item.value.orEmpty()}\n")
      htmlStr.append("  <td>${item.name}</td><td>${item.value.orEmpty()}</td>\n")
      htmlStr.append("</tr>\n")
    }

    // remove the last newline
    plainStr.deleteCharAt(plainStr.length - 1)
    htmlStr.append("</table>\n</body>\n</html>")
    return BasicTransferable(plainStr.toString(), htmlStr.toString())
  }

  override fun exportDone(component: JComponent?, data: Transferable?, action: Int) {
    val table = component as? PTableImpl ?: return
    val rows = table.selectedRows
    if (rows.isEmpty() || action != MOVE) {
      return
    }
    for (row in rows.reversedArray()) {
      val item = table.item(row)
      table.tableModel.removeItem(item)
    }
  }

  override fun importData(component: JComponent?, data: Transferable): Boolean {
    val table = component as? PTableImpl ?: return false
    val model = table.tableModel
    if (!model.supportsInsertableItems()) {
      return false
    }
    val stringValue = data.getTransferData(DataFlavor.stringFlavor) as? String ?: return false
    val lines = stringValue.split('\n')
    for (line in lines) {
      val pair = line.split('\t', limit = 2)
      if (pair.size == 2) {
        model.addItem(pair.first(), pair.last())
      }
    }
    return true
  }

  override fun getSourceActions(component: JComponent): Int {
    val table = component as? PTableImpl ?: return NONE
    val model = table.tableModel
    var actions = COPY
    if (model.supportsRemovableItems()) {
      actions = actions or MOVE
    }
    return actions
  }
}
