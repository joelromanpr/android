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
package com.android.tools.idea.gradle.project.sync.idea;

import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION;
import static com.android.tools.idea.flags.StudioFlags.DISABLE_FORCED_UPGRADES;
import static com.android.tools.idea.gradle.project.sync.IdeAndroidModelsKt.ideAndroidSyncErrorToException;
import static com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId;
import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.simulateRegisteredSyncError;
import static com.android.tools.idea.gradle.project.sync.errors.GradleDistributionInstallIssueCheckerKt.COULD_NOT_INSTALL_GRADLE_DISTRIBUTION_PREFIX;
import static com.android.tools.idea.gradle.project.sync.idea.SdkSyncUtil.syncAndroidSdks;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.JAVA_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NATIVE_VARIANTS;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NDK_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.PROJECT_CLEANUP_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.SYNC_ISSUE;
import static com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgrade.displayForceUpdatesDisabledMessage;
import static com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgrade.expireProjectUpgradeNotifications;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.ANDROID_HOME_JVM_ARG;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.testartifacts.scopes.ExcludedRoots.getAllSourceFolders;
import static com.android.utils.BuildScriptUtil.findGradleSettingsFile;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.GRADLE_SYNC;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE_DETAILS;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.LIBRARY_DEPENDENCY;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.find;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.isInProcessMode;
import static com.intellij.openapi.util.text.StringUtil.endsWithIgnoreCase;
import static com.intellij.util.ExceptionUtil.getRootCause;
import static com.intellij.util.PathUtil.getJarPathForClass;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getModuleId;

import android.annotation.SuppressLint;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.gradle.model.GradlePluginModel;
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifacts;
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifactsModel;
import com.android.repository.Revision;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.LibraryFilePaths;
import com.android.tools.idea.gradle.LibraryFilePaths.ArtifactPaths;
import com.android.tools.idea.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.model.IdeArtifactName;
import com.android.tools.idea.gradle.model.IdeBaseArtifact;
import com.android.tools.idea.gradle.model.IdeModuleSourceSet;
import com.android.tools.idea.gradle.model.IdeSourceProvider;
import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.tools.idea.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeVariantAbi;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.IdeaJavaModuleModelFactory;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.model.V2NdkModel;
import com.android.tools.idea.gradle.project.sync.AdditionalClassifierArtifactsActionOptions;
import com.android.tools.idea.gradle.project.sync.AllVariantsSyncActionOptions;
import com.android.tools.idea.gradle.project.sync.AndroidExtraModelProvider;
import com.android.tools.idea.gradle.project.sync.GradleSyncStudioFlags;
import com.android.tools.idea.gradle.project.sync.IdeAndroidModels;
import com.android.tools.idea.gradle.project.sync.IdeAndroidNativeVariantsModels;
import com.android.tools.idea.gradle.project.sync.IdeAndroidSyncError;
import com.android.tools.idea.gradle.project.sync.NativeVariantsSyncActionOptions;
import com.android.tools.idea.gradle.project.sync.SdkSync;
import com.android.tools.idea.gradle.project.sync.SelectedVariantCollector;
import com.android.tools.idea.gradle.project.sync.SelectedVariants;
import com.android.tools.idea.gradle.project.sync.SingleVariantSyncActionOptions;
import com.android.tools.idea.gradle.project.sync.SyncActionOptions;
import com.android.tools.idea.gradle.project.sync.common.CommandLineArgs;
import com.android.tools.idea.gradle.project.sync.idea.data.model.ProjectCleanupModel;
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys;
import com.android.tools.idea.gradle.project.sync.idea.issues.JdkImportCheck;
import com.android.tools.idea.gradle.run.AndroidGradleTestTasksProvider;
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.io.FilePaths;
import com.android.tools.idea.projectsystem.gradle.GradleProjectPath;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.stats.UsageTrackerUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.externalSystem.JavaModuleData;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationsConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData;
import com.intellij.openapi.externalSystem.model.project.LibraryLevel;
import com.intellij.openapi.externalSystem.model.project.LibraryPathType;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.project.TestData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.PathsList;
import com.intellij.util.SystemProperties;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipException;
import kotlin.Unit;
import org.gradle.tooling.model.ProjectIdentifier;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptGradleModel;
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptModelBuilderService;
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptSourceSetModel;
import org.jetbrains.plugins.gradle.model.BuildScriptClasspathModel;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleExecutionWorkspace;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * Imports Android-Gradle projects into IDEA.
 */
@Order(ExternalSystemConstants.UNORDERED)
public final class AndroidGradleProjectResolver extends AbstractProjectResolverExtension implements AndroidGradleProjectResolverMarker {
  /**
   * Stores a collection of variants of the data node tree for previously synced build variants.
   * <p>
   * NOTE: This key/data is not directly processed by any data importers.
   */
  @NotNull public static final com.intellij.openapi.externalSystem.model.Key<VariantProjectDataNodes>
    CACHED_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS =
    com.intellij.openapi.externalSystem.model.Key.create(VariantProjectDataNodes.class, 1 /* not used */);

  public static final GradleVersion MINIMUM_SUPPORTED_VERSION = GradleVersion.parse(GRADLE_PLUGIN_MINIMUM_VERSION);
  public static final String BUILD_SYNC_ORPHAN_MODULES_NOTIFICATION_GROUP_NAME = "Build sync orphan modules";

  private static final Key<Boolean> IS_ANDROID_PROJECT_KEY = Key.create("IS_ANDROID_PROJECT_KEY");

  private static final Key<Boolean> IS_ANDROID_PLUGIN_REQUESTING_KOTLIN_GRADLE_MODEL_KEY =
    Key.create("IS_ANDROID_PLUGIN_REQUESTING_KOTLIN_GRADLE_MODEL_KEY");

  private static final Key<Boolean> IS_ANDROID_PLUGIN_REQUESTING_KAPT_GRADLE_MODEL_KEY =
    Key.create("IS_ANDROID_PLUGIN_REQUESTING_KAPT_GRADLE_MODEL_KEY");

  static final Key<Boolean> IS_JAR_WRAPPED_MODULE =
    Key.create("JAR_WRAPPED_LIBRARY_MODULE");

  @NotNull private final CommandLineArgs myCommandLineArgs;
  @NotNull private final IdeaJavaModuleModelFactory myIdeaJavaModuleModelFactory;

