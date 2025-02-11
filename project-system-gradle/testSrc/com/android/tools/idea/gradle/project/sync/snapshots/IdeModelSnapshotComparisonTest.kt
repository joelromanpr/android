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
package com.android.tools.idea.gradle.project.sync.snapshots

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.CaptureKotlinModelsProjectResolverExtension
import com.android.tools.idea.gradle.project.sync.internal.dumpAndroidIdeModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.saveAndDump
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.PathUtil
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.annotations.Contract
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Snapshot tests for 'Ide Models'.
 *
 * These tests convert Ide models to a stable text format which does not depend on local
 * environment (and ideally should not depend on the versions of irrelevant libraries) and compare them to pre-recorded golden
 * results.
 *
 * The pre-recorded sync results can be found in [snapshotDirectoryWorkspaceRelativePath] *.txt files.
 *
 * For instructions on how to update the snapshot files see [SnapshotComparisonTest] and if running from the command-line use
 * target as "//tools/adt/idea/android:intellij.android.core.tests_tests__all --test_filter=IdeModelSnapshotComparisonTest".
 */

@RunsInEdt
@RunWith(Parameterized::class)
open class IdeModelSnapshotComparisonTest : GradleIntegrationTest, SnapshotComparisonTest {

  data class TestProject(
    val template: String,
    val pathToOpen: String = "",
    val incompatibleWithAgps: Set<AgpVersion> = emptySet()
  ) {
    override fun toString(): String = "${template.removePrefix("projects/")}$pathToOpen"
  }

  enum class AgpVersion(
    val suffix: String,
    val legacyAgpVersion: String? = null
  ) {
    CURRENT("NewAgp"),
    LEGACY_4_1("Agp_4.1", "4.1.0"),
    LEGACY_4_2("Agp_4.2", "4.2.0");

    override fun toString(): String = suffix
  }

  @JvmField
  @Parameterized.Parameter(0)
  var agpVersion: AgpVersion? = null

  @JvmField
  @Parameterized.Parameter(1)
  var testProjectName: TestProject? = null

  companion object {
    private val projectsList = listOf(
      TestProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION),
      TestProject(TestProjectToSnapshotPaths.WITH_GRADLE_METADATA),
      TestProject(TestProjectToSnapshotPaths.BASIC_CMAKE_APP),
      TestProject(TestProjectToSnapshotPaths.PSD_SAMPLE_GROOVY),
      TestProject(TestProjectToSnapshotPaths.COMPOSITE_BUILD),
      TestProject(TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SETS, "/application"),
      TestProject(TestProjectToSnapshotPaths.LINKED, "/firstapp"),
      TestProject(TestProjectToSnapshotPaths.KOTLIN_KAPT),
      TestProject(TestProjectToSnapshotPaths.LINT_CUSTOM_CHECKS, incompatibleWithAgps = setOf(AgpVersion.LEGACY_4_1, AgpVersion.LEGACY_4_2)),
      TestProject(TestProjectToSnapshotPaths.TEST_FIXTURES, incompatibleWithAgps = setOf(AgpVersion.LEGACY_4_1, AgpVersion.LEGACY_4_2)),
      TestProject(TestProjectToSnapshotPaths.TEST_ONLY_MODULE),
      TestProject(TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM, incompatibleWithAgps = setOf(AgpVersion.LEGACY_4_1, AgpVersion.LEGACY_4_2)),
      TestProject(TestProjectToSnapshotPaths.MULTI_FLAVOR),
      TestProject(TestProjectToSnapshotPaths.NAMESPACES),
      TestProject(TestProjectToSnapshotPaths.INCLUDE_FROM_LIB),
      TestProject(TestProjectToSnapshotPaths.LOCAL_AARS_AS_MODULES),
      TestProject(TestProjectToSnapshotPaths.BASIC)
      )

    fun testProjectsFor(agpVersions: Collection<AgpVersion>) =
      agpVersions
        .flatMap { agpVersion ->
          projectsList
            .filter { agpVersion !in it.incompatibleWithAgps }
            .map { listOf(agpVersion, it).toTypedArray() }
        }

    @Suppress("unused")
    @Contract(pure = true)
    @JvmStatic
    @Parameterized.Parameters(name = "{1}\${0}")
    fun testProjects(): Collection<*> = testProjectsFor(AgpVersion.values().filter { it == AgpVersion.CURRENT })
  }

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  var testName = TestName()

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData/snapshots"
  override fun getAdditionalRepos(): Collection<File> =
    listOf(File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectToSnapshotPaths.PSD_SAMPLE_REPO)))

  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/ideModels"

  @Test
  fun testIdeModels() {
    StudioFlags.GRADLE_SYNC_USE_V2_MODEL.override(false)
    try {
      val projectName = testProjectName ?: error("unit test parameter not initialized")
      val agpVersion = agpVersion ?: error("unit test parameter not initialized")
      val root = prepareGradleProject(
        projectName.template,
        "project",
        null,
        agpVersion.legacyAgpVersion
      )
      CaptureKotlinModelsProjectResolverExtension.registerTestHelperProjectResolver(projectRule.fixture.testRootDisposable)
      openPreparedProject("project${testProjectName?.pathToOpen}") { project ->
        val dump = project.saveAndDump(mapOf("ROOT" to root)) { project, projectDumper ->
          projectDumper.dumpAndroidIdeModel(
            project,
            kotlinModels = { CaptureKotlinModelsProjectResolverExtension.getKotlinModel(it) },
            kaptModels = { CaptureKotlinModelsProjectResolverExtension.getKaptModel(it) }
          )
        }
        assertIsEqualToSnapshot(dump)
      }
    } finally {
      StudioFlags.GRADLE_SYNC_USE_V2_MODEL.clearOverride()
    }
  }
}