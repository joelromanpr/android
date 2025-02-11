// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion
import com.android.tools.adtui.HtmlLabel
import com.android.tools.adtui.HtmlLabel.setUpAsHtmlLabel
import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.model.stdui.DefaultCommonComboBoxModel
import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.adtui.model.stdui.EditingValidation
import com.android.tools.adtui.model.stdui.EditorCompletion
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.adtui.stdui.CommonTextField
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind.FAILURE_PREDICTED
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI.DEFAULT_STYLE_KEY
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeHelper
import com.intellij.ui.CheckboxTreeListener
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeModelAdapter
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JSeparator
import javax.swing.JTree
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.TreeModelEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

private val LOG = Logger.getInstance("Upgrade Assistant")

// "Model" here loosely in the sense of Model-View-Controller
class ToolWindowModel(
  val project: Project,
  val currentVersionProvider: () -> GradleVersion?,
  val recommended: GradleVersion? = null,
  val knownVersionsRequester: () -> Set<GradleVersion> = { IdeGoogleMavenRepository.getVersions("com.android.tools.build", "gradle") }
) : GradleSyncListener, Disposable {

  val latestKnownVersion = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())

  var current: GradleVersion? = currentVersionProvider()
    private set
  private var _selectedVersion: GradleVersion? = recommended ?: latestKnownVersion
  val selectedVersion: GradleVersion?
    get() = _selectedVersion
  var processor: AgpUpgradeRefactoringProcessor? = null

  val uiState = ObjectValueProperty<UIState>(UIState.Loading)

  sealed class UIState{
    abstract val runEnabled: Boolean
    abstract val showLoadingState: Boolean
    abstract val runTooltip: String
    open val loadingText: String = ""
    open val errorMessage: Pair<Icon, String>? = null

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is UIState) return false

      if (runEnabled != other.runEnabled) return false
      if (showLoadingState != other.showLoadingState) return false
      if (runTooltip != other.runTooltip) return false
      if (loadingText != other.loadingText) return false
      if (errorMessage != other.errorMessage) return false

      return true
    }

    override fun hashCode(): Int {
      var result = runEnabled.hashCode()
      result = 31 * result + showLoadingState.hashCode()
      result = 31 * result + runTooltip.hashCode()
      result = 31 * result + loadingText.hashCode()
      result = 31 * result + (errorMessage?.hashCode() ?: 0)
      return result
    }

    object ReadyToRun : UIState() {
      override val runEnabled = true
      override val showLoadingState = false
      override val runTooltip = ""
    }
    object Loading : UIState() {
      override val runEnabled = false
      override val showLoadingState = true
      override val runTooltip = ""
      override val loadingText = "Loading"
    }
    object RunningUpgrade : UIState() {
      override val runEnabled = false
      override val showLoadingState = true
      override val runTooltip = ""
      override val loadingText = "Running Upgrade"
    }
    object RunningSync : UIState() {
      override val runEnabled = false
      override val showLoadingState = true
      override val runTooltip = ""
      override val loadingText = "Running Sync"
    }
    object AllDone : UIState() {
      override val runEnabled = false
      override val showLoadingState = false
      override val runTooltip = "Nothing to do for this upgrade."
    }
    object ProjectFilesNotCleanWarning : UIState() {
      override val runEnabled = true
      override val showLoadingState = false
      override val loadingText = ""
      override val errorMessage = AllIcons.General.Warning to "Uncommitted changes in build files."
      override val runTooltip = "There are uncommitted changes in project build files.  Before upgrading, " +
                                 "you should commit or revert changes to the build files so that changes from the upgrade process " +
                                 "can be handled separately."
    }
    object AgpVersionNotLocatedError : UIState() {
      override val runEnabled = false
      override val showLoadingState = false
      override val loadingText = ""
      override val errorMessage = AllIcons.General.Error to "Cannot find AGP version in build files."
      override val runTooltip = "Cannot locate the version specification for the Android Gradle Plugin dependency, " +
                                "possibly because the project's build files use features not currently support by the " +
                                "Upgrade Assistant (for example: using constants defined in buildSrc)."
    }
    class InvalidVersionError(
      override val errorMessage: Pair<Icon, String>
    ) : UIState() {
      override val runEnabled = false
      override val showLoadingState = false
      override val runTooltip: String
        get() = errorMessage.second
    }
  }

  val knownVersions = OptionalValueProperty<Set<GradleVersion>>()
  val suggestedVersions = OptionalValueProperty<List<GradleVersion>>()

  val treeModel = DefaultTreeModel(CheckedTreeNode(null))

  val checkboxTreeStateUpdater = object : CheckboxTreeListener {
    override fun nodeStateChanged(node: CheckedTreeNode) {
      fun findNecessityNode(necessity: AgpUpgradeComponentNecessity): CheckedTreeNode? =
        (treeModel.root as CheckedTreeNode).children().asSequence().firstOrNull { (it as CheckedTreeNode).userObject == necessity } as? CheckedTreeNode

      fun enableNode(node: CheckedTreeNode) {
        node.isEnabled = true
        node.children().asSequence().forEach { (it as? CheckedTreeNode)?.isEnabled = true }
      }

      fun disableNode(node: CheckedTreeNode) {
        node.isEnabled = false
        node.children().asSequence().forEach { (it as? CheckedTreeNode)?.isEnabled = false }
      }

      fun allChildrenChecked(node: CheckedTreeNode) = node.children().asSequence().all { (it as? CheckedTreeNode)?.isChecked ?: true }
      fun anyChildrenChecked(node: CheckedTreeNode) = node.children().asSequence().any { (it as? CheckedTreeNode)?.isChecked ?: true }
      // We change the enabled states of nodes in the nodeStateChanged calls for the leaves (where the parents are the necessities) so
      // that we can largely ignore issues of checked state propagation; tempting though it is to do this for the state changes on the
      // necessity nodes themselves, it's not possible to get it right, because we can't tell whether we are deselecting a node because
      // of an explicit user action on that node or a propagated deselection of a child, and selecting a child node does not cause a
      // state change in the parent directly in any case.
      val parentNode = (node.parent as? CheckedTreeNode)?.also { if (it.userObject !is AgpUpgradeComponentNecessity) return } ?: return
      // The MANDATORY_CODEPENDENT node is special in two ways:
      // - its children's checkboxes are always disabled;
      // - it acts as a gateway for the other two necessities mentioned here: if it's enabled, then OPTIONAL_CODEPENDENT processors may
      //   be selected; if it's disabled, then MANDATORY_INDEPENDENT processors may be deselected.
      when (parentNode.userObject) {
        MANDATORY_INDEPENDENT -> findNecessityNode(MANDATORY_CODEPENDENT)?.let { it.isEnabled = allChildrenChecked(parentNode) }
        MANDATORY_CODEPENDENT -> {
          findNecessityNode(MANDATORY_INDEPENDENT)?.let { if (node.isChecked) disableNode(it) else enableNode(it) }
          findNecessityNode(OPTIONAL_CODEPENDENT)?.let { if (node.isChecked) enableNode(it) else disableNode(it) }
        }
        OPTIONAL_CODEPENDENT -> findNecessityNode(MANDATORY_CODEPENDENT)?.let { it.isEnabled = !anyChildrenChecked(parentNode) }
      }
    }
  }

  init {
    Disposer.register(project, this)
    refresh()

    GradleSyncState.subscribe(project, this, this)
    // Initialize known versions (e.g. in case of offline work with no cache)
    suggestedVersions.value = suggestedVersionsList(setOf())

    // Request known versions.
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Looking for known versions", false) {
      override fun run(indicator: ProgressIndicator) {
        knownVersions.value = knownVersionsRequester()
        val suggestedVersionsList = suggestedVersionsList(knownVersions.value)
        invokeLater(ModalityState.NON_MODAL) { suggestedVersions.value = suggestedVersionsList }
      }
    })
  }

  override fun syncStarted(project: Project) = uiState.set(UIState.RunningSync)
  override fun syncFailed(project: Project, errorMessage: String) = syncFinished()
  override fun syncSucceeded(project: Project) = syncFinished()
  override fun syncSkipped(project: Project) = syncFinished()

  private fun syncFinished() {
    uiState.set(UIState.Loading)
    refresh(true)
  }

  override fun dispose() {
    processor?.usageView?.close()
  }

  fun editingValidation(value: String?): Pair<EditingErrorCategory, String> {
    val parsed = value?.let { GradleVersion.tryParseAndroidGradlePluginVersion(it) }
    val current = current
    return when {
      current == null -> Pair(EditingErrorCategory.ERROR, "Unknown current AGP version.")
      parsed == null -> Pair(EditingErrorCategory.ERROR, "Invalid AGP version format.")
      parsed < current -> Pair(EditingErrorCategory.ERROR, "Cannot downgrade AGP version.")
      parsed > latestKnownVersion ->
        if (parsed.major > latestKnownVersion.major)
          Pair(EditingErrorCategory.ERROR, "Target AGP version is unsupported.")
        else
          Pair(EditingErrorCategory.WARNING, "Upgrade to target AGP version is unverified.")

      else -> EDITOR_NO_ERROR
    }
  }

  fun newVersionSet(newVersionString: String) {
    val status = editingValidation(newVersionString)
    _selectedVersion = if (status.first == EditingErrorCategory.ERROR) {
      uiState.set(UIState.InvalidVersionError(AllIcons.General.Error to status.second))
      null
    }
    else {
      GradleVersion.tryParseAndroidGradlePluginVersion(newVersionString)
    }
    refresh()
  }

  fun suggestedVersionsList(gMavenVersions: Set<GradleVersion>): List<GradleVersion> = gMavenVersions
    // Make sure the current (if known), recommended, and latest known versions are present, whether published or not
    .union(listOfNotNull(current, recommended, latestKnownVersion))
    // Keep only versions that are later than or equal to current
    .filter { current?.let { current -> it >= current } ?: false }
    // Keep only versions that are no later than the latest version we support
    .filter { it <= latestKnownVersion }
    // Do not keep versions that would force an upgrade from on sync
    .filter { !versionsShouldForcePluginUpgrade(it, latestKnownVersion) }
    .toList()
    .sortedDescending()

  fun refresh(refindPlugin: Boolean = false) {
    val oldState = uiState.get()
    uiState.set(UIState.Loading)
    // First clear some state
    val root = (treeModel.root as CheckedTreeNode)
    root.removeAllChildren()
    treeModel.nodeStructureChanged(root)
    processor?.usageView?.close()
    processor = null

    if (refindPlugin) {
      current = currentVersionProvider()
      suggestedVersions.value = suggestedVersionsList(knownVersions.valueOrNull ?: setOf())
    }
    val newVersion = selectedVersion
    // TODO(xof/mlazeba): should we somehow preserve the existing uuid of the processor?
    val newProcessor = newVersion?.let {
      current?.let { current ->
        if (newVersion >= current && !project.isDisposed)
          project.getService(AssistantInvoker::class.java).createProcessor(project, current, it)
        else
          null
      }
    }

    if (newProcessor == null) {
      // Preserve existing message and run button tooltips from newVersion validation.
      uiState.set(oldState)
    }
    else {
      val application = ApplicationManager.getApplication()
      if (application.isUnitTestMode) {
        parseAndSetEnabled(newProcessor)
      } else {
        application.executeOnPooledThread { parseAndSetEnabled(newProcessor) }
      }
    }
  }

  private fun parseAndSetEnabled(newProcessor: AgpUpgradeRefactoringProcessor) {
    val application = ApplicationManager.getApplication()
    newProcessor.ensureParsedModels()
    val projectFilesClean = isCleanEnoughProject(project)
    val classpathUsageFound = !newProcessor.classpathRefactoringProcessor.isAlwaysNoOpForProject
    if (application.isUnitTestMode) {
      setEnabled(newProcessor, projectFilesClean, classpathUsageFound)
    } else {
      invokeLater(ModalityState.NON_MODAL) { setEnabled(newProcessor, projectFilesClean, classpathUsageFound) }
    }
  }

  private fun setEnabled(newProcessor: AgpUpgradeRefactoringProcessor, projectFilesClean: Boolean, classpathUsageFound: Boolean) {
    refreshTree(newProcessor)
    processor = newProcessor
    if (!classpathUsageFound && newProcessor.current != newProcessor.new) {
      newProcessor.trackProcessorUsage(FAILURE_PREDICTED)
      uiState.set(UIState.AgpVersionNotLocatedError)
    }
    else if (!projectFilesClean) {
      uiState.set(UIState.ProjectFilesNotCleanWarning)
    }
    else if ((treeModel.root as? CheckedTreeNode)?.childCount == 0) {
      uiState.set(UIState.AllDone)
    }
    else {
      uiState.set(UIState.ReadyToRun)
    }
  }

  private fun refreshTree(processor: AgpUpgradeRefactoringProcessor) {
    val root = treeModel.root as CheckedTreeNode
    if (root.childCount > 0) {
      // this can happen if we call refresh() twice in quick succession: both refreshes clear the tree before either
      // gets here.
      root.removeAllChildren()
    }
    fun <T : DefaultMutableTreeNode> populateNecessity(
      necessity: AgpUpgradeComponentNecessity,
      constructor: (Any) -> (T)
    ): CheckedTreeNode {
      val node = CheckedTreeNode(necessity)
      processor.activeComponentsForNecessity(necessity).forEach { component -> node.add(constructor(toStepPresentation(component))) }
      node.let { if (it.childCount > 0) root.add(it) }
      return node
    }
    populateNecessity(MANDATORY_INDEPENDENT) { o -> CheckedTreeNode(o).also { it.isEnabled = false } }.isEnabled = false
    populateNecessity(MANDATORY_CODEPENDENT) { o -> CheckedTreeNode(o).also { it.isEnabled = false } }
    populateNecessity(OPTIONAL_CODEPENDENT) { o -> CheckedTreeNode(o).also { it.isChecked = false } }
    populateNecessity(OPTIONAL_INDEPENDENT) { o -> CheckedTreeNode(o).also { it.isChecked = false } }
    treeModel.nodeStructureChanged(root)
  }

  fun runUpgrade(showPreview: Boolean) = processor?.let { processor ->
    if (!showPreview) uiState.set(UIState.RunningUpgrade)
    processor.components().forEach { it.isEnabled = false }
    CheckboxTreeHelper.getCheckedNodes(DefaultStepPresentation::class.java, null, treeModel)
      .forEach { it.processor.isEnabled = true }

    if (ApplicationManager.getApplication().isUnitTestMode) {
      processor.run()
    }
    else {
      DumbService.getInstance(processor.project).smartInvokeLater {
        //TODO (mlazeba/xof): usages view run button should set our state to running again.
        processor.usageView?.close()
        processor.setPreviewUsages(showPreview)
        processor.run()
      }
    }
  }

  interface StepUiPresentation {
    val pageHeader: String
    val treeText: String
    val helpLinkUrl: String?
    val shortDescription: String?
    val additionalInfo: String?
      get() = null
  }

  interface StepUiWithComboSelectorPresentation {
    val label: String
    val elements: List<Any>
    var selectedValue: Any
  }

  // TODO(mlazeba/xof): temporary here, need to be defined in processor itself probably
  private fun toStepPresentation(processor: AgpUpgradeComponentRefactoringProcessor) = when (processor) {
    is Java8DefaultRefactoringProcessor -> object : DefaultStepPresentation(processor), StepUiWithComboSelectorPresentation {
      override val label: String = "Action on no explicit Java language level: "
      override val pageHeader: String
        get() = processor.commandName
      override val treeText: String
        get() = processor.noLanguageLevelAction.toString()
      override val elements: List<Java8DefaultRefactoringProcessor.NoLanguageLevelAction>
        get() = listOf(
          Java8DefaultRefactoringProcessor.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT,
          Java8DefaultRefactoringProcessor.NoLanguageLevelAction.INSERT_OLD_DEFAULT
        )
      override var selectedValue: Any
        get() = processor.noLanguageLevelAction
        set(value) {
          if (value is Java8DefaultRefactoringProcessor.NoLanguageLevelAction) processor.noLanguageLevelAction = value
        }
      init {
        selectedValue = Java8DefaultRefactoringProcessor.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT
      }
    }
    is GradlePluginsRefactoringProcessor -> object : DefaultStepPresentation(processor) {
      override val additionalInfo =
        processor.cachedUsages
          .filter { it is WellKnownGradlePluginDependencyUsageInfo || it is WellKnownGradlePluginDslUsageInfo }
          .map { it.tooltipText }.toSortedSet().takeIf { !it.isEmpty() }?.run {
             val result = StringBuilder()
            result.append("<p>The following Gradle plugin versions will be updated:</p>\n")
            result.append("<ul>\n")
            forEach { result.append("<li>$it</li>\n") }
            result.append("</ul>\n")
            result.toString()
          }
    }
    else -> DefaultStepPresentation(processor)
  }

  open class DefaultStepPresentation(val processor: AgpUpgradeComponentRefactoringProcessor) : StepUiPresentation {
    override val pageHeader: String
      get() = treeText
    override val treeText: String
      get() = processor.commandName
    override val helpLinkUrl: String?
      get() = processor.getReadMoreUrl()
    override val shortDescription: String?
      get() = processor.getShortDescription()
  }
}