  private @Nullable Project myProject;
  private boolean myIsModulePerSourceSetMode;
  private final Map<GradleProjectPath, DataNode<? extends ModuleData>> myModuleDataByGradlePath = new LinkedHashMap<>();

  public AndroidGradleProjectResolver() {
    this(new CommandLineArgs(), new IdeaJavaModuleModelFactory());
  }

  @NonInjectable
  @VisibleForTesting
  AndroidGradleProjectResolver(@NotNull CommandLineArgs commandLineArgs,
                               @NotNull IdeaJavaModuleModelFactory ideaJavaModuleModelFactory) {
    myCommandLineArgs = commandLineArgs;
    myIdeaJavaModuleModelFactory = ideaJavaModuleModelFactory;
  }

  @Override
  public void setProjectResolverContext(@NotNull ProjectResolverContext projectResolverContext) {
    myProject = projectResolverContext.getExternalSystemTaskId().findProject();
    myIsModulePerSourceSetMode = myProject != null && ModuleUtil.isModulePerSourceSetEnabled(myProject);
    // Setting this flag on the `projectResolverContext` tells the Kotlin IDE plugin that we are requesting `KotlinGradleModel` for all
    // modules. This is to be able to provide additional arguments to the model builder and avoid unnecessary processing of currently the
    // inactive build variants.
    projectResolverContext.putUserData(IS_ANDROID_PLUGIN_REQUESTING_KOTLIN_GRADLE_MODEL_KEY, true);
    // Similarly for KAPT.
    projectResolverContext.putUserData(IS_ANDROID_PLUGIN_REQUESTING_KAPT_GRADLE_MODEL_KEY, true);
    super.setProjectResolverContext(projectResolverContext);
  }

  @Override
  @Nullable
  public DataNode<ModuleData> createModule(@NotNull IdeaModule gradleModule, @NotNull DataNode<ProjectData> projectDataNode) {
    if (!isAndroidGradleProject()) {
      return nextResolver.createModule(gradleModule, projectDataNode);
    }

    IdeAndroidModels androidModels = resolverCtx.getExtraProject(gradleModule, IdeAndroidModels.class);
    DataNode<ModuleData> moduleDataNode = nextResolver.createModule(gradleModule, projectDataNode);
    if (moduleDataNode == null) {
      return null;
    }

    createAndAttachModelsToDataNode(projectDataNode, moduleDataNode, gradleModule, androidModels);
    patchLanguageLevels(moduleDataNode, gradleModule, androidModels != null ? androidModels.getAndroidProject() : null);

    registerModuleData(gradleModule, moduleDataNode);
    return moduleDataNode;
  }

  private void registerModuleData(@NotNull IdeaModule gradleModule,
                                  DataNode<ModuleData> moduleDataNode) {
    ProjectIdentifier projectIdentifier = gradleModule.getGradleProject().getProjectIdentifier();

    if (isModulePerSourceSetEnabled()) {
      Collection<DataNode<GradleSourceSetData>> sourceSetNodes = findAll(moduleDataNode, GradleSourceSetData.KEY);

      if (!sourceSetNodes.isEmpty()) {
        // ":" and similar holder projects do not have any source sets and should not be a target of module dependencies.
        sourceSetNodes.forEach(node -> {
          IdeModuleSourceSet sourceSet = ModuleUtil.getIdeModuleSourceSet(node.getData());

          if (sourceSet != null && sourceSet.getCanBeConsumed()) {
            GradleProjectPath gradleProjectPath = new GradleProjectPath(
              projectIdentifier.getBuildIdentifier().getRootDir(),
              projectIdentifier.getProjectPath(),
              sourceSet
            );
            myModuleDataByGradlePath.put(gradleProjectPath, node);
          }
        });
      } else {
        // We may need to link modules without a source set
        GradleProjectPath gradleProjectPath = new GradleProjectPath(
          projectIdentifier.getBuildIdentifier().getRootDir().getPath(),
          projectIdentifier.getProjectPath(),
          null
        );
        myModuleDataByGradlePath.put(gradleProjectPath, moduleDataNode);
      }
    } else {
      if (moduleDataNode != null) {
        GradleProjectPath gradleProjectPath = new GradleProjectPath(
          projectIdentifier.getBuildIdentifier().getRootDir(),
          projectIdentifier.getProjectPath(),
          IdeModuleSourceSet.MAIN
        );
        myModuleDataByGradlePath.put(gradleProjectPath, moduleDataNode);
      }
    }
  }

  private void patchLanguageLevels(DataNode<ModuleData> moduleDataNode,
                                   @NotNull IdeaModule gradleModule,
                                   @Nullable IdeAndroidProject androidProject) {
    DataNode<JavaModuleData> javaModuleData = find(moduleDataNode, JavaModuleData.KEY);
    if (javaModuleData == null) {
      return;
    }
    JavaModuleData moduleData = javaModuleData.getData();
    if (androidProject != null) {
      LanguageLevel languageLevel = LanguageLevel.parse(androidProject.getJavaCompileOptions().getSourceCompatibility());
      moduleData.setLanguageLevel(languageLevel);
      moduleData.setTargetBytecodeVersion(androidProject.getJavaCompileOptions().getTargetCompatibility());
    }
    else {
      // Workaround BaseGradleProjectResolverExtension since the IdeaJavaLanguageSettings doesn't contain any information.
      // For this we set the language level based on the "main" source set of the module.
      // TODO: Remove once we have switched to module per source set. The base resolver should handle that correctly.
      ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
      if (externalProject != null) {
        // main should always exist, if it doesn't other things will fail before this.
        ExternalSourceSet externalSourceSet = externalProject.getSourceSets().get("main");
        if (externalSourceSet != null) {
          LanguageLevel languageLevel = LanguageLevel.parse(externalSourceSet.getSourceCompatibility());
          moduleData.setLanguageLevel(languageLevel);
          moduleData.setTargetBytecodeVersion(externalSourceSet.getTargetCompatibility());
        }
      }
    }
  }

  @Override
  public void populateModuleCompileOutputSettings(@NotNull IdeaModule gradleModule,
                                                  @NotNull DataNode<ModuleData> ideModule) {
    super.populateModuleCompileOutputSettings(gradleModule, ideModule);
    CompilerOutputUtilKt.setupCompilerOutputPaths(ideModule);
  }

