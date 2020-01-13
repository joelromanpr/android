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
package com.android.tools.idea.compose.preview.runconfiguration

import com.android.tools.idea.compose.preview.PREVIEW_ANNOTATION_FQN
import com.android.tools.idea.compose.preview.isValidComposePreview
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.flags.StudioFlags
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import icons.StudioIcons
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * A [RunLineMarkerContributor] that displays a "Run" gutter icon next to `@Composable` functions annotated with [PREVIEW_ANNOTATION_FQN].
 * The icon can be used to create new [ComposePreviewRunConfiguration] or run an existing configuration.
 *
 * In order to avoid duplicated gutter icons displayed next to the `@Composable` function, [getInfo] should only return a valid [Info] when
 * receiving the [PsiElement] corresponding to the function identifier.
 */
class ComposePreviewRunLineMarkerContributor : RunLineMarkerContributor() {

  override fun getInfo(element: PsiElement): Info? {
    if (!StudioFlags.COMPOSE_PREVIEW_RUN_CONFIGURATION.get()) return null

    if (element !is LeafPsiElement) return null
    if (element.node.elementType != KtTokens.IDENTIFIER) return null

    (element.parent as? KtNamedFunction)?.takeIf { it.isValidComposePreview() }?.let {
      return Info(StudioIcons.Shell.Toolbar.RUN, ExecutorAction.getActions()) { _ -> message("run.line.marker.text", it.name!!) }
    }
    return null
  }
}