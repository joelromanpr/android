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
package com.android.tools.idea.uibuilder.editor

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.flags.StudioFlags
import org.intellij.lang.annotations.Language
import org.jetbrains.android.AndroidTestCase

class DesignFilesPreviewEditorProviderTest : AndroidTestCase() {

  private lateinit var provider : DesignFilesPreviewEditorProvider

  override fun setUp() {
    super.setUp()
    StudioFlags.NELE_SPLIT_EDITOR.override(true)
    provider = DesignFilesPreviewEditorProvider()
  }

  fun testDoNotAcceptNonResourceFile() {
    val file = myFixture.addFileToProject("src/SomeFile.kt", "")
    assertFalse(provider.accept(project, file.virtualFile))
  }

  fun testDoNotAcceptNavigationFile() {
    val file = myFixture.addFileToProject("res/navigation/my_nav.xml", generateContent(SdkConstants.TAG_NAVIGATION))
    assertFalse(provider.accept(project, file.virtualFile))
  }

  fun testDoNotAcceptLayoutFile() {
    val file = myFixture.addFileToProject("res/layout/my_layout.xml", generateContent(SdkConstants.LINEAR_LAYOUT))
    assertFalse(provider.accept(project, file.virtualFile))
  }

  fun testAcceptAdaptiveIcon() {
    val file = myFixture.addFileToProject("res/mipmap/my_icon.xml", generateContent(SdkConstants.TAG_ADAPTIVE_ICON))
    assertTrue(provider.accept(project, file.virtualFile))
  }

  fun testAcceptSelector() {
    val file = myFixture.addFileToProject("res/drawable/my_selector.xml", generateContent(SdkConstants.TAG_SELECTOR))
    assertTrue(provider.accept(project, file.virtualFile))
  }

  fun testAcceptVector() {
    val file = myFixture.addFileToProject("res/drawable/my_vector.xml", generateContent(SdkConstants.TAG_ANIMATED_VECTOR))
    assertTrue(provider.accept(project, file.virtualFile))
  }

  fun testAcceptFont() {
    val file = myFixture.addFileToProject("res/font/my_font.xml", generateContent(SdkConstants.TAG_FONT_FAMILY))
    assertTrue(provider.accept(project, file.virtualFile))
  }

  fun testAcceptDrawable() {
    val file = myFixture.addFileToProject("res/drawable/my_gradient.xml", generateContent(SdkConstants.TAG_GRADIENT))
    assertTrue(provider.accept(project, file.virtualFile))
  }

  fun testOnlyAcceptWhenSplitEditorIsEnabled() {
    StudioFlags.NELE_SPLIT_EDITOR.override(false)
    provider = DesignFilesPreviewEditorProvider()

    val file = myFixture.addFileToProject("res/drawable/my_gradient.xml", generateContent(SdkConstants.TAG_GRADIENT))
    assertFalse(provider.accept(project, file.virtualFile))
  }

  override fun tearDown() {
    super.tearDown()
    StudioFlags.NELE_SPLIT_EDITOR.clearOverride()
  }

  @Language("XML")
  private fun generateContent(rootTag: String): String {
    val content = ComponentDescriptor(rootTag)
    val sb = StringBuilder()
    content.appendXml(sb, 0)
    return sb.toString()
  }
}