  @Override
  public @NotNull Set<Class<?>> getToolingExtensionsClasses() {
    return ImmutableSet.of(KaptModelBuilderService.class, Unit.class);
  }

  /**
   * Creates and attaches the following models to the moduleNode depending on the type of module:
   * <ul>
   *   <li>GradleAndroidModel</li>
   *   <li>NdkModuleModel</li>
   *   <li>GradleModuleModel</li>
   *   <li>JavaModuleModel</li>
   * </ul>
   *
   * @param moduleNode    the module node to attach the models to
   * @param gradleModule  the module in question
   * @param androidModels the android project models obtained from this module (null is none found)
   */
  private void createAndAttachModelsToDataNode(@NotNull DataNode<ProjectData> projectDataNode,
                                               @NotNull DataNode<ModuleData> moduleNode,
                                               @NotNull IdeaModule gradleModule,
                                               @Nullable IdeAndroidModels androidModels) {
    String moduleName = moduleNode.getData().getInternalName();
    File rootModulePath = FilePaths.stringToFile(moduleNode.getData().getLinkedExternalProjectPath());

    ExternalProject externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
    KaptGradleModel kaptGradleModel =
      (androidModels != null) ? androidModels.getKaptGradleModel() : resolverCtx.getExtraProject(gradleModule, KaptGradleModel.class);
    GradlePluginModel gradlePluginModel = resolverCtx.getExtraProject(gradleModule, GradlePluginModel.class);
    BuildScriptClasspathModel buildScriptClasspathModel = resolverCtx.getExtraProject(gradleModule, BuildScriptClasspathModel.class);

    GradleAndroidModel androidModel = null;
    JavaModuleModel javaModuleModel = null;
    NdkModuleModel ndkModuleModel = null;
    GradleModuleModel gradleModel = null;
    Collection<IdeSyncIssue> issueData = null;

    if (androidModels != null) {
      androidModel = createGradleAndroidModel(moduleName, rootModulePath, androidModels);
      issueData = androidModels.getSyncIssues();
      String ndkModuleName = moduleName + ((isModulePerSourceSetEnabled()) ? "." + ModuleUtil.getModuleName(androidModel.getMainArtifact()) : "");
      ndkModuleModel = maybeCreateNdkModuleModel(ndkModuleName, rootModulePath, androidModels);
    }

    Collection<String> gradlePluginList = (gradlePluginModel == null) ? ImmutableList.of() : gradlePluginModel.getGradlePluginList();
    File gradleSettingsFile = findGradleSettingsFile(rootModulePath);
    boolean hasArtifactsOrNoRootSettingsFile = !(gradleSettingsFile.isFile() && !hasArtifacts(externalProject));

    if (hasArtifactsOrNoRootSettingsFile || androidModel != null) {
      gradleModel =
        createGradleModuleModel(moduleName,
                                gradleModule,
                                androidModels == null ? null : androidModels.getAndroidProject().getAgpVersion(),
                                kaptGradleModel,
                                buildScriptClasspathModel,
                                gradlePluginList);
    }
    if (androidModel == null) {
      javaModuleModel = createJavaModuleModel(gradleModule, externalProject, gradlePluginList, hasArtifactsOrNoRootSettingsFile);
    }

    if (javaModuleModel != null) {
      moduleNode.createChild(JAVA_MODULE_MODEL, javaModuleModel);
    }
    if (gradleModel != null) {
      moduleNode.createChild(GRADLE_MODULE_MODEL, gradleModel);
    }
    if (androidModel != null) {
      moduleNode.createChild(ANDROID_MODEL, androidModel);
    }
    if (ndkModuleModel != null) {
      moduleNode.createChild(NDK_MODEL, ndkModuleModel);
    }
    if (issueData != null) {
      issueData.forEach(it -> moduleNode.createChild(SYNC_ISSUE, it));
    }
    // We also need to patch java modules as we disabled the kapt resolver.
    // Setup Kapt this functionality should be done by KaptProjectResovlerExtension if possible.
    // If we have module per sourceSet turned on we need to fill in the GradleSourceSetData for each of the artifacts.
    if (isModulePerSourceSetEnabled() && androidModel != null) {
      IdeVariant variant = androidModel.getSelectedVariant();
      GradleSourceSetData prodModule = createAndSetupGradleSourceSetDataNode(moduleNode, gradleModule, variant.getMainArtifact(), null);
      IdeBaseArtifact unitTest = variant.getUnitTestArtifact();
      if (unitTest != null) {
        createAndSetupGradleSourceSetDataNode(moduleNode, gradleModule, unitTest, prodModule);
      }
      IdeBaseArtifact androidTest = variant.getAndroidTestArtifact();
      if (androidTest != null) {
        createAndSetupGradleSourceSetDataNode(moduleNode, gradleModule, androidTest, prodModule);
      }
      IdeBaseArtifact testFixtures = variant.getTestFixturesArtifact();
      if (testFixtures != null) {
        createAndSetupGradleSourceSetDataNode(moduleNode, gradleModule, testFixtures, prodModule);
      }
    }

    // Setup testData nodes for testing sources used by Gradle test runners.
    if (androidModel != null) {
      createAndSetupTestDataNode(moduleNode, androidModel);
    }

    // Ensure the kapt module is stored on the datanode so that dependency setup can use it
    moduleNode.putUserData(AndroidGradleProjectResolverKeys.KAPT_GRADLE_MODEL_KEY, kaptGradleModel);

    if (androidModel == null) {
      // Maybe set Jar wrapper marker for non-android modules
      ExternalProject project = resolverCtx.getExtraProject(gradleModule, ExternalProject.class);
      if (project != null && project.getArtifactsByConfiguration().size() == 1) {
        Set<File> artifacts = project.getArtifactsByConfiguration().get("default");
        if (artifacts != null) {
          moduleNode.putUserData(IS_JAR_WRAPPED_MODULE, artifacts.stream()
            .anyMatch(artifact -> artifact.isFile() && endsWithIgnoreCase(artifact.getName(), DOT_JAR)));
        }
      }
    }

    patchMissingKaptInformationOntoModelAndDataNode(androidModel, moduleNode, kaptGradleModel);

    // Populate extra things
    populateAdditionalClassifierArtifactsModel(gradleModule);
  }