class ContentManager(val project: Project) {
  init {
    ApplicationManager.getApplication().invokeAndWait {
      // Force EDT here to ease the testing (see com.intellij.ide.plugins.CreateAllServicesAndExtensionsAction: it instantiates services
      //  on a background thread). There is no performance penalties when already invoked on EDT.
      ToolWindowManager.getInstance(project).registerToolWindow(
        RegisterToolWindowTask.closable("Upgrade Assistant", icons.GradleIcons.ToolWindowGradle))
    }
  }

  fun showContent(recommended: GradleVersion? = null) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Upgrade Assistant")!!
    toolWindow.contentManager.removeAllContents(true)
    val model = ToolWindowModel(
      project, currentVersionProvider = { AndroidPluginInfo.find(project)?.pluginVersion }, recommended = recommended)
    val view = View(model, toolWindow.contentManager)
    val content = ContentFactory.getInstance().createContent(view.content, model.current.contentDisplayName(), true)
    content.setDisposer(model)
    content.isPinned = true
    toolWindow.contentManager.addContent(content)
    toolWindow.show()
  }

  class View(val model: ToolWindowModel, contentManager: com.intellij.ui.content.ContentManager) {
    /*
    Experiment of usage of observable property bindings I have found in our code base.
    Taking inspiration from com/android/tools/idea/avdmanager/ConfigureDeviceOptionsStep.java:85 at the moment (Jan 2021).
     */
    private val myBindings = BindingsManager()
    private val myListeners = ListenerManager()

    val tree = CheckboxTree(UpgradeAssistantTreeCellRenderer(), null).apply {
      model = this@View.model.treeModel
      isRootVisible = false
      selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
      addCheckboxTreeListener(this@View.model.checkboxTreeStateUpdater)
      addTreeSelectionListener { e -> refreshDetailsPanel() }
      background = primaryContentBackground
      isOpaque = true
      isEnabled = !this@View.model.uiState.get().showLoadingState
      myListeners.listen(this@View.model.uiState) { uiState ->
        isEnabled = !uiState.showLoadingState
        if (uiState.showLoadingState) {
          selectionModel.clearSelection()
          refreshDetailsPanel()
        }
      }
    }

    val upgradeLabel = JBLabel(model.current.upgradeLabelText()).also { it.border = JBUI.Borders.empty(0, 6) }

    val versionTextField = CommonComboBox<GradleVersion, CommonComboBoxModel<GradleVersion>>(
      // TODO this model needs to be enhanced to know when to commit value, instead of doing it in document listener below.
      object : DefaultCommonComboBoxModel<GradleVersion>(
        model.selectedVersion?.toString() ?: "",
        model.suggestedVersions.valueOrNull ?: emptyList()
      ) {
        init {
          selectedItem = model.selectedVersion
          myListeners.listen(model.suggestedVersions) { suggestedVersions ->
            val selectedVersion = model.selectedVersion
            for (i in size - 1 downTo 0) {
              if (getElementAt(i) != selectedVersion) removeElementAt(i)
            }
            suggestedVersions.orElse(emptyList()).forEachIndexed { i, it ->
              when {
                selectedVersion == null -> addElement(it)
                it > selectedVersion -> insertElementAt(it, i)
                it == selectedVersion -> Unit
                else -> addElement(it)
              }
            }
            selectedItem = selectedVersion
          }
          placeHolderValue = "Select new version"
        }

        // Given the ComponentValidator installation below, one might expect this not to be necessary,
        // but the outline highlighting does not work without it.
        // This is happening because not specifying validation here does not remove validation but just using default 'accept all' one.
        // This validation is triggered after the ComponentValidator and overrides the outline set by ComponentValidator.
        // The solution would be either add support of the tooltip to this component validation logic or use a different component.
        override val editingSupport = object : EditingSupport {
          override val validation: EditingValidation = model::editingValidation
          override val completion: EditorCompletion = { model.suggestedVersions.getValueOr(emptyList()).map { it.toString() }}
        }
      }
    ).apply {
      isEnabled = !this@View.model.uiState.get().showLoadingState
      myListeners.listen(this@View.model.uiState) { uiState ->
        isEnabled = !uiState.showLoadingState
      }

      // Need to register additional key listeners to the textfield that would hide main combo-box popup.
      // Otherwise textfield consumes these events without hiding popup making it impossible to do with a keyboard.
      val textField = editor.editorComponent as CommonTextField<*>
      textField.registerActionKey({ hidePopup(); textField.enterInLookup() }, KeyStrokes.ENTER, "enter")
      textField.registerActionKey({ hidePopup(); textField.escapeInLookup() }, KeyStrokes.ESCAPE, "escape")
      ComponentValidator(this@View.model).withValidator { ->
        val text = editor.item.toString()
        val validation = this@View.model.editingValidation(text)
        when (validation.first) {
          EditingErrorCategory.ERROR -> ValidationInfo(validation.second, this)
          EditingErrorCategory.WARNING -> ValidationInfo(validation.second, this).asWarning()
          else -> null
        }
      }.installOn(this)
      textField.document?.addDocumentListener(
        object: DocumentAdapter() {
          override fun textChanged(e: DocumentEvent) {
            ComponentValidator.getInstance(this@apply).ifPresent { v -> v.revalidate() }
            this@View.model.newVersionSet(editor.item.toString())
          }
        }
      )
    }

    val refreshButton = JButton("Refresh").apply {
      isEnabled = !this@View.model.uiState.get().showLoadingState
      myListeners.listen(this@View.model.uiState) { uiState ->
        isEnabled = !uiState.showLoadingState
      }
      addActionListener {
        this@View.model.run {
          refresh(true)
        }
      }
    }
    val okButton = JButton("Run selected steps").apply {
      addActionListener { this@View.model.runUpgrade(false) }
      this@View.model.uiState.get().let { uiState ->
        toolTipText = uiState.runTooltip
        isEnabled = uiState.runEnabled
      }
      myListeners.listen(this@View.model.uiState) { uiState ->
        toolTipText = uiState.runTooltip
        isEnabled = uiState.runEnabled
      }
      putClientProperty(DEFAULT_STYLE_KEY, true)
    }
    val previewButton = JButton("Show Usages").apply {
      addActionListener { this@View.model.runUpgrade(true) }
      this@View.model.uiState.get().let { uiState ->
        toolTipText = uiState.runTooltip
        isEnabled = uiState.runEnabled
      }
      myListeners.listen(this@View.model.uiState) { uiState ->
        toolTipText = uiState.runTooltip
        isEnabled = uiState.runEnabled
      }
    }
    val messageLabel = JBLabel().apply {
      this@View.model.uiState.get().let { uiState ->
        icon = uiState.errorMessage?.first
        text = uiState.errorMessage?.second
      }
      myListeners.listen(this@View.model.uiState) { uiState ->
        icon = uiState.errorMessage?.first
        text = uiState.errorMessage?.second
      }
    }

    val detailsPanel = JBPanel<JBPanel<*>>().apply {
      layout = VerticalLayout(0, SwingConstants.LEFT)
      border = JBUI.Borders.empty(20)
      myListeners.listen(this@View.model.uiState) { refreshDetailsPanel() }
    }

    val content = JBLoadingPanel(BorderLayout(), contentManager).apply {
      val controlsPanel = makeTopComponent()
      val topPanel = JBPanel<JBPanel<*>>().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(controlsPanel)
        add(JSeparator(SwingConstants.HORIZONTAL))
      }
      add(topPanel, BorderLayout.NORTH)
      val treePanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        add(createScrollPane(tree, SideBorder.NONE), BorderLayout.WEST)
        add(JSeparator(SwingConstants.VERTICAL), BorderLayout.CENTER)
      }
      add(treePanel, BorderLayout.WEST)
      add(detailsPanel, BorderLayout.CENTER)

      fun updateLoadingState(uiState: ToolWindowModel.UIState) {
        setLoadingText(uiState.loadingText)
        if (uiState.showLoadingState) {
          startLoading()
        }
        else {
          stopLoading()
          upgradeLabel.text = model.current.upgradeLabelText()
          contentManager.getContent(this)?.displayName = model.current.contentDisplayName()
        }
      }

      myListeners.listen(model.uiState, ::updateLoadingState)
      updateLoadingState(model.uiState.get())
    }

    init {
      model.treeModel.addTreeModelListener(object : TreeModelAdapter() {
        override fun treeStructureChanged(event: TreeModelEvent?) {
          // Tree expansion should not run in 'treeStructureChanged' as another listener clears the nodes expanded state
          // in the same event listener that is normally called after this one. Probably this state is cached somewhere else
          // making this diversion not immediately visible but on page hide and restore it uses all-folded state form the model.
          invokeLater(ModalityState.NON_MODAL) {
            tree.setHoldSize(false)
            TreeUtil.expandAll(tree) {
              tree.setHoldSize(true)
              content.revalidate()
            }
          }
        }
      })
      TreeUtil.expandAll(tree)
      tree.setHoldSize(true)
    }

    private fun makeTopComponent() = JBPanel<JBPanel<*>>().apply {
      layout = HorizontalLayout(5)
      add(upgradeLabel)
      add(versionTextField)
      add(okButton)
      add(previewButton)
      add(refreshButton)
      add(messageLabel)
    }

    private fun refreshDetailsPanel() {
      detailsPanel.removeAll()
      val selectedStep = (tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject
      val label = HtmlLabel().apply { name = "content" }
      setUpAsHtmlLabel(label)
      when {
        this@View.model.uiState.get() is ToolWindowModel.UIState.AllDone -> {
          val sb = StringBuilder()
          sb.append("<div><b>Nothing to do</b></div>")
          sb.append("<p>Project build files are up-to-date for Android Gradle Plugin version ${this@View.model.current}.")
          if (this@View.model.current?.let { it < this@View.model.latestKnownVersion } == true) {
            sb.append("<br>Upgrades to newer versions of Android Gradle Plugin (up to ${this@View.model.latestKnownVersion}) can be")
            sb.append("<br>performed by selecting those versions from the dropdown.")
          }
          sb.append("</p>")
          label.text = sb.toString()
          detailsPanel.add(label)
        }
        selectedStep is AgpUpgradeComponentNecessity -> {
          label.text = "<div><b>${selectedStep.treeText()}</b></div><p>${selectedStep.description().replace("\n", "<br>")}</p>"
          detailsPanel.add(label)
        }
        selectedStep is ToolWindowModel.StepUiPresentation -> {
          val text = StringBuilder("<div><b>${selectedStep.pageHeader}</b></div>")
          val paragraph = selectedStep.helpLinkUrl != null || selectedStep.shortDescription != null
          if (paragraph) text.append("<p>")
          selectedStep.shortDescription?.let { description ->
            text.append(description.replace("\n", "<br>"))
            selectedStep.helpLinkUrl?.let { text.append("  ") }
          }
          selectedStep.helpLinkUrl?.let { url ->
            // TODO(xof): what if we end near the end of the line, and this sticks out in an ugly fashion?
            text.append("<a href='$url'>Read more</a><icon src='AllIcons.Ide.External_link_arrow'>.")
          }
          selectedStep.additionalInfo?.let { text.append(it) }
          label.text = text.toString()
          detailsPanel.add(label)
          if (selectedStep is ToolWindowModel.StepUiWithComboSelectorPresentation) {
            ComboBox(selectedStep.elements.toTypedArray()).apply {
              name = "selection"
              item = selectedStep.selectedValue
              addActionListener {
                selectedStep.selectedValue = this.item
                tree.repaint()
                refreshDetailsPanel()
              }
              val comboPanel = JBPanel<JBPanel<*>>()
              comboPanel.layout = HorizontalLayout(0)
              comboPanel.add(JBLabel(selectedStep.label).also { it.border = JBUI.Borders.empty(0, 4); it.name = "label" })
              comboPanel.add(this)
              detailsPanel.add(comboPanel)
            }
          }
        }
      }
      detailsPanel.revalidate()
      detailsPanel.repaint()
    }
  }

  private class UpgradeAssistantTreeCellRenderer : CheckboxTree.CheckboxTreeCellRenderer(true, true) {
    override fun customizeRenderer(tree: JTree?,
                                   value: Any?,
                                   selected: Boolean,
                                   expanded: Boolean,
                                   leaf: Boolean,
                                   row: Int,
                                   hasFocus: Boolean) {
      if (value is DefaultMutableTreeNode) {
        when (val o = value.userObject) {
          is AgpUpgradeComponentNecessity -> {
            textRenderer.append(o.treeText())
            myCheckbox.let { toolTipText = o.checkboxToolTipText(it.isEnabled, it.isSelected) }
          }
          is ToolWindowModel.StepUiPresentation -> {
            (value.parent as? DefaultMutableTreeNode)?.let { parent ->
              if (parent.userObject == MANDATORY_CODEPENDENT) {
                toolTipText = null
                myCheckbox.isVisible = false
                textRenderer.append("")
                val totalXoffset = myCheckbox.width + myCheckbox.margin.left + myCheckbox.margin.right
                val firstXoffset = 2 * myCheckbox.width / 5 // approximate padding needed to put the bullet centrally in the space
                textRenderer.appendTextPadding(firstXoffset)
                textRenderer.append("\u2022", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, true)
                // Although this looks wrong (one might expect e.g. `totalXoffset - firstXoffset`), it does seem to be the case that
                // SimpleColoredComponent interprets padding from the start of the extent, rather than from the previous end.  Of course this
                // might be a bug, and if the behaviour of SimpleColoredComponent is changed this will break alignment of the Upgrade steps.
                textRenderer.appendTextPadding(totalXoffset)
              }
              else {
                myCheckbox.let {
                  toolTipText = (parent.userObject as? AgpUpgradeComponentNecessity)?.checkboxToolTipText(it.isEnabled, it.isSelected)
                }
              }
            }
            textRenderer.append(o.treeText, SimpleTextAttributes.REGULAR_ATTRIBUTES, true)
            if (o is ToolWindowModel.StepUiWithComboSelectorPresentation) {
              textRenderer.icon = AllIcons.Actions.Edit
              textRenderer.isIconOnTheRight = true
              textRenderer.iconTextGap = 10
            }
          }
        }
      }
      super.customizeRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
    }
  }
}

