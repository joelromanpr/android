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
package com.android.tools.idea.testartifacts.instrumented

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.run.AndroidLaunchTasksProvider
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.ConsolePrinter
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.LaunchOptions
import com.android.tools.idea.run.editor.AndroidDebuggerState
import com.android.tools.idea.run.tasks.ConnectDebuggerTask
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.run.tasks.LaunchTasksProvider
import com.android.tools.idea.run.util.LaunchStatus
import com.android.tools.idea.testartifacts.instrumented.GradleAndroidTestApplicationLaunchTask.Companion.allInModuleTest
import com.android.tools.idea.testartifacts.instrumented.GradleAndroidTestApplicationLaunchTask.Companion.allInPackageTest
import com.android.tools.idea.testartifacts.instrumented.GradleAndroidTestApplicationLaunchTask.Companion.classTest
import com.android.tools.idea.testartifacts.instrumented.GradleAndroidTestApplicationLaunchTask.Companion.methodTest
import com.google.common.collect.Lists
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.plugins.gradle.util.GradleUtil

/**
 * LaunchTasksProvider that provides GradleAndroidTestApplicationLaunchTasks for instrumentation tests
 */
class GradleAndroidTestApplicationLaunchTasksProvider(private val myRunConfig: AndroidRunConfigurationBase,
                                                      private val myEnv: ExecutionEnvironment,
                                                      private val facet: AndroidFacet,
                                                      applicationIdProvider: ApplicationIdProvider,
                                                      launchOptions: LaunchOptions,
                                                      testingType: Int,
                                                      packageName: String,
                                                      className: String,
                                                      methodName: String,
                                                      private val retentionConfiguration: RetentionConfiguration) : LaunchTasksProvider {
  private val myFacet: AndroidFacet = facet
  private val myApplicationIdProvider: ApplicationIdProvider = applicationIdProvider
  private val myLaunchOptions: LaunchOptions = launchOptions
  private val myProject: Project = facet.module.project
  private val myGradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker =
    GradleConnectedAndroidTestInvoker(
      getNumberOfSelectedDevices(),
      myEnv,
      requireNotNull(myRunConfig.configurationModule.module?.let {
        GradleUtil.findGradleModuleData(it)?.data
      })
    )
  private val myTestingType: Int = testingType
  private val myPackageName: String = packageName
  private val myClassName: String = className
  private val myMethodName: String = methodName

  override fun getTasks(device: IDevice, launchStatus: LaunchStatus, consolePrinter: ConsolePrinter): List<LaunchTask> {
    val launchTasks: MutableList<LaunchTask> = Lists.newArrayList()
    val testAppId: String?
    try {
      testAppId = myApplicationIdProvider.testPackageName
      if (testAppId == null) {
        launchStatus.terminateLaunch("Unable to determine test package name", true)
        return launchTasks
      }
    }
    catch (e: ApkProvisionException) {
      launchStatus.terminateLaunch("Unable to determine test package name", true)
      return launchTasks
    }
    val appLaunchTask = when (myTestingType) {
      TEST_ALL_IN_MODULE -> {
        allInModuleTest(
          myProject,
          requireNotNull(AndroidModuleModel.get(myFacet)),
          testAppId,
          myLaunchOptions.isDebug,
          launchStatus.processHandler,
          consolePrinter,
          device,
          myGradleConnectedAndroidTestInvoker,
          retentionConfiguration)
      }
      TEST_ALL_IN_PACKAGE -> {
        allInPackageTest(
          myProject,
          requireNotNull(AndroidModuleModel.get(myFacet)),
          testAppId,
          myLaunchOptions.isDebug,
          launchStatus.processHandler,
          consolePrinter,
          device,
          myPackageName,
          myGradleConnectedAndroidTestInvoker,
          retentionConfiguration)
      }
      TEST_CLASS -> {
        classTest(
          myProject,
          requireNotNull(AndroidModuleModel.get(myFacet)),
          testAppId,
          myLaunchOptions.isDebug,
          launchStatus.processHandler,
          consolePrinter,
          device,
          myClassName,
          myGradleConnectedAndroidTestInvoker,
          retentionConfiguration)
      }
     TEST_METHOD -> {
       methodTest(
         myProject,
         requireNotNull(AndroidModuleModel.get(myFacet)),
         testAppId,
         myLaunchOptions.isDebug,
         launchStatus.processHandler,
         consolePrinter,
         device,
         myClassName,
         myMethodName,
         myGradleConnectedAndroidTestInvoker,
         retentionConfiguration)
     } else -> {
      launchStatus.terminateLaunch("Unknown testing type is selected, testing type is $myTestingType", true)
      null
     }
    }
    if (appLaunchTask != null) {
      launchTasks.add(appLaunchTask)
    }
    return launchTasks
  }

  override fun getConnectDebuggerTask(launchStatus: LaunchStatus, version: AndroidVersion?): ConnectDebuggerTask? {
    if (!myLaunchOptions.isDebug) {
      return null
    }
    val logger = Logger.getInstance(AndroidLaunchTasksProvider::class.java)
    val androidDebuggerContext = myRunConfig.androidDebuggerContext
    val debugger = androidDebuggerContext.androidDebugger
    if (debugger == null) {
      logger.warn("Unable to determine debugger to use for this launch")
      return null
    }
    logger.info("Using debugger: " + debugger.id)
    val androidDebuggerState = androidDebuggerContext.getAndroidDebuggerState<AndroidDebuggerState>()
    return if (androidDebuggerState != null) {
      debugger.getConnectDebuggerTask(myEnv,
                                      version,
                                      myApplicationIdProvider,
                                      myFacet,
                                      androidDebuggerState,
                                      myRunConfig.type.id).apply {
        timeoutSeconds = -1  // No timeout.
      }
    }
    else null
  }

  private fun getNumberOfSelectedDevices(): Int {
    return (myEnv.getCopyableUserData(DeviceFutures.KEY) ?: error("'DeviceFutures.KEY' not found")).devices.size
  }

  companion object {
    private const val TEST_ALL_IN_MODULE = 0
    private const val TEST_ALL_IN_PACKAGE = 1
    private const val TEST_CLASS = 2
    private const val TEST_METHOD = 3
  }
}