  @NotNull
  private JavaModuleModel createJavaModuleModel(@NotNull IdeaModule gradleModule,
                                                ExternalProject externalProject,
                                                Collection<String> gradlePluginList,
                                                boolean hasArtifactsOrNoRootSettingsFile) {
    boolean isBuildable = hasArtifactsOrNoRootSettingsFile && gradlePluginList.contains("org.gradle.api.plugins.JavaPlugin");
    // TODO: This model should eventually be removed.
    return myIdeaJavaModuleModelFactory.create(gradleModule, externalProject, isBuildable);
  }

  @NotNull
  private static GradleModuleModel createGradleModuleModel(String moduleName,
                                                           @NotNull IdeaModule gradleModule,
                                                           @Nullable String modelVersionString,
                                                           KaptGradleModel kaptGradleModel,
                                                           BuildScriptClasspathModel buildScriptClasspathModel,
                                                           Collection<String> gradlePluginList) {
    File buildScriptPath;
    try {
      buildScriptPath = gradleModule.getGradleProject().getBuildScript().getSourceFile();
    }
    catch (UnsupportedOperationException e) {
      buildScriptPath = null;
    }

    return new GradleModuleModel(
      moduleName,
      gradleModule.getGradleProject(),
      gradlePluginList,
      buildScriptPath,
      (buildScriptClasspathModel == null) ? null : buildScriptClasspathModel.getGradleVersion(),
      modelVersionString,
      kaptGradleModel
    );
  }

  @Nullable
  private static NdkModuleModel maybeCreateNdkModuleModel(@NotNull String moduleName,
                                                          @NotNull File rootModulePath,
                                                          @NotNull IdeAndroidModels ideModels) {
    // Prefer V2 NativeModule if available
    String selectedAbiName = ideModels.getSelectedAbiName();
    // If there are models we have a selected ABI name.
    if (selectedAbiName == null) return null;
    if (ideModels.getV2NativeModule() != null) {
      return new NdkModuleModel(moduleName,
                                rootModulePath,
                                ideModels.getSelectedVariantName(),
                                selectedAbiName,
                                new V2NdkModel(ideModels.getAndroidProject().getAgpVersion(), ideModels.getV2NativeModule()));
    }
    // V2 model not available, fallback to V1 model.
    if (ideModels.getV1NativeProject() != null) {
      List<IdeNativeVariantAbi> ideNativeVariantAbis = new ArrayList<>();
      if (ideModels.getV1NativeVariantAbi() != null) {
        ideNativeVariantAbis.add(ideModels.getV1NativeVariantAbi());
      }

      return new NdkModuleModel(moduleName,
                                rootModulePath,
                                ideModels.getSelectedVariantName(),
                                selectedAbiName,
                                ideModels.getV1NativeProject(),
                                ideNativeVariantAbis);
    }
    return null;
  }

  @NotNull
  private static GradleAndroidModel createGradleAndroidModel(String moduleName,
                                                             File rootModulePath,
                                                             @NotNull IdeAndroidModels ideModels) {

    return GradleAndroidModel.create(moduleName,
                                     rootModulePath,
                                     ideModels.getAndroidProject(),
                                     ideModels.getFetchedVariants(),
                                     ideModels.getSelectedVariantName());
  }

  @SuppressLint("NewApi")
  private void createAndSetupTestDataNode(@NotNull DataNode<ModuleData> moduleDataNode,
                                          @NotNull GradleAndroidModel GradleAndroidModel) {
    // TODO(b/205094187): We can also do setUp androidTest tasks from here and they will then be shown as an option when right clicking run tests.
    // Get the unit test task for the current module.
    String testTaskName = AndroidGradleTestTasksProvider.getTasksFromAndroidModuleData(GradleAndroidModel);
    Set<String> sourceFolders = new HashSet<>();
    for (IdeSourceProvider sourceProvider : GradleAndroidModel.getTestSourceProviders(IdeArtifactName.UNIT_TEST)) {
      for (File sourceFolder : getAllSourceFolders(sourceProvider)) {
        sourceFolders.add(sourceFolder.getPath());
      }
    }
    TestData testData = new TestData(GradleConstants.SYSTEM_ID, testTaskName, testTaskName, sourceFolders);
    moduleDataNode.createChild(ProjectKeys.TEST, testData);
  }

  private GradleSourceSetData createAndSetupGradleSourceSetDataNode(@NotNull DataNode<ModuleData> parentDataNode,
                                                                    @NotNull IdeaModule gradleModule,
                                                                    @NotNull IdeBaseArtifact artifact,
                                                                    @Nullable GradleSourceSetData productionModule) {
    String moduleId = computeModuleIdForArtifact(resolverCtx, gradleModule, artifact);
    String readableArtifactName = ModuleUtil.getModuleName(artifact);
    String moduleExternalName = gradleModule.getName() + ":" + readableArtifactName;
    String moduleInternalName =
      parentDataNode.getData().getInternalName() + "." + readableArtifactName;

    GradleSourceSetData sourceSetData =
      new GradleSourceSetData(moduleId, moduleExternalName, moduleInternalName, parentDataNode.getData().getModuleFileDirectoryPath(),
                              parentDataNode.getData().getLinkedExternalProjectPath());

    if (productionModule != null) {
      sourceSetData.setProductionModuleId(productionModule.getInternalName());
    }

    parentDataNode.createChild(GradleSourceSetData.KEY, sourceSetData);
    return sourceSetData;
  }

  private static String computeModuleIdForArtifact(@NotNull ProjectResolverContext resolverCtx,
                                                   @NotNull IdeaModule gradleModule,
                                                   @NotNull IdeBaseArtifact baseArtifact) {
    return getModuleId(resolverCtx, gradleModule) + ":" + ModuleUtil.getModuleName(baseArtifact);
  }

