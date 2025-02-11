package com.android.tools.idea.editors.literals

import com.android.flags.junit.SetFlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.replaceText
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.util.ui.UIUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class LiveLiteralsServiceTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val liveEditFlagRule = SetFlagRule(StudioFlags.COMPOSE_LIVE_EDIT_PREVIEW, false)
  @get:Rule
  val liveLiteralsFlagRule = SetFlagRule(StudioFlags.COMPOSE_LIVE_LITERALS, true)

  private val project: Project
    get() = projectRule.project
  lateinit var file1: PsiFile
  lateinit var file2: PsiFile

  private fun getTestLiveLiteralsService(): LiveLiteralsService =
    LiveLiteralsService.getInstance(project)

  @Before
  fun setup() {
    LiveLiteralsApplicationConfiguration.getInstance().isEnabled = true
    file1 = projectRule.fixture.addFileToProject("src/main/java/com/literals/test/Test.kt", """
      package com.literals.test

      fun method() {
        val a = "Hello"
        val b = 2
        val c = true
      }

      class Test {
        val a = "ClassHello"
        val b = 2

        fun methodWithParameters(a: Int, b: Float) {
          println("${'$'}a ${'$'}b")
        }

        fun method() {
          val c = 123

          method(9, 999f)
        }
      }
    """.trimIndent())
    file2 = projectRule.fixture.addFileToProject("src/main/java/com/literals/test/Test2.kt", """
      package com.literals.test

      fun method2() {
        val a = 3.0
      }
    """.trimIndent())
  }

  /**
   * Runs [runnable] and ensures that a document was added to the tracking of the [liveLiteralsService] after the call.
   */
  private fun runAndWaitForDocumentAdded(liveLiteralsService: LiveLiteralsService, runnable: () -> Unit) {
    val documentAdded = CountDownLatch(1)
    val disposable = Disposer.newDisposable(projectRule.fixture.testRootDisposable, "DocumentAddDiposable")
    DumbService.getInstance(project).waitForSmartMode()
    try {
      liveLiteralsService.addOnDocumentsUpdatedListener(disposable) {
        documentAdded.countDown()
      }
      runnable()
      // We wait for a maximum of 20 seconds. Finding literals runs in a non-blocking action. During tests
      // indexing can be triggered which can delay the test for a few seconds. Reducing this number will cause
      // the test to become flaky because of some cases where indexing interferes with the test.
      documentAdded.await(20, TimeUnit.SECONDS)
    } finally {
      Disposer.dispose(disposable) // Remove listener
    }
  }

  @Test
  fun `check that already open editors register constants`() {
    projectRule.fixture.configureFromExistingVirtualFile(file1.virtualFile)
    val liveLiteralsService = getTestLiveLiteralsService()
    assertTrue(liveLiteralsService.allConstants().isEmpty())
    assertFalse(liveLiteralsService.isAvailable)
    runAndWaitForDocumentAdded(liveLiteralsService) {
      liveLiteralsService.liveLiteralsMonitorStarted("TestDevice", LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    }
    assertTrue(liveLiteralsService.isAvailable)
    assertEquals(9, liveLiteralsService.allConstants().size)
  }

  @Test
  fun `check that constants are registered after a new editor is opened`() {
    val liveLiteralsService = getTestLiveLiteralsService()
    assertTrue(liveLiteralsService.allConstants().isEmpty())
    assertFalse(liveLiteralsService.isAvailable)
    liveLiteralsService.liveLiteralsMonitorStarted("TestDevice", LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    assertTrue(liveLiteralsService.isAvailable)
    assertTrue(liveLiteralsService.allConstants().isEmpty())
    runAndWaitForDocumentAdded(liveLiteralsService) {
      projectRule.fixture.configureFromExistingVirtualFile(file1.virtualFile)
    }
    assertEquals(9, liveLiteralsService.allConstants().size)

    // Open second editor
    runAndWaitForDocumentAdded(liveLiteralsService) {
      projectRule.fixture.configureFromExistingVirtualFile(file2.virtualFile)
    }
    assertEquals(10, liveLiteralsService.allConstants().size)

    // Close the second editor
    UIUtil.invokeAndWaitIfNeeded(Runnable { FileEditorManager.getInstance(project).closeFile(file2.virtualFile) })
    assertEquals(9, liveLiteralsService.allConstants().size)
  }

  @Test
  fun `listener notification`() {
    // Setup
    val latch = CountDownLatch(1)
    val modifications = mutableListOf<LiteralReference>()
    val liveLiteralsService = getTestLiveLiteralsService()
    liveLiteralsService.addOnLiteralsChangedListener(projectRule.fixture.testRootDisposable) {
      modifications.addAll(it)
      latch.countDown()
    }
    projectRule.fixture.configureFromExistingVirtualFile(file1.virtualFile)
    runAndWaitForDocumentAdded(liveLiteralsService) {
      liveLiteralsService.liveLiteralsMonitorStarted("TestDevice", LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    }
    assertFalse(liveLiteralsService.allConstants().isEmpty())

    // Run test
    projectRule.fixture.editor.executeAndSave {
      replaceText("ClassHello", "ClassBye")
      replaceText("999", "555")
    }

    // Wait for the modification to be notified
    latch.await(5, TimeUnit.SECONDS)
    assertEquals(2, modifications.size)
  }

  @Test
  fun `listener is only called when live literals are available`() {
    var changeListenerCalls = 0
    val liveLiteralsService = getTestLiveLiteralsService()
    liveLiteralsService.addOnLiteralsChangedListener(projectRule.fixture.testRootDisposable) {
      changeListenerCalls++
    }
    assertTrue(liveLiteralsService.allConstants().isEmpty())
    assertFalse(liveLiteralsService.isAvailable)
    assertEquals(0, changeListenerCalls)
    runAndWaitForDocumentAdded(liveLiteralsService) {
      liveLiteralsService.liveLiteralsMonitorStarted("TestDevice", LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    }
    assertTrue(liveLiteralsService.isAvailable)
    assertEquals(0, changeListenerCalls)
    assertTrue(liveLiteralsService.allConstants().isEmpty())
    projectRule.fixture.configureFromExistingVirtualFile(file1.virtualFile)

    ApplicationManager.getApplication().invokeAndWait {
      // We run it and wait to ensure this has executed before the next assert
      liveLiteralsService.liveLiteralsMonitorStopped("TestDevice")
    }
    assertFalse(liveLiteralsService.isAvailable)
    assertEquals(0, changeListenerCalls)
  }

  @Test
  fun `listener is only called when live literals are enabled`() {
    var changeListenerCalls = 0
    val liveLiteralsService = getTestLiveLiteralsService()
    assertTrue(liveLiteralsService.isEnabled)
    val latch = CountDownLatch(1)
    liveLiteralsService.addOnLiteralsChangedListener(projectRule.fixture.testRootDisposable) {
      changeListenerCalls++
      latch.countDown()
    }
    runAndWaitForDocumentAdded(liveLiteralsService) {
      liveLiteralsService.liveLiteralsMonitorStarted("TestDevice", LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    }
    assertTrue(liveLiteralsService.isAvailable)
    runAndWaitForDocumentAdded(liveLiteralsService) {
      projectRule.fixture.configureFromExistingVirtualFile(file1.virtualFile)
    }

    LiveLiteralsApplicationConfiguration.getInstance().isEnabled = false
    assertFalse(liveLiteralsService.isAvailable)
    assertEquals(0, changeListenerCalls)
    projectRule.fixture.editor.executeAndSave {
      replaceText("999", "555")
    }

    LiveLiteralsApplicationConfiguration.getInstance().isEnabled = true
    projectRule.fixture.editor.executeAndSave {
      replaceText("555", "333")
    }
    // Wait for the modification to be notified
    latch.await(5, TimeUnit.SECONDS)
    assertEquals(1, changeListenerCalls)
  }

  // Regression test for b/196253658
  @Test
  fun `check no NPE for PsiElements without file`() {
    val liveLiteralsService = getTestLiveLiteralsService()
    runAndWaitForDocumentAdded(liveLiteralsService) {
      liveLiteralsService.liveLiteralsMonitorStarted("TestDevice", LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    }
    assertTrue(liveLiteralsService.isAvailable)
    val psiElementWithoutContainingFile = runReadAction { file1.containingDirectory }
    liveLiteralsService.isElementManaged(psiElementWithoutContainingFile)
  }
}