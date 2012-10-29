/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.android.builder;

import com.intellij.util.Consumer;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.*;

/**
 * @author nik
 */
public class AndroidProjectBuildTarget extends BuildTarget<BuildRootDescriptor> {
  private final JpsModule myModule;
  private final TargetType myTargetType;

  public AndroidProjectBuildTarget(@NotNull TargetType targetType, @NotNull JpsModule module) {
    super(targetType);
    myTargetType = targetType;
    myModule = module;
  }

  @Override
  public String getId() {
    return myModule.getName();
  }

  @Override
  public Collection<BuildTarget<?>> computeDependencies() {
    final List<BuildTarget<?>> result = new ArrayList<BuildTarget<?>>();
    if (myTargetType == TargetType.PACKAGING) {
      result.add(new AndroidProjectBuildTarget(TargetType.DEX, myModule));
    }
    addModuleTargets(myModule, result);
    final JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(myModule).compileOnly();

    enumerator.processModules(new Consumer<JpsModule>() {
      @Override
      public void consume(JpsModule depModule) {
        addModuleTargets(depModule, result);
      }
    });
    return result;
  }

  private static void addModuleTargets(JpsModule module, List<BuildTarget<?>> result) {
    result.add(new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION));
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);

    if (extension != null && extension.isPackTestCode()) {
      result.add(new ModuleBuildTarget(module, JavaModuleBuildTargetType.TEST));
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AndroidProjectBuildTarget target = (AndroidProjectBuildTarget)o;

    if (!myModule.equals(target.myModule)) return false;
    return myTargetType.equals(target.myTargetType);
  }

  @Override
  public int hashCode() {
    int result = myModule.hashCode();
    result = 31 * result + myTargetType.hashCode();
    return result;
  }

  @NotNull
  @Override
  public List<BuildRootDescriptor> computeRootDescriptors(JpsModel model,
                                                          ModuleExcludeIndex index,
                                                          IgnoredFileIndex ignoredFileIndex,
                                                          BuildDataPaths dataPaths) {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public BuildRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
    return null;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Android " + myTargetType.getPresentableName();
  }

  @NotNull
  @Override
  public Collection<File> getOutputDirs(CompileContext ccontext) {
    return Collections.emptyList();
  }

  public static class TargetType extends BuildTargetType<AndroidProjectBuildTarget> {
    public static final TargetType DEX = new TargetType(AndroidCommonUtils.DEX_BUILD_TARGET_TYPE_ID, "DEX");
    public static final TargetType PACKAGING = new TargetType(AndroidCommonUtils.PACKAGING_BUILD_TARGET_TYPE_ID, "Packaging");

    private final String myPresentableName;

    private TargetType(@NotNull String typeId, @NotNull String presentableName) {
      super(typeId);
      myPresentableName = presentableName;
    }

    @NotNull
    public String getPresentableName() {
      return myPresentableName;
    }

    @NotNull
    @Override
    public List<AndroidProjectBuildTarget> computeAllTargets(@NotNull JpsModel model) {
      if (!AndroidJpsUtil.containsAndroidFacet(model.getProject())) {
        return Collections.emptyList();
      }
      final List<AndroidProjectBuildTarget> targets = new ArrayList<AndroidProjectBuildTarget>();

      for (JpsModule module : model.getProject().getModules()) {
        final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);

        if (extension != null && !extension.isLibrary()) {
          targets.add(new AndroidProjectBuildTarget(this, module));
        }
      }
      return targets;
    }

    @NotNull
    @Override
    public BuildTargetLoader<AndroidProjectBuildTarget> createLoader(@NotNull final JpsModel model) {
      final HashMap<String, AndroidProjectBuildTarget> targetMap = new HashMap<String, AndroidProjectBuildTarget>();

      for (AndroidProjectBuildTarget target : computeAllTargets(model)) {
        targetMap.put(target.getId(), target);
      }
      return new BuildTargetLoader<AndroidProjectBuildTarget>() {
        @Nullable
        @Override
        public AndroidProjectBuildTarget createTarget(@NotNull String targetId) {
          return targetMap.get(targetId);
        }
      };
    }
  }
}
