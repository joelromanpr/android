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
package com.android.tools.idea.databinding.util

import com.android.SdkConstants
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.databinding.index.BindingXmlIndex
import com.android.tools.idea.databinding.index.ViewIdData
import com.android.tools.idea.databinding.util.DataBindingUtil.getQualifiedBindingName
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.util.androidFacet
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiType
import com.intellij.util.IncorrectOperationException
import org.jetbrains.android.facet.AndroidFacet

object LayoutBindingTypeUtil {
  private val VIEW_PACKAGE_ELEMENTS = listOf(
    SdkConstants.VIEW, SdkConstants.VIEW_GROUP, SdkConstants.TEXTURE_VIEW, SdkConstants.SURFACE_VIEW
  )

  /**
   * Creates a [PsiType] for the target [typeStr], returning null instead of throwing an exception if it was not possible to create it for
   * some reason. [typeStr] can be a fully qualified class name, an array type or a primitive type.
   */
  @JvmStatic
  fun parsePsiType(typeStr: String, context: PsiElement): PsiType? {
    return try {
      PsiElementFactory.getInstance(context.project).createTypeFromText(typeStr, context)
    }
    catch (e: IncorrectOperationException) {
      // Class named "text" not found.
      null
    }
  }

  /**
   * Convert a view tag (e.g. &lt;TextView... /&gt;) to its PSI type, if possible, or return `null` otherwise.
   */
  @JvmStatic
  fun resolveViewPsiType(viewIdData: ViewIdData, context: PsiElement): PsiType? {
    val androidFacet = context.androidFacet ?: return null
    val viewClassName = getViewClassName(viewIdData, androidFacet) ?: return null
    return if (viewClassName.isNotEmpty()) PsiType.getTypeByName(viewClassName, context.project, context.resolveScope) else null
  }

  /**
   * Receives a [ViewIdData] and returns the name of the View class that is implied by it. May return null if it cannot find anything
   * reasonable (e.g. it is a merge but does not have data binding)
   */
  private fun getViewClassName(viewIdData: ViewIdData, facet: AndroidFacet): String? {
    val viewName = viewIdData.viewName
    if (viewName.indexOf('.') == -1) {
      when {
        VIEW_PACKAGE_ELEMENTS.contains(viewName) -> return SdkConstants.VIEW_PKG_PREFIX + viewName
        SdkConstants.WEB_VIEW == viewName -> return SdkConstants.ANDROID_WEBKIT_PKG + viewName
        SdkConstants.VIEW_MERGE == viewName -> return getViewClassNameFromMergeTag(viewIdData, facet)
        SdkConstants.VIEW_INCLUDE == viewName -> return getViewClassNameFromIncludeTag(viewIdData, facet)
        SdkConstants.VIEW_STUB == viewName -> {
          val mode = DataBindingUtil.getDataBindingMode(facet)
          return mode.viewStubProxy
        }
        else -> return SdkConstants.WIDGET_PKG_PREFIX + viewName
      }
    }
    else {
      return viewName
    }
  }

  private fun getViewClassNameFromIncludeTag(viewIdData: ViewIdData, facet: AndroidFacet): String {
    val reference = getViewClassNameFromLayoutAttribute(viewIdData.layoutName, facet)
    return reference ?: SdkConstants.CLASS_VIEW
  }

  private fun getViewClassNameFromMergeTag(viewIdData: ViewIdData, facet: AndroidFacet): String? {
    return getViewClassNameFromLayoutAttribute(viewIdData.layoutName, facet)
  }

  private fun getViewClassNameFromLayoutAttribute(layout: String?, facet: AndroidFacet): String? {
    if (layout == null) {
      return null
    }
    // The following line must be called to make sure the underlying repositories are initialized
    ResourceRepositoryManager.getInstance(facet).existingAppResources ?: return null
    val resourceUrl = ResourceUrl.parse(layout)
    if (resourceUrl == null || resourceUrl.type != ResourceType.LAYOUT) {
      return null
    }
    val indexEntry = BindingXmlIndex.getEntriesForLayout(facet.module.project, resourceUrl.name).firstOrNull() ?: return null
    return getQualifiedBindingName(facet, indexEntry)
  }

}