private fun AgpUpgradeRefactoringProcessor.components() = this.componentRefactoringProcessors + this.classpathRefactoringProcessor

private fun AgpUpgradeRefactoringProcessor.activeComponentsForNecessity(necessity: AgpUpgradeComponentNecessity) =
  this.components().filter { it.isEnabled }.filter { it.necessity() == necessity }.filter { !it.isAlwaysNoOpForProject }

fun AgpUpgradeComponentNecessity.treeText() = when (this) {
  MANDATORY_INDEPENDENT -> "Upgrade prerequisites"
  MANDATORY_CODEPENDENT -> "Upgrade"
  OPTIONAL_CODEPENDENT -> "Recommended post-upgrade steps"
  OPTIONAL_INDEPENDENT -> "Recommended steps"
  else -> {
    LOG.error("Irrelevant steps tree text requested")
    "Irrelevant steps"
  }
}

fun AgpUpgradeComponentNecessity.checkboxToolTipText(enabled: Boolean, selected: Boolean) =
  if (enabled) null
  else when (this to selected) {
    MANDATORY_INDEPENDENT to true -> "Cannot be deselected while ${MANDATORY_CODEPENDENT.treeText()} is selected"
    MANDATORY_CODEPENDENT to false -> "Cannot be selected while ${MANDATORY_INDEPENDENT.treeText()} is unselected"
    MANDATORY_CODEPENDENT to true -> "Cannot be deselected while ${OPTIONAL_CODEPENDENT.treeText()} is selected"
    OPTIONAL_CODEPENDENT to false -> "Cannot be selected while ${MANDATORY_CODEPENDENT.treeText()} is unselected"
    else -> {
      LOG.error("Irrelevant step tooltip text requested")
      null
    }
  }

