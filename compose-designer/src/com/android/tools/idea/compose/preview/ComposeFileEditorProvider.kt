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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.common.actions.IssueNotificationAction
import com.android.tools.idea.common.editor.SeamlessTextEditorWithPreview
import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.compose.preview.actions.ForceCompileAndRefreshAction
import com.android.tools.idea.compose.preview.actions.GroupSwitchAction
import com.android.tools.idea.compose.preview.actions.AnimatedPreviewEnabler
import com.android.tools.idea.compose.preview.actions.ShowDebugBoundaries
import com.android.tools.idea.compose.preview.actions.ToggleAutoBuildAction
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.editor.multirepresentation.MultiRepresentationPreview
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.editor.multirepresentation.TextEditorWithMultiRepresentationPreview
import com.android.tools.idea.uibuilder.type.LayoutEditorFileType
import com.google.wireless.android.sdk.stats.LayoutEditorState
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

private const val PREFIX = "ComposePreview"
internal val COMPOSE_PREVIEW_MANAGER = DataKey.create<ComposePreviewManager>("$PREFIX.Manager")
internal val COMPOSE_PREVIEW_ELEMENT = DataKey.create<PreviewElement>("$PREFIX.PreviewElement")

/**
 * Returns a list of all [ComposePreviewManager]s related to the current context (which is implied to be bound to a particular file).
 * The search is done among the open preview parts and [PreviewRepresentation]s (if any) of open file editors.
 */
internal fun findComposePreviewManagersForContext(context: DataContext): List<ComposePreviewManager> {
  val project = context.getData(CommonDataKeys.PROJECT) ?: return emptyList()
  val file = context.getData(CommonDataKeys.VIRTUAL_FILE) ?: return emptyList()
  return FileEditorManager.getInstance(project)?.getEditors(file)?.mapNotNull { it.getComposePreviewManager() } ?: emptyList()
}

/**
 * [ToolbarActionGroups] that includes the [ForceCompileAndRefreshAction]
 */
private class ComposePreviewToolbar(private val surface: DesignSurface) :
  ToolbarActionGroups(surface) {

  override fun getNorthGroup(): ActionGroup = DefaultActionGroup(
    listOfNotNull(
      GroupSwitchAction(),
      if (StudioFlags.COMPOSE_PREVIEW_AUTO_BUILD.get()) ToggleAutoBuildAction() else null,
      ForceCompileAndRefreshAction(surface),
      if (StudioFlags.COMPOSE_ANIMATED_PREVIEW.get()) AnimatedPreviewEnabler() else null,
      if (StudioFlags.COMPOSE_DEBUG_BOUNDS.get()) ShowDebugBoundaries() else null
    )
  )

  override fun getNorthEastGroup(): ActionGroup = DefaultActionGroup().apply {
    addAll(getZoomActionsWithShortcuts(surface, this@ComposePreviewToolbar))
    add(IssueNotificationAction.getInstance())
  }
}

internal class ComposeTextEditorWithPreview constructor(
  composeTextEditor: TextEditor,
  preview: PreviewEditor) :
  SeamlessTextEditorWithPreview<PreviewEditor>(composeTextEditor, preview, "Compose Editor") {
  init {
    preview.registerShortcuts(component)
  }
}

/**
 * Returns the Compose [PreviewEditor] or null if this [FileEditor] is not a Compose preview.
 */
fun FileEditor.getComposePreviewManager(): ComposePreviewManager? = when (this) {
  is PreviewEditor -> this
  is MultiRepresentationPreview -> this.currentRepresentation as? ComposePreviewManager
  is ComposeTextEditorWithPreview -> this.preview
  is TextEditorWithMultiRepresentationPreview<out MultiRepresentationPreview> ->
    this.preview.currentRepresentation as? ComposePreviewManager
  else -> null
}

/**
 * Provider for Compose Preview editors.
 */
class ComposeFileEditorProvider @JvmOverloads constructor(
  private val filePreviewElementProvider: () -> FilePreviewElementFinder = ::defaultFilePreviewElementFinder) : FileEditorProvider, DumbAware {
  private val LOG = Logger.getInstance(ComposeFileEditorProvider::class.java)

  private object ComposeEditorFileType : LayoutEditorFileType() {
    override fun getLayoutEditorStateType() = LayoutEditorState.Type.COMPOSE

    override fun isResourceTypeOf(file: PsiFile): Boolean =
      file.virtualFile is ComposeAdapterLightVirtualFile

    override fun getToolbarActionGroups(surface: DesignSurface): ToolbarActionGroups =
      ComposePreviewToolbar(surface)

    override fun getSelectionContextToolbar(surface: DesignSurface, selection: List<NlComponent>): DefaultActionGroup =
      DefaultActionGroup()
  }

  init {
    if (StudioFlags.COMPOSE_PREVIEW.get()) {
      DesignerTypeRegistrar.register(ComposeEditorFileType)
    }
  }

  override fun accept(project: Project, file: VirtualFile): Boolean {
    if (StudioFlags.NELE_SOURCE_CODE_EDITOR.get() || !StudioFlags.COMPOSE_PREVIEW.get() || !file.isKotlinFileType()) {
      return false
    }

    val hasPreviewMethods = filePreviewElementProvider().hasPreviewMethods(project, file)
    if (LOG.isDebugEnabled) {
      LOG.debug("${file.path} hasPreviewMethods=${hasPreviewMethods}")
    }

    return hasPreviewMethods
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    if (LOG.isDebugEnabled) {
      LOG.debug("createEditor file=${file.path}")
    }
    val psiFile = PsiManager.getInstance(project).findFile(file)!!
    val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
    val previewProvider = object: PreviewElementProvider {
      override val previewElements: List<PreviewElement>
        get() = if (DumbService.isDumb(project)) emptyList() else filePreviewElementProvider().findPreviewMethods(project, file)
    }
    val previewRepresentation = ComposePreviewRepresentation(psiFile, previewProvider)
    val previewEditor = PreviewEditor(psiFile, previewRepresentation)
    val composeEditorWithPreview = ComposeTextEditorWithPreview(textEditor, previewEditor)

    previewEditor.onRefresh = {
      composeEditorWithPreview.isPureTextEditor = previewEditor.representation.previewElements.isEmpty()
    }

    return composeEditorWithPreview
  }

  override fun getEditorTypeId() = "ComposeEditor"

  override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}