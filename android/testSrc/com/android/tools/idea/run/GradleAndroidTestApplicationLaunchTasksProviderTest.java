/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.run;

import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getProjectSystem;
import static com.android.tools.idea.testing.TestProjectPaths.DYNAMIC_APP;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.run.editor.AndroidJavaDebugger;
import com.android.tools.idea.run.tasks.ConnectDebuggerTask;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.tasks.LaunchTaskDurations;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.testartifacts.instrumented.GradleAndroidTestApplicationLaunchTasksProvider;
import com.android.tools.idea.testartifacts.instrumented.RetentionConfiguration;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.diagnostic.Logger;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class GradleAndroidTestApplicationLaunchTasksProviderTest extends AndroidGradleTestCase {
  private ConsolePrinter mockConsolePrinter = mock(ConsolePrinter.class);
  private IDevice mockDevice = mock(IDevice.class);
  private LaunchStatus mockLaunchStatus = mock(LaunchStatus.class);
  private ProcessHandler mockProcessHandler = mock(ProcessHandler.class);
  private ProgramRunner runner = new DefaultStudioProgramRunner();

  @Override
  public void setUp() throws Exception {
    super.setUp();

    loadProject();

    if (myAndroidFacet == null) {
      fail("AndroidFacet was null");
    }
    when(mockLaunchStatus.getProcessHandler()).thenReturn(mockProcessHandler);
  }

  private void loadProject() throws Exception {
    super.loadProject(DYNAMIC_APP);
    // Run build task for main variant.
    String taskName = AndroidModuleModel.get(myAndroidFacet).getSelectedVariant().getMainArtifact().getAssembleTaskName();
    invokeGradleTasks(getProject(), taskName);
  }

  @NotNull
  private static String getDebuggerType() {
    return AndroidJavaDebugger.ID;
  }

  public void testLaunchTaskProvidedForDebugAllInModuleTest() throws Exception {
    RunnerAndConfigurationSettings configSettings = RunManager.getInstance(getProject()).
      createConfiguration("debugAllInModuleTest", AndroidRunConfigurationType.getInstance().getFactory());
    AndroidRunConfiguration config = (AndroidRunConfiguration)configSettings.getConfiguration();
    config.setModule(myAndroidFacet.getModule());
    configSettings.checkSettings();
    config.getAndroidDebuggerContext().setDebuggerType(getDebuggerType());

    Executor ex = DefaultDebugExecutor.getDebugExecutorInstance();
    ExecutionEnvironment env = new ExecutionEnvironment(ex, runner, configSettings, getProject());
    env.putCopyableUserData(DeviceFutures.KEY, DeviceFutures.forDevices(ImmutableList.of(mockDevice)));

    ApplicationIdProvider appIdProvider = getApplicationIdProvider(config);

    LaunchOptions launchOptions = LaunchOptions.builder()
      .setClearLogcatBeforeStart(false)
      .setDebug(true)
      .build();

    GradleAndroidTestApplicationLaunchTasksProvider provider = new GradleAndroidTestApplicationLaunchTasksProvider(
      (AndroidRunConfiguration)configSettings.getConfiguration(),
      env,
      myAndroidFacet,
      appIdProvider,
      launchOptions,
      /*TESTING_TYPE*/0,
      "PACKAGE_NAME",
      "CLASS_NAME",
      "METHOD_NAME",
      new RetentionConfiguration());

    List<LaunchTask> launchTasks = provider.getTasks(mockDevice, mockLaunchStatus, mockConsolePrinter);
    ConnectDebuggerTask connectDebuggerTask = provider.getConnectDebuggerTask(mockLaunchStatus, AndroidVersion.DEFAULT);

    launchTasks.forEach(task -> {
      Logger.getInstance(this.getClass()).info("LaunchTask: " + task);
      assertThat(task.getId()).isEqualTo("GRADLE_ANDROID_TEST_APPLICATION_LAUNCH_TASK");
      assertThat(task.getDescription()).isEqualTo("Launching a connectedAndroidTest for selected devices");
      assertThat(task.getDuration()).isEqualTo(LaunchTaskDurations.LAUNCH_ACTIVITY);
    });
    assertThat(connectDebuggerTask).isNotNull();
    assertThat(connectDebuggerTask.getTimeoutSeconds()).isEqualTo(-1);
  }

  public void testLaunchTaskProvidedForAllInPackageTest() throws Exception {
    RunnerAndConfigurationSettings configSettings = RunManager.getInstance(getProject()).
      createConfiguration("allInPackageTest", AndroidRunConfigurationType.getInstance().getFactory());
    AndroidRunConfiguration config = (AndroidRunConfiguration)configSettings.getConfiguration();
    config.setModule(myAndroidFacet.getModule());
    configSettings.checkSettings();

    Executor ex = DefaultDebugExecutor.getDebugExecutorInstance();
    ExecutionEnvironment env = new ExecutionEnvironment(ex, runner, configSettings, getProject());
    env.putCopyableUserData(DeviceFutures.KEY, DeviceFutures.forDevices(ImmutableList.of(mockDevice)));

    ApplicationIdProvider appIdProvider = getApplicationIdProvider(config);

    LaunchOptions launchOptions = LaunchOptions.builder()
      .setClearLogcatBeforeStart(false)
      .setDebug(false)
      .build();

    GradleAndroidTestApplicationLaunchTasksProvider provider = new GradleAndroidTestApplicationLaunchTasksProvider(
      (AndroidRunConfiguration)configSettings.getConfiguration(),
      env,
      myAndroidFacet,
      appIdProvider,
      launchOptions,
      /*TESTING_TYPE*/1,
      "PACKAGE_NAME",
      "CLASS_NAME",
      "METHOD_NAME",
      new RetentionConfiguration());

    List<LaunchTask> launchTasks = provider.getTasks(mockDevice, mockLaunchStatus, mockConsolePrinter);
    ConnectDebuggerTask connectDebuggerTask = provider.getConnectDebuggerTask(mockLaunchStatus, AndroidVersion.DEFAULT);

    launchTasks.forEach(task -> {
      Logger.getInstance(this.getClass()).info("LaunchTask: " + task);
      assertThat(task.getId()).isEqualTo("GRADLE_ANDROID_TEST_APPLICATION_LAUNCH_TASK");
    });
    assertThat(connectDebuggerTask).isNull();
  }

  public void testLaunchTaskProvidedForClassTest() throws Exception {
    RunnerAndConfigurationSettings configSettings = RunManager.getInstance(getProject()).
      createConfiguration("classTest", AndroidRunConfigurationType.getInstance().getFactory());
    AndroidRunConfiguration config = (AndroidRunConfiguration)configSettings.getConfiguration();
    config.setModule(myAndroidFacet.getModule());
    configSettings.checkSettings();

    Executor ex = DefaultDebugExecutor.getDebugExecutorInstance();
    ExecutionEnvironment env = new ExecutionEnvironment(ex, runner, configSettings, getProject());
    env.putCopyableUserData(DeviceFutures.KEY, DeviceFutures.forDevices(ImmutableList.of(mockDevice)));

    ApplicationIdProvider appIdProvider = getApplicationIdProvider(config);

    LaunchOptions launchOptions = LaunchOptions.builder()
      .setClearLogcatBeforeStart(false)
      .setDebug(false)
      .build();

    GradleAndroidTestApplicationLaunchTasksProvider provider = new GradleAndroidTestApplicationLaunchTasksProvider(
      (AndroidRunConfiguration)configSettings.getConfiguration(),
      env,
      myAndroidFacet,
      appIdProvider,
      launchOptions,
      /*TESTING_TYPE*/2,
      "PACKAGE_NAME",
      "CLASS_NAME",
      "METHOD_NAME",
      new RetentionConfiguration());

    List<LaunchTask> launchTasks = provider.getTasks(mockDevice, mockLaunchStatus, mockConsolePrinter);

    launchTasks.forEach(task -> {
      Logger.getInstance(this.getClass()).info("LaunchTask: " + task);
      assertThat(task.getId()).isEqualTo("GRADLE_ANDROID_TEST_APPLICATION_LAUNCH_TASK");
    });
  }

  public void testLaunchTaskProvidedForMethodTest() throws Exception {
    RunnerAndConfigurationSettings configSettings = RunManager.getInstance(getProject()).
      createConfiguration("methodTest", AndroidRunConfigurationType.getInstance().getFactory());
    AndroidRunConfiguration config = (AndroidRunConfiguration)configSettings.getConfiguration();
    config.setModule(myAndroidFacet.getModule());
    configSettings.checkSettings();

    Executor ex = DefaultDebugExecutor.getDebugExecutorInstance();
    ExecutionEnvironment env = new ExecutionEnvironment(ex, runner, configSettings, getProject());
    env.putCopyableUserData(DeviceFutures.KEY, DeviceFutures.forDevices(ImmutableList.of(mockDevice)));

    ApplicationIdProvider appIdProvider = getApplicationIdProvider(config);

    LaunchOptions launchOptions = LaunchOptions.builder()
      .setClearLogcatBeforeStart(false)
      .setDebug(false)
      .build();

    GradleAndroidTestApplicationLaunchTasksProvider provider = new GradleAndroidTestApplicationLaunchTasksProvider(
      (AndroidRunConfiguration)configSettings.getConfiguration(),
      env,
      myAndroidFacet,
      appIdProvider,
      launchOptions,
      /*TESTING_TYPE*/3,
      "PACKAGE_NAME",
      "CLASS_NAME",
      "METHOD_NAME",
      new RetentionConfiguration());

    List<LaunchTask> launchTasks = provider.getTasks(mockDevice, mockLaunchStatus, mockConsolePrinter);

    launchTasks.forEach(task -> {
      Logger.getInstance(this.getClass()).info("LaunchTask: " + task);
      assertThat(task.getId()).isEqualTo("GRADLE_ANDROID_TEST_APPLICATION_LAUNCH_TASK");
    });
  }

  public void testNoLaunchTaskProvidedForInvalidTestType() throws Exception {
    RunnerAndConfigurationSettings configSettings = RunManager.getInstance(getProject()).
      createConfiguration("methodTest", AndroidRunConfigurationType.getInstance().getFactory());
    AndroidRunConfiguration config = (AndroidRunConfiguration)configSettings.getConfiguration();
    config.setModule(myAndroidFacet.getModule());
    configSettings.checkSettings();

    Executor ex = DefaultDebugExecutor.getDebugExecutorInstance();
    ExecutionEnvironment env = new ExecutionEnvironment(ex, runner, configSettings, getProject());
    env.putCopyableUserData(DeviceFutures.KEY, DeviceFutures.forDevices(ImmutableList.of(mockDevice)));

    ApplicationIdProvider appIdProvider = getApplicationIdProvider(config);

    LaunchOptions launchOptions = LaunchOptions.builder()
      .setClearLogcatBeforeStart(false)
      .setDebug(false)
      .build();

    GradleAndroidTestApplicationLaunchTasksProvider provider = new GradleAndroidTestApplicationLaunchTasksProvider(
      (AndroidRunConfiguration)configSettings.getConfiguration(),
      env,
      myAndroidFacet,
      appIdProvider,
      launchOptions,
      /*INVALID_TESTING_TYPE*/4,
      "PACKAGE_NAME",
      "CLASS_NAME",
      "METHOD_NAME",
      new RetentionConfiguration());

    List<LaunchTask> launchTasks = provider.getTasks(mockDevice, mockLaunchStatus, mockConsolePrinter);

    assertThat(launchTasks).isEmpty();
  }

  public void testNoLaunchTaskProvidedForIndeterminatePackageName() throws Exception {
    RunnerAndConfigurationSettings configSettings = RunManager.getInstance(getProject()).
      createConfiguration("methodTest", AndroidRunConfigurationType.getInstance().getFactory());
    AndroidRunConfiguration config = (AndroidRunConfiguration)configSettings.getConfiguration();
    config.setModule(myAndroidFacet.getModule());
    configSettings.checkSettings();

    Executor ex = DefaultDebugExecutor.getDebugExecutorInstance();
    ExecutionEnvironment env = new ExecutionEnvironment(ex, runner, configSettings, getProject());
    env.putCopyableUserData(DeviceFutures.KEY, DeviceFutures.forDevices(ImmutableList.of(mockDevice)));

    ApplicationIdProvider appIdProvider = mock(ApplicationIdProvider.class);
    when(appIdProvider.getTestPackageName()).thenReturn(null);

    LaunchOptions launchOptions = LaunchOptions.builder()
      .setClearLogcatBeforeStart(false)

      .setDebug(false)
      .build();

    GradleAndroidTestApplicationLaunchTasksProvider provider = new GradleAndroidTestApplicationLaunchTasksProvider(
      (AndroidRunConfiguration)configSettings.getConfiguration(),
      env,
      myAndroidFacet,
      appIdProvider,
      launchOptions,
      /*TESTING_TYPE*/0,
      "PACKAGE_NAME",
      "CLASS_NAME",
      "METHOD_NAME",
      new RetentionConfiguration());

    List<LaunchTask> launchTasks = provider.getTasks(mockDevice, mockLaunchStatus, mockConsolePrinter);

    assertThat(launchTasks).isEmpty();
  }

  public void testNoLaunchTaskProvidedWhenApkProvisionExceptionThrown() throws Exception {
    RunnerAndConfigurationSettings configSettings = RunManager.getInstance(getProject()).
      createConfiguration("methodTest", AndroidRunConfigurationType.getInstance().getFactory());
    AndroidRunConfiguration config = (AndroidRunConfiguration)configSettings.getConfiguration();
    config.setModule(myAndroidFacet.getModule());
    configSettings.checkSettings();

    Executor ex = DefaultDebugExecutor.getDebugExecutorInstance();
    ExecutionEnvironment env = new ExecutionEnvironment(ex, runner, configSettings, getProject());
    env.putCopyableUserData(DeviceFutures.KEY, DeviceFutures.forDevices(ImmutableList.of(mockDevice)));

    ApplicationIdProvider appIdProvider = mock(ApplicationIdProvider.class);
    when(appIdProvider.getTestPackageName()).thenThrow(new ApkProvisionException("unable to determine package name"));

    LaunchOptions launchOptions = LaunchOptions.builder()
      .setClearLogcatBeforeStart(false)

      .setDebug(false)
      .build();

    GradleAndroidTestApplicationLaunchTasksProvider provider = new GradleAndroidTestApplicationLaunchTasksProvider(
      (AndroidRunConfiguration)configSettings.getConfiguration(),
      env,
      myAndroidFacet,
      appIdProvider,
      launchOptions,
      /*TESTING_TYPE*/0,
      "PACKAGE_NAME",
      "CLASS_NAME",
      "METHOD_NAME",
      new RetentionConfiguration());

    List<LaunchTask> launchTasks = provider.getTasks(mockDevice, mockLaunchStatus, mockConsolePrinter);

    assertThat(launchTasks).isEmpty();
  }

  @NotNull
  private ApplicationIdProvider getApplicationIdProvider(@NotNull AndroidRunConfigurationBase runConfiguration) {
    return Objects.requireNonNull(getProjectSystem(myAndroidFacet.getModule().getProject()).getApplicationIdProvider(runConfiguration));
  }
}