  /**
   * Adds the Kapt generated source directories to Android models generated source folders and sets up the kapt generated class library
   * for both Android and non-android modules.
   * <p>
   * This should probably not be done here. If we need this information in the Android model then this should
   * be the responsibility of the Android Gradle plugin. If we don't then this should be handled by the
   * KaptProjectResolverExtension, however as of now this class only works when module per source set is
   * enabled.
   */
  public static void patchMissingKaptInformationOntoModelAndDataNode(@Nullable GradleAndroidModel androidModel,
                                                                     @NotNull DataNode<ModuleData> moduleDataNode,
                                                                     @Nullable KaptGradleModel kaptGradleModel) {
    if (kaptGradleModel == null || !kaptGradleModel.isEnabled()) {
      return;
    }

    Set<File> generatedClassesDirs = new HashSet<>();
    Set<File> generatedTestClassesDirs = new HashSet<>();
    kaptGradleModel.getSourceSets().forEach(sourceSet -> {
      if (androidModel == null) {
        // This is a non-android module
        if (sourceSet.isTest()) {
          generatedTestClassesDirs.add(sourceSet.getGeneratedClassesDirFile());
        } else {
          generatedClassesDirs.add(sourceSet.getGeneratedClassesDirFile());
        }
        return;
      }

      File kotlinGenSourceDir = sourceSet.getGeneratedKotlinSourcesDirFile();
      Pair<IdeVariant, IdeBaseArtifact> result = findVariantAndArtifact(sourceSet, androidModel);
      if (result == null) {
        // No artifact was found for the current source set
        return;
      }

      IdeVariant variant = result.first;
      IdeBaseArtifact artifact = result.second;
      if (artifact != null) {
        if (kotlinGenSourceDir != null && !artifact.getGeneratedSourceFolders().contains(kotlinGenSourceDir)) {
          artifact.addGeneratedSourceFolder(kotlinGenSourceDir);
        }

        if (variant.equals(androidModel.getSelectedVariant())) {
          File classesDirFile = sourceSet.getGeneratedClassesDirFile();
          if (classesDirFile != null) {
            if (artifact.isTestArtifact()) {
              generatedTestClassesDirs.add(classesDirFile);
            } else {
              generatedClassesDirs.add(classesDirFile);
            }
          }
        }
      }
    });

    addToNewOrExistingLibraryData(moduleDataNode, "kaptGeneratedClasses", generatedClassesDirs, false);
    addToNewOrExistingLibraryData(moduleDataNode, "kaptGeneratedTestClasses", generatedTestClassesDirs, true);
  }

  private static void addToNewOrExistingLibraryData(@NotNull DataNode<ModuleData> moduleDataNode,
                                             @NotNull String name,
                                             @NotNull Set<File> files,
                                             boolean isTest) {
    // Code adapted from KaptProjectResolverExtension
    LibraryData newLibrary = new LibraryData(GRADLE_SYSTEM_ID, name);
    LibraryData existingData = moduleDataNode.getChildren().stream().map(DataNode::getData).filter(
      (data) -> data instanceof LibraryDependencyData &&
                newLibrary.getExternalName().equals(((LibraryDependencyData)data).getExternalName()))
      .map(data -> ((LibraryDependencyData)data).getTarget()).findFirst().orElse(null);

    if (existingData != null) {
      files.forEach((file) -> existingData.addPath(LibraryPathType.BINARY, file.getAbsolutePath()));
    } else {
      files.forEach((file) -> newLibrary.addPath(LibraryPathType.BINARY, file.getAbsolutePath()));
      LibraryDependencyData libraryDependencyData = new LibraryDependencyData(moduleDataNode.getData(), newLibrary, LibraryLevel.MODULE);
      libraryDependencyData.setScope(isTest ? DependencyScope.TEST : DependencyScope.COMPILE);
      moduleDataNode.createChild(LIBRARY_DEPENDENCY, libraryDependencyData);
    }
  }

  @Nullable
  private static Pair<IdeVariant, IdeBaseArtifact> findVariantAndArtifact(@NotNull KaptSourceSetModel sourceSetModel,
                                                                          @NotNull GradleAndroidModel androidModel) {
    String sourceSetName = sourceSetModel.getSourceSetName();
    if (!sourceSetModel.isTest()) {
      IdeVariant variant = androidModel.findVariantByName(sourceSetName);
      return variant == null ? null : Pair.create(variant, variant.getMainArtifact());
    }

    // Check if it's android test source set.
    String androidTestSuffix = "AndroidTest";
    if (sourceSetName.endsWith(androidTestSuffix)) {
      String variantName = sourceSetName.substring(0, sourceSetName.length() - androidTestSuffix.length());
      IdeVariant variant = androidModel.findVariantByName(variantName);
      return variant == null ? null : Pair.create(variant, variant.getAndroidTestArtifact());
    }

    // Check if it's test fixtures source set.
    String testFixturesSuffix = "TestFixtures";
    if (sourceSetName.endsWith(testFixturesSuffix)) {
      String variantName = sourceSetName.substring(0, sourceSetName.length() - testFixturesSuffix.length());
      IdeVariant variant = androidModel.findVariantByName(variantName);
      return variant == null ? null : Pair.create(variant, variant.getTestFixturesArtifact());
    }

    // Check if it's unit test source set.
    String unitTestSuffix = "UnitTest";
    if (sourceSetName.endsWith(unitTestSuffix)) {
      String variantName = sourceSetName.substring(0, sourceSetName.length() - unitTestSuffix.length());
      IdeVariant variant = androidModel.findVariantByName(variantName);
      return variant == null ? null : Pair.create(variant, variant.getUnitTestArtifact());
    }

    return null;
  }

  private void populateAdditionalClassifierArtifactsModel(@NotNull IdeaModule gradleModule) {
    Project project = getProject();
    AdditionalClassifierArtifactsModel artifacts = resolverCtx.getExtraProject(gradleModule, AdditionalClassifierArtifactsModel.class);
    if (artifacts != null && project != null) {
      LibraryFilePaths.getInstance(project).populate(artifacts);
    }
  }

  @Override
  public void populateModuleContentRoots(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule) {
    DataNode<GradleAndroidModel> GradleAndroidModelNode = ExternalSystemApiUtil.find(ideModule, AndroidProjectKeys.ANDROID_MODEL);
    // Only process android modules.
    if (GradleAndroidModelNode == null) {
      super.populateModuleContentRoots(gradleModule, ideModule);
      return;
    }

    nextResolver.populateModuleContentRoots(gradleModule, ideModule);

    if (isModulePerSourceSetEnabled()) {
      ContentRootUtilKt.setupAndroidContentEntriesPerSourceSet(
        ideModule,
        GradleAndroidModelNode.getData()
      );
    } else {
      ContentRootUtilKt.setupAndroidContentEntries(ideModule, null);
    }
  }

  private static boolean hasArtifacts(@Nullable ExternalProject externalProject) {
    return externalProject != null && !externalProject.getArtifacts().isEmpty();
  }

