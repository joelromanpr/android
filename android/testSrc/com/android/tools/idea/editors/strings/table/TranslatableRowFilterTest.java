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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.swing.RowFilter;
import javax.swing.RowFilter.Entry;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public final class TranslatableRowFilterTest {
  private RowFilter<StringResourceTableModel, Integer> myFilter;

  @Before
  public void initFilter() {
    myFilter = new TranslatableRowFilter();
  }

  @Test
  public void untranslatableIsFalse() {
    assertTrue(myFilter.include(mockEntry(false)));
  }

  @Test
  public void untranslatableIsTrue() {
    assertFalse(myFilter.include(mockEntry(true)));
  }

  @NotNull
  private static Entry<StringResourceTableModel, Integer> mockEntry(boolean untranslatable) {
    // noinspection unchecked
    Entry<StringResourceTableModel, Integer> entry = Mockito.mock(Entry.class);
    Mockito.when(entry.getValue(StringResourceTableModel.UNTRANSLATABLE_COLUMN)).thenReturn(untranslatable);

    return entry;
  }
}