fun AgpUpgradeComponentNecessity.description() = when (this) {
  MANDATORY_INDEPENDENT ->
    "These steps are required to perform the upgrade of this project.\n" +
    "You can choose to do them in separate steps, in advance of the Android\n" +
    "Gradle Plugin upgrade itself."
  MANDATORY_CODEPENDENT ->
    "These steps are required to perform the upgrade of this project.\n" +
    "They must all happen together, at the same time as the Android Gradle Plugin\n" +
    "upgrade itself."
  OPTIONAL_CODEPENDENT ->
    "These steps are not required to perform the upgrade of this project at this time,\n" +
    "but will be required when upgrading to a later version of the Android Gradle\n" +
    "Plugin.  You can choose to do them in this upgrade to prepare for the future, but\n" +
    "only if the Android Gradle Plugin is upgraded to its new version."
  OPTIONAL_INDEPENDENT ->
    "These steps are not required to perform the upgrade of this project at this time,\n" +
    "but will be required when upgrading to a later version of the Android Gradle\n" +
    "Plugin.  You can choose to do them in this upgrade to prepare for the future,\n" +
    "with or without upgrading the Android Gradle Plugin to its new version."
  else -> {
    LOG.error("Irrelevant step description requested")
    "These steps are irrelevant to this upgrade (and should not be displayed)"
  }
}

fun GradleVersion?.upgradeLabelText() = when (this) {
  null -> "Upgrade Android Gradle Plugin from unknown version to"
  else -> "Upgrade Android Gradle Plugin from version $this to"
}

fun GradleVersion?.contentDisplayName() = when (this) {
  null -> "Upgrade project from unknown AGP"
  else -> "Upgrade project from AGP $this"
}