  @Override
  public void populateModuleDependencies(@NotNull IdeaModule gradleModule,
                                         @NotNull DataNode<ModuleData> ideModule,
                                         @NotNull DataNode<ProjectData> ideProject) {
    DataNode<GradleAndroidModel> androidModelNode = ExternalSystemApiUtil.find(ideModule, AndroidProjectKeys.ANDROID_MODEL);
    // Don't process non-android modules here.
    if (androidModelNode == null) {
      super.populateModuleDependencies(gradleModule, ideModule, ideProject);
      return;
    }

    // Call all the other resolvers to ensure that any dependencies that they need to provide are added.
    nextResolver.populateModuleDependencies(gradleModule, ideModule, ideProject);

    AdditionalClassifierArtifactsModel additionalArtifacts =
      resolverCtx.getExtraProject(gradleModule, AdditionalClassifierArtifactsModel.class);
    // TODO: Log error messages from additionalArtifacts.

    GradleExecutionSettings settings = resolverCtx.getSettings();
    GradleExecutionWorkspace workspace = (settings == null) ? null : settings.getExecutionWorkspace();

    Map<String, AdditionalClassifierArtifacts> additionalArtifactsMap;
    if (additionalArtifacts != null) {
      additionalArtifactsMap =
        additionalArtifacts
          .getArtifacts()
          .stream()
          .collect(
            Collectors.toMap((k) -> String.format("%s:%s:%s", k.getId().getGroupId(), k.getId().getArtifactId(), k.getId().getVersion()),
                             (k) -> k
            ));
    }
    else {
      additionalArtifactsMap = ImmutableMap.of();
    }


    Project project = getProject();
    LibraryFilePaths libraryFilePaths;
    if (project == null) {
      libraryFilePaths = null;
    } else {
      libraryFilePaths = LibraryFilePaths.getInstance(project);
    }

    Function<GradleProjectPath, DataNode<? extends ModuleData>> moduleDataLookup = (gradleProjectPath) -> {
      // In the case when model v2 is enabled and module per source set is disabled, we might get a query to resolve a dependency on
      // a testFixtures module. As testFixtures relies on module per source set, we resolve the dependency to the main module instead.
      if (!isModulePerSourceSetEnabled() && gradleProjectPath.getSourceSet() == IdeModuleSourceSet.TEST_FIXTURES) {
        return myModuleDataByGradlePath.get(
          new GradleProjectPath(gradleProjectPath.getBuildRoot(), gradleProjectPath.getPath(), IdeModuleSourceSet.MAIN));
      }
      return myModuleDataByGradlePath.get(gradleProjectPath);
    };

    Function<String, AdditionalArtifactsPaths> artifactLookup = (artifactId) -> {
      // First check to see if we just obtained any paths from Gradle. Since we don't request all the paths this can be null
      // or contain an imcomplete set of entries. In order to complete this set we need to obtains the reminder from LibraryFilePaths cache.
      AdditionalClassifierArtifacts artifacts = additionalArtifactsMap.get(artifactId);
      if (artifacts != null) {
        new AdditionalArtifactsPaths(artifacts.getSources(), artifacts.getJavadoc(), artifacts.getSampleSources());
      }

      // Then check to see whether we already have the library cached.
      if (libraryFilePaths != null) {
        ArtifactPaths cachedPaths = libraryFilePaths.getCachedPathsForArtifact(artifactId);
        if (cachedPaths != null) {
          return new AdditionalArtifactsPaths(cachedPaths.sources, cachedPaths.javaDoc, cachedPaths.sampleSource);
        }
      }
      return null;
    };

    if (isModulePerSourceSetEnabled()) {
      DependencyUtilKt.setupAndroidDependenciesForMpss(
        ideModule,
        moduleDataLookup::apply,
        artifactLookup::apply,
        androidModelNode.getData(),
        androidModelNode.getData().getSelectedVariant(),
        project
      );
    } else {
      DependencyUtilKt.setupAndroidDependenciesForModule(ideModule, moduleDataLookup::apply, artifactLookup::apply, project);
    }
  }

  @SuppressWarnings("UnstableApiUsage")
  @Override
  public void resolveFinished(@NotNull DataNode<ProjectData> projectDataNode) {
    disableOrphanModuleNotifications();
  }

