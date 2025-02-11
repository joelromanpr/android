/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.table;

import static com.android.tools.idea.editors.strings.table.StringResourceTableModel.UNTRANSLATABLE_COLUMN;

import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;

public final class TranslatableRowFilter extends StringResourceTableRowFilter {
  @Override
  public void update(@NotNull Presentation presentation) {
    presentation.setIcon(null);
    presentation.setText("Show Translatable Keys");
  }

  @Override
  public boolean include(@NotNull Entry<? extends StringResourceTableModel, ? extends Integer> entry) {
    return !(boolean)entry.getValue(UNTRANSLATABLE_COLUMN);
  }
}
