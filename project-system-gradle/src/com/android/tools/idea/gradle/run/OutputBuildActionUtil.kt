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
@file:JvmName("OutputBuildActionUtil")

package com.android.tools.idea.gradle.run

import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.util.DynamicAppUtils
import com.android.tools.idea.gradle.util.GradleUtil
import com.intellij.openapi.module.Module

/**
 * Creates BuildAction based on AndroidModelFeatures.
 * Use [OutputBuildAction] to obtain post build sync models if isPostBuildSyncSupported is true for all modules.
 */
fun create(modules: List<Module>): OutputBuildAction? {
  val usePostBuildSync = modules.mapNotNull { GradleAndroidModel.get(it)?.features }.all { it.isPostBuildSyncSupported }
  return if (usePostBuildSync) OutputBuildAction(getModuleGradlePaths(modules)) else null
}

/**
 * Get the gradle paths for the given module, all the tested projects (if it is a test app), and dynamic feature modules.
 * These paths will be used by the BuildAction run after build to know all the needed models.
 */
private fun getModuleGradlePaths(modules: List<Module>): Set<String> {
  val gradlePaths = mutableSetOf<String>()
  modules.mapNotNullTo(gradlePaths) { GradleUtil.getGradlePath(it) }
  modules
    .mapNotNull { it to (AndroidModuleModel.get(it) ?: return@mapNotNull null) }
    .forEach { (androidModule, androidModel) ->
      val androidProject = androidModel.androidProject
      when (androidProject.projectType) {
        IdeAndroidProjectType.PROJECT_TYPE_TEST -> {
          gradlePaths.addAll(androidModel.selectedVariant.testedTargetVariants.map { it.targetProjectPath })
        }
        IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE,
        IdeAndroidProjectType.PROJECT_TYPE_FEATURE -> {
          val baseModule = DynamicAppUtils.getBaseFeature(androidModule)
          if (baseModule != null) {
            GradleUtil.getGradlePath(baseModule)?.let { gradlePaths.add(it) }
          }
        }
        else -> Unit
      }
      gradlePaths.addAll(androidProject.dynamicFeatures)
    }
  return gradlePaths
}