  /**
   * A method that resets the configuration of "Build sync orphan modules" notification group to "not display" and "not log"
   * in order to prevent a notification which allows users to restore the removed module as a non-Gradle module. Non-Gradle modules
   * are not supported by AS in Gradle projects.
   */
  private static void disableOrphanModuleNotifications() {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      NotificationsConfiguration
        .getNotificationsConfiguration()
        .changeSettings(BUILD_SYNC_ORPHAN_MODULES_NOTIFICATION_GROUP_NAME, NotificationDisplayType.NONE, false, false);
    }
  }

  // Indicates it is an "Android" project if at least one module has an AndroidProject.
  private boolean isAndroidGradleProject() {
    Boolean isAndroidGradleProject = resolverCtx.getUserData(IS_ANDROID_PROJECT_KEY);
    if (isAndroidGradleProject != null) {
      return isAndroidGradleProject;
    }
    isAndroidGradleProject = resolverCtx.hasModulesWithModel(IdeAndroidModels.class);
    return resolverCtx.putUserDataIfAbsent(IS_ANDROID_PROJECT_KEY, isAndroidGradleProject);
  }

  @Override
  public void populateProjectExtraModels(@NotNull IdeaProject gradleProject, @NotNull DataNode<ProjectData> projectDataNode) {
    Project project = getProject();
    if (project != null) {
      attachVariantsSavedFromPreviousSyncs(project, projectDataNode);
    }
    IdeAndroidSyncError syncError = resolverCtx.getModels().getModel(IdeAndroidSyncError.class);
    if (syncError != null) {
      throw ideAndroidSyncErrorToException(syncError);
    }
    // Special mode sync to fetch additional native variants.
    for (IdeaModule gradleModule : gradleProject.getModules()) {
      IdeAndroidNativeVariantsModels nativeVariants = resolverCtx.getExtraProject(gradleModule, IdeAndroidNativeVariantsModels.class);
      if (nativeVariants != null) {
        projectDataNode.createChild(NATIVE_VARIANTS,
                                    new IdeAndroidNativeVariantsModelsWrapper(
                                      GradleProjectResolverUtil.getModuleId(resolverCtx, gradleModule),
                                      nativeVariants
                                    ));
      }
    }
    if (isAndroidGradleProject()) {
      projectDataNode.createChild(PROJECT_CLEANUP_MODEL, ProjectCleanupModel.getInstance());
    }
    super.populateProjectExtraModels(gradleProject, projectDataNode);
  }

  /**
   * This method is not used. Its functionality is only present when not using a
   * {@link ProjectImportModelProvider}. See: {@link #getModelProvider}
   */
  @Override
  @NotNull
  public Set<Class<?>> getExtraProjectModelClasses() {
    throw new UnsupportedOperationException("getExtraProjectModelClasses() is not used when getModelProvider() is overridden.");
  }

  @NotNull
  @Override
  public ProjectImportModelProvider getModelProvider() {
    return configureAndGetExtraModelProvider();
  }

  @Override
  public void preImportCheck() {
    // Don't run pre-import checks for the buildSrc project.
    if (resolverCtx.getBuildSrcGroup() != null) {
      return;
    }

    simulateRegisteredSyncError();

    String projectPath = resolverCtx.getProjectPath();
    syncAndroidSdks(SdkSync.getInstance(), projectPath);

    Project project = getProject();

    if (IdeInfo.getInstance().isAndroidStudio()) {
      // do not confuse IDEA users with warnings and quick fixes that suggest something about Android Studio in pure gradle-java projects (IDEA-266355)

      // TODO: check if we should move JdkImportCheck to platform (IDEA-268384)
      JdkImportCheck.validateProjectGradleJdk(project, projectPath);
      displayInternalWarningIfForcedUpgradesAreDisabled();
    }
    expireProjectUpgradeNotifications(project);

    if (IdeInfo.getInstance().isAndroidStudio()) {
      // Don't execute in IDEA in order to avoid conflicting behavior with IDEA's proxy support in gradle project.
      // (https://youtrack.jetbrains.com/issue/IDEA-245273, see BaseResolverExtension#getExtraJvmArgs)
      // To be discussed with the AOSP team to find a way to unify configuration across IDEA and AndroidStudio.
      cleanUpHttpProxySettings();
    }
  }

  @Override
  @NotNull
  public List<Pair<String, String>> getExtraJvmArgs() {
    if (isInProcessMode(GRADLE_SYSTEM_ID)) {
      List<Pair<String, String>> args = new ArrayList<>();

      if (IdeInfo.getInstance().isAndroidStudio()) {
        // Inject javaagent args.
        TraceSyncUtil.addTraceJvmArgs(args);
      }
      else {
        LocalProperties localProperties = getLocalProperties();
        if (localProperties.getAndroidSdkPath() == null) {
          File androidHomePath = IdeSdks.getInstance().getAndroidSdkPath();
          // In Android Studio, the Android SDK home path will never be null. It may be null when running in IDEA.
          if (androidHomePath != null) {
            args.add(Pair.create(ANDROID_HOME_JVM_ARG, androidHomePath.getPath()));
          }
        }
      }
      return args;
    }
    return emptyList();
  }

  @NotNull
  private LocalProperties getLocalProperties() {
    File projectDir = FilePaths.stringToFile(resolverCtx.getProjectPath());
    try {
      return new LocalProperties(projectDir);
    }
    catch (IOException e) {
      String msg = String.format("Unable to read local.properties file in project '%1$s'", projectDir.getPath());
      throw new ExternalSystemException(msg, e);
    }
  }

  @Override
  @NotNull
  public List<String> getExtraCommandLineArgs() {
    Project project = getProject();
    return myCommandLineArgs.get(project);
  }

  @NotNull
  @Override
  public ExternalSystemException getUserFriendlyError(@Nullable BuildEnvironment buildEnvironment,
                                                      @NotNull Throwable error,
                                                      @NotNull String projectPath,
                                                      @Nullable String buildFilePath) {
    String msg = error.getMessage();
    if (msg != null) {
      Throwable rootCause = getRootCause(error);
      if (rootCause instanceof ClassNotFoundException) {
        msg = rootCause.getMessage();
        // Project is using an old version of Gradle (and most likely an old version of the plug-in.)
        if (isUsingUnsupportedGradleVersion(msg)) {
          AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder();
          // @formatter:off
          event.setCategory(GRADLE_SYNC)
               .setKind(GRADLE_SYNC_FAILURE_DETAILS)
               .setGradleSyncFailure(GradleSyncFailure.UNSUPPORTED_GRADLE_VERSION);
          // @formatter:on;
          UsageTrackerUtils.withProjectId(event, getProject());
          UsageTracker.log(event);

          return new ExternalSystemException("The project is using an unsupported version of Gradle.");
        }
      }
      else if (rootCause instanceof ZipException) {
        if (msg.startsWith(COULD_NOT_INSTALL_GRADLE_DISTRIBUTION_PREFIX)) {
          return new ExternalSystemException(msg);
        }
      }
    }
    return super.getUserFriendlyError(buildEnvironment, error, projectPath, buildFilePath);
  }

  private static boolean isUsingUnsupportedGradleVersion(@Nullable String errorMessage) {
    return "org.gradle.api.artifacts.result.ResolvedComponentResult".equals(errorMessage) ||
           "org.gradle.api.artifacts.result.ResolvedModuleVersionResult".equals(errorMessage);
  }

  @NotNull
  private AndroidExtraModelProvider configureAndGetExtraModelProvider() {
    GradleExecutionSettings gradleExecutionSettings = resolverCtx.getSettings();
    ProjectResolutionMode projectResolutionMode = getRequestedSyncMode(gradleExecutionSettings);
    SyncActionOptions syncOptions;

    boolean parallelSync = StudioFlags.GRADLE_SYNC_PARALLEL_SYNC_ENABLED.get();
    boolean parallelSyncPrefetchVariants = StudioFlags.GRADLE_SYNC_PARALLEL_SYNC_PREFETCH_VARIANTS.get();
    GradleSyncStudioFlags studioFlags = new GradleSyncStudioFlags(
      parallelSync,
      parallelSyncPrefetchVariants,
      StudioFlags.GRADLE_SYNC_USE_V2_MODEL.get(),
      shouldDisableForceUpgrades()
    );

    if (projectResolutionMode == ProjectResolutionMode.SyncProjectMode.INSTANCE) {
      // Here we set up the options for the sync and pass them to the AndroidExtraModelProvider which will decide which will use them
      // to decide which models to request from Gradle.
      @Nullable Project project = getProject();

      AdditionalClassifierArtifactsActionOptions additionalClassifierArtifactsAction =
        new AdditionalClassifierArtifactsActionOptions(
          (project != null) ? LibraryFilePaths.getInstance(project).retrieveCachedLibs() : emptySet(),
          StudioFlags.SAMPLES_SUPPORT_ENABLED.get()
        );
      boolean isSingleVariantSync = project != null && !shouldSyncAllVariants(project);
      if (isSingleVariantSync) {
        SelectedVariantCollector variantCollector = new SelectedVariantCollector(project);
        SelectedVariants selectedVariants = variantCollector.collectSelectedVariants();
        String moduleWithVariantSwitched = project.getUserData(AndroidGradleProjectResolverKeys.MODULE_WITH_BUILD_VARIANT_SWITCHED_FROM_UI);
        project.putUserData(AndroidGradleProjectResolverKeys.MODULE_WITH_BUILD_VARIANT_SWITCHED_FROM_UI, null);
        syncOptions = new SingleVariantSyncActionOptions(
          studioFlags,
          selectedVariants,
          moduleWithVariantSwitched,
          additionalClassifierArtifactsAction
        );
      }
      else {
        syncOptions = new AllVariantsSyncActionOptions(studioFlags, additionalClassifierArtifactsAction);
      }
    }
    else if (projectResolutionMode instanceof ProjectResolutionMode.FetchNativeVariantsMode) {
      ProjectResolutionMode.FetchNativeVariantsMode fetchNativeVariantsMode =
        (ProjectResolutionMode.FetchNativeVariantsMode)projectResolutionMode;
      syncOptions = new NativeVariantsSyncActionOptions(studioFlags,
                                                        fetchNativeVariantsMode.getModuleVariants(),
                                                        fetchNativeVariantsMode.getRequestedAbis());
    }
    else {
      throw new IllegalStateException("Unknown FetchModelsMode class: " + projectResolutionMode.getClass().getName());
    }
    return new AndroidExtraModelProvider(syncOptions);
  }

  public static boolean shouldDisableForceUpgrades() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return true;
    if (SystemProperties.getBooleanProperty("studio.skip.agp.upgrade", false)) return true;
    if (StudioFlags.DISABLE_FORCED_UPGRADES.get()) return true;
    return false;
  }

  @NotNull
  private static ProjectResolutionMode getRequestedSyncMode(GradleExecutionSettings gradleExecutionSettings) {
    ProjectResolutionMode projectResolutionMode =
      gradleExecutionSettings != null ? gradleExecutionSettings.getUserData(AndroidGradleProjectResolverKeys.REQUESTED_PROJECT_RESOLUTION_MODE_KEY) : null;
    return projectResolutionMode != null ? projectResolutionMode : ProjectResolutionMode.SyncProjectMode.INSTANCE;
  }

  private static boolean shouldSyncAllVariants(@NotNull Project project) {
    Boolean shouldSyncAllVariants = project.getUserData(GradleSyncExecutor.ALL_VARIANTS_SYNC_KEY);
    return shouldSyncAllVariants != null && shouldSyncAllVariants;
  }

  private void displayInternalWarningIfForcedUpgradesAreDisabled() {
    if (DISABLE_FORCED_UPGRADES.get()) {
      Project project = getProject();
      if (project != null) {
        displayForceUpdatesDisabledMessage(project);
      }
    }
  }

  private void cleanUpHttpProxySettings() {
    Project project = getProject();
    if (project != null) {
      ApplicationManager.getApplication().invokeAndWait(() -> HttpProxySettingsCleanUp.cleanUp(project));
    }
  }

  @Override
  public void enhanceRemoteProcessing(@NotNull SimpleJavaParameters parameters) {
    PathsList classPath = parameters.getClassPath();
    classPath.add(getJarPathForClass(getClass()));
    classPath.add(getJarPathForClass(Revision.class));
    classPath.add(getJarPathForClass(AndroidGradleSettings.class));
  }

  @Nullable
  public static String getModuleIdForModule(@NotNull Module module) {
    ExternalSystemModulePropertyManager propertyManager = ExternalSystemModulePropertyManager.getInstance(module);
    String rootProjectPath = propertyManager.getRootProjectPath();
    if (rootProjectPath != null) {
      String gradlePath = propertyManager.getLinkedProjectId();
      if (gradlePath != null) {
        return createUniqueModuleId(rootProjectPath, gradlePath);
      }
    }
    return null;
  }

  @NotNull private static final Key<VariantProjectDataNodes> VARIANTS_SAVED_FROM_PREVIOUS_SYNCS =
    new Key<>("variants.saved.from.previous.syncs");

  public static void saveCurrentlySyncedVariantsForReuse(@NotNull Project project) {
    @Nullable ExternalProjectInfo data =
      ProjectDataManager.getInstance().getExternalProjectData(project, GradleConstants.SYSTEM_ID, project.getBasePath());
    if (data == null) return;
    @Nullable DataNode<ProjectData> currentDataNodes = data.getExternalProjectStructure();
    if (currentDataNodes == null) return;

    project.putUserData(
      AndroidGradleProjectResolver.VARIANTS_SAVED_FROM_PREVIOUS_SYNCS,
      VariantProjectDataNodes.Companion.collectCurrentAndPreviouslyCachedVariants(currentDataNodes));
  }

  public static void clearVariantsSavedForReuse(@NotNull Project project) {
    project.putUserData(
      AndroidGradleProjectResolver.VARIANTS_SAVED_FROM_PREVIOUS_SYNCS,
      null);
  }

  @VisibleForTesting
  public static void attachVariantsSavedFromPreviousSyncs(Project project, @NotNull DataNode<ProjectData> projectDataNode) {
    @Nullable VariantProjectDataNodes
      projectUserData = project.getUserData(VARIANTS_SAVED_FROM_PREVIOUS_SYNCS);
    if (projectUserData != null) {
      projectDataNode.createChild(CACHED_VARIANTS_FROM_PREVIOUS_GRADLE_SYNCS, projectUserData);
    }
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  private boolean isModulePerSourceSetEnabled() {
    return myIsModulePerSourceSetMode;
  }
}
